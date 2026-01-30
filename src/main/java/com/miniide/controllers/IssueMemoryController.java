package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.IssueInterestService;
import com.miniide.ProjectContext;
import com.miniide.models.IssueMemoryRecord;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.List;
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
        app.post("/api/issue-memory/agents/{agentId}/issues/{issueId}/tags", this::updatePersonalTags);
        app.post("/api/issue-memory/agents/{agentId}/activate", this::recordActivation);
        app.get("/api/issue-memory/agents/{agentId}/activation", this::getActivationCount);
        app.post("/api/issue-memory/decay", this::runDecay);
        app.post("/api/issue-memory/epoch", this::triggerEpoch);
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

    private void updatePersonalTags(Context ctx) {
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
        List<String> tags = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("note")) {
                    note = json.get("note").asText();
                }
                if (json.has("tags") && json.get("tags").isArray()) {
                    tags = new java.util.ArrayList<>();
                    for (JsonNode tagNode : json.get("tags")) {
                        if (tagNode != null && !tagNode.isNull()) {
                            tags.add(tagNode.asText());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            IssueMemoryRecord record = service.updatePersonalTags(agentId, issueId, tags, note);
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

    private void recordActivation(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String agentId = ctx.pathParam("agentId");
        int count = 1;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("count")) {
                    count = Math.max(1, json.get("count").asInt(1));
                }
            }
        } catch (Exception ignored) {
        }
        int activationCount = service.recordAgentActivations(agentId, count);
        ctx.json(Map.of("agentId", agentId, "activationCount", activationCount, "incrementedBy", count));
    }

    private void getActivationCount(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String agentId = ctx.pathParam("agentId");
        int activationCount = service.getAgentActivationCount(agentId);
        ctx.json(Map.of("agentId", agentId, "activationCount", activationCount));
    }

    private void triggerEpoch(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String epochType = null;
        List<String> tags = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("epochType")) {
                    epochType = json.get("epochType").asText(null);
                }
                if (json.has("tags") && json.get("tags").isArray()) {
                    tags = new java.util.ArrayList<>();
                    for (JsonNode tagNode : json.get("tags")) {
                        if (tagNode != null && !tagNode.isNull()) {
                            tags.add(tagNode.asText());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        int demoted = service.triggerEpoch(epochType, tags);
        ctx.json(Map.of("demoted", demoted));
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
