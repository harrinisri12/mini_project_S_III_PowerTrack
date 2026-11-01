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

import model.Consumer;
import model.NetworkConnection;
import model.NetworkNode;
import model.Outage;
import model.Technician;
import datastructure.Graph;
import datastructure.Queue;

/**
 * ==============================================================================
 * PowerTrack: Web Application Server (Primary Entry Point)
 * ==============================================================================
 * Architecture:
 *   - Utilizes Java's native com.sun.net.httpserver.HttpServer (no heavy external
 *     frameworks like Spring Boot needed, making it lightweight and easy to review).
 *   - Runs on port 8080.
 *   - Serves both REST JSON APIs (/api/*) and static frontend assets (HTML, CSS, JS).
 *   - Connects directly to Supabase PostgreSQL via JDBC for persistent grid data.
 * ==============================================================================
 */
public class PowerTrackServer {

    private static final int PORT = 8080;

    private static ElectricitySystem system;

    private static final String FRONTEND_PATH =
            "../frontend";

    public static void main(String[] args) throws Exception {

        System.out.println();
        System.out.println(
                "=============================================="
        );
        System.out.println(
                "                 POWERTRACK"
        );
        System.out.println(
                "      SMART ELECTRICITY OUTAGE SYSTEM"
        );
        System.out.println(
                "=============================================="
        );

        // =========================================
        // STRICT DATABASE STARTUP VALIDATION
        // =========================================
        if (!DatabaseConnection.isConfigured()) {
            System.err.println();
            System.err.println("[PowerTrack Fatal Error] Supabase database credentials are not configured.");
            System.err.println("Required environment variables: SUPABASE_DB_URL, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD.");
            System.err.println("In-memory offline fallback is completely disabled. Backend startup halted.");
            System.err.println("==============================================");
            System.exit(1);
            return;
        }

        if (!DatabaseConnection.testConnection()) {
            System.err.println();
            System.err.println("[PowerTrack Fatal Error] Cannot connect to Supabase PostgreSQL database.");
            System.err.println("Please verify database status, network connectivity, and credentials.");
            System.err.println("In-memory offline fallback is completely disabled. Backend startup halted.");
            System.err.println("==============================================");
            System.exit(1);
            return;
        }

        try {
            system = new ElectricitySystem();
        } catch (Exception e) {
            System.err.println();
            System.err.println("[PowerTrack Fatal Error] Failed to initialize system from Supabase: "
                    + DatabaseConnection.sanitizeErrorMessage(e.getMessage()));
            System.err.println("In-memory offline fallback is completely disabled. Backend startup halted.");
            System.err.println("==============================================");
            System.exit(1);
            return;
        }

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(PORT),
                        0
                );

        // =========================================
        // API ROUTES
        // =========================================

        server.createContext(
                "/api/network",
                PowerTrackServer::handleNetwork
        );

        server.createContext(
                "/api/consumers",
                PowerTrackServer::handleConsumers
        );

        server.createContext(
                "/api/technicians",
                PowerTrackServer::handleTechnicians
        );

        server.createContext(
                "/api/outages/active",
                PowerTrackServer::handleActiveOutages
        );

        server.createContext(
                "/api/outages/history",
                PowerTrackServer::handleHistory
        );

        server.createContext(
                "/api/outages/simulate",
                PowerTrackServer::handleSimulate
        );

        server.createContext(
                "/api/outages/process",
                PowerTrackServer::handleProcess
        );

        server.createContext(
                "/api/outages/restore",
                PowerTrackServer::handleRestore
        );

        server.createContext(
                "/api/outages/assign",
                PowerTrackServer::handleAssign
        );

        server.createContext(
                "/api/outages/update-status",
                PowerTrackServer::handleUpdateStatus
        );

        server.createContext(
                "/api/stats",
                PowerTrackServer::handleStats
        );

        server.createContext(
                "/api/health",
                PowerTrackServer::handleHealth
        );

        // =========================================
        // FRONTEND
        // =========================================

        server.createContext(
                "/",
                PowerTrackServer::handleFrontend
        );

        server.setExecutor(null);

        server.start();

        System.out.println(
                "Server started successfully!"
        );

        System.out.println(
                "Open: http://localhost:" + PORT
        );

        System.out.println(
                "Press CTRL+C to stop the server."
        );

        System.out.println(
                "=============================================="
        );
    }


    // =========================================================
    // CORS
    // =========================================================

    private static void addCorsHeaders(
            HttpExchange exchange
    ) {

        Headers headers =
                exchange.getResponseHeaders();

        headers.set(
                "Access-Control-Allow-Origin",
                "*"
        );

        headers.set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS"
        );

        headers.set(
                "Access-Control-Allow-Headers",
                "Content-Type"
        );

        headers.set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );
    }


    private static boolean handleOptions(
            HttpExchange exchange
    ) throws IOException {

        if (
                exchange.getRequestMethod()
                        .equalsIgnoreCase("OPTIONS")
        ) {

            addCorsHeaders(exchange);

            exchange.sendResponseHeaders(
                    204,
                    -1
            );

            exchange.close();

            return true;
        }

        return false;
    }


    // =========================================================
    // NETWORK
    // =========================================================

    private static void handleNetwork(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("GET")
        ) {

            sendError(
                    exchange,
                    405,
                    "Method not allowed"
            );

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
                if (i > 0) {
                    json.append(",");
                }

                json.append("{");
                json.append("\"id\":\"").append(escape(node.getNodeId())).append("\",");
                json.append("\"name\":\"").append(escape(node.getNodeName())).append("\",");
                json.append("\"type\":\"").append(escape(node.getNodeType())).append("\",");
                json.append("\"active\":").append(node.isActive());

                List<String> neighbors = connectionMap.get(node.getNodeId());
                json.append(",\"connectedTo\":[");
                if (neighbors != null) {
                    for (int j = 0; j < neighbors.size(); j++) {
                        if (j > 0) {
                            json.append(",");
                        }
                        json.append("\"").append(escape(neighbors.get(j))).append("\"");
                    }
                }
                json.append("]");

                json.append("}");
            }

            json.append("]");

            sendJson(
                    exchange,
                    200,
                    json.toString()
            );

        } catch (SQLException e) {
            sendError(
                    exchange,
                    503,
                    "Database connection unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage())
            );
        } catch (Exception e) {
            sendError(
                    exchange,
                    500,
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // CONSUMERS
    // =========================================================

    private static void handleConsumers(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("GET")
        ) {

            sendError(
                    exchange,
                    405,
                    "Method not allowed"
            );

            return;
        }

        try {
            List<Consumer> consumers = system.getConsumerDAO().getAllConsumers();

            StringBuilder json = new StringBuilder("[");

            for (int i = 0; i < consumers.size(); i++) {
                Consumer consumer = consumers.get(i);
                if (i > 0) {
                    json.append(",");
                }

                json.append("{");
                json.append("\"id\":").append(consumer.getConsumerId()).append(",");
                json.append("\"name\":\"").append(escape(consumer.getName())).append("\",");
                json.append("\"area\":\"").append(escape(consumer.getArea())).append("\",");
                json.append("\"powerAvailable\":").append(consumer.isPowerAvailable()).append(",");
                json.append("\"consumerType\":\"").append(escape(consumer.getConsumerType())).append("\",");
                json.append("\"criticalityLevel\":\"").append(escape(consumer.getCriticalityLevel())).append("\"");
                json.append("}");
            }

            json.append("]");

            sendJson(
                    exchange,
                    200,
                    json.toString()
            );

        } catch (SQLException e) {
            sendError(
                    exchange,
                    503,
                    "Database connection unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage())
            );
        } catch (Exception e) {
            sendError(
                    exchange,
                    500,
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // TECHNICIANS
    // =========================================================

    private static void handleTechnicians(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("GET")
        ) {

            sendError(
                    exchange,
                    405,
                    "Method not allowed"
            );

            return;
        }

        try {
            List<Technician> technicians = system.getTechnicianDAO().getAllTechnicians();

            StringBuilder json = new StringBuilder("[");

            for (int i = 0; i < technicians.size(); i++) {
                Technician technician = technicians.get(i);
                if (i > 0) {
                    json.append(",");
                }

                json.append("{");
                json.append("\"id\":").append(technician.getTechnicianId()).append(",");
                json.append("\"name\":\"").append(escape(technician.getName())).append("\",");
                json.append("\"specialization\":\"").append(escape(technician.getSpecialization())).append("\",");
                json.append("\"available\":").append(technician.isAvailable());
                json.append("}");
            }

            json.append("]");

            sendJson(
                    exchange,
                    200,
                    json.toString()
            );

        } catch (SQLException e) {
            sendError(
                    exchange,
                    503,
                    "Database connection unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage())
            );
        } catch (Exception e) {
            sendError(
                    exchange,
                    500,
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // ACTIVE OUTAGES
    // =========================================================

    private static void handleActiveOutages(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("GET")
        ) {

            sendError(
                    exchange,
                    405,
                    "Method not allowed"
            );

            return;
        }

        sendOutages(
                exchange,
                false
        );
    }


    // =========================================================
    // HISTORY
    // =========================================================

    private static void handleHistory(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("GET")
        ) {

            sendError(
                    exchange,
                    405,
                    "Method not allowed"
            );

            return;
        }

        sendOutages(
                exchange,
                true
        );
    }


    // =========================================================
    // OUTAGE DATA
    // =========================================================

    private static void sendOutages(
            HttpExchange exchange,
            boolean includeRestored
    ) throws IOException {

        try {
            List<Outage> outages = system.getOutageDAO().getAllOutages();

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            for (Outage outage : outages) {
                if (outage == null) {
                    continue;
                }

                if (
                        !includeRestored
                        &&
                        "RESTORED".equalsIgnoreCase(outage.getStatus())
                ) {
                    continue;
                }

                if (
                        includeRestored
                        &&
                        !"RESTORED".equalsIgnoreCase(outage.getStatus())
                ) {
                    continue;
                }

                if (!first) {
                    json.append(",");
                }
                first = false;

                json.append(
                        outageToJson(outage)
                );
            }

            json.append("]");

            sendJson(
                    exchange,
                    200,
                    json.toString()
            );

        } catch (SQLException e) {
            sendError(
                    exchange,
                    503,
                    "Database connection unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage())
            );
        } catch (Exception e) {
            sendError(
                    exchange,
                    500,
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // SIMULATE FAULT
    // =========================================================

    private static void handleSimulate(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("POST")
        ) {

            sendError(
                    exchange,
                    405,
                    "Use POST"
            );

            return;
        }

        try {
            if (!DatabaseConnection.testConnection()) {
                sendError(
                        exchange,
                        503,
                        "Database connection unavailable. Cannot simulate fault without Supabase."
                );
                return;
            }

            String body =
                    readBody(exchange);

            String component =
                    getJsonString(
                            body,
                            "component"
                    );

            if (
                    component == null
                    ||
                    component.isEmpty()
            ) {

                sendError(
                        exchange,
                        400,
                        "Component is required"
                );

                return;
            }

            component =
                    component.toUpperCase();

            int beforeCount =
                    getOutageCount();

            system.simulateFault(component);

            int afterCount =
                    getOutageCount();

            if (afterCount > beforeCount) {

                Outage outage =
                        getLastOutage();

                sendJson(
                        exchange,
                        200,
                        "{"
                        + "\"success\":true,"
                        + "\"message\":\"Fault simulated successfully on " + escape(component) + "\","
                        + "\"outage\":"
                        + outageToJson(outage)
                        + "}"
                );

            } else {

                sendJson(
                        exchange,
                        400,
                        "{"
                        + "\"success\":false,"
                        + "\"message\":\"Failed to simulate fault for component: " + escape(component) + ". Please choose an eligible feeder (FD-...) or transformer (DT-...)\""
                        + "}"
                );
            }

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Database error")) {
                sendError(exchange, 503, e.getMessage());
            } else {
                sendError(exchange, 500, e.getMessage());
            }
        } catch (Exception e) {

            sendError(
                    exchange,
                    500,
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // PROCESS NEXT OUTAGE
    // =========================================================

    private static void handleProcess(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("POST")
        ) {

            sendError(
                    exchange,
                    405,
                    "Use POST"
            );

            return;
        }

        try {
            if (!DatabaseConnection.testConnection()) {
                sendError(
                        exchange,
                        503,
                        "Database connection unavailable. Cannot process outage without Supabase."
                );
                return;
            }

            if (system.getOutageQueue().isEmpty()) {

                sendJson(
                        exchange,
                        400,
                        "{"
                        + "\"success\":false,"
                        + "\"message\":\"No active outages are waiting in the queue.\""
                        + "}"
                );

                return;
            }

            system.processNextOutage();

            Outage current =
                    getCurrentOutage();

            if (current != null && "IN PROGRESS".equals(current.getStatus())) {

                sendJson(
                        exchange,
                        200,
                        "{"
                        + "\"success\":true,"
                        + "\"message\":\"Outage OUT-" + current.getOutageId() + " assigned to technician " + escape(current.getAssignedTechnician()) + "\","
                        + "\"outage\":"
                        + outageToJson(current)
                        + "}"
                );

            } else {

                sendJson(
                        exchange,
                        400,
                        "{"
                        + "\"success\":false,"
                        + "\"message\":\"No field technician is currently available in this zone. Outage retained in queue.\""
                        + "}"
                );
            }

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Database error")) {
                sendError(exchange, 503, e.getMessage());
            } else {
                sendError(exchange, 500, e.getMessage());
            }
        } catch (Exception e) {

            sendError(
                    exchange,
                    500,
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // RESTORE OUTAGE
    // =========================================================

    private static void handleRestore(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("POST")
        ) {

            sendError(
                    exchange,
                    405,
                    "Use POST"
            );

            return;
        }

        try {
            if (!DatabaseConnection.testConnection()) {
                sendError(
                        exchange,
                        503,
                        "Database connection unavailable. Cannot restore outage without Supabase."
                );
                return;
            }

            String body =
                    readBody(exchange);

            String idString =
                    getJsonString(
                            body,
                            "outageId"
                    );

            if (
                    idString == null
                    ||
                    idString.isEmpty()
            ) {

                sendError(
                        exchange,
                        400,
                        "Outage ID is required"
                );

                return;
            }

            int outageId =
                    Integer.parseInt(idString);

            Outage outage =
                    findOutage(outageId);

            if (outage == null) {

                sendError(
                        exchange,
                        404,
                        "Outage not found"
                );

                return;
            }

            if (!"IN PROGRESS".equals(outage.getStatus())) {

                sendJson(
                        exchange,
                        400,
                        "{"
                        + "\"success\":false,"
                        + "\"message\":\"Outage OUT-" + outageId + " cannot be restored. Outage must be IN PROGRESS before restoration (Current status: " + escape(outage.getStatus()) + ").\""
                        + "}"
                );

                return;
            }

            String restorationDetails = getJsonString(body, "restorationDetails");
            system.restoreOutage(outageId, restorationDetails);

            sendJson(
                    exchange,
                    200,
                    "{"
                    + "\"success\":true,"
                    + "\"message\":\"Outage restored successfully\","
                    + "\"outage\":"
                    + outageToJson(outage)
                    + "}"
            );

        } catch (NumberFormatException e) {

            sendError(
                    exchange,
                    400,
                    "Invalid outage ID"
            );

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Database error")) {
                sendError(exchange, 503, e.getMessage());
            } else {
                sendError(exchange, 500, e.getMessage());
            }
        } catch (Exception e) {

            sendError(
                    exchange,
                    500,
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // ASSIGN OUTAGE TO SPECIFIC TECHNICIAN
    // =========================================================

    private static void handleAssign(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("POST")
        ) {
            sendError(exchange, 405, "Use POST");
            return;
        }

        try {
            if (!DatabaseConnection.testConnection()) {
                sendError(exchange, 503, "Database connection unavailable.");
                return;
            }

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

            if (updated == null) {
                sendError(exchange, 404, "Outage or Technician not found");
                return;
            }

            sendJson(
                    exchange,
                    200,
                    "{"
                    + "\"success\":true,"
                    + "\"message\":\"Outage OUT-" + outageId + " assigned to technician " + escape(updated.getAssignedTechnician()) + "\","
                    + "\"outage\":"
                    + outageToJson(updated)
                    + "}"
            );

        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid numeric format for Outage or Technician ID");
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
    // UPDATE OUTAGE WORK STATUS
    // =========================================================

    private static void handleUpdateStatus(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("POST")
        ) {
            sendError(exchange, 405, "Use POST");
            return;
        }

        try {
            if (!DatabaseConnection.testConnection()) {
                sendError(exchange, 503, "Database connection unavailable.");
                return;
            }

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
            Outage outage = findOutage(outageId);

            if (outage == null) {
                sendError(exchange, 404, "Outage not found");
                return;
            }

            targetStatus = targetStatus.toUpperCase().trim();

            if ("RESTORED".equals(targetStatus)) {
                if (!"IN PROGRESS".equals(outage.getStatus())) {
                    sendJson(exchange, 400, "{\"success\":false,\"message\":\"Outage must be IN PROGRESS before marking as RESTORED.\"}");
                    return;
                }
                system.restoreOutage(outageId, details);
                sendJson(exchange, 200, "{"
                        + "\"success\":true,"
                        + "\"message\":\"Outage OUT-" + outageId + " marked as RESTORED and power restored.\","
                        + "\"outage\":" + outageToJson(outage)
                        + "}");
                return;
            } else if ("IN PROGRESS".equals(targetStatus) || "ASSIGNED".equals(targetStatus)) {
                if (techIdStr != null && !techIdStr.trim().isEmpty()) {
                    int techId = Integer.parseInt(techIdStr.replace("T", "").trim());
                    system.assignTechnicianToOutage(outageId, techId);
                } else if (!"IN PROGRESS".equals(outage.getStatus())) {
                    system.processNextOutage();
                }
                sendJson(exchange, 200, "{"
                        + "\"success\":true,"
                        + "\"message\":\"Outage OUT-" + outageId + " status updated to " + escape(targetStatus) + "\","
                        + "\"outage\":" + outageToJson(outage)
                        + "}");
                return;
            } else {
                sendError(exchange, 400, "Unsupported status: " + targetStatus);
            }

        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid numeric format");
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
    // STATISTICS
    // =========================================================

    private static void handleStats(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("GET")
        ) {

            sendError(
                    exchange,
                    405,
                    "Method not allowed"
            );

            return;
        }

        try {
            dao.StatsDAO.SystemStats dbStats = system.getStatsDAO().getSystemStats();
            sendJson(
                    exchange,
                    200,
                    dbStats.toJson()
            );

        } catch (SQLException e) {
            sendError(
                    exchange,
                    503,
                    "Database connection unavailable: " + DatabaseConnection.sanitizeErrorMessage(e.getMessage())
            );
        } catch (Exception e) {
            sendError(
                    exchange,
                    500,
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // HEALTH CHECK
    // =========================================================

    private static void handleHealth(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        addCorsHeaders(exchange);

        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase("GET")
        ) {

            sendError(
                    exchange,
                    405,
                    "Method not allowed"
            );

            return;
        }

        boolean dbConnected = DatabaseConnection.testConnection();

        if (dbConnected) {
            sendJson(
                    exchange,
                    200,
                    "{"
                    + "\"status\":\"UP\","
                    + "\"service\":\"PowerTrack\","
                    + "\"database\":\"CONNECTED\""
                    + "}"
            );
        } else {
            sendJson(
                    exchange,
                    503,
                    "{"
                    + "\"status\":\"DOWN\","
                    + "\"service\":\"PowerTrack\","
                    + "\"database\":\"DISCONNECTED\","
                    + "\"error\":\"Database connection unavailable. PowerTrack requires an active Supabase connection.\""
                    + "}"
            );
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

    private static void handleFrontend(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        String requestPath =
                URLDecoder.decode(
                        exchange.getRequestURI()
                                .getPath(),
                        StandardCharsets.UTF_8
                );

        if (
                requestPath.equals("/")
                ||
                requestPath.isEmpty()
        ) {

            requestPath =
                    "/index.html";
        }

        Path frontendRoot =
                getFrontendRoot();

        Path requestedFile =
                frontendRoot
                        .resolve(
                                requestPath.substring(1)
                        )
                        .normalize();

        // Prevent access outside frontend folder
        if (
                !requestedFile
                        .startsWith(frontendRoot)
        ) {

            sendText(
                    exchange,
                    403,
                    "Forbidden"
            );

            return;
        }

        if (
                !Files.exists(requestedFile)
                ||
                Files.isDirectory(requestedFile)
        ) {

            sendText(
                    exchange,
                    404,
                    "File not found"
            );

            return;
        }

        byte[] data =
                Files.readAllBytes(
                        requestedFile
                );

        Headers headers =
                exchange.getResponseHeaders();

        headers.set(
                "Content-Type",
                getContentType(requestedFile)
        );

        headers.set(
                "Cache-Control",
                "no-cache"
        );

        exchange.sendResponseHeaders(
                200,
                data.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(data);
        }
    }


    // =========================================================
    // OUTAGE HELPERS VIA SUPABASE DAO
    // =========================================================

    private static int getOutageCount() throws Exception {
        return system.getOutageDAO().getAllOutages().size();
    }

    private static Outage getLastOutage() throws Exception {
        List<Outage> list = system.getOutageDAO().getAllOutages();
        if (list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1);
    }

    private static Outage getCurrentOutage() {
        return system.getCurrentOutage();
    }

    private static Outage findOutage(
            int outageId
    ) throws Exception {

        List<Outage> outages = system.getOutageDAO().getAllOutages();

        for (Outage outage : outages) {

            if (outage == null) {
                continue;
            }

            if (
                    outage.getOutageId()
                    == outageId
            ) {

                return outage;
            }
        }

        return null;
    }


    // =========================================================
    // OUTAGE JSON
    // =========================================================

    private static String outageToJson(
            Outage outage
    ) {

        StringBuilder areas =
                new StringBuilder("[");

        ArrayList<String> affectedAreas =
                outage.getAffectedAreas();

        for (int i = 0;
             i < affectedAreas.size();
             i++) {

            if (i > 0) {
                areas.append(",");
            }

            areas.append(
                    "\""
                    + escape(
                        affectedAreas.get(i)
                    )
                    + "\""
            );
        }

        areas.append("]");

        StringBuilder crit =
                new StringBuilder("[");

        ArrayList<String> criticalFacilities =
                outage.getCriticalFacilities();

        if (criticalFacilities != null) {
            for (int i = 0;
                 i < criticalFacilities.size();
                 i++) {

                if (i > 0) {
                    crit.append(",");
                }

                crit.append(
                        "\""
                        + escape(
                            criticalFacilities.get(i)
                        )
                        + "\""
                );
            }
        }

        crit.append("]");

        int queuePosition = system.getQueuePosition(outage);

        return "{"
                + "\"id\":\"OUT-"
                + outage.getOutageId()
                + "\","

                + "\"outageId\":"
                + outage.getOutageId()
                + ","

                + "\"component\":\""
                + escape(
                    outage.getFailedComponent()
                )
                + "\","

                + "\"node\":\""
                + escape(
                    outage.getFailedComponent()
                )
                + "\","

                + "\"cause\":\""
                + escape(
                    outage.getFaultType()
                )
                + "\","

                + "\"areas\":"
                + areas
                + ","

                + "\"consumers\":"
                + outage.getAffectedConsumers()
                + ","

                + "\"priority\":\""
                + escape(
                    outage.getPriority()
                )
                + "\","

                + "\"criticalFacilities\":"
                + crit
                + ","

                + "\"priorityReason\":\""
                + escape(
                    outage.getPriorityReason()
                )
                + "\","

                + "\"queuePosition\":"
                + queuePosition
                + ","

                + "\"queueSequence\":"
                + outage.getQueueSequence()
                + ","

                + "\"status\":\""
                + escape(
                    outage.getStatus()
                )
                + "\","

                + "\"technician\":\""
                + escape(
                    outage.getAssignedTechnician()
                )
                + "\","

                + "\"assignedTechnicianId\":"
                + (outage.getAssignedTechnicianId() != null ? outage.getAssignedTechnicianId() : "null")
                + ","

                + "\"reportedAt\":\""
                + (outage.getReportedAt() != null ? escape(outage.getReportedAt().toString()) : "")
                + "\","

                + "\"restoredAt\":\""
                + (outage.getRestoredAt() != null ? escape(outage.getRestoredAt().toString()) : "")
                + "\""

                + "}";
    }


    // =========================================================
    // READ REQUEST BODY
    // =========================================================

    private static String readBody(
            HttpExchange exchange
    ) throws IOException {

        try (
                InputStream input =
                        exchange.getRequestBody()
        ) {

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }


    // =========================================================
    // SIMPLE JSON VALUE READER
    // =========================================================

    private static String getJsonString(
            String json,
            String key
    ) {

        String search =
                "\"" + key + "\"";

        int keyIndex =
                json.indexOf(search);

        if (keyIndex == -1) {
            return null;
        }

        int colon =
                json.indexOf(
                        ":",
                        keyIndex
                );

        if (colon == -1) {
            return null;
        }

        int start =
                colon + 1;

        while (
                start < json.length()
                &&
                Character.isWhitespace(
                        json.charAt(start)
                )
        ) {

            start++;
        }

        // Quoted JSON string
        if (
                start < json.length()
                &&
                json.charAt(start) == '"'
        ) {

            start++;

            int end =
                    json.indexOf(
                            "\"",
                            start
                    );

            if (end == -1) {
                return null;
            }

            return json.substring(
                    start,
                    end
            );
        }

        // Number / boolean
        int end = start;

        while (
                end < json.length()
                &&
                json.charAt(end) != ','
                &&
                json.charAt(end) != '}'
                &&
                !Character.isWhitespace(
                        json.charAt(end)
                )
        ) {

            end++;
        }

        return json.substring(
                start,
                end
        );
    }


    // =========================================================
    // RESPONSE HELPERS
    // =========================================================

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String json
    ) throws IOException {

        byte[] data =
                json.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.sendResponseHeaders(
                status,
                data.length
        );

        try (
                OutputStream output =
                        exchange.getResponseBody()
        ) {

            output.write(data);
        }
    }


    private static void sendError(
            HttpExchange exchange,
            int status,
            String message
    ) throws IOException {

        addCorsHeaders(exchange);

        String json =
                "{"
                + "\"success\":false,"
                + "\"message\":\""
                + escape(
                    message == null
                        ? "Unknown error"
                        : message
                )
                + "\""
                + "}";

        sendJson(
                exchange,
                status,
                json
        );
    }


    private static void sendText(
            HttpExchange exchange,
            int status,
            String text
    ) throws IOException {

        byte[] data =
                text.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "text/plain; charset=UTF-8"
                );

        exchange.sendResponseHeaders(
                status,
                data.length
        );

        try (
                OutputStream output =
                        exchange.getResponseBody()
        ) {

            output.write(data);
        }
    }


    // =========================================================
    // JSON ESCAPE
    // =========================================================

    private static String escape(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }


    // =========================================================
    // CONTENT TYPE
    // =========================================================

    private static String getContentType(
            Path path
    ) {

        String name =
                path.getFileName()
                        .toString()
                        .toLowerCase();

        if (name.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }

        if (name.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }

        if (name.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }

        if (name.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }

        if (name.endsWith(".png")) {
            return "image/png";
        }

        if (
                name.endsWith(".jpg")
                ||
                name.endsWith(".jpeg")
        ) {

            return "image/jpeg";
        }

        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }

        return "application/octet-stream";
    }
}