package com.miniide.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentKeysMetadataFile {
    private int version = 1;
    private Map<String, List<AgentKeyMetadata>> providers = new HashMap<>();

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Map<String, List<AgentKeyMetadata>> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, List<AgentKeyMetadata>> providers) {
        this.providers = providers != null ? providers : new HashMap<>();
    }
}
