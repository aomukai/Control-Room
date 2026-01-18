package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppConfig;
import com.miniide.ProjectPreparationService;
import com.miniide.ProjectPreparationService.PrepareEmptyPayload;
import com.miniide.ProjectPreparationService.PrepareIngestPayload;
import com.miniide.ProjectPreparationService.PreparationResult;
import com.miniide.ProjectContext;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PreparationController implements Controller {
    private final ProjectContext projectContext;
    private final ObjectMapper objectMapper;

    public PreparationController(ProjectContext projectContext, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.objectMapper = objectMapper;
    }

    /**
     * Get the preparation service dynamically to support workspace switching.
     */
    private ProjectPreparationService preparationService() {
        return projectContext.preparation();
    }

    /**
     * Returns true only if a valid project is selected (not workspace root).
     */
    private boolean isValidProjectSelected() {
        try {
            Path current = projectContext.currentRoot();
            if (current == null) return false;
            Path root = AppConfig.getConfiguredWorkspaceRoot();
            // Must not be the workspace root itself
            if (current.normalize().equals(root.normalize())) {
                return false;
            }
            // Must be a direct child of workspace root
            if (current.getParent() == null || !current.getParent().normalize().equals(root.normalize())) {
                return false;
            }
            // Must have .control-room marker
            return Files.exists(current.resolve(".control-room"));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/preparation/status", this::getStatus);
        app.get("/api/preparation/debug", this::getDebugSnapshot);
        app.get("/api/preparation/canon-review", this::getCanonReview);
        app.post("/api/preparation/canon/confirm", this::confirmCanonReview);
        app.post("/api/preparation/finalize", this::finalizePreparation);
        app.post("/api/preparation/empty", this::prepareEmpty);
        app.post("/api/preparation/ingest", this::prepareIngest);
    }

    private void getStatus(Context ctx) {
        try {
            var meta = preparationService().getMetadata();
            var canon = preparationService().loadCanonManifest();
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("prepared", meta != null && meta.isPrepared());
            response.put("prepStage", meta != null ? meta.getPrepStage() : null);
            response.put("agentsUnlocked", meta != null && meta.isAgentsUnlocked());
            response.put("preparedMode", meta != null ? meta.getPreparedMode() : null);
            response.put("preparedAt", meta != null ? meta.getPreparedAt() : null);
            response.put("canonStatus", canon != null ? canon.getStatus() : null);
            response.put("canonReviewedAt", canon != null ? canon.getReviewedAt() : null);
            ctx.json(response);
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getCanonReview(Context ctx) {
        try {
            ctx.json(preparationService().getCanonReviewSummary());
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getDebugSnapshot(Context ctx) {
        try {
            ctx.json(preparationService().getDebugSnapshot());
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void confirmCanonReview(Context ctx) {
        try {
            preparationService().confirmCanonReview();
            ctx.json(Map.of("ok", true));
        } catch (IllegalStateException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void finalizePreparation(Context ctx) {
        try {
            preparationService().finalizePreparation();
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void prepareEmpty(Context ctx) {
        try {
            if (!isValidProjectSelected()) {
                ctx.status(400).json(Map.of("error", "No project selected. Create a project first."));
                return;
            }
            JsonNode json = objectMapper.readTree(ctx.body());
            PrepareEmptyPayload payload = new PrepareEmptyPayload();
            payload.premise = json.has("premise") ? json.get("premise").asText() : null;
            payload.genre = json.has("genre") ? json.get("genre").asText() : null;
            payload.storyIdea = json.has("storyIdea") ? json.get("storyIdea").asText() : null;
            payload.protagonistName = json.has("protagonistName") ? json.get("protagonistName").asText() : null;
            payload.protagonistRole = json.has("protagonistRole") ? json.get("protagonistRole").asText() : null;
            payload.themes = json.has("themes") ? json.get("themes").asText() : null;

            PreparationResult result = preparationService().prepareEmpty(payload);
            if (result.alreadyPrepared) {
                ctx.status(409).json(Map.of("error", "Project already prepared"));
                return;
            }
            ctx.json(result);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void prepareIngest(Context ctx) {
        try {
            if (!isValidProjectSelected()) {
                ctx.status(400).json(Map.of("error", "No project selected. Create a project first."));
                return;
            }
            PrepareIngestPayload payload = new PrepareIngestPayload();
            List<UploadedFile> manuscripts = ctx.uploadedFiles("manuscripts");
            List<UploadedFile> canon = ctx.uploadedFiles("canon");
            if (manuscripts != null) {
                payload.manuscripts.addAll(manuscripts);
            }
            if (canon != null) {
                payload.canonFiles.addAll(canon);
            }
            PreparationResult result = preparationService().prepareIngest(payload);
            if (result.alreadyPrepared) {
                ctx.status(409).json(Map.of("error", "Project already prepared"));
                return;
            }
            ctx.json(result);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }
}
