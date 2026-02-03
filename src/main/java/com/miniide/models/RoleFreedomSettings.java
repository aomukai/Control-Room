package com.miniide.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleFreedomSettings {

    private String role;
    private String template;                  // "autonomous" | "balanced" | "verbose" | "custom"
    private String freedomLevel;
    private List<String> notifyUserOn = new ArrayList<>();
    private List<String> requireApprovalFor = new ArrayList<>();
    private String roleCharter;               // Job description text
    private String collaborationGuidance;     // How to collaborate/escalate
    private String toolAndSafetyNotes;        // Tool preferences, safety constraints

    public RoleFreedomSettings() {
    }

    public RoleFreedomSettings(String role, String freedomLevel, List<String> notifyUserOn,
                               List<String> requireApprovalFor) {
        this.role = role;
        this.freedomLevel = freedomLevel;
        this.notifyUserOn = notifyUserOn != null ? new ArrayList<>(notifyUserOn) : new ArrayList<>();
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

    public List<String> getRequireApprovalFor() {
        return requireApprovalFor;
    }

    public void setRequireApprovalFor(List<String> requireApprovalFor) {
        this.requireApprovalFor = requireApprovalFor != null ? new ArrayList<>(requireApprovalFor) : new ArrayList<>();
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getRoleCharter() {
        return roleCharter;
    }

    public void setRoleCharter(String roleCharter) {
        this.roleCharter = roleCharter;
    }

    public String getCollaborationGuidance() {
        return collaborationGuidance;
    }

    public void setCollaborationGuidance(String collaborationGuidance) {
        this.collaborationGuidance = collaborationGuidance;
    }

    public String getToolAndSafetyNotes() {
        return toolAndSafetyNotes;
    }

    public void setToolAndSafetyNotes(String toolAndSafetyNotes) {
        this.toolAndSafetyNotes = toolAndSafetyNotes;
    }
}
