package com.miniide;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple logging utility that writes to both console and file.
 */
public class AppLogger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrintStream fileOutput;
    private final PrintStream consoleOutput;
    private final boolean consoleEnabled;

    private static AppLogger instance;

    private AppLogger(Path logFile, boolean consoleEnabled) throws IOException {
        this.consoleOutput = System.out;
        this.consoleEnabled = consoleEnabled;

        // Open log file in append mode
        FileOutputStream fos = new FileOutputStream(logFile.toFile(), true);
        this.fileOutput = new PrintStream(fos, true, "UTF-8");

        // Log startup
        String separator = "=".repeat(60);
        fileOutput.println();
        fileOutput.println(separator);
        fileOutput.println("Control Room Started at " + LocalDateTime.now().format(TIME_FORMAT));
        fileOutput.println(separator);
    }

    public static synchronized void initialize(Path logFile, boolean consoleEnabled) throws IOException {
        if (instance == null) {
            instance = new AppLogger(logFile, consoleEnabled);
        }
    }

    public static AppLogger get() {
        return instance;
    }

    public void info(String message) {
        log("INFO", message);
    }

    public void warn(String message) {
        log("WARN", message);
    }

    public void error(String message) {
        log("ERROR", message);
    }

    public void error(String message, Throwable t) {
        log("ERROR", message);
        if (fileOutput != null) {
            t.printStackTrace(fileOutput);
        }
        if (consoleEnabled) {
            t.printStackTrace(consoleOutput);
        }
    }

    private void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String line = String.format("[%s] [%s] %s", timestamp, level, message);

        if (fileOutput != null) {
            fileOutput.println(line);
        }

        if (consoleEnabled) {
            consoleOutput.println(line);
        }
    }

    /**
     * Print to console only (for startup banners, etc.)
     */
    public void console(String message) {
        if (consoleEnabled) {
            consoleOutput.println(message);
        }
        if (fileOutput != null) {
            fileOutput.println(message);
        }
    }

    public void close() {
        if (fileOutput != null) {
            fileOutput.close();
        }
    }
}
