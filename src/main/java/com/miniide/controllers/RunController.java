package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.AppLogger;
import com.miniide.pipeline.RunStore;
import com.miniide.pipeline.StepRunner;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST controller for pipeline runs.
 *
 * Endpoints:
 *   POST   /api/runs              Start a new run
 *   GET    /api/runs              List runs
 *   GET    /api/runs/{id}         Consolidated polling (manifest + steps + cache summary)
 *   GET    /api/runs/{id}/steps   Full step log
 *   GET    /api/runs/{id}/cache/{slot}  Read a cache slot value
 *   POST   /api/runs/{id}/cancel  Cancel a running run
 */
public class RunController implements Controller {

    private final StepRunner stepRunner;
    private final RunStore runStore;
    private final ObjectMapper objectMapper;
    private final AppLogger logger = AppLogger.get();

    public RunController(StepRunner stepRunner, RunStore runStore, ObjectMapper objectMapper) {
        this.stepRunner = stepRunner;
        this.runStore = runStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.post("/api/runs", this::startRun);
        app.get("/api/runs", this::listRuns);
        app.get("/api/runs/{id}", this::getRun);
        app.get("/api/runs/{id}/steps", this::getSteps);
        app.get("/api/runs/{id}/cache/{slot}", this::getCacheSlot);
        app.post("/api/runs/{id}/cancel", this::cancelRun);
    }

    /**
     * POST /api/runs
     * Body: { "recipe_id": "...", "args": { ... }, "description": "..." }
     */
    private void startRun(Context ctx) {
        try {
            JsonNode body = objectMapper.readTree(ctx.body());
            String recipeId = body.path("recipe_id").asText(null);
            if (recipeId == null || recipeId.isBlank()) {
                ctx.status(400).json(Map.of("error", "recipe_id is required"));
                return;
            }

            String description = body.path("description").asText("");
            Map<String, Object> args = null;
            JsonNode argsNode = body.path("args");
            if (argsNode.isObject()) {
                args = objectMapper.convertValue(argsNode, Map.class);
            }

            String runId = stepRunner.startRun(recipeId, args, description);
            ctx.status(201).json(Map.of("run_id", runId, "status", "running"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.warn("Failed to start run: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * GET /api/runs?status=...&recipe_id=...
     */
    private void listRuns(Context ctx) {
        try {
            String status = ctx.queryParam("status");
            String recipeId = ctx.queryParam("recipe_id");
            List<JsonNode> runs = runStore.listRuns(status, recipeId);

            ArrayNode result = objectMapper.createArrayNode();
            for (JsonNode run : runs) {
                ObjectNode summary = objectMapper.createObjectNode();
                summary.put("run_id", run.path("run_id").asText());
                summary.put("recipe_id", run.path("recipe_id").asText());
                summary.put("status", run.path("status").asText());
                summary.put("created_at", run.path("created_at").asText());
                summary.set("task", run.path("task"));
                result.add(summary);
            }
            ctx.json(result);
        } catch (Exception e) {
            logger.warn("Failed to list runs: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * GET /api/runs/{id}
     * Consolidated polling endpoint: manifest + steps + cache summary in one call.
     */
    private void getRun(Context ctx) {
        try {
            String runId = ctx.pathParam("id");
            JsonNode manifest = runStore.readManifest(runId);
            if (manifest == null) {
                ctx.status(404).json(Map.of("error", "Run not found: " + runId));
                return;
            }

            // Build consolidated response
            ObjectNode response = objectMapper.createObjectNode();
            response.put("run_id", manifest.path("run_id").asText());
            response.put("recipe_id", manifest.path("recipe_id").asText());
            response.put("status", manifest.path("status").asText());
            response.put("phase", manifest.path("phase").asText(null));
            response.put("current_step_index", manifest.path("current_step_index").asInt(0));
            response.put("total_steps", manifest.path("total_steps").asInt(0));
            response.put("created_at", manifest.path("created_at").asText());
            response.put("updated_at", manifest.path("updated_at").asText());
            JsonNode completedAt = manifest.path("completed_at");
            if (completedAt.isNull() || completedAt.isMissingNode()) {
                response.putNull("completed_at");
            } else {
                response.put("completed_at", completedAt.asText());
            }
            response.set("task", manifest.path("task"));

            // Steps
            List<JsonNode> steps = runStore.readSteps(runId);
            ArrayNode stepsArray = response.putArray("steps");
            for (JsonNode step : steps) {
                ObjectNode stepSummary = objectMapper.createObjectNode();
                stepSummary.put("step_id", step.path("step_id").asText());
                stepSummary.put("phase", step.path("phase").asText());
                stepSummary.put("status", step.path("status").asText());
                if (step.has("tool") && !step.path("tool").isNull()) {
                    stepSummary.put("tool", step.path("tool").asText());
                }
                if (step.has("agent_archetype") && !step.path("agent_archetype").isNull()) {
                    stepSummary.put("agent_archetype", step.path("agent_archetype").asText());
                }
                stepSummary.put("output_slot", step.path("output_slot").asText());
                if (step.has("output_preview") && !step.path("output_preview").isNull()) {
                    stepSummary.put("output_preview", step.path("output_preview").asText());
                }
                if (step.has("error") && !step.path("error").isNull()) {
                    stepSummary.put("error", step.path("error").asText());
                }
                stepsArray.add(stepSummary);
            }

            // Cache summary
            JsonNode cache = runStore.readCache(runId);
            ObjectNode cacheSummary = response.putObject("cache_summary");
            if (cache.isObject()) {
                var fields = cache.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    JsonNode slot = entry.getValue();
                    ObjectNode slotSummary = objectMapper.createObjectNode();
                    slotSummary.put("type", slot.path("type").asText("unknown"));
                    if (slot.has("summary")) {
                        slotSummary.put("preview", slot.path("summary").asText());
                    }
                    cacheSummary.set(entry.getKey(), slotSummary);
                }
            }

            // Error
            JsonNode error = manifest.path("error");
            if (error.isNull() || error.isMissingNode()) {
                response.putNull("error");
            } else {
                response.put("error", error.asText());
            }

            ctx.json(response);
        } catch (Exception e) {
            logger.warn("Failed to get run: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * GET /api/runs/{id}/steps
     * Full step log with all observability fields.
     */
    private void getSteps(Context ctx) {
        try {
            String runId = ctx.pathParam("id");
            JsonNode manifest = runStore.readManifest(runId);
            if (manifest == null) {
                ctx.status(404).json(Map.of("error", "Run not found: " + runId));
                return;
            }
            List<JsonNode> steps = runStore.readSteps(runId);
            ctx.json(steps);
        } catch (Exception e) {
            logger.warn("Failed to get steps: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * GET /api/runs/{id}/cache/{slot}
     * Drill-down into a specific cache slot.
     */
    private void getCacheSlot(Context ctx) {
        try {
            String runId = ctx.pathParam("id");
            String slotName = ctx.pathParam("slot");

            JsonNode manifest = runStore.readManifest(runId);
            if (manifest == null) {
                ctx.status(404).json(Map.of("error", "Run not found: " + runId));
                return;
            }

            JsonNode cache = runStore.readCache(runId);
            JsonNode slot = cache.path(slotName);
            if (slot.isMissingNode() || slot.isNull()) {
                ctx.status(404).json(Map.of("error", "Cache slot not found: " + slotName));
                return;
            }

            // Return slot without the internal "data" field (which can be large)
            ObjectNode response = objectMapper.createObjectNode();
            response.put("slot", slotName);
            response.put("type", slot.path("type").asText("unknown"));
            if (slot.has("receipt_id")) {
                response.put("receipt_id", slot.path("receipt_id").asText());
            }
            if (slot.has("agent_id")) {
                response.put("agent_id", slot.path("agent_id").asText());
            }
            if (slot.has("sha256")) {
                response.put("sha256", slot.path("sha256").asText());
            }
            if (slot.has("summary")) {
                response.put("summary", slot.path("summary").asText());
            }
            // For artifact slots, include full text; for pointers, include data
            if (slot.has("text")) {
                response.put("text", slot.path("text").asText());
            }
            if (slot.has("data")) {
                response.set("data", slot.path("data"));
            }

            ctx.json(response);
        } catch (Exception e) {
            logger.warn("Failed to get cache slot: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * POST /api/runs/{id}/cancel
     */
    private void cancelRun(Context ctx) {
        try {
            String runId = ctx.pathParam("id");
            JsonNode manifest = runStore.readManifest(runId);
            if (manifest == null) {
                ctx.status(404).json(Map.of("error", "Run not found: " + runId));
                return;
            }
            String status = manifest.path("status").asText("");
            if (!"running".equals(status)) {
                ctx.status(409).json(Map.of("error", "Run is not in 'running' state (current: " + status + ")"));
                return;
            }
            boolean cancelled = stepRunner.cancelRun(runId);
            if (cancelled) {
                ctx.json(Map.of("run_id", runId, "status", "cancelled"));
            } else {
                // Run might have just finished
                ctx.status(409).json(Map.of("error", "Run could not be cancelled (may have already completed)"));
            }
        } catch (Exception e) {
            logger.warn("Failed to cancel run: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }
}
