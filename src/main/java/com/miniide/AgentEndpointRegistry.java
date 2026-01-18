package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.AgentEndpointConfig;
import com.miniide.models.AgentEndpointsFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class AgentEndpointRegistry {
    private final Path registryPath;
    private final ObjectMapper objectMapper;
    private AgentEndpointsFile endpointsFile;
    private final AppLogger logger;

    public AgentEndpointRegistry(Path workspaceRoot, ObjectMapper objectMapper) {
        this.registryPath = workspaceRoot.resolve(".control-room").resolve("agents").resolve("agent-endpoints.json");
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
        if (hasWorkspaceMarker()) {
            ensureRegistryExists();
            loadFromDisk();
        } else {
            initializeEmptyRegistry();
        }
    }

    public Map<String, AgentEndpointConfig> listEndpoints() {
        if (endpointsFile == null || endpointsFile.getAgents() == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(endpointsFile.getAgents());
    }

    public AgentEndpointConfig getEndpoint(String agentId) {
        if (endpointsFile == null || endpointsFile.getAgents() == null || agentId == null) {
            return null;
        }
        return endpointsFile.getAgents().get(agentId);
    }

    public AgentEndpointConfig upsertEndpoint(String agentId, AgentEndpointConfig config) {
        if (agentId == null || agentId.isBlank() || config == null) {
            return null;
        }
        if (endpointsFile == null) {
            endpointsFile = new AgentEndpointsFile();
        }
        endpointsFile.getAgents().put(agentId, config);
        saveToDisk();
        return config;
    }

    private void ensureRegistryExists() {
        if (!hasWorkspaceMarker()) {
            logger.info("Skipping agent endpoints registry creation - no .control-room marker (NO_PROJECT state)");
            return;
        }
        if (Files.exists(registryPath)) {
            return;
        }

        try {
            Files.createDirectories(registryPath.getParent());
            AgentEndpointsFile seed = new AgentEndpointsFile();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), seed);
        } catch (IOException e) {
            logger.warn("Failed to create agent endpoints registry: " + e.getMessage());
        }
    }

    private void loadFromDisk() {
        if (!hasWorkspaceMarker()) {
            initializeEmptyRegistry();
            return;
        }
        try {
            endpointsFile = objectMapper.readValue(registryPath.toFile(), AgentEndpointsFile.class);
            if (endpointsFile.getAgents() == null) {
                endpointsFile.setAgents(new java.util.HashMap<>());
            }
        } catch (IOException e) {
            logger.warn("Failed to load agent endpoints registry: " + e.getMessage());
            initializeEmptyRegistry();
        }
    }

    private boolean hasWorkspaceMarker() {
        Path controlRoomDir = registryPath.getParent().getParent();
        return Files.exists(controlRoomDir.resolve("workspace.json"));
    }

    private void initializeEmptyRegistry() {
        endpointsFile = new AgentEndpointsFile();
        endpointsFile.setAgents(new java.util.HashMap<>());
    }

    private void saveToDisk() {
        try {
            Files.createDirectories(registryPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), endpointsFile);
        } catch (IOException e) {
            logger.error("Failed to save agent endpoints registry: " + e.getMessage());
        }
    }
}
