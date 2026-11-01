package dao;

import database.DatabaseConnection;
import model.NetworkConnection;
import model.NetworkNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'network_nodes' and 'network_connections' tables.
 *
 * Requirements satisfied:
 * - Specific column projection (no SELECT *).
 * - Proper try-with-resources resource cleanup.
 * - Thread-safe caching of topology metadata with invalidation on state changes.
 */
public class NetworkDAO {

    // Thread-safe in-memory cache for network topology
    private static volatile List<NetworkNode> cachedNodes = null;
    private static volatile List<NetworkConnection> cachedConnections = null;
    private static final Object cacheLock = new Object();

    /**
     * Loads all grid network nodes from Supabase.
     * Uses cached nodes if available; refreshes from database on cache miss or invalidation.
     */
    public List<NetworkNode> getAllNodes() throws SQLException {
        List<NetworkNode> cached = cachedNodes;
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        synchronized (cacheLock) {
            if (cachedNodes != null) {
                return new ArrayList<>(cachedNodes);
            }

            List<NetworkNode> list = new ArrayList<>();
            String sql = "SELECT node_id, node_name, node_type, grid_locality_area, is_active " +
                         "FROM network_nodes ORDER BY node_id ASC";

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String id = rs.getString("node_id");
                    String name = rs.getString("node_name");
                    String type = rs.getString("node_type");
                    String area = rs.getString("grid_locality_area");
                    boolean active = rs.getBoolean("is_active");

                    list.add(new NetworkNode(id, name, type, area, active));
                }
            }

            cachedNodes = list;
            return new ArrayList<>(list);
        }
    }

    /**
     * Loads all topology connections from Supabase.
     */
    public List<NetworkConnection> getAllConnections() throws SQLException {
        List<NetworkConnection> cached = cachedConnections;
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        synchronized (cacheLock) {
            if (cachedConnections != null) {
                return new ArrayList<>(cachedConnections);
            }

            List<NetworkConnection> list = new ArrayList<>();
            String sql = "SELECT connection_id, source_node_id, destination_node_id, connection_type " +
                         "FROM network_connections ORDER BY connection_id ASC";

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    int id = rs.getInt("connection_id");
                    String src = rs.getString("source_node_id");
                    String dst = rs.getString("destination_node_id");
                    String type = rs.getString("connection_type");

                    list.add(new NetworkConnection(id, src, dst, type != null ? type : "TRANSMISSION"));
                }
            }

            cachedConnections = list;
            return new ArrayList<>(list);
        }
    }

    /**
     * Updates operating status of a network node and updates the local cache.
     */
    public void updateNodeStatus(String nodeId, boolean isActive, Connection externalConn) throws SQLException {
        if (nodeId == null || nodeId.trim().isEmpty()) return;

        String normalizedId = nodeId.trim().toUpperCase();
        boolean closeConn = false;
        Connection conn = externalConn;

        try {
            if (conn == null) {
                conn = DatabaseConnection.getConnection();
                closeConn = true;
            }

            if (!DatabaseConnection.columnExists(conn, "network_nodes", "is_active")) {
                return;
            }

            String sql = "UPDATE network_nodes SET is_active = ? WHERE node_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBoolean(1, isActive);
                stmt.setString(2, normalizedId);
                stmt.executeUpdate();
            }

            // Update cached node in memory if present
            synchronized (cacheLock) {
                if (cachedNodes != null) {
                    for (NetworkNode n : cachedNodes) {
                        if (n.getNodeId().equalsIgnoreCase(normalizedId)) {
                            n.setActive(isActive);
                            break;
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
     * Explicitly invalidates the network topology cache.
     */
    public static void invalidateCache() {
        synchronized (cacheLock) {
            cachedNodes = null;
            cachedConnections = null;
        }
    }
}
