package com.miniide;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application configuration handling platform-specific paths and settings.
 */
public class AppConfig {

    private static final String APP_NAME = "Mini-IDE";

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
     * Get the default workspace path based on the operating system.
     * Windows: %USERPROFILE%\Documents\Mini-IDE\workspace
     * macOS: ~/Documents/Mini-IDE/workspace
     * Linux: ~/Mini-IDE/workspace
     */
    public static Path getDefaultWorkspacePath() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            // Windows: Use Documents folder
            String documents = System.getenv("USERPROFILE");
            if (documents == null) {
                documents = userHome;
            }
            return Paths.get(documents, "Documents", APP_NAME, "workspace");
        } else if (os.contains("mac")) {
            // macOS: Use Documents folder
            return Paths.get(userHome, "Documents", APP_NAME, "workspace");
        } else {
            // Linux and others: Use home directory
            return Paths.get(userHome, APP_NAME, "workspace");
        }
    }

    /**
     * Get the log directory path based on the operating system.
     * Windows: %APPDATA%\Mini-IDE\logs
     * macOS: ~/Library/Logs/Mini-IDE
     * Linux: ~/.local/share/Mini-IDE/logs
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
     * Get the log file path.
     */
    public static Path getLogFilePath() {
        return getLogDirectory().resolve("mini-ide.log");
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
        private int preferredPort = 8080;
        private boolean devMode = false;

        public Builder workspacePath(String path) {
            if (path != null && !path.isEmpty()) {
                this.workspacePath = Paths.get(path).toAbsolutePath().normalize();
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
            Path workspace = workspacePath != null ? workspacePath : getDefaultWorkspacePath();

            // Find available port
            int port = findAvailablePort(preferredPort);

            // Ensure log directory exists
            Path logPath = ensureLogDirectory();

            return new AppConfig(workspace, logPath, port, devMode);
        }
    }
}
