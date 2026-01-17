package com.miniide.controllers;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller for listing available ambient audio files.
 * Scans the public/audio directory for MP3 files.
 */
public class AudioController implements Controller {

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/audio", this::listAudioFiles);
    }

    /**
     * GET /api/audio
     * Returns a list of available audio files from the public/audio directory.
     * Each entry includes: filename, displayName, path
     */
    private void listAudioFiles(Context ctx) {
        try {
            List<Map<String, String>> audioFiles = scanAudioDirectory();
            ctx.json(Map.of("files", audioFiles));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private List<Map<String, String>> scanAudioDirectory() throws IOException, URISyntaxException {
        List<Map<String, String>> files = new ArrayList<>();

        // Get the audio directory from classpath resources
        URL audioUrl = getClass().getClassLoader().getResource("public/audio");
        if (audioUrl == null) {
            // Directory doesn't exist yet - return empty list
            return files;
        }

        // Handle both file system and JAR resources
        Path audioPath;
        if (audioUrl.getProtocol().equals("jar")) {
            // Running from JAR - need to use FileSystem
            String jarPath = audioUrl.toString().split("!")[0];
            try {
                FileSystem fs = FileSystems.newFileSystem(java.net.URI.create(jarPath), Collections.emptyMap());
                audioPath = fs.getPath("/public/audio");
            } catch (FileSystemAlreadyExistsException e) {
                // FileSystem already exists, get it
                FileSystem fs = FileSystems.getFileSystem(java.net.URI.create(jarPath));
                audioPath = fs.getPath("/public/audio");
            }
        } else {
            // Running from file system (development mode)
            audioPath = Paths.get(audioUrl.toURI());
        }

        if (!Files.exists(audioPath) || !Files.isDirectory(audioPath)) {
            return files;
        }

        // Scan for audio files (mp3, ogg, wav)
        try (Stream<Path> stream = Files.list(audioPath)) {
            files = stream
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav");
                })
                .map(p -> {
                    String filename = p.getFileName().toString();
                    String displayName = filenameToDisplayName(filename);
                    return Map.of(
                        "filename", filename,
                        "displayName", displayName,
                        "path", "/audio/" + filename
                    );
                })
                .sorted(Comparator.comparing(m -> m.get("displayName")))
                .collect(Collectors.toList());
        }

        return files;
    }

    /**
     * Convert a filename to a display name.
     * e.g., "coffee-shop.mp3" -> "Coffee Shop"
     *       "rain_forest.mp3" -> "Rain Forest"
     */
    private String filenameToDisplayName(String filename) {
        // Remove extension
        int dotIndex = filename.lastIndexOf('.');
        String name = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;

        // Replace hyphens and underscores with spaces
        name = name.replace('-', ' ').replace('_', ' ');

        // Capitalize each word
        String[] words = name.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (result.length() > 0) {
                    result.append(' ');
                }
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }
}
