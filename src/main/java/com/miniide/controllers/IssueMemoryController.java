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
        app.post("/api/issue-memory/agents/{agentId}/issues/{issueId}/contradiction", this::recordContradiction);
        app.post("/api/issue-memory/agents/{agentId}/issues/{issueId}/leech/confirm", this::confirmLeech);
        app.post("/api/issue-memory/agents/{agentId}/issues/{issueId}/leech/dismiss", this::dismissLeech);
        app.post("/api/issue-memory/agents/{agentId}/issues/{issueId}/defer", this::deferAccess);
        app.post("/api/issue-memory/agents/{agentId}/issues/{issueId}/approve", this::approveAccess);
        app.post("/api/issue-memory/agents/{agentId}/activate", this::recordActivation);
        app.get("/api/issue-memory/agents/{agentId}/activation", this::getActivationCount);
        app.post("/api/issue-memory/decay", this::runDecay);
        app.post("/api/issue-memory/epoch", this::triggerEpoch);
        app.get("/api/issue-memory/leeches", this::listLeeches);
        app.post("/api/issue-memory/wiedervorlage/trigger", this::triggerDeferral);
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

    private void recordContradiction(Context ctx) {
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
        int contradictionIssueId = 0;
        String note = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("contradictionIssueId")) {
                    contradictionIssueId = json.get("contradictionIssueId").asInt(0);
                }
                if (json.has("note")) {
                    note = json.get("note").asText();
                }
            }
        } catch (Exception ignored) {
        }
        if (contradictionIssueId <= 0) {
            ctx.status(400).json(Map.of("error", "contradictionIssueId is required"));
            return;
        }
        try {
            IssueMemoryRecord record = service.recordContradictionSignal(agentId, issueId, contradictionIssueId, note);
            ctx.json(record);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void confirmLeech(Context ctx) {
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
        String confirmedBy = null;
        String note = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("confirmedBy")) {
                    confirmedBy = json.get("confirmedBy").asText();
                }
                if (json.has("note")) {
                    note = json.get("note").asText();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            IssueMemoryRecord record = service.confirmLeech(agentId, issueId, confirmedBy, note);
            ctx.json(record);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void dismissLeech(Context ctx) {
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
            IssueMemoryRecord record = service.dismissLeech(agentId, issueId, note);
            ctx.json(record);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void deferAccess(Context ctx) {
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
        String triggerType = null;
        String triggerValue = null;
        Integer escalateTo = null;
        Boolean notify = null;
        String message = null;
        String reason = null;
        String deferredBy = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("trigger") && json.get("trigger").isObject()) {
                    JsonNode trigger = json.get("trigger");
                    if (trigger.has("type")) {
                        triggerType = trigger.get("type").asText(null);
                    }
                    if (trigger.has("value")) {
                        triggerValue = trigger.get("value").asText(null);
                    }
                }
                if (json.has("escalateTo")) {
                    escalateTo = json.get("escalateTo").asInt(0);
                }
                if (json.has("notify")) {
                    notify = json.get("notify").asBoolean(false);
                }
                if (json.has("message")) {
                    message = json.get("message").asText(null);
                }
                if (json.has("reason")) {
                    reason = json.get("reason").asText(null);
                }
                if (json.has("deferredBy")) {
                    deferredBy = json.get("deferredBy").asText(null);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            IssueMemoryRecord record = service.deferAccess(agentId, issueId, triggerType, triggerValue,
                escalateTo, notify, message, reason, deferredBy);
            ctx.json(record);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void approveAccess(Context ctx) {
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
        int level = 3;
        String approvedBy = null;
        String note = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("level")) {
                    level = json.get("level").asInt(3);
                }
                if (json.has("approvedBy")) {
                    approvedBy = json.get("approvedBy").asText(null);
                }
                if (json.has("note")) {
                    note = json.get("note").asText(null);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            IssueMemoryRecord record = service.approveAccess(agentId, issueId, level, approvedBy, note);
            ctx.json(record);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void listLeeches(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String agentId = ctx.queryParam("agentId");
        ctx.json(service.listLeechCandidates(agentId));
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
        List<String> epochTypes = null;
        List<String> tags = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("epochType")) {
                    epochType = json.get("epochType").asText(null);
                }
                if (json.has("epochTypes") && json.get("epochTypes").isArray()) {
                    epochTypes = new java.util.ArrayList<>();
                    for (JsonNode typeNode : json.get("epochTypes")) {
                        if (typeNode != null && !typeNode.isNull()) {
                            epochTypes.add(typeNode.asText());
                        }
                    }
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
        if (epochTypes == null || epochTypes.isEmpty()) {
            epochTypes = epochType != null ? List.of(epochType) : List.of();
        }
        int demoted = service.triggerEpoch(epochTypes, tags);
        ctx.json(Map.of("demoted", demoted));
    }

    private void triggerDeferral(Context ctx) {
        IssueInterestService service = service();
        if (service == null) {
            ctx.status(500).json(Map.of("error", "Issue memory service unavailable"));
            return;
        }
        String triggerType = null;
        String triggerValue = null;
        String agentId = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("trigger") && json.get("trigger").isObject()) {
                    JsonNode trigger = json.get("trigger");
                    if (trigger.has("type")) {
                        triggerType = trigger.get("type").asText(null);
                    }
                    if (trigger.has("value")) {
                        triggerValue = trigger.get("value").asText(null);
                    }
                } else {
                    if (json.has("type")) {
                        triggerType = json.get("type").asText(null);
                    }
                    if (json.has("value")) {
                        triggerValue = json.get("value").asText(null);
                    }
                }
                if (json.has("agentId")) {
                    agentId = json.get("agentId").asText(null);
                }
            }
        } catch (Exception ignored) {
        }
        int updated = service.triggerDeferrals(triggerType, triggerValue, agentId);
        ctx.json(Map.of("updated", updated));
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
