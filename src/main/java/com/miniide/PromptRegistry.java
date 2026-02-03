package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.PromptTool;
import com.miniide.models.PromptToolsFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PromptRegistry {
    private static final List<String> CORE_TOOL_IDS = List.of(
        "file-locator",
        "task-router",
        "canon-checker",
        "outline-analyzer"
    );

    private final Path registryPath;
    private final Path workspaceRoot;
    private final ObjectMapper objectMapper;
    private PromptToolsFile projectPrompts;
    private PromptToolsFile globalPrompts;
    private PromptToolsFile userPrompts;
    private final AppLogger logger;

    public PromptRegistry(Path workspaceRoot, ObjectMapper objectMapper) {
        this.registryPath = workspaceRoot.resolve(".control-room").resolve("prompts").resolve("prompts.json");
        this.workspaceRoot = workspaceRoot;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
        loadGlobalPrompts();
        loadUserPrompts();
        requireCoreTools();
        if (hasWorkspaceMarker()) {
            ensureRegistryExists();
            loadFromDisk();
            validatePromptReferences();
            warnOverrides();
        } else {
            initializeEmptyRegistry();
        }
    }

    public List<PromptTool> listPrompts() {
        List<PromptTool> prompts = new ArrayList<>();
        prompts.addAll(mergedPrompts().values());
        prompts.sort(Comparator.comparing(prompt -> prompt.getName() != null ? prompt.getName() : ""));
        return prompts;
    }

    public PromptTool getPrompt(String id) {
        if (id == null) {
            return null;
        }
        return mergedPrompts().get(id);
    }

    public PromptTool savePrompt(PromptTool prompt) {
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt payload is required");
        }
        if (prompt.getName() == null || prompt.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt name is required");
        }
        if (projectPrompts == null) {
            projectPrompts = new PromptToolsFile();
            projectPrompts.setPrompts(new ArrayList<>());
        }

        String id = prompt.getId();
        if (id == null || id.trim().isEmpty()) {
            id = slugify(prompt.getName());
        }

        PromptTool existing = getProjectPrompt(id);
        long now = System.currentTimeMillis();
        if (existing != null) {
            existing.setName(prompt.getName());
            existing.setArchetype(prompt.getArchetype());
            existing.setScope(prompt.getScope());
            existing.setUsageNotes(prompt.getUsageNotes());
            existing.setGoals(prompt.getGoals());
            existing.setGuardrails(prompt.getGuardrails());
            existing.setPrompt(prompt.getPrompt());
            existing.setUpdatedAt(now);
            saveToDisk();
            return existing;
        }

        if (getPrompt(id) != null) {
            id = generateUniqueId(id);
        }

        prompt.setId(id);
        prompt.setCreatedAt(now);
        prompt.setUpdatedAt(now);

        projectPrompts.getPrompts().add(prompt);
        if (!saveToDisk()) {
            throw new IllegalStateException("Failed to save prompt registry");
        }
        logger.info("Saved prompt tool: " + prompt.getName() + " (" + id + ")");
        return prompt;
    }

    public boolean deletePrompt(String id) {
        if (projectPrompts == null || projectPrompts.getPrompts() == null || id == null) {
            return false;
        }
        boolean removed = projectPrompts.getPrompts().removeIf(prompt -> id.equals(prompt.getId()));
        if (removed) {
            saveToDisk();
            logger.info("Deleted prompt tool: " + id);
        }
        return removed;
    }

    public String buildCatalogPrompt() {
        List<PromptTool> prompts = listPrompts();
        if (prompts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Tool Catalog (prompt tools):\n");
        for (PromptTool prompt : prompts) {
            if (prompt == null) continue;
            builder.append("- ").append(safe(prompt.getName()));
            if (prompt.getId() != null && !prompt.getId().isBlank()) {
                builder.append(" (id: ").append(prompt.getId()).append(")");
            }
            builder.append("\n");
            if (prompt.getArchetype() != null && !prompt.getArchetype().isBlank()) {
                builder.append("  Archetype: ").append(prompt.getArchetype()).append("\n");
            }
            if (prompt.getScope() != null && !prompt.getScope().isBlank()) {
                builder.append("  Scope: ").append(prompt.getScope()).append("\n");
            }
            if (prompt.getUsageNotes() != null && !prompt.getUsageNotes().isBlank()) {
                builder.append("  Use when: ").append(prompt.getUsageNotes().trim()).append("\n");
            }
            if (prompt.getGoals() != null && !prompt.getGoals().isBlank()) {
                builder.append("  Goals: ").append(prompt.getGoals().trim()).append("\n");
            }
            if (prompt.getGuardrails() != null && !prompt.getGuardrails().isBlank()) {
                builder.append("  Guardrails: ").append(prompt.getGuardrails().trim()).append("\n");
            }
            if (prompt.getPrompt() != null && !prompt.getPrompt().isBlank()) {
                builder.append("  Prompt:\n");
                builder.append(prompt.getPrompt().trim()).append("\n");
            }
        }
        return builder.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean saveToDisk() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), projectPrompts);
            logger.info("Saved prompt registry to " + registryPath);
            return true;
        } catch (IOException e) {
            logger.error("Failed to save prompt registry: " + e.getMessage(), e);
            return false;
        }
    }

    private void ensureRegistryExists() {
        if (!hasWorkspaceMarker()) {
            logger.info("Skipping prompt registry creation - no .control-room marker (NO_PROJECT state)");
            return;
        }
        if (Files.exists(registryPath)) {
            return;
        }

        try {
            Files.createDirectories(registryPath.getParent());
            PromptToolsFile emptyRegistry = new PromptToolsFile();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), emptyRegistry);
            logger.info("Created empty prompt registry at " + registryPath);
        } catch (IOException e) {
            logger.error("Failed to create prompt registry: " + e.getMessage(), e);
        }
    }

    private void loadFromDisk() {
        if (!hasWorkspaceMarker()) {
            initializeEmptyRegistry();
            return;
        }
        try {
            projectPrompts = objectMapper.readValue(registryPath.toFile(), PromptToolsFile.class);
            logger.info("Loaded prompt registry: " + registryPath);
        } catch (IOException e) {
            logger.error("Failed to load prompt registry: " + e.getMessage(), e);
            projectPrompts = null;
        }
    }

    private boolean hasWorkspaceMarker() {
        Path controlRoomDir = registryPath.getParent().getParent();
        return Files.exists(controlRoomDir.resolve("workspace.json"));
    }

    private void initializeEmptyRegistry() {
        projectPrompts = new PromptToolsFile();
        projectPrompts.setPrompts(new ArrayList<>());
    }

    private PromptTool getProjectPrompt(String id) {
        if (projectPrompts == null || projectPrompts.getPrompts() == null || id == null) {
            return null;
        }
        return projectPrompts.getPrompts().stream()
            .filter(prompt -> id.equals(prompt.getId()))
            .findFirst()
            .orElse(null);
    }

    private java.util.LinkedHashMap<String, PromptTool> mergedPrompts() {
        java.util.LinkedHashMap<String, PromptTool> merged = new java.util.LinkedHashMap<>();
        if (globalPrompts != null && globalPrompts.getPrompts() != null) {
            globalPrompts.getPrompts().forEach(prompt -> {
                if (prompt != null && prompt.getId() != null) {
                    merged.put(prompt.getId(), prompt);
                }
            });
        }
        if (userPrompts != null && userPrompts.getPrompts() != null) {
            userPrompts.getPrompts().forEach(prompt -> {
                if (prompt != null && prompt.getId() != null) {
                    merged.put(prompt.getId(), prompt);
                }
            });
        }
        if (projectPrompts != null && projectPrompts.getPrompts() != null) {
            projectPrompts.getPrompts().forEach(prompt -> {
                if (prompt != null && prompt.getId() != null) {
                    merged.put(prompt.getId(), prompt);
                }
            });
        }
        return merged;
    }

    private void loadGlobalPrompts() {
        try (var stream = PromptRegistry.class.getResourceAsStream("/prompts/tools.json")) {
            if (stream == null) {
                logger.error("Global prompt registry missing: /prompts/tools.json");
                globalPrompts = new PromptToolsFile();
                return;
            }
            globalPrompts = objectMapper.readValue(stream, PromptToolsFile.class);
        } catch (Exception e) {
            logger.error("Failed to load global prompt registry: " + e.getMessage(), e);
            globalPrompts = new PromptToolsFile();
        }
    }

    private void loadUserPrompts() {
        try {
            String home = System.getProperty("user.home");
            if (home == null || home.isBlank()) {
                userPrompts = new PromptToolsFile();
                return;
            }
            Path userPath = Path.of(home, ".control-room", "prompts", "tools.json");
            if (!Files.exists(userPath)) {
                userPrompts = new PromptToolsFile();
                return;
            }
            userPrompts = objectMapper.readValue(userPath.toFile(), PromptToolsFile.class);
        } catch (Exception e) {
            logger.error("Failed to load user prompt registry: " + e.getMessage(), e);
            userPrompts = new PromptToolsFile();
        }
    }

    private void validatePromptReferences() {
        if (projectPrompts == null || projectPrompts.getPrompts() == null) {
            return;
        }
        Path scnOutline = workspaceRoot.resolve("Story").resolve("SCN-outline.md");
        for (PromptTool tool : projectPrompts.getPrompts()) {
            if (tool == null) continue;
            String prompt = tool.getPrompt();
            if (prompt == null || prompt.isBlank()) continue;
            String lowered = prompt.toLowerCase();
            if (lowered.contains("story/outline.md")) {
                logger.warn("Prompt tool references deprecated path Story/outline.md: " + tool.getId());
            }
            if (lowered.contains("story/scn-outline.md") && !Files.exists(scnOutline)) {
                logger.warn("Prompt tool references Story/SCN-outline.md but file is missing: " + tool.getId());
            }
        }
    }

    private void warnOverrides() {
        if (projectPrompts == null || projectPrompts.getPrompts() == null) {
            return;
        }
        for (PromptTool tool : projectPrompts.getPrompts()) {
            if (tool == null || tool.getId() == null) continue;
            if (CORE_TOOL_IDS.contains(tool.getId())) {
                logger.warn("Project prompt overrides core tool: " + tool.getId());
            }
        }
        if (userPrompts != null && userPrompts.getPrompts() != null) {
            for (PromptTool tool : userPrompts.getPrompts()) {
                if (tool == null || tool.getId() == null) continue;
                if (CORE_TOOL_IDS.contains(tool.getId())) {
                    logger.warn("User prompt overrides core tool: " + tool.getId());
                }
            }
        }
    }

    private void requireCoreTools() {
        for (String id : CORE_TOOL_IDS) {
            PromptTool tool = mergedPrompts().get(id);
            if (tool == null) {
                throw new IllegalStateException("Global tool registry missing core tool: " + id);
            }
        }
    }

    private String generateUniqueId(String baseId) {
        String id = baseId;
        int counter = 1;
        while (getPrompt(id) != null) {
            id = baseId + "-" + counter++;
        }
        return id;
    }

    private String slugify(String value) {
        if (value == null) {
            return "prompt";
        }
        String slug = value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "prompt" : slug;
    }
}
