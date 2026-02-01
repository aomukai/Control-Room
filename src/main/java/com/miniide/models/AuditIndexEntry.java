package com.miniide.models;

public class AuditIndexEntry {
    private String kind;
    private String packetId;
    private String timestamp;
    private String filename;

    public AuditIndexEntry() {
    }

    public AuditIndexEntry(String kind, String packetId, String timestamp, String filename) {
        this.kind = kind;
        this.packetId = packetId;
        this.timestamp = timestamp;
        this.filename = filename;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getPacketId() {
        return packetId;
    }

    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
