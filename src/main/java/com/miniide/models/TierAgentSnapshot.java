package com.miniide.models;

public class TierAgentSnapshot {
    private String agentId;
    private String modelId;
    private int currentTier;
    private int capRunStreak;
    private int capClampRemaining;
    private double capClampFactor;
    private Long cooldownUntil;
    private double assistedRateLastN;
    private double verificationFailRateLastN;
    private int watchlistEventsLastW;
    private TierCaps caps;
    private TierCaps effectiveCaps;

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
}
