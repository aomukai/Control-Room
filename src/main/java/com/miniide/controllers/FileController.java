package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.ProjectContext;
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

    private final ProjectContext projectContext;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public FileController(ProjectContext projectContext, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
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
            if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                ctx.json(projectContext.preparedWorkspace().getTree());
            } else {
                ctx.json(projectContext.workspace().getTree(""));
            }
        } catch (Exception e) {
            logger.error("Error getting tree: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getFile(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }
            String content;
            if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                content = projectContext.preparedWorkspace().readFile(path);
            } else {
                content = projectContext.workspace().readFile(path);
            }
            ctx.contentType("text/plain; charset=utf-8").result(content);
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Controller.errorBody(e));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
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
            if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                projectContext.preparedWorkspace().writeFile(path, content);
            } else {
                projectContext.workspace().writeFile(path, content);
            }
            logger.info("File saved: " + path);
            ctx.json(Map.of("success", true, "message", "File saved: " + path));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
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
                if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                    ctx.status(400).json(Map.of("error", "Folders are not created in prepared mode."));
                    return;
                }
                projectContext.workspace().createFolder(path);
            } else {
                if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                    projectContext.preparedWorkspace().createFile(path, initialContent);
                } else {
                    projectContext.workspace().createFile(path, initialContent);
                }
            }
            logger.info("Created " + type + ": " + path);
            ctx.json(Map.of("success", true, "message", "Created: " + path));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void deleteFile(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }
            if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                projectContext.preparedWorkspace().deleteEntry(path);
            } else {
                projectContext.workspace().deleteEntry(path);
            }
            logger.info("Deleted: " + path);
            ctx.json(Map.of("success", true, "message", "Deleted: " + path));
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Controller.errorBody(e));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
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
            if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                projectContext.preparedWorkspace().renameEntry(from, to);
            } else {
                projectContext.workspace().renameEntry(from, to);
            }
            logger.info("Renamed: " + from + " -> " + to);
            ctx.json(Map.of("success", true, "message", "Renamed: " + from + " -> " + to));
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Controller.errorBody(e));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
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
            String newPath;
            if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                newPath = projectContext.preparedWorkspace().duplicateEntry(path);
            } else {
                newPath = projectContext.workspace().duplicateEntry(path);
            }
            logger.info("Duplicated: " + path + " -> " + newPath);
            ctx.json(Map.of("success", true, "message", "Duplicated: " + path, "newPath", newPath));
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Controller.errorBody(e));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void search(Context ctx) {
        try {
            String query = ctx.queryParam("q");
            String pattern = ctx.queryParam("pattern");
            List<SearchResult> results;
            if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                results = projectContext.preparedWorkspace().search(query);
            } else {
                results = projectContext.workspace().search(query, pattern);
            }
            ctx.json(results);
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getSegments(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }
            List<SceneSegment> segments;
            if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                segments = projectContext.preparedWorkspace().getSceneSegments(path);
            } else {
                segments = projectContext.workspace().getSceneSegments(path);
            }
            ctx.json(segments);
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Controller.errorBody(e));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
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
            if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                ctx.json(Map.of("ok", false, "error", "Prepared projects are virtual-only."));
                return;
            }

            Path absPath = projectContext.workspace().resolvePath(path);

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
            parentDir = projectContext.workspace().getWorkspaceRoot();
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
            if (projectContext.preparation() != null && projectContext.preparation().isPrepared()) {
                ctx.json(Map.of("ok", false, "error", "Prepared projects are virtual-only."));
                return;
            }

            Path absPath = projectContext.workspace().resolvePath(path);

            if (!Files.exists(absPath)) {
                ctx.json(Map.of("ok", false, "error", "File not found: " + path));
                return;
            }

            Path targetDir = Files.isDirectory(absPath) ? absPath : absPath.getParent();
            if (targetDir == null) {
                targetDir = projectContext.workspace().getWorkspaceRoot();
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
