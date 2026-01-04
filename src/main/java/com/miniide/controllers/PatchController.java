package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.PatchService;
import com.miniide.WorkspaceService;
import com.miniide.models.PatchProposal;
import com.miniide.NotificationStore;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatchController implements Controller {

    private final PatchService patchService;
    private final WorkspaceService workspaceService;
    private final NotificationStore notificationStore;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public PatchController(PatchService patchService, WorkspaceService workspaceService, NotificationStore notificationStore, ObjectMapper objectMapper) {
        this.patchService = patchService;
        this.workspaceService = workspaceService;
        this.notificationStore = notificationStore;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/patches", this::listPatches);
        app.get("/api/patches/{id}", this::getPatch);
        app.post("/api/patches", this::createPatch);
        app.post("/api/patches/{id}/apply", this::applyPatch);
        app.post("/api/patches/{id}/reject", this::rejectPatch);
        app.post("/api/patches/simulate", this::simulatePatch);
    }

    private void listPatches(Context ctx) {
        List<PatchProposal> list = patchService.list();
        ctx.json(Map.of("patches", list));
    }

    private void getPatch(Context ctx) {
        String id = ctx.pathParam("id");
        PatchProposal p = patchService.get(id);
        if (p == null) {
            ctx.status(404).json(Map.of("error", "Patch not found: " + id));
            return;
        }
        ctx.json(p);
    }

    private void createPatch(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            PatchProposal proposal = objectMapper.convertValue(json, PatchProposal.class);
            PatchProposal created = patchService.create(proposal);
            sendPatchNotification(created);
            ctx.status(201).json(created);
        } catch (Exception e) {
            logger.error("Failed to create patch: " + e.getMessage(), e);
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void applyPatch(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            PatchProposal updated = patchService.apply(id);
            ctx.json(Map.of("ok", true, "patch", updated));
        } catch (Exception e) {
            logger.error("Failed to apply patch: " + e.getMessage(), e);
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void rejectPatch(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            PatchProposal updated = patchService.reject(id);
            ctx.json(Map.of("ok", true, "patch", updated));
        } catch (Exception e) {
            logger.error("Failed to reject patch: " + e.getMessage(), e);
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void simulatePatch(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String filePath = json.has("filePath") ? json.get("filePath").asText() : "README.md";
            PatchProposal proposal = patchService.simulatePatch(filePath);
            sendPatchNotification(proposal);
            ctx.status(201).json(proposal);
        } catch (Exception e) {
            logger.error("Failed to simulate patch: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void sendPatchNotification(PatchProposal proposal) {
        if (notificationStore == null || proposal == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("kind", "review-patch");
        payload.put("patchId", proposal.getId());
        notificationStore.push(
            com.miniide.models.Notification.Level.INFO,
            com.miniide.models.Notification.Scope.EDITOR,
            "Patch proposed for " + proposal.getFilePath(),
            proposal.getDescription() != null ? proposal.getDescription() : "",
            com.miniide.models.Notification.Category.ATTENTION,
            true,
            "Review Patch",
            payload,
            "patch"
        );
    }
}
