package com.miniide.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentPersonalityConfig {

    private String tone;
    private String verbosity;
    private List<String> voiceTags = new ArrayList<>();
    private String baseInstructions;

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public String getVerbosity() {
        return verbosity;
    }

    public void setVerbosity(String verbosity) {
        this.verbosity = verbosity;
    }

    public List<String> getVoiceTags() {
        return voiceTags;
    }

    public void setVoiceTags(List<String> voiceTags) {
        this.voiceTags = voiceTags != null ? new ArrayList<>(voiceTags) : new ArrayList<>();
    }

    public String getBaseInstructions() {
        return baseInstructions;
    }

    public void setBaseInstructions(String baseInstructions) {
        this.baseInstructions = baseInstructions;
    }
}
