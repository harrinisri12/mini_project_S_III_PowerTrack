package dao;

import database.DatabaseConnection;
import model.Outage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'outages', 'outage_affected_consumers',
 * 'technician_assignments', and 'restoration_history' tables.
 * Utilizes atomic transactions where multiple related records must succeed together.
 */
public class OutageDAO {

    /**
     * Loads all outages (active, in-progress, and historical restored) from Supabase.
     */
    public List<Outage> getAllOutages() throws SQLException {
        List<Outage> list = new ArrayList<>();
        String sql = "SELECT * FROM outages ORDER BY reported_at ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            boolean hasPriorityReason = DatabaseConnection.hasColumn(meta, "priority_reason");
            boolean hasCriticalFacilities = DatabaseConnection.hasColumn(meta, "critical_facilities");
            boolean hasAssignedName = DatabaseConnection.hasColumn(meta, "assigned_technician_name");
            boolean hasAssignedId = DatabaseConnection.hasColumn(meta, "assigned_technician_id");
            boolean hasQueueSeq = DatabaseConnection.hasColumn(meta, "queue_sequence");
            boolean hasReportedAt = DatabaseConnection.hasColumn(meta, "reported_at");
            boolean hasRestoredAt = DatabaseConnection.hasColumn(meta, "restored_at");

            int fallbackSeq = 1;

            while (rs.next()) {
                int id = rs.getInt("outage_id");
                String comp = rs.getString("fault_component_id");
                String faultType = rs.getString("fault_type");
                String rawAreas = rs.getString("affected_area");
                int consumers = rs.getInt("affected_consumer_count");
                String priority = rs.getString("priority");
                String status = rs.getString("status");

                String reason = hasPriorityReason ? rs.getString("priority_reason") : null;
                String rawCrit = hasCriticalFacilities ? rs.getString("critical_facilities") : null;
                int techId = hasAssignedId ? rs.getInt("assigned_technician_id") : 0;
                String techName = hasAssignedName ? rs.getString("assigned_technician_name") : null;
                int seq = hasQueueSeq ? rs.getInt("queue_sequence") : fallbackSeq++;
                Timestamp repAt = hasReportedAt ? rs.getTimestamp("reported_at") : null;
                Timestamp resAt = hasRestoredAt ? rs.getTimestamp("restored_at") : null;

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

                // If technician name was not directly on the outage, resolve via technician_assignments join
                if (techName != null && !techName.trim().isEmpty()) {
                    o.setAssignedTechnician(techName);
                    o.setAssignedTechnicianId(techId > 0 ? techId : null);
                } else {
                    resolveTechnicianFromAssignments(conn, o);
                }

                list.add(o);
            }
        }
        return list;
    }

    /**
     * Resolves the assigned technician from technician_assignments and technicians tables.
     */
    private void resolveTechnicianFromAssignments(Connection conn, Outage o) {
        String sql = "SELECT t.technician_id, t.technician_name " +
                     "FROM technician_assignments ta " +
                     "JOIN technicians t ON ta.technician_id = t.technician_id " +
                     "WHERE ta.outage_id = ? " +
                     "ORDER BY ta.assigned_at DESC LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, o.getOutageId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int tId = rs.getInt("technician_id");
                    String tName = rs.getString("technician_name");
                    o.setAssignedTechnician(tName != null ? tName : "NOT ASSIGNED");
                    o.setAssignedTechnicianId(tId > 0 ? tId : null);
                    return;
                }
            }
        } catch (SQLException ignored) {
        }

        o.setAssignedTechnician("NOT ASSIGNED");
        o.setAssignedTechnicianId(null);
    }

    /**
     * Retrieves the next available Outage ID from the database sequence/max value.
     */
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
    public void insertOutageTransaction(Outage outage, List<Integer> affectedConsumerIds) throws SQLException {
        if (outage == null) return;

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            boolean hasReason = DatabaseConnection.columnExists(conn, "outages", "priority_reason");
            boolean hasCrit = DatabaseConnection.columnExists(conn, "outages", "critical_facilities");
            boolean hasSeq = DatabaseConnection.columnExists(conn, "outages", "queue_sequence");

            // 1. Build insert statement dynamically for outages
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
    public void assignTechnicianTransaction(int outageId, int technicianId, String technicianName) throws SQLException {
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
    public void restoreOutageTransaction(
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
