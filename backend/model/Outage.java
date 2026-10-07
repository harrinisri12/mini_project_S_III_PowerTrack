package model;

import java.util.ArrayList;

public class Outage {

    private int outageId;

    private String failedComponent;
    private String faultType;

    private ArrayList<String> affectedAreas;

    private int affectedConsumers;

    private String priority;
    private String status;

    private String assignedTechnician;

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

    // =========================================
    // SETTERS
    // =========================================

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAssignedTechnician(
        String assignedTechnician
    ) {
        this.assignedTechnician = assignedTechnician;
    }

    // =========================================
    // DISPLAY
    // =========================================

    public void display() {

        System.out.println();

        System.out.println(
            "Outage ID: " + outageId
        );

        System.out.println(
            "Failed Component: " + failedComponent
        );

        System.out.println(
            "Fault Type: " + faultType
        );

        System.out.println(
            "Affected Areas: " + affectedAreas
        );

        System.out.println(
            "Affected Consumers: " + affectedConsumers
        );

        System.out.println(
            "Priority: " + priority
        );

        System.out.println(
            "Status: " + status
        );

        System.out.println(
            "Technician: " + assignedTechnician
        );
    }

    @Override
    public String toString() {

        return
            "Outage ID: " + outageId +
            " | Component: " + failedComponent +
            " | Areas: " + affectedAreas +
            " | Consumers: " + affectedConsumers +
            " | Priority: " + priority +
            " | Status: " + status +
            " | Technician: " + assignedTechnician;
    }
}