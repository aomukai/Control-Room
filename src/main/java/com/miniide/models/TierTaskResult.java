package com.miniide.models;

public class TierTaskResult {
    private Integer requiredStepsEstimate;
    private Integer requiredOutputTokensEstimate;
    private Integer requiredActiveIssuesEstimate;
    private Boolean assisted;
    private String assistedReason;
    private Boolean verified;
    private Integer retriesUsed;
    private Boolean watchlistEvent;
    private Boolean failure;
    private String failureReason;
    private Boolean criticalFailure;
    private Boolean confidenceHigh;
    private Boolean scopeExceeded;
    private Boolean uncertainty;
    private Boolean noProgress;
    private Boolean hysteria;

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

    public Boolean getAssisted() {
        return assisted;
    }

    public void setAssisted(Boolean assisted) {
        this.assisted = assisted;
    }

    public String getAssistedReason() {
        return assistedReason;
    }

    public void setAssistedReason(String assistedReason) {
        this.assistedReason = assistedReason;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Integer getRetriesUsed() {
        return retriesUsed;
    }

    public void setRetriesUsed(Integer retriesUsed) {
        this.retriesUsed = retriesUsed;
    }

    public Boolean getWatchlistEvent() {
        return watchlistEvent;
    }

    public void setWatchlistEvent(Boolean watchlistEvent) {
        this.watchlistEvent = watchlistEvent;
    }

    public Boolean getFailure() {
        return failure;
    }

    public void setFailure(Boolean failure) {
        this.failure = failure;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Boolean getCriticalFailure() {
        return criticalFailure;
    }

    public void setCriticalFailure(Boolean criticalFailure) {
        this.criticalFailure = criticalFailure;
    }

    public Boolean getConfidenceHigh() {
        return confidenceHigh;
    }

    public void setConfidenceHigh(Boolean confidenceHigh) {
        this.confidenceHigh = confidenceHigh;
    }

    public Boolean getScopeExceeded() {
        return scopeExceeded;
    }

    public void setScopeExceeded(Boolean scopeExceeded) {
        this.scopeExceeded = scopeExceeded;
    }

    public Boolean getUncertainty() {
        return uncertainty;
    }

    public void setUncertainty(Boolean uncertainty) {
        this.uncertainty = uncertainty;
    }

    public Boolean getNoProgress() {
        return noProgress;
    }

    public void setNoProgress(Boolean noProgress) {
        this.noProgress = noProgress;
    }

    public Boolean getHysteria() {
        return hysteria;
    }

    public void setHysteria(Boolean hysteria) {
        this.hysteria = hysteria;
    }
}
