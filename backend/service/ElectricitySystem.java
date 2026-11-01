package service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

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
 * Essential Data Structures Used:
 *   1. Queue (datastructure.Queue<Outage>):
 *      - Priority Queue for managing active grid outage tickets.
 *      - Ensures outages affecting Hospitals and Emergency services are dequeued first.
 *   2. Graph (datastructure.Graph):
 *      - Custom Adjacency List representing regional grid assets (substations, feeders, lines).
 *      - Provides BFS and DFS algorithms for downstream fault impact tracing.
 *   3. ArrayList (java.util.ArrayList):
 *      - Used throughout the system for dynamic collections (affected locality areas,
 *        critical facility lists, queued outage items, and database records).
 *   4. HashMap (java.util.HashMap):
 *      - Used for O(1) asset lookups, connection mapping, and neighbor indexing.
 *
 * Outage Priority Logic:
 *   - HIGH: Essential facilities affected (Hospitals, Emergency Healthcare).
 *   - MEDIUM: Commercial, industrial, or educational institutions affected.
 *   - LOW: Standard residential consumers.
 * ==============================================================================
 */
public class ElectricitySystem {

    private Consumer[] consumers;
    private NetworkNode[] networkNodes;
    private Outage[] outages;
    private Technician[] technicians;

    // Custom Data Structures: Priority Queue and Grid Topology Graph
    private Queue<Outage> outageQueue;
    private Graph electricityGraph;

    private int consumerCount;
    private int networkNodeCount;
    private int outageCount;
    private int technicianCount;

    private int nextOutageId;
    private int nextQueueSequence;

    // Currently processed outage
    private Outage currentOutage;

    // Database DAO & connectivity
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

        consumers = new Consumer[100];
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

        currentOutage = null;

        initializeSystem();
    }

    // =========================================
    // INITIALIZATION & DATABASE SYNC
    // =========================================

    private void initializeSystem() {
        if (!DatabaseConnection.isConfigured()) {
            String err = "[PowerTrack Fatal Error] Supabase database credentials are not configured.\n"
                    + "Environment variables SUPABASE_DB_URL, SUPABASE_DB_USER, and SUPABASE_DB_PASSWORD are required.\n"
                    + "In-memory offline fallback is completely disabled. Startup halted.";
            System.err.println(err);
            throw new IllegalStateException(err);
        }

        if (!DatabaseConnection.testConnection()) {
            String err = "[PowerTrack Fatal Error] Cannot connect to Supabase PostgreSQL database.\n"
                    + "Please verify database status, network connectivity, and credentials.\n"
                    + "In-memory offline fallback is completely disabled. Startup halted.";
            System.err.println(err);
            throw new IllegalStateException(err);
        }

        try {
            System.out.println();
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

    private void loadFromDatabase() throws SQLException {
        // Reset in-memory structures to mirror Supabase database directly
        consumerCount = 0;
        networkNodeCount = 0;
        outageCount = 0;
        technicianCount = 0;
        outageQueue = new Queue<>();
        electricityGraph = new Graph();

        // 1. Network nodes from Supabase
        List<NetworkNode> dbNodes = networkDAO.getAllNodes();
        for (NetworkNode node : dbNodes) {
            addNetworkNode(node);
        }

        // 2. Network connections into Graph from Supabase
        List<NetworkConnection> dbConns = networkDAO.getAllConnections();
        for (NetworkConnection conn : dbConns) {
            electricityGraph.addEdge(conn.getSourceNodeId(), conn.getDestinationNodeId());
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

        // 5. Outages (both active queued outages and historical completed ones) from Supabase
        List<Outage> dbOutages = outageDAO.getAllOutages();
        int maxSeq = 0;
        for (Outage o : dbOutages) {
            addOutage(o);
            if (o.getQueueSequence() > maxSeq) {
                maxSeq = o.getQueueSequence();
            }

            if ("ACTIVE".equalsIgnoreCase(o.getStatus())) {
                outageQueue.enqueue(o);
            } else if ("IN PROGRESS".equalsIgnoreCase(o.getStatus())) {
                this.currentOutage = o;
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

    public void addConsumer(Consumer consumer) {
        if (consumerCount >= consumers.length) {
            consumers = Arrays.copyOf(consumers, consumers.length * 2);
        }
        consumers[consumerCount] = consumer;
        consumerCount++;
    }

    public void addNetworkNode(NetworkNode node) {
        if (networkNodeCount >= networkNodes.length) {
            networkNodes = Arrays.copyOf(networkNodes, networkNodes.length * 2);
        }
        networkNodes[networkNodeCount] = node;
        networkNodeCount++;
    }

    public void addTechnician(Technician technician) {
        if (technicianCount >= technicians.length) {
            technicians = Arrays.copyOf(technicians, technicians.length * 2);
        }
        technicians[technicianCount] = technician;
        technicianCount++;
    }

    public void addOutage(Outage outage) {
        if (outageCount >= outages.length) {
            outages = Arrays.copyOf(outages, outages.length * 2);
        }
        outages[outageCount] = outage;
        outageCount++;
    }

    public void displayConsumers() {
        System.out.println();
        System.out.println("========== REGISTERED CONSUMERS (TAMIL NADU GRID) ==========");
        if (consumerCount == 0) {
            System.out.println("No consumers found.");
            return;
        }
        for (int i = 0; i < consumerCount; i++) {
            consumers[i].display();
        }
    }

    public void displayNetwork() {
        System.out.println();
        System.out.println("========== ELECTRICITY NETWORK (ERODE-TIRUPPUR REGION) ==========");
        for (int i = 0; i < networkNodeCount; i++) {
            networkNodes[i].display();
        }
        electricityGraph.displayGraph();
    }

    public void displayTechnicians() {
        System.out.println();
        System.out.println("========== FIELD TECHNICIANS ROSTER ==========");
        if (technicianCount == 0) {
            System.out.println("No technicians found.");
            return;
        }
        for (int i = 0; i < technicianCount; i++) {
            technicians[i].display();
        }
    }

    public void displayAvailableComponents() {
        System.out.println("Available Distribution Components (from Supabase):");
        boolean found = false;
        for (int i = 0; i < networkNodeCount; i++) {
            NetworkNode n = networkNodes[i];
            if ("FEEDER".equalsIgnoreCase(n.getNodeType()) || "TRANSFORMER".equalsIgnoreCase(n.getNodeType())) {
                System.out.printf("  %-12s - %s [%s]%n", n.getNodeId(), n.getNodeName(), (n.isActive() ? "OPERATIONAL" : "FAULT / OFFLINE"));
                found = true;
            }
        }
        if (!found) {
            System.out.println("  (No distribution feeders or transformers found in network)");
        }
    }

    // =========================================
    // SIMULATE FAULT
    // =========================================

    public void simulateFault(String failedComponent) {

        // Normalize legacy shorthand aliases
        if (failedComponent.equalsIgnoreCase("F1")) failedComponent = "FD-ERD-01";
        else if (failedComponent.equalsIgnoreCase("F2")) failedComponent = "FD-PER-02";
        else if (failedComponent.equalsIgnoreCase("T1")) failedComponent = "DT-ERD-101";
        else if (failedComponent.equalsIgnoreCase("T2")) failedComponent = "DT-PER-102";

        if (!electricityGraph.containsNode(failedComponent)) {
            System.out.println();
            System.out.println("Invalid network component ID: " + failedComponent);
            return;
        }

        if (
            failedComponent.startsWith("SS-") ||
            failedComponent.startsWith("AREA-")
        ) {
            System.out.println();
            System.out.println("Please select a distribution feeder (FD-...) or transformer (DT-...).");
            return;
        }

        System.out.println();
        System.out.println("========== FAULT DETECTION ENGINE ==========");
        System.out.println("Failed Component: " + failedComponent);

        // -----------------------------------------
        // Mark failed component
        // -----------------------------------------
        setNodeStatus(failedComponent, false);

        // -----------------------------------------
        // Determine affected areas (using BFS graph algorithm)
        // -----------------------------------------
        ArrayList<String> affectedAreas = findAffectedAreas(failedComponent);

        if (affectedAreas.isEmpty()) {
            System.out.println("No downstream affected consumer areas detected.");
            setNodeStatus(failedComponent, true);
            return;
        }

        // -----------------------------------------
        // Find affected consumers
        // -----------------------------------------
        int affectedConsumerCount = countAffectedConsumers(affectedAreas);

        // -----------------------------------------
        // Determine priority & evaluate critical facilities
        // -----------------------------------------
        OutagePriorityEvaluation eval = evaluateOutagePriority(affectedAreas, affectedConsumerCount);
        String priority = eval.getPriority();

        // -----------------------------------------
        // Determine fault classification
        // -----------------------------------------
        String faultType = failedComponent.startsWith("DT-")
            ? "Transformer Core Overload & Oil Breakdown"
            : "High-Tension Line Fault & Circuit Breaker Trip";

        // -----------------------------------------
        // Create outage
        // -----------------------------------------
        Outage outage = new Outage(
            nextOutageId,
            failedComponent,
            faultType,
            affectedAreas,
            affectedConsumerCount
        );

        outage.setPriority(priority);
        outage.setCriticalFacilities(eval.getCriticalFacilities());
        outage.setPriorityReason(eval.getPriorityReason());
        outage.setQueueSequence(nextQueueSequence++);

        // -----------------------------------------
        // Store outage in memory
        // -----------------------------------------
        addOutage(outage);
        nextOutageId++;

        // -----------------------------------------
        // Turn consumer power OFF
        // -----------------------------------------
        updateConsumerPower(affectedAreas);

        // -----------------------------------------
        // Add to priority queue
        // -----------------------------------------
        outageQueue.enqueue(outage);

        // -----------------------------------------
        // Persist to Supabase Database (Transaction)
        // -----------------------------------------
        try {
            List<Integer> affectedIds = consumerDAO.getConsumerIdsByAreas(affectedAreas, null);
            outageDAO.insertOutageTransaction(outage, affectedIds);
        } catch (SQLException e) {
            String sanitized = DatabaseConnection.sanitizeErrorMessage(e.getMessage());
            System.err.println("[PowerTrack Database Error] Failed to persist outage to Supabase: " + sanitized);
            throw new RuntimeException("Database error persisting outage to Supabase: " + sanitized, e);
        }

        // -----------------------------------------
        // Display detection result
        // -----------------------------------------
        int queuePos = outageQueue.getPosition(outage);

        System.out.println();
        System.out.println("Affected Areas: " + affectedAreas);
        System.out.println("Affected Consumers: " + affectedConsumerCount);
        System.out.println("Emergency Priority Level: [" + priority + "]");
        if (!eval.getCriticalFacilities().isEmpty()) {
            System.out.println("CRITICAL FACILITIES AFFECTED: " + eval.getCriticalFacilities());
        }
        System.out.println("Priority Reason: " + eval.getPriorityReason());
        System.out.println("Queue Position: #" + (queuePos > 0 ? queuePos : 1) + " (Priority Order)");
        System.out.println("Registered Outage ID: OUT-" + outage.getOutageId());
        System.out.println("Status: " + outage.getStatus());
        System.out.println();
        System.out.println("Outage successfully enqueued to priority restoration queue.");
    }

    // =========================================
    // FIND AFFECTED AREAS (TOPOLOGY TRAVERSAL)
    // =========================================

    private ArrayList<String> findAffectedAreas(String failedComponent) {
        ArrayList<String> affectedAreas = new ArrayList<>();

        if (failedComponent.equalsIgnoreCase("FD-ERD-01") || failedComponent.equalsIgnoreCase("DT-ERD-101")) {
            affectedAreas.add("AREA-ERD-PR");
            affectedAreas.add("AREA-ERD-SN");
        }
        else if (failedComponent.equalsIgnoreCase("FD-PER-02") || failedComponent.equalsIgnoreCase("DT-PER-102")) {
            affectedAreas.add("AREA-PER-SIP");
        }
        else if (failedComponent.equalsIgnoreCase("FD-CHN-03") || failedComponent.equalsIgnoreCase("DT-CHN-103")) {
            affectedAreas.add("AREA-CHN-TWN");
        }
        else if (failedComponent.equalsIgnoreCase("FD-AVI-04") || failedComponent.equalsIgnoreCase("DT-AVI-104")) {
            affectedAreas.add("AREA-AVI-BYP");
        }
        else if (failedComponent.equalsIgnoreCase("FD-TPR-05") || failedComponent.equalsIgnoreCase("DT-TPR-105")) {
            affectedAreas.add("AREA-TPR-RAY");
        }
        else {
            // General BFS traversal for any other connected area nodes
            ArrayList<String> bfsOrder = electricityGraph.bfs(failedComponent);
            for (String node : bfsOrder) {
                if (node.startsWith("AREA-")) {
                    affectedAreas.add(node);
                }
            }
        }

        return affectedAreas;
    }

    // =========================================
    // COUNT AFFECTED CONSUMERS
    // =========================================

    private int countAffectedConsumers(ArrayList<String> affectedAreas) {
        int count = 0;
        for (int i = 0; i < consumerCount; i++) {
            String consumerArea = consumers[i].getArea();
            if (affectedAreas.contains(consumerArea)) {
                count++;
            }
        }
        return count;
    }

    // =========================================
    // UPDATE CONSUMER POWER
    // =========================================

    private void updateConsumerPower(ArrayList<String> affectedAreas) {
        for (int i = 0; i < consumerCount; i++) {
            String area = consumers[i].getArea();
            if (affectedAreas.contains(area)) {
                consumers[i].setPowerAvailable(false);
            }
        }
    }

    // =========================================
    // RESTORE CONSUMER POWER
    // =========================================

    private void restoreConsumerPower(ArrayList<String> affectedAreas) {
        for (int i = 0; i < consumerCount; i++) {
            String area = consumers[i].getArea();
            if (affectedAreas.contains(area)) {
                consumers[i].setPowerAvailable(true);
            }
        }
    }

    // =========================================
    // EMERGENCY PRIORITY CALCULATION EVALUATION
    // =========================================

    public static class OutagePriorityEvaluation {
        private String priority;
        private ArrayList<String> criticalFacilities;
        private String priorityReason;

        public OutagePriorityEvaluation(String priority, ArrayList<String> criticalFacilities, String priorityReason) {
            this.priority = priority;
            this.criticalFacilities = criticalFacilities != null ? criticalFacilities : new ArrayList<>();
            this.priorityReason = priorityReason != null ? priorityReason : "";
        }

        public String getPriority() {
            return priority;
        }

        public ArrayList<String> getCriticalFacilities() {
            return criticalFacilities;
        }

        public String getPriorityReason() {
            return priorityReason;
        }
    }

    public OutagePriorityEvaluation evaluateOutagePriority(ArrayList<String> affectedAreas, int affectedConsumerCount) {
        ArrayList<String> criticalFacilities = new ArrayList<>();
        ArrayList<String> highFacilities = new ArrayList<>();
        ArrayList<String> mediumFacilities = new ArrayList<>();
        ArrayList<String> lowFacilities = new ArrayList<>();

        int highestScore = 0; // 3 = HIGH, 2 = MEDIUM, 1 = LOW

        for (int i = 0; i < consumerCount; i++) {
            Consumer c = consumers[i];
            if (affectedAreas.contains(c.getArea())) {
                String level = c.getCriticalityLevel();
                if (level == null || level.trim().isEmpty() || "DEFAULT".equalsIgnoreCase(level)) {
                    level = Consumer.inferCriticalityLevel(c.getName(), c.getConsumerType());
                }

                if ("HIGH".equalsIgnoreCase(level) || "CRITICAL".equalsIgnoreCase(level)) {
                    highFacilities.add(c.getName());
                    criticalFacilities.add("[HIGH] " + c.getName());
                    if (highestScore < 3) highestScore = 3;
                } else if ("MEDIUM".equalsIgnoreCase(level)) {
                    mediumFacilities.add(c.getName());
                    criticalFacilities.add("[MEDIUM] " + c.getName());
                    if (highestScore < 2) highestScore = 2;
                } else {
                    lowFacilities.add(c.getName());
                    if (highestScore < 1) highestScore = 1;
                }
            }
        }

        String priority;
        String priorityReason;

        if (highestScore == 3) {
            priority = "HIGH";
            priorityReason = "HIGH PRIORITY: Essential facilities affected (" + String.join(", ", highFacilities)
                + "). Priority assigned from highest consumer criticality level.";
        } else if (highestScore == 2) {
            priority = "MEDIUM";
            priorityReason = "MEDIUM PRIORITY: Commercial, industrial, or educational facilities affected ("
                + String.join(", ", mediumFacilities) + "). Priority assigned from highest consumer criticality level.";
        } else {
            priority = "LOW";
            priorityReason = "LOW PRIORITY: Ordinary residential consumers and non-critical loads affected.";
        }

        return new OutagePriorityEvaluation(priority, criticalFacilities, priorityReason);
    }

    // =========================================
    // NODE STATUS
    // =========================================

    private void setNodeStatus(String nodeId, boolean status) {
        for (int i = 0; i < networkNodeCount; i++) {
            if (networkNodes[i].getNodeId().equalsIgnoreCase(nodeId)) {
                networkNodes[i].setActive(status);
                return;
            }
        }
    }

    // =========================================
    // ACTIVE OUTAGES
    // =========================================

    public void displayActiveOutages() {
        System.out.println();
        System.out.println("========== ACTIVE OUTAGES & RESTORATION QUEUE ==========");

        boolean found = false;

        for (int i = 0; i < outageCount; i++) {
            if (!outages[i].getStatus().equals("RESTORED")) {
                Outage o = outages[i];
                int pos = outageQueue.getPosition(o);

                System.out.println();
                System.out.println("Outage ID: OUT-" + o.getOutageId());
                System.out.println("Fault Location: " + o.getFailedComponent());
                System.out.println("Affected Areas: " + o.getAffectedAreas());
                System.out.println("Affected Consumers: " + o.getAffectedConsumers());
                System.out.println("Priority Level: [" + o.getPriority() + "]");
                if (o.getCriticalFacilities() != null && !o.getCriticalFacilities().isEmpty()) {
                    System.out.println("CRITICAL FACILITIES AFFECTED: " + o.getCriticalFacilities());
                }
                if (o.getPriorityReason() != null && !o.getPriorityReason().isEmpty()) {
                    System.out.println("Priority Reason: " + o.getPriorityReason());
                }
                if (pos > 0) {
                    System.out.println("Queue Position: #" + pos + (pos == 1 ? " (Next for Dispatch)" : ""));
                } else if ("IN PROGRESS".equalsIgnoreCase(o.getStatus())) {
                    System.out.println("Queue Position: DISPATCHED (Crew On Site)");
                }
                System.out.println("Assigned Technician: " + o.getAssignedTechnician());
                System.out.println("Current Status: " + o.getStatus());

                found = true;
            }
        }

        if (!found) {
            System.out.println("No active outages. All grid circuits operational.");
        }
    }

    // =========================================
    // PROCESS NEXT OUTAGE
    // =========================================

    public void processNextOutage() {

        if (outageQueue.isEmpty()) {
            System.out.println();
            System.out.println("No outages are waiting in the queue.");
            return;
        }

        Outage outage = outageQueue.dequeue();

        System.out.println();
        System.out.println("========== PROCESSING HIGHEST PRIORITY OUTAGE FOR DISPATCH ==========");
        System.out.println("Outage ID: OUT-" + outage.getOutageId());
        System.out.println("Failed Component: " + outage.getFailedComponent());
        System.out.println("Priority Level: [" + outage.getPriority() + "]");
        if (outage.getCriticalFacilities() != null && !outage.getCriticalFacilities().isEmpty()) {
            System.out.println("CRITICAL FACILITIES AFFECTED: " + outage.getCriticalFacilities());
        }
        System.out.println("Priority Reason: " + outage.getPriorityReason());
        System.out.println("Affected Consumers: " + outage.getAffectedConsumers());

        // -----------------------------------------
        // Assign technician
        // -----------------------------------------
        Technician technician = findAvailableTechnician(outage.getFailedComponent());

        if (technician == null) {
            System.out.println();
            System.out.println("No field technician is currently available in this zone.");
            System.out.println("Outage OUT-" + outage.getOutageId() + " retained at top of priority queue.");
            // Re-enqueue outage to maintain priority order
            outageQueue.enqueue(outage);
            return;
        }

        technician.setAvailable(false);
        outage.setAssignedTechnician(technician.getName());
        outage.setAssignedTechnicianId(technician.getTechnicianId());
        outage.setStatus("IN PROGRESS");
        currentOutage = outage;

        // -----------------------------------------
        // Persist assignment to Supabase (Transaction)
        // -----------------------------------------
        try {
            outageDAO.assignTechnicianTransaction(
                outage.getOutageId(),
                technician.getTechnicianId(),
                technician.getName()
            );
        } catch (SQLException e) {
            String sanitized = DatabaseConnection.sanitizeErrorMessage(e.getMessage());
            System.err.println("[PowerTrack Database Error] Failed to persist dispatch to Supabase: " + sanitized);
            outageQueue.enqueue(outage); // Retain outage in queue
            throw new RuntimeException("Database error persisting dispatch to Supabase: " + sanitized, e);
        }

        System.out.println();
        System.out.println("Technician Dispatched: " + technician.getName());
        System.out.println("Specialization: " + technician.getSpecialization());
        System.out.println();
        System.out.println("Outage Status: IN PROGRESS (Crew En Route)");
    }

    // =========================================
    // FIND AVAILABLE TECHNICIAN
    // =========================================

    private Technician findAvailableTechnician(String failedComponent) {

        String requiredSpecialization;

        if (failedComponent.startsWith("DT-") || failedComponent.startsWith("T")) {
            requiredSpecialization = "Transformers";
        }
        else {
            requiredSpecialization = "Electrical Lines";
        }

        // First pass: match exact technical specialization
        for (int i = 0; i < technicianCount; i++) {
            if (
                technicians[i].isAvailable() &&
                technicians[i].getSpecialization().equalsIgnoreCase(requiredSpecialization)
            ) {
                return technicians[i];
            }
        }

        // Second pass: fallback to Distribution specialists
        for (int i = 0; i < technicianCount; i++) {
            if (
                technicians[i].isAvailable() &&
                technicians[i].getSpecialization().equalsIgnoreCase("Distribution")
            ) {
                return technicians[i];
            }
        }

        // Third pass: any available technician
        for (int i = 0; i < technicianCount; i++) {
            if (technicians[i].isAvailable()) {
                return technicians[i];
            }
        }

        return null;
    }

    // =========================================
    // RESTORE OUTAGE
    // =========================================

    public void restoreOutage(int outageId) {
        restoreOutage(outageId, null);
    }

    public void restoreOutage(int outageId, String restorationDetails) {

        Outage outage = findOutageById(outageId);

        if (outage == null) {
            System.out.println();
            System.out.println("Outage ID " + outageId + " not found.");
            return;
        }

        if (!outage.getStatus().equals("IN PROGRESS")) {
            System.out.println();
            System.out.println("Outage must be IN PROGRESS before restoration.");
            return;
        }

        System.out.println();
        System.out.println("========== GRID RESTORATION OPERATION ==========");
        System.out.println("Outage ID: " + outage.getOutageId());
        System.out.println("Component: " + outage.getFailedComponent());

        // -----------------------------------------
        // Restore network component
        // -----------------------------------------
        setNodeStatus(outage.getFailedComponent(), true);

        // -----------------------------------------
        // Restore affected consumers
        // -----------------------------------------
        restoreConsumerPower(outage.getAffectedAreas());

        // -----------------------------------------
        // Make technician available
        // -----------------------------------------
        String technicianName = outage.getAssignedTechnician();
        Integer technicianId = outage.getAssignedTechnicianId();

        for (int i = 0; i < technicianCount; i++) {
            if (technicians[i] != null && (technicians[i].getName().equals(technicianName)
                    || (technicianId != null && technicians[i].getTechnicianId() == technicianId))) {
                technicians[i].setAvailable(true);
                if (technicianId == null) {
                    technicianId = technicians[i].getTechnicianId();
                }
                break;
            }
        }

        // -----------------------------------------
        // Update outage
        // -----------------------------------------
        outage.setStatus("RESTORED");
        outage.setRestoredAt(new java.sql.Timestamp(System.currentTimeMillis()));
        if (currentOutage != null && currentOutage.getOutageId() == outageId) {
            currentOutage = null;
        }

        // -----------------------------------------
        // Persist restoration to Supabase (Transaction)
        // -----------------------------------------
        try {
            String details = (restorationDetails != null && !restorationDetails.trim().isEmpty())
                ? restorationDetails.trim()
                : ("Component " + outage.getFailedComponent() + " re-energized. Normal power supply restored.");
            outageDAO.restoreOutageTransaction(
                outageId,
                outage.getFailedComponent(),
                outage.getAffectedAreas(),
                technicianId,
                technicianName,
                details
            );
        } catch (SQLException e) {
            String sanitized = DatabaseConnection.sanitizeErrorMessage(e.getMessage());
            System.err.println("[PowerTrack Database Error] Failed to persist restoration to Supabase: " + sanitized);
            throw new RuntimeException("Database error persisting restoration to Supabase: " + sanitized, e);
        }

        System.out.println();
        System.out.println("Network Component: ACTIVE");
        System.out.println("Affected Areas: " + outage.getAffectedAreas() + " -> POWER RESTORED");
        System.out.println("Technician " + technicianName + " -> AVAILABLE");
        System.out.println();
        System.out.println("Outage OUT-" + outageId + " -> RESTORED & ARCHIVED");
    }

    // =========================================
    // DIRECT TECHNICIAN ASSIGNMENT
    // =========================================

    public Outage assignTechnicianToOutage(int outageId, int technicianId) {
        Outage outage = findOutageById(outageId);
        if (outage == null) {
            return null;
        }

        Technician technician = findTechnicianById(technicianId);
        if (technician == null) {
            return null;
        }

        technician.setAvailable(false);
        outage.setAssignedTechnician(technician.getName());
        outage.setAssignedTechnicianId(technician.getTechnicianId());
        outage.setStatus("IN PROGRESS");
        currentOutage = outage;

        // Remove from priority queue
        outageQueue.remove(outage);

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

        return outage;
    }

    public Outage findOutageById(int outageId) {
        for (int i = 0; i < outageCount; i++) {
            if (outages[i] != null && outages[i].getOutageId() == outageId) {
                return outages[i];
            }
        }
        return null;
    }

    public Technician findTechnicianById(int technicianId) {
        for (int i = 0; i < technicianCount; i++) {
            if (technicians[i] != null && technicians[i].getTechnicianId() == technicianId) {
                return technicians[i];
            }
        }
        return null;
    }

    // =========================================
    // OUTAGE HISTORY
    // =========================================

    public void displayOutageHistory() {

        System.out.println();
        System.out.println("========== RESTORATION HISTORY AUDIT LOG ==========");

        if (outageCount == 0) {
            System.out.println("No outage records found.");
            return;
        }

        for (int i = 0; i < outageCount; i++) {
            outages[i].display();
        }
    }

    // =========================================
    // QUEUE DISPLAY & ACCESSORS
    // =========================================

    public void displayOutageQueue() {

        System.out.println();
        System.out.println("========== OUTAGE QUEUE STATUS ==========");
        System.out.println("Queue Size: " + outageQueue.size());
        outageQueue.display();
    }

    public int getQueuePosition(Outage outage) {
        if (outage == null) {
            return -1;
        }
        return outageQueue.getPosition(outage);
    }

    public Queue<Outage> getOutageQueue() {
        return outageQueue;
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

    public Outage getCurrentOutage() {
        return currentOutage;
    }
}