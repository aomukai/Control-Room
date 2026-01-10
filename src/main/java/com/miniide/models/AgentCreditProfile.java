package com.miniide.models;

import java.util.HashMap;
import java.util.Map;

public class AgentCreditProfile {

    private String agentId;
    private double lifetimeCredits;
    private double currentCredits;
    private Map<String, Double> creditsByReason = new HashMap<>();
    private double creditsThisSession;
    private double creditsThisChapter;
    private double verificationRate;
    private double applicationRate;
    private double penaltyRate;
    private int currentVerifiedStreak;
    private int longestVerifiedStreak;
    private String reliabilityTier;
    private double recentDelta;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public double getLifetimeCredits() {
        return lifetimeCredits;
    }

    public void setLifetimeCredits(double lifetimeCredits) {
        this.lifetimeCredits = lifetimeCredits;
    }

    public double getCurrentCredits() {
        return currentCredits;
    }

    public void setCurrentCredits(double currentCredits) {
        this.currentCredits = currentCredits;
    }

    public Map<String, Double> getCreditsByReason() {
        return creditsByReason;
    }

    public void setCreditsByReason(Map<String, Double> creditsByReason) {
        this.creditsByReason = creditsByReason != null ? creditsByReason : new HashMap<>();
    }

    public double getCreditsThisSession() {
        return creditsThisSession;
    }

    public void setCreditsThisSession(double creditsThisSession) {
        this.creditsThisSession = creditsThisSession;
    }

    public double getCreditsThisChapter() {
        return creditsThisChapter;
    }

    public void setCreditsThisChapter(double creditsThisChapter) {
        this.creditsThisChapter = creditsThisChapter;
    }

    public double getVerificationRate() {
        return verificationRate;
    }

    public void setVerificationRate(double verificationRate) {
        this.verificationRate = verificationRate;
    }

    public double getApplicationRate() {
        return applicationRate;
    }

    public void setApplicationRate(double applicationRate) {
        this.applicationRate = applicationRate;
    }

    public double getPenaltyRate() {
        return penaltyRate;
    }

    public void setPenaltyRate(double penaltyRate) {
        this.penaltyRate = penaltyRate;
    }

    public int getCurrentVerifiedStreak() {
        return currentVerifiedStreak;
    }

    public void setCurrentVerifiedStreak(int currentVerifiedStreak) {
        this.currentVerifiedStreak = currentVerifiedStreak;
    }

    public int getLongestVerifiedStreak() {
        return longestVerifiedStreak;
    }

    public void setLongestVerifiedStreak(int longestVerifiedStreak) {
        this.longestVerifiedStreak = longestVerifiedStreak;
    }

    public String getReliabilityTier() {
        return reliabilityTier;
    }

    public void setReliabilityTier(String reliabilityTier) {
        this.reliabilityTier = reliabilityTier;
    }

    public double getRecentDelta() {
        return recentDelta;
    }

    public void setRecentDelta(double recentDelta) {
        this.recentDelta = recentDelta;
    }
}
