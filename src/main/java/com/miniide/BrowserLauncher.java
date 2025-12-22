package com.miniide;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility to open the default browser across different platforms.
 */
public class BrowserLauncher {

    private static final AtomicBoolean browserOpened = new AtomicBoolean(false);

    /**
     * Open the specified URL in the default browser.
     * This method ensures the browser is only opened once per application start.
     *
     * @param url The URL to open
     * @return true if the browser was opened, false if it was already opened or failed
     */
    public static boolean openBrowser(String url) {
        // Ensure we only open the browser once
        if (!browserOpened.compareAndSet(false, true)) {
            return false;
        }

        AppLogger logger = AppLogger.get();

        try {
            // Try java.awt.Desktop first (works on most platforms with GUI)
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(url));
                    if (logger != null) {
                        logger.info("Browser opened: " + url);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            // Desktop API failed, try platform-specific fallbacks
            if (logger != null) {
                logger.warn("Desktop.browse() failed, trying fallback: " + e.getMessage());
            }
        }

        // Platform-specific fallbacks
        String os = System.getProperty("os.name").toLowerCase();
        String[] command;

        try {
            if (os.contains("win")) {
                // Windows
                command = new String[]{"cmd", "/c", "start", "", url};
            } else if (os.contains("mac")) {
                // macOS
                command = new String[]{"open", url};
            } else if (os.contains("nux") || os.contains("nix")) {
                // Linux - try xdg-open first, then common browsers
                command = new String[]{"xdg-open", url};
            } else {
                if (logger != null) {
                    logger.warn("Unsupported OS for browser launch: " + os);
                }
                browserOpened.set(false); // Reset so it can be tried again
                return false;
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            pb.start();

            if (logger != null) {
                logger.info("Browser opened via command: " + url);
            }
            return true;

        } catch (IOException e) {
            if (logger != null) {
                logger.error("Failed to open browser: " + e.getMessage());
            }
            browserOpened.set(false); // Reset so it can be tried again
            return false;
        }
    }

    /**
     * Open browser after a delay (useful to let the server start).
     *
     * @param url The URL to open
     * @param delayMs Delay in milliseconds before opening
     */
    public static void openBrowserDelayed(String url, long delayMs) {
        Thread browserThread = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                openBrowser(url);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "BrowserLauncher");
        browserThread.setDaemon(true);
        browserThread.start();
    }

    /**
     * Reset the browser opened flag (for testing purposes).
     */
    public static void reset() {
        browserOpened.set(false);
    }
}
