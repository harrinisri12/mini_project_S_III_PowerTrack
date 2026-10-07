package model;

public class Consumer {

    private int consumerId;
    private String name;
    private String area;
    private boolean powerAvailable;

    public Consumer(int consumerId, String name, String area) {
        this.consumerId = consumerId;
        this.name = name;
        this.area = area;
        this.powerAvailable = true;
    }

    public int getConsumerId() {
        return consumerId;
    }

    public String getName() {
        return name;
    }

    public String getArea() {
        return area;
    }

    public boolean isPowerAvailable() {
        return powerAvailable;
    }

    public void setPowerAvailable(boolean powerAvailable) {
        this.powerAvailable = powerAvailable;
    }

    public void display() {
        System.out.println(
            "ID: " + consumerId +
            " | Name: " + name +
            " | Area: " + area +
            " | Power: " +
            (powerAvailable ? "AVAILABLE" : "OUTAGE")
        );
    }
}