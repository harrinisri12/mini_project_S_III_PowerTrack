package dao;

import database.DatabaseConnection;
import model.Consumer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'consumers' table.
 *
 * Requirements satisfied:
 * - Specific column projection (no SELECT *).
 * - Thread-safe caching with automatic invalidation on power status updates.
 * - Batch update operations for performance.
 * - Proper try-with-resources for JDBC resource management.
 */
public class ConsumerDAO {

    private static volatile List<Consumer> cachedConsumers = null;
    private static final Object cacheLock = new Object();

    /**
     * Loads all registered consumers from Supabase database.
     * Reuses cached records if valid.
     */
    public List<Consumer> getAllConsumers() throws SQLException {
        List<Consumer> cached = cachedConsumers;
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        synchronized (cacheLock) {
            if (cachedConsumers != null) {
                return new ArrayList<>(cachedConsumers);
            }

            List<Consumer> list = new ArrayList<>();
            String sql = "SELECT consumer_id, account_name, category_type, criticality_level, " +
                         "grid_locality_area, connected_load_kw, power_status " +
                         "FROM consumers ORDER BY consumer_id ASC";

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    int id = rs.getInt("consumer_id");
                    String name = rs.getString("account_name");
                    String category = rs.getString("category_type");
                    String criticality = rs.getString("criticality_level");
                    String area = rs.getString("grid_locality_area");
                    double load = rs.getDouble("connected_load_kw");
                    if (rs.wasNull()) load = 5.0;

                    boolean power = true;
                    Object powerObj = rs.getObject("power_status");
                    if (powerObj != null) {
                        if (powerObj instanceof Boolean) {
                            power = (Boolean) powerObj;
                        } else {
                            String s = powerObj.toString().trim();
                            power = "true".equalsIgnoreCase(s) || "POWER ON".equalsIgnoreCase(s) || "t".equalsIgnoreCase(s);
                        }
                    }

                    list.add(new Consumer(id, name, area, category, criticality, load, power));
                }
            }

            cachedConsumers = list;
            return new ArrayList<>(list);
        }
    }

    /**
     * Updates power availability status for consumers in specified locality areas.
     */
    public void updatePowerStatusByAreas(List<String> areas, boolean powerStatus, Connection externalConn) throws SQLException {
        if (areas == null || areas.isEmpty()) return;

        boolean closeConn = false;
        Connection conn = externalConn;

        try {
            if (conn == null) {
                conn = DatabaseConnection.getConnection();
                closeConn = true;
            }

            if (!DatabaseConnection.columnExists(conn, "consumers", "power_status")) {
                return;
            }

            boolean isBool = DatabaseConnection.isColumnTypeBoolean(conn, "consumers", "power_status");
            String sql = "UPDATE consumers SET power_status = ? WHERE grid_locality_area = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (String area : areas) {
                    if (area == null || area.trim().isEmpty()) continue;
                    if (isBool) {
                        stmt.setBoolean(1, powerStatus);
                    } else {
                        stmt.setString(1, powerStatus ? "POWER ON" : "OUTAGE");
                    }
                    stmt.setString(2, area.trim());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            // Invalidate/update cache
            synchronized (cacheLock) {
                if (cachedConsumers != null) {
                    for (Consumer c : cachedConsumers) {
                        if (areas.contains(c.getArea())) {
                            c.setPowerAvailable(powerStatus);
                        }
                    }
                }
            }
        } finally {
            if (closeConn) {
                DatabaseConnection.closeQuietly(conn);
            }
        }
    }

    /**
     * Retrieves IDs of consumers located within the given grid locality areas.
     */
    public List<Integer> getConsumerIdsByAreas(List<String> areas, Connection externalConn) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        if (areas == null || areas.isEmpty()) return ids;

        StringBuilder sql = new StringBuilder("SELECT consumer_id FROM consumers WHERE grid_locality_area IN (");
        for (int i = 0; i < areas.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(") ORDER BY consumer_id ASC");

        boolean closeConn = false;
        Connection conn = externalConn;

        try {
            if (conn == null) {
                conn = DatabaseConnection.getConnection();
                closeConn = true;
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < areas.size(); i++) {
                    stmt.setString(i + 1, areas.get(i).trim());
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getInt("consumer_id"));
                    }
                }
            }
        } finally {
            if (closeConn) {
                DatabaseConnection.closeQuietly(conn);
            }
        }
        return ids;
    }

    /**
     * Explicitly invalidates consumer cache.
     */
    public static void invalidateCache() {
        synchronized (cacheLock) {
            cachedConsumers = null;
        }
    }
}
