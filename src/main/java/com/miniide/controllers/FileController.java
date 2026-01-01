package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.WorkspaceService;
import com.miniide.models.SceneSegment;
import com.miniide.models.SearchResult;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Controller for file operations.
 * Handles: tree, file CRUD, rename, duplicate, search, segments, reveal, open-folder
 */
public class FileController implements Controller {

    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public FileController(WorkspaceService workspaceService, ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/tree", this::getTree);
        app.get("/api/file", this::getFile);
        app.put("/api/file", this::putFile);
        app.post("/api/file", this::createFile);
        app.delete("/api/file", this::deleteFile);
        app.post("/api/rename", this::renameFile);
        app.post("/api/duplicate", this::duplicateFile);
        app.get("/api/search", this::search);
        app.get("/api/segments", this::getSegments);
        app.post("/api/file/reveal", this::revealFile);
        app.post("/api/file/open-folder", this::openContainingFolder);
    }

    private void getTree(Context ctx) {
        try {
            ctx.json(workspaceService.getTree(""));
        } catch (Exception e) {
            logger.error("Error getting tree: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void getFile(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }
            String content = workspaceService.readFile(path);
            ctx.contentType("text/plain; charset=utf-8").result(content);
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void putFile(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }
            String content = ctx.body();
            workspaceService.writeFile(path, content);
            logger.info("File saved: " + path);
            ctx.json(Map.of("success", true, "message", "File saved: " + path));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void createFile(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String path = json.has("path") ? json.get("path").asText() : null;
            String type = json.has("type") ? json.get("type").asText() : "file";
            String initialContent = json.has("initialContent") ? json.get("initialContent").asText() : "";

            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path is required"));
                return;
            }

            if ("folder".equals(type)) {
                workspaceService.createFolder(path);
            } else {
                workspaceService.createFile(path, initialContent);
            }
            logger.info("Created " + type + ": " + path);
            ctx.json(Map.of("success", true, "message", "Created: " + path));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void deleteFile(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }
            workspaceService.deleteEntry(path);
            logger.info("Deleted: " + path);
            ctx.json(Map.of("success", true, "message", "Deleted: " + path));
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void renameFile(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String from = json.has("from") ? json.get("from").asText() : null;
            String to = json.has("to") ? json.get("to").asText() : null;

            if (from == null || to == null) {
                ctx.status(400).json(Map.of("error", "Both 'from' and 'to' paths required"));
                return;
            }

            workspaceService.renameEntry(from, to);
            logger.info("Renamed: " + from + " -> " + to);
            ctx.json(Map.of("success", true, "message", "Renamed: " + from + " -> " + to));
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void duplicateFile(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String path = json.has("path") ? json.get("path").asText() : null;

            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path is required"));
                return;
            }

            String newPath = workspaceService.duplicateEntry(path);
            logger.info("Duplicated: " + path + " -> " + newPath);
            ctx.json(Map.of("success", true, "message", "Duplicated: " + path, "newPath", newPath));
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void search(Context ctx) {
        try {
            String query = ctx.queryParam("q");
            String pattern = ctx.queryParam("pattern");
            List<SearchResult> results = workspaceService.search(query, pattern);
            ctx.json(results);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void getSegments(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }
            List<SceneSegment> segments = workspaceService.getSceneSegments(path);
            ctx.json(segments);
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void revealFile(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String path = json.has("path") ? json.get("path").asText() : null;

            if (path == null || path.isEmpty()) {
                ctx.json(Map.of("ok", false, "error", "Path is required"));
                return;
            }

            Path absPath = workspaceService.resolvePath(path);

            if (!Files.exists(absPath)) {
                ctx.json(Map.of("ok", false, "error", "File not found: " + path));
                return;
            }

            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer.exe", "/select,", absPath.toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", "-R", absPath.toString());
            } else {
                Path parentDir = absPath.getParent();
                pb = new ProcessBuilder("xdg-open", parentDir.toString());
            }

            try {
                pb.start();
                logger.info("Revealed file in explorer: " + path);
                ctx.json(Map.of("ok", true));
            } catch (IOException io) {
                logger.warn("Reveal failed, opening containing folder: " + io.getMessage());
                openParentFolder(absPath, os, ctx);
            }
        } catch (SecurityException e) {
            logger.warn("Security violation in reveal: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to reveal file: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    private void openParentFolder(Path absPath, String os, Context ctx) throws IOException {
        Path parentDir = absPath.getParent();
        if (parentDir == null) {
            parentDir = workspaceService.getWorkspaceRoot();
        }

        ProcessBuilder fallbackPb;
        if (os.contains("win")) {
            fallbackPb = new ProcessBuilder("explorer.exe", parentDir.toString());
        } else if (os.contains("mac")) {
            fallbackPb = new ProcessBuilder("open", parentDir.toString());
        } else {
            fallbackPb = new ProcessBuilder("xdg-open", parentDir.toString());
        }

        fallbackPb.start();
        logger.info("Opened containing folder fallback: " + parentDir);
        ctx.json(Map.of("ok", true, "fallback", "open-folder"));
    }

    private void openContainingFolder(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String path = json.has("path") ? json.get("path").asText() : null;

            if (path == null || path.isEmpty()) {
                ctx.json(Map.of("ok", false, "error", "Path is required"));
                return;
            }

            Path absPath = workspaceService.resolvePath(path);

            if (!Files.exists(absPath)) {
                ctx.json(Map.of("ok", false, "error", "File not found: " + path));
                return;
            }

            Path targetDir = Files.isDirectory(absPath) ? absPath : absPath.getParent();
            if (targetDir == null) {
                targetDir = workspaceService.getWorkspaceRoot();
            }

            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer.exe", targetDir.toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", targetDir.toString());
            } else {
                pb = new ProcessBuilder("xdg-open", targetDir.toString());
            }

            pb.start();
            logger.info("Opened containing folder: " + targetDir);
            ctx.json(Map.of("ok", true));
        } catch (SecurityException e) {
            logger.warn("Security violation in open folder: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to open containing folder: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
