package model;

/**
 * Model representing a field technician in the PowerTrack workforce roster.
 */
public class Technician {

    private int technicianId;
    private String name;
    private String specialization;
    private boolean available;
    private String phone;
    private String email;
    private String assignedArea;
    private int currentWorkload;

    public Technician(
        int technicianId,
        String name,
        String specialization
    ) {
        this.technicianId = technicianId;
        this.name = name;
        this.specialization = specialization;
        this.available = true;
        this.phone = "+91 94440 " + (21000 + technicianId);
        this.email = deriveEmail(name);
        this.assignedArea = "Chennai Central Grid Division";
        this.currentWorkload = 0;
    }

    public Technician(
        int technicianId,
        String name,
        String specialization,
        boolean available,
        String phone,
        String email,
        String assignedArea,
        int currentWorkload
    ) {
        this.technicianId = technicianId;
        this.name = name;
        this.specialization = specialization;
        this.available = available;
        this.phone = (phone != null && !phone.trim().isEmpty()) ? phone : "+91 94440 " + (21000 + technicianId);
        this.email = (email != null && !email.trim().isEmpty()) ? email : deriveEmail(name);
        this.assignedArea = (assignedArea != null && !assignedArea.trim().isEmpty()) ? assignedArea : "Chennai Central Grid Division";
        this.currentWorkload = currentWorkload;
    }

    private static String deriveEmail(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "technician@powertrack.tneb.gov.in";
        }
        String clean = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        return clean + "@powertrack.tneb.gov.in";
    }

    public int getTechnicianId() {
        return technicianId;
    }

    public String getName() {
        return name;
    }

    public String getSpecialization() {
        return specialization;
    }

    public String getSkills() {
        return specialization;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAssignedArea() {
        return assignedArea;
    }

    public void setAssignedArea(String assignedArea) {
        this.assignedArea = assignedArea;
    }

    public int getCurrentWorkload() {
        return currentWorkload;
    }

    public void setCurrentWorkload(int currentWorkload) {
        this.currentWorkload = currentWorkload;
    }

    public void display() {
        System.out.println(
            "Technician ID: " + technicianId +
            " | Name: " + name +
            " | Specialization: " + specialization +
            " | Status: " + (available ? "AVAILABLE" : "ASSIGNED") +
            " | Workload: " + currentWorkload +
            " | Phone: " + phone
        );
    }
}
