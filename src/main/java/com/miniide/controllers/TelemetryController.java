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
        app.get("/api/telemetry/status", this::getStatus);
        app.post("/api/telemetry/test", this::runTest);
        app.post("/api/telemetry/conference", this::recordConferenceEvent);
        app.post("/api/telemetry/prune", this::pruneNow);
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

    private void getStatus(Context ctx) {
        TelemetryStore store = store();
        if (store == null) {
            ctx.status(500).json(Map.of("error", "Telemetry store unavailable"));
            return;
        }
        ctx.json(store.getStatusSnapshot());
    }

    private void runTest(Context ctx) {
        TelemetryStore store = store();
        if (store == null) {
            ctx.status(500).json(Map.of("error", "Telemetry store unavailable"));
            return;
        }
        String agentId = null;
        long tokensIn = 120;
        long tokensOut = 80;
        int activations = 1;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("agentId")) {
                    agentId = json.get("agentId").asText(null);
                }
                if (json.has("tokensIn")) {
                    tokensIn = Math.max(0, json.get("tokensIn").asLong(tokensIn));
                }
                if (json.has("tokensOut")) {
                    tokensOut = Math.max(0, json.get("tokensOut").asLong(tokensOut));
                }
                if (json.has("activations")) {
                    activations = Math.max(1, json.get("activations").asInt(activations));
                }
            }
        } catch (Exception ignored) {
        }
        store.recordActivation(agentId != null ? agentId : "system", activations);
        store.recordIssueAccess(agentId != null ? agentId : "system");
        store.recordTokens(agentId != null ? agentId : "system", tokensIn, tokensOut);
        ctx.json(store.getStatusSnapshot());
    }

    private void pruneNow(Context ctx) {
        TelemetryStore store = store();
        if (store == null) {
            ctx.status(500).json(Map.of("error", "Telemetry store unavailable"));
            return;
        }
        int deleted = store.pruneNow();
        ctx.json(Map.of("deleted", deleted));
    }

    private void recordConferenceEvent(Context ctx) {
        TelemetryStore store = store();
        if (store == null) {
            ctx.status(500).json(Map.of("error", "Telemetry store unavailable"));
            return;
        }
        String conferenceId = null;
        String agentId = null;
        String type = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("conferenceId")) {
                    conferenceId = json.get("conferenceId").asText(null);
                }
                if (json.has("agentId")) {
                    agentId = json.get("agentId").asText(null);
                }
                if (json.has("type")) {
                    type = json.get("type").asText(null);
                }
            }
        } catch (Exception ignored) {
        }
        if (conferenceId == null || conferenceId.isBlank() || type == null || type.isBlank()) {
            ctx.status(400).json(Map.of("error", "conferenceId and type are required"));
            return;
        }
        store.recordRejection(conferenceId, agentId, type);
        ctx.json(Map.of("status", "ok"));
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
