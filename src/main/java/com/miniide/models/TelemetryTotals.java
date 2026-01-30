package com.miniide.models;

import java.util.HashMap;
import java.util.Map;

public class TelemetryTotals {
    private long createdAt;
    private long updatedAt;
    private TelemetryCounters totals = new TelemetryCounters();
    private Map<String, TelemetryCounters> agents = new HashMap<>();

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public TelemetryCounters getTotals() {
        return totals;
    }

    public void setTotals(TelemetryCounters totals) {
        this.totals = totals != null ? totals : new TelemetryCounters();
    }

    public Map<String, TelemetryCounters> getAgents() {
        return agents;
    }

    public void setAgents(Map<String, TelemetryCounters> agents) {
        this.agents = agents != null ? agents : new HashMap<>();
    }
}
