package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class IssueMemoryRecord {
    private String agentId;
    private int issueId;
    private int interestLevel;
    private Long lastAccessedAt;
    private Long lastRefreshedAt;
    private int accessCount;
    private Boolean appliedInWork;
    private Boolean wasUseful;
    private List<String> personalTags = new ArrayList<>();
    private String note;
    private Long createdAt;
    private Long updatedAt;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public int getIssueId() {
        return issueId;
    }

    public void setIssueId(int issueId) {
        this.issueId = issueId;
    }

    public int getInterestLevel() {
        return interestLevel;
    }

    public void setInterestLevel(int interestLevel) {
        this.interestLevel = interestLevel;
    }

    public Long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Long lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Long getLastRefreshedAt() {
        return lastRefreshedAt;
    }

    public void setLastRefreshedAt(Long lastRefreshedAt) {
        this.lastRefreshedAt = lastRefreshedAt;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    public Boolean getAppliedInWork() {
        return appliedInWork;
    }

    public void setAppliedInWork(Boolean appliedInWork) {
        this.appliedInWork = appliedInWork;
    }

    public Boolean getWasUseful() {
        return wasUseful;
    }

    public void setWasUseful(Boolean wasUseful) {
        this.wasUseful = wasUseful;
    }

    public List<String> getPersonalTags() {
        return personalTags;
    }

    public void setPersonalTags(List<String> personalTags) {
        this.personalTags = personalTags != null ? new ArrayList<>(personalTags) : new ArrayList<>();
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
