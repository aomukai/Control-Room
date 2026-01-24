package com.miniide.models;

public class TierEvent {
    private String id;
    private long timestamp;
    private String agentId;
    private String modelId;
    private String type;
    private int fromTier;
    private int toTier;
    private String evidence;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getFromTier() {
        return fromTier;
    }

    public void setFromTier(int fromTier) {
        this.fromTier = fromTier;
    }

    public int getToTier() {
        return toTier;
    }

    public void setToTier(int toTier) {
        this.toTier = toTier;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }
}
