package com.miniide.models;

public class TelemetryConfig {
    private boolean enabled = true;
    private int maxSessions = 200;
    private int maxAgeDays = 90;
    private int maxTotalMb = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    public int getMaxAgeDays() {
        return maxAgeDays;
    }

    public void setMaxAgeDays(int maxAgeDays) {
        this.maxAgeDays = maxAgeDays;
    }

    public int getMaxTotalMb() {
        return maxTotalMb;
    }

    public void setMaxTotalMb(int maxTotalMb) {
        this.maxTotalMb = maxTotalMb;
    }
}
