package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.controllers.*;
import com.miniide.providers.ProviderChatService;
import com.miniide.providers.ProviderModelsService;
import com.miniide.settings.SettingsService;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.http.staticfiles.Location;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;

public class Main {

    private static final String VERSION = "1.0.0";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static AppLogger logger;
    private static MemoryDecayScheduler decayScheduler;
    private static DecayConfigStore decayConfigStore;

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
            WorkspaceService workspaceService = new WorkspaceService(config.getWorkspacePath());
            logger.info("Workspace initialized: " + config.getWorkspacePath());

            // Initialize agent registry
            AgentRegistry agentRegistry = new AgentRegistry(workspaceService.getWorkspaceRoot(), objectMapper);
            logger.info("Agent registry initialized");

            AgentEndpointRegistry agentEndpointRegistry = new AgentEndpointRegistry(workspaceService.getWorkspaceRoot(), objectMapper);
            logger.info("Agent endpoint registry initialized");

            // Initialize notification and issue services
            NotificationStore notificationStore = new NotificationStore();
            IssueMemoryService issueService = new IssueMemoryService();
            MemoryService memoryService = new MemoryService();
            PatchService patchService = new PatchService(workspaceService);
            logger.info("Notification and Issue services initialized");
            logger.info("Memory service initialized");
            decayConfigStore = new DecayConfigStore(objectMapper);

            // Initialize settings and provider services
            SettingsService settingsService = new SettingsService(AppConfig.getSettingsDirectory(), objectMapper);
            ProviderModelsService providerModelsService = new ProviderModelsService(objectMapper);
            ProviderChatService providerChatService = new ProviderChatService(objectMapper);
            logger.info("Settings services initialized");

            // Create and configure Javalin
            Javalin app = Javalin.create(cfg -> {
                cfg.jsonMapper(new JavalinJackson(objectMapper));
                cfg.staticFiles.add("/public", Location.CLASSPATH);
                cfg.http.defaultContentType = "application/json";
                // Increase max request size for avatar uploads (10MB)
                cfg.http.maxRequestSize = 10_000_000L;
            });

            // Start memory decay scheduler (runs in background)
            DecayConfigStore.DecayConfig defaultDecayConfig = buildDefaultDecayConfig();
            DecayConfigStore.DecayConfig decayConfig = decayConfigStore.loadOrDefault(defaultDecayConfig);
            MemoryService.DecaySettings decaySettings = decayConfig.toDecaySettings();
            long intervalMs = decayConfig.toIntervalMs();
            if (intervalMs <= 0) {
                intervalMs = defaultDecayConfig.toIntervalMs();
            }
            decayScheduler = new MemoryDecayScheduler(memoryService, notificationStore, intervalMs, decaySettings);
            decayScheduler.start();

            // Create and register controllers
            MemoryController memoryController = new MemoryController(memoryService, decayScheduler, decayConfigStore, objectMapper);
            List<Controller> controllers = List.of(
                new FileController(workspaceService, objectMapper),
                new WorkspaceController(workspaceService, objectMapper),
                new AgentController(agentRegistry, agentEndpointRegistry, objectMapper),
                new SettingsController(settingsService, providerModelsService, objectMapper),
                new NotificationController(notificationStore, objectMapper),
                new IssueController(issueService, objectMapper),
                memoryController,
                new PatchController(patchService, workspaceService, notificationStore, objectMapper),
                new ChatController(agentRegistry, agentEndpointRegistry, settingsService, providerChatService, memoryService, objectMapper)
            );

            controllers.forEach(c -> c.registerRoutes(app));
            logger.info("Registered " + controllers.size() + " controllers");

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
                decayScheduler.stop();
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

    private static void registerExceptionHandlers(Javalin app) {
        app.exception(FileNotFoundException.class, (e, ctx) -> {
            logger.warn("File not found: " + e.getMessage());
            ctx.status(404).json(errorBody(e));
        });

        app.exception(SecurityException.class, (e, ctx) -> {
            logger.warn("Security violation: " + e.getMessage());
            ctx.status(403).json(errorBody(e));
        });

        app.exception(Exception.class, (e, ctx) -> {
            logger.error("Unhandled exception: " + e.getMessage(), e);
            ctx.status(500).json(errorBody(e));
        });
    }

    /**
     * Safe error body helper that handles null exception messages.
     */
    private static Map<String, Object> errorBody(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            m = e.getClass().getSimpleName();
        }
        return Map.of("error", m);
    }

    private static DecayConfigStore.DecayConfig buildDefaultDecayConfig() {
        DecayConfigStore.DecayConfig config = new DecayConfigStore.DecayConfig();
        config.setIntervalMinutes(getIntervalMinutesFromEnv());
        config.setArchiveAfterDays(parseDaysEnv("CR_DECAY_ARCHIVE_DAYS", 14) / (24L * 60L * 60L * 1000L));
        config.setExpireAfterDays(parseDaysEnv("CR_DECAY_EXPIRE_DAYS", 30) / (24L * 60L * 60L * 1000L));
        config.setPruneExpiredR5(parseBoolEnv("CR_DECAY_PRUNE_R5", false));
        config.setCollectReport(parseBoolEnv("CR_DECAY_REPORT", false));
        config.setDryRun(parseBoolEnv("CR_DECAY_DRY_RUN", false));
        config.setNotifyOnRun(true);
        return config;
    }

    private static long getIntervalMinutesFromEnv() {
        String env = System.getenv("CR_DECAY_INTERVAL_MINUTES");
        if (env != null && !env.isBlank()) {
            try {
                long minutes = Long.parseLong(env.trim());
                if (minutes > 0) {
                    return minutes;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 6 * 60; // default 6h
    }

    private static long parseDaysEnv(String key, int defaultDays) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            try {
                int days = Integer.parseInt(env.trim());
                if (days > 0) {
                    return days * 24L * 60L * 60L * 1000L;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultDays * 24L * 60L * 60L * 1000L;
    }

    private static boolean parseBoolEnv(String key, boolean defaultValue) {
        String env = System.getenv(key);
        if (env == null || env.isBlank()) {
            return defaultValue;
        }
        String v = env.trim().toLowerCase();
        return v.equals("1") || v.equals("true") || v.equals("yes") || v.equals("on");
    }
}
