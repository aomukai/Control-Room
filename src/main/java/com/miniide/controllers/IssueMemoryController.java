package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.IssueInterestService;
import com.miniide.ProjectContext;
import com.miniide.models.IssueMemoryRecord;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

public class IssueMemoryController implements Controller {
    private final ProjectContext projectContext;
    private final ObjectMapper objectMapper;

    public IssueMemoryController(ProjectContext projectContext, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/issue-memory/agents/{agentId}", this::listAgentMemories);
        app.get("/api/issue-memory/agents/{agentId}/issues/{issueId}", this::getMemory);
        app.post("/api/issue-memory/agents/{agentId}/issues/{issueId}/access", this::recordAccess);
        app.post("/api/issue-memory/agents/{agentId}/issues/{issueId}/applied", this::recordApplied);
        app.post("/api/issue-memory/agents/{agentId}/issues/{issueId}/irrelevant", this::markIrrelevant);
        app.post("/api/issue-memory/decay", this::runDecay);
    }

    private IssueInterestService service() {
        return projectContext != null ? projectContext.issueInterest() : null;
    }

    private void listAgentMemories(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String agentId = ctx.pathParam("agentId");
        ctx.json(service.listForAgent(agentId));
    }

    private void getMemory(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String agentId = ctx.pathParam("agentId");
        int issueId = parseIssueId(ctx);
        if (issueId <= 0) {
            return;
        }
        IssueMemoryRecord record = service.getRecord(agentId, issueId);
        if (record == null) {
            ctx.status(404).json(Map.of("error", "Issue memory not found"));
            return;
        }
        ctx.json(record);
    }

    private void recordAccess(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String agentId = ctx.pathParam("agentId");
        int issueId = parseIssueId(ctx);
        if (issueId <= 0) {
            return;
        }
        try {
            IssueMemoryRecord record = service.recordAccess(agentId, issueId);
            ctx.json(record);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void recordApplied(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String agentId = ctx.pathParam("agentId");
        int issueId = parseIssueId(ctx);
        if (issueId <= 0) {
            return;
        }
        try {
            IssueMemoryRecord record = service.recordApplied(agentId, issueId);
            ctx.json(record);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void markIrrelevant(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String agentId = ctx.pathParam("agentId");
        int issueId = parseIssueId(ctx);
        if (issueId <= 0) {
            return;
        }
        String note = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("note")) {
                    note = json.get("note").asText();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            IssueMemoryRecord record = service.markIrrelevant(agentId, issueId, note);
            ctx.json(record);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void runDecay(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String agentId = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("agentId")) {
                    agentId = json.get("agentId").asText(null);
                }
            }
        } catch (Exception ignored) {
        }
        int decayed = agentId != null && !agentId.isBlank()
            ? service.decayAgent(agentId)
            : service.decayAll();
        ctx.json(Map.of("decayed", decayed));
    }

    private int parseIssueId(Context ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("issueId"));
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
            return -1;
        }
    }
}
