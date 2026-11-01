package dao;

import database.DatabaseConnection;
import model.Technician;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'technicians' table.
 *
 * Requirements satisfied:
 * - Specific column projection (no SELECT *).
 * - Proper try-with-resources resource cleanup.
 * - Thread-safe caching with proper invalidation.
 */
public class TechnicianDAO {

    private static volatile List<Technician> cachedTechnicians = null;
    private static final Object cacheLock = new Object();

    /**
     * Loads all field technicians from Supabase.
     */
    public List<Technician> getAllTechnicians() throws SQLException {
        List<Technician> cached = cachedTechnicians;
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        synchronized (cacheLock) {
            if (cachedTechnicians != null) {
                return new ArrayList<>(cachedTechnicians);
            }

            List<Technician> list = new ArrayList<>();

            try (Connection conn = DatabaseConnection.getConnection()) {
                boolean hasPhone = DatabaseConnection.columnExists(conn, "technicians", "phone");
                boolean hasEmail = DatabaseConnection.columnExists(conn, "technicians", "email");
                boolean hasArea = DatabaseConnection.columnExists(conn, "technicians", "assigned_area");

                StringBuilder sql = new StringBuilder("SELECT technician_id, technician_name, specialization, availability_status");
                if (hasPhone) sql.append(", phone");
                if (hasEmail) sql.append(", email");
                if (hasArea) sql.append(", assigned_area");
                sql.append(" FROM technicians ORDER BY technician_id ASC");

                try (PreparedStatement stmt = conn.prepareStatement(sql.toString());
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        int id = rs.getInt("technician_id");
                        String name = rs.getString("technician_name");
                        String spec = rs.getString("specialization");

                        boolean avail = true;
                        Object availObj = rs.getObject("availability_status");
                        if (availObj != null) {
                            if (availObj instanceof Boolean) {
                                avail = (Boolean) availObj;
                            } else {
                                String val = availObj.toString().trim();
                                avail = "true".equalsIgnoreCase(val) || "AVAILABLE".equalsIgnoreCase(val) || "t".equalsIgnoreCase(val);
                            }
                        }

                        String phone = hasPhone ? rs.getString("phone") : null;
                        String email = hasEmail ? rs.getString("email") : null;
                        String area = hasArea ? rs.getString("assigned_area") : null;

                        Technician t = new Technician(id, name, spec, avail, phone, email, area, 0);
                        list.add(t);
                    }
                }
            }

            cachedTechnicians = list;
            return new ArrayList<>(list);
        }
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

            // Update in-memory cache
            synchronized (cacheLock) {
                if (cachedTechnicians != null) {
                    for (Technician t : cachedTechnicians) {
                        if (t.getTechnicianId() == technicianId) {
                            t.setAvailable(available);
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

            // Update in-memory cache
            synchronized (cacheLock) {
                if (cachedTechnicians != null) {
                    for (Technician t : cachedTechnicians) {
                        if (t.getName().equalsIgnoreCase(name.trim())) {
                            t.setAvailable(available);
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
     * Explicitly invalidates technician cache.
     */
    public static void invalidateCache() {
        synchronized (cacheLock) {
            cachedTechnicians = null;
        }
    }
}
