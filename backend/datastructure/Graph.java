package datastructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class Graph {

    // Adjacency List: maps each Node ID to its list of connected neighbor Node IDs
    private HashMap<String, ArrayList<String>> adjacencyList;

    /**
     * Initializes an empty graph.
     */
    public Graph() {
        adjacencyList = new HashMap<>();
    }

    /**
     * Adds an electrical node to the graph if not already present.
     *
     * @param nodeId unique node identifier (e.g., 'SUB-01', 'FEEDER-01')
     */
    public void addNode(String nodeId) {
        if (!adjacencyList.containsKey(nodeId)) {
            adjacencyList.put(nodeId, new ArrayList<>());
        }
    }

    /**
     * Adds an undirected electrical transmission connection between two nodes.
     *
     * @param source source node identifier
     * @param destination destination node identifier
     */
    public void addEdge(String source, String destination) {
        addNode(source);
        addNode(destination);

        adjacencyList.get(source).add(destination);
        adjacencyList.get(destination).add(source);
    }

    /**
     * Returns the list of directly connected neighbor nodes for a given node.
     *
     * @param nodeId node identifier
     * @return ArrayList of connected node IDs, or null if node doesn't exist
     */
    public ArrayList<String> getNeighbors(String nodeId) {
        return adjacencyList.get(nodeId);
    }

    /**
     * Checks if a node exists in the graph.
     *
     * @param nodeId node identifier
     * @return true if node exists, false otherwise
     */
    public boolean containsNode(String nodeId) {
        return adjacencyList.containsKey(nodeId);
    }

    /**
     * Breadth-First Search (BFS) Traversal.
     * Explores the electrical network layer by layer from the starting node.
     * Used in fault simulation to determine downstream cascading effects.
     *
     * @param startNode starting node ID
     * @return ArrayList of visited node IDs in BFS order
     */
    public ArrayList<String> bfs(String startNode) {
        ArrayList<String> visitedOrder = new ArrayList<>();

        if (!adjacencyList.containsKey(startNode)) {
            return visitedOrder;
        }

        HashSet<String> visited = new HashSet<>();
        LinkedList<String> queue = new LinkedList<>();

        queue.add(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            visitedOrder.add(current);

            for (String neighbor : adjacencyList.get(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.addLast(neighbor);
                }
            }
        }

        return visitedOrder;
    }

    /**
     * Depth-First Search (DFS) Traversal.
     * Traces line paths deeply from the start node before backtracking.
     *
     * @param startNode starting node ID
     * @return ArrayList of visited node IDs in DFS order
     */
    public ArrayList<String> dfs(String startNode) {
        ArrayList<String> visitedOrder = new ArrayList<>();

        if (!adjacencyList.containsKey(startNode)) {
            return visitedOrder;
        }

        HashSet<String> visited = new HashSet<>();
        dfsRecursive(startNode, visited, visitedOrder);

        return visitedOrder;
    }

    private void dfsRecursive(
        String node,
        HashSet<String> visited,
        ArrayList<String> visitedOrder
    ) {
        visited.add(node);
        visitedOrder.add(node);

        for (String neighbor : adjacencyList.get(node)) {
            if (!visited.contains(neighbor)) {
                dfsRecursive(neighbor, visited, visitedOrder);
            }
        }
    }

    /**
     * Prints the network adjacency list to standard output (for terminal demo).
     */
    public void displayGraph() {
        System.out.println();
        System.out.println("========== NETWORK CONNECTIONS (ADJACENCY LIST) ==========");

        for (String node : adjacencyList.keySet()) {
            System.out.print(node + " -> ");
            for (String neighbor : adjacencyList.get(node)) {
                System.out.print(neighbor + " ");
            }
            System.out.println();
        }
    }
}