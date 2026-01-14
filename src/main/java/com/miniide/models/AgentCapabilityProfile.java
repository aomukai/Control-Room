package com.miniide.models;

public class AgentCapabilityProfile {
    private Integer maxSafeSteps;
    private Integer maxTaskDosage;
    private String preferredInstructionFormat;

    public Integer getMaxSafeSteps() {
        return maxSafeSteps;
    }

    public void setMaxSafeSteps(Integer maxSafeSteps) {
        this.maxSafeSteps = maxSafeSteps;
    }

    public Integer getMaxTaskDosage() {
        return maxTaskDosage;
    }

    public void setMaxTaskDosage(Integer maxTaskDosage) {
        this.maxTaskDosage = maxTaskDosage;
    }

    public String getPreferredInstructionFormat() {
        return preferredInstructionFormat;
    }

    public void setPreferredInstructionFormat(String preferredInstructionFormat) {
        this.preferredInstructionFormat = preferredInstructionFormat;
    }
}
