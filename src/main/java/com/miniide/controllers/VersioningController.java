package com.miniide.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppConfig;
import com.miniide.AppLogger;
import com.miniide.models.Snapshot;
import com.miniide.models.SnapshotFile;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller for Version Control / Snapshot History functionality.
 * Provides writer-friendly versioning without Git jargon.
 */
public class VersioningController implements Controller {

    private static final String HISTORY_DIR = ".control-room/history";
    private static final String SNAPSHOTS_FILE = "snapshots.json";
    private static final String CONTENT_DIR = "content";

    private final ObjectMapper objectMapper;
    private final AppLogger logger;
    private final Path workspaceRoot;

    public VersioningController(ObjectMapper objectMapper, Path workspaceRoot) {
        this.objectMapper = objectMapper;
        this.workspaceRoot = workspaceRoot;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/versioning/status", this::getStatus);
        app.get("/api/versioning/changes", this::getChanges);
        app.get("/api/versioning/snapshots", this::listSnapshots);
        app.post("/api/versioning/publish", this::publishSnapshot);
        app.post("/api/versioning/discard", this::discardChanges);
        app.post("/api/versioning/restore", this::restoreFile);
        app.get("/api/versioning/snapshot/{id}", this::getSnapshot);
        app.get("/api/versioning/file-history", this::getFileHistory);
        app.delete("/api/versioning/snapshot/{id}", this::deleteSnapshot);
        app.post("/api/versioning/cleanup", this::cleanupSnapshots);
    }

    /**
     * Get versioning status for current workspace.
     */
    private void getStatus(Context ctx) {
        try {
            Path historyPath = workspaceRoot.resolve(HISTORY_DIR);
            boolean initialized = Files.exists(historyPath.resolve(SNAPSHOTS_FILE));
            List<Snapshot> snapshots = initialized ? loadSnapshots() : new ArrayList<>();

            ctx.json(Map.of(
                "initialized", initialized,
                "snapshotCount", snapshots.size(),
                "lastSnapshot", snapshots.isEmpty() ? null : snapshots.get(0)
            ));
        } catch (Exception e) {
            logger.error("Failed to get versioning status: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Get list of files changed since last published snapshot.
     */
    private void getChanges(Context ctx) {
        try {
            List<Map<String, Object>> changes = detectChanges();

            // Calculate word deltas
            int addedWords = 0;
            int removedWords = 0;
            for (Map<String, Object> change : changes) {
                addedWords += (int) change.getOrDefault("addedWords", 0);
                removedWords += (int) change.getOrDefault("removedWords", 0);
            }

            ctx.json(Map.of(
                "changes", changes,
                "fileCount", changes.size(),
                "addedWords", addedWords,
                "removedWords", removedWords
            ));
        } catch (Exception e) {
            logger.error("Failed to get changes: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * List all published snapshots (newest first).
     */
    private void listSnapshots(Context ctx) {
        try {
            List<Snapshot> snapshots = loadSnapshots();
            ctx.json(Map.of("snapshots", snapshots));
        } catch (Exception e) {
            logger.error("Failed to list snapshots: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Publish current changes as a new snapshot.
     */
    private void publishSnapshot(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String name = json.has("name") && !json.get("name").asText().isBlank()
                ? json.get("name").asText()
                : generateDefaultName();

            List<Map<String, Object>> changes = detectChanges();
            if (changes.isEmpty()) {
                ctx.status(400).json(Map.of("error", "No changes to publish"));
                return;
            }

            // Create snapshot
            String snapshotId = UUID.randomUUID().toString().substring(0, 8);
            Instant now = Instant.now();
            Snapshot snapshot = new Snapshot(snapshotId, name, now);

            // Calculate totals and add files
            int totalAdded = 0;
            int totalRemoved = 0;

            Path contentDir = workspaceRoot.resolve(HISTORY_DIR).resolve(CONTENT_DIR).resolve(snapshotId);
            Files.createDirectories(contentDir);

            for (Map<String, Object> change : changes) {
                String filePath = (String) change.get("path");
                String status = (String) change.get("status");
                totalAdded += (int) change.getOrDefault("addedWords", 0);
                totalRemoved += (int) change.getOrDefault("removedWords", 0);

                // Store file content
                Path sourcePath = workspaceRoot.resolve(filePath);
                String contentHash = "";

                if (!status.equals("deleted") && Files.exists(sourcePath)) {
                    byte[] content = Files.readAllBytes(sourcePath);
                    contentHash = hashContent(content);

                    // Store content by hash to dedupe
                    Path hashDir = workspaceRoot.resolve(HISTORY_DIR).resolve(CONTENT_DIR).resolve("blobs");
                    Files.createDirectories(hashDir);
                    Path blobPath = hashDir.resolve(contentHash);
                    if (!Files.exists(blobPath)) {
                        Files.write(blobPath, content);
                    }
                }

                SnapshotFile snapshotFile = new SnapshotFile(filePath, status, contentHash);
                snapshot.addFile(snapshotFile);
            }

            snapshot.setAddedWords(totalAdded);
            snapshot.setRemovedWords(totalRemoved);

            // Save to snapshots list
            List<Snapshot> snapshots = loadSnapshots();
            snapshots.add(0, snapshot); // Add to front (newest first)
            saveSnapshots(snapshots);

            // Update baseline hashes
            updateBaseline();

            logger.info("Published snapshot: " + name + " (" + changes.size() + " files)");
            ctx.json(Map.of(
                "ok", true,
                "snapshot", snapshot
            ));
        } catch (Exception e) {
            logger.error("Failed to publish snapshot: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Discard changes for specific files or all files.
     */
    private void discardChanges(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            boolean all = json.has("all") && json.get("all").asBoolean();
            List<String> paths = new ArrayList<>();

            if (json.has("paths") && json.get("paths").isArray()) {
                for (JsonNode pathNode : json.get("paths")) {
                    paths.add(pathNode.asText());
                }
            }

            List<Map<String, Object>> changes = detectChanges();
            List<String> toRestore = all
                ? changes.stream().map(c -> (String) c.get("path")).collect(Collectors.toList())
                : paths;

            int restored = 0;
            for (String filePath : toRestore) {
                if (restoreFileFromLastSnapshot(filePath)) {
                    restored++;
                }
            }

            ctx.json(Map.of(
                "ok", true,
                "restoredCount", restored
            ));
        } catch (Exception e) {
            logger.error("Failed to discard changes: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Restore a file from a specific snapshot.
     */
    private void restoreFile(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String snapshotId = json.get("snapshotId").asText();
            String filePath = json.get("path").asText();

            List<Snapshot> snapshots = loadSnapshots();
            Snapshot snapshot = snapshots.stream()
                .filter(s -> s.getId().equals(snapshotId))
                .findFirst()
                .orElse(null);

            if (snapshot == null) {
                ctx.status(404).json(Map.of("error", "Snapshot not found"));
                return;
            }

            SnapshotFile snapshotFile = snapshot.getFiles().stream()
                .filter(f -> f.getPath().equals(filePath))
                .findFirst()
                .orElse(null);

            if (snapshotFile == null) {
                ctx.status(404).json(Map.of("error", "File not found in snapshot"));
                return;
            }

            // Restore content from blob
            Path blobPath = workspaceRoot.resolve(HISTORY_DIR).resolve(CONTENT_DIR)
                .resolve("blobs").resolve(snapshotFile.getContentHash());
            Path targetPath = workspaceRoot.resolve(filePath);

            if (Files.exists(blobPath)) {
                Files.createDirectories(targetPath.getParent());
                Files.copy(blobPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                ctx.json(Map.of("ok", true, "path", filePath));
            } else {
                ctx.status(404).json(Map.of("error", "Content blob not found"));
            }
        } catch (Exception e) {
            logger.error("Failed to restore file: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Get details of a specific snapshot.
     */
    private void getSnapshot(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            List<Snapshot> snapshots = loadSnapshots();

            Snapshot snapshot = snapshots.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);

            if (snapshot == null) {
                ctx.status(404).json(Map.of("error", "Snapshot not found"));
                return;
            }

            ctx.json(snapshot);
        } catch (Exception e) {
            logger.error("Failed to get snapshot: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Get history of a specific file across snapshots.
     */
    private void getFileHistory(Context ctx) {
        try {
            String filePath = ctx.queryParam("path");
            if (filePath == null || filePath.isBlank()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }

            List<Snapshot> snapshots = loadSnapshots();
            List<Map<String, Object>> history = new ArrayList<>();

            for (Snapshot snapshot : snapshots) {
                for (SnapshotFile file : snapshot.getFiles()) {
                    if (file.getPath().equals(filePath)) {
                        history.add(Map.of(
                            "snapshotId", snapshot.getId(),
                            "snapshotName", snapshot.getName(),
                            "publishedAt", snapshot.getPublishedAt().toString(),
                            "status", file.getStatus(),
                            "contentHash", file.getContentHash()
                        ));
                        break;
                    }
                }
            }

            ctx.json(Map.of("path", filePath, "history", history));
        } catch (Exception e) {
            logger.error("Failed to get file history: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Delete a specific snapshot.
     */
    private void deleteSnapshot(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            List<Snapshot> snapshots = loadSnapshots();

            boolean removed = snapshots.removeIf(s -> s.getId().equals(id));
            if (!removed) {
                ctx.status(404).json(Map.of("error", "Snapshot not found"));
                return;
            }

            saveSnapshots(snapshots);
            // Note: We don't delete blobs as they may be referenced by other snapshots

            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            logger.error("Failed to delete snapshot: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Cleanup old snapshots, keeping only the last N.
     */
    private void cleanupSnapshots(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            int keepCount = json.has("keepCount") ? json.get("keepCount").asInt() : 10;

            List<Snapshot> snapshots = loadSnapshots();
            int originalCount = snapshots.size();

            if (snapshots.size() > keepCount) {
                snapshots = snapshots.subList(0, keepCount);
                saveSnapshots(snapshots);
            }

            int removed = originalCount - snapshots.size();
            ctx.json(Map.of(
                "ok", true,
                "removed", removed,
                "remaining", snapshots.size()
            ));
        } catch (Exception e) {
            logger.error("Failed to cleanup snapshots: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    // ===== Helpers =====

    private List<Snapshot> loadSnapshots() throws IOException {
        Path snapshotsPath = workspaceRoot.resolve(HISTORY_DIR).resolve(SNAPSHOTS_FILE);
        if (!Files.exists(snapshotsPath)) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(snapshotsPath.toFile(),
            new TypeReference<List<Snapshot>>() {});
    }

    private void saveSnapshots(List<Snapshot> snapshots) throws IOException {
        Path historyDir = workspaceRoot.resolve(HISTORY_DIR);
        Files.createDirectories(historyDir);
        Path snapshotsPath = historyDir.resolve(SNAPSHOTS_FILE);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(snapshotsPath.toFile(), snapshots);
    }

    private List<Map<String, Object>> detectChanges() throws IOException {
        List<Map<String, Object>> changes = new ArrayList<>();
        Map<String, String> baseline = loadBaseline();

        // Get current file hashes
        Map<String, String> currentHashes = getCurrentFileHashes();

        // Check for modified and added files
        for (Map.Entry<String, String> entry : currentHashes.entrySet()) {
            String path = entry.getKey();
            String currentHash = entry.getValue();
            String baselineHash = baseline.get(path);

            if (baselineHash == null) {
                // New file
                changes.add(createChangeEntry(path, "added", currentHash));
            } else if (!baselineHash.equals(currentHash)) {
                // Modified file
                changes.add(createChangeEntry(path, "modified", currentHash));
            }
        }

        // Check for deleted files
        for (String path : baseline.keySet()) {
            if (!currentHashes.containsKey(path)) {
                changes.add(createChangeEntry(path, "deleted", ""));
            }
        }

        return changes;
    }

    private Map<String, Object> createChangeEntry(String path, String status, String hash) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", path);
        entry.put("status", status);
        entry.put("hash", hash);

        // Simple word count estimation for text files
        if (!status.equals("deleted")) {
            try {
                Path filePath = workspaceRoot.resolve(path);
                if (Files.exists(filePath) && isTextFile(path)) {
                    String content = Files.readString(filePath);
                    int words = countWords(content);
                    entry.put("addedWords", words);
                    entry.put("removedWords", 0);
                }
            } catch (Exception e) {
                // Ignore word count errors
            }
        }

        return entry;
    }

    private Map<String, String> loadBaseline() throws IOException {
        Path baselinePath = workspaceRoot.resolve(HISTORY_DIR).resolve("baseline.json");
        if (!Files.exists(baselinePath)) {
            return new HashMap<>();
        }
        return objectMapper.readValue(baselinePath.toFile(),
            new TypeReference<Map<String, String>>() {});
    }

    private void updateBaseline() throws IOException {
        Map<String, String> currentHashes = getCurrentFileHashes();
        Path baselinePath = workspaceRoot.resolve(HISTORY_DIR).resolve("baseline.json");
        Files.createDirectories(baselinePath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(baselinePath.toFile(), currentHashes);
    }

    private Map<String, String> getCurrentFileHashes() throws IOException {
        Map<String, String> hashes = new HashMap<>();

        // Walk workspace, excluding .control-room and common ignore patterns
        try (Stream<Path> walk = Files.walk(workspaceRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(this::shouldTrackFile)
                .forEach(path -> {
                    try {
                        String relativePath = workspaceRoot.relativize(path).toString().replace('\\', '/');
                        byte[] content = Files.readAllBytes(path);
                        hashes.put(relativePath, hashContent(content));
                    } catch (IOException e) {
                        logger.error("Failed to hash file: " + path, e);
                    }
                });
        }

        return hashes;
    }

    private boolean shouldTrackFile(Path path) {
        String pathStr = path.toString().replace('\\', '/');

        // Ignore patterns
        if (pathStr.contains("/.control-room/")) return false;
        if (pathStr.contains("/node_modules/")) return false;
        if (pathStr.contains("/.git/")) return false;
        if (pathStr.contains("/__pycache__/")) return false;
        if (pathStr.endsWith(".class")) return false;
        if (pathStr.endsWith(".jar")) return false;

        return true;
    }

    private boolean restoreFileFromLastSnapshot(String filePath) {
        try {
            List<Snapshot> snapshots = loadSnapshots();
            if (snapshots.isEmpty()) {
                return false;
            }

            // Find file in the most recent snapshot
            for (Snapshot snapshot : snapshots) {
                for (SnapshotFile file : snapshot.getFiles()) {
                    if (file.getPath().equals(filePath) && !file.getStatus().equals("deleted")) {
                        Path blobPath = workspaceRoot.resolve(HISTORY_DIR)
                            .resolve(CONTENT_DIR).resolve("blobs").resolve(file.getContentHash());
                        Path targetPath = workspaceRoot.resolve(filePath);

                        if (Files.exists(blobPath)) {
                            Files.createDirectories(targetPath.getParent());
                            Files.copy(blobPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("Failed to restore file: " + filePath, e);
            return false;
        }
    }

    private String generateDefaultName() {
        // Format: project_name_YYYYMMDD_HHMM
        String projectName = workspaceRoot.getFileName().toString();
        String normalized = projectName.toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
            .withZone(ZoneId.systemDefault());
        String timestamp = formatter.format(Instant.now());

        return normalized + "_" + timestamp;
    }

    private String hashContent(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().substring(0, 16); // Use first 16 chars
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 16);
        }
    }

    private boolean isTextFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json")
            || lower.endsWith(".js") || lower.endsWith(".java") || lower.endsWith(".py")
            || lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".xml")
            || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".toml");
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.split("\\s+").length;
    }
}
