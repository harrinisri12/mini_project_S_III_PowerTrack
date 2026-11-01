package datastructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Custom Adjacency List Graph representation of the regional electricity distribution network.
 * Supports directed downstream traversal:
 * Substation → Feeder → Transformer → Distribution Areas → Consumers
 */
public class Graph {

    // Adjacency List: maps each Node ID to its list of downstream connected neighbor Node IDs
    private final HashMap<String, ArrayList<String>> adjacencyList;

    /**
     * Initializes an empty directed graph.
     */
    public Graph() {
        this.adjacencyList = new HashMap<>();
    }

    /**
     * Adds an electrical network node if not already present.
     *
     * @param nodeId unique node identifier (e.g., 'SS-ERD-01', 'FD-ERD-01', 'DT-ERD-101')
     */
    public synchronized void addNode(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return;
        }
        String normalized = nodeId.trim().toUpperCase();
        if (!adjacencyList.containsKey(normalized)) {
            adjacencyList.put(normalized, new ArrayList<>());
        }
    }

    /**
     * Adds a directed electrical transmission/distribution connection from source to destination.
     * Directs power flow downstream: Substation → Feeder → Transformer → Area.
     *
     * @param source source node identifier (upstream)
     * @param destination destination node identifier (downstream)
     */
    public synchronized void addDirectedEdge(String source, String destination) {
        if (source == null || destination == null) {
            return;
        }
        String src = source.trim().toUpperCase();
        String dst = destination.trim().toUpperCase();

        addNode(src);
        addNode(dst);

        ArrayList<String> neighbors = adjacencyList.get(src);
        if (!neighbors.contains(dst)) {
            neighbors.add(dst);
        }
    }

    /**
     * Adds a connection between two nodes.
     * By default in PowerTrack, electrical connections follow directed downstream power distribution.
     *
     * @param source source node identifier
     * @param destination destination node identifier
     */
    public synchronized void addEdge(String source, String destination) {
        addDirectedEdge(source, destination);
    }

    /**
     * Adds a connection with explicit direction control.
     *
     * @param source source node identifier
     * @param destination destination node identifier
     * @param directed true for directed downstream flow, false for bidirectional
     */
    public synchronized void addEdge(String source, String destination, boolean directed) {
        addDirectedEdge(source, destination);
        if (!directed) {
            addDirectedEdge(destination, source);
        }
    }

    /**
     * Returns the list of directly connected downstream neighbor nodes for a given node.
     *
     * @param nodeId node identifier
     * @return ArrayList of connected downstream node IDs, or empty list if node doesn't exist
     */
    public synchronized ArrayList<String> getNeighbors(String nodeId) {
        if (nodeId == null) {
            return new ArrayList<>();
        }
        ArrayList<String> list = adjacencyList.get(nodeId.trim().toUpperCase());
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    /**
     * Checks if a node exists in the graph.
     *
     * @param nodeId node identifier
     * @return true if node exists, false otherwise
     */
    public synchronized boolean containsNode(String nodeId) {
        if (nodeId == null) {
            return false;
        }
        return adjacencyList.containsKey(nodeId.trim().toUpperCase());
    }

    /**
     * Breadth-First Search (BFS) Traversal strictly along directed downstream connections.
     * Used in fault simulation to dynamically identify all downstream impacted assets without
     * traversing upstream to unaffected substations.
     *
     * Uses a queue and visited set to guarantee termination and avoid duplicate visits or cycles.
     *
     * @param startNode starting node ID (the failed component)
     * @return ArrayList of visited node IDs in downstream BFS order
     */
    public synchronized ArrayList<String> bfs(String startNode) {
        ArrayList<String> visitedOrder = new ArrayList<>();

        if (startNode == null) {
            return visitedOrder;
        }

        String start = startNode.trim().toUpperCase();
        if (!adjacencyList.containsKey(start)) {
            return visitedOrder;
        }

        HashSet<String> visited = new HashSet<>();
        LinkedList<String> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            visitedOrder.add(current);

            ArrayList<String> neighbors = adjacencyList.get(current);
            if (neighbors != null) {
                for (String neighbor : neighbors) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.addLast(neighbor);
                    }
                }
            }
        }

        return visitedOrder;
    }

    /**
     * Dynamically discovers all downstream distribution areas (nodes prefixed with 'AREA-')
     * affected by a fault at the given component, using downstream BFS traversal.
     *
     * @param startNode component where the fault occurred
     * @return ArrayList of affected locality area IDs
     */
    public synchronized ArrayList<String> getDownstreamAreas(String startNode) {
        ArrayList<String> affectedAreas = new ArrayList<>();
        ArrayList<String> downstreamNodes = bfs(startNode);

        for (String node : downstreamNodes) {
            if (node.startsWith("AREA-")) {
                if (!affectedAreas.contains(node)) {
                    affectedAreas.add(node);
                }
            }
        }

        return affectedAreas;
    }

    /**
     * Depth-First Search (DFS) Traversal along directed downstream connections.
     * Traces line paths deeply before backtracking.
     *
     * @param startNode starting node ID
     * @return ArrayList of visited node IDs in DFS order
     */
    public synchronized ArrayList<String> dfs(String startNode) {
        ArrayList<String> visitedOrder = new ArrayList<>();

        if (startNode == null) {
            return visitedOrder;
        }

        String start = startNode.trim().toUpperCase();
        if (!adjacencyList.containsKey(start)) {
            return visitedOrder;
        }

        HashSet<String> visited = new HashSet<>();
        dfsRecursive(start, visited, visitedOrder);

        return visitedOrder;
    }

    private void dfsRecursive(
        String node,
        HashSet<String> visited,
        ArrayList<String> visitedOrder
    ) {
        visited.add(node);
        visitedOrder.add(node);

        ArrayList<String> neighbors = adjacencyList.get(node);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    dfsRecursive(neighbor, visited, visitedOrder);
                }
            }
        }
    }

    /**
     * Prints the network adjacency list to standard output (for terminal demo).
     */
    public synchronized void displayGraph() {
        System.out.println();
        System.out.println("========== NETWORK CONNECTIONS (DIRECTED TOPOLOGY) ==========");

        List<String> sortedKeys = new ArrayList<>(adjacencyList.keySet());
        Collections.sort(sortedKeys);

        for (String node : sortedKeys) {
            System.out.print(node + " -> ");
            ArrayList<String> neighbors = adjacencyList.get(node);
            if (neighbors != null && !neighbors.isEmpty()) {
                for (String neighbor : neighbors) {
                    System.out.print(neighbor + " ");
                }
            } else {
                System.out.print("(terminal / distribution end)");
            }
            System.out.println();
        }
    }
}