package com.miniide.models;

public class TierPolicy {
    private TierCapFormula safeSteps;
    private TierCapFormula activeIssues;
    private TierCapFormula outputTokens;
    private TierCapFormula toolActions;
    private TierCapFormula parallelHandoffs;

    private double atCapThreshold;
    private int capRunStreakRequired;
    private double assistedRateMax;
    private double verificationFailRateMax;
    private int rateWindow;
    private int watchlistWindow;
    private int demotionFailureWindow;
    private int clampTasks;
    private double clampFactor;
    private long cooldownMs;

    private Integer maxNewIssuesPerIssue;
    private Integer maxFilesTouchedPerIssue;
    private Integer maxToolActionsPerIssue;
    private Integer maxOutputTokensPerIssue;

    public TierCapFormula getSafeSteps() {
        return safeSteps;
    }

    public void setSafeSteps(TierCapFormula safeSteps) {
        this.safeSteps = safeSteps;
    }

    public TierCapFormula getActiveIssues() {
        return activeIssues;
    }

    public void setActiveIssues(TierCapFormula activeIssues) {
        this.activeIssues = activeIssues;
    }

    public TierCapFormula getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(TierCapFormula outputTokens) {
        this.outputTokens = outputTokens;
    }

    public TierCapFormula getToolActions() {
        return toolActions;
    }

    public void setToolActions(TierCapFormula toolActions) {
        this.toolActions = toolActions;
    }

    public TierCapFormula getParallelHandoffs() {
        return parallelHandoffs;
    }

    public void setParallelHandoffs(TierCapFormula parallelHandoffs) {
        this.parallelHandoffs = parallelHandoffs;
    }

    public double getAtCapThreshold() {
        return atCapThreshold;
    }

    public void setAtCapThreshold(double atCapThreshold) {
        this.atCapThreshold = atCapThreshold;
    }

    public int getCapRunStreakRequired() {
        return capRunStreakRequired;
    }

    public void setCapRunStreakRequired(int capRunStreakRequired) {
        this.capRunStreakRequired = capRunStreakRequired;
    }

    public double getAssistedRateMax() {
        return assistedRateMax;
    }

    public void setAssistedRateMax(double assistedRateMax) {
        this.assistedRateMax = assistedRateMax;
    }

    public double getVerificationFailRateMax() {
        return verificationFailRateMax;
    }

    public void setVerificationFailRateMax(double verificationFailRateMax) {
        this.verificationFailRateMax = verificationFailRateMax;
    }

    public int getRateWindow() {
        return rateWindow;
    }

    public void setRateWindow(int rateWindow) {
        this.rateWindow = rateWindow;
    }

    public int getWatchlistWindow() {
        return watchlistWindow;
    }

    public void setWatchlistWindow(int watchlistWindow) {
        this.watchlistWindow = watchlistWindow;
    }

    public int getDemotionFailureWindow() {
        return demotionFailureWindow;
    }

    public void setDemotionFailureWindow(int demotionFailureWindow) {
        this.demotionFailureWindow = demotionFailureWindow;
    }

    public int getClampTasks() {
        return clampTasks;
    }

    public void setClampTasks(int clampTasks) {
        this.clampTasks = clampTasks;
    }

    public double getClampFactor() {
        return clampFactor;
    }

    public void setClampFactor(double clampFactor) {
        this.clampFactor = clampFactor;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public void setCooldownMs(long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }

    public Integer getMaxNewIssuesPerIssue() {
        return maxNewIssuesPerIssue;
    }

    public void setMaxNewIssuesPerIssue(Integer maxNewIssuesPerIssue) {
        this.maxNewIssuesPerIssue = maxNewIssuesPerIssue;
    }

    public Integer getMaxFilesTouchedPerIssue() {
        return maxFilesTouchedPerIssue;
    }

    public void setMaxFilesTouchedPerIssue(Integer maxFilesTouchedPerIssue) {
        this.maxFilesTouchedPerIssue = maxFilesTouchedPerIssue;
    }

    public Integer getMaxToolActionsPerIssue() {
        return maxToolActionsPerIssue;
    }

    public void setMaxToolActionsPerIssue(Integer maxToolActionsPerIssue) {
        this.maxToolActionsPerIssue = maxToolActionsPerIssue;
    }

    public Integer getMaxOutputTokensPerIssue() {
        return maxOutputTokensPerIssue;
    }

    public void setMaxOutputTokensPerIssue(Integer maxOutputTokensPerIssue) {
        this.maxOutputTokensPerIssue = maxOutputTokensPerIssue;
    }
}
