package model;

public class NetworkConnection {

    private int connectionId;
    private String sourceNodeId;
    private String destinationNodeId;
    private String connectionType;

    public NetworkConnection(String sourceNodeId, String destinationNodeId) {
        this(0, sourceNodeId, destinationNodeId, "TRANSMISSION");
    }

    public NetworkConnection(
        int connectionId,
        String sourceNodeId,
        String destinationNodeId,
        String connectionType
    ) {
        this.connectionId = connectionId;
        this.sourceNodeId = sourceNodeId;
        this.destinationNodeId = destinationNodeId;
        this.connectionType = connectionType != null ? connectionType : "TRANSMISSION";
    }

    public int getConnectionId() {
        return connectionId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public String getDestinationNodeId() {
        return destinationNodeId;
    }

    public String getConnectionType() {
        return connectionType;
    }

    @Override
    public String toString() {
        return sourceNodeId + " -> " + destinationNodeId + " (" + connectionType + ")";
    }
}
