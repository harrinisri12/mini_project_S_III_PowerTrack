package dao;

import database.DatabaseConnection;
import model.Consumer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'consumers' table.
 */
public class ConsumerDAO {

    /**
     * Loads all registered consumers from Supabase database.
     */
    public List<Consumer> getAllConsumers() throws SQLException {
        List<Consumer> list = new ArrayList<>();
        String sql = "SELECT * FROM consumers ORDER BY consumer_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            boolean hasConnectedLoad = DatabaseConnection.hasColumn(meta, "connected_load_kw");
            boolean hasPowerStatus = DatabaseConnection.hasColumn(meta, "power_status");

            while (rs.next()) {
                int id = rs.getInt("consumer_id");
                String name = rs.getString("account_name");
                String category = rs.getString("category_type");
                String criticality = rs.getString("criticality_level");
                String area = rs.getString("grid_locality_area");
                double load = hasConnectedLoad ? rs.getDouble("connected_load_kw") : 5.0;
                boolean power = true;
                if (hasPowerStatus) {
                    String statusVal = rs.getString("power_status");
                    if (statusVal != null) {
                        statusVal = statusVal.trim();
                        power = "POWER ON".equalsIgnoreCase(statusVal) || "true".equalsIgnoreCase(statusVal) || "t".equalsIgnoreCase(statusVal) || "1".equalsIgnoreCase(statusVal);
                    }
                }

                Consumer c = new Consumer(id, name, area, category, criticality, load, power);
                list.add(c);
            }
        }
        return list;
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
                    if (isBool) {
                        stmt.setBoolean(1, powerStatus);
                    } else {
                        stmt.setString(1, powerStatus ? "POWER ON" : "OUTAGE");
                    }
                    stmt.setString(2, area);
                    stmt.addBatch();
                }
                stmt.executeBatch();
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
                    stmt.setString(i + 1, areas.get(i));
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
}
