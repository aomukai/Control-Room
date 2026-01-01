package com.miniide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.Agent;
import com.miniide.models.Comment;
import com.miniide.models.Issue;
import com.miniide.models.Notification;
import com.miniide.models.RoleFreedomSettings;
import com.miniide.models.Notification.Level;
import com.miniide.models.Notification.Scope;
import com.miniide.models.Notification.Category;
import com.miniide.models.SceneSegment;
import com.miniide.models.SearchResult;
import com.miniide.providers.ProviderModelsService;
import com.miniide.settings.AgentKeysMetadataFile;
import com.miniide.settings.SecuritySettings;
import com.miniide.settings.SettingsService;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import io.javalin.http.staticfiles.Location;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Main {

    private static final String VERSION = "1.0.0";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static WorkspaceService workspaceService;
    private static AgentRegistry agentRegistry;
    private static AgentEndpointRegistry agentEndpointRegistry;
    private static NotificationStore notificationStore;
    private static IssueMemoryService issueService;
    private static AppLogger logger;
    private static SettingsService settingsService;
    private static ProviderModelsService providerModelsService;

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

            // Initialize workspace (FileService handles initial setup/seeding)
            new FileService(config.getWorkspacePath().toString());

            // Create WorkspaceService for all runtime file operations
            workspaceService = new WorkspaceService(config.getWorkspacePath());
            logger.info("Workspace initialized: " + config.getWorkspacePath());

            // Initialize agent registry
            agentRegistry = new AgentRegistry(workspaceService.getWorkspaceRoot(), objectMapper);
            logger.info("Agent registry initialized");

            agentEndpointRegistry = new AgentEndpointRegistry(workspaceService.getWorkspaceRoot(), objectMapper);
            logger.info("Agent endpoint registry initialized");

            // Initialize notification and issue services
            notificationStore = new NotificationStore();
            issueService = new IssueMemoryService();
            logger.info("Notification and Issue services initialized");

            // Initialize settings and provider services
            settingsService = new SettingsService(AppConfig.getSettingsDirectory(), objectMapper);
            providerModelsService = new ProviderModelsService(objectMapper);
            logger.info("Settings services initialized");

            // Create and configure Javalin
            Javalin app = Javalin.create(cfg -> {
                cfg.jsonMapper(new JavalinJackson(objectMapper));
                cfg.staticFiles.add("/public", Location.CLASSPATH);
                cfg.http.defaultContentType = "application/json";
                // Increase max request size for avatar uploads (10MB)
                cfg.http.maxRequestSize = 10_000_000L;
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
        app.post("/api/duplicate", Main::duplicateFile);
        app.get("/api/search", Main::search);
        app.get("/api/segments", Main::getSegments);
        app.post("/api/ai/chat", Main::aiChat);
        app.post("/api/workspace/open", Main::openWorkspace);
        app.post("/api/workspace/terminal", Main::openWorkspaceTerminal);
        app.post("/api/file/reveal", Main::revealFile);
        app.post("/api/file/open-folder", Main::openContainingFolder);
        app.get("/api/workspace/info", Main::getWorkspaceInfo);
        app.post("/api/workspace/select", Main::selectWorkspace);
        app.get("/api/agents", Main::getAgents);
        app.get("/api/agents/all", Main::getAllAgents);
        app.post("/api/agents", Main::createAgent);
        app.get("/api/agents/{id}", Main::getAgent);
        app.put("/api/agents/{id}", Main::updateAgent);
        app.put("/api/agents/{id}/status", Main::setAgentStatus);
        app.put("/api/agents/order", Main::reorderAgents);
        app.post("/api/agents/import", Main::importAgent);
        app.get("/api/agent-endpoints", Main::listAgentEndpoints);
        app.get("/api/agent-endpoints/{id}", Main::getAgentEndpoint);
        app.put("/api/agent-endpoints/{id}", Main::updateAgentEndpoint);

        // Role Settings endpoints
        app.get("/api/agents/role-settings", Main::getRoleSettings);
        app.get("/api/agents/role-settings/{role}", Main::getRoleSettingsByRole);
        app.put("/api/agents/role-settings/{role}", Main::saveRoleSettings);

        // Global Settings endpoints
        app.get("/api/settings/security", Main::getSecuritySettings);
        app.put("/api/settings/security", Main::updateSecuritySettings);
        app.post("/api/settings/security/unlock", Main::unlockSecurityVault);
        app.post("/api/settings/security/lock", Main::lockSecurityVault);
        app.get("/api/settings/keys", Main::listApiKeys);
        app.post("/api/settings/keys", Main::addApiKey);
        app.delete("/api/settings/keys/{provider}/{id}", Main::deleteApiKey);

        // Provider models
        app.get("/api/providers/models", Main::getProviderModels);

        // Notification endpoints
        app.get("/api/notifications", Main::getNotifications);
        app.get("/api/notifications/unread-count", Main::getUnreadCount);
        app.get("/api/notifications/{id}", Main::getNotification);
        app.post("/api/notifications", Main::createNotification);
        app.put("/api/notifications/{id}", Main::updateNotification);
        app.delete("/api/notifications/{id}", Main::deleteNotification);
        app.post("/api/notifications/mark-all-read", Main::markAllNotificationsRead);
        app.post("/api/notifications/clear", Main::clearNotifications);

        // Issue endpoints
        app.get("/api/issues", Main::getIssues);
        app.get("/api/issues/{id}", Main::getIssue);
        app.post("/api/issues", Main::createIssue);
        app.put("/api/issues/{id}", Main::updateIssue);
        app.delete("/api/issues/{id}", Main::deleteIssue);
        app.post("/api/issues/{id}/comments", Main::addComment);
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
            ctx.json(workspaceService.getTree(""));
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
            String content = workspaceService.readFile(path);
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
            workspaceService.writeFile(path, content);
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

    private static void deleteFile(Context ctx) {
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

    private static void renameFile(Context ctx) {
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

    private static void duplicateFile(Context ctx) {
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

    private static void search(Context ctx) {
        try {
            String query = ctx.queryParam("q");
            String pattern = ctx.queryParam("pattern"); // optional glob pattern
            List<SearchResult> results = workspaceService.search(query, pattern);
            ctx.json(results);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void getSegments(Context ctx) {
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

    private static void getAgents(Context ctx) {
        try {
            ctx.json(agentRegistry.listEnabledAgents());
        } catch (Exception e) {
            logger.error("Failed to list agents: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void getAllAgents(Context ctx) {
        try {
            ctx.json(agentRegistry.listAllAgents());
        } catch (Exception e) {
            logger.error("Failed to list agents: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void createAgent(Context ctx) {
        try {
            Agent agent = ctx.bodyAsClass(Agent.class);
            Agent created = agentRegistry.createAgent(agent);
            if (created.getEndpoint() != null) {
                agentEndpointRegistry.upsertEndpoint(created.getId(), created.getEndpoint());
            }
            ctx.status(201).json(created);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (BadRequestResponse e) {
            ctx.status(400).json(Map.of("error", "Invalid agent payload"));
        } catch (Exception e) {
            logger.error("Failed to create agent: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void getAgent(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            Agent agent = agentRegistry.getAgent(id);
            if (agent == null) {
                ctx.status(404).json(Map.of("error", "Agent not found: " + id));
                return;
            }
            ctx.json(agent);
        } catch (Exception e) {
            logger.error("Failed to get agent: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void updateAgent(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            Agent updates = ctx.bodyAsClass(Agent.class);
            Agent updated = agentRegistry.updateAgent(id, updates);
            if (updated == null) {
                ctx.status(404).json(Map.of("error", "Agent not found: " + id));
                return;
            }
            if (updates.getEndpoint() != null) {
                agentEndpointRegistry.upsertEndpoint(id, updates.getEndpoint());
            }
            ctx.json(updated);
        } catch (Exception e) {
            logger.error("Failed to update agent: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void setAgentStatus(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            JsonNode json = objectMapper.readTree(ctx.body());
            boolean enabled = json.has("enabled") && json.get("enabled").asBoolean();
            Agent updated = agentRegistry.setEnabled(id, enabled);
            if (updated == null) {
                ctx.status(404).json(Map.of("error", "Agent not found: " + id));
                return;
            }
            ctx.json(updated);
        } catch (Exception e) {
            logger.error("Failed to update agent status: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void reorderAgents(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            if (!json.has("order") || !json.get("order").isArray()) {
                ctx.status(400).json(Map.of("error", "order array required"));
                return;
            }
            List<String> order = new ArrayList<>();
            json.get("order").forEach(node -> order.add(node.asText()));
            agentRegistry.reorderAgents(order);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            logger.error("Failed to reorder agents: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void importAgent(Context ctx) {
        try {
            Agent agent = ctx.bodyAsClass(Agent.class);
            Agent imported = agentRegistry.importAgent(agent);
            ctx.status(201).json(imported);
        } catch (Exception e) {
            logger.error("Failed to import agent: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void listAgentEndpoints(Context ctx) {
        ctx.json(agentEndpointRegistry.listEndpoints());
    }

    private static void getAgentEndpoint(Context ctx) {
        String id = ctx.pathParam("id");
        var endpoint = agentEndpointRegistry.getEndpoint(id);
        if (endpoint == null) {
            ctx.status(404).json(Map.of("error", "Endpoint not found for agent: " + id));
            return;
        }
        ctx.json(endpoint);
    }

    private static void updateAgentEndpoint(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            var config = ctx.bodyAsClass(com.miniide.models.AgentEndpointConfig.class);
            var saved = agentEndpointRegistry.upsertEndpoint(id, config);
            if (saved == null) {
                ctx.status(400).json(Map.of("error", "Invalid agent endpoint payload"));
                return;
            }
            ctx.json(saved);
        } catch (Exception e) {
            logger.error("Failed to update agent endpoint: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    // ===== Settings Endpoints =====

    private static void getSecuritySettings(Context ctx) {
        SecuritySettings settings = settingsService.getSecuritySettings();
        ctx.json(Map.of(
            "keysSecurityMode", settings.getKeysSecurityMode(),
            "vaultUnlocked", settingsService.isVaultUnlocked()
        ));
    }

    private static void updateSecuritySettings(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String mode = json.has("keysSecurityMode") ? json.get("keysSecurityMode").asText() : null;
            String password = json.has("password") ? json.get("password").asText() : null;
            SecuritySettings updated = settingsService.updateSecurityMode(mode, password);
            ctx.json(Map.of("keysSecurityMode", updated.getKeysSecurityMode()));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to update security settings: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void unlockSecurityVault(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String password = json.has("password") ? json.get("password").asText() : null;
            if (password == null || password.isBlank()) {
                ctx.status(400).json(Map.of("error", "Password is required"));
                return;
            }
            settingsService.unlockVault(password);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            logger.error("Failed to unlock key vault: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void lockSecurityVault(Context ctx) {
        settingsService.lockVault();
        ctx.json(Map.of("ok", true));
    }

    private static void listApiKeys(Context ctx) {
        try {
            AgentKeysMetadataFile metadata = settingsService.listKeyMetadata();
            ctx.json(metadata);
        } catch (Exception e) {
            logger.error("Failed to list API keys: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void addApiKey(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String provider = json.has("provider") ? json.get("provider").asText() : null;
            String label = json.has("label") ? json.get("label").asText() : null;
            String key = json.has("key") ? json.get("key").asText() : null;
            String id = json.has("id") ? json.get("id").asText() : null;
            String password = json.has("password") ? json.get("password").asText() : null;
            String keyRef = settingsService.addKey(provider, label, key, id, password);
            ctx.json(Map.of("keyRef", keyRef));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to add API key: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void deleteApiKey(Context ctx) {
        try {
            String provider = ctx.pathParam("provider");
            String id = ctx.pathParam("id");
            JsonNode json = ctx.body().isBlank() ? null : objectMapper.readTree(ctx.body());
            String password = json != null && json.has("password") ? json.get("password").asText() : null;
            settingsService.deleteKey(provider, id, password);
            ctx.json(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to delete API key: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void getProviderModels(Context ctx) {
        try {
            String provider = ctx.queryParam("provider");
            String baseUrl = ctx.queryParam("baseUrl");
            String keyRef = ctx.queryParam("keyRef");
            String apiKey = null;
            if (keyRef != null && !keyRef.isBlank()) {
                apiKey = settingsService.resolveKey(keyRef);
            }
            if (provider == null || provider.isBlank()) {
                ctx.status(400).json(Map.of("error", "provider is required"));
                return;
            }
            ctx.json(providerModelsService.fetchModels(provider, apiKey, baseUrl));
        } catch (IllegalStateException e) {
            ctx.status(401).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to fetch provider models: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    // =============== Role Settings Endpoints ===============

    private static void getRoleSettings(Context ctx) {
        try {
            ctx.json(agentRegistry.listRoleSettings());
        } catch (Exception e) {
            logger.error("Failed to list role settings: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void getRoleSettingsByRole(Context ctx) {
        try {
            String role = ctx.pathParam("role");
            RoleFreedomSettings settings = agentRegistry.getRoleSettings(role);
            if (settings == null) {
                // Return default settings for new/unknown role
                RoleFreedomSettings defaults = new RoleFreedomSettings();
                defaults.setRole(role);
                defaults.setTemplate("balanced");
                defaults.setFreedomLevel("semi-autonomous");
                defaults.setNotifyUserOn(List.of("question", "conflict", "completion", "error"));
                defaults.setMaxActionsPerSession(10);
                defaults.setRoleCharter("");
                defaults.setCollaborationGuidance("");
                defaults.setToolAndSafetyNotes("");
                ctx.json(defaults);
                return;
            }
            ctx.json(settings);
        } catch (Exception e) {
            logger.error("Failed to get role settings: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void saveRoleSettings(Context ctx) {
        try {
            String role = ctx.pathParam("role");
            RoleFreedomSettings settings = ctx.bodyAsClass(RoleFreedomSettings.class);
            settings.setRole(role); // Ensure role matches path
            RoleFreedomSettings saved = agentRegistry.saveRoleSettings(settings);
            ctx.json(saved);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to save role settings: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void openWorkspace(Context ctx) {
        try {
            String workspacePath = workspaceService.getWorkspaceRoot().toString();
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

    private static void getWorkspaceInfo(Context ctx) {
        try {
            Path current = workspaceService.getWorkspaceRoot();
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
                "available", names
            ));
        } catch (Exception e) {
            logger.error("Failed to get workspace info: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void selectWorkspace(Context ctx) {
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

            Path current = workspaceService.getWorkspaceRoot();
            Path root = current.getParent() != null ? current.getParent() : current;
            Path target = root.resolve(trimmed).normalize();

            if (!target.startsWith(root)) {
                ctx.status(400).json(Map.of("error", "Invalid workspace path"));
                return;
            }

            Files.createDirectories(target);
            AppConfig.persistWorkspaceSelection(root, trimmed);

            logger.info("Workspace selection updated to " + target + " (restart required)");
            ctx.json(Map.of(
                "ok", true,
                "restartRequired", true,
                "targetPath", target.toString()
            ));
        } catch (Exception e) {
            logger.error("Failed to select workspace: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
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

            // Use WorkspaceService.resolvePath for safety checks (no path traversal)
            Path absPath = workspaceService.resolvePath(path);

            if (!Files.exists(absPath)) {
                ctx.json(Map.of("ok", false, "error", "File not found: " + path));
                return;
            }

            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                // Windows: explorer /select,"<path>"
                pb = new ProcessBuilder("explorer.exe", "/select,", absPath.toString());
            } else if (os.contains("mac")) {
                // macOS: open -R "<path>"
                pb = new ProcessBuilder("open", "-R", absPath.toString());
            } else {
                // Linux: xdg-open parent directory (reveal not standardized)
                Path parentDir = absPath.getParent();
                pb = new ProcessBuilder("xdg-open", parentDir.toString());
            }

            try {
                pb.start();
                logger.info("Revealed file in explorer: " + path);
                ctx.json(Map.of("ok", true));
            } catch (IOException io) {
                logger.warn("Reveal failed, opening containing folder: " + io.getMessage());
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
                logger.info("Opened containing folder fallback: " + parentDir.toString());
                ctx.json(Map.of("ok", true, "fallback", "open-folder"));
            }
        } catch (SecurityException e) {
            logger.warn("Security violation in reveal: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to reveal file: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    private static void openWorkspaceTerminal(Context ctx) {
        try {
            String workspacePath = workspaceService.getWorkspaceRoot().toString();
            String os = System.getProperty("os.name").toLowerCase();

            ProcessBuilder pb = null;
            String terminalUsed = null;

            if (os.contains("win")) {
                // Windows: Try Windows Terminal first, fallback to cmd.exe
                if (isCommandAvailable("wt.exe")) {
                    pb = new ProcessBuilder("wt.exe", "-d", workspacePath);
                    terminalUsed = "Windows Terminal";
                } else {
                    pb = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", "cd", "/d", workspacePath);
                    terminalUsed = "Command Prompt";
                }
            } else if (os.contains("mac")) {
                // macOS: Open Terminal.app at workspace root
                String script = "tell application \"Terminal\" to do script \"cd '" + workspacePath.replace("'", "'\\''") + "'\"";
                pb = new ProcessBuilder("osascript", "-e", script);
                terminalUsed = "Terminal.app";
            } else {
                // Linux: Try common terminal emulators in order of preference
                String[] terminals = {"x-terminal-emulator", "gnome-terminal", "konsole", "xfce4-terminal", "xterm"};
                for (String terminal : terminals) {
                    if (isCommandAvailable(terminal)) {
                        if (terminal.equals("gnome-terminal") || terminal.equals("xfce4-terminal")) {
                            pb = new ProcessBuilder(terminal, "--working-directory=" + workspacePath);
                        } else if (terminal.equals("konsole")) {
                            pb = new ProcessBuilder(terminal, "--workdir", workspacePath);
                        } else {
                            // x-terminal-emulator and xterm: use cd in shell
                            pb = new ProcessBuilder(terminal, "-e", "sh", "-c", "cd '" + workspacePath + "' && exec $SHELL");
                        }
                        terminalUsed = terminal;
                        break;
                    }
                }

                if (pb == null) {
                    logger.warn("No terminal emulator found on Linux");
                    ctx.json(Map.of("ok", false, "error", "No terminal emulator found. Please install one of: gnome-terminal, konsole, xfce4-terminal, or xterm"));
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

    private static boolean isCommandAvailable(String command) {
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

    private static void openContainingFolder(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String path = json.has("path") ? json.get("path").asText() : null;

            if (path == null || path.isEmpty()) {
                ctx.json(Map.of("ok", false, "error", "Path is required"));
                return;
            }

            // Use WorkspaceService.resolvePath for safety checks (no path traversal)
            Path absPath = workspaceService.resolvePath(path);

            if (!Files.exists(absPath)) {
                ctx.json(Map.of("ok", false, "error", "File not found: " + path));
                return;
            }

            // If a folder is passed, open it directly; otherwise open the parent folder.
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
            logger.info("Opened containing folder: " + targetDir.toString());
            ctx.json(Map.of("ok", true));
        } catch (SecurityException e) {
            logger.warn("Security violation in open folder: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to open containing folder: " + e.getMessage());
            ctx.json(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    // ===== Notification Endpoints =====

    private static void getNotifications(Context ctx) {
        try {
            String levelParam = ctx.queryParam("level");
            String scopeParam = ctx.queryParam("scope");

            Set<Level> levels = null;
            Set<Scope> scopes = null;

            if (levelParam != null && !levelParam.isBlank()) {
                levels = new HashSet<>();
                for (String l : levelParam.split(",")) {
                    Level level = Level.fromString(l.trim());
                    if (level != null) {
                        levels.add(level);
                    }
                }
            }

            if (scopeParam != null && !scopeParam.isBlank()) {
                scopes = new HashSet<>();
                for (String s : scopeParam.split(",")) {
                    Scope scope = Scope.fromString(s.trim());
                    if (scope != null) {
                        scopes.add(scope);
                    }
                }
            }

            List<Notification> notifications;
            if (levels != null || scopes != null) {
                notifications = notificationStore.getByLevelAndScope(levels, scopes);
            } else {
                notifications = notificationStore.getAll();
            }

            ctx.json(notifications);
        } catch (Exception e) {
            logger.error("Error getting notifications: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void getNotification(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            List<Notification> all = notificationStore.getAll();
            Optional<Notification> found = all.stream()
                .filter(n -> id.equals(n.getId()))
                .findFirst();

            if (found.isPresent()) {
                ctx.json(found.get());
            } else {
                ctx.status(404).json(Map.of("error", "Notification not found: " + id));
            }
        } catch (Exception e) {
            logger.error("Error getting notification: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void getUnreadCount(Context ctx) {
        try {
            String scopeParam = ctx.queryParam("scope");
            int count;

            if (scopeParam != null && !scopeParam.isBlank()) {
                Scope scope = Scope.fromString(scopeParam);
                if (scope != null) {
                    count = notificationStore.getUnreadCountByScope(scope);
                } else {
                    count = notificationStore.getUnreadCount();
                }
            } else {
                count = notificationStore.getUnreadCount();
            }

            ctx.json(Map.of("unreadCount", count));
        } catch (Exception e) {
            logger.error("Error getting unread count: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void createNotification(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());

            String message = json.has("message") ? json.get("message").asText() : null;
            if (message == null || message.isBlank()) {
                ctx.status(400).json(Map.of("error", "Message is required"));
                return;
            }

            Level level = Level.INFO;
            if (json.has("level")) {
                Level parsed = Level.fromString(json.get("level").asText());
                if (parsed != null) level = parsed;
            }

            Scope scope = Scope.GLOBAL;
            if (json.has("scope")) {
                Scope parsed = Scope.fromString(json.get("scope").asText());
                if (parsed != null) scope = parsed;
            }

            Category category = Category.INFO;
            if (json.has("category")) {
                Category parsed = Category.fromString(json.get("category").asText());
                if (parsed != null) category = parsed;
            }

            String details = json.has("details") ? json.get("details").asText() : null;
            boolean persistent = json.has("persistent") && json.get("persistent").asBoolean();
            String actionLabel = json.has("actionLabel") ? json.get("actionLabel").asText() : null;
            Object actionPayload = json.has("actionPayload") ? json.get("actionPayload") : null;
            String source = json.has("source") ? json.get("source").asText() : null;

            Notification notification = notificationStore.push(level, scope, message, details,
                category, persistent, actionLabel, actionPayload, source);

            logger.info("Notification created via API: " + notification.getId());
            ctx.status(201).json(notification);
        } catch (Exception e) {
            logger.error("Error creating notification: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void updateNotification(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            JsonNode json = objectMapper.readTree(ctx.body());

            // Currently only supports marking as read
            if (json.has("read") && json.get("read").asBoolean()) {
                notificationStore.markRead(id);
                logger.info("Notification marked as read: " + id);
                ctx.json(Map.of("success", true, "message", "Notification marked as read"));
            } else {
                ctx.json(Map.of("success", true, "message", "No changes made"));
            }
        } catch (Exception e) {
            logger.error("Error updating notification: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void deleteNotification(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            boolean deleted = notificationStore.delete(id);

            if (deleted) {
                logger.info("Notification deleted: " + id);
                ctx.json(Map.of("success", true, "message", "Notification deleted"));
            } else {
                ctx.status(404).json(Map.of("error", "Notification not found: " + id));
            }
        } catch (Exception e) {
            logger.error("Error deleting notification: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void markAllNotificationsRead(Context ctx) {
        try {
            notificationStore.markAllRead();
            logger.info("All notifications marked as read");
            ctx.json(Map.of("success", true, "message", "All notifications marked as read"));
        } catch (Exception e) {
            logger.error("Error marking all notifications read: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void clearNotifications(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            boolean clearAll = json.has("all") && json.get("all").asBoolean();

            if (clearAll) {
                notificationStore.clearAll();
                logger.info("All notifications cleared");
                ctx.json(Map.of("success", true, "message", "All notifications cleared"));
            } else {
                notificationStore.clearNonErrors();
                logger.info("Non-error notifications cleared");
                ctx.json(Map.of("success", true, "message", "Non-error notifications cleared"));
            }
        } catch (Exception e) {
            logger.error("Error clearing notifications: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    // ===== Issue Endpoints =====

    private static void getIssues(Context ctx) {
        try {
            String tag = ctx.queryParam("tag");
            String assignedTo = ctx.queryParam("assignedTo");
            String status = ctx.queryParam("status");

            List<Issue> issues;

            if (tag != null && !tag.isBlank()) {
                issues = issueService.listIssuesByTag(tag);
            } else if (assignedTo != null && !assignedTo.isBlank()) {
                issues = issueService.listIssuesByAssignee(assignedTo);
            } else {
                issues = issueService.listIssues();
            }

            // Filter by status if provided
            if (status != null && !status.isBlank()) {
                List<Issue> filtered = new ArrayList<>();
                for (Issue issue : issues) {
                    if (status.equalsIgnoreCase(issue.getStatus())) {
                        filtered.add(issue);
                    }
                }
                issues = filtered;
            }

            ctx.json(issues);
        } catch (Exception e) {
            logger.error("Error getting issues: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void getIssue(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Optional<Issue> issue = issueService.getIssue(id);

            if (issue.isPresent()) {
                ctx.json(issue.get());
            } else {
                ctx.status(404).json(Map.of("error", "Issue not found: #" + id));
            }
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
        } catch (Exception e) {
            logger.error("Error getting issue: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void createIssue(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());

            String title = json.has("title") ? json.get("title").asText() : null;
            if (title == null || title.isBlank()) {
                ctx.status(400).json(Map.of("error", "Title is required"));
                return;
            }

            String body = json.has("body") ? json.get("body").asText() : "";
            String openedBy = json.has("openedBy") ? json.get("openedBy").asText() : "user";
            String assignedTo = json.has("assignedTo") ? json.get("assignedTo").asText() : null;
            String priority = json.has("priority") ? json.get("priority").asText() : "normal";

            List<String> tags = new ArrayList<>();
            if (json.has("tags") && json.get("tags").isArray()) {
                for (JsonNode tagNode : json.get("tags")) {
                    tags.add(tagNode.asText());
                }
            }

            Issue issue = issueService.createIssue(title, body, openedBy, assignedTo, tags, priority);
            logger.info("Issue created via API: #" + issue.getId() + " - " + issue.getTitle());
            ctx.status(201).json(issue);
        } catch (Exception e) {
            logger.error("Error creating issue: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void updateIssue(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Optional<Issue> existing = issueService.getIssue(id);

            if (existing.isEmpty()) {
                ctx.status(404).json(Map.of("error", "Issue not found: #" + id));
                return;
            }

            JsonNode json = objectMapper.readTree(ctx.body());
            Issue issue = existing.get();

            if (json.has("title")) {
                issue.setTitle(json.get("title").asText());
            }
            if (json.has("body")) {
                issue.setBody(json.get("body").asText());
            }
            if (json.has("openedBy")) {
                issue.setOpenedBy(json.get("openedBy").asText());
            }
            if (json.has("assignedTo")) {
                issue.setAssignedTo(json.get("assignedTo").asText());
            }
            if (json.has("priority")) {
                issue.setPriority(json.get("priority").asText());
            }
            if (json.has("status")) {
                issue.setStatus(json.get("status").asText());
            }
            if (json.has("tags") && json.get("tags").isArray()) {
                List<String> tags = new ArrayList<>();
                for (JsonNode tagNode : json.get("tags")) {
                    tags.add(tagNode.asText());
                }
                issue.setTags(tags);
            }

            Issue updated = issueService.updateIssue(issue);
            logger.info("Issue updated via API: #" + updated.getId());
            ctx.json(updated);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating issue: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void deleteIssue(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            boolean deleted = issueService.deleteIssue(id);

            if (deleted) {
                logger.info("Issue deleted via API: #" + id);
                ctx.json(Map.of("success", true, "message", "Issue #" + id + " deleted"));
            } else {
                ctx.status(404).json(Map.of("error", "Issue not found: #" + id));
            }
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
        } catch (Exception e) {
            logger.error("Error deleting issue: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private static void addComment(Context ctx) {
        try {
            int issueId = Integer.parseInt(ctx.pathParam("id"));
            JsonNode json = objectMapper.readTree(ctx.body());

            String author = json.has("author") ? json.get("author").asText() : "user";
            String body = json.has("body") ? json.get("body").asText() : null;

            if (body == null || body.isBlank()) {
                ctx.status(400).json(Map.of("error", "Comment body is required"));
                return;
            }

            Comment.CommentAction action = null;
            if (json.has("action") && json.get("action").isObject()) {
                JsonNode actionNode = json.get("action");
                String type = actionNode.has("type") ? actionNode.get("type").asText() : null;
                String details = actionNode.has("details") ? actionNode.get("details").asText() : null;
                if (type != null) {
                    action = new Comment.CommentAction(type, details);
                }
            }

            Comment comment = issueService.addComment(issueId, author, body, action);
            logger.info("Comment added to Issue #" + issueId + " by " + author);
            ctx.status(201).json(comment);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
        } catch (IllegalArgumentException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error adding comment: " + e.getMessage());
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }
}
