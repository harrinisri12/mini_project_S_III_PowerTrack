import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import service.ElectricitySystem;
import database.DatabaseConnection;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import model.Consumer;
import model.NetworkConnection;
import model.NetworkNode;
import model.Outage;
import model.Technician;

/**
 * ==============================================================================
 * PowerTrack: Web Application Server (Primary Entry Point)
 * ==============================================================================
 * Architecture & Performance Optimizations:
 *   - Controlled fixed thread pool (Executors) allowing concurrent non-blocking API execution.
 *   - Unified dashboard summary endpoint (/api/dashboard/summary) preventing duplicate fetches.
 *   - Targeted database queries (getActiveOutages, getHistoricalOutages) instead of full table scans.
 *   - Automatic cache-control headers and robust CORS preflight handling.
 *   - Graceful shutdown hook terminating thread pool and recycling JDBC connection pool.
 * ==============================================================================
 */
public class PowerTrackServer {

    private static final int PORT = 8080;
    private static ElectricitySystem system;
    private static ExecutorService threadPool;
    private static final String FRONTEND_PATH = "../frontend";

    public static void main(String[] args) throws Exception {

        System.out.println();
        System.out.println("==============================================");
        System.out.println("                 POWERTRACK");
        System.out.println("      SMART ELECTRICITY OUTAGE SYSTEM");
        System.out.println("==============================================");

        // Strict database configuration check
        if (!DatabaseConnection.isConfigured()) {
            System.err.println("\n[PowerTrack Fatal Error] Supabase database credentials are not configured.");
            System.err.println("Required environment variables: SUPABASE_DB_URL, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD.");
            System.err.println("Backend startup halted.");
            System.exit(1);
            return;
        }

        if (!DatabaseConnection.testConnection()) {
            System.err.println("\n[PowerTrack Fatal Error] Cannot connect to Supabase PostgreSQL database.");
            System.err.println("Please verify database status, network connectivity, and credentials.");
            System.err.println("Backend startup halted.");
            System.exit(1);
            return;
        }

        try {
            system = new ElectricitySystem();
        } catch (Exception e) {
            System.err.println("\n[PowerTrack Fatal Error] Failed to initialize system from Supabase: "
                    + DatabaseConnection.sanitizeErrorMessage(e.getMessage()));
            System.exit(1);
            return;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // =========================================
        // REST API ROUTES
        // =========================================
        server.createContext("/api/network", PowerTrackServer::handleNetwork);
        server.createContext("/api/consumers", PowerTrackServer::handleConsumers);
        server.createContext("/api/technicians", PowerTrackServer::handleTechnicians);
        server.createContext("/api/outages/active", PowerTrackServer::handleActiveOutages);
        server.createContext("/api/outages/history", PowerTrackServer::handleHistory);
        server.createContext("/api/outages/simulate", PowerTrackServer::handleSimulate);
        server.createContext("/api/outages/process", PowerTrackServer::handleProcess);
        server.createContext("/api/outages/restore", PowerTrackServer::handleRestore);
        server.createContext("/api/outages/assign", PowerTrackServer::handleAssign);
        server.createContext("/api/outages/update-status", PowerTrackServer::handleUpdateStatus);
        server.createContext("/api/stats", PowerTrackServer::handleStats);
        server.createContext("/api/dashboard/summary", PowerTrackServer::handleDashboardSummary);
        server.createContext("/api/health", PowerTrackServer::handleHealth);

        // =========================================
        // FRONTEND STATIC ASSETS
        // =========================================
        server.createContext("/", PowerTrackServer::handleFrontend);

        // Controlled thread pool for concurrent request handling
        int workerThreads = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        threadPool = Executors.newFixedThreadPool(workerThreads, new ThreadFactory() {
            private final AtomicInteger threadId = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "powertrack-http-" + threadId.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        server.setExecutor(threadPool);

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[PowerTrack] Stopping HTTP server and releasing connection pool...");
            server.stop(1);
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdown();
            }
            DatabaseConnection.shutdownPool();
            System.out.println("[PowerTrack] Shutdown completed successfully.");
        }, "powertrack-server-shutdown"));

        server.start();

        System.out.println("Server started successfully with " + workerThreads + " concurrent worker threads!");
        System.out.println("Open: http://localhost:" + PORT);
        System.out.println("Press CTRL+C to stop the server.");
        System.out.println("==============================================");
    }

    // =========================================================
    // CORS & HTTP HEADERS
    // =========================================================

    private static void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Accept");
        headers.set("Content-Type", "application/json; charset=UTF-8");
        headers.set("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.set("Pragma", "no-cache");
    }

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    // =========================================================
    // NETWORK
    // =========================================================

    private static void handleNetwork(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            List<NetworkNode> nodes = system.getNetworkDAO().getAllNodes();
            List<NetworkConnection> conns = system.getNetworkDAO().getAllConnections();

            Map<String, List<String>> connectionMap = new HashMap<>();
            for (NetworkConnection conn : conns) {
                connectionMap.computeIfAbsent(conn.getSourceNodeId(), k -> new ArrayList<>()).add(conn.getDestinationNodeId());
            }

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < nodes.size(); i++) {
                NetworkNode node = nodes.get(i);
                if (i > 0) json.append(",");

                json.append("{");
                json.append("\"id\":\"").append(escape(node.getNodeId())).append("\",");
                json.append("\"name\":\"").append(escape(node.getNodeName())).append("\",");
                json.append("\"type\":\"").append(escape(node.getNodeType())).append("\",");
                json.append("\"active\":").append(node.isActive());

                List<String> neighbors = connectionMap.get(node.getNodeId());
                json.append(",\"connectedTo\":[");
                if (neighbors != null) {
                    for (int j = 0; j < neighbors.size(); j++) {
                        if (j > 0) json.append(",");
                        json.append("\"").append(escape(neighbors.get(j))).append("\"");
                    }
                }
                json.append("]}");
            }
            json.append("]");

            sendJson(exchange, 200, json.toString());
        } catch (SQLException e) {
            sendError(exchange, 503, "Database unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage()));
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // CONSUMERS
    // =========================================================

    private static void handleConsumers(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            List<Consumer> consumers = system.getConsumerDAO().getAllConsumers();

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < consumers.size(); i++) {
                Consumer consumer = consumers.get(i);
                if (i > 0) json.append(",");

                json.append("{");
                json.append("\"id\":").append(consumer.getConsumerId()).append(",");
                json.append("\"name\":\"").append(escape(consumer.getName())).append("\",");
                json.append("\"area\":\"").append(escape(consumer.getArea())).append("\",");
                json.append("\"powerAvailable\":").append(consumer.isPowerAvailable()).append(",");
                json.append("\"consumerType\":\"").append(escape(consumer.getConsumerType())).append("\",");
                json.append("\"criticalityLevel\":\"").append(escape(consumer.getCriticalityLevel())).append("\",");
                json.append("\"criticalityTier\":").append(consumer.getCriticalityTier());
                json.append("}");
            }
            json.append("]");

            sendJson(exchange, 200, json.toString());
        } catch (SQLException e) {
            sendError(exchange, 503, "Database unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage()));
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // TECHNICIANS
    // =========================================================

    private static void handleTechnicians(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            List<Technician> list = system.getTechnicianDAO().getAllTechnicians();

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                Technician t = list.get(i);
                if (i > 0) json.append(",");

                json.append("{");
                json.append("\"id\":\"T").append(t.getTechnicianId()).append("\",");
                json.append("\"technicianId\":").append(t.getTechnicianId()).append(",");
                json.append("\"name\":\"").append(escape(t.getName())).append("\",");
                json.append("\"specialization\":\"").append(escape(t.getSpecialization())).append("\",");
                json.append("\"available\":").append(t.isAvailable());
                json.append("}");
            }
            json.append("]");

            sendJson(exchange, 200, json.toString());
        } catch (SQLException e) {
            sendError(exchange, 503, "Database unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage()));
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // OUTAGES (ACTIVE & HISTORY)
    // =========================================================

    private static void handleActiveOutages(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        sendOutages(exchange, false);
    }

    private static void handleHistory(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        sendOutages(exchange, true);
    }

    private static void sendOutages(HttpExchange exchange, boolean includeRestored) throws IOException {
        try {
            // Use targeted DAO queries avoiding unnecessary full table scans
            List<Outage> outages = includeRestored
                ? system.getOutageDAO().getHistoricalOutages()
                : system.getOutageDAO().getActiveOutages();

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            for (Outage outage : outages) {
                if (outage == null) continue;

                if (!first) json.append(",");
                first = false;

                json.append(outageToJson(outage));
            }
            json.append("]");

            sendJson(exchange, 200, json.toString());
        } catch (SQLException e) {
            sendError(exchange, 503, "Database unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage()));
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // SIMULATE FAULT
    // =========================================================

    private static void handleSimulate(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(exchange, 405, "Use POST");
            return;
        }

        try {
            String body = readBody(exchange);
            String component = getJsonString(body, "component");

            if (component == null || component.trim().isEmpty()) {
                sendError(exchange, 400, "Component identifier is required.");
                return;
            }

            component = component.trim().toUpperCase();

            // Execute the outage detection workflow directly
            Outage outage = system.simulateFault(component);

            sendJson(exchange, 200, "{"
                    + "\"success\":true,"
                    + "\"message\":\"Fault simulated successfully on " + escape(component) + "\","
                    + "\"outage\":" + outageToJson(outage)
                    + "}");

        } catch (IllegalArgumentException | IllegalStateException e) {
            sendJson(exchange, 400, "{"
                    + "\"success\":false,"
                    + "\"message\":\"" + escape(e.getMessage()) + "\""
                    + "}");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Database error")) {
                sendError(exchange, 503, e.getMessage());
            } else {
                sendError(exchange, 500, e.getMessage());
            }
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // PROCESS NEXT OUTAGE (DISPATCH)
    // =========================================================

    private static void handleProcess(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(exchange, 405, "Use POST");
            return;
        }

        try {
            if (system.getOutageQueue().isEmpty()) {
                sendJson(exchange, 400, "{"
                        + "\"success\":false,"
                        + "\"message\":\"No active outages are waiting in the queue.\""
                        + "}");
                return;
            }

            Outage current = system.processNextOutage();

            if (current != null && "IN PROGRESS".equals(current.getStatus())) {
                sendJson(exchange, 200, "{"
                        + "\"success\":true,"
                        + "\"message\":\"Outage OUT-" + current.getOutageId() + " assigned to technician " + escape(current.getAssignedTechnician()) + "\","
                        + "\"outage\":" + outageToJson(current)
                        + "}");
            } else {
                sendJson(exchange, 400, "{"
                        + "\"success\":false,"
                        + "\"message\":\"No field technician is currently available in this zone. Outage retained at head of queue.\""
                        + "}");
            }

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Database error")) {
                sendError(exchange, 503, e.getMessage());
            } else {
                sendError(exchange, 500, e.getMessage());
            }
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // RESTORE OUTAGE
    // =========================================================

    private static void handleRestore(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(exchange, 405, "Use POST");
            return;
        }

        try {
            String body = readBody(exchange);
            String idString = getJsonString(body, "outageId");

            if (idString == null || idString.isEmpty()) {
                sendError(exchange, 400, "outageId is required");
                return;
            }

            int outageId = Integer.parseInt(idString.replace("OUT-", "").trim());
            String restorationDetails = getJsonString(body, "restorationDetails");

            system.restoreOutage(outageId, restorationDetails);

            Outage outage = system.findOutageById(outageId);

            sendJson(exchange, 200, "{"
                    + "\"success\":true,"
                    + "\"message\":\"Outage OUT-" + outageId + " restored successfully\","
                    + "\"outage\":" + (outage != null ? outageToJson(outage) : "null")
                    + "}");

        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid numeric format for Outage ID");
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendJson(exchange, 400, "{\"success\":false,\"message\":\"" + escape(e.getMessage()) + "\"}");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Database error")) {
                sendError(exchange, 503, e.getMessage());
            } else {
                sendError(exchange, 500, e.getMessage());
            }
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // ASSIGN TECHNICIAN
    // =========================================================

    private static void handleAssign(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(exchange, 405, "Use POST");
            return;
        }

        try {
            String body = readBody(exchange);
            String outageIdStr = getJsonString(body, "outageId");
            String techIdStr = getJsonString(body, "technicianId");

            if (outageIdStr == null || outageIdStr.isEmpty() || techIdStr == null || techIdStr.isEmpty()) {
                sendError(exchange, 400, "outageId and technicianId are required");
                return;
            }

            int outageId = Integer.parseInt(outageIdStr.replace("OUT-", "").trim());
            int technicianId = Integer.parseInt(techIdStr.replace("T", "").trim());

            Outage updated = system.assignTechnicianToOutage(outageId, technicianId);

            sendJson(exchange, 200, "{"
                    + "\"success\":true,"
                    + "\"message\":\"Outage OUT-" + outageId + " assigned to technician " + escape(updated.getAssignedTechnician()) + "\","
                    + "\"outage\":" + outageToJson(updated)
                    + "}");

        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid numeric format for Outage or Technician ID");
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendJson(exchange, 400, "{\"success\":false,\"message\":\"" + escape(e.getMessage()) + "\"}");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Database error")) {
                sendError(exchange, 503, e.getMessage());
            } else {
                sendError(exchange, 500, e.getMessage());
            }
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // UPDATE STATUS
    // =========================================================

    private static void handleUpdateStatus(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(exchange, 405, "Use POST");
            return;
        }

        try {
            String body = readBody(exchange);
            String outageIdStr = getJsonString(body, "outageId");
            String targetStatus = getJsonString(body, "status");
            String techIdStr = getJsonString(body, "technicianId");
            String details = getJsonString(body, "details");

            if (outageIdStr == null || outageIdStr.isEmpty() || targetStatus == null || targetStatus.isEmpty()) {
                sendError(exchange, 400, "outageId and status are required");
                return;
            }

            int outageId = Integer.parseInt(outageIdStr.replace("OUT-", "").trim());
            targetStatus = targetStatus.toUpperCase().trim();

            if ("RESTORED".equals(targetStatus)) {
                system.restoreOutage(outageId, details);
                Outage outage = system.findOutageById(outageId);
                sendJson(exchange, 200, "{"
                        + "\"success\":true,"
                        + "\"message\":\"Outage OUT-" + outageId + " marked as RESTORED and power restored.\","
                        + "\"outage\":" + (outage != null ? outageToJson(outage) : "null")
                        + "}");
                return;
            } else if ("IN PROGRESS".equals(targetStatus) || "ASSIGNED".equals(targetStatus)) {
                Outage outage;
                if (techIdStr != null && !techIdStr.trim().isEmpty()) {
                    int techId = Integer.parseInt(techIdStr.replace("T", "").trim());
                    outage = system.assignTechnicianToOutage(outageId, techId);
                } else {
                    outage = system.processNextOutage();
                }
                sendJson(exchange, 200, "{"
                        + "\"success\":true,"
                        + "\"message\":\"Outage OUT-" + outageId + " status updated to " + escape(targetStatus) + "\","
                        + "\"outage\":" + (outage != null ? outageToJson(outage) : "null")
                        + "}");
                return;
            } else {
                sendError(exchange, 400, "Unsupported status: " + targetStatus);
            }

        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid numeric format");
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendJson(exchange, 400, "{\"success\":false,\"message\":\"" + escape(e.getMessage()) + "\"}");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Database error")) {
                sendError(exchange, 503, e.getMessage());
            } else {
                sendError(exchange, 500, e.getMessage());
            }
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // STATISTICS & UNIFIED DASHBOARD SUMMARY
    // =========================================================

    private static void handleStats(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            dao.StatsDAO.SystemStats dbStats = system.getStatsDAO().getSystemStats();
            sendJson(exchange, 200, dbStats.toJson());
        } catch (SQLException e) {
            sendError(exchange, 503, "Database unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage()));
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    /**
     * Unified Dashboard Summary endpoint:
     * Returns all grid counts (consumers, active outages, assigned, in-progress, restored, technicians)
     * in a single fast, indexed query, avoiding multiple round-trip calls.
     */
    private static void handleDashboardSummary(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            dao.StatsDAO.SystemStats dbStats = system.getStatsDAO().getSystemStats();
            int queueSize = system.getOutageQueue().size();

            String json = "{"
                + "\"stats\":" + dbStats.toJson() + ","
                + "\"queueSize\":" + queueSize + ","
                + "\"service\":\"PowerTrack\","
                + "\"database\":\"CONNECTED\""
                + "}";

            sendJson(exchange, 200, json);
        } catch (SQLException e) {
            sendError(exchange, 503, "Database unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage()));
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    // =========================================================
    // HEALTH CHECK
    // =========================================================

    private static void handleHealth(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        addCorsHeaders(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        boolean dbConnected = DatabaseConnection.testConnection();

        if (dbConnected) {
            sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"PowerTrack\",\"database\":\"CONNECTED\"}");
        } else {
            sendJson(exchange, 503, "{\"status\":\"DOWN\",\"service\":\"PowerTrack\",\"database\":\"DISCONNECTED\",\"error\":\"Database connection unavailable.\"}");
        }
    }

    // =========================================================
    // FRONTEND FILE SERVER
    // =========================================================

    private static Path getFrontendRoot() {
        Path p = Paths.get("../frontend").toAbsolutePath().normalize();
        if (Files.exists(p) && Files.isDirectory(p)) {
            return p;
        }
        p = Paths.get("frontend").toAbsolutePath().normalize();
        if (Files.exists(p) && Files.isDirectory(p)) {
            return p;
        }
        return Paths.get(FRONTEND_PATH).toAbsolutePath().normalize();
    }

    private static void handleFrontend(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;

        String requestPath = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if (requestPath.equals("/") || requestPath.isEmpty()) {
            requestPath = "/index.html";
        }

        Path frontendRoot = getFrontendRoot();
        Path requestedFile = frontendRoot.resolve(requestPath.substring(1)).normalize();

        // Prevent path traversal outside frontend directory
        if (!requestedFile.startsWith(frontendRoot)) {
            sendText(exchange, 403, "Forbidden");
            return;
        }

        if (!Files.exists(requestedFile) || Files.isDirectory(requestedFile)) {
            sendText(exchange, 404, "File not found");
            return;
        }

        byte[] data = Files.readAllBytes(requestedFile);

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", getContentType(requestedFile));
        headers.set("Cache-Control", "public, max-age=300");

        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    // =========================================================
    // JSON SERIALIZATION & UTILITIES
    // =========================================================

    private static String outageToJson(Outage outage) {
        if (outage == null) return "null";

        StringBuilder areas = new StringBuilder("[");
        ArrayList<String> affectedAreas = outage.getAffectedAreas();
        for (int i = 0; i < affectedAreas.size(); i++) {
            if (i > 0) areas.append(",");
            areas.append("\"").append(escape(affectedAreas.get(i))).append("\"");
        }
        areas.append("]");

        StringBuilder crit = new StringBuilder("[");
        ArrayList<String> criticalFacilities = outage.getCriticalFacilities();
        if (criticalFacilities != null) {
            for (int i = 0; i < criticalFacilities.size(); i++) {
                if (i > 0) crit.append(",");
                crit.append("\"").append(escape(criticalFacilities.get(i))).append("\"");
            }
        }
        crit.append("]");

        int queuePosition = system.getQueuePosition(outage);

        return "{"
                + "\"id\":\"OUT-" + outage.getOutageId() + "\","
                + "\"outageId\":" + outage.getOutageId() + ","
                + "\"component\":\"" + escape(outage.getFailedComponent()) + "\","
                + "\"node\":\"" + escape(outage.getFailedComponent()) + "\","
                + "\"cause\":\"" + escape(outage.getFaultType()) + "\","
                + "\"areas\":" + areas + ","
                + "\"consumers\":" + outage.getAffectedConsumers() + ","
                + "\"priority\":\"" + escape(outage.getPriority()) + "\","
                + "\"priorityTier\":" + outage.getPriorityTier() + ","
                + "\"criticalFacilities\":" + crit + ","
                + "\"priorityReason\":\"" + escape(outage.getPriorityReason()) + "\","
                + "\"queuePosition\":" + queuePosition + ","
                + "\"queueSequence\":" + outage.getQueueSequence() + ","
                + "\"status\":\"" + escape(outage.getStatus()) + "\","
                + "\"technician\":\"" + escape(outage.getAssignedTechnician()) + "\","
                + "\"assignedTechnicianId\":" + (outage.getAssignedTechnicianId() != null ? outage.getAssignedTechnicianId() : "null") + ","
                + "\"reportedAt\":\"" + (outage.getReportedAt() != null ? escape(outage.getReportedAt().toString()) : "") + "\","
                + "\"restoredAt\":\"" + (outage.getRestoredAt() != null ? escape(outage.getRestoredAt().toString()) : "") + "\""
                + "}";
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String getJsonString(String json, String key) {
        if (json == null || key == null) return null;
        String search = "\"" + key + "\"";
        int keyIndex = json.indexOf(search);
        if (keyIndex == -1) return null;

        int colon = json.indexOf(":", keyIndex + search.length());
        if (colon == -1) return null;

        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) return null;

        if (json.charAt(start) == '\"') {
            int end = json.indexOf('\"', start + 1);
            if (end == -1) return null;
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && !Character.isWhitespace(json.charAt(end))) {
                end++;
            }
            return json.substring(start, end).trim();
        }
    }

    private static String getContentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=UTF-8";
        if (name.endsWith(".css")) return "text/css; charset=UTF-8";
        if (name.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (name.endsWith(".json")) return "application/json; charset=UTF-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int status, String msg) throws IOException {
        String json = "{\"success\":false,\"error\":\"" + escape(msg) + "\",\"message\":\"" + escape(msg) + "\"}";
        sendJson(exchange, status, json);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}