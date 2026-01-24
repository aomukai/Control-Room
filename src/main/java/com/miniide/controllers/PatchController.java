package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.CreditStore;
import com.miniide.IssueMemoryService;
import com.miniide.PatchService.ApplyOutcome;
import com.miniide.ProjectContext;
import com.miniide.models.Comment;
import com.miniide.models.CreditEvent;
import com.miniide.models.PatchProposal;
import com.miniide.models.TextReplace;
import com.miniide.NotificationStore;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PatchController implements Controller {

    private final ProjectContext projectContext;
    private final IssueMemoryService issueService;
    private final NotificationStore notificationStore;
    private final CreditStore creditStore;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public PatchController(ProjectContext projectContext, IssueMemoryService issueService,
                           NotificationStore notificationStore, CreditStore creditStore,
                           ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.issueService = issueService;
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
        app.post("/api/patches/ai", this::createPatchFromAi);
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
            ensureIssueForPatch(proposal, resolveAgentId(proposal));
            PatchProposal created = projectContext.patches().create(proposal);
            sendPatchNotification(created);
            appendPatchIssueComment(created);
            ctx.status(201).json(created);
        } catch (Exception e) {
            logger.error("Failed to create patch: " + e.getMessage(), e);
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void createPatchFromAi(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String agentId = json.has("agentId") ? json.get("agentId").asText() : null;
            String title = json.has("title") ? json.get("title").asText() : null;
            String description = json.has("description") ? json.get("description").asText() : null;
            String filePath = json.has("filePath") ? json.get("filePath").asText() : null;
            String preview = json.has("preview") ? json.get("preview").asText() : null;
            String issueId = json.has("issueId") ? json.get("issueId").asText(null) : null;

            if (title == null || title.isBlank()) {
                ctx.status(400).json(Map.of("error", "title is required"));
                return;
            }
            if (description == null || description.isBlank()) {
                ctx.status(400).json(Map.of("error", "description is required"));
                return;
            }
            if (filePath == null || filePath.isBlank()) {
                ctx.status(400).json(Map.of("error", "filePath is required"));
                return;
            }

            List<JsonNode> editsNodes = new ArrayList<>();
            List<JsonNode> replacementNodes = new ArrayList<>();
            if (json.has("edits") && json.get("edits").isArray()) {
                json.get("edits").forEach(editsNodes::add);
            }
            if (json.has("replacements") && json.get("replacements").isArray()) {
                json.get("replacements").forEach(replacementNodes::add);
            }
            if (editsNodes.isEmpty() && replacementNodes.isEmpty()) {
                ctx.status(400).json(Map.of("error", "at least one edit or replacement is required"));
                return;
            }

            var edits = editsNodes.stream()
                .map(node -> objectMapper.convertValue(node, com.miniide.models.TextEdit.class))
                .collect(Collectors.toList());
            for (int i = 0; i < edits.size(); i++) {
                var edit = edits.get(i);
                if (edit.getStartLine() < 1) {
                    ctx.status(400).json(Map.of("error", "edit " + (i + 1) + ": startLine must be >= 1"));
                    return;
                }
                if (edit.getEndLine() < edit.getStartLine()) {
                    ctx.status(400).json(Map.of("error", "edit " + (i + 1) + ": endLine must be >= startLine"));
                    return;
                }
            }

            var replacements = replacementNodes.stream()
                .map(node -> objectMapper.convertValue(node, TextReplace.class))
                .collect(Collectors.toList());
            for (int i = 0; i < replacements.size(); i++) {
                var replacement = replacements.get(i);
                if (replacement.getBefore() == null || replacement.getBefore().isBlank()) {
                    ctx.status(400).json(Map.of("error", "replacement " + (i + 1) + ": before is required"));
                    return;
                }
                if (replacement.getOccurrence() != null && replacement.getOccurrence() < 1) {
                    ctx.status(400).json(Map.of("error", "replacement " + (i + 1) + ": occurrence must be >= 1"));
                    return;
                }
            }

            PatchProposal proposal = new PatchProposal();
            proposal.setTitle(title);
            proposal.setDescription(description);
            proposal.setFilePath(filePath);
            proposal.setPreview(preview);
            proposal.setEdits(edits);
            if (issueId != null && !issueId.isBlank()) {
                proposal.setIssueId(issueId);
            }

            com.miniide.models.PatchFileChange change = new com.miniide.models.PatchFileChange();
            change.setFilePath(filePath);
            change.setEdits(edits);
            change.setReplacements(replacements);
            if (json.has("baseHash") && json.get("baseHash").isTextual()) {
                change.setBaseHash(json.get("baseHash").asText());
            }
            change.setPreview(preview);
            proposal.setFiles(List.of(change));

            com.miniide.models.PatchProvenance provenance = new com.miniide.models.PatchProvenance();
            provenance.setSource("agentic-editing");
            provenance.setAgent(agentId);
            if (agentId != null && !agentId.isBlank()) {
                var agent = projectContext.agents().getAgent(agentId);
                if (agent != null && agent.getName() != null && !agent.getName().isBlank()) {
                    provenance.setAuthor(agent.getName());
                } else {
                    provenance.setAuthor(agentId);
                }
                var endpoint = projectContext.agentEndpoints().getEndpoint(agentId);
                if (endpoint == null && agent != null) {
                    endpoint = agent.getEndpoint();
                }
                if (endpoint != null && endpoint.getModel() != null && !endpoint.getModel().isBlank()) {
                    provenance.setModel(endpoint.getModel());
                }
            } else {
                provenance.setAuthor("agent");
            }
            proposal.setProvenance(provenance);

            ensureIssueForPatch(proposal, agentId);
            PatchProposal created = projectContext.patches().create(proposal);
            sendPatchNotification(created);
            appendPatchIssueComment(created);
            ctx.status(201).json(created);
        } catch (Exception e) {
            logger.error("Failed to create AI patch: " + e.getMessage(), e);
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

    private void ensureIssueForPatch(PatchProposal proposal, String agentId) {
        if (issueService == null || proposal == null) {
            return;
        }
        if (proposal.getIssueId() != null && !proposal.getIssueId().isBlank()) {
            return;
        }

        String title = proposal.getTitle();
        if (title == null || title.isBlank()) {
            title = proposal.getId() != null ? proposal.getId() : "Patch proposal";
        }
        String issueTitle = "Patch proposal: " + title;

        List<String> filePaths = proposal.getFiles() != null
            ? proposal.getFiles().stream()
                .map(file -> file != null ? file.getFilePath() : null)
                .filter(path -> path != null && !path.isBlank())
                .collect(Collectors.toList())
            : List.of();
        if (filePaths.isEmpty() && proposal.getFilePath() != null && !proposal.getFilePath().isBlank()) {
            filePaths = List.of(proposal.getFilePath());
        }

        StringBuilder body = new StringBuilder();
        body.append("Patch proposal created.\n\n");
        body.append("**Patch**: ").append(title).append("\n");
        if (!filePaths.isEmpty()) {
            body.append("**Files**:\n");
            for (String filePath : filePaths) {
                body.append("- ").append(filePath).append("\n");
            }
            body.append("\n");
        }
        if (proposal.getDescription() != null && !proposal.getDescription().isBlank()) {
            body.append("**Description**:\n").append(proposal.getDescription()).append("\n");
        }

        String openedBy = (agentId != null && !agentId.isBlank()) ? agentId : "system";
        String assignedTo = (agentId != null && !agentId.isBlank()) ? agentId : null;
        List<String> tags = List.of("patch-proposal");

        try {
            var issue = issueService.createIssue(issueTitle, body.toString(), openedBy, assignedTo, tags, "normal");
            proposal.setIssueId(String.valueOf(issue.getId()));
            if (notificationStore != null) {
                notificationStore.issueCreated(issue.getId(), issue.getTitle(), issue.getOpenedBy(), issue.getAssignedTo());
            }
        } catch (Exception e) {
            logger.warn("Failed to create issue for patch: " + e.getMessage());
        }
    }

    private String resolveAgentId(PatchProposal proposal) {
        if (proposal == null || proposal.getProvenance() == null) {
            return null;
        }
        String agentId = proposal.getProvenance().getAgent();
        return agentId != null && !agentId.isBlank() ? agentId : null;
    }

    private void appendPatchIssueComment(PatchProposal proposal) {
        if (issueService == null || proposal == null) {
            return;
        }
        String issueId = proposal.getIssueId();
        if (issueId == null || issueId.isBlank()) {
            return;
        }
        int id;
        try {
            id = Integer.parseInt(issueId);
        } catch (NumberFormatException e) {
            return;
        }

        String title = proposal.getTitle();
        String message = title != null && !title.isBlank()
            ? "Patch proposed: " + title
            : "Patch proposed: " + proposal.getId();
        String author = "system";
        if (proposal.getProvenance() != null && proposal.getProvenance().getAgent() != null
            && !proposal.getProvenance().getAgent().isBlank()) {
            author = proposal.getProvenance().getAgent();
        } else if (proposal.getProvenance() != null && proposal.getProvenance().getAuthor() != null
            && !proposal.getProvenance().getAuthor().isBlank()) {
            author = proposal.getProvenance().getAuthor();
        }
        Comment.CommentAction action = new Comment.CommentAction(
            "patch-proposed",
            "patchId: " + proposal.getId()
        );
        try {
            issueService.addComment(id, author, message, action, null, null);
        } catch (Exception e) {
            logger.warn("Failed to log patch proposal on Issue #" + issueId + ": " + e.getMessage());
        }
    }
}
