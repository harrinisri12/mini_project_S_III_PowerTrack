package model;

import java.util.ArrayList;

public class Outage implements Comparable<Outage> {

    private int outageId;

    private String failedComponent;
    private String faultType;

    private ArrayList<String> affectedAreas;

    private int affectedConsumers;

    private String priority;
    private String status;

    private String assignedTechnician;
    private Integer assignedTechnicianId;

    private ArrayList<String> criticalFacilities;
    private String priorityReason;
    private int queueSequence;

    private java.sql.Timestamp reportedAt;
    private java.sql.Timestamp restoredAt;

    public Outage(
        int outageId,
        String failedComponent,
        String faultType,
        ArrayList<String> affectedAreas,
        int affectedConsumers
    ) {

        this.outageId = outageId;
        this.failedComponent = failedComponent;
        this.faultType = faultType;
        this.affectedAreas = affectedAreas;
        this.affectedConsumers = affectedConsumers;

        this.priority = "PENDING";
        this.status = "ACTIVE";
        this.assignedTechnician = "NOT ASSIGNED";

        this.criticalFacilities = new ArrayList<>();
        this.priorityReason = "Standard circuit fault";
        this.queueSequence = 0;
    }

    // =========================================
    // COMPARABLE PRIORITY QUEUE LOGIC
    // =========================================

    @Override
    public int compareTo(Outage other) {
        if (other == null) return -1;

        int thisScore = getPriorityScore(this.priority);
        int otherScore = getPriorityScore(other.priority);

        // 1. Primary: Priority level (HIGH > MEDIUM > LOW)
        if (thisScore != otherScore) {
            return Integer.compare(otherScore, thisScore); // Descending (higher score comes first)
        }

        // 2. Consistent tie-breaker: Earliest reported outage first
        if (this.reportedAt != null && other.reportedAt != null) {
            int timeCmp = this.reportedAt.compareTo(other.reportedAt);
            if (timeCmp != 0) {
                return timeCmp; // Ascending (earlier reported time comes first)
            }
        }

        if (this.queueSequence != other.queueSequence) {
            return Integer.compare(this.queueSequence, other.queueSequence); // Ascending (earlier first)
        }

        // 3. Fallback tie-breaker: Outage ID
        return Integer.compare(this.outageId, other.outageId);
    }

    public static int getPriorityScore(String p) {
        if (p == null) return 0;
        switch (p.trim().toUpperCase()) {
            case "CRITICAL":
            case "HIGH":     return 3;
            case "MEDIUM":   return 2;
            case "LOW":      return 1;
            default:         return 0;
        }
    }

    // =========================================
    // GETTERS
    // =========================================

    public int getOutageId() {
        return outageId;
    }

    public String getFailedComponent() {
        return failedComponent;
    }

    public String getFaultType() {
        return faultType;
    }

    public ArrayList<String> getAffectedAreas() {
        return affectedAreas;
    }

    public int getAffectedConsumers() {
        return affectedConsumers;
    }

    public String getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public String getAssignedTechnician() {
        return assignedTechnician;
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

    public String getPriorityReason() {
        return priorityReason;
    }

    public int getQueueSequence() {
        return queueSequence;
    }

    // =========================================
    // SETTERS
    // =========================================

    public void setPriority(String priority) {
        if (priority != null && priority.trim().equalsIgnoreCase("CRITICAL")) {
            this.priority = "HIGH";
        } else {
            this.priority = priority != null ? priority.trim().toUpperCase() : "LOW";
        }
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAssignedTechnician(String assignedTechnician) {
        this.assignedTechnician = assignedTechnician;
    }

    public void setCriticalFacilities(ArrayList<String> criticalFacilities) {
        this.criticalFacilities = criticalFacilities != null ? criticalFacilities : new ArrayList<>();
    }

    public void setPriorityReason(String priorityReason) {
        this.priorityReason = priorityReason != null ? priorityReason : "";
    }

    public void setQueueSequence(int queueSequence) {
        this.queueSequence = queueSequence;
    }

    // =========================================
    // DISPLAY
    // =========================================

    public void display() {

        System.out.println();
        System.out.println("Outage ID: OUT-" + outageId);
        System.out.println("Failed Component: " + failedComponent);
        System.out.println("Fault Type: " + faultType);
        System.out.println("Affected Areas: " + affectedAreas);
        System.out.println("Affected Consumers: " + affectedConsumers);
        System.out.println("Priority Level: [" + priority + "]");

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
            " | Priority: [" + priority + "]" +
            " | Consumers: " + affectedConsumers +
            (criticalFacilities != null && !criticalFacilities.isEmpty() ? " | Critical Facilities: " + criticalFacilities : "") +
            " | Status: " + status +
            " | Technician: " + assignedTechnician;
    }
}