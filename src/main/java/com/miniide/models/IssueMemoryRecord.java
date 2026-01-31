package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class IssueMemoryRecord {
    private String agentId;
    private int issueId;
    private int interestLevel;
    private Long lastAccessedAt;
    private Long lastAccessedAtActivation;
    private Long lastRefreshedAt;
    private int accessCount;
    private Boolean appliedInWork;
    private Boolean wasUseful;
    private Integer accessWindowCount;
    private Long accessWindowStartActivation;
    private Boolean leechReviewPending;
    private Boolean leechMarked;
    private List<Integer> leechContradictionIssueIds = new ArrayList<>();
    private String leechNote;
    private Long leechFlaggedAt;
    private Long leechConfirmedAt;
    private String leechConfirmedBy;
    private Long leechDismissedAt;
    private Boolean deferredAccess;
    private String deferredTriggerType;
    private String deferredTriggerValue;
    private Integer deferredEscalateTo;
    private Boolean deferredNotify;
    private String deferredMessage;
    private String deferredReason;
    private Long deferredAt;
    private String deferredBy;
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

    public Long getLastAccessedAtActivation() {
        return lastAccessedAtActivation;
    }

    public void setLastAccessedAtActivation(Long lastAccessedAtActivation) {
        this.lastAccessedAtActivation = lastAccessedAtActivation;
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

    public Integer getAccessWindowCount() {
        return accessWindowCount;
    }

    public void setAccessWindowCount(Integer accessWindowCount) {
        this.accessWindowCount = accessWindowCount;
    }

    public Long getAccessWindowStartActivation() {
        return accessWindowStartActivation;
    }

    public void setAccessWindowStartActivation(Long accessWindowStartActivation) {
        this.accessWindowStartActivation = accessWindowStartActivation;
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

    public Boolean getLeechReviewPending() {
        return leechReviewPending;
    }

    public void setLeechReviewPending(Boolean leechReviewPending) {
        this.leechReviewPending = leechReviewPending;
    }

    public Boolean getLeechMarked() {
        return leechMarked;
    }

    public void setLeechMarked(Boolean leechMarked) {
        this.leechMarked = leechMarked;
    }

    public List<Integer> getLeechContradictionIssueIds() {
        return leechContradictionIssueIds;
    }

    public void setLeechContradictionIssueIds(List<Integer> leechContradictionIssueIds) {
        this.leechContradictionIssueIds = leechContradictionIssueIds != null
            ? new ArrayList<>(leechContradictionIssueIds)
            : new ArrayList<>();
    }

    public String getLeechNote() {
        return leechNote;
    }

    public void setLeechNote(String leechNote) {
        this.leechNote = leechNote;
    }

    public Long getLeechFlaggedAt() {
        return leechFlaggedAt;
    }

    public void setLeechFlaggedAt(Long leechFlaggedAt) {
        this.leechFlaggedAt = leechFlaggedAt;
    }

    public Long getLeechConfirmedAt() {
        return leechConfirmedAt;
    }

    public void setLeechConfirmedAt(Long leechConfirmedAt) {
        this.leechConfirmedAt = leechConfirmedAt;
    }

    public String getLeechConfirmedBy() {
        return leechConfirmedBy;
    }

    public void setLeechConfirmedBy(String leechConfirmedBy) {
        this.leechConfirmedBy = leechConfirmedBy;
    }

    public Long getLeechDismissedAt() {
        return leechDismissedAt;
    }

    public void setLeechDismissedAt(Long leechDismissedAt) {
        this.leechDismissedAt = leechDismissedAt;
    }

    public Boolean getDeferredAccess() {
        return deferredAccess;
    }

    public void setDeferredAccess(Boolean deferredAccess) {
        this.deferredAccess = deferredAccess;
    }

    public String getDeferredTriggerType() {
        return deferredTriggerType;
    }

    public void setDeferredTriggerType(String deferredTriggerType) {
        this.deferredTriggerType = deferredTriggerType;
    }

    public String getDeferredTriggerValue() {
        return deferredTriggerValue;
    }

    public void setDeferredTriggerValue(String deferredTriggerValue) {
        this.deferredTriggerValue = deferredTriggerValue;
    }

    public Integer getDeferredEscalateTo() {
        return deferredEscalateTo;
    }

    public void setDeferredEscalateTo(Integer deferredEscalateTo) {
        this.deferredEscalateTo = deferredEscalateTo;
    }

    public Boolean getDeferredNotify() {
        return deferredNotify;
    }

    public void setDeferredNotify(Boolean deferredNotify) {
        this.deferredNotify = deferredNotify;
    }

    public String getDeferredMessage() {
        return deferredMessage;
    }

    public void setDeferredMessage(String deferredMessage) {
        this.deferredMessage = deferredMessage;
    }

    public String getDeferredReason() {
        return deferredReason;
    }

    public void setDeferredReason(String deferredReason) {
        this.deferredReason = deferredReason;
    }

    public Long getDeferredAt() {
        return deferredAt;
    }

    public void setDeferredAt(Long deferredAt) {
        this.deferredAt = deferredAt;
    }

    public String getDeferredBy() {
        return deferredBy;
    }

    public void setDeferredBy(String deferredBy) {
        this.deferredBy = deferredBy;
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
