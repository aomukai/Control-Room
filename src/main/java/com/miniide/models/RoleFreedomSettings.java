package com.miniide.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleFreedomSettings {

    private String role;
    private String freedomLevel;
    private List<String> notifyUserOn = new ArrayList<>();
    private Integer maxActionsPerSession;
    private List<String> requireApprovalFor = new ArrayList<>();

    public RoleFreedomSettings() {
    }

    public RoleFreedomSettings(String role, String freedomLevel, List<String> notifyUserOn,
                               Integer maxActionsPerSession, List<String> requireApprovalFor) {
        this.role = role;
        this.freedomLevel = freedomLevel;
        this.notifyUserOn = notifyUserOn != null ? new ArrayList<>(notifyUserOn) : new ArrayList<>();
        this.maxActionsPerSession = maxActionsPerSession;
        this.requireApprovalFor = requireApprovalFor != null ? new ArrayList<>(requireApprovalFor) : new ArrayList<>();
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFreedomLevel() {
        return freedomLevel;
    }

    public void setFreedomLevel(String freedomLevel) {
        this.freedomLevel = freedomLevel;
    }

    public List<String> getNotifyUserOn() {
        return notifyUserOn;
    }

    public void setNotifyUserOn(List<String> notifyUserOn) {
        this.notifyUserOn = notifyUserOn != null ? new ArrayList<>(notifyUserOn) : new ArrayList<>();
    }

    public Integer getMaxActionsPerSession() {
        return maxActionsPerSession;
    }

    public void setMaxActionsPerSession(Integer maxActionsPerSession) {
        this.maxActionsPerSession = maxActionsPerSession;
    }

    public List<String> getRequireApprovalFor() {
        return requireApprovalFor;
    }

    public void setRequireApprovalFor(List<String> requireApprovalFor) {
        this.requireApprovalFor = requireApprovalFor != null ? new ArrayList<>(requireApprovalFor) : new ArrayList<>();
    }
}
