package com.miniide;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Application configuration handling platform-specific paths and settings.
 */
public class AppConfig {

    private static final String APP_NAME = "Control Room";
    private static final String CONFIG_FILE_NAME = "config.properties";

    private final Path workspacePath;
    private final Path logPath;
    private final int port;
    private final boolean devMode;

    private AppConfig(Path workspacePath, Path logPath, int port, boolean devMode) {
        this.workspacePath = workspacePath;
        this.logPath = logPath;
        this.port = port;
        this.devMode = devMode;
    }

    public Path getWorkspacePath() {
        return workspacePath;
    }

    public Path getLogPath() {
        return logPath;
    }

    public int getPort() {
        return port;
    }

    public boolean isDevMode() {
        return devMode;
    }

    /**
     * Get the default workspace root path based on the current working directory.
     * Default: ./workspace
     */
    public static Path getDefaultWorkspaceRoot() {
        return Paths.get("").toAbsolutePath().normalize().resolve("workspace");
    }

    /**
     * Get the log directory path based on the operating system.
     * Windows: %APPDATA%\Control-Room\logs
     * macOS: ~/Library/Logs/Control-Room
     * Linux: ~/.local/share/Control-Room/logs
     */
    public static Path getLogDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null) {
                appData = Paths.get(userHome, "AppData", "Roaming").toString();
            }
            return Paths.get(appData, APP_NAME, "logs");
        } else if (os.contains("mac")) {
            return Paths.get(userHome, "Library", "Logs", APP_NAME);
        } else {
            return Paths.get(userHome, ".local", "share", APP_NAME, "logs");
        }
    }

    /**
     * Get the app config directory path based on the operating system.
     * Windows: %APPDATA%\Control-Room
     * macOS: ~/Library/Logs/Control-Room (shared with logs)
     * Linux: ~/.local/share/Control-Room
     */
    public static Path getConfigDirectory() {
        return getLogDirectory().getParent();
    }

    /**
     * Get the app settings directory path.
     * Windows: %APPDATA%\Control-Room\settings
     * macOS: ~/Library/Logs/Control-Room/settings
     * Linux: ~/.local/share/Control-Room/settings
     */
    public static Path getSettingsDirectory() {
        return getConfigDirectory().resolve("settings");
    }

    /**
     * Persist workspace selection for next launch.
     */
    public static void persistWorkspaceSelection(Path workspaceRoot, String workspaceName) throws IOException {
        Path configDir = getConfigDirectory();
        Files.createDirectories(configDir);
        Path configPath = configDir.resolve(CONFIG_FILE_NAME);

        Properties props = new Properties();
        if (Files.exists(configPath)) {
            try (var input = Files.newInputStream(configPath)) {
                props.load(input);
            }
        }

        props.remove("workspace.path");

        if (workspaceRoot != null) {
            props.setProperty("workspace.root", workspaceRoot.toString());
        }
        if (workspaceName != null && !workspaceName.isBlank()) {
            props.setProperty("workspace.name", workspaceName);
        }

        try (var output = Files.newOutputStream(configPath)) {
            props.store(output, "Control Room configuration");
        }
    }

    /**
     * Get the log file path.
     */
    public static Path getLogFilePath() {
        return getLogDirectory().resolve("control-room.log");
    }

    /**
     * Find an available port, starting with the preferred port.
     * If the preferred port is in use, finds the next available port.
     */
    public static int findAvailablePort(int preferredPort) {
        // First try the preferred port
        if (isPortAvailable(preferredPort)) {
            return preferredPort;
        }

        // Try to find any available port
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            // Fallback: try a range of ports
            for (int port = preferredPort + 1; port < preferredPort + 100; port++) {
                if (isPortAvailable(port)) {
                    return port;
                }
            }
        }

        // Last resort: return the preferred port and let it fail later with a clear error
        return preferredPort;
    }

    /**
     * Check if a port is available.
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Ensure the log directory exists and return the log file path.
     */
    public static Path ensureLogDirectory() throws IOException {
        Path logDir = getLogDirectory();
        Files.createDirectories(logDir);
        return getLogFilePath();
    }

    /**
     * Builder for AppConfig.
     */
    public static class Builder {
        private Path workspacePath = null;
        private Path workspaceRoot = null;
        private String workspaceName = null;
        private int preferredPort = 8080;
        private boolean devMode = false;

        public Builder workspacePath(String path) {
            if (path != null && !path.isEmpty()) {
                this.workspacePath = Paths.get(path).toAbsolutePath().normalize();
            }
            return this;
        }

        public Builder workspaceRoot(String path) {
            if (path != null && !path.isEmpty()) {
                this.workspaceRoot = Paths.get(path).toAbsolutePath().normalize();
            }
            return this;
        }

        public Builder workspaceName(String name) {
            if (name != null && !name.isEmpty()) {
                this.workspaceName = name.trim();
            }
            return this;
        }

        public Builder port(int port) {
            this.preferredPort = port;
            return this;
        }

        public Builder devMode(boolean devMode) {
            this.devMode = devMode;
            return this;
        }

        public Builder parseArgs(String[] args) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];

                // Handle --workspace=value or --workspace value
                if (arg.startsWith("--workspace=")) {
                    workspacePath(arg.substring("--workspace=".length()));
                } else if ("--workspace".equals(arg) && i + 1 < args.length) {
                    workspacePath(args[++i]);
                }

                // Handle --workspace-root=value or --workspace-root value
                else if (arg.startsWith("--workspace-root=")) {
                    workspaceRoot(arg.substring("--workspace-root=".length()));
                } else if ("--workspace-root".equals(arg) && i + 1 < args.length) {
                    workspaceRoot(args[++i]);
                }

                // Handle --workspace-name=value or --workspace-name value
                else if (arg.startsWith("--workspace-name=")) {
                    workspaceName(arg.substring("--workspace-name=".length()));
                } else if ("--workspace-name".equals(arg) && i + 1 < args.length) {
                    workspaceName(args[++i]);
                }

                // Handle --port=value or --port value
                else if (arg.startsWith("--port=")) {
                    try {
                        this.preferredPort = Integer.parseInt(arg.substring("--port=".length()));
                    } catch (NumberFormatException ignored) {}
                } else if ("--port".equals(arg) && i + 1 < args.length) {
                    try {
                        this.preferredPort = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException ignored) {}
                }

                // Handle --dev flag
                else if ("--dev".equals(arg)) {
                    this.devMode = true;
                }
            }
            return this;
        }

        public AppConfig build() throws IOException {
            // Resolve workspace path
            Path workspace = resolveWorkspacePath();

            // Find available port
            int port = findAvailablePort(preferredPort);

            // Ensure log directory exists
            Path logPath = ensureLogDirectory();

            return new AppConfig(workspace, logPath, port, devMode);
        }

        private Path resolveWorkspacePath() throws IOException {
            if (workspacePath != null) {
                persistWorkspaceConfig(workspacePath, null, null);
                return workspacePath;
            }

            Properties config = loadConfig();
            String storedPath = config.getProperty("workspace.path");
            if (storedPath != null && !storedPath.isBlank()) {
                Path resolved = Paths.get(storedPath).toAbsolutePath().normalize();
                return resolved;
            }

            Path root = workspaceRoot;
            if (root == null) {
                String storedRoot = config.getProperty("workspace.root");
                if (storedRoot != null && !storedRoot.isBlank()) {
                    root = Paths.get(storedRoot).toAbsolutePath().normalize();
                } else {
                    root = getDefaultWorkspaceRoot();
                }
            }

            String name = workspaceName;
            if (name == null || name.isBlank()) {
                String storedName = config.getProperty("workspace.name");
                name = (storedName == null || storedName.isBlank()) ? "default" : storedName;
            }

            Path resolved = root.resolve(name).toAbsolutePath().normalize();
            persistWorkspaceConfig(null, root, name);
            return resolved;
        }

        private Properties loadConfig() throws IOException {
            Properties props = new Properties();
            Path configDir = getConfigDirectory();
            Path configPath = configDir.resolve(CONFIG_FILE_NAME);
            if (Files.exists(configPath)) {
                try (var input = Files.newInputStream(configPath)) {
                    props.load(input);
                }
            }
            return props;
        }

        private void persistWorkspaceConfig(Path path, Path root, String name) throws IOException {
            Path configDir = getConfigDirectory();
            Files.createDirectories(configDir);
            Path configPath = configDir.resolve(CONFIG_FILE_NAME);

            Properties props = new Properties();
            if (Files.exists(configPath)) {
                try (var input = Files.newInputStream(configPath)) {
                    props.load(input);
                }
            }

            if (path != null) {
                props.setProperty("workspace.path", path.toString());
            } else {
                props.remove("workspace.path");
            }

            if (root != null) {
                props.setProperty("workspace.root", root.toString());
            }
            if (name != null && !name.isBlank()) {
                props.setProperty("workspace.name", name);
            }

            try (var output = Files.newOutputStream(configPath)) {
                props.store(output, "Control Room configuration");
            }
        }
    }
}
