package com.miniide.models;

public class AgentPerformanceStats {
    private int totalTasks;
    private int successfulTasks;
    private int failedTasks;
    private int scopeExceededCount;
    private int uncertaintyCount;
    private int noProgressCount;
    private int hysteriaCount;
    private int currentTier;
    private int capRunStreak;
    private int capClampRemaining;
    private double capClampFactor;
    private Long cooldownUntil;
    private Long lastPromotionAt;
    private Long lastDemotionAt;
    private java.util.List<TierTaskSnapshot> recentTasks = new java.util.ArrayList<>();
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

    public int getCurrentTier() {
        return currentTier;
    }

    public void setCurrentTier(int currentTier) {
        this.currentTier = currentTier;
    }

    public int getCapRunStreak() {
        return capRunStreak;
    }

    public void setCapRunStreak(int capRunStreak) {
        this.capRunStreak = capRunStreak;
    }

    public int getCapClampRemaining() {
        return capClampRemaining;
    }

    public void setCapClampRemaining(int capClampRemaining) {
        this.capClampRemaining = capClampRemaining;
    }

    public double getCapClampFactor() {
        return capClampFactor;
    }

    public void setCapClampFactor(double capClampFactor) {
        this.capClampFactor = capClampFactor;
    }

    public Long getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(Long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public Long getLastPromotionAt() {
        return lastPromotionAt;
    }

    public void setLastPromotionAt(Long lastPromotionAt) {
        this.lastPromotionAt = lastPromotionAt;
    }

    public Long getLastDemotionAt() {
        return lastDemotionAt;
    }

    public void setLastDemotionAt(Long lastDemotionAt) {
        this.lastDemotionAt = lastDemotionAt;
    }

    public java.util.List<TierTaskSnapshot> getRecentTasks() {
        return recentTasks;
    }

    public void setRecentTasks(java.util.List<TierTaskSnapshot> recentTasks) {
        this.recentTasks = recentTasks != null ? recentTasks : new java.util.ArrayList<>();
    }

    public Long getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(Long lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }
}
