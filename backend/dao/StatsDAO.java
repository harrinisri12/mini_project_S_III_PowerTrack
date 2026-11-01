package dao;

import database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object for calculating live dashboard system statistics
 * directly from database records in Supabase PostgreSQL using optimized aggregate queries.
 *
 * Optimizations:
 * - Single consolidated query using scalar sub-selects (replaces 4 sequential queries).
 * - Short-TTL cache (3 seconds) to prevent redundant queries during rapid dashboard refreshes.
 * - Explicit cache invalidation after write operations.
 */
public class StatsDAO {

    // Short-TTL cache to prevent repeated identical aggregate queries
    private static volatile SystemStats cachedStats = null;
    private static volatile long cachedStatsTimestamp = 0;
    private static final long STATS_CACHE_TTL_MS = 3000; // 3 seconds

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
     * Calculates grid statistics using a single consolidated query with scalar sub-selects,
     * replacing the previous 4-query sequential approach.
     * Results are cached for STATS_CACHE_TTL_MS to avoid redundant queries during rapid refreshes.
     */
    public SystemStats getSystemStats() throws SQLException {
        // Check TTL cache first
        long now = System.currentTimeMillis();
        SystemStats cached = cachedStats;
        if (cached != null && (now - cachedStatsTimestamp) < STATS_CACHE_TTL_MS) {
            return cached;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean hasPowerStatus = DatabaseConnection.columnExists(conn, "consumers", "power_status");
            boolean hasActiveNode = DatabaseConnection.columnExists(conn, "network_nodes", "is_active");
            boolean hasAvailTech = DatabaseConnection.columnExists(conn, "technicians", "availability_status");

            // Build a single consolidated query using scalar sub-selects
            StringBuilder sql = new StringBuilder("SELECT ");

            // Consumer counts
            sql.append("(SELECT COUNT(*) FROM consumers) AS total_consumers, ");
            if (hasPowerStatus) {
                sql.append("(SELECT COUNT(*) FROM consumers WHERE power_status::text IN ('true','POWER ON','t')) AS powered_consumers, ");
            } else {
                sql.append("(SELECT COUNT(*) FROM consumers) AS powered_consumers, ");
            }

            // Outage counts
            sql.append("(SELECT COUNT(*) FROM outages WHERE status != 'RESTORED') AS active_outages, ");
            sql.append("(SELECT COUNT(*) FROM outages WHERE assigned_technician_id IS NOT NULL AND status != 'RESTORED' AND status != 'IN PROGRESS') AS assigned_outages, ");
            sql.append("(SELECT COUNT(*) FROM outages WHERE status = 'IN PROGRESS') AS in_progress_outages, ");
            sql.append("(SELECT COUNT(*) FROM outages WHERE status = 'RESTORED') AS restored_outages, ");

            // Node counts
            if (hasActiveNode) {
                sql.append("(SELECT COUNT(*) FROM network_nodes WHERE is_active = TRUE) AS active_nodes, ");
            } else {
                sql.append("(SELECT COUNT(*) FROM network_nodes) AS active_nodes, ");
            }

            // Technician counts
            if (hasAvailTech) {
                sql.append("(SELECT COUNT(*) FROM technicians WHERE availability_status::text IN ('true','AVAILABLE','t')) AS available_techs");
            } else {
                sql.append("(SELECT COUNT(*) FROM technicians) AS available_techs");
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString());
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    SystemStats result = new SystemStats(
                        rs.getInt("total_consumers"),
                        rs.getInt("powered_consumers"),
                        rs.getInt("active_outages"),
                        rs.getInt("assigned_outages"),
                        rs.getInt("in_progress_outages"),
                        rs.getInt("restored_outages"),
                        rs.getInt("active_nodes"),
                        rs.getInt("available_techs")
                    );
                    cachedStats = result;
                    cachedStatsTimestamp = System.currentTimeMillis();
                    return result;
                }
            }
        }

        return new SystemStats(0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Invalidates the cached stats so the next call re-queries the database.
     * Called after write operations that change outage, consumer, or technician state.
     */
    public static void invalidateCache() {
        cachedStats = null;
        cachedStatsTimestamp = 0;
    }
}
