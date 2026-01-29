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
    private final Path registryPath;
    private final ObjectMapper objectMapper;
    private PromptToolsFile promptFile;
    private final AppLogger logger;

    public PromptRegistry(Path workspaceRoot, ObjectMapper objectMapper) {
        this.registryPath = workspaceRoot.resolve(".control-room").resolve("prompts").resolve("prompts.json");
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
        if (hasWorkspaceMarker()) {
            ensureRegistryExists();
            loadFromDisk();
            ensureDefaultPrompts();
        } else {
            initializeEmptyRegistry();
        }
    }

    public List<PromptTool> listPrompts() {
        if (promptFile == null || promptFile.getPrompts() == null) {
            return List.of();
        }
        List<PromptTool> prompts = new ArrayList<>(promptFile.getPrompts());
        prompts.sort(Comparator.comparing(prompt -> prompt.getName() != null ? prompt.getName() : ""));
        return prompts;
    }

    public PromptTool getPrompt(String id) {
        if (promptFile == null || promptFile.getPrompts() == null || id == null) {
            return null;
        }
        return promptFile.getPrompts().stream()
            .filter(prompt -> id.equals(prompt.getId()))
            .findFirst()
            .orElse(null);
    }

    public PromptTool savePrompt(PromptTool prompt) {
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt payload is required");
        }
        if (prompt.getName() == null || prompt.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt name is required");
        }
        if (promptFile == null) {
            promptFile = new PromptToolsFile();
            promptFile.setPrompts(new ArrayList<>());
        }

        String id = prompt.getId();
        if (id == null || id.trim().isEmpty()) {
            id = slugify(prompt.getName());
        }

        PromptTool existing = getPrompt(id);
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

        promptFile.getPrompts().add(prompt);
        if (!saveToDisk()) {
            throw new IllegalStateException("Failed to save prompt registry");
        }
        logger.info("Saved prompt tool: " + prompt.getName() + " (" + id + ")");
        return prompt;
    }

    public boolean deletePrompt(String id) {
        if (promptFile == null || promptFile.getPrompts() == null || id == null) {
            return false;
        }
        boolean removed = promptFile.getPrompts().removeIf(prompt -> id.equals(prompt.getId()));
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
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), promptFile);
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
            promptFile = objectMapper.readValue(registryPath.toFile(), PromptToolsFile.class);
            logger.info("Loaded prompt registry: " + registryPath);
        } catch (IOException e) {
            logger.error("Failed to load prompt registry: " + e.getMessage(), e);
            promptFile = null;
        }
    }

    private boolean hasWorkspaceMarker() {
        Path controlRoomDir = registryPath.getParent().getParent();
        return Files.exists(controlRoomDir.resolve("workspace.json"));
    }

    private void initializeEmptyRegistry() {
        promptFile = new PromptToolsFile();
        promptFile.setPrompts(new ArrayList<>());
    }

    private void ensureDefaultPrompts() {
        if (promptFile == null) {
            promptFile = new PromptToolsFile();
            promptFile.setPrompts(new ArrayList<>());
        }
        boolean changed = false;
        if (getPrompt("search-issues") == null) {
            PromptTool tool = new PromptTool();
            tool.setId("search-issues");
            tool.setName("Search Issues");
            tool.setArchetype("Any");
            tool.setScope("project");
            tool.setUsageNotes("Find issues by shared tags, assignment, or personal tags for the active agent.");
            tool.setGoals("Retrieve relevant issues quickly and avoid re-reading irrelevant threads.");
            tool.setGuardrails("Do not invent issues. Use only the filters provided.");
            tool.setPrompt(
                "search_issues(\\n" +
                "  tags?: string[],\\n" +
                "  assignedTo?: string,\\n" +
                "  status?: \"open\" | \"closed\" | \"all\",\\n" +
                "  priority?: \"low\" | \"normal\" | \"high\" | \"urgent\",\\n" +
                "  personalTags?: string[],\\n" +
                "  personalAgent?: string,\\n" +
                "  excludePersonalTags?: string[],\\n" +
                "  minInterestLevel?: number\\n" +
                ")\\n\\n" +
                "Notes:\\n" +
                "- personalTags filters on the agent's personal tags; requires personalAgent (agentId).\\n" +
                "- excludePersonalTags can hide items marked irrelevant."
            );
            long now = System.currentTimeMillis();
            tool.setCreatedAt(now);
            tool.setUpdatedAt(now);
            promptFile.getPrompts().add(tool);
            changed = true;
        }
        if (changed && hasWorkspaceMarker()) {
            saveToDisk();
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
