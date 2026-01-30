package com.miniide.models;

public class TelemetryCounters {
    private long activations;
    private long tokensIn;
    private long tokensOut;
    private long issueAccesses;
    private long issueDemotions;
    private long errors;

    public long getActivations() {
        return activations;
    }

    public void setActivations(long activations) {
        this.activations = activations;
    }

    public long getTokensIn() {
        return tokensIn;
    }

    public void setTokensIn(long tokensIn) {
        this.tokensIn = tokensIn;
    }

    public long getTokensOut() {
        return tokensOut;
    }

    public void setTokensOut(long tokensOut) {
        this.tokensOut = tokensOut;
    }

    public long getIssueAccesses() {
        return issueAccesses;
    }

    public void setIssueAccesses(long issueAccesses) {
        this.issueAccesses = issueAccesses;
    }

    public long getIssueDemotions() {
        return issueDemotions;
    }

    public void setIssueDemotions(long issueDemotions) {
        this.issueDemotions = issueDemotions;
    }

    public long getErrors() {
        return errors;
    }

    public void setErrors(long errors) {
        this.errors = errors;
    }

    public void incrementActivations(long count) {
        activations += Math.max(0, count);
    }

    public void incrementTokensIn(long count) {
        tokensIn += Math.max(0, count);
    }

    public void incrementTokensOut(long count) {
        tokensOut += Math.max(0, count);
    }

    public void incrementIssueAccesses(long count) {
        issueAccesses += Math.max(0, count);
    }

    public void incrementIssueDemotions(long count) {
        issueDemotions += Math.max(0, count);
    }

    public void incrementErrors(long count) {
        errors += Math.max(0, count);
    }
}
