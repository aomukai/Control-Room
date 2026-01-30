package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.TelemetryConfig;

import java.nio.file.Files;
import java.nio.file.Path;

public class TelemetryConfigStore {
    private final ObjectMapper objectMapper;
    private Path configPath;

    public TelemetryConfigStore(Path workspaceRoot, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        configure(workspaceRoot);
    }

    public void configure(Path workspaceRoot) {
        this.configPath = workspaceRoot.resolve(".control-room")
            .resolve("telemetry")
            .resolve("telemetry-config.json");
    }

    public TelemetryConfig loadOrDefault(TelemetryConfig defaults) {
        if (configPath == null || !Files.exists(configPath)) {
            return defaults;
        }
        try {
            TelemetryConfig loaded = objectMapper.readValue(configPath.toFile(), TelemetryConfig.class);
            return loaded != null ? loaded : defaults;
        } catch (Exception ignored) {
            return defaults;
        }
    }

    public void save(TelemetryConfig config) {
        if (configPath == null || config == null) {
            return;
        }
        try {
            Files.createDirectories(configPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        } catch (Exception ignored) {
        }
    }
}
