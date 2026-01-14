package com.miniide.models;

public class AgentPerformanceStats {
    private int totalTasks;
    private int successfulTasks;
    private int failedTasks;
    private int scopeExceededCount;
    private int uncertaintyCount;
    private int noProgressCount;
    private int hysteriaCount;
    private Long lastEvaluatedAt;

    public int getTotalTasks() {
        return totalTasks;
    }

    public void setTotalTasks(int totalTasks) {
        this.totalTasks = totalTasks;
    }

    public int getSuccessfulTasks() {
        return successfulTasks;
    }

    public void setSuccessfulTasks(int successfulTasks) {
        this.successfulTasks = successfulTasks;
    }

    public int getFailedTasks() {
        return failedTasks;
    }

    public void setFailedTasks(int failedTasks) {
        this.failedTasks = failedTasks;
    }

    public int getScopeExceededCount() {
        return scopeExceededCount;
    }

    public void setScopeExceededCount(int scopeExceededCount) {
        this.scopeExceededCount = scopeExceededCount;
    }

    public int getUncertaintyCount() {
        return uncertaintyCount;
    }

    public void setUncertaintyCount(int uncertaintyCount) {
        this.uncertaintyCount = uncertaintyCount;
    }

    public int getNoProgressCount() {
        return noProgressCount;
    }

    public void setNoProgressCount(int noProgressCount) {
        this.noProgressCount = noProgressCount;
    }

    public int getHysteriaCount() {
        return hysteriaCount;
    }

    public void setHysteriaCount(int hysteriaCount) {
        this.hysteriaCount = hysteriaCount;
    }

    public Long getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(Long lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }
}
