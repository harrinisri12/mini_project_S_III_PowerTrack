package model;

public class Consumer {

    private int consumerId;
    private String name;
    private String area;
    private String consumerType;
    private String criticalityLevel;
    private double connectedLoadKw;
    private boolean powerAvailable;

    public Consumer(int consumerId, String name, String area) {
        this(consumerId, name, area, null, null);
    }

    public Consumer(
        int consumerId,
        String name,
        String area,
        String consumerType,
        String criticalityLevel
    ) {
        this(consumerId, name, area, consumerType, criticalityLevel, 5.0, true);
    }

    public Consumer(
        int consumerId,
        String name,
        String area,
        String consumerType,
        String criticalityLevel,
        double connectedLoadKw,
        boolean powerAvailable
    ) {
        this.consumerId = consumerId;
        this.name = name;
        this.area = area;
        this.connectedLoadKw = connectedLoadKw;

        // Determine consumer category
        if (consumerType != null && !consumerType.trim().isEmpty() && !consumerType.equalsIgnoreCase("DEFAULT")) {
            this.consumerType = consumerType.trim().toUpperCase();
        } else {
            this.consumerType = inferConsumerType(name);
        }

        // Determine criticality level:
        // Hospitals, clinics, health centers, emergency services -> HIGH
        // Commercial establishments, industries, schools -> MEDIUM
        // Ordinary residential households -> LOW
        if (criticalityLevel != null && !criticalityLevel.trim().isEmpty()
                && !criticalityLevel.equalsIgnoreCase("DEFAULT")
                && !criticalityLevel.equalsIgnoreCase("LOW")) {
            String cleanLevel = criticalityLevel.trim().toUpperCase();
            this.criticalityLevel = cleanLevel.equals("CRITICAL") ? "HIGH" : cleanLevel;
        } else if (criticalityLevel != null && criticalityLevel.equalsIgnoreCase("LOW") && isPureResidential(name, this.consumerType)) {
            this.criticalityLevel = "LOW";
        } else {
            this.criticalityLevel = inferCriticalityLevel(name, this.consumerType);
        }

        this.powerAvailable = powerAvailable;
    }

    public static boolean isPureResidential(String name, String type) {
        if (type != null && !type.equalsIgnoreCase("RESIDENTIAL")) {
            return false;
        }
        if (name == null) return true;
        String lower = name.toLowerCase();
        return !lower.contains("hospital") && !lower.contains("clinic") && !lower.contains("health")
            && !lower.contains("emergency") && !lower.contains("fire") && !lower.contains("water")
            && !lower.contains("effluent") && !lower.contains("commercial") && !lower.contains("complex")
            && !lower.contains("mill") && !lower.contains("textile") && !lower.contains("school")
            && !lower.contains("college") && !lower.contains("storage") && !lower.contains("logistics")
            && !lower.contains("works") && !lower.contains("garments") && !lower.contains("co-op")
            && !lower.contains("society") && !lower.contains("plant");
    }

    public static String inferConsumerType(String name) {
        if (name == null) return "RESIDENTIAL";
        String lower = name.toLowerCase();
        if (lower.contains("hospital")) return "HOSPITAL";
        if (lower.contains("clinic")) return "CLINIC";
        if (lower.contains("health center") || lower.contains("health centre")) return "COMMUNITY_HEALTH";
        if (lower.contains("fire") || lower.contains("emergency") || lower.contains("ambulance")) return "EMERGENCY_SERVICE";
        if (lower.contains("water") || lower.contains("effluent")) return "WATER_FACILITY";
        if (lower.contains("school") || lower.contains("college") || lower.contains("education") || lower.contains("university")) return "EDUCATION";
        if (lower.contains("mill") || lower.contains("spinning") || lower.contains("textile") || lower.contains("dyeing") || lower.contains("knitwear") || lower.contains("industry") || lower.contains("plant")) return "INDUSTRIAL";
        if (lower.contains("commercial") || lower.contains("complex") || lower.contains("bazaar") || lower.contains("works") || lower.contains("logistics") || lower.contains("storage") || lower.contains("co-op") || lower.contains("society") || lower.contains("garments")) return "COMMERCIAL";
        return "RESIDENTIAL";
    }

    public static String inferCriticalityLevel(String name, String type) {
        String t = (type != null) ? type.toUpperCase() : "";
        String n = (name != null) ? name.toLowerCase() : "";

        // 1. HIGH: Hospitals, clinics, community health centers, emergency services, water facilities & essential utilities
        if (t.contains("HOSPITAL") || t.contains("CLINIC") || t.contains("HEALTH") || t.contains("EMERGENCY") || t.contains("WATER")
            || n.contains("hospital") || n.contains("clinic") || n.contains("health center") || n.contains("health centre") 
            || n.contains("emergency") || n.contains("fire") || n.contains("oxygen") || n.contains("trauma") || n.contains("icu") 
            || n.contains("water supply") || n.contains("effluent")) {
            return "HIGH";
        }

        // 2. MEDIUM: Commercial establishments, industries, schools and other public facilities
        if (t.contains("COMMERCIAL") || t.contains("INDUSTRIAL") || t.contains("EDUCATION") || t.contains("PUBLIC")
            || n.contains("commercial") || n.contains("complex") || n.contains("mill") || n.contains("spinning")
            || n.contains("textile") || n.contains("school") || n.contains("bazaar") || n.contains("storage") 
            || n.contains("logistics") || n.contains("works") || n.contains("dyeing") || n.contains("garments") 
            || n.contains("co-op") || n.contains("society") || n.contains("plant")) {
            return "MEDIUM";
        }

        // 3. LOW: Ordinary residential households and non-critical loads
        return "LOW";
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

    public String getConsumerType() {
        return consumerType;
    }

    public void setConsumerType(String consumerType) {
        this.consumerType = (consumerType != null) ? consumerType.toUpperCase() : "RESIDENTIAL";
        this.criticalityLevel = inferCriticalityLevel(this.name, this.consumerType);
    }

    public String getCriticalityLevel() {
        return criticalityLevel;
    }

    public void setCriticalityLevel(String criticalityLevel) {
        if (criticalityLevel != null) {
            String clean = criticalityLevel.trim().toUpperCase();
            this.criticalityLevel = clean.equals("CRITICAL") ? "HIGH" : clean;
        } else {
            this.criticalityLevel = inferCriticalityLevel(this.name, this.consumerType);
        }
    }

    public double getConnectedLoadKw() {
        return connectedLoadKw;
    }

    public void setConnectedLoadKw(double connectedLoadKw) {
        this.connectedLoadKw = connectedLoadKw;
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
            " | Type: " + consumerType +
            " | Criticality: [" + criticalityLevel + "]" +
            " | Power: " + (powerAvailable ? "AVAILABLE" : "OUTAGE")
        );
    }
}