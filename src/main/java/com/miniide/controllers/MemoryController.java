package com.miniide.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.MemoryService;
import com.miniide.models.MemoryItem;
import com.miniide.models.MemoryVersion;
import com.miniide.models.R5Event;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for librarian memory APIs (auto-level, escalation, evidence).
 */
public class MemoryController implements Controller {

    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public MemoryController(MemoryService memoryService, ObjectMapper objectMapper) {
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.post("/api/memory", this::createMemoryItem);
        app.post("/api/memory/{id}/versions", this::createVersion);
        app.post("/api/memory/{id}/events", this::createEvent);
        app.get("/api/memory/{id}", this::getMemory);
        app.get("/api/memory/{id}/versions", this::getVersions);
        app.get("/api/memory/{id}/evidence", this::getEvidence);
        app.put("/api/memory/{id}/active/{versionId}", this::setActiveVersion);
    }

    private void createMemoryItem(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String agentId = json.has("agentId") ? json.get("agentId").asText(null) : null;
            String topicKey = json.has("topicKey") ? json.get("topicKey").asText(null) : null;
            Integer defaultLevel = json.has("defaultLevel") ? json.get("defaultLevel").asInt() : null;
            Integer pinnedMinLevel = json.has("pinnedMinLevel") ? json.get("pinnedMinLevel").asInt() : null;

            MemoryItem item = memoryService.createMemoryItem(agentId, topicKey, defaultLevel, pinnedMinLevel);
            logger.info("Memory item created: " + item.getId());
            ctx.status(201).json(item);
        } catch (Exception e) {
            logger.error("Error creating memory item: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void createVersion(Context ctx) {
        String memoryId = ctx.pathParam("id");
        if (!memoryService.memoryExists(memoryId)) {
            ctx.status(404).json(Map.of("error", "Memory item not found: " + memoryId));
            return;
        }

        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            if (!json.has("repLevel")) {
                ctx.status(400).json(Map.of("error", "repLevel is required"));
                return;
            }
            int repLevel = json.get("repLevel").asInt();
            String content = json.has("content") ? json.get("content").asText() : "";
            String derivationKind = json.has("derivationKind") ? json.get("derivationKind").asText(null) : null;
            String derivedFrom = json.has("derivedFromVersionId") ? json.get("derivedFromVersionId").asText(null) : null;

            MemoryVersion version = memoryService.addVersion(memoryId, repLevel, content, derivationKind, derivedFrom);
            logger.info("Memory version created: " + version.getId() + " for memory " + memoryId);
            ctx.status(201).json(version);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Error creating memory version: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void createEvent(Context ctx) {
        String memoryId = ctx.pathParam("id");
        if (!memoryService.memoryExists(memoryId)) {
            ctx.status(404).json(Map.of("error", "Memory item not found: " + memoryId));
            return;
        }

        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String text = json.has("text") ? json.get("text").asText() : null;
            if (text == null || text.isBlank()) {
                ctx.status(400).json(Map.of("error", "text is required"));
                return;
            }
            String author = json.has("author") ? json.get("author").asText(null) : null;
            String agent = json.has("agent") ? json.get("agent").asText(null) : null;

            Map<String, Object> meta = new HashMap<>();
            if (json.has("meta") && json.get("meta").isObject()) {
                meta = objectMapper.convertValue(json.get("meta"), new TypeReference<Map<String, Object>>() {});
            }

            R5Event event = memoryService.addEvent(memoryId, author, agent, text, meta);
            logger.info("Memory event added: " + event.getId() + " for memory " + memoryId);
            ctx.status(201).json(event);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Error creating memory event: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getMemory(Context ctx) {
        String memoryId = ctx.pathParam("id");
        String level = ctx.queryParam("level", "auto");

        MemoryService.MemoryResult result = "more".equalsIgnoreCase(level)
            ? memoryService.getMemoryAtNextLevel(memoryId)
            : memoryService.getMemoryAtAutoLevel(memoryId);

        if (result == null) {
            ctx.status(404).json(Map.of("error", "Memory item not found: " + memoryId));
            return;
        }

        if (result.getVersion() == null) {
            ctx.status(404).json(Map.of("error", "No versions available for memory: " + memoryId));
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("memoryId", memoryId);
        body.put("repLevel", result.getVersion().getRepLevel());
        body.put("content", result.getVersion().getContent());
        body.put("activeVersionId", result.getItem().getActiveVersionId());
        body.put("defaultLevel", result.getItem().getDefaultLevel());
        body.put("pinnedMinLevel", result.getItem().getPinnedMinLevel());
        body.put("escalated", result.isEscalated());

        ctx.json(body);
    }

    private void getVersions(Context ctx) {
        String memoryId = ctx.pathParam("id");
        if (!memoryService.memoryExists(memoryId)) {
            ctx.status(404).json(Map.of("error", "Memory item not found: " + memoryId));
            return;
        }

        List<MemoryVersion> versions = memoryService.getVersions(memoryId);
        ctx.json(versions);
    }

    private void getEvidence(Context ctx) {
        String memoryId = ctx.pathParam("id");
        if (!memoryService.memoryExists(memoryId)) {
            ctx.status(404).json(Map.of("error", "Memory item not found: " + memoryId));
            return;
        }

        String witness = ctx.queryParam("witness");
        R5Event evidence = memoryService.getEvidence(memoryId, witness);
        if (evidence == null) {
            ctx.status(404).json(Map.of("error", "Evidence not found for witness: " + witness));
            return;
        }
        ctx.json(evidence);
    }

    private void setActiveVersion(Context ctx) {
        String memoryId = ctx.pathParam("id");
        String versionId = ctx.pathParam("versionId");
        if (!memoryService.memoryExists(memoryId)) {
            ctx.status(404).json(Map.of("error", "Memory item not found: " + memoryId));
            return;
        }

        long lockMinutes = 0;
        try {
            lockMinutes = ctx.queryParam("lockMinutes") != null
                ? Long.parseLong(ctx.queryParam("lockMinutes"))
                : 0;
        } catch (NumberFormatException ignored) {
        }
        long lockMillis = lockMinutes > 0 ? lockMinutes * 60 * 1000L : 0;
        String reason = ctx.queryParam("reason");

        boolean ok = memoryService.setActiveVersion(memoryId, versionId, lockMillis, reason);
        if (!ok) {
            ctx.status(404).json(Map.of("error", "Version not found for memory: " + versionId));
            return;
        }

        MemoryService.MemoryResult state = memoryService.getMemoryAtAutoLevel(memoryId);
        Long lockUntil = state != null && state.getItem() != null ? state.getItem().getActiveLockUntil() : null;

        ctx.json(Map.of(
            "success", true,
            "memoryId", memoryId,
            "activeVersionId", versionId,
            "lockUntil", lockUntil,
            "reason", reason
        ));
    }
}
