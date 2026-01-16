package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.ProjectPreparationService;
import com.miniide.ProjectPreparationService.PrepareEmptyPayload;
import com.miniide.ProjectPreparationService.PrepareIngestPayload;
import com.miniide.ProjectPreparationService.PreparationResult;
import com.miniide.ProjectContext;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.util.List;
import java.util.Map;

public class PreparationController implements Controller {
    private final ProjectPreparationService preparationService;
    private final ObjectMapper objectMapper;

    public PreparationController(ProjectContext projectContext, ObjectMapper objectMapper) {
        this.preparationService = projectContext.preparation();
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/preparation/status", this::getStatus);
        app.post("/api/preparation/empty", this::prepareEmpty);
        app.post("/api/preparation/ingest", this::prepareIngest);
    }

    private void getStatus(Context ctx) {
        var meta = preparationService.getMetadata();
        ctx.json(Map.of(
            "prepared", meta != null && meta.isPrepared(),
            "preparedMode", meta != null ? meta.getPreparedMode() : null,
            "preparedAt", meta != null ? meta.getPreparedAt() : null
        ));
    }

    private void prepareEmpty(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            PrepareEmptyPayload payload = new PrepareEmptyPayload();
            payload.premise = json.has("premise") ? json.get("premise").asText() : null;
            payload.genre = json.has("genre") ? json.get("genre").asText() : null;
            payload.protagonistName = json.has("protagonistName") ? json.get("protagonistName").asText() : null;
            payload.protagonistRole = json.has("protagonistRole") ? json.get("protagonistRole").asText() : null;
            payload.themes = json.has("themes") ? json.get("themes").asText() : null;

            PreparationResult result = preparationService.prepareEmpty(payload);
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
            PrepareIngestPayload payload = new PrepareIngestPayload();
            List<UploadedFile> manuscripts = ctx.uploadedFiles("manuscripts");
            List<UploadedFile> canon = ctx.uploadedFiles("canon");
            if (manuscripts != null) {
                payload.manuscripts.addAll(manuscripts);
            }
            if (canon != null) {
                payload.canonFiles.addAll(canon);
            }
            PreparationResult result = preparationService.prepareIngest(payload);
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
