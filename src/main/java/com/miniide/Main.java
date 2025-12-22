package com.miniide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.SearchResult;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import io.javalin.http.staticfiles.Location;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;

public class Main {

    private static final String VERSION = "1.0.0";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static FileService fileService;
    private static AppLogger logger;

    public static void main(String[] args) {
        try {
            // Parse configuration from args and environment
            AppConfig config = new AppConfig.Builder()
                    .parseArgs(args)
                    .build();

            // Initialize logging
            AppLogger.initialize(config.getLogPath(), config.isDevMode());
            logger = AppLogger.get();

            // Print startup banner
            printBanner(config);

            // Initialize file service with workspace
            fileService = new FileService(config.getWorkspacePath().toString());
            logger.info("Workspace initialized: " + config.getWorkspacePath());

            // Create and configure Javalin
            Javalin app = Javalin.create(cfg -> {
                cfg.jsonMapper(new JavalinJackson(objectMapper));
                cfg.staticFiles.add("/public", Location.CLASSPATH);
                cfg.http.defaultContentType = "application/json";
            });

            // Register API routes
            registerRoutes(app);

            // Register exception handlers
            registerExceptionHandlers(app);

            // Start the server
            app.start(config.getPort());

            String url = "http://localhost:" + config.getPort() + "/";
            logger.info("Server started on " + url);
            logger.console("");
            logger.console("  Listening on " + url);
            logger.console("  Workspace: " + config.getWorkspacePath());
            logger.console("  Log file: " + config.getLogPath());
            logger.console("");
            logger.console("========================================");
            logger.console("  Press Ctrl+C to stop");
            logger.console("========================================");

            // Open browser (with small delay to ensure server is ready)
            BrowserLauncher.openBrowserDelayed(url, 500);

            // Add shutdown hook for clean shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down...");
                app.stop();
                logger.close();
            }));

        } catch (Exception e) {
            System.err.println("Failed to start Control Room: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printBanner(AppConfig config) {
        logger.console("");
        logger.console("========================================");
        logger.console("  Control Room v" + VERSION);
        logger.console("========================================");
        logger.console("  Starting server...");
        if (config.isDevMode()) {
            logger.console("  Mode: Development");
        }
    }

    private static void registerRoutes(Javalin app) {
        app.get("/api/tree", Main::getTree);
        app.get("/api/file", Main::getFile);
        app.put("/api/file", Main::putFile);
        app.post("/api/file", Main::createFile);
        app.delete("/api/file", Main::deleteFile);
        app.post("/api/rename", Main::renameFile);
        app.get("/api/search", Main::search);
        app.post("/api/ai/chat", Main::aiChat);
        app.post("/api/workspace/open", Main::openWorkspace);
        app.post("/api/file/reveal", Main::revealFile);
    }

    private static void registerExceptionHandlers(Javalin app) {
        app.exception(FileNotFoundException.class, (e, ctx) -> {
            logger.warn("File not found: " + e.getMessage());
            ctx.status(404).json(Map.of("error", e.getMessage()));
        });

        app.exception(SecurityException.class, (e, ctx) -> {
            logger.warn("Security violation: " + e.getMessage());
            ctx.status(403).json(Map.of("error", e.getMessage()));
        });

        app.exception(Exception.class, (e, ctx) -> {
            logger.error("Unhandled exception: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        });
    }

    private static void getTree(Context ctx) {
        try {
            ctx.json(fileService.getTree());
        } catch (Exception e) {
            logger.error("Error getting tree: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void getFile(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }
            String content = fileService.readFile(path);
            ctx.contentType("text/plain; charset=utf-8").result(content);
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void putFile(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }
            String content = ctx.body();
            fileService.writeFile(path, content);
            logger.info("File saved: " + path);
            ctx.json(Map.of("success", true, "message", "File saved: " + path));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void createFile(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String path = json.has("path") ? json.get("path").asText() : null;
            String type = json.has("type") ? json.get("type").asText() : "file";
            String initialContent = json.has("initialContent") ? json.get("initialContent").asText() : "";

            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path is required"));
                return;
            }

            fileService.createFileOrFolder(path, type, initialContent);
            logger.info("Created " + type + ": " + path);
            ctx.json(Map.of("success", true, "message", "Created: " + path));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void deleteFile(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isEmpty()) {
                ctx.status(400).json(Map.of("error", "Path parameter required"));
                return;
            }
            fileService.deleteFileOrFolder(path);
            logger.info("Deleted: " + path);
            ctx.json(Map.of("success", true, "message", "Deleted: " + path));
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void renameFile(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String from = json.has("from") ? json.get("from").asText() : null;
            String to = json.has("to") ? json.get("to").asText() : null;

            if (from == null || to == null) {
                ctx.status(400).json(Map.of("error", "Both 'from' and 'to' paths required"));
                return;
            }

            fileService.rename(from, to);
            logger.info("Renamed: " + from + " -> " + to);
            ctx.json(Map.of("success", true, "message", "Renamed: " + from + " -> " + to));
        } catch (FileNotFoundException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void search(Context ctx) {
        try {
            String query = ctx.queryParam("q");
            List<SearchResult> results = fileService.search(query);
            ctx.json(results);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void aiChat(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String message = json.has("message") ? json.get("message").asText() : "";

            String response = generateStubResponse(message);

            ctx.json(Map.of(
                "role", "assistant",
                "content", response
            ));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static String generateStubResponse(String message) {
        String lower = message.toLowerCase();

        if (lower.contains("hello") || lower.contains("hi")) {
            return "Hello! I'm your AI writing assistant. I can help you with your creative writing project. " +
                   "Try asking me about character development, plot ideas, or scene descriptions!";
        }

        if (lower.contains("character") || lower.contains("mara")) {
            return "I see you're working on character development! Mara Chen sounds like a compelling protagonist. " +
                   "Some suggestions:\n" +
                   "- Consider adding a personal flaw that creates internal conflict\n" +
                   "- Her past as a detective could inform her investigation methods\n" +
                   "- Think about her relationship with the mysterious Stranger";
        }

        if (lower.contains("scene") || lower.contains("plot")) {
            return "For your scene, consider these elements:\n" +
                   "- **Setting**: Use sensory details (sounds, smells, textures)\n" +
                   "- **Tension**: What's at stake for the characters?\n" +
                   "- **Dialogue**: Keep it natural, with subtext\n" +
                   "- **Pacing**: Vary sentence length for rhythm";
        }

        if (lower.contains("help")) {
            return "I can assist you with:\n" +
                   "- Developing characters and their motivations\n" +
                   "- Crafting compelling dialogue\n" +
                   "- Building your world and setting\n" +
                   "- Plotting story arcs\n" +
                   "- Providing writing prompts and ideas\n\n" +
                   "What would you like to work on?";
        }

        if (lower.contains("write") || lower.contains("draft")) {
            return "I'd be happy to help you draft content! Here's a quick writing prompt:\n\n" +
                   "*The rain had stopped, but Mara knew the real storm was just beginning. " +
                   "She checked her holster, took a deep breath, and pushed open the warehouse door...*\n\n" +
                   "Feel free to modify this or ask for alternatives!";
        }

        return "That's an interesting thought! As your writing assistant, I'm here to help develop your story. " +
               "I can see you're working on a noir-style narrative set in Neo-Seattle. " +
               "Would you like me to help with character development, plot structure, or scene descriptions?";
    }

    private static void openWorkspace(Context ctx) {
        try {
            String workspacePath = fileService.getWorkspaceRoot().toString();
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

    private static void revealFile(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String path = json.has("path") ? json.get("path").asText() : null;

            if (path == null || path.isEmpty()) {
                ctx.json(Map.of("ok", false, "error", "Path is required"));
                return;
            }

            // Use FileService.resolvePath for safety checks (no path traversal)
            java.nio.file.Path absPath = fileService.resolvePath(path);

            if (!java.nio.file.Files.exists(absPath)) {
                ctx.json(Map.of("ok", false, "error", "File not found: " + path));
                return;
            }

            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                // Windows: explorer /select,"<path>"
                pb = new ProcessBuilder("explorer.exe", "/select," + absPath.toString());
            } else if (os.contains("mac")) {
                // macOS: open -R "<path>"
                pb = new ProcessBuilder("open", "-R", absPath.toString());
            } else {
                // Linux: xdg-open parent directory (reveal not standardized)
                java.nio.file.Path parentDir = absPath.getParent();
                pb = new ProcessBuilder("xdg-open", parentDir.toString());
            }

            pb.start();
            logger.info("Revealed file in explorer: " + path);
            ctx.json(Map.of("ok", true));
        } catch (SecurityException e) {
            logger.warn("Security violation in reveal: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to reveal file: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
