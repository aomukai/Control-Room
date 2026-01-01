package com.miniide.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentEndpointsFile {
    private int version = 1;
    private Map<String, AgentEndpointConfig> agents = new HashMap<>();

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Map<String, AgentEndpointConfig> getAgents() {
        return agents;
    }

    public void setAgents(Map<String, AgentEndpointConfig> agents) {
        this.agents = agents != null ? agents : new HashMap<>();
    }
}
