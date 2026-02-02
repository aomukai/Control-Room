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
        if (getPrompt("file-locator") == null) {
            PromptTool tool = new PromptTool();
            tool.setId("file-locator");
            tool.setName("File Locator");
            tool.setArchetype("Any");
            tool.setScope("project");
            tool.setUsageNotes("Use to find relevant files before analysis. Required first step for scenes, canon, or outline tasks.");
            tool.setGoals("Locate real project files, report metadata with confidence/match basis, and avoid assuming file contents.");
            tool.setGuardrails(
                "Return actual file paths only. Never invent files. " +
                "Include confidence and match_basis for each metadata field. " +
                "If DEEP_SCAN exceeds safe context, warn and request confirmation."
            );
            tool.setPrompt(
                "file_locator(\\n" +
                "  search_criteria: string,\\n" +
                "  scan_mode?: \"FAST_SCAN\" | \"DEEP_SCAN\",\\n" +
                "  max_files?: number,\\n" +
                "  dry_run?: boolean\\n" +
                ")\\n\\n" +
                "Search locations:\\n" +
                "- Story/Scenes/ (scene files)\\n" +
                "- Story/Compendium/ (canon/worldbuilding)\\n" +
                "- Story/SCN-outline.md (story structure, virtual)\\n\\n" +
                "Metadata fields to return per file:\\n" +
                "- Type: scene/canon/outline\\n" +
                "- POV (if scene) with confidence and match_basis\\n" +
                "- Scene # (if applicable) with confidence and match_basis\\n" +
                "- Size: word count or file size\\n" +
                "- Modified: last modified timestamp\\n" +
                "- Keywords: list with confidence and match_basis\\n\\n" +
                "Match basis values: FILENAME_MATCH | CONTENT_MATCH | METADATA_MATCH\\n\\n" +
                "If scan_mode=DEEP_SCAN, estimate token usage before loading content:\\n" +
                "DEEP_SCAN requested on [N] files (estimated [TOKENS] tokens).\\n" +
                "WARNING if TOKENS > 30000. Recommend FAST_SCAN or reduce scope to <= 6 files.\\n" +
                "Proceed anyway? [yes/no]\\n\\n" +
                "If dry_run=true, output what would be scanned and estimated token usage only."
            );
            long now = System.currentTimeMillis();
            tool.setCreatedAt(now);
            tool.setUpdatedAt(now);
            promptFile.getPrompts().add(tool);
            changed = true;
        }
        if (getPrompt("task-router") == null) {
            PromptTool tool = new PromptTool();
            tool.setId("task-router");
            tool.setName("Task Router");
            tool.setArchetype("Chief");
            tool.setScope("project");
            tool.setUsageNotes("Use when a user request needs routing to specialized agents or tools.");
            tool.setGoals("Classify task type, choose role(s), define evidence type, and specify required context.");
            tool.setGuardrails(
                "Do not hardcode agent names. Route by role. " +
                "If a role is unavailable, route to Chief with FALLBACK reason. " +
                "If ambiguous, output CLARIFY questions before routing."
            );
            tool.setPrompt(
                "task_router(\\n" +
                "  user_request: string,\\n" +
                "  dry_run?: boolean\\n" +
                ")\\n\\n" +
                "Available roles:\\n" +
                "- planner (outline structure, scene ordering, stakes)\\n" +
                "- writer (prose drafting, scenes, dialogue)\\n" +
                "- editor (line editing, clarity, consistency)\\n" +
                "- critic (narrative impact, reader experience)\\n" +
                "- continuity (timeline, canon consistency, character tracking)\\n" +
                "- chief (router/moderator)\\n\\n" +
                "Output format (exact fields):\\n" +
                "ROUTE: [role or roles]\\n" +
                "ORDER: [sequential order if multiple]\\n" +
                "PARALLEL: [roles that can run together]\\n" +
                "CONTEXT: [files needed] or \"needs file_locator scan\"\\n" +
                "EXPECTED_OUTPUT: [what to produce]\\n" +
                "EVIDENCE_TYPE: QUOTE | LINE_REF | SCOPE_SCAN\\n" +
                "COMPLEXITY: LOW | MEDIUM | HIGH\\n" +
                "ESTIMATED_TIME: [e.g., 3-5 minutes]\\n" +
                "PRIORITY: LOW | NORMAL | HIGH\\n" +
                "FALLBACK: [reason if routing to chief]\\n\\n" +
                "If ambiguous, output: CLARIFY: [specific questions]\\n\\n" +
                "If dry_run=true, output a routing preview only (no final route)."
            );
            long now = System.currentTimeMillis();
            tool.setCreatedAt(now);
            tool.setUpdatedAt(now);
            promptFile.getPrompts().add(tool);
            changed = true;
        }
        if (getPrompt("canon-checker") == null) {
            PromptTool tool = new PromptTool();
            tool.setId("canon-checker");
            tool.setName("Canon Checker");
            tool.setArchetype("Continuity");
            tool.setScope("project");
            tool.setUsageNotes("Use when verifying scene content against canon files.");
            tool.setGoals("Ensure scenes do not contradict canon. Identify gaps explicitly.");
            tool.setGuardrails(
                "Do not proceed without canon access. " +
                "If canon files missing, output CANON_NEEDED and stop. " +
                "Quote both canon and scene for any claim."
            );
            tool.setPrompt(
                "canon_checker(\\n" +
                "  scene_file: string,\\n" +
                "  canon_files: string[],\\n" +
                "  dry_run?: boolean\\n" +
                ")\\n\\n" +
                "If canon_files are missing, output:\\n" +
                "CANON_NEEDED: [list of canon files]\\n" +
                "STATUS: Cannot validate without canon\\n\\n" +
                "Otherwise, verify character, location, technology, culture, and historical facts.\\n" +
                "Report format:\\n" +
                "Scene: [filename]\\n" +
                "Canon: [filename]\\n" +
                "Scene quote: \"...\"\\n" +
                "Canon quote: \"...\"\\n" +
                "Status: Consistent | Inconsistent | Canon gap\\n" +
                "Recommendation: [Add to canon / Revise scene / Flag for review]\\n\\n" +
                "If dry_run=true, output which files would be checked."
            );
            long now = System.currentTimeMillis();
            tool.setCreatedAt(now);
            tool.setUpdatedAt(now);
            promptFile.getPrompts().add(tool);
            changed = true;
        }
        if (getPrompt("outline-analyzer") == null) {
            PromptTool tool = new PromptTool();
            tool.setId("outline-analyzer");
            tool.setName("Outline Analyzer");
            tool.setArchetype("Planner");
            tool.setScope("project");
            tool.setUsageNotes("Analyze story outline for structure, gaps, and stakes progression.");
            tool.setGoals("Identify structural issues with grounded references to outline text.");
            tool.setGuardrails(
                "If outline path unknown, call file_locator first. " +
                "Do not claim outline is missing without file_locator confirmation. " +
                "Quote outline text for all structural claims."
            );
            tool.setPrompt(
                "outline_analyzer(\\n" +
                "  outline_path?: string,\\n" +
                "  dry_run?: boolean\\n" +
                ")\\n\\n" +
                "If outline_path not provided, call file_locator for \"outline\" and report results.\\n" +
                "If no outline file exists, output: No outline file found.\\n\\n" +
                "Analyze:\\n" +
                "1) Scene count and distribution\\n" +
                "2) Stakes progression\\n" +
                "3) Act structure (setup/confrontation/resolution)\\n" +
                "4) Gaps or missing transitions\\n" +
                "5) POV balance\\n\\n" +
                "Report format:\\n" +
                "Structure Overview: ...\\n" +
                "Problem Identified: [quote + location]\\n" +
                "Suggested Fix: [actionable change]\\n\\n" +
                "If dry_run=true, output which outline file would be analyzed."
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
