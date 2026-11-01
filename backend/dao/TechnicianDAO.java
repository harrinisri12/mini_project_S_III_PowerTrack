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
            String sql = "SELECT technician_id, technician_name, specialization, availability_status " +
                         "FROM technicians ORDER BY technician_id ASC";

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
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

                    Technician t = new Technician(id, name, spec);
                    t.setAvailable(avail);
                    list.add(t);
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
