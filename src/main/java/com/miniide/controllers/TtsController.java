package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppConfig;
import com.miniide.AppLogger;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.awt.Desktop;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller for Text-to-Speech (TTS) functionality.
 * Uses Piper TTS server running on localhost:5050.
 */
public class TtsController implements Controller {

    private static final String PIPER_URL = "http://localhost:5050";
    private static final Path VOICES_DIR = Path.of("data/voices");
    private static final Path TTS_SETTINGS_PATH = AppConfig.getSettingsDirectory().resolve("tts.json");

    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public TtsController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/tts/voices", this::listVoices);
        app.get("/api/tts/settings", this::getSettings);
        app.put("/api/tts/settings", this::updateSettings);
        app.post("/api/tts/test", this::testVoice);
        app.post("/api/tts/open-folder", this::openVoicesFolder);
        app.get("/api/tts/status", this::getStatus);
    }

    /**
     * List available Piper voices from the data/voices directory.
     * Looks for .onnx files and returns their base names.
     */
    private void listVoices(Context ctx) {
        try {
            List<Map<String, Object>> voices = new ArrayList<>();

            if (Files.exists(VOICES_DIR) && Files.isDirectory(VOICES_DIR)) {
                try (Stream<Path> files = Files.list(VOICES_DIR)) {
                    List<Path> onnxFiles = files
                            .filter(p -> p.toString().endsWith(".onnx") && !p.toString().endsWith(".onnx.json"))
                            .sorted()
                            .collect(Collectors.toList());

                    for (Path onnxFile : onnxFiles) {
                        String filename = onnxFile.getFileName().toString();
                        String voiceId = filename.substring(0, filename.length() - 5); // Remove .onnx

                        Map<String, Object> voice = new LinkedHashMap<>();
                        voice.put("id", voiceId);
                        voice.put("name", formatVoiceName(voiceId));
                        voice.put("hasConfig", Files.exists(onnxFile.resolveSibling(filename + ".json")));
                        voices.add(voice);
                    }
                }
            }

            ctx.json(Map.of(
                "voices", voices,
                "voicesDir", VOICES_DIR.toAbsolutePath().toString()
            ));
        } catch (Exception e) {
            logger.error("Failed to list TTS voices: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Get TTS settings (selected voice, speed).
     */
    private void getSettings(Context ctx) {
        try {
            TtsSettings settings = loadSettings();
            ctx.json(settings);
        } catch (Exception e) {
            logger.error("Failed to get TTS settings: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Update TTS settings.
     */
    private void updateSettings(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            TtsSettings settings = loadSettings();

            if (json.has("voice")) {
                settings.voice = json.get("voice").asText();
            }
            if (json.has("speed")) {
                settings.speed = json.get("speed").asDouble();
            }

            saveSettings(settings);
            ctx.json(settings);
        } catch (Exception e) {
            logger.error("Failed to update TTS settings: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Test TTS by generating audio and returning it.
     */
    private void testVoice(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String text = json.has("text") ? json.get("text").asText() : "Hello, this is a test.";
            String voice = json.has("voice") ? json.get("voice").asText() : null;
            double speed = json.has("speed") ? json.get("speed").asDouble() : 1.0;

            // Validate speed range (0.5 to 2.0)
            speed = Math.max(0.5, Math.min(2.0, speed));

            // Build Piper request
            // length_scale is the inverse of speed (lower = faster)
            double lengthScale = 1.0 / speed;

            Map<String, Object> piperRequest = new LinkedHashMap<>();
            piperRequest.put("text", text);
            if (voice != null && !voice.isBlank()) {
                piperRequest.put("voice", voice);
            }
            piperRequest.put("length_scale", lengthScale);

            // Call Piper server
            byte[] audioData = callPiperServer(piperRequest);

            ctx.contentType("audio/wav");
            ctx.result(audioData);
        } catch (Exception e) {
            logger.error("Failed to generate TTS audio: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Open the voices folder in the system file manager.
     */
    private void openVoicesFolder(Context ctx) {
        try {
            Path absPath = VOICES_DIR.toAbsolutePath();

            // Ensure directory exists
            if (!Files.exists(absPath)) {
                Files.createDirectories(absPath);
            }

            // Open in file manager
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(absPath.toFile());
                ctx.json(Map.of("ok", true, "path", absPath.toString()));
            } else {
                // Fallback for headless systems
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("win")) {
                    pb = new ProcessBuilder("explorer", absPath.toString());
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", absPath.toString());
                } else {
                    pb = new ProcessBuilder("xdg-open", absPath.toString());
                }
                pb.start();
                ctx.json(Map.of("ok", true, "path", absPath.toString()));
            }
        } catch (Exception e) {
            logger.error("Failed to open voices folder: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * Check if Piper TTS server is available.
     */
    private void getStatus(Context ctx) {
        boolean available = false;
        String message = "Piper TTS server not running";

        try {
            URL url = new URL(PIPER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            // Piper returns 405 for GET, but that means it's running
            available = (responseCode == 200 || responseCode == 405);
            message = available ? "Piper TTS server is running" : "Piper TTS returned: " + responseCode;
            conn.disconnect();
        } catch (Exception e) {
            message = "Piper TTS server not available: " + e.getMessage();
        }

        ctx.json(Map.of(
            "available", available,
            "message", message,
            "voicesDir", VOICES_DIR.toAbsolutePath().toString()
        ));
    }

    // ===== Helpers =====

    private byte[] callPiperServer(Map<String, Object> request) throws IOException {
        URL url = new URL(PIPER_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(30000); // TTS can take a while

        // Send request
        try (OutputStream os = conn.getOutputStream()) {
            objectMapper.writeValue(os, request);
        }

        // Read response
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String error = "";
            try (InputStream es = conn.getErrorStream()) {
                if (es != null) {
                    error = new String(es.readAllBytes());
                }
            }
            throw new IOException("Piper returned " + responseCode + ": " + error);
        }

        try (InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private String formatVoiceName(String voiceId) {
        // Convert voice ID like "en_US-amy-medium" to "Amy (English US, Medium)"
        String[] parts = voiceId.split("-");
        if (parts.length >= 2) {
            String locale = parts[0];
            String name = capitalize(parts[1]);
            String quality = parts.length > 2 ? capitalize(parts[2]) : "";

            String language = formatLocale(locale);
            if (quality.isEmpty()) {
                return name + " (" + language + ")";
            }
            return name + " (" + language + ", " + quality + ")";
        }
        return voiceId;
    }

    private String formatLocale(String locale) {
        // Convert "en_US" to "English US"
        Map<String, String> langMap = Map.of(
            "en", "English",
            "de", "German",
            "fr", "French",
            "es", "Spanish",
            "it", "Italian",
            "ja", "Japanese",
            "zh", "Chinese",
            "ru", "Russian"
        );

        String[] parts = locale.split("_");
        String lang = langMap.getOrDefault(parts[0], parts[0].toUpperCase());
        if (parts.length > 1) {
            return lang + " " + parts[1];
        }
        return lang;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private TtsSettings loadSettings() {
        try {
            if (Files.exists(TTS_SETTINGS_PATH)) {
                return objectMapper.readValue(TTS_SETTINGS_PATH.toFile(), TtsSettings.class);
            }
        } catch (Exception e) {
            logger.error("Failed to load TTS settings, using defaults: " + e.getMessage());
        }
        return new TtsSettings();
    }

    private void saveSettings(TtsSettings settings) throws IOException {
        Files.createDirectories(TTS_SETTINGS_PATH.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(TTS_SETTINGS_PATH.toFile(), settings);
    }

    /**
     * TTS settings POJO.
     */
    public static class TtsSettings {
        public String voice = "en_US-amy-medium";
        public double speed = 1.0;
    }
}
