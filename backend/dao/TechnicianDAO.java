package dao;

import database.DatabaseConnection;
import model.Technician;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'technicians' table.
 */
public class TechnicianDAO {

    /**
     * Loads all field technicians from Supabase.
     */
    public List<Technician> getAllTechnicians() throws SQLException {
        List<Technician> list = new ArrayList<>();
        String sql = "SELECT * FROM technicians ORDER BY technician_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            boolean hasAvail = DatabaseConnection.hasColumn(meta, "availability_status");

            while (rs.next()) {
                int id = rs.getInt("technician_id");
                String name = rs.getString("technician_name");
                String spec = rs.getString("specialization");
                boolean avail = true;
                if (hasAvail) {
                    String val = rs.getString("availability_status");
                    if (val != null) {
                        val = val.trim();
                        avail = "true".equalsIgnoreCase(val) || "AVAILABLE".equalsIgnoreCase(val) || "t".equalsIgnoreCase(val) || "1".equalsIgnoreCase(val);
                    }
                }

                Technician t = new Technician(id, name, spec);
                t.setAvailable(avail);
                list.add(t);
            }
        }
        return list;
    }

    /**
     * Updates technician availability by technician ID.
     */
    public void updateAvailability(int technicianId, boolean available, Connection externalConn) throws SQLException {
        boolean closeConn = false;
        Connection conn = externalConn;

        try {
            if (conn == null) {
                conn = DatabaseConnection.getConnection();
                closeConn = true;
            }

            if (!DatabaseConnection.columnExists(conn, "technicians", "availability_status")) {
                return;
            }

            boolean isBool = DatabaseConnection.isColumnTypeBoolean(conn, "technicians", "availability_status");
            String sql = "UPDATE technicians SET availability_status = ? WHERE technician_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (isBool) {
                    stmt.setBoolean(1, available);
                } else {
                    stmt.setString(1, available ? "true" : "false");
                }
                stmt.setInt(2, technicianId);
                stmt.executeUpdate();
            }
        } finally {
            if (closeConn) {
                DatabaseConnection.closeQuietly(conn);
            }
        }
    }

    /**
     * Updates technician availability by technician name.
     */
    public void updateAvailabilityByName(String name, boolean available, Connection externalConn) throws SQLException {
        if (name == null || name.trim().isEmpty()) return;

        boolean closeConn = false;
        Connection conn = externalConn;

        try {
            if (conn == null) {
                conn = DatabaseConnection.getConnection();
                closeConn = true;
            }

            if (!DatabaseConnection.columnExists(conn, "technicians", "availability_status")) {
                return;
            }

            boolean isBool = DatabaseConnection.isColumnTypeBoolean(conn, "technicians", "availability_status");
            String sql = "UPDATE technicians SET availability_status = ? WHERE technician_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (isBool) {
                    stmt.setBoolean(1, available);
                } else {
                    stmt.setString(1, available ? "true" : "false");
                }
                stmt.setString(2, name.trim());
                stmt.executeUpdate();
            }
        } finally {
            if (closeConn) {
                DatabaseConnection.closeQuietly(conn);
            }
        }
    }
}
