package com.miniide.models;

/**
 * Represents a memory container with active/default version settings and lifecycle state.
 */
public class MemoryItem {

    private String id;
    private String agentId;
    private String topicKey;
    private Integer defaultLevel;
    private String activeVersionId;
    private Integer pinnedMinLevel;
    private String state; // active | archived | expired
    private Long lastAccessedAt;
    private Long lastAccessedActivation;
    private Integer totalAccessCount;
    private String projectEpoch;
    private java.util.List<String> tags;
    private Long activeLockUntil;
    private String activeLockReason;
    private Long createdAt;
    private Long updatedAt;

    public MemoryItem() {
        // Default constructor for Jackson
    }

    public MemoryItem(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getTopicKey() {
        return topicKey;
    }

    public void setTopicKey(String topicKey) {
        this.topicKey = topicKey;
    }

    public Integer getDefaultLevel() {
        return defaultLevel;
    }

    public void setDefaultLevel(Integer defaultLevel) {
        this.defaultLevel = defaultLevel;
    }

    public String getActiveVersionId() {
        return activeVersionId;
    }

    public void setActiveVersionId(String activeVersionId) {
        this.activeVersionId = activeVersionId;
    }

    public Integer getPinnedMinLevel() {
        return pinnedMinLevel;
    }

    public void setPinnedMinLevel(Integer pinnedMinLevel) {
        this.pinnedMinLevel = pinnedMinLevel;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Long lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Long getLastAccessedActivation() {
        return lastAccessedActivation;
    }

    public void setLastAccessedActivation(Long lastAccessedActivation) {
        this.lastAccessedActivation = lastAccessedActivation;
    }

    public Integer getTotalAccessCount() {
        return totalAccessCount;
    }

    public void setTotalAccessCount(Integer totalAccessCount) {
        this.totalAccessCount = totalAccessCount;
    }

    public String getProjectEpoch() {
        return projectEpoch;
    }

    public void setProjectEpoch(String projectEpoch) {
        this.projectEpoch = projectEpoch;
    }

    public java.util.List<String> getTags() {
        return tags;
    }

    public void setTags(java.util.List<String> tags) {
        this.tags = tags;
    }

    public Long getActiveLockUntil() {
        return activeLockUntil;
    }

    public void setActiveLockUntil(Long activeLockUntil) {
        this.activeLockUntil = activeLockUntil;
    }

    public String getActiveLockReason() {
        return activeLockReason;
    }

    public void setActiveLockReason(String activeLockReason) {
        this.activeLockReason = activeLockReason;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
