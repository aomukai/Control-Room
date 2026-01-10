package com.miniide.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Agent {

    private String id;
    private String name;
    private String role;
    private boolean enabled;
    private String avatar;
    private String color;
    private AgentEndpointConfig endpoint;
    private List<String> skills = new ArrayList<>();
    private List<String> goals = new ArrayList<>();
    private AgentPersonalityConfig personality;
    private Map<String, Integer> personalitySliders = new HashMap<>();
    private String signatureLine;
    private List<AgentToolCapability> tools = new ArrayList<>();
    private List<AgentAutoActionConfig> autoActions = new ArrayList<>();
    private Boolean isPrimaryForRole;
    private Boolean canBeTeamLead;
    private AgentMemoryProfile memoryProfile;
    private Boolean assisted;
    private String assistedReason;
    private Long assistedSince;
    private String assistedModel;
    private String clonedFrom;
    private long createdAt;
    private long updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public AgentEndpointConfig getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(AgentEndpointConfig endpoint) {
        this.endpoint = endpoint;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? new ArrayList<>(skills) : new ArrayList<>();
    }

    public List<String> getGoals() {
        return goals;
    }

    public void setGoals(List<String> goals) {
        this.goals = goals != null ? new ArrayList<>(goals) : new ArrayList<>();
    }

    public AgentPersonalityConfig getPersonality() {
        return personality;
    }

    public void setPersonality(AgentPersonalityConfig personality) {
        this.personality = personality;
    }

    public Map<String, Integer> getPersonalitySliders() {
        return personalitySliders;
    }

    public void setPersonalitySliders(Map<String, Integer> personalitySliders) {
        this.personalitySliders = personalitySliders != null ? new HashMap<>(personalitySliders) : new HashMap<>();
    }

    public String getSignatureLine() {
        return signatureLine;
    }

    public void setSignatureLine(String signatureLine) {
        this.signatureLine = signatureLine;
    }

    public List<AgentToolCapability> getTools() {
        return tools;
    }

    public void setTools(List<AgentToolCapability> tools) {
        this.tools = tools != null ? new ArrayList<>(tools) : new ArrayList<>();
    }

    public List<AgentAutoActionConfig> getAutoActions() {
        return autoActions;
    }

    public void setAutoActions(List<AgentAutoActionConfig> autoActions) {
        this.autoActions = autoActions != null ? new ArrayList<>(autoActions) : new ArrayList<>();
    }

    public Boolean getIsPrimaryForRole() {
        return isPrimaryForRole;
    }

    public void setIsPrimaryForRole(Boolean isPrimaryForRole) {
        this.isPrimaryForRole = isPrimaryForRole;
    }

    public Boolean getCanBeTeamLead() {
        return canBeTeamLead;
    }

    public void setCanBeTeamLead(Boolean canBeTeamLead) {
        this.canBeTeamLead = canBeTeamLead;
    }

    public AgentMemoryProfile getMemoryProfile() {
        return memoryProfile;
    }

    public void setMemoryProfile(AgentMemoryProfile memoryProfile) {
        this.memoryProfile = memoryProfile;
    }

    public Boolean getAssisted() {
        return assisted;
    }

    public void setAssisted(Boolean assisted) {
        this.assisted = assisted;
    }

    public String getAssistedReason() {
        return assistedReason;
    }

    public void setAssistedReason(String assistedReason) {
        this.assistedReason = assistedReason;
    }

    public Long getAssistedSince() {
        return assistedSince;
    }

    public void setAssistedSince(Long assistedSince) {
        this.assistedSince = assistedSince;
    }

    public String getAssistedModel() {
        return assistedModel;
    }

    public void setAssistedModel(String assistedModel) {
        this.assistedModel = assistedModel;
    }

    public String getClonedFrom() {
        return clonedFrom;
    }

    public void setClonedFrom(String clonedFrom) {
        this.clonedFrom = clonedFrom;
    }

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
}
