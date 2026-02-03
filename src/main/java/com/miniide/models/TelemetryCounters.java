package com.miniide.models;

public class TelemetryCounters {
    private long activations;
    private long tokensIn;
    private long tokensOut;
    private long issueAccesses;
    private long issueDemotions;
    private long errors;
    private long rejectEvidenceMissingOrInvalid;
    private long rejectQuoteNotFound;
    private long rejectToolSyntaxInText;
    private long cotLeakDetected;
    private long formatError;

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

    public long getRejectEvidenceMissingOrInvalid() {
        return rejectEvidenceMissingOrInvalid;
    }

    public void setRejectEvidenceMissingOrInvalid(long rejectEvidenceMissingOrInvalid) {
        this.rejectEvidenceMissingOrInvalid = rejectEvidenceMissingOrInvalid;
    }

    public long getRejectQuoteNotFound() {
        return rejectQuoteNotFound;
    }

    public void setRejectQuoteNotFound(long rejectQuoteNotFound) {
        this.rejectQuoteNotFound = rejectQuoteNotFound;
    }

    public long getRejectToolSyntaxInText() {
        return rejectToolSyntaxInText;
    }

    public void setRejectToolSyntaxInText(long rejectToolSyntaxInText) {
        this.rejectToolSyntaxInText = rejectToolSyntaxInText;
    }

    public long getCotLeakDetected() {
        return cotLeakDetected;
    }

    public void setCotLeakDetected(long cotLeakDetected) {
        this.cotLeakDetected = cotLeakDetected;
    }

    public long getFormatError() {
        return formatError;
    }

    public void setFormatError(long formatError) {
        this.formatError = formatError;
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

    public void incrementRejectEvidenceMissingOrInvalid(long count) {
        rejectEvidenceMissingOrInvalid += Math.max(0, count);
    }

    public void incrementRejectQuoteNotFound(long count) {
        rejectQuoteNotFound += Math.max(0, count);
    }

    public void incrementRejectToolSyntaxInText(long count) {
        rejectToolSyntaxInText += Math.max(0, count);
    }

    public void incrementCotLeakDetected(long count) {
        cotLeakDetected += Math.max(0, count);
    }

    public void incrementFormatError(long count) {
        formatError += Math.max(0, count);
    }
}
