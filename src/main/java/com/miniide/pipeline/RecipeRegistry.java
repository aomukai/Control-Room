package com.miniide.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads bundled recipes from classpath (src/main/resources/recipes/*.json)
 * and project overrides from .control-room/recipes/*.json.
 * Project recipes shadow bundled recipes by recipe_id.
 */
public class RecipeRegistry {

    private final ObjectMapper objectMapper;
    private final Path projectRecipesDir;
    private final AppLogger logger = AppLogger.get();
    private final Map<String, JsonNode> recipes = new LinkedHashMap<>();

    private static final String BUNDLED_RECIPES_DIR = "recipes/";
    private static final String[] BUNDLED_RECIPE_FILES = {
        "creative_draft_scene.json"
    };

    public RecipeRegistry(Path workspaceRoot, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.projectRecipesDir = workspaceRoot.resolve(".control-room").resolve("recipes");
        reload();
    }

    /**
     * Reload all recipes from bundled + project sources.
     * Project recipes shadow bundled ones by recipe_id.
     */
    public synchronized void reload() {
        recipes.clear();
        loadBundled();
        loadProject();
        logger.info("RecipeRegistry loaded " + recipes.size() + " recipes");
    }

    public synchronized Map<String, JsonNode> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
    }

    public synchronized JsonNode get(String recipeId) {
        return recipes.get(recipeId);
    }

    public synchronized boolean has(String recipeId) {
        return recipes.containsKey(recipeId);
    }

    private void loadBundled() {
        for (String filename : BUNDLED_RECIPE_FILES) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(BUNDLED_RECIPES_DIR + filename)) {
                if (is == null) {
                    logger.warn("Bundled recipe not found on classpath: " + filename);
                    continue;
                }
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(json);
                String recipeId = node.path("recipe_id").asText(null);
                if (recipeId == null || recipeId.isBlank()) {
                    logger.warn("Bundled recipe missing recipe_id: " + filename);
                    continue;
                }
                recipes.put(recipeId, node);
            } catch (IOException e) {
                logger.warn("Failed to load bundled recipe " + filename + ": " + e.getMessage());
            }
        }
    }

    private void loadProject() {
        if (!Files.isDirectory(projectRecipesDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectRecipesDir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    JsonNode node = objectMapper.readTree(json);
                    String recipeId = node.path("recipe_id").asText(null);
                    if (recipeId == null || recipeId.isBlank()) {
                        logger.warn("Project recipe missing recipe_id: " + file.getFileName());
                        continue;
                    }
                    recipes.put(recipeId, node); // shadows bundled
                } catch (IOException e) {
                    logger.warn("Failed to load project recipe " + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to scan project recipes dir: " + e.getMessage());
        }
    }
}
