package com.miniide.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.AppLogger;
import com.miniide.tools.ToolCall;
import com.miniide.tools.ToolExecutionContext;
import com.miniide.tools.ToolExecutionResult;
import com.miniide.tools.ToolExecutionService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes recipes sequentially (Phase A only for now).
 *
 * Phase A = tool steps executed by the system, no model involved.
 * Each step: resolve args via $ref, call ToolExecutionService, cache result,
 * persist step record, update manifest. Halt on failure.
 *
 * Phase B (agent calls) is out of scope — stubs only.
 */
public class StepRunner {

    private final ToolExecutionService toolService;
    private final RunStore runStore;
    private final RefResolver refResolver;
    private final RecipeRegistry recipeRegistry;
    private final ObjectMapper objectMapper;
    private final AppLogger logger = AppLogger.get();

    /** Tracks cancellation requests by runId */
    private final ConcurrentHashMap<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public StepRunner(ToolExecutionService toolService, RunStore runStore,
                      RefResolver refResolver, RecipeRegistry recipeRegistry,
                      ObjectMapper objectMapper) {
        this.toolService = toolService;
        this.runStore = runStore;
        this.refResolver = refResolver;
        this.recipeRegistry = recipeRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Start a run asynchronously. Creates the manifest, then executes Phase A
     * steps on a background thread.
     *
     * @param recipeId the recipe to execute
     * @param args     initial task args (scene_path, canon_paths, etc.)
     * @param description task description for the manifest
     * @return the run_id
     */
    public String startRun(String recipeId, Map<String, Object> args, String description) throws IOException {
        JsonNode recipe = recipeRegistry.get(recipeId);
        if (recipe == null) {
            throw new IllegalArgumentException("Unknown recipe: " + recipeId);
        }

        JsonNode phaseA = recipe.path("phase_a");
        JsonNode phaseB = recipe.path("phase_b");
        int totalSteps = (phaseA.isArray() ? phaseA.size() : 0) + (phaseB.isArray() ? phaseB.size() : 0);

        String runId = runStore.generateRunId();
        String sessionId = "pipeline-" + runId;
        String now = Instant.now().toString();

        // Build manifest
        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put("run_id", runId);
        manifest.put("recipe_id", recipeId);
        manifest.put("session_id", sessionId);
        manifest.put("status", "running");
        manifest.put("created_at", now);
        manifest.put("updated_at", now);
        manifest.putNull("completed_at");
        ObjectNode taskNode = manifest.putObject("task");
        taskNode.put("description", description != null ? description : "");
        ObjectNode initialArgs = objectMapper.createObjectNode();
        if (args != null) {
            for (var entry : args.entrySet()) {
                initialArgs.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
            }
        }
        taskNode.set("initial_args", initialArgs);
        taskNode.set("args", initialArgs); // alias for $ref resolution (spec C: "task.args.*")
        manifest.put("current_step_index", 0);
        manifest.put("total_steps", totalSteps);
        manifest.put("phase", "a");
        manifest.putNull("error");

        runStore.createRun(manifest);

        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelFlags.put(runId, cancelled);

        // Execute Phase A on a background thread
        Thread runner = new Thread(() -> executePhaseA(runId, recipe, manifest, cancelled), "pipeline-" + runId);
        runner.setDaemon(true);
        runner.start();

        return runId;
    }

    /**
     * Request cancellation of a running run.
     */
    public boolean cancelRun(String runId) {
        AtomicBoolean flag = cancelFlags.get(runId);
        if (flag != null) {
            flag.set(true);
            return true;
        }
        return false;
    }

    private void executePhaseA(String runId, JsonNode recipe, ObjectNode manifest, AtomicBoolean cancelled) {
        JsonNode phaseA = recipe.path("phase_a");
        if (!phaseA.isArray()) {
            completeRun(runId, manifest, "done", null);
            return;
        }

        String sessionId = manifest.path("session_id").asText();

        // Build the task node for $ref resolution
        JsonNode taskNode = manifest.path("task");

        // Load current cache (empty initially, or from restart)
        ObjectNode cache;
        try {
            JsonNode existing = runStore.readCache(runId);
            cache = existing.isObject() ? (ObjectNode) existing : objectMapper.createObjectNode();
        } catch (IOException e) {
            completeRun(runId, manifest, "failed", "Failed to read cache: " + e.getMessage());
            return;
        }

        for (int i = 0; i < phaseA.size(); i++) {
            if (cancelled.get()) {
                completeRun(runId, manifest, "cancelled", null);
                return;
            }

            JsonNode step = phaseA.get(i);
            String stepId = step.path("step_id").asText("step_" + i);
            String toolName = step.path("tool").asText();
            String outputSlot = step.path("output_slot").asText();
            JsonNode argsTemplate = step.path("args");

            Instant stepStart = Instant.now();

            // Update manifest: current step
            manifest.put("current_step_index", i);
            manifest.put("updated_at", Instant.now().toString());
            try {
                runStore.updateManifest(runId, manifest);
            } catch (IOException e) {
                logger.warn("Failed to update manifest for step " + stepId + ": " + e.getMessage());
            }

            // Resolve $ref args
            Map<String, Object> resolvedArgs;
            try {
                resolvedArgs = refResolver.resolveArgs(argsTemplate, taskNode, cache);
            } catch (RefResolver.RefResolutionException e) {
                recordFailedStep(runId, i, stepId, toolName, outputSlot, stepStart, e.getMessage());
                completeRun(runId, manifest, "failed", "Ref resolution failed at step '" + stepId + "': " + e.getMessage());
                return;
            }

            // Execute the tool
            ToolCall call = new ToolCall(toolName, resolvedArgs, null, null);
            ToolExecutionContext ctx = new ToolExecutionContext(sessionId, runId, "step-" + i, null);
            ToolExecutionResult result = toolService.execute(call, ctx);

            if (!result.isOk()) {
                recordFailedStep(runId, i, stepId, toolName, outputSlot, stepStart,
                        result.getError() + ": " + result.getOutput());
                completeRun(runId, manifest, "failed",
                        "Tool '" + toolName + "' failed at step '" + stepId + "': " + result.getError());
                return;
            }

            // Build cache slot (Phase A = pointer mode)
            String outputHash = sha256(result.getOutput());
            String preview = truncate(result.getOutput(), 200);
            ObjectNode slot = objectMapper.createObjectNode();
            slot.put("type", "pointer");
            if (result.getReceiptId() != null) {
                slot.put("receipt_id", result.getReceiptId());
            }
            slot.put("sha256", outputHash);
            slot.put("summary", preview);
            // Also store parsed data for $ref resolution by subsequent steps
            try {
                JsonNode parsed = objectMapper.readTree(result.getOutput());
                slot.set("data", parsed);
            } catch (Exception e) {
                // Tool output wasn't JSON — store as text node
                slot.put("data", result.getOutput());
            }
            cache.set(outputSlot, slot);

            // Persist cache atomically
            try {
                runStore.writeCache(runId, cache);
            } catch (IOException e) {
                logger.warn("Failed to write cache after step " + stepId + ": " + e.getMessage());
            }

            // Record completed step
            Instant stepEnd = Instant.now();
            ObjectNode stepRecord = objectMapper.createObjectNode();
            stepRecord.put("step_index", i);
            stepRecord.put("step_id", stepId);
            stepRecord.put("phase", "a");
            stepRecord.put("tool", toolName);
            stepRecord.putNull("agent_archetype");
            stepRecord.putNull("agent_id");
            stepRecord.put("status", "done");
            stepRecord.put("output_slot", outputSlot);
            stepRecord.put("receipt_id", result.getReceiptId());
            stepRecord.put("output_hash", outputHash);
            stepRecord.put("output_preview", preview);
            stepRecord.put("started_at", stepStart.toString());
            stepRecord.put("completed_at", stepEnd.toString());
            stepRecord.putNull("error");

            try {
                runStore.appendStep(runId, stepRecord);
            } catch (IOException e) {
                logger.warn("Failed to append step record for " + stepId + ": " + e.getMessage());
            }

            logger.info("Pipeline " + runId + " step " + stepId + " (" + toolName + ") completed");
        }

        // Phase A complete. Phase B is out of scope — mark done.
        JsonNode phaseB = recipe.path("phase_b");
        if (phaseB.isArray() && phaseB.size() > 0) {
            // Phase B exists but not implemented yet — mark the run as done (Phase A only)
            manifest.put("phase", "a_complete");
            completeRun(runId, manifest, "done", null);
        } else {
            completeRun(runId, manifest, "done", null);
        }

        cancelFlags.remove(runId);
    }

    private void recordFailedStep(String runId, int index, String stepId, String toolName,
                                   String outputSlot, Instant stepStart, String error) {
        ObjectNode stepRecord = objectMapper.createObjectNode();
        stepRecord.put("step_index", index);
        stepRecord.put("step_id", stepId);
        stepRecord.put("phase", "a");
        stepRecord.put("tool", toolName);
        stepRecord.putNull("agent_archetype");
        stepRecord.putNull("agent_id");
        stepRecord.put("status", "failed");
        stepRecord.put("output_slot", outputSlot);
        stepRecord.putNull("receipt_id");
        stepRecord.putNull("output_hash");
        stepRecord.putNull("output_preview");
        stepRecord.put("started_at", stepStart.toString());
        stepRecord.put("completed_at", Instant.now().toString());
        stepRecord.put("error", error);

        try {
            runStore.appendStep(runId, stepRecord);
        } catch (IOException e) {
            logger.warn("Failed to append failed step record for " + stepId + ": " + e.getMessage());
        }
    }

    private void completeRun(String runId, ObjectNode manifest, String status, String error) {
        manifest.put("status", status);
        manifest.put("updated_at", Instant.now().toString());
        if ("done".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
            manifest.put("completed_at", Instant.now().toString());
        }
        if (error != null) {
            manifest.put("error", error);
        }
        try {
            runStore.updateManifest(runId, manifest);
        } catch (IOException e) {
            logger.warn("Failed to update manifest for completion of " + runId + ": " + e.getMessage());
        }
        cancelFlags.remove(runId);
    }

    private static String sha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "sha256:error";
        }
    }

    private static String truncate(String input, int maxLen) {
        if (input == null) return "";
        if (input.length() <= maxLen) return input;
        return input.substring(0, maxLen) + "...";
    }
}
