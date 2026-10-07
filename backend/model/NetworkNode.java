package model;

public class NetworkNode {

    private String nodeId;
    private String nodeName;
    private String nodeType;
    private boolean active;

    public NetworkNode(String nodeId, String nodeName, String nodeType) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.active = true;
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