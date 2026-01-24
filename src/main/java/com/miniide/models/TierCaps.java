package com.miniide.models;

public class TierCaps {
    private int maxSafeSteps;
    private int maxActiveIssues;
    private int maxOutputTokens;
    private int maxToolActions;
    private Integer maxParallelHandoffs;

    public int getMaxSafeSteps() {
        return maxSafeSteps;
    }

    public void setMaxSafeSteps(int maxSafeSteps) {
        this.maxSafeSteps = maxSafeSteps;
    }

    public int getMaxActiveIssues() {
        return maxActiveIssues;
    }

    public void setMaxActiveIssues(int maxActiveIssues) {
        this.maxActiveIssues = maxActiveIssues;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxToolActions() {
        return maxToolActions;
    }

    public void setMaxToolActions(int maxToolActions) {
        this.maxToolActions = maxToolActions;
    }

    public Integer getMaxParallelHandoffs() {
        return maxParallelHandoffs;
    }

    public void setMaxParallelHandoffs(Integer maxParallelHandoffs) {
        this.maxParallelHandoffs = maxParallelHandoffs;
    }
}
