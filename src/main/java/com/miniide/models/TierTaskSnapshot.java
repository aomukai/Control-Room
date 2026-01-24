package com.miniide.models;

public class TierTaskSnapshot {
    private long timestamp;
    private int tier;
    private boolean assisted;
    private String assistedReason;
    private boolean verified;
    private boolean failure;
    private String failureReason;
    private boolean criticalFailure;
    private boolean atCap;
    private String atCapDimension;
    private boolean watchlistEvent;
    private Boolean confidenceHigh;
    private Integer retriesUsed;
    private Integer requiredStepsEstimate;
    private Integer requiredOutputTokensEstimate;
    private Integer requiredActiveIssuesEstimate;

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public boolean isAssisted() {
        return assisted;
    }

    public void setAssisted(boolean assisted) {
        this.assisted = assisted;
    }

    public String getAssistedReason() {
        return assistedReason;
    }

    public void setAssistedReason(String assistedReason) {
        this.assistedReason = assistedReason;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public boolean isFailure() {
        return failure;
    }

    public void setFailure(boolean failure) {
        this.failure = failure;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean isCriticalFailure() {
        return criticalFailure;
    }

    public void setCriticalFailure(boolean criticalFailure) {
        this.criticalFailure = criticalFailure;
    }

    public boolean isAtCap() {
        return atCap;
    }

    public void setAtCap(boolean atCap) {
        this.atCap = atCap;
    }

    public String getAtCapDimension() {
        return atCapDimension;
    }

    public void setAtCapDimension(String atCapDimension) {
        this.atCapDimension = atCapDimension;
    }

    public boolean isWatchlistEvent() {
        return watchlistEvent;
    }

    public void setWatchlistEvent(boolean watchlistEvent) {
        this.watchlistEvent = watchlistEvent;
    }

    public Boolean getConfidenceHigh() {
        return confidenceHigh;
    }

    public void setConfidenceHigh(Boolean confidenceHigh) {
        this.confidenceHigh = confidenceHigh;
    }

    public Integer getRetriesUsed() {
        return retriesUsed;
    }

    public void setRetriesUsed(Integer retriesUsed) {
        this.retriesUsed = retriesUsed;
    }

    public Integer getRequiredStepsEstimate() {
        return requiredStepsEstimate;
    }

    public void setRequiredStepsEstimate(Integer requiredStepsEstimate) {
        this.requiredStepsEstimate = requiredStepsEstimate;
    }

    public Integer getRequiredOutputTokensEstimate() {
        return requiredOutputTokensEstimate;
    }

    public void setRequiredOutputTokensEstimate(Integer requiredOutputTokensEstimate) {
        this.requiredOutputTokensEstimate = requiredOutputTokensEstimate;
    }

    public Integer getRequiredActiveIssuesEstimate() {
        return requiredActiveIssuesEstimate;
    }

    public void setRequiredActiveIssuesEstimate(Integer requiredActiveIssuesEstimate) {
        this.requiredActiveIssuesEstimate = requiredActiveIssuesEstimate;
    }
}
