package com.miniide.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentAutoActionConfig {

    private String id;
    private Map<String, Object> trigger;
    private String toolId;
    private boolean enabled;
    private Integer maxRunsPerSession;
    private Integer minIntervalMs;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getTrigger() {
        return trigger;
    }

    public void setTrigger(Map<String, Object> trigger) {
        this.trigger = trigger;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getMaxRunsPerSession() {
        return maxRunsPerSession;
    }

    public void setMaxRunsPerSession(Integer maxRunsPerSession) {
        this.maxRunsPerSession = maxRunsPerSession;
    }

    public Integer getMinIntervalMs() {
        return minIntervalMs;
    }

    public void setMinIntervalMs(Integer minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }
}
