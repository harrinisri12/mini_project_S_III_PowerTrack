package model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Outage model representing an active or restored electrical incident.
 *
 * Implements Comparable<Outage> for priority queue sorting:
 * 1. Hospitals & emergency medical facilities (Tier 5 - Highest Priority)
 * 2. Emergency services & critical infrastructure (Tier 4)
 * 3. Essential public services & education (Tier 3)
 * 4. Residential consumers (Tier 2)
 * 5. Commercial & industrial consumers (Tier 1)
 *
 * Outages with equal priority maintain strict FIFO ordering (earliest reported first).
 */
public class Outage implements Comparable<Outage> {

    private int outageId;

    private String failedComponent;
    private String faultType;

    private ArrayList<String> affectedAreas;

    private int affectedConsumers;

    private String priority; // "HIGH", "MEDIUM", "LOW"
    private int priorityTier; // 5 (Hospitals), 4 (Emergency), 3 (Public), 2 (Residential), 1 (Commercial)
    private String status;   // "ACTIVE", "IN PROGRESS", "RESTORED"

    private String assignedTechnician;
    private Integer assignedTechnicianId;

    private ArrayList<String> criticalFacilities;
    private String priorityReason;
    private int queueSequence;

    private Timestamp reportedAt;
    private Timestamp restoredAt;

    public Outage(
        int outageId,
        String failedComponent,
        String faultType,
        ArrayList<String> affectedAreas,
        int affectedConsumers
    ) {
        this.outageId = outageId;
        this.failedComponent = failedComponent != null ? failedComponent.trim() : "";
        this.faultType = faultType != null ? faultType.trim() : "Standard circuit fault";
        this.affectedAreas = affectedAreas != null ? new ArrayList<>(affectedAreas) : new ArrayList<>();
        this.affectedConsumers = affectedConsumers;

        this.priority = "LOW";
        this.priorityTier = 1;
        this.status = "ACTIVE";
        this.assignedTechnician = "NOT ASSIGNED";
        this.assignedTechnicianId = null;

        this.criticalFacilities = new ArrayList<>();
        this.priorityReason = "Standard circuit fault";
        this.queueSequence = 0;
        this.reportedAt = new Timestamp(System.currentTimeMillis());
        this.restoredAt = null;
    }

    // =========================================
    // DETERMINISTIC PRIORITY QUEUE LOGIC
    // =========================================

    /**
     * Compares two outages for priority ordering:
     * 1. Higher priority tier comes first (Tier 5 > 4 > 3 > 2 > 1).
     * 2. When priorities are equal, strict FIFO ordering is maintained:
     *    - Earliest reported timestamp comes first.
     *    - Lowest queue sequence comes first.
     *    - Lowest outage ID comes first.
     */
    @Override
    public int compareTo(Outage other) {
        if (other == null) return -1;
        if (this.outageId == other.outageId) return 0;

        int thisScore = this.priorityTier > 0 ? this.priorityTier : getPriorityScore(this.priority);
        int otherScore = other.priorityTier > 0 ? other.priorityTier : getPriorityScore(other.priority);

        // 1. Primary: Priority Tier (Highest score first - Descending order)
        if (thisScore != otherScore) {
            return Integer.compare(otherScore, thisScore);
        }

        // 2. Strict FIFO Tie-Breaker: Earliest reported timestamp first
        if (this.reportedAt != null && other.reportedAt != null) {
            int timeCmp = this.reportedAt.compareTo(other.reportedAt);
            if (timeCmp != 0) {
                return timeCmp; // Ascending (earliest timestamp comes first)
            }
        }

        // 3. Secondary FIFO Tie-Breaker: Queue sequence (lower sequence means earlier)
        if (this.queueSequence != other.queueSequence) {
            return Integer.compare(this.queueSequence, other.queueSequence);
        }

        // 4. Final Tie-Breaker: Outage ID
        return Integer.compare(this.outageId, other.outageId);
    }

    public static int getPriorityScore(String p) {
        if (p == null) return 1;
        switch (p.trim().toUpperCase()) {
            case "CRITICAL":
            case "HIGH":     return 5;
            case "MEDIUM":   return 3;
            case "LOW":      return 2;
            default:         return 1;
        }
    }

    // =========================================
    // GETTERS & SETTERS
    // =========================================

    public int getOutageId() {
        return outageId;
    }

    public void setOutageId(int outageId) {
        this.outageId = outageId;
    }

    public String getFailedComponent() {
        return failedComponent;
    }

    public void setFailedComponent(String failedComponent) {
        this.failedComponent = failedComponent;
    }

    public String getFaultType() {
        return faultType;
    }

    public void setFaultType(String faultType) {
        this.faultType = faultType;
    }

    public ArrayList<String> getAffectedAreas() {
        return affectedAreas;
    }

    public void setAffectedAreas(ArrayList<String> affectedAreas) {
        this.affectedAreas = affectedAreas != null ? new ArrayList<>(affectedAreas) : new ArrayList<>();
    }

    public int getAffectedConsumers() {
        return affectedConsumers;
    }

    public void setAffectedConsumers(int affectedConsumers) {
        this.affectedConsumers = affectedConsumers;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        if (priority != null && priority.trim().equalsIgnoreCase("CRITICAL")) {
            this.priority = "HIGH";
        } else {
            this.priority = priority != null ? priority.trim().toUpperCase() : "LOW";
        }
        if (this.priorityTier == 0 || this.priorityTier == 1) {
            this.priorityTier = getPriorityScore(this.priority);
        }
    }

    public int getPriorityTier() {
        return priorityTier;
    }

    public void setPriorityTier(int priorityTier) {
        this.priorityTier = priorityTier;
        if (priorityTier >= 4) {
            this.priority = "HIGH";
        } else if (priorityTier == 3) {
            this.priority = "MEDIUM";
        } else {
            this.priority = "LOW";
        }
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status != null ? status.trim().toUpperCase() : "ACTIVE";
    }

    public String getAssignedTechnician() {
        return assignedTechnician;
    }

    public void setAssignedTechnician(String assignedTechnician) {
        this.assignedTechnician = (assignedTechnician != null && !assignedTechnician.trim().isEmpty())
            ? assignedTechnician.trim()
            : "NOT ASSIGNED";
    }

    public Integer getAssignedTechnicianId() {
        return assignedTechnicianId;
    }

    public void setAssignedTechnicianId(Integer assignedTechnicianId) {
        this.assignedTechnicianId = assignedTechnicianId;
    }

    public java.sql.Timestamp getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(java.sql.Timestamp reportedAt) {
        this.reportedAt = reportedAt;
    }

    public java.sql.Timestamp getRestoredAt() {
        return restoredAt;
    }

    public void setRestoredAt(java.sql.Timestamp restoredAt) {
        this.restoredAt = restoredAt;
    }

    public ArrayList<String> getCriticalFacilities() {
        return criticalFacilities;
    }

    public void setCriticalFacilities(ArrayList<String> criticalFacilities) {
        this.criticalFacilities = criticalFacilities != null ? new ArrayList<>(criticalFacilities) : new ArrayList<>();
    }

    public String getPriorityReason() {
        return priorityReason;
    }

    public void setPriorityReason(String priorityReason) {
        this.priorityReason = priorityReason != null ? priorityReason.trim() : "";
    }

    public int getQueueSequence() {
        return queueSequence;
    }

    public void setQueueSequence(int queueSequence) {
        this.queueSequence = queueSequence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Outage outage = (Outage) o;
        return outageId == outage.outageId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(outageId);
    }

    public void display() {
        System.out.println();
        System.out.println("Outage ID: OUT-" + outageId);
        System.out.println("Failed Component: " + failedComponent);
        System.out.println("Fault Type: " + faultType);
        System.out.println("Affected Areas: " + affectedAreas);
        System.out.println("Affected Consumers: " + affectedConsumers);
        System.out.println("Priority Level: [" + priority + "] (Tier " + priorityTier + ")");

        if (criticalFacilities != null && !criticalFacilities.isEmpty()) {
            System.out.println("CRITICAL FACILITIES AFFECTED: " + criticalFacilities);
        }

        if (priorityReason != null && !priorityReason.isEmpty()) {
            System.out.println("Priority Reason: " + priorityReason);
        }

        System.out.println("Status: " + status);
        System.out.println("Assigned Technician: " + assignedTechnician);
    }

    @Override
    public String toString() {
        return "OUT-" + outageId +
            " | Component: " + failedComponent +
            " | Priority: [" + priority + "] (Tier " + priorityTier + ")" +
            " | Consumers: " + affectedConsumers +
            (criticalFacilities != null && !criticalFacilities.isEmpty() ? " | Critical Facilities: " + criticalFacilities : "") +
            " | Status: " + status +
            " | Technician: " + assignedTechnician;
    }
}