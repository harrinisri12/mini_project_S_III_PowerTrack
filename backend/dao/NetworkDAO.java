package dao;

import database.DatabaseConnection;
import model.NetworkConnection;
import model.NetworkNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'network_nodes' and 'network_connections' tables.
 */
public class NetworkDAO {

    /**
     * Loads all grid network nodes from Supabase.
     */
    public List<NetworkNode> getAllNodes() throws SQLException {
        List<NetworkNode> list = new ArrayList<>();
        String sql = "SELECT * FROM network_nodes ORDER BY node_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            boolean hasArea = DatabaseConnection.hasColumn(meta, "grid_locality_area");
            boolean hasActive = DatabaseConnection.hasColumn(meta, "is_active");

            while (rs.next()) {
                String id = rs.getString("node_id");
                String name = rs.getString("node_name");
                String type = rs.getString("node_type");
                String area = hasArea ? rs.getString("grid_locality_area") : null;
                boolean active = hasActive ? rs.getBoolean("is_active") : true;

                NetworkNode node = new NetworkNode(id, name, type, area, active);
                list.add(node);
            }
        }
        return list;
    }

    /**
     * Loads all topology connections from Supabase.
     */
    public List<NetworkConnection> getAllConnections() throws SQLException {
        List<NetworkConnection> list = new ArrayList<>();
        String sql = "SELECT * FROM network_connections ORDER BY connection_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            boolean hasType = DatabaseConnection.hasColumn(meta, "connection_type");

            while (rs.next()) {
                int id = rs.getInt("connection_id");
                String src = rs.getString("source_node_id");
                String dst = rs.getString("destination_node_id");
                String type = hasType ? rs.getString("connection_type") : "TRANSMISSION";

                list.add(new NetworkConnection(id, src, dst, type));
            }
        }
        return list;
    }

    /**
     * Updates operating status of a network node if the is_active column exists.
     */
    public void updateNodeStatus(String nodeId, boolean isActive, Connection externalConn) throws SQLException {
        if (nodeId == null || nodeId.trim().isEmpty()) return;

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
                stmt.setString(2, nodeId.trim().toUpperCase());
                stmt.executeUpdate();
            }
        } finally {
            if (closeConn) {
                DatabaseConnection.closeQuietly(conn);
            }
        }
    }
}
