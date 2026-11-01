package database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance JDBC Connection Pool and Utility for Supabase PostgreSQL.
 *
 * Requirements satisfied:
 * - Managed JDBC Connection Pool reusing database connections across concurrent threads.
 * - Dynamic Proxy wrapping Connection so try-with-resources transparently recycles connections.
 * - Health-checked connections (isValid) before reuse; automatic replenishment of dead connections.
 * - Metadata caching for column existence checks to prevent costly repeated round-trips.
 * - Thread-safe and graceful shutdown hook.
 */
public class DatabaseConnection {

    private static final String DRIVER_CLASS = "org.postgresql.Driver";
    private static final int MAX_POOL_SIZE = 12;
    private static final int INITIAL_POOL_SIZE = 3;
    private static final int CONNECTION_TIMEOUT_SEC = 10;

    private static boolean driverLoaded = false;
    private static Map<String, String> dotEnvCache = null;

    // Connection Pool State
    private static final BlockingQueue<Connection> pool = new ArrayBlockingQueue<>(MAX_POOL_SIZE);
    private static final AtomicInteger createdConnections = new AtomicInteger(0);
    private static volatile boolean poolInitialized = false;
    private static final Object poolLock = new Object();

    // Cache for column existence checks (avoids repeated DatabaseMetaData queries)
    private static final ConcurrentHashMap<String, Boolean> columnExistsCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> isBooleanColumnCache = new ConcurrentHashMap<>();

    static {
        // Pre-seed known schema column cache to eliminate runtime metadata round-trips
        columnExistsCache.put("outages.priority_reason", true);
        columnExistsCache.put("outages.critical_facilities", true);
        columnExistsCache.put("outages.queue_sequence", true);
        columnExistsCache.put("consumers.power_status", true);
        columnExistsCache.put("network_nodes.is_active", true);
        columnExistsCache.put("technicians.availability_status", true);

        isBooleanColumnCache.put("consumers.power_status", false); // CHECK constraint text ('POWER ON', 'OUTAGE')
        isBooleanColumnCache.put("network_nodes.is_active", true);
        isBooleanColumnCache.put("technicians.availability_status", true);
    }

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        try {
            Class.forName(DRIVER_CLASS);
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[PowerTrack Database] PostgreSQL JDBC driver class not found on classpath: " + DRIVER_CLASS);
            driverLoaded = false;
        }

        // Add JVM shutdown hook to close pooled connections
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdownPool();
        }, "powertrack-db-shutdown"));
    }

    /**
     * Initializes the connection pool with pre-warmed connections.
     */
    private static void ensurePoolInitialized() {
        if (!poolInitialized && isConfigured()) {
            synchronized (poolLock) {
                if (!poolInitialized) {
                    int targetInit = Math.min(INITIAL_POOL_SIZE, MAX_POOL_SIZE);
                    for (int i = 0; i < targetInit; i++) {
                        try {
                            Connection physical = createNewPhysicalConnection();
                            pool.offer(physical);
                            createdConnections.incrementAndGet();
                        } catch (SQLException e) {
                            System.err.println("[PowerTrack Database Pool] Warm-up connection failed: " + sanitizeErrorMessage(e.getMessage()));
                            break;
                        }
                    }
                    poolInitialized = true;
                }
            }
        }
    }

    /**
     * Obtains a pooled connection wrapped in a dynamic proxy.
     * When caller calls conn.close() (e.g. via try-with-resources), the connection
     * is automatically recycled back to the pool rather than terminating the socket.
     *
     * @return Connection active database connection
     * @throws SQLException if connection cannot be obtained
     */
    public static Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            throw new SQLException("PostgreSQL JDBC driver (" + DRIVER_CLASS + ") is not loaded.");
        }

        ensurePoolInitialized();

        Connection physicalConn = null;

        // 1. Try to take an existing idle connection from the pool
        while (!pool.isEmpty()) {
            Connection candidate = pool.poll();
            if (candidate != null) {
                try {
                    if (!candidate.isClosed() && candidate.isValid(1)) {
                        physicalConn = candidate;
                        break;
                    } else {
                        closeQuietly(candidate);
                        createdConnections.decrementAndGet();
                    }
                } catch (SQLException ignored) {
                    closeQuietly(candidate);
                    createdConnections.decrementAndGet();
                }
            }
        }

        // 2. If no valid idle connection is available and we haven't reached capacity, create one
        if (physicalConn == null) {
            synchronized (poolLock) {
                if (createdConnections.get() < MAX_POOL_SIZE) {
                    physicalConn = createNewPhysicalConnection();
                    createdConnections.incrementAndGet();
                }
            }
        }

        // 3. If capacity reached, wait for a connection to be returned
        if (physicalConn == null) {
            try {
                physicalConn = pool.poll(CONNECTION_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted waiting for a pooled database connection", e);
            }

            if (physicalConn == null) {
                throw new SQLException("Database connection pool exhausted. All " + MAX_POOL_SIZE
                    + " connections are currently in use and none became available within "
                    + CONNECTION_TIMEOUT_SEC + " seconds.");
            }
        }

        // Wrap the physical connection in a proxy that intercepts close()
        return createPooledProxy(physicalConn);
    }

    /**
     * Creates a fresh physical JDBC connection to Supabase PostgreSQL.
     */
    private static Connection createNewPhysicalConnection() throws SQLException {
        String rawUrl = getEnvValue("SUPABASE_DB_URL");
        String user = getEnvValue("SUPABASE_DB_USER");
        String password = getEnvValue("SUPABASE_DB_PASSWORD");

        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new SQLException("SUPABASE_DB_URL environment variable is not set.");
        }

        String jdbcUrl = formatJdbcUrl(rawUrl.trim());

        try {
            Connection conn;
            if (user != null && !user.trim().isEmpty()) {
                conn = DriverManager.getConnection(jdbcUrl, user.trim(), password != null ? password : "");
            } else {
                conn = DriverManager.getConnection(jdbcUrl);
            }
            conn.setAutoCommit(true);
            return conn;
        } catch (SQLException e) {
            String sanitizedMsg = sanitizeErrorMessage(e.getMessage());
            throw new SQLException("Database connection failed: " + sanitizedMsg, e.getSQLState(), e.getErrorCode());
        }
    }

    /**
     * Creates a dynamic proxy around the physical connection.
     * Calling close() recycles the connection back to the pool.
     */
    private static Connection createPooledProxy(final Connection physical) {
        return (Connection) Proxy.newProxyInstance(
            DatabaseConnection.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            new InvocationHandler() {
                private boolean closed = false;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String methodName = method.getName();

                    if ("close".equals(methodName)) {
                        if (!closed) {
                            closed = true;
                            recyclePhysicalConnection(physical);
                        }
                        return null;
                    }

                    if ("isClosed".equals(methodName)) {
                        return closed || physical.isClosed();
                    }

                    if (closed) {
                        throw new SQLException("Connection has already been closed.");
                    }

                    try {
                        return method.invoke(physical, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
            }
        );
    }

    /**
     * Recycles a physical connection back into the pool.
     */
    private static void recyclePhysicalConnection(Connection physical) {
        if (physical == null) return;
        try {
            if (!physical.isClosed() && physical.isValid(1)) {
                if (!physical.getAutoCommit()) {
                    physical.rollback();
                    physical.setAutoCommit(true);
                }
                physical.clearWarnings();

                if (!pool.offer(physical)) {
                    // Pool is full, close physical connection
                    closeQuietly(physical);
                    createdConnections.decrementAndGet();
                }
            } else {
                closeQuietly(physical);
                createdConnections.decrementAndGet();
            }
        } catch (Exception e) {
            closeQuietly(physical);
            createdConnections.decrementAndGet();
        }
    }

    /**
     * Shuts down the connection pool, closing all physical connections.
     */
    public static void shutdownPool() {
        synchronized (poolLock) {
            while (!pool.isEmpty()) {
                Connection c = pool.poll();
                closeQuietly(c);
            }
            createdConnections.set(0);
            poolInitialized = false;
        }
    }

    /**
     * Checks if the required database environment variables are configured.
     */
    public static boolean isConfigured() {
        String url = getEnvValue("SUPABASE_DB_URL");
        return url != null && !url.trim().isEmpty();
    }

    /**
     * Tests connectivity to Supabase PostgreSQL using a lightweight validation query.
     *
     * @return boolean true if connection test succeeded
     */
    public static boolean testConnection() {
        if (!isConfigured()) {
            return false;
        }
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(3);
        } catch (Exception e) {
            System.err.println("[PowerTrack Database] Health check failed: " + sanitizeErrorMessage(e.getMessage()));
            return false;
        }
    }

    /**
     * Formats URL to ensure valid JDBC format and SSL mode for Supabase.
     */
    public static String formatJdbcUrl(String raw) {
        String url = raw.trim();

        if (url.startsWith("postgresql://")) {
            url = "jdbc:" + url;
        } else if (url.startsWith("postgres://")) {
            url = "jdbc:postgresql://" + url.substring("postgres://".length());
        } else if (!url.startsWith("jdbc:postgresql://") && !url.startsWith("jdbc:")) {
            url = "jdbc:postgresql://" + url;
        }

        if (!url.toLowerCase().contains("sslmode=")) {
            if (url.contains("?")) {
                url = url + "&sslmode=require";
            } else {
                url = url + "?sslmode=require";
            }
        }

        return url;
    }

    /**
     * Safely reads environment variable, falling back to local .env file.
     */
    public static String getEnvValue(String key) {
        String value = System.getenv(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        if (dotEnvCache == null) {
            synchronized (DatabaseConnection.class) {
                if (dotEnvCache == null) {
                    dotEnvCache = loadDotEnv();
                }
            }
        }

        return dotEnvCache != null ? dotEnvCache.get(key) : null;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        String[] candidates = new String[] {
            ".env",
            "backend/.env",
            "../.env",
            "../backend/.env"
        };

        for (String path : candidates) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            String k = line.substring(0, eq).trim();
                            String v = line.substring(eq + 1).trim();

                            if (k.startsWith("$env:")) {
                                k = k.substring("$env:".length()).trim();
                            } else if (k.toLowerCase().startsWith("export ")) {
                                k = k.substring("export ".length()).trim();
                            } else if (k.toLowerCase().startsWith("set ")) {
                                k = k.substring("set ".length()).trim();
                            }

                            map.put(k, v);
                        }
                    }
                    boolean hasPlaceholder = "your_supabase_password_here".equals(map.get("SUPABASE_DB_PASSWORD"))
                            || "postgres.xxxxxxxxxxxxxxxxxxxx".equals(map.get("SUPABASE_DB_USER"));
                    if (!hasPlaceholder && map.containsKey("SUPABASE_DB_URL")) {
                        break;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return map;
    }

    public static String sanitizeErrorMessage(String rawMessage) {
        if (rawMessage == null) return "Unknown database error";
        String pass = getEnvValue("SUPABASE_DB_PASSWORD");
        if (pass != null && !pass.isEmpty() && rawMessage.contains(pass)) {
            return rawMessage.replace(pass, "********");
        }
        return rawMessage;
    }

    public static void closeQuietly(AutoCloseable... closeables) {
        if (closeables == null) return;
        for (AutoCloseable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static boolean hasColumn(java.sql.ResultSetMetaData meta, String colName) {
        if (meta == null || colName == null) return false;
        try {
            int count = meta.getColumnCount();
            for (int i = 1; i <= count; i++) {
                if (colName.equalsIgnoreCase(meta.getColumnName(i)) || colName.equalsIgnoreCase(meta.getColumnLabel(i))) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    /**
     * Checks if a column exists in a database table with thread-safe caching.
     */
    public static boolean columnExists(Connection conn, String tableName, String columnName) {
        if (tableName == null || columnName == null) return false;
        String cacheKey = tableName.toLowerCase() + "." + columnName.toLowerCase();
        Boolean cached = columnExistsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (conn == null) return false;

        boolean exists = false;
        try {
            java.sql.DatabaseMetaData meta = conn.getMetaData();
            try (java.sql.ResultSet rs = meta.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
                if (rs.next()) {
                    exists = true;
                }
            }
            if (!exists) {
                try (java.sql.ResultSet rs = meta.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
                    if (rs.next()) {
                        exists = true;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        columnExistsCache.put(cacheKey, exists);
        return exists;
    }

    /**
     * Checks if a column's data type in a database table is boolean with thread-safe caching.
     */
    public static boolean isColumnTypeBoolean(Connection conn, String tableName, String columnName) {
        if (tableName == null || columnName == null) return false;
        String cacheKey = tableName.toLowerCase() + "." + columnName.toLowerCase();
        Boolean cached = isBooleanColumnCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (conn == null) return false;

        boolean isBool = false;
        try {
            java.sql.DatabaseMetaData meta = conn.getMetaData();
            try (java.sql.ResultSet rs = meta.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
                if (rs.next()) {
                    String type = rs.getString("TYPE_NAME");
                    isBool = type != null && (type.equalsIgnoreCase("bool") || type.equalsIgnoreCase("boolean"));
                }
            }
            if (!isBool) {
                try (java.sql.ResultSet rs = meta.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
                    if (rs.next()) {
                        String type = rs.getString("TYPE_NAME");
                        isBool = type != null && (type.equalsIgnoreCase("bool") || type.equalsIgnoreCase("boolean"));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        isBooleanColumnCache.put(cacheKey, isBool);
        return isBool;
    }
}
