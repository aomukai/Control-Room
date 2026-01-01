package com.miniide.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentKeyRecord {
    private String id;
    private String label;
    private String key;
    private long createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public AgentKeyMetadata toMetadata() {
        AgentKeyMetadata meta = new AgentKeyMetadata();
        meta.setId(id);
        meta.setLabel(label);
        meta.setCreatedAt(createdAt);
        return meta;
    }
}
