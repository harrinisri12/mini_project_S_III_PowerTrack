package service;

import java.util.ArrayList;
import java.util.Scanner;

import model.Consumer;
import model.NetworkNode;
import model.Outage;
import model.Technician;

import datastructure.Queue;
import datastructure.Graph;

public class ElectricitySystem {

    private Consumer[] consumers;
    private NetworkNode[] networkNodes;
    private Outage[] outages;
    private Technician[] technicians;

    private Queue<Outage> outageQueue;
    private Graph electricityGraph;

    private int consumerCount;
    private int networkNodeCount;
    private int outageCount;
    private int technicianCount;

    private int nextOutageId;

    // Currently processed outage
    private Outage currentOutage;

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

        currentOutage = null;

        initializeNetwork();
        initializeConsumers();
        initializeTechnicians();
        initializeHistory();
    }

    // =========================================
    // INITIAL NETWORK - TAMIL NADU REGIONAL GRID
    // (Erode, Perundurai, Chennimalai, Tiruppur, Avinashi)
    // =========================================

    private void initializeNetwork() {

        // --- SUBSTATIONS (230kV / 110kV Transmission Nodes) ---
        addNetworkNode(new NetworkNode("SS-ERD-01", "Erode Central 230kV Substation", "SUBSTATION"));
        addNetworkNode(new NetworkNode("SS-PER-02", "Perundurai 110kV Substation", "SUBSTATION"));
        addNetworkNode(new NetworkNode("SS-TPR-03", "Tiruppur North 110kV Substation", "SUBSTATION"));

        // --- FEEDERS (11kV / 22kV High-Tension Distribution Feeders) ---
        addNetworkNode(new NetworkNode("FD-ERD-01", "Erode North Feeder", "FEEDER"));
        addNetworkNode(new NetworkNode("FD-PER-02", "SIPCOT Industrial Feeder", "FEEDER"));
        addNetworkNode(new NetworkNode("FD-CHN-03", "Chennimalai Feeder", "FEEDER"));
        addNetworkNode(new NetworkNode("FD-AVI-04", "Avinashi Feeder", "FEEDER"));
        addNetworkNode(new NetworkNode("FD-TPR-05", "Rayapuram Feeder", "FEEDER"));

        // --- DISTRIBUTION TRANSFORMERS (11kV / 415V Step-Down Transformers) ---
        addNetworkNode(new NetworkNode("DT-ERD-101", "Kumalan Kuttai Transformer", "TRANSFORMER"));
        addNetworkNode(new NetworkNode("DT-PER-102", "SIPCOT Phase-I Transformer", "TRANSFORMER"));
        addNetworkNode(new NetworkNode("DT-CHN-103", "Chennimalai Town Transformer", "TRANSFORMER"));
        addNetworkNode(new NetworkNode("DT-AVI-104", "Avinashi Main Transformer", "TRANSFORMER"));
        addNetworkNode(new NetworkNode("DT-TPR-105", "Rayapuram Textile Transformer", "TRANSFORMER"));

        // --- CONSUMER LOCALITY AREAS ---
        addNetworkNode(new NetworkNode("AREA-ERD-PR", "Perundurai Road Locality", "AREA"));
        addNetworkNode(new NetworkNode("AREA-ERD-SN", "Sampath Nagar Locality", "AREA"));
        addNetworkNode(new NetworkNode("AREA-PER-SIP", "SIPCOT Industrial Zone", "AREA"));
        addNetworkNode(new NetworkNode("AREA-CHN-TWN", "Chennimalai Town Area", "AREA"));
        addNetworkNode(new NetworkNode("AREA-AVI-BYP", "Avinashi Bypass Area", "AREA"));
        addNetworkNode(new NetworkNode("AREA-TPR-RAY", "Rayapuram Textile Cluster", "AREA"));

        // =========================================
        // GRAPH CONNECTIONS (HIERARCHY & INTER-LINKS)
        // =========================================

        // Substation Transmission Inter-ties
        electricityGraph.addEdge("SS-ERD-01", "SS-PER-02");
        electricityGraph.addEdge("SS-PER-02", "SS-TPR-03");

        // Substation to Feeder connections
        electricityGraph.addEdge("SS-ERD-01", "FD-ERD-01");
        electricityGraph.addEdge("SS-PER-02", "FD-PER-02");
        electricityGraph.addEdge("SS-PER-02", "FD-CHN-03");
        electricityGraph.addEdge("SS-TPR-03", "FD-AVI-04");
        electricityGraph.addEdge("SS-TPR-03", "FD-TPR-05");

        // Feeder to Distribution Transformer connections
        electricityGraph.addEdge("FD-ERD-01", "DT-ERD-101");
        electricityGraph.addEdge("FD-PER-02", "DT-PER-102");
        electricityGraph.addEdge("FD-CHN-03", "DT-CHN-103");
        electricityGraph.addEdge("FD-AVI-04", "DT-AVI-104");
        electricityGraph.addEdge("FD-TPR-05", "DT-TPR-105");

        // Transformer to Consumer Area distribution feeds
        electricityGraph.addEdge("DT-ERD-101", "AREA-ERD-PR");
        electricityGraph.addEdge("DT-ERD-101", "AREA-ERD-SN");
        electricityGraph.addEdge("DT-PER-102", "AREA-PER-SIP");
        electricityGraph.addEdge("DT-CHN-103", "AREA-CHN-TWN");
        electricityGraph.addEdge("DT-AVI-104", "AREA-AVI-BYP");
        electricityGraph.addEdge("DT-TPR-105", "AREA-TPR-RAY");
    }

    // =========================================
    // INITIAL CONSUMERS - REALISTIC TN CONSUMERS
    // =========================================

    private void initializeConsumers() {

        // Erode - Perundurai Road & Sampath Nagar
        addConsumer(new Consumer(1001, "S. Karthikeyan (Domestic, 3.2 kW)", "AREA-ERD-PR"));
        addConsumer(new Consumer(1002, "Dr. M. Soundararajan - Erode City Clinic", "AREA-ERD-PR"));
        addConsumer(new Consumer(1003, "V. Meenakshi Sundaram (Commercial Complex)", "AREA-ERD-SN"));
        addConsumer(new Consumer(1004, "Sampath Nagar Community Health Center", "AREA-ERD-SN"));
        addConsumer(new Consumer(1005, "K. Rangarajan (Domestic, 2.5 kW)", "AREA-ERD-SN"));

        // Perundurai - SIPCOT Industrial Zone
        addConsumer(new Consumer(1006, "Sakthi Murugan Spinning Mills Ltd.", "AREA-PER-SIP"));
        addConsumer(new Consumer(1007, "Perundurai Tanneries Effluent Plant", "AREA-PER-SIP"));
        addConsumer(new Consumer(1008, "G. Thangavel - Sri Krishna Textiles", "AREA-PER-SIP"));
        addConsumer(new Consumer(1009, "P. Dhanalakshmi (SIPCOT Staff Quarters)", "AREA-PER-SIP"));

        // Chennimalai Town Area
        addConsumer(new Consumer(1010, "Chennimalai Handloom Weavers Co-op Society", "AREA-CHN-TWN"));
        addConsumer(new Consumer(1011, "Murugan Temple Bazaar Commercial Complex", "AREA-CHN-TWN"));
        addConsumer(new Consumer(1012, "N. Balasubramanian (Domestic, 4.0 kW)", "AREA-CHN-TWN"));
        addConsumer(new Consumer(1013, "Chennimalai Govt Higher Secondary School", "AREA-CHN-TWN"));

        // Avinashi Bypass Area
        addConsumer(new Consumer(1014, "Avinashi Cold Storage & Agro Logistics", "AREA-AVI-BYP"));
        addConsumer(new Consumer(1015, "R. Selvakumar - Sri Amman Auto Works", "AREA-AVI-BYP"));
        addConsumer(new Consumer(1016, "Avinashi Primary Health Centre", "AREA-AVI-BYP"));
        addConsumer(new Consumer(1017, "T. Muthusamy (Domestic, 3.0 kW)", "AREA-AVI-BYP"));

        // Tiruppur - Rayapuram Textile Cluster
        addConsumer(new Consumer(1018, "Tiruppur Export Knitwear Unit No. 4", "AREA-TPR-RAY"));
        addConsumer(new Consumer(1019, "Rayapuram Dyeing & Processing Works", "AREA-TPR-RAY"));
        addConsumer(new Consumer(1020, "K. Jayanthi - Jayam Garments", "AREA-TPR-RAY"));
        addConsumer(new Consumer(1021, "Tiruppur Govt Hospital Oxygen Plant", "AREA-TPR-RAY"));
        addConsumer(new Consumer(1022, "A. Palanisamy (Domestic, 2.0 kW)", "AREA-TPR-RAY"));
    }

    // =========================================
    // INITIAL TECHNICIANS - FIELD WORKFORCE
    // =========================================

    private void initializeTechnicians() {

        addTechnician(new Technician(201, "R. Suresh", "Electrical Lines"));
        addTechnician(new Technician(202, "M. Senthil Kumar", "Transformers"));
        addTechnician(new Technician(203, "K. Mani", "Distribution"));
        addTechnician(new Technician(204, "P. Gunasekaran", "Transformers"));
        addTechnician(new Technician(205, "S. Murugesan", "Electrical Lines"));
        addTechnician(new Technician(206, "V. Anandhan", "Distribution"));
    }

    // =========================================
    // INITIAL HISTORICAL RESTORED OUTAGES
    // =========================================

    private void initializeHistory() {

        // 1. Erode North Feeder Trip
        ArrayList<String> areas1 = new ArrayList<>();
        areas1.add("AREA-ERD-PR");
        areas1.add("AREA-ERD-SN");
        Outage h1 = new Outage(
            nextOutageId++,
            "FD-ERD-01",
            "33kV Feeder Tripping via Earth Fault",
            areas1,
            5
        );
        h1.setPriority("HIGH");
        h1.setStatus("RESTORED");
        h1.setAssignedTechnician("R. Suresh");
        outages[outageCount++] = h1;

        // 2. Chennimalai Transformer Overload
        ArrayList<String> areas2 = new ArrayList<>();
        areas2.add("AREA-CHN-TWN");
        Outage h2 = new Outage(
            nextOutageId++,
            "DT-CHN-103",
            "Distribution Transformer Thermal Overload",
            areas2,
            4
        );
        h2.setPriority("MEDIUM");
        h2.setStatus("RESTORED");
        h2.setAssignedTechnician("P. Gunasekaran");
        outages[outageCount++] = h2;

        // 3. SIPCOT Phase-I Transformer Flashover
        ArrayList<String> areas3 = new ArrayList<>();
        areas3.add("AREA-PER-SIP");
        Outage h3 = new Outage(
            nextOutageId++,
            "DT-PER-102",
            "HT Bushing Insulator Flashover",
            areas3,
            4
        );
        h3.setPriority("HIGH");
        h3.setStatus("RESTORED");
        h3.setAssignedTechnician("M. Senthil Kumar");
        outages[outageCount++] = h3;

        // 4. Avinashi Overhead Line Severance
        ArrayList<String> areas4 = new ArrayList<>();
        areas4.add("AREA-AVI-BYP");
        Outage h4 = new Outage(
            nextOutageId++,
            "FD-AVI-04",
            "11kV Overhead Conductor Snap",
            areas4,
            4
        );
        h4.setPriority("MEDIUM");
        h4.setStatus("RESTORED");
        h4.setAssignedTechnician("S. Murugesan");
        outages[outageCount++] = h4;

        // 5. Rayapuram Textile Transformer Core Saturation
        ArrayList<String> areas5 = new ArrayList<>();
        areas5.add("AREA-TPR-RAY");
        Outage h5 = new Outage(
            nextOutageId++,
            "DT-TPR-105",
            "Transformer Oil Deterioration & Trip",
            areas5,
            5
        );
        h5.setPriority("CRITICAL");
        h5.setStatus("RESTORED");
        h5.setAssignedTechnician("M. Senthil Kumar");
        outages[outageCount++] = h5;
    }

    // =========================================
    // CONSUMERS
    // =========================================

    public void addConsumer(Consumer consumer) {

        if (consumerCount >= consumers.length) {
            System.out.println("Consumer storage is full.");
            return;
        }

        consumers[consumerCount] = consumer;
        consumerCount++;
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

    // =========================================
    // NETWORK
    // =========================================

    public void addNetworkNode(NetworkNode node) {

        if (networkNodeCount >= networkNodes.length) {
            System.out.println("Network storage is full.");
            return;
        }

        networkNodes[networkNodeCount] = node;
        networkNodeCount++;
    }

    public void displayNetwork() {

        System.out.println();
        System.out.println("========== ELECTRICITY NETWORK (ERODE-TIRUPPUR REGION) ==========");

        for (int i = 0; i < networkNodeCount; i++) {
            networkNodes[i].display();
        }

        electricityGraph.displayGraph();
    }

    // =========================================
    // TECHNICIANS
    // =========================================

    public void addTechnician(Technician technician) {

        if (technicianCount >= technicians.length) {
            System.out.println("Technician storage is full.");
            return;
        }

        technicians[technicianCount] = technician;
        technicianCount++;
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
        // Determine affected areas
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
        // Determine priority
        // -----------------------------------------
        String priority = calculatePriority(affectedConsumerCount);

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

        // -----------------------------------------
        // Store outage
        // -----------------------------------------
        outages[outageCount] = outage;
        outageCount++;
        nextOutageId++;

        // -----------------------------------------
        // Turn consumer power OFF
        // -----------------------------------------
        updateConsumerPower(affectedAreas);

        // -----------------------------------------
        // Add to queue
        // -----------------------------------------
        outageQueue.enqueue(outage);

        // -----------------------------------------
        // Display detection result
        // -----------------------------------------
        System.out.println();
        System.out.println("Affected Areas: " + affectedAreas);
        System.out.println("Affected Consumers: " + affectedConsumerCount);
        System.out.println("Restoration Priority: " + priority);
        System.out.println("Registered Outage ID: " + outage.getOutageId());
        System.out.println("Status: " + outage.getStatus());
        System.out.println();
        System.out.println("Outage successfully enqueued to restoration queue.");
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
    // PRIORITY CALCULATION
    // =========================================

    private String calculatePriority(int affectedConsumers) {

        if (affectedConsumers >= 5) {
            return "HIGH";
        }
        else if (affectedConsumers >= 4) {
            return "HIGH";
        }
        else if (affectedConsumers >= 3) {
            return "MEDIUM";
        }

        return "LOW";
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
                outages[i].display();
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
        System.out.println("========== PROCESSING OUTAGE FOR DISPATCH ==========");
        System.out.println("Outage ID: " + outage.getOutageId());
        System.out.println("Failed Component: " + outage.getFailedComponent());
        System.out.println("Priority: " + outage.getPriority());

        // -----------------------------------------
        // Assign technician
        // -----------------------------------------
        Technician technician = findAvailableTechnician(outage.getFailedComponent());

        if (technician == null) {
            System.out.println();
            System.out.println("No field technician is currently available in this zone.");
            // Re-enqueue outage
            outageQueue.enqueue(outage);
            return;
        }

        technician.setAvailable(false);
        outage.setAssignedTechnician(technician.getName());
        outage.setStatus("IN PROGRESS");
        currentOutage = outage;

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

        Outage outage = null;

        for (int i = 0; i < outageCount; i++) {
            if (outages[i].getOutageId() == outageId) {
                outage = outages[i];
                break;
            }
        }

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

        for (int i = 0; i < technicianCount; i++) {
            if (technicians[i].getName().equals(technicianName)) {
                technicians[i].setAvailable(true);
                break;
            }
        }

        // -----------------------------------------
        // Update outage
        // -----------------------------------------
        outage.setStatus("RESTORED");
        currentOutage = null;

        System.out.println();
        System.out.println("Network Component: ACTIVE");
        System.out.println("Affected Areas: " + outage.getAffectedAreas() + " -> POWER RESTORED");
        System.out.println("Technician " + technicianName + " -> AVAILABLE");
        System.out.println();
        System.out.println("Outage OUT-" + outageId + " -> RESTORED & ARCHIVED");
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
    // QUEUE DISPLAY
    // =========================================

    public void displayOutageQueue() {

        System.out.println();
        System.out.println("========== OUTAGE QUEUE STATUS ==========");
        System.out.println("Queue Size: " + outageQueue.size());
        outageQueue.display();
    }
}