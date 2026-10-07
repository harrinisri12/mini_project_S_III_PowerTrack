import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import service.ElectricitySystem;

import java.io.*;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import model.Consumer;
import model.NetworkNode;
import model.Outage;
import model.Technician;

public class PowerTrackServer {

    private static final int PORT = 8080;

    private static final ElectricitySystem system =
            new ElectricitySystem();

    private static final String FRONTEND_PATH =
            "../frontend";

    public static void main(String[] args) throws Exception {

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
                "/api/stats",
                PowerTrackServer::handleStats
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

            Field field =
                    ElectricitySystem.class
                            .getDeclaredField(
                                    "networkNodes"
                            );

            field.setAccessible(true);

            NetworkNode[] nodes =
                    (NetworkNode[]) field.get(system);

            StringBuilder json =
                    new StringBuilder("[");

            for (NetworkNode node : nodes) {

                if (node == null) {
                    break;
                }

                if (json.length() > 1) {
                    json.append(",");
                }

                json.append("{");

                json.append(
                        "\"id\":\""
                                + escape(node.getNodeId())
                                + "\","
                );

                json.append(
                        "\"name\":\""
                                + escape(node.getNodeName())
                                + "\","
                );

                json.append(
                        "\"type\":\""
                                + escape(node.getNodeType())
                                + "\","
                );

                json.append(
                        "\"active\":"
                                + node.isActive()
                );

                json.append("}");
            }

            json.append("]");

            sendJson(
                    exchange,
                    200,
                    json.toString()
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

            Field field =
                    ElectricitySystem.class
                            .getDeclaredField(
                                    "consumers"
                            );

            field.setAccessible(true);

            Consumer[] consumers =
                    (Consumer[]) field.get(system);

            StringBuilder json =
                    new StringBuilder("[");

            for (Consumer consumer : consumers) {

                if (consumer == null) {
                    break;
                }

                if (json.length() > 1) {
                    json.append(",");
                }

                json.append("{");

                json.append(
                        "\"id\":"
                                + consumer.getConsumerId()
                                + ","
                );

                json.append(
                        "\"name\":\""
                                + escape(
                                    consumer.getName()
                                )
                                + "\","
                );

                json.append(
                        "\"area\":\""
                                + escape(
                                    consumer.getArea()
                                )
                                + "\","
                );

                json.append(
                        "\"powerAvailable\":"
                                + consumer.isPowerAvailable()
                );

                json.append("}");
            }

            json.append("]");

            sendJson(
                    exchange,
                    200,
                    json.toString()
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

            Field field =
                    ElectricitySystem.class
                            .getDeclaredField(
                                    "technicians"
                            );

            field.setAccessible(true);

            Technician[] technicians =
                    (Technician[]) field.get(system);

            StringBuilder json =
                    new StringBuilder("[");

            for (Technician technician : technicians) {

                if (technician == null) {
                    break;
                }

                if (json.length() > 1) {
                    json.append(",");
                }

                json.append("{");

                json.append(
                        "\"id\":"
                                + technician.getTechnicianId()
                                + ","
                );

                json.append(
                        "\"name\":\""
                                + escape(
                                    technician.getName()
                                )
                                + "\","
                );

                json.append(
                        "\"specialization\":\""
                                + escape(
                                    technician
                                        .getSpecialization()
                                )
                                + "\","
                );

                json.append(
                        "\"available\":"
                                + technician.isAvailable()
                );

                json.append("}");
            }

            json.append("]");

            sendJson(
                    exchange,
                    200,
                    json.toString()
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

            Field field =
                    ElectricitySystem.class
                            .getDeclaredField(
                                    "outages"
                            );

            field.setAccessible(true);

            Outage[] outages =
                    (Outage[]) field.get(system);

            StringBuilder json =
                    new StringBuilder("[");

            for (Outage outage : outages) {

                if (outage == null) {
                    break;
                }

                if (
                        !includeRestored
                        &&
                        outage.getStatus()
                                .equals("RESTORED")
                ) {

                    continue;
                }

                if (json.length() > 1) {
                    json.append(",");
                }

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
                        + "\"message\":\"Fault simulated successfully\","
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
                        + "\"message\":\"Unable to create outage\""
                        + "}"
                );
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

            system.processNextOutage();

            Outage current =
                    getCurrentOutage();

            if (current != null) {

                sendJson(
                        exchange,
                        200,
                        "{"
                        + "\"success\":true,"
                        + "\"message\":\"Outage assigned to technician\","
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
                        + "\"message\":\"No outage could be processed\""
                        + "}"
                );
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

            system.restoreOutage(outageId);

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

        } catch (Exception e) {

            sendError(
                    exchange,
                    500,
                    e.getMessage()
            );
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

            Consumer[] consumers =
                    getConsumers();

            NetworkNode[] nodes =
                    getNetworkNodes();

            Technician[] technicians =
                    getTechnicians();

            Outage[] outages =
                    getOutages();

            int totalConsumers = 0;
            int powerAvailable = 0;
            int activeOutages = 0;
            int restoredOutages = 0;
            int activeNodes = 0;
            int availableTechnicians = 0;

            for (Consumer consumer : consumers) {

                if (consumer == null) {
                    break;
                }

                totalConsumers++;

                if (
                        consumer.isPowerAvailable()
                ) {

                    powerAvailable++;
                }
            }

            for (NetworkNode node : nodes) {

                if (node == null) {
                    break;
                }

                if (node.isActive()) {
                    activeNodes++;
                }
            }

            for (Technician technician : technicians) {

                if (technician == null) {
                    break;
                }

                if (technician.isAvailable()) {
                    availableTechnicians++;
                }
            }

            for (Outage outage : outages) {

                if (outage == null) {
                    break;
                }

                if (
                        outage.getStatus()
                                .equals("RESTORED")
                ) {

                    restoredOutages++;

                } else {

                    activeOutages++;
                }
            }

            String json =
                    "{"
                    + "\"totalConsumers\":"
                    + totalConsumers
                    + ","
                    + "\"powerAvailable\":"
                    + powerAvailable
                    + ","
                    + "\"powerOutage\":"
                    + (totalConsumers - powerAvailable)
                    + ","
                    + "\"activeOutages\":"
                    + activeOutages
                    + ","
                    + "\"restoredOutages\":"
                    + restoredOutages
                    + ","
                    + "\"activeNodes\":"
                    + activeNodes
                    + ","
                    + "\"availableTechnicians\":"
                    + availableTechnicians
                    + "}";

            sendJson(
                    exchange,
                    200,
                    json
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
    // FRONTEND FILE SERVER
    // =========================================================

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
                Paths.get(
                        FRONTEND_PATH
                ).toAbsolutePath()
                .normalize();

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
    // GETTERS USING EXISTING ELECTRICITY SYSTEM
    // =========================================================

    private static Consumer[] getConsumers()
            throws Exception {

        Field field =
                ElectricitySystem.class
                        .getDeclaredField(
                                "consumers"
                        );

        field.setAccessible(true);

        return (Consumer[]) field.get(system);
    }


    private static NetworkNode[] getNetworkNodes()
            throws Exception {

        Field field =
                ElectricitySystem.class
                        .getDeclaredField(
                                "networkNodes"
                        );

        field.setAccessible(true);

        return (NetworkNode[]) field.get(system);
    }


    private static Technician[] getTechnicians()
            throws Exception {

        Field field =
                ElectricitySystem.class
                        .getDeclaredField(
                                "technicians"
                        );

        field.setAccessible(true);

        return (Technician[]) field.get(system);
    }


    private static Outage[] getOutages()
            throws Exception {

        Field field =
                ElectricitySystem.class
                        .getDeclaredField(
                                "outages"
                        );

        field.setAccessible(true);

        return (Outage[]) field.get(system);
    }


    private static int getOutageCount()
            throws Exception {

        Field field =
                ElectricitySystem.class
                        .getDeclaredField(
                                "outageCount"
                        );

        field.setAccessible(true);

        return field.getInt(system);
    }


    private static Outage getLastOutage()
            throws Exception {

        Outage[] outages =
                getOutages();

        int count =
                getOutageCount();

        if (count == 0) {
            return null;
        }

        return outages[count - 1];
    }


    private static Outage getCurrentOutage()
            throws Exception {

        Field field =
                ElectricitySystem.class
                        .getDeclaredField(
                                "currentOutage"
                        );

        field.setAccessible(true);

        return (Outage) field.get(system);
    }


    private static Outage findOutage(
            int outageId
    ) throws Exception {

        Outage[] outages =
                getOutages();

        for (Outage outage : outages) {

            if (outage == null) {
                break;
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

                + "\"status\":\""
                + escape(
                    outage.getStatus()
                )
                + "\","

                + "\"technician\":\""
                + escape(
                    outage.getAssignedTechnician()
                )
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