package com.miniide.models;

public class TierCapFormula {
    private double offset;
    private double multiplier;
    private double growth;
    private int ceiling;

    public double getOffset() {
        return offset;
    }

    public void setOffset(double offset) {
        this.offset = offset;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getGrowth() {
        return growth;
    }

    public void setGrowth(double growth) {
        this.growth = growth;
    }

    public int getCeiling() {
        return ceiling;
    }

    public void setCeiling(int ceiling) {
        this.ceiling = ceiling;
    }

    public int compute(int tier) {
        int t = Math.max(1, tier);
        double raw = offset + multiplier * Math.pow(growth, t - 1);
        int rounded = (int) Math.round(raw);
        if (ceiling > 0) {
            rounded = Math.min(rounded, ceiling);
        }
        return Math.max(1, rounded);
    }
}
