package com.miniide.models;

public class TierEvaluation {
    private String agentId;
    private String modelId;
    private int previousTier;
    private int currentTier;
    private boolean promoted;
    private boolean demoted;
    private boolean capClampApplied;
    private int capClampRemaining;
    private double capClampFactor;
    private double assistedRateLastN;
    private double verificationFailRateLastN;
    private int watchlistEventsLastW;
    private int capRunStreak;
    private Long cooldownUntil;
    private TierCaps caps;
    private TierCaps effectiveCaps;
    private TierTaskSnapshot task;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public int getPreviousTier() {
        return previousTier;
    }

    public void setPreviousTier(int previousTier) {
        this.previousTier = previousTier;
    }

    public int getCurrentTier() {
        return currentTier;
    }

    public void setCurrentTier(int currentTier) {
        this.currentTier = currentTier;
    }

    public boolean isPromoted() {
        return promoted;
    }

    public void setPromoted(boolean promoted) {
        this.promoted = promoted;
    }

    public boolean isDemoted() {
        return demoted;
    }

    public void setDemoted(boolean demoted) {
        this.demoted = demoted;
    }

    public boolean isCapClampApplied() {
        return capClampApplied;
    }

    public void setCapClampApplied(boolean capClampApplied) {
        this.capClampApplied = capClampApplied;
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

    public double getAssistedRateLastN() {
        return assistedRateLastN;
    }

    public void setAssistedRateLastN(double assistedRateLastN) {
        this.assistedRateLastN = assistedRateLastN;
    }

    public double getVerificationFailRateLastN() {
        return verificationFailRateLastN;
    }

    public void setVerificationFailRateLastN(double verificationFailRateLastN) {
        this.verificationFailRateLastN = verificationFailRateLastN;
    }

    public int getWatchlistEventsLastW() {
        return watchlistEventsLastW;
    }

    public void setWatchlistEventsLastW(int watchlistEventsLastW) {
        this.watchlistEventsLastW = watchlistEventsLastW;
    }

    public int getCapRunStreak() {
        return capRunStreak;
    }

    public void setCapRunStreak(int capRunStreak) {
        this.capRunStreak = capRunStreak;
    }

    public Long getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(Long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public TierCaps getCaps() {
        return caps;
    }

    public void setCaps(TierCaps caps) {
        this.caps = caps;
    }

    public TierCaps getEffectiveCaps() {
        return effectiveCaps;
    }

    public void setEffectiveCaps(TierCaps effectiveCaps) {
        this.effectiveCaps = effectiveCaps;
    }

    public TierTaskSnapshot getTask() {
        return task;
    }

    public void setTask(TierTaskSnapshot task) {
        this.task = task;
    }
}
