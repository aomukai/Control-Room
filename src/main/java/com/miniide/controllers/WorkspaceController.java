package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppConfig;
import com.miniide.AppLogger;
import com.miniide.ProjectContext;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller for workspace operations.
 */
public class WorkspaceController implements Controller {

    private final ProjectContext projectContext;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;
    private final boolean devMode;

    public WorkspaceController(ProjectContext projectContext, ObjectMapper objectMapper, boolean devMode) {
        this.projectContext = projectContext;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
        this.devMode = devMode;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.post("/api/workspace/open", this::openWorkspace);
        app.post("/api/workspace/terminal", this::openWorkspaceTerminal);
        app.get("/api/workspace/info", this::getWorkspaceInfo);
        app.post("/api/workspace/select", this::selectWorkspace);
        app.get("/api/workspace/metadata", this::getMetadata);
        app.put("/api/workspace/metadata", this::updateMetadata);
    }

    private void openWorkspace(Context ctx) {
        try {
            String workspacePath = projectContext.workspace().getWorkspaceRoot().toString();
            String os = System.getProperty("os.name").toLowerCase();

            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer.exe", workspacePath);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", workspacePath);
            } else {
                pb = new ProcessBuilder("xdg-open", workspacePath);
            }

            pb.start();
            logger.info("Opened workspace folder: " + workspacePath);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            logger.error("Failed to open workspace folder: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    private void openWorkspaceTerminal(Context ctx) {
        try {
            String workspacePath = projectContext.workspace().getWorkspaceRoot().toString();
            String os = System.getProperty("os.name").toLowerCase();

            ProcessBuilder pb = null;
            String terminalUsed = null;

            if (os.contains("win")) {
                if (isCommandAvailable("wt.exe")) {
                    pb = new ProcessBuilder("wt.exe", "-d", workspacePath);
                    terminalUsed = "Windows Terminal";
                } else {
                    pb = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", "cd", "/d", workspacePath);
                    terminalUsed = "Command Prompt";
                }
            } else if (os.contains("mac")) {
                String script = "tell application \"Terminal\" to do script \"cd '" + workspacePath.replace("'", "'\\''") + "'\"";
                pb = new ProcessBuilder("osascript", "-e", script);
                terminalUsed = "Terminal.app";
            } else {
                String[] terminals = {"x-terminal-emulator", "gnome-terminal", "konsole", "xfce4-terminal", "xterm"};
                for (String terminal : terminals) {
                    if (isCommandAvailable(terminal)) {
                        if (terminal.equals("gnome-terminal") || terminal.equals("xfce4-terminal")) {
                            pb = new ProcessBuilder(terminal, "--working-directory=" + workspacePath);
                        } else if (terminal.equals("konsole")) {
                            pb = new ProcessBuilder(terminal, "--workdir", workspacePath);
                        } else {
                            pb = new ProcessBuilder(terminal, "-e", "sh", "-c", "cd '" + workspacePath + "' && exec $SHELL");
                        }
                        terminalUsed = terminal;
                        break;
                    }
                }

                if (pb == null) {
                    logger.warn("No terminal emulator found on Linux");
                    ctx.json(Map.of("ok", false, "error", "No terminal emulator found"));
                    return;
                }
            }

            pb.start();
            logger.info("Opened terminal at workspace: " + workspacePath + " (using " + terminalUsed + ")");
            ctx.json(Map.of("ok", true, "terminal", terminalUsed));
        } catch (Exception e) {
            logger.error("Failed to open terminal: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    private boolean isCommandAvailable(String command) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("where", command);
            } else {
                pb = new ProcessBuilder("which", command);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void getWorkspaceInfo(Context ctx) {
        try {
            Path current = projectContext.workspace().getWorkspaceRoot();
            Path root = current.getParent() != null ? current.getParent() : current;
            String currentName = current.getFileName() != null ? current.getFileName().toString() : current.toString();

            List<String> names = new ArrayList<>();
            if (Files.exists(root) && Files.isDirectory(root)) {
                try (var stream = Files.list(root)) {
                    stream.filter(Files::isDirectory)
                        .map(path -> path.getFileName() != null ? path.getFileName().toString() : path.toString())
                        .sorted()
                        .forEach(names::add);
                }
            }

            ctx.json(Map.of(
                "currentPath", current.toString(),
                "rootPath", root.toString(),
                "currentName", currentName,
                "available", names,
                "metadata", projectContext.workspace().loadMetadata(),
                "devMode", devMode
            ));
        } catch (Exception e) {
            logger.error("Failed to get workspace info: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void selectWorkspace(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String name = json.has("name") ? json.get("name").asText() : null;

            if (name == null || name.isBlank()) {
                ctx.status(400).json(Map.of("error", "Workspace name is required"));
                return;
            }
            String trimmed = name.trim();
            if (".".equals(trimmed) || "..".equals(trimmed) || trimmed.contains("/") || trimmed.contains("\\")) {
                ctx.status(400).json(Map.of("error", "Invalid workspace name"));
                return;
            }

            Path current = projectContext.workspace().getWorkspaceRoot();
            Path root = current.getParent() != null ? current.getParent() : current;
            Path target = root.resolve(trimmed).normalize();

            if (!target.startsWith(root)) {
                ctx.status(400).json(Map.of("error", "Invalid workspace path"));
                return;
            }

            Files.createDirectories(target);
            AppConfig.persistWorkspaceSelection(root, trimmed);
            projectContext.switchWorkspace(target);

            logger.info("Workspace selection updated to " + target + " (live switch applied)");
            ctx.json(Map.of(
                "ok", true,
                "restartRequired", false,
                "targetPath", target.toString()
            ));
        } catch (Exception e) {
            logger.error("Failed to select workspace: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getMetadata(Context ctx) {
        try {
            ctx.json(projectContext.workspace().loadMetadata());
        } catch (Exception e) {
            logger.error("Failed to load workspace metadata: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void updateMetadata(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            var meta = projectContext.workspace().loadMetadata();

            if (json.has("displayName")) {
                String dn = json.get("displayName").asText("");
                meta.setDisplayName(trimToLength(dn, 80));
            }
            if (json.has("description")) {
                String desc = json.get("description").asText("");
                meta.setDescription(trimToLength(desc, 280));
            }
            if (json.has("icon")) {
                String icon = json.get("icon").asText("");
                meta.setIcon(trimToLength(icon, 16));
            }
            if (json.has("accentColor")) {
                String color = json.get("accentColor").asText("");
                meta.setAccentColor(trimToLength(color, 16));
            }

            var saved = projectContext.workspace().saveMetadata(meta);
            ctx.json(Map.of("ok", true, "metadata", saved));
        } catch (Exception e) {
            logger.error("Failed to update workspace metadata: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private String trimToLength(String value, int maxLen) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen);
    }
}
