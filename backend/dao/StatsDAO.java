package dao;

import database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object for calculating live dashboard system statistics
 * directly from actual database records in Supabase PostgreSQL.
 */
public class StatsDAO {

    public static class SystemStats {
        public final int totalConsumers;
        public final int powerAvailable;
        public final int powerOutage;
        public final int activeOutages;
        public final int restoredOutages;
        public final int activeNodes;
        public final int availableTechnicians;

        public SystemStats(
            int totalConsumers,
            int powerAvailable,
            int activeOutages,
            int restoredOutages,
            int activeNodes,
            int availableTechnicians
        ) {
            this.totalConsumers = totalConsumers;
            this.powerAvailable = powerAvailable;
            this.powerOutage = Math.max(0, totalConsumers - powerAvailable);
            this.activeOutages = activeOutages;
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
                + "\"restoredOutages\":" + restoredOutages + ","
                + "\"activeNodes\":" + activeNodes + ","
                + "\"availableTechnicians\":" + availableTechnicians
                + "}";
        }
    }

    /**
     * Calculates grid statistics directly from database tables.
     */
    public SystemStats getSystemStats() throws SQLException {
        int totalConsumers = 0;
        int powerAvailable = 0;
        int activeOutages = 0;
        int restoredOutages = 0;
        int activeNodes = 0;
        int availableTechnicians = 0;

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean hasPowerStatus = DatabaseConnection.columnExists(conn, "consumers", "power_status");
            boolean hasActiveNode = DatabaseConnection.columnExists(conn, "network_nodes", "is_active");
            boolean hasAvailTech = DatabaseConnection.columnExists(conn, "technicians", "availability_status");

            // 1. Consumers count & powered count
            String sqlConsumers = hasPowerStatus
                ? "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE power_status::text = 'true' OR power_status::text = 'POWER ON' OR power_status::text = 't') AS powered FROM consumers"
                : "SELECT COUNT(*) AS total, COUNT(*) AS powered FROM consumers";

            try (PreparedStatement stmt = conn.prepareStatement(sqlConsumers);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    totalConsumers = rs.getInt("total");
                    powerAvailable = rs.getInt("powered");
                }
            }

            // 2. Outages count (active vs restored)
            String sqlOutages = "SELECT " +
                                "COUNT(*) FILTER (WHERE status != 'RESTORED') AS active_count, " +
                                "COUNT(*) FILTER (WHERE status = 'RESTORED') AS restored_count " +
                                "FROM outages";
            try (PreparedStatement stmt = conn.prepareStatement(sqlOutages);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    activeOutages = rs.getInt("active_count");
                    restoredOutages = rs.getInt("restored_count");
                }
            }

            // 3. Active network nodes
            String sqlNodes = hasActiveNode
                ? "SELECT COUNT(*) FROM network_nodes WHERE is_active = TRUE"
                : "SELECT GREATEST(0, (SELECT COUNT(*) FROM network_nodes) - (SELECT COUNT(DISTINCT fault_component_id) FROM outages WHERE status != 'RESTORED'))";

            try (PreparedStatement stmt = conn.prepareStatement(sqlNodes);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    activeNodes = rs.getInt(1);
                }
            }

            // 4. Available technicians
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
            restoredOutages,
            activeNodes,
            availableTechnicians
        );
    }
}
