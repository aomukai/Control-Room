package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.CreditStore;
import com.miniide.PatchService.ApplyOutcome;
import com.miniide.ProjectContext;
import com.miniide.models.CreditEvent;
import com.miniide.models.PatchProposal;
import com.miniide.NotificationStore;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PatchController implements Controller {

    private final ProjectContext projectContext;
    private final NotificationStore notificationStore;
    private final CreditStore creditStore;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public PatchController(ProjectContext projectContext, NotificationStore notificationStore,
                           CreditStore creditStore, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.notificationStore = notificationStore;
        this.creditStore = creditStore;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/patches", this::listPatches);
        app.get("/api/patches/{id}", this::getPatch);
        app.get("/api/patches/{id}/audit", this::exportPatchAudit);
        app.get("/api/patches/audit/export", this::exportAllPatchAudits);
        app.post("/api/patches", this::createPatch);
        app.post("/api/patches/{id}/apply", this::applyPatch);
        app.post("/api/patches/{id}/reject", this::rejectPatch);
        app.delete("/api/patches/{id}", this::deletePatch);
        app.post("/api/patches/cleanup", this::cleanupPatches);
        app.post("/api/patches/simulate", this::simulatePatch);
    }

    private void listPatches(Context ctx) {
        List<PatchProposal> list = projectContext.patches().list();
        ctx.json(Map.of("patches", list));
    }

    private void getPatch(Context ctx) {
        String id = ctx.pathParam("id");
        PatchProposal p = projectContext.patches().get(id);
        if (p == null) {
            ctx.status(404).json(Map.of("error", "Patch not found: " + id));
            return;
        }
        ctx.json(p);
    }

    private void exportPatchAudit(Context ctx) {
        String id = ctx.pathParam("id");
        PatchProposal patch = projectContext.patches().get(id);
        if (patch == null) {
            ctx.status(404).json(Map.of("error", "Patch not found: " + id));
            return;
        }
        ctx.json(buildPatchAuditExport(patch));
    }

    private void exportAllPatchAudits(Context ctx) {
        List<PatchProposal> patches = projectContext.patches().list();
        List<Map<String, Object>> exports = patches.stream()
            .map(this::buildPatchAuditExport)
            .collect(Collectors.toList());
        Map<String, Object> payload = new HashMap<>();
        payload.put("exportedAt", System.currentTimeMillis());
        payload.put("count", exports.size());
        payload.put("patches", exports);
        ctx.json(payload);
    }

    private void createPatch(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            PatchProposal proposal = objectMapper.convertValue(json, PatchProposal.class);
            PatchProposal created = projectContext.patches().create(proposal);
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
            ApplyOutcome outcome = projectContext.patches().apply(id);
            if (!outcome.isSuccess()) {
                ctx.status(400).json(Map.of(
                    "error", outcome.getErrorMessage(),
                    "fileErrors", outcome.getFileResults()
                ));
                return;
            }
            awardPatchApplyCredit(outcome.getProposal());
            ctx.json(Map.of("ok", true, "patch", outcome.getProposal(), "fileResults", outcome.getFileResults()));
        } catch (Exception e) {
            logger.error("Failed to apply patch: " + e.getMessage(), e);
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void rejectPatch(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            PatchProposal updated = projectContext.patches().reject(id);
            ctx.json(Map.of("ok", true, "patch", updated));
        } catch (Exception e) {
            logger.error("Failed to reject patch: " + e.getMessage(), e);
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void deletePatch(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            boolean removed = projectContext.patches().delete(id);
            if (!removed) {
                ctx.status(404).json(Map.of("error", "Patch not found: " + id));
                return;
            }
            ctx.json(Map.of("ok", true, "deleted", id));
        } catch (Exception e) {
            logger.error("Failed to delete patch: " + e.getMessage(), e);
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void cleanupPatches(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            Set<String> statuses = null;
            if (json.has("statuses") && json.get("statuses").isArray()) {
                statuses = objectMapper.convertValue(json.get("statuses"), Set.class);
            }
            int removed = projectContext.patches().cleanup(statuses);
            ctx.json(Map.of("ok", true, "removed", removed));
        } catch (Exception e) {
            logger.error("Failed to clean up patches: " + e.getMessage(), e);
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void simulatePatch(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String filePath = json.has("filePath") ? json.get("filePath").asText() : "README.md";
            PatchProposal proposal = projectContext.patches().simulatePatch(filePath);
            sendPatchNotification(proposal);
            ctx.status(201).json(proposal);
        } catch (Exception e) {
            logger.error("Failed to simulate patch: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void sendPatchNotification(PatchProposal proposal) {
        if (notificationStore == null || proposal == null) return;
        String fileLabel = proposal.getFiles() != null && !proposal.getFiles().isEmpty()
            ? proposal.getFiles().get(0).getFilePath()
            : proposal.getFilePath();
        String projectName = null;
        try {
            projectName = projectContext.workspace().loadMetadata().getDisplayName();
        } catch (Exception ignored) {
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("kind", "review-patch");
        payload.put("patchId", proposal.getId());
        if (proposal.getIssueId() != null && !proposal.getIssueId().isBlank()) {
            payload.put("issueId", proposal.getIssueId());
        }
        payload.put("filePath", fileLabel);
        payload.put("filePaths", proposal.getFiles() != null
            ? proposal.getFiles().stream().map(f -> f.getFilePath()).collect(Collectors.toList())
            : List.of());
        payload.put("patchTitle", proposal.getTitle());
        if (proposal.getProvenance() != null) {
            payload.put("provenance", proposal.getProvenance());
        }
        if (projectName != null && !projectName.isBlank()) {
            payload.put("projectName", projectName);
        }
        notificationStore.push(
            com.miniide.models.Notification.Level.INFO,
            com.miniide.models.Notification.Scope.EDITOR,
            "Patch proposed for " + fileLabel,
            proposal.getDescription() != null ? proposal.getDescription() : "",
            com.miniide.models.Notification.Category.ATTENTION,
            true,
            "Review Patch",
            payload,
            "patch"
        );
    }

    private void awardPatchApplyCredit(PatchProposal proposal) {
        if (creditStore == null || proposal == null || proposal.getProvenance() == null) {
            return;
        }
        String agentId = proposal.getProvenance().getAgent();
        if (agentId == null || agentId.isBlank()) {
            return;
        }

        CreditEvent event = new CreditEvent();
        event.setAgentId(agentId);
        event.setAmount(3);
        event.setReason("proposal-accepted-by-user");
        event.setVerifiedBy("user");
        event.setTimestamp(System.currentTimeMillis());
        CreditEvent.RelatedEntity related = new CreditEvent.RelatedEntity();
        related.setType("patch");
        related.setId(proposal.getId());
        event.setRelatedEntity(related);

        try {
            creditStore.award(event);
        } catch (Exception e) {
            logger.warn("Failed to award patch credit: " + e.getMessage());
        }
    }

    private Map<String, Object> buildPatchAuditExport(PatchProposal patch) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("patchId", patch.getId());
        payload.put("title", patch.getTitle());
        payload.put("status", patch.getStatus());
        payload.put("description", patch.getDescription());
        payload.put("issueId", patch.getIssueId());
        payload.put("createdAt", patch.getCreatedAt());
        payload.put("provenance", patch.getProvenance());
        payload.put("auditLog", patch.getAuditLog());
        payload.put("files", patch.getFiles());
        payload.put("exportedAt", System.currentTimeMillis());
        return payload;
    }
}
