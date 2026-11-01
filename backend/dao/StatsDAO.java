package dao;

import database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object for calculating live dashboard system statistics
 * directly from database records in Supabase PostgreSQL using optimized aggregate queries.
 */
public class StatsDAO {

    public static class SystemStats {
        public final int totalConsumers;
        public final int powerAvailable;
        public final int powerOutage;
        public final int activeOutages;
        public final int assignedOutages;
        public final int inProgressOutages;
        public final int restoredOutages;
        public final int activeNodes;
        public final int availableTechnicians;

        public SystemStats(
            int totalConsumers,
            int powerAvailable,
            int activeOutages,
            int assignedOutages,
            int inProgressOutages,
            int restoredOutages,
            int activeNodes,
            int availableTechnicians
        ) {
            this.totalConsumers = totalConsumers;
            this.powerAvailable = powerAvailable;
            this.powerOutage = Math.max(0, totalConsumers - powerAvailable);
            this.activeOutages = activeOutages;
            this.assignedOutages = assignedOutages;
            this.inProgressOutages = inProgressOutages;
            this.restoredOutages = restoredOutages;
            this.activeNodes = activeNodes;
            this.availableTechnicians = availableTechnicians;
        }

        public String toJson() {
            return "{"
                + "\"totalConsumers\":" + totalConsumers + ","
                + "\"powerAvailable\":" + powerAvailable + ","
                + "\"powerOutage\":" + powerOutage + ","
                + "\"activeOutages\":" + activeOutages + ","
                + "\"assignedOutages\":" + assignedOutages + ","
                + "\"inProgressOutages\":" + inProgressOutages + ","
                + "\"restoredOutages\":" + restoredOutages + ","
                + "\"activeNodes\":" + activeNodes + ","
                + "\"availableTechnicians\":" + availableTechnicians
                + "}";
        }
    }

    /**
     * Calculates grid statistics directly from database tables using fast conditional aggregates.
     */
    public SystemStats getSystemStats() throws SQLException {
        int totalConsumers = 0;
        int powerAvailable = 0;
        int activeOutages = 0;
        int assignedOutages = 0;
        int inProgressOutages = 0;
        int restoredOutages = 0;
        int activeNodes = 0;
        int availableTechnicians = 0;

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean hasPowerStatus = DatabaseConnection.columnExists(conn, "consumers", "power_status");
            boolean hasActiveNode = DatabaseConnection.columnExists(conn, "network_nodes", "is_active");
            boolean hasAvailTech = DatabaseConnection.columnExists(conn, "technicians", "availability_status");

            // 1. Consumers aggregate (Total and Powered in 1 query)
            String sqlConsumers = hasPowerStatus
                ? "SELECT COUNT(*) AS total, " +
                  "COUNT(CASE WHEN power_status::text = 'true' OR power_status::text = 'POWER ON' OR power_status::text = 't' THEN 1 END) AS powered " +
                  "FROM consumers"
                : "SELECT COUNT(*) AS total, COUNT(*) AS powered FROM consumers";

            try (PreparedStatement stmt = conn.prepareStatement(sqlConsumers);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    totalConsumers = rs.getInt("total");
                    powerAvailable = rs.getInt("powered");
                }
            }

            // 2. Outages aggregates (active, assigned, in-progress, restored in 1 query)
            String sqlOutages = "SELECT " +
                                "COUNT(CASE WHEN status != 'RESTORED' THEN 1 END) AS active_count, " +
                                "COUNT(CASE WHEN status = 'ASSIGNED' OR (technician_assignments_count > 0 AND status = 'ACTIVE') THEN 1 END) AS assigned_count, " +
                                "COUNT(CASE WHEN status = 'IN PROGRESS' THEN 1 END) AS in_progress_count, " +
                                "COUNT(CASE WHEN status = 'RESTORED' THEN 1 END) AS restored_count " +
                                "FROM (" +
                                "   SELECT o.status, " +
                                "   (SELECT COUNT(*) FROM technician_assignments ta WHERE ta.outage_id = o.outage_id) AS technician_assignments_count " +
                                "   FROM outages o" +
                                ") sub";
            try (PreparedStatement stmt = conn.prepareStatement(sqlOutages);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    activeOutages = rs.getInt("active_count");
                    assignedOutages = rs.getInt("assigned_count");
                    inProgressOutages = rs.getInt("in_progress_count");
                    restoredOutages = rs.getInt("restored_count");
                }
            } catch (SQLException fallback) {
                // Simpler fallback query if subquery fails
                String simpleSql = "SELECT " +
                                   "COUNT(CASE WHEN status != 'RESTORED' THEN 1 END) AS active_count, " +
                                   "COUNT(CASE WHEN status = 'IN PROGRESS' THEN 1 END) AS in_progress_count, " +
                                   "COUNT(CASE WHEN status = 'RESTORED' THEN 1 END) AS restored_count " +
                                   "FROM outages";
                try (PreparedStatement stmt2 = conn.prepareStatement(simpleSql);
                     ResultSet rs2 = stmt2.executeQuery()) {
                    if (rs2.next()) {
                        activeOutages = rs2.getInt("active_count");
                        inProgressOutages = rs2.getInt("in_progress_count");
                        restoredOutages = rs2.getInt("restored_count");
                    }
                }
            }

            // 3. Active network nodes in 1 query
            String sqlNodes = hasActiveNode
                ? "SELECT COUNT(*) FROM network_nodes WHERE is_active = TRUE"
                : "SELECT COUNT(*) FROM network_nodes";

            try (PreparedStatement stmt = conn.prepareStatement(sqlNodes);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    activeNodes = rs.getInt(1);
                }
            }

            // 4. Available technicians in 1 query
            String sqlTech = hasAvailTech
                ? "SELECT COUNT(*) FROM technicians WHERE availability_status::text = 'true' OR availability_status::text = 'AVAILABLE' OR availability_status::text = 't'"
                : "SELECT COUNT(*) FROM technicians";

            try (PreparedStatement stmt = conn.prepareStatement(sqlTech);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    availableTechnicians = rs.getInt(1);
                }
            }
        }

        return new SystemStats(
            totalConsumers,
            powerAvailable,
            activeOutages,
            assignedOutages,
            inProgressOutages,
            restoredOutages,
            activeNodes,
            availableTechnicians
        );
    }
}
