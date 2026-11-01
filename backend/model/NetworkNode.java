package model;

public class NetworkNode {

    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String gridLocalityArea;
    private boolean active;

    public NetworkNode(String nodeId, String nodeName, String nodeType) {
        this(nodeId, nodeName, nodeType, null, true);
    }

    public NetworkNode(
        String nodeId,
        String nodeName,
        String nodeType,
        String gridLocalityArea,
        boolean active
    ) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.gridLocalityArea = gridLocalityArea;
        this.active = active;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getGridLocalityArea() {
        return gridLocalityArea;
    }

    public void setGridLocalityArea(String gridLocalityArea) {
        this.gridLocalityArea = gridLocalityArea;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void display() {
        System.out.println(
            "ID: " + nodeId +
            " | Name: " + nodeName +
            " | Type: " + nodeType +
            " | Status: " +
            (active ? "ACTIVE" : "FAULT")
        );
    }
}