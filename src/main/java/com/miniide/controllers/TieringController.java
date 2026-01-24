package com.miniide.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.ProjectContext;
import com.miniide.TieringService;
import com.miniide.models.TierAgentSnapshot;
import com.miniide.models.TierCaps;
import com.miniide.models.TierEvaluation;
import com.miniide.models.TierPolicy;
import com.miniide.models.TierTaskResult;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.Map;

public class TieringController implements Controller {

    private final ProjectContext projectContext;
    private final ObjectMapper objectMapper;

    public TieringController(ProjectContext projectContext, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/tiers/policy", this::getPolicy);
        app.put("/api/tiers/policy", this::updatePolicy);
        app.get("/api/tiers/agents", this::listAgents);
        app.get("/api/tiers/agents/{id}", this::getAgent);
        app.get("/api/tiers/agents/{id}/caps", this::getAgentCaps);
        app.post("/api/tiers/agents/{id}/task-result", this::recordTaskResult);
    }

    private TieringService tiering() {
        return projectContext != null ? projectContext.tiering() : null;
    }

    private void getPolicy(Context ctx) {
        TieringService service = tiering();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Tiering service unavailable"));
            return;
        }
        ctx.json(service.getPolicy());
    }

    private void updatePolicy(Context ctx) {
        TieringService service = tiering();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Tiering service unavailable"));
            return;
        }
        try {
            TierPolicy policy = ctx.bodyAsClass(TierPolicy.class);
            ctx.json(service.updatePolicy(policy));
        } catch (Exception e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void listAgents(Context ctx) {
        TieringService service = tiering();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Tiering service unavailable"));
            return;
        }
        ctx.json(service.listAgentSnapshots());
    }

    private void getAgent(Context ctx) {
        TieringService service = tiering();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Tiering service unavailable"));
            return;
        }
        String id = ctx.pathParam("id");
        String modelId = ctx.queryParam("model");
        TierAgentSnapshot snapshot = service.getAgentSnapshot(id, modelId);
        if (snapshot == null) {
            ctx.status(404).json(Map.of("error", "Agent tier data not found: " + id));
            return;
        }
        ctx.json(snapshot);
    }

    private void getAgentCaps(Context ctx) {
        TieringService service = tiering();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Tiering service unavailable"));
            return;
        }
        String id = ctx.pathParam("id");
        String modelId = ctx.queryParam("model");
        TierAgentSnapshot snapshot = service.getAgentSnapshot(id, modelId);
        if (snapshot == null) {
            ctx.status(404).json(Map.of("error", "Agent tier data not found: " + id));
            return;
        }
        Map<String, Object> response = new HashMap<>();
        TierCaps caps = snapshot.getCaps();
        TierCaps effective = snapshot.getEffectiveCaps();
        response.put("agentId", snapshot.getAgentId());
        response.put("modelId", snapshot.getModelId());
        response.put("caps", caps);
        response.put("effectiveCaps", effective);
        response.put("currentTier", snapshot.getCurrentTier());
        ctx.json(response);
    }

    private void recordTaskResult(Context ctx) {
        TieringService service = tiering();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Tiering service unavailable"));
            return;
        }
        String id = ctx.pathParam("id");
        String modelId = ctx.queryParam("model");
        try {
            TierTaskResult result = ctx.bodyAsClass(TierTaskResult.class);
            TierEvaluation evaluation = service.recordTaskResult(id, modelId, result);
            ctx.json(evaluation);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }
}
