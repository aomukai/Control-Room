package com.miniide.models;

public class AgentModelRecord {
    private String modelId;
    private String role;
    private boolean active;
    private long activatedAt;
    private Long deactivatedAt;
    private AgentCapabilityProfile capabilityProfile;
    private AgentPerformanceStats performance;

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(long activatedAt) {
        this.activatedAt = activatedAt;
    }

    public Long getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(Long deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }

    public AgentCapabilityProfile getCapabilityProfile() {
        return capabilityProfile;
    }

    public void setCapabilityProfile(AgentCapabilityProfile capabilityProfile) {
        this.capabilityProfile = capabilityProfile;
    }

    public AgentPerformanceStats getPerformance() {
        return performance;
    }

    public void setPerformance(AgentPerformanceStats performance) {
        this.performance = performance;
    }
}
