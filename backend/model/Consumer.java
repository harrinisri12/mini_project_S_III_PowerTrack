package model;

import java.util.Objects;

/**
 * Consumer model representing electricity accounts connected to the grid.
 *
 * Categorization & Priority Tiers:
 * 1. Hospitals and emergency medical facilities (Tier 5 - Highest Priority)
 * 2. Emergency services and critical infrastructure (Tier 4)
 * 3. Essential public services & educational institutions (Tier 3)
 * 4. Residential consumers (Tier 2)
 * 5. Commercial & industrial consumers (Tier 1)
 */
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
        this.name = name != null ? name.trim() : "Unknown Consumer";
        this.area = area != null ? area.trim() : "";
        this.connectedLoadKw = connectedLoadKw > 0 ? connectedLoadKw : 5.0;

        // 1. Determine consumer category
        if (consumerType != null && !consumerType.trim().isEmpty() && !consumerType.equalsIgnoreCase("DEFAULT")) {
            this.consumerType = consumerType.trim().toUpperCase();
        } else {
            this.consumerType = inferConsumerType(this.name);
        }

        // 2. Determine criticality level (HIGH, MEDIUM, LOW)
        if (criticalityLevel != null && !criticalityLevel.trim().isEmpty()
                && !criticalityLevel.equalsIgnoreCase("DEFAULT")) {
            String clean = criticalityLevel.trim().toUpperCase();
            this.criticalityLevel = clean.equals("CRITICAL") ? "HIGH" : clean;
        } else {
            this.criticalityLevel = inferCriticalityLevel(this.name, this.consumerType);
        }

        this.powerAvailable = powerAvailable;
    }

    /**
     * Determines the detailed restoration priority tier (1 to 5):
     * Tier 5: Hospitals and emergency medical facilities
     * Tier 4: Emergency services and critical infrastructure (fire, water treatment)
     * Tier 3: Essential public services and education
     * Tier 2: Residential consumers
     * Tier 1: Commercial & industrial consumers
     */
    /**
     * Determines the detailed restoration priority tier (1 to 5).
     * Uses the stored criticalityLevel from PostgreSQL as the authoritative bracket:
     * - HIGH: Tier 5 (Hospitals/medical) or Tier 4 (Emergency/critical infrastructure)
     * - MEDIUM: Tier 3 (Essential public services / education)
     * - LOW: Tier 2 (Residential) or Tier 1 (Commercial / industrial)
     * Falls back to inference only when the stored criticalityLevel is missing or unrecognized.
     */
    public int getCriticalityTier() {
        if (this.criticalityLevel != null && !this.criticalityLevel.trim().isEmpty()) {
            String level = this.criticalityLevel.trim().toUpperCase();
            int inferred = evaluateCriticalityTier(this.name, this.consumerType);

            if ("HIGH".equals(level)) {
                // Guaranteed at least Tier 4; Tier 5 for hospitals/trauma/medical
                return (inferred >= 5) ? 5 : 4;
            } else if ("MEDIUM".equals(level)) {
                // Tier 3: Essential public services / education
                return 3;
            } else if ("LOW".equals(level)) {
                // Tier 2 for residential, Tier 1 for commercial/industrial; capped at 2
                return (inferred <= 1) ? 1 : 2;
            }
        }

        return evaluateCriticalityTier(this.name, this.consumerType);
    }

    public static int evaluateCriticalityTier(String name, String type) {
        String n = (name != null) ? name.toLowerCase() : "";
        String t = (type != null) ? type.toUpperCase() : "";

        // Tier 5: Hospitals and emergency medical facilities
        if (t.contains("HOSPITAL") || t.contains("CLINIC") || t.contains("TRAUMA")
                || n.contains("hospital") || n.contains("clinic") || n.contains("trauma")
                || n.contains("icu") || n.contains("oxygen plant") || n.contains("maternity")) {
            return 5;
        }

        // Tier 4: Emergency services and critical infrastructure
        if (t.contains("EMERGENCY") || t.contains("FIRE") || t.contains("WATER")
                || n.contains("emergency") || n.contains("fire") || n.contains("ambulance")
                || n.contains("water supply") || n.contains("effluent") || n.contains("treatment plant")) {
            return 4;
        }

        // Tier 3: Essential public services & education
        if (t.contains("EDUCATION") || t.contains("COMMUNITY") || t.contains("PUBLIC")
                || n.contains("school") || n.contains("college") || n.contains("university")
                || n.contains("health center") || n.contains("health centre") || n.contains("co-op")) {
            return 3;
        }

        // Tier 2: Residential consumers
        if (t.contains("RESIDENTIAL") || n.contains("domestic") || n.contains("quarters") || n.contains("household")) {
            return 2;
        }

        // Tier 1: Commercial & industrial consumers
        if (t.contains("COMMERCIAL") || t.contains("INDUSTRIAL")
                || n.contains("commercial") || n.contains("textile") || n.contains("mill")
                || n.contains("industry") || n.contains("plant") || n.contains("garments")
                || n.contains("bazaar") || n.contains("complex") || n.contains("logistics")) {
            return 1;
        }

        return 2; // Default to residential tier
    }

    public static String inferConsumerType(String name) {
        if (name == null) return "RESIDENTIAL";
        String lower = name.toLowerCase();
        if (lower.contains("hospital")) return "HOSPITAL";
        if (lower.contains("clinic") || lower.contains("maternity") || lower.contains("trauma")) return "CLINIC";
        if (lower.contains("health center") || lower.contains("health centre")) return "COMMUNITY_HEALTH";
        if (lower.contains("fire") || lower.contains("emergency") || lower.contains("ambulance")) return "EMERGENCY_SERVICE";
        if (lower.contains("water") || lower.contains("effluent")) return "WATER_FACILITY";
        if (lower.contains("school") || lower.contains("college") || lower.contains("education") || lower.contains("university")) return "EDUCATION";
        if (lower.contains("mill") || lower.contains("spinning") || lower.contains("textile") || lower.contains("dyeing") || lower.contains("knitwear") || lower.contains("industry") || lower.contains("plant")) return "INDUSTRIAL";
        if (lower.contains("commercial") || lower.contains("complex") || lower.contains("bazaar") || lower.contains("works") || lower.contains("logistics") || lower.contains("storage") || lower.contains("co-op") || lower.contains("society") || lower.contains("garments")) return "COMMERCIAL";
        return "RESIDENTIAL";
    }

    public static String inferCriticalityLevel(String name, String type) {
        int tier = evaluateCriticalityTier(name, type);
        if (tier >= 4) {
            return "HIGH";
        } else if (tier == 3) {
            return "MEDIUM";
        } else if (tier == 2) {
            return "LOW";
        } else {
            return "LOW";
        }
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Consumer consumer = (Consumer) o;
        return consumerId == consumer.consumerId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerId);
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