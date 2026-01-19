package com.miniide.models;

public class HookMatch {
    private String hook;
    private String cardStableId;
    private String matchType;
    private double confidence;
    private int start;
    private int end;

    public String getHook() {
        return hook;
    }

    public void setHook(String hook) {
        this.hook = hook;
    }

    public String getCardStableId() {
        return cardStableId;
    }

    public void setCardStableId(String cardStableId) {
        this.cardStableId = cardStableId;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }
}
