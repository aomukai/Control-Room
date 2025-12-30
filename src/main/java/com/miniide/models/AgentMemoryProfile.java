package com.miniide.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentMemoryProfile {

    private String retention;
    private List<String> focusTags = new ArrayList<>();
    private Integer maxInterestLevel;
    private boolean canPinIssues;

    public String getRetention() {
        return retention;
    }

    public void setRetention(String retention) {
        this.retention = retention;
    }

    public List<String> getFocusTags() {
        return focusTags;
    }

    public void setFocusTags(List<String> focusTags) {
        this.focusTags = focusTags != null ? new ArrayList<>(focusTags) : new ArrayList<>();
    }

    public Integer getMaxInterestLevel() {
        return maxInterestLevel;
    }

    public void setMaxInterestLevel(Integer maxInterestLevel) {
        this.maxInterestLevel = maxInterestLevel;
    }

    public boolean isCanPinIssues() {
        return canPinIssues;
    }

    public void setCanPinIssues(boolean canPinIssues) {
        this.canPinIssues = canPinIssues;
    }
}
