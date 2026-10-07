package model;

public class Technician {

    private int technicianId;
    private String name;
    private String specialization;
    private boolean available;

    public Technician(
        int technicianId,
        String name,
        String specialization
    ) {

        this.technicianId = technicianId;
        this.name = name;
        this.specialization = specialization;
        this.available = true;
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

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void display() {

        System.out.println(
            "Technician ID: " + technicianId +
            " | Name: " + name +
            " | Specialization: " + specialization +
            " | Status: " +
            (available ? "AVAILABLE" : "ASSIGNED")
        );
    }
}