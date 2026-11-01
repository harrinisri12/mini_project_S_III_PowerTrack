package database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Reusable JDBC Database Connection Utility for Supabase PostgreSQL.
 * Securely reads SUPABASE_DB_URL, SUPABASE_DB_USER, and SUPABASE_DB_PASSWORD
 * from environment variables or a local .env configuration file.
 */
public class DatabaseConnection {

    private static final String DRIVER_CLASS = "org.postgresql.Driver";

    private static boolean driverLoaded = false;
    private static Map<String, String> dotEnvCache = null;

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        try {
            Class.forName(DRIVER_CLASS);
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[PowerTrack Database] PostgreSQL JDBC driver class not found on classpath: " + DRIVER_CLASS);
            driverLoaded = false;
        }
    }

    /**
     * Obtains a standard JDBC connection to Supabase PostgreSQL.
     *
     * @return Connection active database connection
     * @throws SQLException if connection fails or credentials are missing
     */
    public static Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            throw new SQLException("PostgreSQL JDBC driver (" + DRIVER_CLASS + ") is not loaded. Ensure the JDBC jar is on the classpath.");
        }

        String rawUrl = getEnvValue("SUPABASE_DB_URL");
        String user = getEnvValue("SUPABASE_DB_USER");
        String password = getEnvValue("SUPABASE_DB_PASSWORD");

        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new SQLException("SUPABASE_DB_URL environment variable is not set. Please configure Supabase connection parameters.");
        }

        String jdbcUrl = formatJdbcUrl(rawUrl.trim());

        try {
            Connection conn;
            if (user != null && !user.trim().isEmpty()) {
                conn = DriverManager.getConnection(jdbcUrl, user.trim(), password != null ? password : "");
            } else {
                conn = DriverManager.getConnection(jdbcUrl);
            }
            return conn;
        } catch (SQLException e) {
            String sanitizedMsg = sanitizeErrorMessage(e.getMessage());
            throw new SQLException("Database connection failed: " + sanitizedMsg, e.getSQLState(), e.getErrorCode());
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
     * Tests connectivity to Supabase PostgreSQL.
     *
     * @return boolean true if connection test succeeded
     */
    public static boolean testConnection() {
        if (!isConfigured()) {
            return false;
        }
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(5);
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

        // Convert standard postgres:// or postgresql:// URI format to jdbc:postgresql://
        if (url.startsWith("postgresql://")) {
            url = "jdbc:" + url;
        } else if (url.startsWith("postgres://")) {
            url = "jdbc:postgresql://" + url.substring("postgres://".length());
        } else if (!url.startsWith("jdbc:postgresql://") && !url.startsWith("jdbc:")) {
            url = "jdbc:postgresql://" + url;
        }

        // Supabase requires SSL. Ensure sslmode=require is configured if not present
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
        // 1. Check system environment variables
        String value = System.getenv(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        // 2. Check Java system properties (e.g. -DSUPABASE_DB_URL=...)
        value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        // 3. Fallback to reading from .env file
        if (dotEnvCache == null) {
            dotEnvCache = loadDotEnv();
        }

        return dotEnvCache.get(key);
    }

    /**
     * Loads key-value pairs from .env in working directory, backend, or parent directory.
     */
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

                            // Strip PowerShell $env: prefix if present
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

    /**
     * Strips sensitive strings such as passwords from error messages.
     */
    public static String sanitizeErrorMessage(String rawMessage) {
        if (rawMessage == null) return "Unknown database error";
        String pass = getEnvValue("SUPABASE_DB_PASSWORD");
        if (pass != null && !pass.isEmpty() && rawMessage.contains(pass)) {
            return rawMessage.replace(pass, "********");
        }
        return rawMessage;
    }

    /**
     * Quietly closes JDBC resources without throwing checked exceptions.
     */
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

    /**
     * Checks if a column exists in a ResultSetMetaData by column name.
     */
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
     * Checks if a column exists in a database table.
     */
    public static boolean columnExists(Connection conn, String tableName, String columnName) {
        if (conn == null || tableName == null || columnName == null) return false;
        try {
            java.sql.DatabaseMetaData meta = conn.getMetaData();
            try (java.sql.ResultSet rs = meta.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
                if (rs.next()) return true;
            }
            try (java.sql.ResultSet rs = meta.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
                if (rs.next()) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Checks if a column's data type in a database table is boolean.
     */
    public static boolean isColumnTypeBoolean(Connection conn, String tableName, String columnName) {
        if (conn == null || tableName == null || columnName == null) return false;
        try {
            java.sql.DatabaseMetaData meta = conn.getMetaData();
            try (java.sql.ResultSet rs = meta.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
                if (rs.next()) {
                    String type = rs.getString("TYPE_NAME");
                    return type != null && (type.equalsIgnoreCase("bool") || type.equalsIgnoreCase("boolean"));
                }
            }
            try (java.sql.ResultSet rs = meta.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
                if (rs.next()) {
                    String type = rs.getString("TYPE_NAME");
                    return type != null && (type.equalsIgnoreCase("bool") || type.equalsIgnoreCase("boolean"));
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
