package com.miniide.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.AppLogger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Persistence layer for pipeline runs.
 *
 * Layout per run:
 *   .control-room/runs/{runId}/
 *   ├── run.json       manifest (metadata, status, timing, progress)
 *   ├── steps.jsonl    append-only log (one line per completed step)
 *   └── cache.json     slot store (written atomically after each step)
 */
public class RunStore {

    private final Path runsRoot;
    private final ObjectMapper objectMapper;
    private final AppLogger logger = AppLogger.get();

    public RunStore(Path workspaceRoot, ObjectMapper objectMapper) {
        this.runsRoot = workspaceRoot.resolve(".control-room").resolve("runs");
        this.objectMapper = objectMapper;
    }

    public String generateRunId() {
        return "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * Create a new run directory and write the initial run.json manifest.
     */
    public void createRun(ObjectNode manifest) throws IOException {
        String runId = manifest.path("run_id").asText();
        Path runDir = runsRoot.resolve(runId);
        Files.createDirectories(runDir);
        writeJsonAtomic(runDir.resolve("run.json"), manifest);
        // Initialize empty cache
        writeJsonAtomic(runDir.resolve("cache.json"), objectMapper.createObjectNode());
        logger.info("Run created: " + runId);
    }

    /**
     * Update the run.json manifest (atomic write).
     */
    public void updateManifest(String runId, ObjectNode manifest) throws IOException {
        Path runDir = runsRoot.resolve(runId);
        if (!Files.isDirectory(runDir)) {
            throw new IOException("Run directory not found: " + runId);
        }
        writeJsonAtomic(runDir.resolve("run.json"), manifest);
    }

    /**
     * Read the run.json manifest.
     */
    public JsonNode readManifest(String runId) throws IOException {
        Path file = runsRoot.resolve(runId).resolve("run.json");
        if (!Files.exists(file)) {
            return null;
        }
        return objectMapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * Append a step record to steps.jsonl.
     */
    public void appendStep(String runId, ObjectNode stepRecord) throws IOException {
        Path runDir = runsRoot.resolve(runId);
        if (!Files.isDirectory(runDir)) {
            throw new IOException("Run directory not found: " + runId);
        }
        Path stepsFile = runDir.resolve("steps.jsonl");
        String jsonLine = objectMapper.writeValueAsString(stepRecord);
        try (BufferedWriter writer = Files.newBufferedWriter(
                stepsFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            writer.write(jsonLine);
            writer.newLine();
        }
    }

    /**
     * Read all step records from steps.jsonl.
     */
    public List<JsonNode> readSteps(String runId) throws IOException {
        Path stepsFile = runsRoot.resolve(runId).resolve("steps.jsonl");
        if (!Files.exists(stepsFile)) {
            return List.of();
        }
        List<JsonNode> steps = new ArrayList<>();
        for (String line : Files.readAllLines(stepsFile, StandardCharsets.UTF_8)) {
            if (line != null && !line.isBlank()) {
                steps.add(objectMapper.readTree(line));
            }
        }
        return steps;
    }

    /**
     * Write the cache.json atomically (write-to-temp-rename for crash safety).
     */
    public void writeCache(String runId, ObjectNode cache) throws IOException {
        Path runDir = runsRoot.resolve(runId);
        if (!Files.isDirectory(runDir)) {
            throw new IOException("Run directory not found: " + runId);
        }
        writeJsonAtomic(runDir.resolve("cache.json"), cache);
    }

    /**
     * Read the cache.json.
     */
    public JsonNode readCache(String runId) throws IOException {
        Path file = runsRoot.resolve(runId).resolve("cache.json");
        if (!Files.exists(file)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * List all runs, optionally filtered by status and/or recipe_id.
     */
    public List<JsonNode> listRuns(String statusFilter, String recipeFilter) throws IOException {
        List<JsonNode> result = new ArrayList<>();
        if (!Files.isDirectory(runsRoot)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(runsRoot)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;
                Path manifestFile = dir.resolve("run.json");
                if (!Files.exists(manifestFile)) continue;
                try {
                    JsonNode manifest = objectMapper.readTree(Files.readString(manifestFile, StandardCharsets.UTF_8));
                    if (statusFilter != null && !statusFilter.isBlank()) {
                        String status = manifest.path("status").asText("");
                        if (!statusFilter.equalsIgnoreCase(status)) continue;
                    }
                    if (recipeFilter != null && !recipeFilter.isBlank()) {
                        String recipe = manifest.path("recipe_id").asText("");
                        if (!recipeFilter.equalsIgnoreCase(recipe)) continue;
                    }
                    result.add(manifest);
                } catch (IOException e) {
                    logger.warn("Failed to read run manifest: " + dir.getFileName() + " (" + e.getMessage() + ")");
                }
            }
        }
        result.sort(Comparator.comparing((JsonNode n) -> n.path("created_at").asText("")).reversed());
        return result;
    }

    /**
     * Atomic write: write to .tmp file, then rename.
     */
    private void writeJsonAtomic(Path target, JsonNode node) throws IOException {
        Path tmpFile = target.resolveSibling(target.getFileName().toString() + ".tmp");
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        Files.writeString(tmpFile, json, StandardCharsets.UTF_8);
        Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
