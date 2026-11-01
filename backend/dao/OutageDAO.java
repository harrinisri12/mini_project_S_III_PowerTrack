package dao;

import database.DatabaseConnection;
import model.Outage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'outages', 'outage_affected_consumers',
 * 'technician_assignments', and 'restoration_history' tables.
 *
 * Requirements satisfied:
 * - Specific column projection (avoids SELECT *).
 * - Filtered queries (getActiveOutages, getHistoricalOutages) avoiding unnecessary full table scans.
 * - Checking for existing active outages on components.
 * - Atomic ACID database transactions with proper rollback and connection recycling.
 * - Automatic cache invalidation across dependent DAOs.
 */
public class OutageDAO {

    /**
     * Base query with LEFT JOIN to resolve technician assignments in a single query,
     * completely eliminating N+1 individual lookups per outage.
     */
    private static final String OUTAGE_BASE_QUERY =
        "SELECT o.outage_id, o.fault_component_id, o.fault_type, o.affected_area, " +
        "o.affected_consumer_count, o.priority, o.status, o.reported_at, o.restored_at, " +
        "o.priority_reason, o.critical_facilities, o.queue_sequence, " +
        "COALESCE(o.assigned_technician_id, ta_latest.technician_id) AS resolved_tech_id, " +
        "COALESCE(o.assigned_technician_name, t.technician_name) AS resolved_tech_name " +
        "FROM outages o " +
        "LEFT JOIN (" +
        "  SELECT DISTINCT ON (outage_id) outage_id, technician_id " +
        "  FROM technician_assignments ORDER BY outage_id, assigned_at DESC" +
        ") ta_latest ON ta_latest.outage_id = o.outage_id " +
        "LEFT JOIN technicians t ON t.technician_id = ta_latest.technician_id";

    /**
     * Loads all outages (active, in-progress, and historical restored) from Supabase.
     */
    public List<Outage> getAllOutages() throws SQLException {
        String sql = OUTAGE_BASE_QUERY + " ORDER BY o.reported_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return mapOutages(rs);
        }
    }

    /**
     * Loads only active/in-progress outages for dashboard performance.
     */
    public List<Outage> getActiveOutages() throws SQLException {
        String sql = OUTAGE_BASE_QUERY + " WHERE o.status != 'RESTORED' ORDER BY o.queue_sequence ASC, o.reported_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return mapOutages(rs);
        }
    }

    /**
     * Loads only completed/restored outages for audit history.
     */
    public List<Outage> getHistoricalOutages() throws SQLException {
        String sql = OUTAGE_BASE_QUERY + " WHERE o.status = 'RESTORED' ORDER BY o.restored_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return mapOutages(rs);
        }
    }

    /**
     * Checks if there is already an active or in-progress outage for the given component.
     */
    public boolean hasActiveOutageForComponent(String componentId) throws SQLException {
        if (componentId == null || componentId.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM outages WHERE fault_component_id = ? AND status != 'RESTORED' LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, componentId.trim().toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<Outage> mapOutages(ResultSet rs) throws SQLException {
        List<Outage> list = new ArrayList<>();
        int fallbackSeq = 1;

        while (rs.next()) {
            int id = rs.getInt("outage_id");
            String comp = rs.getString("fault_component_id");
            String faultType = rs.getString("fault_type");
            String rawAreas = rs.getString("affected_area");
            int consumers = rs.getInt("affected_consumer_count");
            String priority = rs.getString("priority");
            String status = rs.getString("status");

            String reason = rs.getString("priority_reason");
            String rawCrit = rs.getString("critical_facilities");
            int seq = rs.getInt("queue_sequence");
            if (rs.wasNull() || seq == 0) {
                seq = fallbackSeq++;
            }
            Timestamp repAt = rs.getTimestamp("reported_at");
            Timestamp resAt = rs.getTimestamp("restored_at");

            int techId = rs.getInt("resolved_tech_id");
            String techName = rs.getString("resolved_tech_name");

            ArrayList<String> areasList = parseCommaList(rawAreas);
            ArrayList<String> critList = parseCommaList(rawCrit);

            Outage o = new Outage(id, comp, faultType, areasList, consumers);
            o.setPriority(priority);
            o.setStatus(status);
            o.setCriticalFacilities(critList);
            o.setPriorityReason(reason != null ? reason : "");
            o.setQueueSequence(seq);
            o.setReportedAt(repAt);
            o.setRestoredAt(resAt);

            if (techName != null && !techName.trim().isEmpty()) {
                o.setAssignedTechnician(techName);
                o.setAssignedTechnicianId(techId > 0 ? techId : null);
            } else {
                o.setAssignedTechnician("NOT ASSIGNED");
                o.setAssignedTechnicianId(null);
            }

            list.add(o);
        }
        return list;
    }

    public int getNextOutageId() throws SQLException {
        String sql = "SELECT COALESCE(MAX(outage_id), 1000) + 1 FROM outages";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 1001;
    }

    /**
     * Persists a newly simulated outage using an atomic database transaction:
     * 1. Inserts record into 'outages'
     * 2. Associates affected consumers in 'outage_affected_consumers'
     * 3. Sets power_status = FALSE on affected consumer records
     * 4. Sets is_active = FALSE on failed network node
     */
    public synchronized void insertOutageTransaction(Outage outage, List<Integer> affectedConsumerIds) throws SQLException {
        if (outage == null) return;

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            boolean hasReason = DatabaseConnection.columnExists(conn, "outages", "priority_reason");
            boolean hasCrit = DatabaseConnection.columnExists(conn, "outages", "critical_facilities");
            boolean hasSeq = DatabaseConnection.columnExists(conn, "outages", "queue_sequence");

            StringBuilder sql = new StringBuilder("INSERT INTO outages (outage_id, fault_component_id, fault_type, affected_area, affected_consumer_count, priority, status, reported_at");
            if (hasReason) sql.append(", priority_reason");
            if (hasCrit) sql.append(", critical_facilities");
            if (hasSeq) sql.append(", queue_sequence");
            sql.append(") VALUES (?, ?, ?, ?, ?, ?, ?, NOW()");
            if (hasReason) sql.append(", ?");
            if (hasCrit) sql.append(", ?");
            if (hasSeq) sql.append(", ?");
            sql.append(") ON CONFLICT (outage_id) DO UPDATE SET status = EXCLUDED.status, priority = EXCLUDED.priority");

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                int p = 1;
                stmt.setInt(p++, outage.getOutageId());
                stmt.setString(p++, outage.getFailedComponent());
                stmt.setString(p++, outage.getFaultType());
                stmt.setString(p++, String.join(", ", outage.getAffectedAreas()));
                stmt.setInt(p++, outage.getAffectedConsumers());
                stmt.setString(p++, outage.getPriority());
                stmt.setString(p++, outage.getStatus());
                if (hasReason) stmt.setString(p++, outage.getPriorityReason());
                if (hasCrit) stmt.setString(p++, outage.getCriticalFacilities() != null ? String.join(", ", outage.getCriticalFacilities()) : "");
                if (hasSeq) stmt.setInt(p++, outage.getQueueSequence());
                stmt.executeUpdate();
            }

            // 2. Associate affected consumers & update consumer power
            if (affectedConsumerIds != null && !affectedConsumerIds.isEmpty()) {
                String insertJunctionSql =
                    "INSERT INTO outage_affected_consumers (outage_id, consumer_id) " +
                    "VALUES (?, ?) ON CONFLICT (outage_id, consumer_id) DO NOTHING";

                try (PreparedStatement junctionStmt = conn.prepareStatement(insertJunctionSql)) {
                    for (Integer cId : affectedConsumerIds) {
                        junctionStmt.setInt(1, outage.getOutageId());
                        junctionStmt.setInt(2, cId);
                        junctionStmt.addBatch();
                    }
                    junctionStmt.executeBatch();
                }

                if (DatabaseConnection.columnExists(conn, "consumers", "power_status")) {
                    boolean isBool = DatabaseConnection.isColumnTypeBoolean(conn, "consumers", "power_status");
                    String updateConsumersSql = isBool
                        ? "UPDATE consumers SET power_status = FALSE WHERE consumer_id = ?"
                        : "UPDATE consumers SET power_status = 'OUTAGE' WHERE consumer_id = ?";
                    try (PreparedStatement consumerStmt = conn.prepareStatement(updateConsumersSql)) {
                        for (Integer cId : affectedConsumerIds) {
                            consumerStmt.setInt(1, cId);
                            consumerStmt.addBatch();
                        }
                        consumerStmt.executeBatch();
                    }
                }
            }

            // 3. Mark failed node inactive
            if (DatabaseConnection.columnExists(conn, "network_nodes", "is_active")) {
                String updateNodeSql = "UPDATE network_nodes SET is_active = FALSE WHERE node_id = ?";
                try (PreparedStatement nodeStmt = conn.prepareStatement(updateNodeSql)) {
                    nodeStmt.setString(1, outage.getFailedComponent());
                    nodeStmt.executeUpdate();
                }
            }

            conn.commit();

            // Invalidate caches to guarantee consistent state across system
            NetworkDAO.invalidateCache();
            ConsumerDAO.invalidateCache();
            StatsDAO.invalidateCache();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
                DatabaseConnection.closeQuietly(conn);
            }
        }
    }

    /**
     * Persists technician dispatch assignment using an atomic database transaction:
     * 1. Updates outage with technician info and status 'IN PROGRESS'
     * 2. Inserts technician dispatch record into 'technician_assignments'
     * 3. Sets availability_status = FALSE on technician roster
     */
    public synchronized void assignTechnicianTransaction(int outageId, int technicianId, String technicianName) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            boolean hasAssignedName = DatabaseConnection.columnExists(conn, "outages", "assigned_technician_name");
            boolean hasAssignedId = DatabaseConnection.columnExists(conn, "outages", "assigned_technician_id");

            StringBuilder updateOutageSql = new StringBuilder("UPDATE outages SET status = 'IN PROGRESS'");
            if (hasAssignedId) updateOutageSql.append(", assigned_technician_id = ?");
            if (hasAssignedName) updateOutageSql.append(", assigned_technician_name = ?");
            updateOutageSql.append(" WHERE outage_id = ?");

            try (PreparedStatement stmt = conn.prepareStatement(updateOutageSql.toString())) {
                int p = 1;
                if (hasAssignedId) stmt.setInt(p++, technicianId);
                if (hasAssignedName) stmt.setString(p++, technicianName);
                stmt.setInt(p++, outageId);
                stmt.executeUpdate();
            }

            String insertAssignSql =
                "INSERT INTO technician_assignments (outage_id, technician_id, assigned_at, assignment_status) " +
                "VALUES (?, ?, NOW(), 'IN_PROGRESS')";

            try (PreparedStatement stmt = conn.prepareStatement(insertAssignSql)) {
                stmt.setInt(1, outageId);
                stmt.setInt(2, technicianId);
                stmt.executeUpdate();
            }

            if (DatabaseConnection.columnExists(conn, "technicians", "availability_status")) {
                boolean isBool = DatabaseConnection.isColumnTypeBoolean(conn, "technicians", "availability_status");
                String updateTechSql = isBool
                    ? "UPDATE technicians SET availability_status = FALSE WHERE technician_id = ?"
                    : "UPDATE technicians SET availability_status = 'false' WHERE technician_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateTechSql)) {
                    stmt.setInt(1, technicianId);
                    stmt.executeUpdate();
                }
            }

            conn.commit();

            TechnicianDAO.invalidateCache();
            StatsDAO.invalidateCache();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
                DatabaseConnection.closeQuietly(conn);
            }
        }
    }

    /**
     * Executes grid restoration using an atomic database transaction:
     * 1. Marks outage as 'RESTORED' with restored_at = NOW()
     * 2. Re-activates network component in 'network_nodes'
     * 3. Re-energizes all consumers in affected locality areas in 'consumers'
     * 4. Releases technician back to AVAILABLE in 'technicians'
     * 5. Updates technician assignment status to 'COMPLETED' in 'technician_assignments'
     * 6. Creates persistent audit entry in 'restoration_history'
     */
    public synchronized void restoreOutageTransaction(
        int outageId,
        String failedComponent,
        List<String> affectedAreas,
        Integer technicianId,
        String technicianName,
        String restorationDetails
    ) throws SQLException {

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Update outage status
            String updateOutageSql = "UPDATE outages SET status = 'RESTORED', restored_at = NOW() WHERE outage_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateOutageSql)) {
                stmt.setInt(1, outageId);
                stmt.executeUpdate();
            }

            // 2. Re-activate node
            if (DatabaseConnection.columnExists(conn, "network_nodes", "is_active")) {
                String updateNodeSql = "UPDATE network_nodes SET is_active = TRUE WHERE node_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateNodeSql)) {
                    stmt.setString(1, failedComponent);
                    stmt.executeUpdate();
                }
            }

            // 3. Restore consumer power in affected areas
            if (affectedAreas != null && !affectedAreas.isEmpty() && DatabaseConnection.columnExists(conn, "consumers", "power_status")) {
                boolean isBool = DatabaseConnection.isColumnTypeBoolean(conn, "consumers", "power_status");
                String updateConsumerSql = isBool
                    ? "UPDATE consumers SET power_status = TRUE WHERE grid_locality_area = ?"
                    : "UPDATE consumers SET power_status = 'POWER ON' WHERE grid_locality_area = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateConsumerSql)) {
                    for (String area : affectedAreas) {
                        stmt.setString(1, area);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            }

            // 4. Update technician & assignment if present
            if (technicianId != null && technicianId > 0) {
                if (DatabaseConnection.columnExists(conn, "technicians", "availability_status")) {
                    boolean isBool = DatabaseConnection.isColumnTypeBoolean(conn, "technicians", "availability_status");
                    String updateTechSql = isBool
                        ? "UPDATE technicians SET availability_status = TRUE WHERE technician_id = ?"
                        : "UPDATE technicians SET availability_status = 'true' WHERE technician_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(updateTechSql)) {
                        stmt.setInt(1, technicianId);
                        stmt.executeUpdate();
                    }
                }

                String updateAssignSql = "UPDATE technician_assignments SET assignment_status = 'COMPLETED' WHERE outage_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateAssignSql)) {
                    stmt.setInt(1, outageId);
                    stmt.executeUpdate();
                }
            }

            // 5. Insert restoration history audit record
            String insertHistorySql =
                "INSERT INTO restoration_history (outage_id, technician_id, restoration_details, restored_at) " +
                "VALUES (?, ?, ?, NOW())";

            try (PreparedStatement stmt = conn.prepareStatement(insertHistorySql)) {
                stmt.setInt(1, outageId);
                if (technicianId != null && technicianId > 0) {
                    stmt.setInt(2, technicianId);
                } else {
                    stmt.setNull(2, java.sql.Types.INTEGER);
                }
                String details = restorationDetails != null && !restorationDetails.isEmpty()
                    ? restorationDetails
                    : "Grid component " + failedComponent + " re-energized. Power normalized to downstream consumers.";
                stmt.setString(3, details);
                stmt.executeUpdate();
            }

            conn.commit();

            // Invalidate caches so other queries see updated statuses immediately
            NetworkDAO.invalidateCache();
            ConsumerDAO.invalidateCache();
            TechnicianDAO.invalidateCache();
            StatsDAO.invalidateCache();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
                DatabaseConnection.closeQuietly(conn);
            }
        }
    }

    private static ArrayList<String> parseCommaList(String raw) {
        ArrayList<String> list = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return list;
        String[] parts = raw.split(",");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }
}
