package com.miniide.models;

import java.util.HashMap;
import java.util.Map;

public class TelemetrySession {
    private String id;
    private long startedAt;
    private long updatedAt;
    private TelemetryCounters totals = new TelemetryCounters();
    private Map<String, TelemetryCounters> agents = new HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
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
