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
    private final MemoryDecayScheduler decayScheduler;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public MemoryController(MemoryService memoryService, MemoryDecayScheduler decayScheduler, ObjectMapper objectMapper) {
        this.memoryService = memoryService;
        this.decayScheduler = decayScheduler;
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
        app.put("/api/memory/{id}/pin", this::pinMemory);
        app.put("/api/memory/{id}/state", this::setState);
        app.post("/api/memory/decay", this::runDecay);
        app.get("/api/memory/decay/status", this::getDecayStatus);
        app.put("/api/memory/decay/config", this::updateDecayConfig);
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

    private void pinMemory(Context ctx) {
        String memoryId = ctx.pathParam("id");
        if (!memoryService.memoryExists(memoryId)) {
            ctx.status(404).json(Map.of("error", "Memory item not found: " + memoryId));
            return;
        }
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            Integer pinned = json.has("pinnedMinLevel") ? json.get("pinnedMinLevel").asInt() : null;
            boolean ok = memoryService.setPinnedMinLevel(memoryId, pinned);
            if (!ok) {
                ctx.status(404).json(Map.of("error", "Failed to pin memory: " + memoryId));
                return;
            }
            ctx.json(Map.of("success", true, "memoryId", memoryId, "pinnedMinLevel", pinned));
        } catch (Exception e) {
            logger.error("Error pinning memory: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void setState(Context ctx) {
        String memoryId = ctx.pathParam("id");
        if (!memoryService.memoryExists(memoryId)) {
            ctx.status(404).json(Map.of("error", "Memory item not found: " + memoryId));
            return;
        }
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String state = json.has("state") ? json.get("state").asText(null) : null;
            if (state == null || state.isBlank()) {
                ctx.status(400).json(Map.of("error", "state is required"));
                return;
            }
            boolean ok = memoryService.setState(memoryId, state);
            if (!ok) {
                ctx.status(404).json(Map.of("error", "Failed to update state for memory: " + memoryId));
                return;
            }
            ctx.json(Map.of("success", true, "memoryId", memoryId, "state", state));
        } catch (Exception e) {
            logger.error("Error setting memory state: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void runDecay(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            long archiveDays = json.has("archiveAfterDays") ? json.get("archiveAfterDays").asLong(14) : 14;
            long expireDays = json.has("expireAfterDays") ? json.get("expireAfterDays").asLong(30) : 30;
            boolean pruneExpiredR5 = json.has("pruneExpiredR5") && json.get("pruneExpiredR5").asBoolean();
            boolean dryRun = json.has("dryRun") && json.get("dryRun").asBoolean();
            boolean collectReport = json.has("collectReport") && json.get("collectReport").asBoolean();

            MemoryService.DecaySettings settings = new MemoryService.DecaySettings();
            settings.setArchiveAfterMs(archiveDays * 24 * 60 * 60 * 1000L);
            settings.setExpireAfterMs(expireDays * 24 * 60 * 60 * 1000L);
            settings.setPruneExpiredR5(pruneExpiredR5);
            settings.setCollectReport(collectReport);

            MemoryService.DecayResult result = memoryService.runDecay(settings, dryRun);

            ctx.json(Map.of(
                "archived", result.getArchivedIds(),
                "expired", result.getExpiredIds(),
                "prunable", result.getPrunableIds(),
                "prunedEvents", result.getPrunedEvents(),
                "lockedItems", result.getLockedItems(),
                "lockedIds", result.getLockedIds(),
                "items", result.getItems(),
                "dryRun", dryRun,
                "archiveAfterDays", archiveDays,
                "expireAfterDays", expireDays,
                "pruneExpiredR5", pruneExpiredR5
            ));
        } catch (Exception e) {
            logger.error("Error running decay: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getDecayStatus(Context ctx) {
        if (decayScheduler == null) {
            ctx.status(404).json(Map.of("error", "Decay scheduler not configured"));
            return;
        }
        MemoryDecayScheduler.DecayStatus status = decayScheduler.getStatus();
        Map<String, Object> body = new HashMap<>();
        body.put("intervalMs", status.intervalMs);
        body.put("archiveAfterMs", status.settings != null ? status.settings.getArchiveAfterMs() : null);
        body.put("expireAfterMs", status.settings != null ? status.settings.getExpireAfterMs() : null);
        body.put("pruneExpiredR5", status.settings != null && status.settings.isPruneExpiredR5());
        body.put("lastRunAt", status.lastRunAt);
        if (status.lastResult != null) {
            body.put("archived", status.lastResult.getArchivedIds());
            body.put("expired", status.lastResult.getExpiredIds());
            body.put("prunedEvents", status.lastResult.getPrunedEvents());
            body.put("lockedItems", status.lastResult.getLockedItems());
        }
        ctx.json(body);
    }

    private void updateDecayConfig(Context ctx) {
        if (decayScheduler == null) {
            ctx.status(404).json(Map.of("error", "Decay scheduler not configured"));
            return;
        }
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            long intervalMinutes = json.has("intervalMinutes") ? json.get("intervalMinutes").asLong(360) : 360;
            long archiveDays = json.has("archiveAfterDays") ? json.get("archiveAfterDays").asLong(14) : 14;
            long expireDays = json.has("expireAfterDays") ? json.get("expireAfterDays").asLong(30) : 30;
            boolean pruneExpiredR5 = json.has("pruneExpiredR5") && json.get("pruneExpiredR5").asBoolean();

            MemoryService.DecaySettings settings = new MemoryService.DecaySettings();
            settings.setArchiveAfterMs(archiveDays * 24 * 60 * 60 * 1000L);
            settings.setExpireAfterMs(expireDays * 24 * 60 * 60 * 1000L);
            settings.setPruneExpiredR5(pruneExpiredR5);

            decayScheduler.updateConfig(intervalMinutes * 60_000L, settings);

            ctx.json(Map.of(
                "success", true,
                "intervalMinutes", intervalMinutes,
                "archiveAfterDays", archiveDays,
                "expireAfterDays", expireDays,
                "pruneExpiredR5", pruneExpiredR5
            ));
        } catch (Exception e) {
            logger.error("Error updating decay config: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }
}
