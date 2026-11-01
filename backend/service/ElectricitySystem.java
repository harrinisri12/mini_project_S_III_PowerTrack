package service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import database.DatabaseConnection;
import dao.ConsumerDAO;
import dao.NetworkDAO;
import dao.TechnicianDAO;
import dao.OutageDAO;
import dao.StatsDAO;

import model.Consumer;
import model.NetworkConnection;
import model.NetworkNode;
import model.Outage;
import model.Technician;

import datastructure.Queue;
import datastructure.Graph;

/**
 * ==============================================================================
 * PowerTrack: Core Electricity System Service & Coordinator
 * ==============================================================================
 * Requirements satisfied:
 *   1. Directed Graph Traversal: Substation → Feeder → Transformer → Distribution Areas.
 *   2. Dynamic Downstream BFS: Zero hardcoded affected area mappings.
 *   3. Outage Detection Workflow: Validates component, checks existing active outage,
 *      discovers downstream consumers, evaluates deterministic 5-tier priority,
 *      enqueues in priority queue, and commits atomic DB transaction.
 *   4. Deterministic 5-Tier Priority Evaluation:
 *      Tier 5: Hospitals & emergency medical facilities (HIGH)
 *      Tier 4: Emergency services & critical infrastructure (HIGH)
 *      Tier 3: Essential public services & education (MEDIUM)
 *      Tier 2: Residential consumers (LOW)
 *      Tier 1: Commercial & industrial consumers (LOW)
 *   5. Strict FIFO Tie-Breaking for equal priorities.
 *   6. Thread-Safe Operations for concurrent HTTP requests.
 *   7. Technician Availability Constraints: No technician can hold multiple active assignments.
 * ==============================================================================
 */
public class ElectricitySystem {

    private Consumer[] consumers;
    private NetworkNode[] networkNodes;
    private Outage[] outages;
    private Technician[] technicians;

    // Custom Data Structures: Priority Queue and Directed Grid Topology Graph
    private final Queue<Outage> outageQueue;
    private final Graph electricityGraph;

    private int consumerCount;
    private int networkNodeCount;
    private int outageCount;
    private int technicianCount;

    private int nextOutageId;
    private int nextQueueSequence;

    // Database DAOs
    private final ConsumerDAO consumerDAO = new ConsumerDAO();
    private final NetworkDAO networkDAO = new NetworkDAO();
    private final TechnicianDAO technicianDAO = new TechnicianDAO();
    private final OutageDAO outageDAO = new OutageDAO();
    private final StatsDAO statsDAO = new StatsDAO();
    private boolean databaseConnected = false;

    // =========================================
    // CONSTRUCTOR
    // =========================================

    public ElectricitySystem() {
        consumers = new Consumer[120];
        networkNodes = new NetworkNode[100];
        outages = new Outage[100];
        technicians = new Technician[50];

        outageQueue = new Queue<>();
        electricityGraph = new Graph();

        consumerCount = 0;
        networkNodeCount = 0;
        outageCount = 0;
        technicianCount = 0;

        nextOutageId = 1001;
        nextQueueSequence = 1;

        initializeSystem();
    }

    // =========================================
    // INITIALIZATION & DATABASE SYNC
    // =========================================

    private void initializeSystem() {
        if (!DatabaseConnection.isConfigured()) {
            String err = "[PowerTrack Fatal Error] Supabase database credentials are not configured.\n"
                    + "Environment variables SUPABASE_DB_URL, SUPABASE_DB_USER, and SUPABASE_DB_PASSWORD are required.\n"
                    + "Backend startup halted.";
            System.err.println(err);
            throw new IllegalStateException(err);
        }

        if (!DatabaseConnection.testConnection()) {
            String err = "[PowerTrack Fatal Error] Cannot connect to Supabase PostgreSQL database.\n"
                    + "Please verify database status, network connectivity, and credentials.\n"
                    + "Backend startup halted.";
            System.err.println(err);
            throw new IllegalStateException(err);
        }

        try {
            System.out.println("[PowerTrack] Connected to Supabase PostgreSQL database.");
            loadFromDatabase();
            databaseConnected = true;
            System.out.println("[PowerTrack] Database synchronization complete.");
        } catch (Exception e) {
            String err = "[PowerTrack Fatal Error] Failed to load data from Supabase: "
                    + DatabaseConnection.sanitizeErrorMessage(e.getMessage());
            System.err.println(err);
            throw new IllegalStateException(err, e);
        }
    }

    public synchronized void loadFromDatabase() throws SQLException {
        // Reset in-memory structures to mirror Supabase database directly
        consumerCount = 0;
        networkNodeCount = 0;
        outageCount = 0;
        technicianCount = 0;
        outageQueue.clear();

        // 1. Network nodes from Supabase
        List<NetworkNode> dbNodes = networkDAO.getAllNodes();
        for (NetworkNode node : dbNodes) {
            addNetworkNode(node);
            electricityGraph.addNode(node.getNodeId(), node.getNodeType());
        }

        // 2. Directed network connections into Graph from Supabase
        // Directs downstream power flow: Substation → Feeder → Transformer → Distribution Area
        List<NetworkConnection> dbConns = networkDAO.getAllConnections();
        for (NetworkConnection conn : dbConns) {
            electricityGraph.addDirectedEdge(conn.getSourceNodeId(), conn.getDestinationNodeId());
        }

        // 3. Consumers from Supabase
        List<Consumer> dbConsumers = consumerDAO.getAllConsumers();
        for (Consumer c : dbConsumers) {
            addConsumer(c);
        }

        // 4. Technicians from Supabase
        List<Technician> dbTechs = technicianDAO.getAllTechnicians();
        for (Technician t : dbTechs) {
            addTechnician(t);
        }

        // 5. Outages from Supabase
        List<Outage> dbOutages = outageDAO.getAllOutages();
        int maxSeq = 0;
        for (Outage o : dbOutages) {
            addOutage(o);
            if (o.getQueueSequence() > maxSeq) {
                maxSeq = o.getQueueSequence();
            }

            if ("ACTIVE".equalsIgnoreCase(o.getStatus())) {
                outageQueue.enqueue(o);
            }
        }

        this.nextOutageId = outageDAO.getNextOutageId();
        this.nextQueueSequence = maxSeq + 1;

        System.out.println("[PowerTrack] Live database records loaded from Supabase:");
        System.out.println("  - Nodes: " + networkNodeCount);
        System.out.println("  - Connections: " + dbConns.size());
        System.out.println("  - Consumers: " + consumerCount);
        System.out.println("  - Technicians: " + technicianCount);
        System.out.println("  - Outages: " + outageCount + " (" + outageQueue.size() + " active in queue)");
    }

    // =========================================
    // STORAGE HELPERS (DYNAMIC SIZING)
    // =========================================

    public synchronized void addConsumer(Consumer consumer) {
        if (consumerCount >= consumers.length) {
            consumers = Arrays.copyOf(consumers, consumers.length * 2);
        }
        consumers[consumerCount] = consumer;
        consumerCount++;
    }

    public synchronized void addNetworkNode(NetworkNode node) {
        if (networkNodeCount >= networkNodes.length) {
            networkNodes = Arrays.copyOf(networkNodes, networkNodes.length * 2);
        }
        networkNodes[networkNodeCount] = node;
        networkNodeCount++;
    }

    public synchronized void addTechnician(Technician technician) {
        if (technicianCount >= technicians.length) {
            technicians = Arrays.copyOf(technicians, technicians.length * 2);
        }
        technicians[technicianCount] = technician;
        technicianCount++;
    }

    public synchronized void addOutage(Outage outage) {
        if (outageCount >= outages.length) {
            outages = Arrays.copyOf(outages, outages.length * 2);
        }
        outages[outageCount] = outage;
        outageCount++;
    }

    // =========================================
    // SIMULATE FAULT (CORRECT OUTAGE WORKFLOW)
    // =========================================

    /**
     * Executes the consistent 12-step outage detection workflow:
     * 1. Validate the failed component ID.
     * 2. Check for an existing active outage on the component.
     * 3. Mark the component as faulty.
     * 4. Perform downstream BFS.
     * 5. Identify affected distribution areas dynamically.
     * 6. Identify consumers connected to those areas.
     * 7. Calculate the affected consumer count.
     * 8. Identify critical facilities and their tiers.
     * 9. Create an outage record.
     * 10. Calculate deterministic priority.
     * 11. Insert it into the priority queue.
     * 12. Persist the outage in PostgreSQL transaction.
     *
     * @param inputComponent component identifier
     * @return the created Outage record
     */
    public synchronized Outage simulateFault(String inputComponent) {
        if (inputComponent == null || inputComponent.trim().isEmpty()) {
            throw new IllegalArgumentException("Failed component identifier is required.");
        }

        String failedComponent = inputComponent.trim().toUpperCase();

        // Normalize legacy shorthand aliases
        if (failedComponent.equalsIgnoreCase("F1")) failedComponent = "FD-ERD-01";
        else if (failedComponent.equalsIgnoreCase("F2")) failedComponent = "FD-PER-02";
        else if (failedComponent.equalsIgnoreCase("T1")) failedComponent = "DT-ERD-101";
        else if (failedComponent.equalsIgnoreCase("T2")) failedComponent = "DT-PER-102";

        // Step 1: Validate the failed component ID
        if (!electricityGraph.containsNode(failedComponent)) {
            throw new IllegalArgumentException("Invalid network component ID: " + failedComponent + ". Component does not exist in grid topology.");
        }

        if (failedComponent.startsWith("SS-") || failedComponent.startsWith("AREA-")) {
            throw new IllegalArgumentException("Please select a distribution feeder (FD-...) or transformer (DT-...). Substations and area endpoints cannot be simulated directly.");
        }

        // Step 2: Check for an existing active outage
        try {
            if (outageDAO.hasActiveOutageForComponent(failedComponent)) {
                throw new IllegalStateException("An active or in-progress outage already exists for component: " + failedComponent + ". Component is already offline.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error checking existing active outages: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage()), e);
        }

        for (int i = 0; i < outageCount; i++) {
            if (outages[i] != null
                    && outages[i].getFailedComponent().equalsIgnoreCase(failedComponent)
                    && !"RESTORED".equalsIgnoreCase(outages[i].getStatus())) {
                throw new IllegalStateException("An active or in-progress outage already exists in memory for component: " + failedComponent);
            }
        }

        // Step 3: Mark the component as faulty in memory and topology
        setNodeStatus(failedComponent, false);

        // Step 4 & 5: Perform downstream BFS and identify affected distribution areas dynamically
        ArrayList<String> affectedAreas = electricityGraph.getDownstreamAreas(failedComponent);

        if (affectedAreas.isEmpty()) {
            setNodeStatus(failedComponent, true);
            throw new IllegalStateException("No downstream affected distribution areas discovered for component: " + failedComponent);
        }

        // Step 6 & 7: Identify consumers connected to those areas and count them
        int affectedConsumerCount = countAffectedConsumers(affectedAreas);

        // Step 8 & 10: Identify critical facilities and calculate deterministic priority
        OutagePriorityEvaluation eval = evaluateOutagePriority(affectedAreas, affectedConsumerCount);

        // Step 9: Create outage record
        String faultType = failedComponent.startsWith("DT-")
            ? "Transformer Core Overload & Oil Breakdown"
            : "High-Tension Line Fault & Circuit Breaker Trip";

        Outage outage = new Outage(
            nextOutageId,
            failedComponent,
            faultType,
            affectedAreas,
            affectedConsumerCount
        );

        outage.setPriority(eval.getPriority());
        outage.setPriorityTier(eval.getPriorityTier());
        outage.setCriticalFacilities(eval.getCriticalFacilities());
        outage.setPriorityReason(eval.getPriorityReason());
        outage.setQueueSequence(nextQueueSequence);
        outage.setStatus("ACTIVE");

        // Step 12: Persist outage in PostgreSQL FIRST using atomic transaction
        // Memory state is updated only AFTER successful database commit to ensure complete consistency
        try {
            List<Integer> affectedIds = consumerDAO.getConsumerIdsByAreas(affectedAreas, null);
            outageDAO.insertOutageTransaction(outage, affectedIds);
        } catch (SQLException e) {
            // Rollback node status on failure; no other in-memory state was modified yet
            setNodeStatus(failedComponent, true);
            String sanitized = DatabaseConnection.sanitizeErrorMessage(e.getMessage());
            System.err.println("[PowerTrack Database Error] Failed to persist outage to Supabase: " + sanitized);
            throw new RuntimeException("Database error persisting outage to Supabase: " + sanitized, e);
        }

        // Database transaction committed successfully — now apply in-memory updates
        nextQueueSequence++;
        updateConsumerPower(affectedAreas);
        outageQueue.enqueue(outage);
        addOutage(outage);
        nextOutageId++;

        return outage;
    }

    // =========================================
    // DYNAMIC DOWNSTREAM AREA TRAVERSAL
    // =========================================

    public ArrayList<String> findAffectedAreas(String failedComponent) {
        return electricityGraph.getDownstreamAreas(failedComponent);
    }

    private int countAffectedConsumers(ArrayList<String> affectedAreas) {
        if (affectedAreas == null || affectedAreas.isEmpty()) return 0;
        HashSet<String> areaSet = new HashSet<>(affectedAreas);
        int count = 0;
        for (int i = 0; i < consumerCount; i++) {
            if (consumers[i] != null && areaSet.contains(consumers[i].getArea())) {
                count++;
            }
        }
        return count;
    }

    private void updateConsumerPower(ArrayList<String> affectedAreas) {
        if (affectedAreas == null || affectedAreas.isEmpty()) return;
        HashSet<String> areaSet = new HashSet<>(affectedAreas);
        for (int i = 0; i < consumerCount; i++) {
            if (consumers[i] != null && areaSet.contains(consumers[i].getArea())) {
                consumers[i].setPowerAvailable(false);
            }
        }
    }

    private void restoreConsumerPower(ArrayList<String> affectedAreas) {
        if (affectedAreas == null || affectedAreas.isEmpty()) return;
        HashSet<String> areaSet = new HashSet<>(affectedAreas);
        for (int i = 0; i < consumerCount; i++) {
            if (consumers[i] != null && areaSet.contains(consumers[i].getArea())) {
                consumers[i].setPowerAvailable(true);
            }
        }
    }

    // =========================================
    // DETERMINISTIC 5-TIER PRIORITY EVALUATION
    // =========================================

    public static class OutagePriorityEvaluation {
        private final String priority; // "HIGH", "MEDIUM", "LOW"
        private final int priorityTier; // 5 down to 1
        private final ArrayList<String> criticalFacilities;
        private final String priorityReason;

        public OutagePriorityEvaluation(String priority, int priorityTier, ArrayList<String> criticalFacilities, String priorityReason) {
            this.priority = priority;
            this.priorityTier = priorityTier;
            this.criticalFacilities = criticalFacilities != null ? criticalFacilities : new ArrayList<>();
            this.priorityReason = priorityReason != null ? priorityReason : "";
        }

        public String getPriority() {
            return priority;
        }

        public int getPriorityTier() {
            return priorityTier;
        }

        public ArrayList<String> getCriticalFacilities() {
            return criticalFacilities;
        }

        public String getPriorityReason() {
            return priorityReason;
        }
    }

    /**
     * Evaluates outage priority strictly following the required deterministic order:
     * Tier 5: Hospitals and emergency medical facilities (HIGH)
     * Tier 4: Emergency services and critical infrastructure (HIGH)
     * Tier 3: Essential public services & education (MEDIUM)
     * Tier 2: Residential consumers (LOW)
     * Tier 1: Commercial & industrial consumers (LOW)
     */
    public OutagePriorityEvaluation evaluateOutagePriority(ArrayList<String> affectedAreas, int affectedConsumerCount) {
        ArrayList<String> criticalFacilities = new ArrayList<>();
        ArrayList<String> tier5Facilities = new ArrayList<>();
        ArrayList<String> tier4Facilities = new ArrayList<>();
        ArrayList<String> tier3Facilities = new ArrayList<>();

        int highestTier = 1;

        HashSet<String> areaSet = new HashSet<>(affectedAreas != null ? affectedAreas : new ArrayList<>());
        for (int i = 0; i < consumerCount; i++) {
            Consumer c = consumers[i];
            if (c != null && areaSet.contains(c.getArea())) {
                int tier = c.getCriticalityTier();

                if (tier == 5) {
                    tier5Facilities.add(c.getName());
                    criticalFacilities.add("[HOSPITAL] " + c.getName());
                    if (highestTier < 5) highestTier = 5;
                } else if (tier == 4) {
                    tier4Facilities.add(c.getName());
                    criticalFacilities.add("[EMERGENCY] " + c.getName());
                    if (highestTier < 4) highestTier = 4;
                } else if (tier == 3) {
                    tier3Facilities.add(c.getName());
                    criticalFacilities.add("[PUBLIC] " + c.getName());
                    if (highestTier < 3) highestTier = 3;
                } else if (tier == 2) {
                    if (highestTier < 2) highestTier = 2;
                }
            }
        }

        String priority;
        String priorityReason;

        if (highestTier == 5) {
            priority = "HIGH";
            priorityReason = "HIGH PRIORITY (Tier 1 of Restoration Queue): Hospital and emergency medical facilities affected ("
                + String.join(", ", tier5Facilities) + "). Immediate priority override applied.";
        } else if (highestTier == 4) {
            priority = "HIGH";
            priorityReason = "HIGH PRIORITY (Tier 2 of Restoration Queue): Emergency services and critical public infrastructure affected ("
                + String.join(", ", tier4Facilities) + ").";
        } else if (highestTier == 3) {
            priority = "MEDIUM";
            priorityReason = "MEDIUM PRIORITY (Tier 3 of Restoration Queue): Essential public services and community education facilities affected ("
                + String.join(", ", tier3Facilities) + ").";
        } else if (highestTier == 2) {
            priority = "LOW";
            priorityReason = "LOW PRIORITY (Tier 4 of Restoration Queue): Domestic residential connections affected ("
                + affectedConsumerCount + " households).";
        } else {
            priority = "LOW";
            priorityReason = "LOW PRIORITY (Tier 5 of Restoration Queue): Commercial and industrial loads affected.";
        }

        return new OutagePriorityEvaluation(priority, highestTier, criticalFacilities, priorityReason);
    }

    private void setNodeStatus(String nodeId, boolean status) {
        if (nodeId == null) return;
        for (int i = 0; i < networkNodeCount; i++) {
            if (networkNodes[i] != null && networkNodes[i].getNodeId().equalsIgnoreCase(nodeId)) {
                networkNodes[i].setActive(status);
                break;
            }
        }
    }

    // =========================================
    // PROCESS NEXT OUTAGE (DISPATCH)
    // =========================================

    public synchronized Outage processNextOutage() {
        if (outageQueue.isEmpty()) {
            return null;
        }

        Outage outage = outageQueue.dequeue();

        // Find an available technician matching specialization
        Technician technician = findAvailableTechnician(outage.getFailedComponent());

        if (technician == null) {
            // Re-enqueue outage at front of priority queue
            outageQueue.enqueue(outage);
            return null;
        }

        // Persist assignment to Supabase FIRST (Atomic Transaction)
        try {
            outageDAO.assignTechnicianTransaction(
                outage.getOutageId(),
                technician.getTechnicianId(),
                technician.getName()
            );
        } catch (SQLException e) {
            // Re-enqueue outage on database failure; in-memory status was not modified
            outageQueue.enqueue(outage);
            String sanitized = DatabaseConnection.sanitizeErrorMessage(e.getMessage());
            System.err.println("[PowerTrack Database Error] Failed to persist dispatch to Supabase: " + sanitized);
            throw new RuntimeException("Database error persisting dispatch to Supabase: " + sanitized, e);
        }

        // Database commit succeeded — now apply in-memory state updates
        technician.setAvailable(false);
        outage.setAssignedTechnician(technician.getName());
        outage.setAssignedTechnicianId(technician.getTechnicianId());
        outage.setStatus("IN PROGRESS");

        return outage;
    }

    // =========================================
    // FIND AVAILABLE TECHNICIAN
    // =========================================

    private Technician findAvailableTechnician(String failedComponent) {
        String requiredSpecialization = (failedComponent.startsWith("DT-") || failedComponent.startsWith("T"))
            ? "Transformers"
            : "Electrical Lines";

        // First pass: exact technical specialization & available
        for (int i = 0; i < technicianCount; i++) {
            if (technicians[i] != null
                    && technicians[i].isAvailable()
                    && technicians[i].getSpecialization().equalsIgnoreCase(requiredSpecialization)) {
                return technicians[i];
            }
        }

        // Second pass: fallback to compatible general Distribution specialists
        for (int i = 0; i < technicianCount; i++) {
            if (technicians[i] != null
                    && technicians[i].isAvailable()
                    && technicians[i].getSpecialization().equalsIgnoreCase("Distribution")) {
                return technicians[i];
            }
        }

        // Do not assign technicians with unrelated specializations (e.g. linesmen to transformers)
        return null;
    }

    // =========================================
    // RESTORE OUTAGE
    // =========================================

    public synchronized void restoreOutage(int outageId) {
        restoreOutage(outageId, null);
    }

    public synchronized void restoreOutage(int outageId, String restorationDetails) {
        Outage outage = findOutageById(outageId);

        if (outage == null) {
            throw new IllegalArgumentException("Outage OUT-" + outageId + " not found.");
        }

        if ("RESTORED".equalsIgnoreCase(outage.getStatus())) {
            throw new IllegalStateException("Outage OUT-" + outageId + " is already restored.");
        }

        if (!"IN PROGRESS".equalsIgnoreCase(outage.getStatus())) {
            throw new IllegalStateException("Outage OUT-" + outageId + " must be IN PROGRESS before restoration (Current status: " + outage.getStatus() + ").");
        }

        // Gather restoration metadata prior to transaction
        String failedComponent = outage.getFailedComponent();
        ArrayList<String> affectedAreas = outage.getAffectedAreas();
        String technicianName = outage.getAssignedTechnician();
        Integer technicianId = outage.getAssignedTechnicianId();

        // Resolve technician ID from name if needed
        if (technicianId == null && technicianName != null && !"NOT ASSIGNED".equalsIgnoreCase(technicianName)) {
            for (int i = 0; i < technicianCount; i++) {
                if (technicians[i] != null && technicians[i].getName().equalsIgnoreCase(technicianName)) {
                    technicianId = technicians[i].getTechnicianId();
                    break;
                }
            }
        }

        // 1. Persist restoration to Supabase FIRST (Atomic Database Transaction)
        // Memory state is updated ONLY after the database confirms commit
        try {
            String details = (restorationDetails != null && !restorationDetails.trim().isEmpty())
                ? restorationDetails.trim()
                : ("Component " + failedComponent + " re-energized. Normal power supply restored to downstream consumers.");

            outageDAO.restoreOutageTransaction(
                outageId,
                failedComponent,
                affectedAreas,
                technicianId,
                technicianName,
                details
            );
        } catch (SQLException e) {
            String sanitized = DatabaseConnection.sanitizeErrorMessage(e.getMessage());
            System.err.println("[PowerTrack Database Error] Failed to persist restoration to Supabase: " + sanitized);
            throw new RuntimeException("Database error persisting restoration to Supabase: " + sanitized, e);
        }

        // 2. Database commit succeeded — now update all in-memory state
        setNodeStatus(failedComponent, true);
        restoreConsumerPower(affectedAreas);

        // Release technician back to available
        for (int i = 0; i < technicianCount; i++) {
            if (technicians[i] != null && (technicians[i].getName().equalsIgnoreCase(technicianName)
                    || (technicianId != null && technicians[i].getTechnicianId() == technicianId))) {
                technicians[i].setAvailable(true);
                break;
            }
        }

        // Update outage record in memory
        outage.setStatus("RESTORED");
        outage.setRestoredAt(new java.sql.Timestamp(System.currentTimeMillis()));

        // Remove from queue if still present
        outageQueue.remove(outage);
    }

    // =========================================
    // DIRECT TECHNICIAN ASSIGNMENT
    // =========================================

    public synchronized Outage assignTechnicianToOutage(int outageId, int technicianId) {
        Outage outage = findOutageById(outageId);
        if (outage == null) {
            throw new IllegalArgumentException("Outage OUT-" + outageId + " not found.");
        }

        if ("RESTORED".equalsIgnoreCase(outage.getStatus())) {
            throw new IllegalStateException("Outage OUT-" + outageId + " is already completed and restored.");
        }

        Technician technician = findTechnicianById(technicianId);
        if (technician == null) {
            throw new IllegalArgumentException("Technician ID " + technicianId + " not found.");
        }

        // Prevent one technician from receiving multiple active assignments
        if (!technician.isAvailable()) {
            throw new IllegalStateException("Technician " + technician.getName() + " is already assigned to another active outage. One technician cannot hold multiple active assignments.");
        }

        // Persist assignment to Supabase FIRST (Atomic Database Transaction)
        try {
            outageDAO.assignTechnicianTransaction(
                outage.getOutageId(),
                technician.getTechnicianId(),
                technician.getName()
            );
        } catch (SQLException e) {
            String sanitized = DatabaseConnection.sanitizeErrorMessage(e.getMessage());
            System.err.println("[PowerTrack Database Error] Failed to persist assignment to Supabase: " + sanitized);
            throw new RuntimeException("Database error persisting assignment to Supabase: " + sanitized, e);
        }

        // Database commit succeeded — now update in-memory state
        technician.setAvailable(false);
        outage.setAssignedTechnician(technician.getName());
        outage.setAssignedTechnicianId(technician.getTechnicianId());
        outage.setStatus("IN PROGRESS");

        // Remove from priority restoration queue
        outageQueue.remove(outage);

        return outage;
    }

    public synchronized Outage findOutageById(int outageId) {
        for (int i = 0; i < outageCount; i++) {
            if (outages[i] != null && outages[i].getOutageId() == outageId) {
                return outages[i];
            }
        }
        return null;
    }

    public synchronized Technician findTechnicianById(int technicianId) {
        for (int i = 0; i < technicianCount; i++) {
            if (technicians[i] != null && technicians[i].getTechnicianId() == technicianId) {
                return technicians[i];
            }
        }
        return null;
    }

    // =========================================
    // CLI DEMO HELPERS & ACCESSORS
    // =========================================

    public synchronized void displayConsumers() {
        System.out.println("\n========== REGISTERED CONSUMERS (TAMIL NADU GRID) ==========");
        if (consumerCount == 0) {
            System.out.println("No consumers found.");
            return;
        }
        for (int i = 0; i < consumerCount; i++) {
            if (consumers[i] != null) consumers[i].display();
        }
    }

    public synchronized void displayNetwork() {
        System.out.println("\n========== ELECTRICITY NETWORK (ERODE-TIRUPPUR REGION) ==========");
        for (int i = 0; i < networkNodeCount; i++) {
            if (networkNodes[i] != null) networkNodes[i].display();
        }
        electricityGraph.displayGraph();
    }

    public synchronized void displayAvailableComponents() {
        System.out.println("Available Distribution Components (from Supabase):");
        boolean found = false;
        for (int i = 0; i < networkNodeCount; i++) {
            NetworkNode n = networkNodes[i];
            if (n != null && ("FEEDER".equalsIgnoreCase(n.getNodeType()) || "TRANSFORMER".equalsIgnoreCase(n.getNodeType()))) {
                System.out.printf("  %-12s - %s [%s]%n", n.getNodeId(), n.getNodeName(), (n.isActive() ? "OPERATIONAL" : "FAULT / OFFLINE"));
                found = true;
            }
        }
        if (!found) {
            System.out.println("  (No distribution feeders or transformers found in network)");
        }
    }

    public synchronized void displayTechnicians() {
        System.out.println("\n========== FIELD TECHNICIANS ROSTER ==========");
        if (technicianCount == 0) {
            System.out.println("No technicians found.");
            return;
        }
        for (int i = 0; i < technicianCount; i++) {
            if (technicians[i] != null) technicians[i].display();
        }
    }

    public synchronized void displayActiveOutages() {
        System.out.println("\n========== ACTIVE OUTAGES & RESTORATION QUEUE ==========");
        boolean found = false;
        for (int i = 0; i < outageCount; i++) {
            if (outages[i] != null && !outages[i].getStatus().equals("RESTORED")) {
                outages[i].display();
                found = true;
            }
        }
        if (!found) {
            System.out.println("No active outages. All grid circuits operational.");
        }
    }

    public synchronized void displayOutageHistory() {
        System.out.println("\n========== RESTORATION HISTORY AUDIT LOG ==========");
        if (outageCount == 0) {
            System.out.println("No outage records found.");
            return;
        }
        for (int i = 0; i < outageCount; i++) {
            if (outages[i] != null) outages[i].display();
        }
    }

    public synchronized void displayOutageQueue() {
        System.out.println("\n========== OUTAGE QUEUE STATUS ==========");
        System.out.println("Queue Size: " + outageQueue.size());
        outageQueue.display();
    }

    public synchronized int getQueuePosition(Outage outage) {
        if (outage == null) return -1;
        return outageQueue.getPosition(outage);
    }

    public Queue<Outage> getOutageQueue() {
        return outageQueue;
    }

    public Graph getElectricityGraph() {
        return electricityGraph;
    }

    public boolean isDatabaseConnected() {
        return databaseConnected;
    }

    public StatsDAO getStatsDAO() {
        return statsDAO;
    }

    public OutageDAO getOutageDAO() {
        return outageDAO;
    }

    public ConsumerDAO getConsumerDAO() {
        return consumerDAO;
    }

    public NetworkDAO getNetworkDAO() {
        return networkDAO;
    }

    public TechnicianDAO getTechnicianDAO() {
        return technicianDAO;
    }
}