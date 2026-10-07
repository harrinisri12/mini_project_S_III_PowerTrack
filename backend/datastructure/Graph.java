package datastructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class Graph {

    private HashMap<String, ArrayList<String>> adjacencyList;

    public Graph() {
        adjacencyList = new HashMap<>();
    }

    // =========================================
    // ADD NODE
    // =========================================

    public void addNode(String nodeId) {

        if (!adjacencyList.containsKey(nodeId)) {
            adjacencyList.put(nodeId, new ArrayList<>());
        }
    }

    // =========================================
    // ADD EDGE
    // =========================================

    public void addEdge(String source, String destination) {

        addNode(source);
        addNode(destination);

        adjacencyList.get(source).add(destination);
        adjacencyList.get(destination).add(source);
    }

    // =========================================
    // DISPLAY GRAPH
    // =========================================

    public void displayGraph() {

        System.out.println();
        System.out.println("========== NETWORK CONNECTIONS ==========");

        for (String node : adjacencyList.keySet()) {

            System.out.print(node + " -> ");

            for (String neighbor : adjacencyList.get(node)) {
                System.out.print(neighbor + " ");
            }

            System.out.println();
        }
    }

    // =========================================
    // GET NEIGHBORS
    // =========================================

    public ArrayList<String> getNeighbors(String nodeId) {

        return adjacencyList.get(nodeId);
    }

    // =========================================
    // CHECK NODE
    // =========================================

    public boolean containsNode(String nodeId) {

        return adjacencyList.containsKey(nodeId);
    }

    // =========================================
    // BFS
    // =========================================

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

    // =========================================
    // DFS
    // =========================================

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

                dfsRecursive(
                    neighbor,
                    visited,
                    visitedOrder
                );
            }
        }
    }
}