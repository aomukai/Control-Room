package com.miniide.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentsFile {

    private int version = 1;
    private List<Agent> agents = new ArrayList<>();
    private List<RoleFreedomSettings> roleSettings = new ArrayList<>();

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<Agent> getAgents() {
        return agents;
    }

    public void setAgents(List<Agent> agents) {
        this.agents = agents != null ? new ArrayList<>(agents) : new ArrayList<>();
    }

    public List<RoleFreedomSettings> getRoleSettings() {
        return roleSettings;
    }

    public void setRoleSettings(List<RoleFreedomSettings> roleSettings) {
        this.roleSettings = roleSettings != null ? new ArrayList<>(roleSettings) : new ArrayList<>();
    }
}
