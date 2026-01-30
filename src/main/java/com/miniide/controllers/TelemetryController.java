package com.miniide.controllers;

import com.miniide.ProjectContext;
import com.miniide.TelemetryConfigStore;
import com.miniide.TelemetryStore;
import com.miniide.models.TelemetryConfig;
import com.miniide.models.TelemetrySession;
import com.miniide.models.TelemetryTotals;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

public class TelemetryController implements Controller {
    private final ProjectContext projectContext;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public TelemetryController(ProjectContext projectContext, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/telemetry/summary", this::getSummary);
        app.get("/api/telemetry/config", this::getConfig);
        app.put("/api/telemetry/config", this::updateConfig);
    }

    private TelemetryStore store() {
        return projectContext != null ? projectContext.telemetry() : null;
    }

    private void getSummary(Context ctx) {
        TelemetryStore store = store();
        if (store == null) {
            ctx.status(500).json(Map.of("error", "Telemetry store unavailable"));
            return;
        }
        TelemetryTotals totals = store.getTotals();
        TelemetrySession session = store.getCurrentSession();
        ctx.json(Map.of(
            "totals", totals,
            "session", session
        ));
    }

    private void getConfig(Context ctx) {
        TelemetryConfigStore configStore = projectContext != null ? projectContext.telemetryConfigStore() : null;
        if (configStore == null) {
            ctx.status(500).json(Map.of("error", "Telemetry config store unavailable"));
            return;
        }
        TelemetryConfig defaults = new TelemetryConfig();
        TelemetryConfig config = configStore.loadOrDefault(defaults);
        ctx.json(config);
    }

    private void updateConfig(Context ctx) {
        TelemetryConfigStore configStore = projectContext != null ? projectContext.telemetryConfigStore() : null;
        TelemetryStore store = store();
        if (configStore == null || store == null) {
            ctx.status(500).json(Map.of("error", "Telemetry config store unavailable"));
            return;
        }
        try {
            TelemetryConfig incoming = objectMapper.readValue(ctx.body(), TelemetryConfig.class);
            if (incoming == null) {
                ctx.status(400).json(Map.of("error", "Invalid telemetry config"));
                return;
            }
            configStore.save(incoming);
            store.updateConfig(incoming);
            ctx.json(incoming);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid telemetry config"));
        }
    }
}
