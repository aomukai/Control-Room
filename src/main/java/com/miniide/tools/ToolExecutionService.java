package com.miniide.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.AppLogger;
import com.miniide.IssueMemoryService;
import com.miniide.PreparedWorkspaceService;
import com.miniide.ProjectContext;
import com.miniide.WorkspaceService;
import com.miniide.models.FileNode;
import com.miniide.models.Issue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ToolExecutionService {
    private static final Set<String> SUPPORTED_TOOLS = Set.of(
        "file_locator",
        "file_reader",
        "outline_analyzer",
        "canon_checker",
        "task_router",
        "issue_status_summarizer",
        "search_issues",
        "stakes_mapper",
        "prose_analyzer",
        "line_editor",
        "consistency_checker",
        "scene_draft_validator",
        "scene_impact_analyzer",
        "reader_experience_simulator",
        "timeline_validator",
        "beat_architect"
    );

    private final ProjectContext projectContext;
    private final IssueMemoryService issueService;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public ToolExecutionService(ProjectContext projectContext, IssueMemoryService issueService, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.issueService = issueService;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    public boolean isSupported(String toolName) {
        return toolName != null && SUPPORTED_TOOLS.contains(toolName);
    }

    public ToolExecutionResult execute(ToolCall call, ToolExecutionContext context) {
        if (call == null || call.getName() == null) {
            return ToolExecutionResult.error("Tool call missing name.", "missing-tool");
        }
        String tool = call.getName();
        try {
            ToolRun run;
            if (!isSupported(tool)) {
                run = ToolRun.of("Unknown tool: " + tool);
                String receiptId = writeToolReceipt(call, run, context);
                return ToolExecutionResult.withReceipt(run.output, false, "unknown-tool", receiptId);
            }
            switch (tool) {
                case "file_locator":
                    run = executeFileLocator(call.getArgs());
                    break;
                case "file_reader":
                    run = executeFileReader(call.getArgs());
                    break;
                case "outline_analyzer":
                    run = executeOutlineAnalyzer(call.getArgs());
                    break;
                case "canon_checker":
                    run = executeCanonChecker(call.getArgs());
                    break;
                case "task_router":
                    run = executeTaskRouter(call.getArgs());
                    break;
                case "issue_status_summarizer":
                    run = executeIssueStatusSummarizer(call.getArgs(), context);
                    break;
                case "search_issues":
                    run = executeSearchIssues(call.getArgs());
                    break;
                case "stakes_mapper":
                    run = executeStakesMapper(call.getArgs());
                    break;
                case "prose_analyzer":
                    run = executeProseAnalyzer(call.getArgs());
                    break;
                case "line_editor":
                    run = executeLineEditor(call.getArgs());
                    break;
                case "consistency_checker":
                    run = executeConsistencyChecker(call.getArgs());
                    break;
                case "scene_draft_validator":
                    run = executeSceneDraftValidator(call.getArgs());
                    break;
                case "scene_impact_analyzer":
                    run = executeSceneImpactAnalyzer(call.getArgs());
                    break;
                case "reader_experience_simulator":
                    run = executeReaderExperienceSimulator(call.getArgs());
                    break;
                case "timeline_validator":
                    run = executeTimelineValidator(call.getArgs());
                    break;
                case "beat_architect":
                    run = executeBeatArchitect(call.getArgs());
                    break;
                default:
                    run = ToolRun.of("Unsupported tool: " + tool);
                    String receiptId = writeToolReceipt(call, run, context);
                    return ToolExecutionResult.withReceipt(run.output, false, "unsupported-tool", receiptId);
            }
            String receiptId = writeToolReceipt(call, run, context);
            return ToolExecutionResult.withReceipt(run.output, true, null, receiptId);
        } catch (Exception e) {
            logger.warn("Tool execution failed: " + tool + " (" + e.getMessage() + ")");
            try {
                ToolRun run = ToolRun.of("Tool execution failed: " + e.getMessage());
                String receiptId = writeToolReceipt(call, run, context);
                return ToolExecutionResult.withReceipt(run.output, false, "execution-error", receiptId);
            } catch (Exception ignored) {
                return ToolExecutionResult.error("Tool execution failed: " + e.getMessage(), "execution-error");
            }
        }
    }

    private ToolRun executeFileLocator(Map<String, Object> args) throws IOException {
        String criteria = stringArg(args, "search_criteria", "");
        String scanMode = stringArg(args, "scan_mode", "FAST_SCAN").toUpperCase(Locale.ROOT);
        int maxFiles = intArg(args, "max_results", 12);
        boolean includeGlobs = boolArg(args, "include_globs", false);
        boolean dryRun = boolArg(args, "dry_run", false);

        List<FileNode> files = collectStoryFiles();
        String lowered = criteria != null ? criteria.toLowerCase(Locale.ROOT) : "";
        List<FileNode> matched = files.stream()
            .filter(node -> {
                if (lowered.isEmpty()) return true;
                String path = (node.getPath() != null ? node.getPath() : "").toLowerCase(Locale.ROOT);
                String name = (node.getName() != null ? node.getName() : "").toLowerCase(Locale.ROOT);
                return path.contains(lowered) || name.contains(lowered);
            })
            .limit(Math.max(1, maxFiles))
            .collect(Collectors.toList());

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "file_locator");
        root.put("search_criteria", criteria);
        root.put("scan_mode", scanMode);
        root.put("dry_run", dryRun);
        root.put("include_globs", includeGlobs);
        ArrayNode fileNodes = root.putArray("files");

        if (dryRun) {
            for (FileNode node : matched) {
                ObjectNode entry = fileNodes.addObject();
                entry.put("path", node.getPath());
            }
            root.put("estimated_tokens", 0);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        for (FileNode node : matched) {
            ObjectNode entry = fileNodes.addObject();
            entry.put("path", node.getPath());
            entry.set("type", withMeta(guessType(node.getPath()), 0.7, "METADATA_MATCH"));
            entry.set("pov", withMeta(extractPov(node.getPath()), 0.5, "FILENAME_MATCH"));
            entry.set("scene_number", withMeta(extractSceneNumber(node.getPath()), 0.5, "FILENAME_MATCH"));
            entry.put("size_bytes", fileSize(node.getPath()));
            entry.put("modified", fileModified(node.getPath()));
            entry.set("keywords", withKeywords(node.getPath()));
        }

        root.put("notes", scanMode.equals("DEEP_SCAN")
            ? "DEEP_SCAN requested; content not loaded in MVP."
            : "FAST_SCAN results; content not loaded.");
        return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private ToolRun executeFileReader(Map<String, Object> args) throws IOException {
        String filePath = stringArg(args, "file_path", "");
        if (filePath == null || filePath.isBlank()) {
            filePath = stringArg(args, "path", "");
        }
        int startLine = intArg(args, "start_line", 1);
        int endLine = intArg(args, "end_line", Math.max(startLine, startLine + 80));
        boolean includeLineNumbers = boolArg(args, "include_line_numbers", true);

        startLine = Math.max(1, startLine);
        endLine = Math.max(startLine, endLine);
        int maxLines = 250;
        if (endLine - startLine + 1 > maxLines) {
            endLine = startLine + maxLines - 1;
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "file_reader");
        root.put("file_path", filePath);
        root.put("start_line", startLine);
        root.put("end_line", endLine);
        root.put("include_line_numbers", includeLineNumbers);

        if (filePath == null || filePath.isBlank()) {
            root.put("error", "Missing required file_path.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        String content = readFile(filePath);
        if (content == null) {
            root.put("error", "File not found or unreadable.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }
        List<String> lines = List.of(content.split("\n", -1));
        root.put("total_lines", lines.size());

        int startIdx = Math.min(lines.size(), startLine - 1);
        int endIdxExclusive = Math.min(lines.size(), endLine);

        StringBuilder excerpt = new StringBuilder();
        for (int i = startIdx; i < endIdxExclusive; i++) {
            int lineNo = i + 1;
            if (includeLineNumbers) {
                excerpt.append(lineNo).append(": ");
            }
            excerpt.append(lines.get(i));
            if (i < endIdxExclusive - 1) excerpt.append("\n");
        }
        root.put("excerpt", excerpt.toString());
        return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private ToolRun executeOutlineAnalyzer(Map<String, Object> args) throws IOException {
        String outlinePath = stringArg(args, "outline_path", "");
        String mode = stringArg(args, "mode", "structure");
        boolean dryRun = boolArg(args, "dry_run", false);
        if (outlinePath == null || outlinePath.isBlank()) {
            outlinePath = "Story/SCN-outline.md";
        }
        if (dryRun) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "outline_analyzer");
            root.put("outline_path", outlinePath);
            root.put("mode", mode);
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        String content = readFile(outlinePath);
        if (content == null || content.isBlank()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "outline_analyzer");
            root.put("outline_path", outlinePath);
            root.put("error", "No outline file found or file is empty.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        List<String> lines = List.of(content.split("\n", -1));
        List<OutlineScene> scenes = parseOutlineScenes(lines);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "outline_analyzer");
        root.put("outline_path", outlinePath);
        root.put("mode", mode);
        root.put("total_scenes", scenes.size());
        root.put("total_lines", lines.size());

        ArrayNode scenesArray = root.putArray("scenes");
        for (OutlineScene scene : scenes) {
            ObjectNode entry = scenesArray.addObject();
            entry.put("number", scene.number);
            entry.put("title", scene.title);
            if (scene.pov != null && !scene.pov.isBlank()) {
                entry.put("pov", scene.pov);
            }
            entry.put("start_line", scene.startLine);
            entry.put("end_line", scene.endLine);
            entry.put("summary", scene.summary);
        }

        if (!"structure".equalsIgnoreCase(mode)) {
            root.put("content", content);
        }

        ToolRun run = ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        run.fileRefs.add(buildFileRef(outlinePath, content,
            content.length() > 500 ? content.substring(0, 500) : content));
        return run;
    }

    private List<OutlineScene> parseOutlineScenes(List<String> lines) {
        List<OutlineScene> scenes = new ArrayList<>();
        int sceneNumber = 0;
        int sceneStart = -1;
        String sceneTitle = "";
        String scenePov = null;
        StringBuilder summaryBuilder = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("## ")) {
                // Flush previous scene
                if (sceneStart >= 0) {
                    scenes.add(new OutlineScene(sceneNumber, sceneTitle, scenePov,
                        sceneStart, i, summaryBuilder.toString().trim()));
                }
                sceneNumber++;
                sceneStart = i + 1;
                sceneTitle = extractSceneTitle(line);
                scenePov = extractPovFromHeading(line);
                summaryBuilder = new StringBuilder();
            } else if (sceneStart >= 0 && !line.isEmpty()) {
                // Check for POV marker in body lines
                if (scenePov == null) {
                    String bodyPov = extractPovFromLine(line);
                    if (bodyPov != null) {
                        scenePov = bodyPov;
                    }
                }
                if (summaryBuilder.length() > 0) summaryBuilder.append(" ");
                summaryBuilder.append(line);
            }
        }
        // Flush last scene
        if (sceneStart >= 0) {
            scenes.add(new OutlineScene(sceneNumber, sceneTitle, scenePov,
                sceneStart, lines.size(), summaryBuilder.toString().trim()));
        }
        return scenes;
    }

    private String extractSceneTitle(String headingLine) {
        // "## Scene 3: The Anomaly" → "The Anomaly"
        // "## The Anomaly" → "The Anomaly"
        // "## Scene 3 - The Anomaly (POV: Serynthas)" → "The Anomaly"
        String raw = headingLine.replaceFirst("^##\\s*", "");
        // Strip POV parenthetical from end
        raw = raw.replaceFirst("\\s*\\(POV[:\\s][^)]*\\)\\s*$", "");
        raw = raw.replaceFirst("\\s*\\[POV[:\\s][^\\]]*\\]\\s*$", "");
        // Strip "Scene N:" or "Scene N -" prefix
        raw = raw.replaceFirst("^(?i)scene\\s+\\d+\\s*[:\\-–—]\\s*", "");
        return raw.trim();
    }

    private String extractPovFromHeading(String headingLine) {
        // Match (POV: Name) or [POV: Name] in heading
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?i)\\(?POV[:\\s]+([^)\\]]+)[)\\]]").matcher(headingLine);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractPovFromLine(String line) {
        // Match "POV: Name" or "**POV:** Name" at start of line
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?i)^\\*{0,2}POV\\*{0,2}[:\\s]+(.+)$").matcher(line);
        return m.find() ? m.group(1).trim() : null;
    }

    private static final class OutlineScene {
        final int number;
        final String title;
        final String pov;
        final int startLine;
        final int endLine;
        final String summary;
        String matchMethod; // set by matchSceneToBeat()

        OutlineScene(int number, String title, String pov, int startLine, int endLine, String summary) {
            this.number = number;
            this.title = title;
            this.pov = pov;
            this.startLine = startLine;
            this.endLine = endLine;
            // Keep summary concise — truncate if very long
            this.summary = summary != null && summary.length() > 200
                ? summary.substring(0, 197) + "..." : summary;
        }
    }

    private ToolRun executeCanonChecker(Map<String, Object> args) throws IOException {
        String sceneFile = stringArg(args, "scene_path", "");
        List<String> canonFiles = listArg(args, "canon_paths");
        String mode = stringArg(args, "mode", "strict");
        boolean dryRun = boolArg(args, "dry_run", false);
        if (canonFiles == null) canonFiles = List.of();

        if (dryRun) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "canon_checker");
            root.put("scene_path", sceneFile);
            ArrayNode paths = root.putArray("canon_paths");
            canonFiles.forEach(paths::add);
            root.put("mode", mode);
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        // Phase 1 (discovery): no canon_paths, or all guessed paths unreadable → return manifest
        if (canonFiles.isEmpty() || allCanonPathsUnreadable(canonFiles)) {
            return executeCanonCheckerDiscovery(sceneFile, mode);
        }

        // Phase 2 (check): canon_paths provided and at least one readable → compare
        return executeCanonCheckerCompare(sceneFile, canonFiles, mode);
    }

    private boolean allCanonPathsUnreadable(List<String> paths) {
        for (String path : paths) {
            if (path == null || path.isBlank()) continue;
            try {
                String content = readFile(path);
                if (content != null && !content.isBlank()) return false;
            } catch (IOException ignored) {}
        }
        return true;
    }

    private ToolRun executeCanonCheckerDiscovery(String sceneFile, String mode) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "canon_checker");
        root.put("phase", "discovery");
        root.put("scene_path", sceneFile);
        root.put("mode", mode);

        // Read scene for context
        String sceneContent = null;
        if (!sceneFile.isBlank()) {
            try {
                sceneContent = readFile(sceneFile);
            } catch (IOException ignored) {}
        }
        if (sceneContent != null && !sceneContent.isBlank()) {
            int sceneBudget = 600;
            String sceneExcerpt = sceneContent.length() > sceneBudget
                ? sceneContent.substring(0, sceneBudget) : sceneContent;
            root.put("scene_excerpt", sceneExcerpt);
            if (sceneContent.length() > sceneBudget) {
                root.put("scene_truncated", true);
                root.put("scene_total_chars", sceneContent.length());
            }
        }

        // Build manifest of all canon files (Compendium/)
        List<FileNode> allFiles = collectStoryFiles();
        List<FileNode> canonCandidates = allFiles.stream()
            .filter(f -> {
                String p = (f.getPath() != null ? f.getPath() : "").toLowerCase(Locale.ROOT);
                return p.contains("compendium") || p.contains("canon");
            })
            .collect(Collectors.toList());

        ArrayNode manifest = root.putArray("available_canon_files");
        for (FileNode f : canonCandidates) {
            ObjectNode entry = manifest.addObject();
            entry.put("path", f.getPath());
            entry.put("name", Path.of(f.getPath()).getFileName().toString());
            entry.put("category", guessCanonCategory(f.getPath()));
            entry.put("size_bytes", fileSize(f.getPath()));
        }
        root.put("total_canon_files", canonCandidates.size());
        root.put("instruction", "Select the canon files relevant to this scene and call canon_checker again with canon_paths.");

        ToolRun run = ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        if (sceneContent != null && !sceneContent.isBlank()) {
            run.fileRefs.add(buildFileRef(sceneFile, sceneContent,
                sceneContent.length() > 200 ? sceneContent.substring(0, 200) : sceneContent));
        }
        return run;
    }

    private ToolRun executeCanonCheckerCompare(String sceneFile, List<String> canonFiles, String mode)
            throws IOException {
        String sceneContent = readFile(sceneFile);
        if (sceneContent == null || sceneContent.isBlank()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "canon_checker");
            root.put("scene_path", sceneFile);
            root.put("error", "Scene file not found or empty.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "canon_checker");
        root.put("phase", "compare");
        root.put("scene_path", sceneFile);
        root.put("mode", mode);

        // Scene content — budget-aware
        int sceneBudget = 800;
        String sceneExcerpt = sceneContent.length() > sceneBudget
            ? sceneContent.substring(0, sceneBudget) : sceneContent;
        root.put("scene_content", sceneExcerpt);
        if (sceneContent.length() > sceneBudget) {
            root.put("scene_truncated", true);
            root.put("scene_total_chars", sceneContent.length());
        }

        // Canon files — split remaining budget evenly
        ArrayNode canonArray = root.putArray("canon_files");
        int canonBudget = Math.max(200, 1000 / canonFiles.size());
        ToolRun refCollector = ToolRun.of("");
        for (String canonPath : canonFiles) {
            ObjectNode canonEntry = canonArray.addObject();
            canonEntry.put("path", canonPath);
            try {
                String canonContent = readFile(canonPath);
                if (canonContent == null || canonContent.isBlank()) {
                    canonEntry.put("error", "File not found or empty.");
                    continue;
                }
                String canonExcerpt = canonContent.length() > canonBudget
                    ? canonContent.substring(0, canonBudget) : canonContent;
                canonEntry.put("content", canonExcerpt);
                if (canonContent.length() > canonBudget) {
                    canonEntry.put("truncated", true);
                    canonEntry.put("total_chars", canonContent.length());
                }
                refCollector.fileRefs.add(buildFileRef(canonPath, canonContent,
                    canonContent.length() > 200 ? canonContent.substring(0, 200) : canonContent));
            } catch (IOException e) {
                canonEntry.put("error", "Failed to read: " + e.getMessage());
            }
        }

        refCollector.fileRefs.add(buildFileRef(sceneFile, sceneContent,
            sceneContent.length() > 200 ? sceneContent.substring(0, 200) : sceneContent));

        String output = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        ToolRun result = ToolRun.of(output);
        result.fileRefs.addAll(refCollector.fileRefs);
        return result;
    }

    private String guessCanonCategory(String path) {
        if (path == null) return "unknown";
        String lower = path.toLowerCase(Locale.ROOT);
        // Check parent directory (VFS structure: Compendium/{Bucket}/...)
        if (lower.contains("/characters/")) return "character";
        if (lower.contains("/culture/")) return "culture";
        if (lower.contains("/lore/")) return "lore";
        if (lower.contains("/technology/")) return "technology";
        if (lower.contains("/themes/")) return "theme";
        if (lower.contains("/factions/")) return "faction";
        if (lower.contains("/glossary/")) return "glossary";
        if (lower.contains("/misc/")) return "misc";
        // Check VFS card prefixes (CHAR-, CONCEPT-, CULTURE-, etc.)
        String name = Path.of(path).getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.startsWith("char-")) return "character";
        if (name.startsWith("concept-")) return "lore";
        if (name.startsWith("culture-")) return "culture";
        if (name.startsWith("tech-")) return "technology";
        if (name.startsWith("theme-")) return "theme";
        if (name.startsWith("faction-")) return "faction";
        if (name.startsWith("gloss-")) return "glossary";
        // Fallback: physical file naming
        if (name.startsWith("characters-")) return "character";
        if (name.startsWith("lore-")) return "lore";
        if (name.startsWith("technology-")) return "technology";
        if (name.startsWith("themes-")) return "theme";
        if (name.startsWith("factions-")) return "faction";
        if (name.startsWith("glossary-")) return "glossary";
        return "other";
    }

    private ToolRun executeTaskRouter(Map<String, Object> args) throws IOException {
        String request = stringArg(args, "user_request", "");
        boolean dryRun = boolArg(args, "dry_run", false);
        String role = routeRole(request);
        String evidenceType = roleEvidenceType(role);
        String context = request.toLowerCase(Locale.ROOT).contains("outline")
            ? "Story/SCN-outline.md"
            : "needs file_locator scan";
        String output = "ROUTE: " + role + "\n" +
            "ORDER: " + role + "\n" +
            "PARALLEL: none\n" +
            "CONTEXT: " + context + "\n" +
            "EXPECTED_OUTPUT: one grounded issue with evidence\n" +
            "EVIDENCE_TYPE: " + evidenceType + "\n" +
            "COMPLEXITY: MEDIUM\n" +
            "ESTIMATED_TIME: 3-5 minutes\n" +
            "PRIORITY: NORMAL\n" +
            "FALLBACK: \n";
        if (dryRun) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "task_router");
            root.put("preview", output);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }
        return ToolRun.of(output);
    }

    private ToolRun executeSearchIssues(Map<String, Object> args) throws IOException {
        if (issueService == null) {
            return ToolRun.of("Issue service unavailable.");
        }
        List<Issue> issues = new ArrayList<>(issueService.listIssues());
        String assignedTo = stringArg(args, "assignedTo", "");
        String status = stringArg(args, "status", "open");
        String priority = stringArg(args, "priority", "");
        List<String> tags = listArg(args, "tags");
        if (!assignedTo.isBlank()) {
            issues = issues.stream()
                .filter(issue -> assignedTo.equalsIgnoreCase(issue.getAssignedTo()))
                .collect(Collectors.toList());
        }
        if (!priority.isBlank()) {
            issues = issues.stream()
                .filter(issue -> priority.equalsIgnoreCase(issue.getPriority()))
                .collect(Collectors.toList());
        }
        if (tags != null && !tags.isEmpty()) {
            issues = issues.stream()
                .filter(issue -> issue.getTags() != null && issue.getTags().containsAll(tags))
                .collect(Collectors.toList());
        }
        if (!"all".equalsIgnoreCase(status)) {
            issues = issues.stream()
                .filter(issue -> status.equalsIgnoreCase(issue.getStatus()))
                .collect(Collectors.toList());
        }
        issues.sort(Comparator.comparingLong(Issue::getCreatedAt).reversed());
        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "search_issues");
        ArrayNode results = root.putArray("issues");
        for (Issue issue : issues.stream().limit(20).collect(Collectors.toList())) {
            ObjectNode entry = results.addObject();
            entry.put("id", issue.getId());
            entry.put("title", safe(issue.getTitle()));
            entry.put("status", safe(issue.getStatus()));
            entry.put("priority", safe(issue.getPriority()));
            entry.put("assignedTo", safe(issue.getAssignedTo()));
        }
        root.put("note", "personalTags filters not implemented in tool runner MVP.");
        return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    // ── issue_status_summarizer ────────────────────────────────────

    private ToolRun executeIssueStatusSummarizer(Map<String, Object> args, ToolExecutionContext context) throws IOException {
        boolean dryRun = boolArg(args, "dry_run", false);
        if (dryRun) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "issue_status_summarizer");
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "issue_status_summarizer");

        // Issues
        ArrayNode active = root.putArray("active_issues");
        int openCount = 0;
        int waitingCount = 0;
        int closedCount = 0;
        if (issueService != null) {
            List<Issue> issues = new ArrayList<>(issueService.listIssues());
            issues.sort(Comparator.comparingLong(Issue::getUpdatedAt).reversed());
            for (Issue issue : issues) {
                String status = safe(issue.getStatus()).toLowerCase(Locale.ROOT);
                if ("closed".equals(status)) {
                    closedCount++;
                    continue;
                }
                if ("waiting-on-user".equals(status)) {
                    waitingCount++;
                } else if ("open".equals(status)) {
                    openCount++;
                } else {
                    openCount++;
                }
                ObjectNode entry = active.addObject();
                entry.put("id", issue.getId());
                entry.put("title", safe(issue.getTitle()));
                entry.put("assignedTo", safe(issue.getAssignedTo()));
                entry.put("status", safe(issue.getStatus()));
                entry.put("priority", safe(issue.getPriority()));
                entry.put("updatedAt", issue.getUpdatedAt());
                if (issue.getTags() != null && !issue.getTags().isEmpty()) {
                    ArrayNode tags = entry.putArray("tags");
                    issue.getTags().stream().limit(12).forEach(tags::add);
                }
                if (active.size() >= 20) break;
            }
        } else {
            root.put("issues_note", "Issue service unavailable.");
        }
        ObjectNode counts = root.putObject("issue_counts");
        counts.put("open", openCount);
        counts.put("waiting_on_user", waitingCount);
        counts.put("closed", closedCount);

        // Recent receipts for the current session (if available)
        String sessionId = context != null ? context.getSessionId() : null;
        if ((sessionId == null || sessionId.isBlank()) && context != null && context.getTaskId() != null) {
            sessionId = context.getTaskId();
        }
        String sessionIdSource = "context";
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = findLatestSessionWithReceipts();
            sessionIdSource = sessionId != null ? "latest" : "none";
        }
        ArrayNode recent = root.putArray("recent_activity");
        ArrayNode touched = root.putArray("files_touched");

        List<Map<String, Object>> receipts = readRecentToolReceipts(sessionId, 5);
        java.util.Set<String> touchedPaths = new java.util.LinkedHashSet<>();
        for (Map<String, Object> rcpt : receipts) {
            ObjectNode entry = recent.addObject();
            entry.put("receipt_id", safeObj(rcpt.get("receipt_id")));
            entry.put("agent_id", safeObj(rcpt.get("agent_id")));
            entry.put("tool_id", safeObj(rcpt.get("tool_id")));
            entry.put("timestamp", safeObj(rcpt.get("timestamp")));
            Object fileRefs = rcpt.get("file_refs");
            if (fileRefs instanceof List<?> list) {
                ArrayNode files = entry.putArray("file_refs");
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    Object p = m.get("path");
                    if (p != null) {
                        String path = p.toString();
                        files.add(path);
                        touchedPaths.add(path);
                    }
                }
            }
        }
        for (String path : touchedPaths.stream().limit(30).toList()) {
            touched.add(path);
        }
        if (receipts.isEmpty()) {
            root.put("recent_activity_note", sessionIdSource.equals("none")
                ? "No session id available in context; no receipts to summarize."
                : "No session receipts found for session_id=" + sessionId + ".");
        } else {
            root.put("session_id", sessionId);
            root.put("session_id_source", sessionIdSource);
        }

        root.put("status", (openCount + waitingCount) == 0
            ? "No issues currently tracked (or all closed)."
            : "Active issues present; see active_issues and recent_activity.");
        return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private String findLatestSessionWithReceipts() {
        if (projectContext == null || projectContext.audit() == null) return null;
        try {
            Path sessionsRoot = projectContext.audit().getSessionsRoot();
            if (sessionsRoot == null || !Files.exists(sessionsRoot)) return null;
            Path newest = null;
            long newestMs = -1;
            try (var stream = Files.list(sessionsRoot)) {
                for (Path dir : stream.toList()) {
                    if (!Files.isDirectory(dir)) continue;
                    Path receipts = dir.resolve("tool_receipts.jsonl");
                    if (!Files.exists(receipts)) continue;
                    long ms = Files.getLastModifiedTime(receipts).toMillis();
                    if (ms > newestMs) {
                        newestMs = ms;
                        newest = dir;
                    }
                }
            }
            return newest != null ? newest.getFileName().toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> readRecentToolReceipts(String sessionId, int limit) {
        if (limit <= 0) return List.of();
        if (projectContext == null || projectContext.audit() == null) return List.of();
        if (sessionId == null || sessionId.isBlank()) return List.of();
        try {
            Path receipts = projectContext.audit().getSessionsRoot()
                .resolve(sessionId.trim().replaceAll("[^a-zA-Z0-9._-]+", "_"))
                .resolve("tool_receipts.jsonl");
            if (!Files.exists(receipts)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(receipts, StandardCharsets.UTF_8);
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = Math.max(0, lines.size() - limit); i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) continue;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> node = objectMapper.readValue(line, Map.class);
                    result.add(node);
                } catch (Exception ignored) {
                }
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String safeObj(Object value) {
        return value == null ? "" : value.toString();
    }

    // ── stakes_mapper ──────────────────────────────────────────────

    private ToolRun executeStakesMapper(Map<String, Object> args) throws IOException {
        String outlinePath = stringArg(args, "outline_path", "Story/SCN-outline.md");
        List<String> scenePaths = listArg(args, "scene_paths");
        int maxScenes = intArg(args, "max_scenes", 20);
        boolean dryRun = boolArg(args, "dry_run", false);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "stakes_mapper");
        root.put("outline_path", outlinePath);
        root.put("max_scenes", maxScenes);
        if (scenePaths != null && !scenePaths.isEmpty()) {
            ArrayNode arr = root.putArray("scene_paths");
            scenePaths.forEach(arr::add);
        }
        if (dryRun) {
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ToolRun refCollector = ToolRun.of("");

        if (scenePaths != null && !scenePaths.isEmpty()) {
            ArrayNode scenes = root.putArray("scenes");
            for (String path : scenePaths.stream().limit(Math.max(1, maxScenes)).toList()) {
                ObjectNode entry = scenes.addObject();
                entry.put("path", path);
                try {
                    String content = readFile(path);
                    if (content == null || content.isBlank()) {
                        entry.put("error", "File not found or empty.");
                        continue;
                    }
                    entry.put("word_count", countWords(content));
                    entry.put("opening", excerptFirst(content, 400));
                    entry.put("ending", excerptLast(content, 400));
                    ArrayNode candidates = entry.putArray("stakes_candidates");
                    extractStakeCandidates(content).stream().limit(5).forEach(candidates::add);
                    refCollector.fileRefs.add(buildFileRef(path, content,
                        content.length() > 200 ? content.substring(0, 200) : content));
                } catch (IOException e) {
                    entry.put("error", "Failed to read: " + e.getMessage());
                }
            }
            String output = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            ToolRun run = ToolRun.of(output);
            run.fileRefs.addAll(refCollector.fileRefs);
            return run;
        }

        // Outline-driven stakes map (uses outline summaries)
        String outlineContent;
        try {
            outlineContent = readFile(outlinePath);
        } catch (IOException e) {
            outlineContent = null;
        }
        if (outlineContent == null || outlineContent.isBlank()) {
            root.put("error", "No outline file found or file is empty.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        List<String> lines = List.of(outlineContent.split("\n", -1));
        List<OutlineScene> scenes = parseOutlineScenes(lines);
        ArrayNode mapped = root.putArray("scenes");
        for (OutlineScene scene : scenes.stream().limit(Math.max(1, maxScenes)).toList()) {
            ObjectNode entry = mapped.addObject();
            entry.put("number", scene.number);
            entry.put("title", scene.title);
            if (scene.pov != null && !scene.pov.isBlank()) {
                entry.put("pov", scene.pov);
            }
            entry.put("start_line", scene.startLine);
            entry.put("end_line", scene.endLine);
            entry.put("summary", scene.summary);
            ArrayNode candidates = entry.putArray("stakes_candidates");
            extractStakeCandidates(scene.summary).stream().limit(3).forEach(candidates::add);
        }
        ToolRun run = ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        run.fileRefs.add(buildFileRef(outlinePath, outlineContent,
            outlineContent.length() > 200 ? outlineContent.substring(0, 200) : outlineContent));
        return run;
    }

    private List<String> extractStakeCandidates(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> sentences = new ArrayList<>();
        for (String s : text.split("(?<=[.!?])\\s+")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) sentences.add(trimmed);
        }
        java.util.List<String> hits = new ArrayList<>();
        for (String s : sentences) {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.contains("must") || lower.contains("risk") || lower.contains("threat")
                || lower.contains("or else") || lower.contains("if ") || lower.contains("unless")
                || lower.contains("lose") || lower.contains("loss") || lower.contains("save")
                || lower.contains("die") || lower.contains("survive") || lower.contains("consequence")
                || lower.contains("stakes")) {
                hits.add(trimQuote(s));
            }
        }
        if (!hits.isEmpty()) return hits;
        // Fallback: return first 2 sentences (for LLM to interpret stakes)
        return sentences.stream().limit(2).map(this::trimQuote).toList();
    }

    // ── prose_analyzer ──────────────────────────────────────────────

    private ToolRun executeProseAnalyzer(Map<String, Object> args) throws IOException {
        String scenePath = stringArg(args, "scene_path", "");
        String focus = stringArg(args, "focus", "all").toLowerCase(Locale.ROOT);
        boolean dryRun = boolArg(args, "dry_run", false);

        if (scenePath.isBlank()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "prose_analyzer");
            root.put("error", "scene_path is required.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        if (dryRun) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "prose_analyzer");
            root.put("scene_path", scenePath);
            root.put("focus", focus);
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        String content = readFile(scenePath);
        if (content == null || content.isBlank()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "prose_analyzer");
            root.put("scene_path", scenePath);
            root.put("error", "File not found or empty.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "prose_analyzer");
        root.put("scene_path", scenePath);
        root.put("focus", focus);

        // Compute metrics
        ObjectNode metrics = objectMapper.createObjectNode();

        String[] words = content.split("\\s+");
        int wordCount = 0;
        for (String w : words) {
            if (!w.isBlank()) wordCount++;
        }
        metrics.put("word_count", wordCount);

        List<String> sentences = splitSentences(content);
        metrics.put("sentence_count", sentences.size());

        String[] paragraphs = content.split("\n\\s*\n");
        int paragraphCount = 0;
        for (String p : paragraphs) {
            if (!p.trim().isEmpty()) paragraphCount++;
        }
        metrics.put("paragraph_count", paragraphCount);

        boolean includePacing = "all".equals(focus) || "pacing".equals(focus);
        boolean includeVoice = "all".equals(focus) || "voice".equals(focus);
        boolean includeRhythm = "all".equals(focus) || "rhythm".equals(focus);

        // Sentence length stats (pacing + rhythm)
        if (includePacing || includeRhythm) {
            int[] sentenceLengths = new int[sentences.size()];
            for (int i = 0; i < sentences.size(); i++) {
                sentenceLengths[i] = sentences.get(i).split("\\s+").length;
            }
            if (sentences.size() > 0) {
                double avg = 0;
                int longest = 0, shortest = Integer.MAX_VALUE;
                int longestIdx = 0, shortestIdx = 0;
                for (int i = 0; i < sentenceLengths.length; i++) {
                    avg += sentenceLengths[i];
                    if (sentenceLengths[i] > longest) { longest = sentenceLengths[i]; longestIdx = i; }
                    if (sentenceLengths[i] < shortest) { shortest = sentenceLengths[i]; shortestIdx = i; }
                }
                avg /= sentenceLengths.length;
                metrics.put("avg_sentence_length", Math.round(avg * 10.0) / 10.0);

                // Standard deviation
                double variance = 0;
                for (int len : sentenceLengths) {
                    variance += (len - avg) * (len - avg);
                }
                variance /= sentenceLengths.length;
                metrics.put("sentence_length_stdev", Math.round(Math.sqrt(variance) * 10.0) / 10.0);

                ObjectNode longestNode = metrics.putObject("longest_sentence");
                longestNode.put("length", longest);
                longestNode.put("text", trimQuote(sentences.get(longestIdx)));

                ObjectNode shortestNode = metrics.putObject("shortest_sentence");
                shortestNode.put("length", shortest);
                shortestNode.put("text", trimQuote(sentences.get(shortestIdx)));
            }
        }

        // Voice metrics
        if (includeVoice) {
            // Dialogue ratio: lines with at least one pair of quotes / total non-blank lines
            String[] lines = content.split("\n");
            int dialogueLines = 0;
            int nonBlankLines = 0;
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                nonBlankLines++;
                // Count quote marks — a pair means dialogue
                long quoteCount = line.chars().filter(c -> c == '\u201C' || c == '\u201D'
                    || c == '"').count();
                if (quoteCount >= 2) dialogueLines++;
            }
            metrics.put("dialogue_ratio",
                nonBlankLines > 0 ? Math.round((double) dialogueLines / nonBlankLines * 100.0) / 100.0 : 0.0);

            // POV signals
            ObjectNode pov = metrics.putObject("pov_signals");
            String lower = content.toLowerCase(Locale.ROOT);
            String[] tokens = lower.split("[^a-z']+");
            int first = 0, second = 0, third = 0;
            for (String t : tokens) {
                switch (t) {
                    case "i": case "me": case "my": case "mine": case "myself":
                        first++; break;
                    case "you": case "your": case "yours": case "yourself":
                        second++; break;
                    case "he": case "she": case "him": case "her": case "his":
                    case "hers": case "they": case "them": case "their": case "theirs":
                    case "its": case "itself": case "himself": case "herself": case "themselves":
                        third++; break;
                }
            }
            pov.put("first_person", first);
            pov.put("second_person", second);
            pov.put("third_person", third);
        }

        // Rhythm metrics
        if (includeRhythm) {
            // Adverb count (words ending in -ly, excluding common non-adverbs)
            String[] tokens = content.toLowerCase(Locale.ROOT).split("[^a-z]+");
            int adverbCount = 0;
            for (String t : tokens) {
                if (t.length() > 3 && t.endsWith("ly") && !ADVERB_EXCEPTIONS.contains(t)) {
                    adverbCount++;
                }
            }
            metrics.put("adverb_count", adverbCount);

            // Repeated words (non-stopwords, top 10)
            Map<String, Integer> freq = new java.util.LinkedHashMap<>();
            for (String t : tokens) {
                if (t.length() < 4 || STOPWORDS.contains(t)) continue;
                freq.merge(t, 1, Integer::sum);
            }
            ArrayNode repeated = metrics.putArray("repeated_words");
            freq.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    ObjectNode entry = repeated.addObject();
                    entry.put("word", e.getKey());
                    entry.put("count", e.getValue());
                });
        }

        root.set("metrics", metrics);

        // Content excerpt (budget-aware, same as canon_checker)
        int contentBudget = 800;
        String contentExcerpt = content.length() > contentBudget
            ? content.substring(0, contentBudget) : content;
        root.put("content", contentExcerpt);
        if (content.length() > contentBudget) {
            root.put("content_truncated", true);
            root.put("content_total_chars", content.length());
        }

        ToolRun run = ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        run.fileRefs.add(buildFileRef(scenePath, content,
            content.length() > 200 ? content.substring(0, 200) : content));
        return run;
    }

    // ── line_editor ────────────────────────────────────────────────

    private ToolRun executeLineEditor(Map<String, Object> args) throws IOException {
        String filePath = stringArg(args, "file_path", "");
        String text = stringArg(args, "text", "");
        int startLine = intArg(args, "start_line", -1);
        int endLine = intArg(args, "end_line", -1);
        int maxEdits = intArg(args, "max_edits", 12);
        boolean dryRun = boolArg(args, "dry_run", false);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "line_editor");
        root.put("max_edits", maxEdits);
        if (!filePath.isBlank()) root.put("file_path", filePath);
        if (startLine > 0) root.put("start_line", startLine);
        if (endLine > 0) root.put("end_line", endLine);

        if (dryRun) {
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        String content = null;
        ToolRun refCollector = ToolRun.of("");
        if (!text.isBlank()) {
            content = text;
            root.put("source", "inline_text");
        } else if (!filePath.isBlank()) {
            content = readFile(filePath);
            if (content == null || content.isBlank()) {
                root.put("error", "File not found or empty.");
                return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            }
            root.put("source", "file");
            refCollector.fileRefs.add(buildFileRef(filePath, content,
                content.length() > 200 ? content.substring(0, 200) : content));
            if (startLine > 0 || endLine > 0) {
                content = sliceLines(content, startLine, endLine);
            }
        } else {
            root.put("error", "Provide either text or file_path.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        root.put("word_count", countWords(content));
        root.put("excerpt", content.length() > 900 ? content.substring(0, 900) : content);

        ArrayNode findings = root.putArray("findings");
        int remaining = Math.max(1, maxEdits);

        // Mechanical cleanup suggestions (safe edits)
        if (content.contains("  ")) {
            ObjectNode f = findings.addObject();
            f.put("kind", "whitespace");
            f.put("original", "Contains multiple consecutive spaces.");
            f.put("suggestion", "Collapse consecutive spaces to a single space where appropriate.");
            remaining--;
        }

        // Long sentences
        List<String> sentences = splitSentences(content);
        int longCount = 0;
        for (String s : sentences) {
            if (remaining <= 0) break;
            int len = s.split("\\s+").length;
            if (len >= 30) {
                ObjectNode f = findings.addObject();
                f.put("kind", "long_sentence");
                f.put("word_count", len);
                f.put("original", trimQuote(s));
                f.put("suggestion", "Consider splitting or tightening; look for optional clauses or stacked prepositional phrases.");
                remaining--;
                longCount++;
                if (longCount >= 6) break;
            }
        }

        // Filler words and -ly adverbs (flag; do not rewrite meaning)
        if (remaining > 0) {
            List<String> fillerHits = findFillerPhrases(content);
            for (String hit : fillerHits.stream().limit(remaining).toList()) {
                ObjectNode f = findings.addObject();
                f.put("kind", "filler_word");
                f.put("original", hit);
                f.put("suggestion", "If it isn't doing intentional voice work, remove or replace with a concrete action/sensory detail.");
                remaining--;
                if (remaining <= 0) break;
            }
        }

        if (remaining > 0) {
            List<String> adverbs = findAdverbs(content);
            for (String adv : adverbs.stream().limit(remaining).toList()) {
                ObjectNode f = findings.addObject();
                f.put("kind", "adverb");
                f.put("original", adv);
                f.put("suggestion", "If the verb can carry the meaning alone, remove the adverb or choose a stronger verb.");
                remaining--;
                if (remaining <= 0) break;
            }
        }

        // Repeated words (top offenders)
        if (remaining > 0) {
            Map<String, Integer> freq = extractTermFrequencies(content);
            for (Map.Entry<String, Integer> e : freq.entrySet().stream()
                .filter(en -> en.getValue() >= 4)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .toList()) {
                if (remaining <= 0) break;
                ObjectNode f = findings.addObject();
                f.put("kind", "repetition");
                f.put("word", e.getKey());
                f.put("count", e.getValue());
                f.put("suggestion", "Check nearby sentences for repeated phrasing; vary the wording or remove redundancy.");
                remaining--;
            }
        }

        root.put("note", "This tool flags line-level edit opportunities deterministically; the model should propose final rewrites while preserving meaning/voice.");
        String output = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        ToolRun run = ToolRun.of(output);
        run.fileRefs.addAll(refCollector.fileRefs);
        return run;
    }

    private String sliceLines(String content, int startLine, int endLine) {
        if (content == null) return "";
        if (startLine <= 0 && endLine <= 0) return content;
        List<String> lines = List.of(content.split("\n", -1));
        int start = Math.max(1, startLine > 0 ? startLine : 1);
        int end = endLine > 0 ? Math.min(lines.size(), endLine) : lines.size();
        if (start > end) return "";
        return String.join("\n", lines.subList(start - 1, end));
    }

    private List<String> findFillerPhrases(String content) {
        if (content == null || content.isBlank()) return List.of();
        String[] fillers = new String[] {" really ", " very ", " just ", " actually ", " basically ", " suddenly ", " somewhat ", " quite "};
        List<String> hits = new ArrayList<>();
        String lower = " " + content.toLowerCase(Locale.ROOT).replace('\n', ' ') + " ";
        for (String f : fillers) {
            int idx = lower.indexOf(f);
            if (idx >= 0) {
                hits.add(f.trim());
            }
        }
        return hits;
    }

    private List<String> findAdverbs(String content) {
        if (content == null || content.isBlank()) return List.of();
        String[] tokens = content.toLowerCase(Locale.ROOT).split("[^a-z]+");
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String t : tokens) {
            if (t.length() > 3 && t.endsWith("ly") && !ADVERB_EXCEPTIONS.contains(t)) {
                seen.add(t);
            }
        }
        return new ArrayList<>(seen);
    }

    // ── consistency_checker ────────────────────────────────────────

    private ToolRun executeConsistencyChecker(Map<String, Object> args) throws IOException {
        List<String> filePaths = listArg(args, "file_paths");
        String focus = stringArg(args, "focus", "general").toLowerCase(Locale.ROOT);
        boolean dryRun = boolArg(args, "dry_run", false);

        if (filePaths == null || filePaths.isEmpty()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "consistency_checker");
            root.put("error", "file_paths is required (provide at least 2 files).");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }
        if (filePaths.size() > 10) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "consistency_checker");
            root.put("error", "Too many files (" + filePaths.size() + "). Maximum is 10.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        if (dryRun) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "consistency_checker");
            root.put("focus", focus);
            root.put("dry_run", true);
            root.put("file_count", filePaths.size());
            ArrayNode paths = root.putArray("file_paths");
            filePaths.forEach(paths::add);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        // Read all files
        Map<String, String> fileContents = new java.util.LinkedHashMap<>();
        for (String path : filePaths) {
            try {
                String content = readFile(path);
                if (content != null && !content.isBlank()) {
                    fileContents.put(path, content);
                }
            } catch (IOException ignored) {}
        }

        if (fileContents.isEmpty()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "consistency_checker");
            root.put("error", "No readable files found.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "consistency_checker");
        root.put("focus", focus);
        root.put("file_count", fileContents.size());

        boolean includeEntities = "general".equals(focus) || "characters".equals(focus);
        boolean includeTerms = "general".equals(focus) || "terminology".equals(focus);
        boolean includeEvents = "general".equals(focus) || "events".equals(focus);

        // Per-file data + entity extraction
        Map<String, List<String>> entitiesByFile = new java.util.LinkedHashMap<>();
        Map<String, Map<String, Integer>> termsByFile = new java.util.LinkedHashMap<>();

        ArrayNode filesArray = root.putArray("files");
        int contentBudget = Math.max(200, 2000 / fileContents.size());

        ToolRun refCollector = ToolRun.of("");

        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();

            ObjectNode fileNode = filesArray.addObject();
            fileNode.put("path", path);

            String[] words = content.split("\\s+");
            int wc = 0;
            for (String w : words) { if (!w.isBlank()) wc++; }
            fileNode.put("word_count", wc);

            // Extract entities (named proper nouns)
            if (includeEntities) {
                List<String> entities = extractEntities(content);
                entitiesByFile.put(path, entities);
                ArrayNode entArr = fileNode.putArray("entities");
                entities.stream().limit(30).forEach(entArr::add);
            }

            // Extract term frequencies (for cross-referencing)
            if (includeTerms) {
                Map<String, Integer> terms = extractTermFrequencies(content);
                termsByFile.put(path, terms);
            }

            // Event markers
            if (includeEvents) {
                List<String> eventLines = extractEventLines(content);
                if (!eventLines.isEmpty()) {
                    ArrayNode evArr = fileNode.putArray("event_markers");
                    eventLines.stream().limit(10).forEach(evArr::add);
                }
            }

            // Budget-aware content excerpt
            String excerpt = content.length() > contentBudget
                ? content.substring(0, contentBudget) : content;
            fileNode.put("content", excerpt);
            if (content.length() > contentBudget) {
                fileNode.put("content_truncated", true);
                fileNode.put("content_total_chars", content.length());
            }

            refCollector.fileRefs.add(buildFileRef(path, content,
                content.length() > 200 ? content.substring(0, 200) : content));
        }

        // Cross-references: entities appearing in 2+ files
        if (includeEntities && entitiesByFile.size() >= 2) {
            // Build entity → {file → count} map
            Map<String, Map<String, Integer>> entityIndex = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : entitiesByFile.entrySet()) {
                String path = e.getKey();
                Map<String, Integer> freq = new java.util.LinkedHashMap<>();
                for (String ent : e.getValue()) {
                    freq.merge(ent, 1, Integer::sum);
                }
                for (Map.Entry<String, Integer> f : freq.entrySet()) {
                    entityIndex.computeIfAbsent(f.getKey(), k -> new java.util.LinkedHashMap<>())
                        .put(path, f.getValue());
                }
            }

            ArrayNode crossRefs = root.putArray("cross_references");
            int crossRefCount = 0;
            for (Map.Entry<String, Map<String, Integer>> e : entityIndex.entrySet()) {
                if (e.getValue().size() < 2) continue;
                if (crossRefCount >= 20) break;
                ObjectNode ref = crossRefs.addObject();
                ref.put("entity", e.getKey());
                ArrayNode refFiles = ref.putArray("files");
                ArrayNode refCounts = ref.putArray("counts");
                for (Map.Entry<String, Integer> f : e.getValue().entrySet()) {
                    refFiles.add(f.getKey());
                    refCounts.add(f.getValue());
                }
                crossRefCount++;
            }
        }

        // Shared terms: non-stopwords appearing in 2+ files
        if (includeTerms && termsByFile.size() >= 2) {
            // Merge term frequencies across files
            Map<String, Map<String, Integer>> termIndex = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Integer>> e : termsByFile.entrySet()) {
                String path = e.getKey();
                for (Map.Entry<String, Integer> t : e.getValue().entrySet()) {
                    termIndex.computeIfAbsent(t.getKey(), k -> new java.util.LinkedHashMap<>())
                        .put(path, t.getValue());
                }
            }

            ArrayNode sharedTerms = root.putArray("shared_terms");
            termIndex.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .sorted((a, b) -> {
                    int totalA = a.getValue().values().stream().mapToInt(Integer::intValue).sum();
                    int totalB = b.getValue().values().stream().mapToInt(Integer::intValue).sum();
                    return Integer.compare(totalB, totalA);
                })
                .limit(15)
                .forEach(e -> {
                    ObjectNode termNode = sharedTerms.addObject();
                    termNode.put("term", e.getKey());
                    ArrayNode termFiles = termNode.putArray("files");
                    e.getValue().keySet().forEach(termFiles::add);
                    termNode.put("total_count",
                        e.getValue().values().stream().mapToInt(Integer::intValue).sum());
                });
        }

        String output = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        ToolRun result = ToolRun.of(output);
        result.fileRefs.addAll(refCollector.fileRefs);
        return result;
    }

    private List<String> extractEntities(String content) {
        // Extract proper nouns: capitalized words not at sentence starts, merged into multi-word entities
        List<String> entities = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        String[] lines = content.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            // Skip markdown headings and blank lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            String[] words = trimmed.split("\\s+");
            StringBuilder multiWord = new StringBuilder();

            for (int i = 0; i < words.length; i++) {
                String word = words[i].replaceAll("[^a-zA-Z'-]", "");
                if (word.isEmpty()) {
                    flushEntity(multiWord, seen, entities);
                    continue;
                }

                boolean isCapitalized = Character.isUpperCase(word.charAt(0)) && word.length() > 1;
                boolean isSentenceStart = (i == 0) || (i > 0 && words[i - 1].matches(".*[.!?]$"));

                if (isCapitalized && !isSentenceStart) {
                    if (multiWord.length() > 0) multiWord.append(" ");
                    multiWord.append(word);
                } else if (isCapitalized && isSentenceStart && multiWord.length() > 0) {
                    // Sentence start but we have an ongoing multi-word — flush and start fresh
                    flushEntity(multiWord, seen, entities);
                } else {
                    flushEntity(multiWord, seen, entities);
                }
            }
            flushEntity(multiWord, seen, entities);
        }
        return entities;
    }

    private void flushEntity(StringBuilder multiWord, Set<String> seen, List<String> entities) {
        if (multiWord.length() == 0) return;
        String entity = multiWord.toString();
        multiWord.setLength(0);
        // Filter: skip very short, common words, articles
        if (entity.length() <= 1) return;
        if (ENTITY_STOPWORDS.contains(entity.toLowerCase(Locale.ROOT))) return;
        if (!seen.contains(entity)) {
            seen.add(entity);
            entities.add(entity);
        }
    }

    private Map<String, Integer> extractTermFrequencies(String content) {
        String[] tokens = content.toLowerCase(Locale.ROOT).split("[^a-z]+");
        Map<String, Integer> freq = new java.util.LinkedHashMap<>();
        for (String t : tokens) {
            if (t.length() < 4 || STOPWORDS.contains(t)) continue;
            freq.merge(t, 1, Integer::sum);
        }
        return freq;
    }

    private List<String> extractEventLines(String content) {
        // Find lines containing time markers
        List<String> events = new ArrayList<>();
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (EVENT_PATTERN.matcher(lower).find()) {
                events.add(trimQuote(trimmed));
            }
        }
        return events;
    }

    private static final java.util.regex.Pattern EVENT_PATTERN = java.util.regex.Pattern.compile(
        "\\b(later|ago|before|after|during|yesterday|tomorrow|morning|evening|night|dawn|dusk"
        + "|year|month|week|day|hour|minute|season|cycle|epoch"
        + "|first|second|third|fourth|fifth|final|last"
        + "|began|ended|arrived|departed|returned)\\b"
    );

    private static final Set<String> ENTITY_STOPWORDS = Set.of(
        "the", "and", "but", "for", "not", "its", "this", "that",
        "with", "from", "they", "them", "their", "there", "then",
        "been", "were", "have", "had", "has", "will", "would",
        "could", "should", "may", "might", "shall", "can",
        "which", "where", "when", "what", "who", "whom",
        "some", "any", "all", "each", "every", "both",
        "many", "much", "more", "most", "other", "another"
    );

    // ── scene_draft_validator ───────────────────────────────────────

    private ToolRun executeSceneDraftValidator(Map<String, Object> args) throws IOException {
        String scenePath = stringArg(args, "scene_path", "");
        String outlinePath = stringArg(args, "outline_path", "Story/SCN-outline.md");
        boolean includeCanon = boolArg(args, "include_canon", true);
        boolean dryRun = boolArg(args, "dry_run", false);

        if (scenePath.isBlank()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "scene_draft_validator");
            root.put("error", "scene_path is required.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }
        if (outlinePath == null || outlinePath.isBlank()) {
            outlinePath = "Story/SCN-outline.md";
        }

        if (dryRun) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "scene_draft_validator");
            root.put("scene_path", scenePath);
            root.put("outline_path", outlinePath);
            root.put("include_canon", includeCanon);
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        // Read scene
        String sceneContent = readFile(scenePath);
        if (sceneContent == null || sceneContent.isBlank()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "scene_draft_validator");
            root.put("scene_path", scenePath);
            root.put("error", "Scene file not found or empty.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "scene_draft_validator");
        root.put("scene_path", scenePath);

        // Scene word count
        String[] words = sceneContent.split("\\s+");
        int wc = 0;
        for (String w : words) { if (!w.isBlank()) wc++; }
        root.put("scene_word_count", wc);

        // Scene content excerpt
        int sceneBudget = 800;
        String sceneExcerpt = sceneContent.length() > sceneBudget
            ? sceneContent.substring(0, sceneBudget) : sceneContent;
        root.put("scene_content", sceneExcerpt);
        if (sceneContent.length() > sceneBudget) {
            root.put("scene_content_truncated", true);
            root.put("scene_total_chars", sceneContent.length());
        }

        ToolRun refCollector = ToolRun.of("");
        refCollector.fileRefs.add(buildFileRef(scenePath, sceneContent,
            sceneContent.length() > 200 ? sceneContent.substring(0, 200) : sceneContent));

        // Read and parse outline
        ObjectNode outlineMatch = root.putObject("outline_match");
        String outlineContent = null;
        try {
            outlineContent = readFile(outlinePath);
        } catch (IOException ignored) {}

        if (outlineContent == null || outlineContent.isBlank()) {
            outlineMatch.put("matched", false);
            outlineMatch.put("error", "Outline file not found or empty at " + outlinePath);
        } else {
            List<String> outlineLines = List.of(outlineContent.split("\n", -1));
            List<OutlineScene> scenes = parseOutlineScenes(outlineLines);

            OutlineScene matched = matchSceneToBeat(scenePath, scenes);
            if (matched != null) {
                outlineMatch.put("matched", true);
                outlineMatch.put("scene_number", matched.number);
                outlineMatch.put("title", matched.title);
                if (matched.pov != null && !matched.pov.isBlank()) {
                    outlineMatch.put("pov", matched.pov);
                }
                outlineMatch.put("summary", matched.summary);
                outlineMatch.put("match_method", matched.matchMethod != null ? matched.matchMethod : "title_slug");
            } else {
                outlineMatch.put("matched", false);
                // Return all beats as candidates
                ArrayNode candidates = outlineMatch.putArray("candidates");
                for (OutlineScene scene : scenes) {
                    ObjectNode c = candidates.addObject();
                    c.put("number", scene.number);
                    c.put("title", scene.title);
                    if (scene.pov != null) c.put("pov", scene.pov);
                    c.put("summary", scene.summary);
                }
            }

            // Canon card lookup
            if (includeCanon) {
                String povName = null;
                if (matched != null && matched.pov != null && !matched.pov.isBlank()) {
                    povName = matched.pov;
                }
                // Fallback: try to extract POV from scene filename
                if (povName == null) {
                    povName = extractPovFromScenePath(scenePath);
                }
                if (povName != null) {
                    ObjectNode canonCard = findAndReadPovCanonCard(povName, refCollector);
                    if (canonCard != null) {
                        root.set("canon_card", canonCard);
                    }
                }
            }
        }

        String output = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        ToolRun result = ToolRun.of(output);
        result.fileRefs.addAll(refCollector.fileRefs);
        return result;
    }

    // ── scene_impact_analyzer ──────────────────────────────────────

    private ToolRun executeSceneImpactAnalyzer(Map<String, Object> args) throws IOException {
        String scenePath = stringArg(args, "scene_path", "");
        String outlinePath = stringArg(args, "outline_path", "Story/SCN-outline.md");
        boolean dryRun = boolArg(args, "dry_run", false);

        if (scenePath.isBlank()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "scene_impact_analyzer");
            root.put("error", "scene_path is required.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        if (dryRun) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "scene_impact_analyzer");
            root.put("scene_path", scenePath);
            root.put("outline_path", outlinePath);
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        String content = readFile(scenePath);
        if (content == null || content.isBlank()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "scene_impact_analyzer");
            root.put("scene_path", scenePath);
            root.put("error", "File not found or empty.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "scene_impact_analyzer");
        root.put("scene_path", scenePath);
        root.put("word_count", countWords(content));

        // Opening + ending excerpts
        root.put("opening", excerptFirst(content, 550));
        root.put("ending", excerptLast(content, 550));

        // Simple impact signals
        ObjectNode signals = root.putObject("impact_signals");
        signals.put("question_marks", countChar(content, '?'));
        signals.put("exclamation_marks", countChar(content, '!'));
        signals.put("ellipsis_count", countSubstring(content, "..."));
        signals.put("dialogue_ratio", computeDialogueRatio(content));
        signals.put("conflict_marker_lines", countConflictLines(content));

        // Optional outline match (best-effort)
        ObjectNode beat = root.putObject("outline_match");
        try {
            String outlineContent = readFile(outlinePath);
            if (outlineContent != null && !outlineContent.isBlank()) {
                List<OutlineScene> scenes = parseOutlineScenes(List.of(outlineContent.split("\n", -1)));
                OutlineScene matched = matchSceneToBeat(scenePath, scenes);
                if (matched != null) {
                    beat.put("matched", true);
                    beat.put("scene_number", matched.number);
                    beat.put("title", matched.title);
                    if (matched.pov != null && !matched.pov.isBlank()) {
                        beat.put("pov", matched.pov);
                    }
                    beat.put("summary", matched.summary);
                    beat.put("match_method", matched.matchMethod != null ? matched.matchMethod : "title_slug");
                } else {
                    beat.put("matched", false);
                    beat.put("note", "No outline beat match found.");
                }
            } else {
                beat.put("matched", false);
                beat.put("note", "Outline file not found or empty at " + outlinePath);
            }
        } catch (IOException e) {
            beat.put("matched", false);
            beat.put("note", "Failed to read outline: " + e.getMessage());
        }

        ToolRun run = ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        run.fileRefs.add(buildFileRef(scenePath, content,
            content.length() > 200 ? content.substring(0, 200) : content));
        return run;
    }

    private int countChar(String content, char c) {
        if (content == null) return 0;
        return (int) content.chars().filter(ch -> ch == c).count();
    }

    private int countSubstring(String content, String sub) {
        if (content == null || sub == null || sub.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private double computeDialogueRatio(String content) {
        if (content == null || content.isBlank()) return 0.0;
        String[] lines = content.split("\n");
        int dialogueLines = 0;
        int nonBlankLines = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            nonBlankLines++;
            long quoteCount = line.chars().filter(c -> c == '\u201C' || c == '\u201D' || c == '"').count();
            if (quoteCount >= 2) dialogueLines++;
        }
        if (nonBlankLines == 0) return 0.0;
        return Math.round(((double) dialogueLines / nonBlankLines) * 100.0) / 100.0;
    }

    private int countConflictLines(String content) {
        if (content == null || content.isBlank()) return 0;
        String[] lines = content.split("\n");
        int count = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            String lower = t.toLowerCase(Locale.ROOT);
            if (lower.contains("but ") || lower.contains("however") || lower.contains("yet ")
                || lower.contains("unless") || lower.contains("instead") || lower.contains("can't")
                || lower.contains("cannot") || lower.contains("won't") || lower.contains("won’t")) {
                count++;
            }
        }
        return count;
    }

    // ── reader_experience_simulator ────────────────────────────────

    private ToolRun executeReaderExperienceSimulator(Map<String, Object> args) throws IOException {
        List<String> scenePaths = listArg(args, "scene_paths");
        boolean dryRun = boolArg(args, "dry_run", false);

        if (scenePaths == null || scenePaths.isEmpty()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "reader_experience_simulator");
            root.put("error", "scene_paths is required.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }
        if (scenePaths.size() > 12) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "reader_experience_simulator");
            root.put("error", "Too many scene_paths (" + scenePaths.size() + "). Maximum is 12.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "reader_experience_simulator");
        ArrayNode paths = root.putArray("scene_paths");
        scenePaths.forEach(paths::add);

        if (dryRun) {
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ArrayNode scenes = root.putArray("scenes");
        ToolRun refCollector = ToolRun.of("");

        List<SceneSnapshot> snapshots = new ArrayList<>();
        for (String path : scenePaths) {
            ObjectNode entry = scenes.addObject();
            entry.put("path", path);
            try {
                String content = readFile(path);
                if (content == null || content.isBlank()) {
                    entry.put("error", "File not found or empty.");
                    continue;
                }
                int wc = countWords(content);
                entry.put("word_count", wc);
                entry.put("dialogue_ratio", computeDialogueRatio(content));
                String opening = excerptFirst(content, 450);
                String ending = excerptLast(content, 450);
                entry.put("opening", opening);
                entry.put("ending", ending);

                List<String> entities = extractEntities(content);
                ArrayNode entArr = entry.putArray("entities");
                entities.stream().limit(20).forEach(entArr::add);

                snapshots.add(new SceneSnapshot(path, opening, ending));
                refCollector.fileRefs.add(buildFileRef(path, content,
                    content.length() > 200 ? content.substring(0, 200) : content));
            } catch (IOException e) {
                entry.put("error", "Failed to read: " + e.getMessage());
            }
        }

        ArrayNode transitions = root.putArray("transitions");
        for (int i = 0; i + 1 < snapshots.size(); i++) {
            SceneSnapshot a = snapshots.get(i);
            SceneSnapshot b = snapshots.get(i + 1);
            ObjectNode t = transitions.addObject();
            t.put("from", a.path);
            t.put("to", b.path);
            t.put("from_ending", a.ending);
            t.put("to_opening", b.opening);
        }

        String output = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        ToolRun run = ToolRun.of(output);
        run.fileRefs.addAll(refCollector.fileRefs);
        return run;
    }

    private static final class SceneSnapshot {
        final String path;
        final String opening;
        final String ending;

        SceneSnapshot(String path, String opening, String ending) {
            this.path = path;
            this.opening = opening;
            this.ending = ending;
        }
    }

    // ── timeline_validator ─────────────────────────────────────────

    private ToolRun executeTimelineValidator(Map<String, Object> args) throws IOException {
        List<String> filePaths = listArg(args, "file_paths");
        boolean dryRun = boolArg(args, "dry_run", false);

        if (filePaths == null || filePaths.isEmpty()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "timeline_validator");
            root.put("error", "file_paths is required.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }
        if (filePaths.size() > 12) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "timeline_validator");
            root.put("error", "Too many file_paths (" + filePaths.size() + "). Maximum is 12.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "timeline_validator");
        ArrayNode paths = root.putArray("file_paths");
        filePaths.forEach(paths::add);
        if (dryRun) {
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        ArrayNode files = root.putArray("files");
        ArrayNode timeline = root.putArray("timeline");
        ToolRun refCollector = ToolRun.of("");

        int timelineCount = 0;
        for (String path : filePaths) {
            ObjectNode fileNode = files.addObject();
            fileNode.put("path", path);
            String content;
            try {
                content = readFile(path);
            } catch (IOException e) {
                fileNode.put("error", "Failed to read: " + e.getMessage());
                continue;
            }
            if (content == null || content.isBlank()) {
                fileNode.put("error", "File not found or empty.");
                continue;
            }
            fileNode.put("word_count", countWords(content));
            ArrayNode markers = fileNode.putArray("time_markers");
            List<TimeMarker> extracted = extractTimeMarkers(content);
            for (TimeMarker m : extracted.stream().limit(30).toList()) {
                ObjectNode marker = markers.addObject();
                marker.put("line", m.line);
                marker.put("text", m.text);
            }
            for (TimeMarker m : extracted) {
                if (timelineCount >= 60) break;
                ObjectNode ev = timeline.addObject();
                ev.put("file", path);
                ev.put("line", m.line);
                ev.put("quote", m.text);
                timelineCount++;
            }
            refCollector.fileRefs.add(buildFileRef(path, content,
                content.length() > 200 ? content.substring(0, 200) : content));
        }

        root.put("note", "Timeline entries are extracted in reading order. The model should reconcile chronology and flag contradictions using the quotes/line numbers.");
        String output = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        ToolRun run = ToolRun.of(output);
        run.fileRefs.addAll(refCollector.fileRefs);
        return run;
    }

    private static final class TimeMarker {
        final int line;
        final String text;

        TimeMarker(int line, String text) {
            this.line = line;
            this.text = text;
        }
    }

    private List<TimeMarker> extractTimeMarkers(String content) {
        if (content == null || content.isBlank()) return List.of();
        List<TimeMarker> markers = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (EVENT_PATTERN.matcher(lower).find() || TIME_PATTERN.matcher(lower).find()) {
                // IMPORTANT: Evidence validator checks quoted text exists verbatim in the file content.
                // Do not append "..." here; if we must truncate, return a prefix-only excerpt.
                markers.add(new TimeMarker(i + 1, quotePrefix(trimmed, 220)));
            }
        }
        return markers;
    }

    private String quotePrefix(String text, int maxChars) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (maxChars <= 0) return "";
        if (trimmed.length() <= maxChars) return trimmed;
        // No ellipsis: keep it as a strict prefix so it can still be found in the source file.
        return trimmed.substring(0, maxChars);
    }

    private static final java.util.regex.Pattern TIME_PATTERN = java.util.regex.Pattern.compile(
        "\\b(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}:\\d{2}\\s*(am|pm)?"
            + "|jan(uary)?|feb(ruary)?|mar(ch)?|apr(il)?|may|jun(e)?|jul(y)?|aug(ust)?"
            + "|sep(tember)?|oct(ober)?|nov(ember)?|dec(ember)?"
            + "|monday|tuesday|wednesday|thursday|friday|saturday|sunday"
            + "|next\\s+(day|morning|night|week)|the\\s+next\\s+(day|morning|night|week)"
            + "|\\b\\d+\\s+(days|weeks|months|years)\\s+(later|ago)\\b)\\b"
    );

    // ── beat_architect ─────────────────────────────────────────────

    private ToolRun executeBeatArchitect(Map<String, Object> args) throws IOException {
        String brief = stringArg(args, "scene_brief", "");
        String density = stringArg(args, "density", "balanced").toLowerCase(Locale.ROOT);
        int maxClusters = intArg(args, "max_clusters", -1);
        boolean dryRun = boolArg(args, "dry_run", false);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("tool", "beat_architect");
        root.put("density", density);

        if (brief == null || brief.isBlank()) {
            root.put("error", "scene_brief is required.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }
        if (dryRun) {
            root.put("dry_run", true);
            root.put("scene_brief_excerpt", brief.length() > 200 ? brief.substring(0, 200) : brief);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        int confidence = estimateBriefConfidence(brief);
        root.put("confidence_in_comprehension", confidence);

        if (confidence < 85) {
            root.put("needs_clarification", true);
            ArrayNode questions = root.putArray("questions");
            for (String q : buildBriefQuestions(brief)) {
                questions.add(q);
            }
            root.put("instruction", "Ask these questions before generating clusters.");
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        int clusters = switch (density) {
            case "lean" -> 5;
            case "dense" -> 10;
            default -> 7;
        };
        if (maxClusters > 0) clusters = Math.min(clusters, maxClusters);
        clusters = Math.max(3, Math.min(12, clusters));

        root.put("cluster_count", clusters);
        root.put("needs_clarification", false);

        ArrayNode templates = root.putArray("cluster_templates");
        for (int i = 0; i < clusters; i++) {
            ObjectNode c = templates.addObject();
            if (i == 0) c.put("label", "SCENE START");
            else if (i == clusters - 1) c.put("label", "SCENE ENDING");
            else c.put("label", "CONTINUATION " + i);
            c.put("sensory_anchor_hint", "Anchor this beat in a concrete perception (sound/texture/light/EM signal/etc.).");
            c.put("procedural_core_hint", "List the key actions in logical order; keep it reproducible for drafting.");
            c.put("realization_hint", "What changes in the POV's understanding/emotion by the end of this beat?");
            c.put("variable_points_hint", "What can flex without breaking the story intent?");
            c.put("fixed_points_hint", "What must remain constant (theme/decision/reveal)?");
            c.put("canonical_verification_hint", "Flag any lore/biology/tech details not explicitly grounded in canon.");
        }
        root.put("scene_brief", brief.length() > 1200 ? brief.substring(0, 1200) : brief);
        if (brief.length() > 1200) {
            root.put("scene_brief_truncated", true);
            root.put("scene_brief_total_chars", brief.length());
        }
        root.put("note", "This tool generates a beat cluster scaffold. The model should fill each cluster with concrete, canon-aware content, and ask questions first if needs_clarification=true.");
        return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private int estimateBriefConfidence(String brief) {
        if (brief == null || brief.isBlank()) return 0;
        String lower = brief.toLowerCase(Locale.ROOT);
        int score = 50;
        if (lower.contains("pov")) score += 10;
        if (lower.contains("setting") || lower.contains("location") || lower.contains("where")) score += 10;
        if (lower.contains("wants") || lower.contains("goal") || lower.contains("must")) score += 10;
        if (lower.contains("conflict") || lower.contains("but") || lower.contains("however") || lower.contains("threat")) score += 10;
        if (lower.contains("ends") || lower.contains("ending") || lower.contains("reveal") || lower.contains("decision")) score += 10;
        int len = brief.trim().length();
        if (len >= 300) score += 10;
        if (len >= 800) score += 5;
        return Math.max(0, Math.min(100, score));
    }

    private List<String> buildBriefQuestions(String brief) {
        List<String> qs = new ArrayList<>();
        String lower = brief != null ? brief.toLowerCase(Locale.ROOT) : "";
        if (!lower.contains("pov")) qs.add("Who is the POV character for this scene?");
        if (!(lower.contains("setting") || lower.contains("location") || lower.contains("where"))) qs.add("Where does the scene take place (specific location/environment)?");
        if (!(lower.contains("goal") || lower.contains("wants") || lower.contains("must"))) qs.add("What is the POV character trying to achieve in this scene?");
        if (!(lower.contains("conflict") || lower.contains("threat") || lower.contains("but") || lower.contains("however"))) qs.add("What is the core obstacle/conflict that pushes back?");
        if (!(lower.contains("ending") || lower.contains("ends") || lower.contains("decision") || lower.contains("reveal"))) qs.add("What needs to change by the end (decision, reveal, or emotional turn)?");
        qs.add("Desired beat density: lean (5), balanced (7), or dense (10)?");
        return qs;
    }


    private OutlineScene matchSceneToBeat(String scenePath, List<OutlineScene> scenes) {
        if (scenes.isEmpty()) return null;

        // Extract slug from scene path: "Story/Scenes/SCN-early-cycle-anomalies.md" → "early-cycle-anomalies"
        String filename = Path.of(scenePath).getFileName().toString();
        String slug = filename
            .replaceFirst("^(?i)SCN-", "")
            .replaceFirst("\\.md$", "");
        String normalizedSlug = normalizeForMatch(slug);

        // Pass 1: title match (normalized slug contains title or vice versa)
        OutlineScene bestMatch = null;
        int bestScore = 0;
        for (OutlineScene scene : scenes) {
            String normalizedTitle = normalizeForMatch(scene.title);
            if (normalizedTitle.isEmpty()) continue;

            if (normalizedSlug.contains(normalizedTitle) || normalizedTitle.contains(normalizedSlug)) {
                int score = normalizedTitle.length(); // longer match = better
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = scene;
                    bestMatch.matchMethod = "title_slug";
                }
            }
        }
        if (bestMatch != null) return bestMatch;

        // Pass 2: POV name match (slug contains character name)
        for (OutlineScene scene : scenes) {
            if (scene.pov == null || scene.pov.isBlank()) continue;
            String normalizedPov = normalizeForMatch(scene.pov);
            if (normalizedSlug.contains(normalizedPov)) {
                // Could match multiple — prefer first one (scene order)
                scene.matchMethod = "pov_name";
                return scene;
            }
        }

        // Pass 3: scene number match (slug contains a number matching scene number)
        // Prefer an explicit "scene-<N>" marker (avoid matching unrelated numeric segments like "00" / "01").
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)scene[-_ ]*(\\d{1,3})").matcher(slug);
        if (m.find()) {
            try {
                int num = Integer.parseInt(m.group(1));
                for (OutlineScene scene : scenes) {
                    if (scene.number == num) {
                        scene.matchMethod = "scene_number";
                        return scene;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        // Fallback: scan all numeric segments, but ignore leading-zero tokens like "00"/"01" unless they're the only option.
        List<String> numericParts = new ArrayList<>();
        for (String part : slug.split("[^0-9]+")) {
            if (part.isEmpty()) continue;
            numericParts.add(part);
        }
        for (String part : numericParts) {
            if (part.length() > 1 && part.startsWith("0")) {
                continue;
            }
            try {
                int num = Integer.parseInt(part);
                for (OutlineScene scene : scenes) {
                    if (scene.number == num) {
                        scene.matchMethod = "scene_number";
                        return scene;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        // Last resort: if everything was leading-zero, allow it (better than no match).
        for (String part : numericParts) {
            try {
                int num = Integer.parseInt(part);
                for (OutlineScene scene : scenes) {
                    if (scene.number == num) {
                        scene.matchMethod = "scene_number";
                        return scene;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }

    private String normalizeForMatch(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }

    private String extractPovFromScenePath(String scenePath) {
        // Try to extract a character name from the scene slug
        // e.g., "SCN-scenes-00-instability-00-scene-1-seryn.md" → might contain "seryn"
        String filename = Path.of(scenePath).getFileName().toString();
        String slug = filename
            .replaceFirst("^(?i)SCN-", "")
            .replaceFirst("\\.md$", "");
        // The last segment after the final hyphen is often a character short name
        String[] parts = slug.split("-");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            if (lastPart.length() >= 3 && lastPart.matches("[a-zA-Z]+")) {
                return lastPart;
            }
        }
        return null;
    }

    private ObjectNode findAndReadPovCanonCard(String povName, ToolRun refCollector) throws IOException {
        String normalizedPov = povName.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (normalizedPov.isEmpty()) return null;

        List<FileNode> files = collectStoryFiles();
        // Search for character card matching POV name
        for (FileNode file : files) {
            String path = file.getPath();
            if (path == null) continue;
            String lower = path.toLowerCase(Locale.ROOT);

            // Match VFS naming: Compendium/Characters/CHAR-serynthas.md
            // or physical naming: characters-pov-serynthas.md
            boolean isCharFile = lower.contains("/characters/")
                || lower.contains("char-")
                || lower.contains("characters-");
            if (!isCharFile) continue;

            String fileName = Path.of(path).getFileName().toString().toLowerCase(Locale.ROOT);
            if (fileName.contains(normalizedPov)) {
                try {
                    String content = readFile(path);
                    if (content == null || content.isBlank()) continue;

                    ObjectNode card = objectMapper.createObjectNode();
                    card.put("path", path);

                    int canonBudget = 600;
                    String excerpt = content.length() > canonBudget
                        ? content.substring(0, canonBudget) : content;
                    card.put("content", excerpt);
                    if (content.length() > canonBudget) {
                        card.put("content_truncated", true);
                        card.put("content_total_chars", content.length());
                    }

                    refCollector.fileRefs.add(buildFileRef(path, content,
                        content.length() > 200 ? content.substring(0, 200) : content));
                    return card;
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private List<String> splitSentences(String text) {
        // Split on sentence-ending punctuation followed by whitespace or end-of-string.
        // Skip common abbreviations.
        String cleaned = text.replace("\n", " ").replaceAll("\\s+", " ").trim();
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            current.append(c);

            if ((c == '.' || c == '!' || c == '?') &&
                (i + 1 >= cleaned.length() || Character.isWhitespace(cleaned.charAt(i + 1))
                    || cleaned.charAt(i + 1) == '"' || cleaned.charAt(i + 1) == '\u201D')) {

                // Check for common abbreviations before the period
                String built = current.toString().trim();
                if (c == '.' && isAbbreviation(built)) {
                    continue;
                }
                if (!built.isEmpty()) {
                    sentences.add(built);
                }
                current = new StringBuilder();
            }
        }
        // Flush remaining text as final sentence
        String remaining = current.toString().trim();
        if (!remaining.isEmpty() && remaining.length() > 2) {
            sentences.add(remaining);
        }
        return sentences;
    }

    private boolean isAbbreviation(String textEndingWithDot) {
        String lower = textEndingWithDot.toLowerCase(Locale.ROOT);
        for (String abbr : ABBREVIATIONS) {
            if (lower.endsWith(abbr)) return true;
        }
        return false;
    }

    private static final Set<String> ABBREVIATIONS = Set.of(
        "mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.",
        "st.", "ave.", "blvd.", "dept.", "inc.", "ltd.", "corp.",
        "vs.", "etc.", "e.g.", "i.e.", "approx.", "govt."
    );

    private static final Set<String> ADVERB_EXCEPTIONS = Set.of(
        "only", "early", "daily", "holy", "lonely", "ugly",
        "likely", "family", "belly", "jelly", "rally", "ally",
        "supply", "apply", "reply", "fly", "july", "italy",
        "assembly", "butterfly", "bully", "fully", "tally"
    );

    private static final Set<String> STOPWORDS = Set.of(
        "the", "and", "that", "this", "with", "from", "have", "had",
        "has", "was", "were", "been", "being", "would", "could",
        "should", "will", "shall", "into", "also", "than", "then",
        "them", "they", "their", "there", "here", "what", "when",
        "where", "which", "while", "about", "just", "like", "over",
        "such", "some", "more", "most", "other", "each", "every",
        "much", "very", "does", "done", "didn", "hadn", "hasn",
        "isn", "aren", "wasn", "weren", "couldn", "wouldn", "shouldn",
        "through", "before", "after", "under", "between", "back",
        "down", "still", "know", "said", "told"
    );

    private String writeToolReceipt(ToolCall call, ToolRun run, ToolExecutionContext context) throws IOException {
        if (projectContext == null || projectContext.audit() == null) {
            return null;
        }
        String sessionId = context != null ? context.getSessionId() : null;
        if ((sessionId == null || sessionId.isBlank()) && context != null && context.getTaskId() != null) {
            sessionId = context.getTaskId();
        }
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "adhoc-" + Instant.now().toString().replace(":", "").replace(".", "");
        }
        String receiptId = "rcpt_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ObjectNode receipt = objectMapper.createObjectNode();
        receipt.put("receipt_id", receiptId);
        if (context != null && context.getSessionId() != null) {
            receipt.put("conference_id", context.getSessionId());
        }
        if (context != null && context.getTaskId() != null) {
            receipt.put("task_id", context.getTaskId());
        }
        if (context != null && context.getTurnId() != null) {
            receipt.put("turn_id", context.getTurnId());
        }
        if (context != null && context.getAgentId() != null) {
            receipt.put("agent_id", context.getAgentId());
        }
        receipt.put("tool_id", call.getName());
        receipt.set("inputs", objectMapper.valueToTree(call.getArgs()));
        receipt.set("outputs", buildOutputPayload(run.output));
        receipt.set("file_refs", buildFileRefs(run.fileRefs));
        receipt.put("timestamp", Instant.now().toString());

        String payload = objectMapper.writeValueAsString(receipt);
        String signature = projectContext.audit().signPayload(payload);
        receipt.put("signature", signature);
        receipt.put("signature_alg", "HMAC-SHA256");

        String jsonLine = objectMapper.writeValueAsString(receipt);
        projectContext.audit().appendSessionToolReceipt(sessionId, jsonLine);
        return receiptId;
    }

    private ObjectNode buildOutputPayload(String output) throws IOException {
        ObjectNode node = objectMapper.createObjectNode();
        if (output == null) {
            node.put("text", "");
            node.put("truncated", false);
            return node;
        }
        int max = 2000;
        if (output.length() <= max) {
            node.put("text", output);
            node.put("truncated", false);
            return node;
        }
        String excerpt = output.substring(0, max);
        node.put("truncated", true);
        node.put("excerpt", excerpt);
        node.put("sha256", sha256(excerpt));
        node.put("full_size", output.length());
        return node;
    }

    private ArrayNode buildFileRefs(List<ObjectNode> refs) {
        ArrayNode arr = objectMapper.createArrayNode();
        if (refs == null) return arr;
        refs.forEach(arr::add);
        return arr;
    }

    private ObjectNode buildFileRef(String path, String content, String excerpt) throws IOException {
        ObjectNode ref = objectMapper.createObjectNode();
        ref.put("path", path);
        LineRange range = findLineRange(content, excerpt);
        if (range != null) {
            ref.put("start_line", range.start);
            ref.put("end_line", range.end);
        }
        ref.put("sha256", sha256(excerpt));
        ref.put("excerpt", excerpt.length() > 200 ? excerpt.substring(0, 200) + "..." : excerpt);
        return ref;
    }

    private LineRange findLineRange(String content, String excerpt) {
        if (content == null || excerpt == null || excerpt.isBlank()) {
            return null;
        }
        int idx = content.indexOf(excerpt);
        if (idx < 0) {
            return null;
        }
        String before = content.substring(0, idx);
        int startLine = before.split("\n", -1).length;
        int lineCount = excerpt.split("\n", -1).length;
        return new LineRange(startLine, startLine + Math.max(0, lineCount - 1));
    }

    private String sha256(String value) throws IOException {
        if (value == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("sha256 failed", e);
        }
    }

    private static final class LineRange {
        private final int start;
        private final int end;

        private LineRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class ToolRun {
        private final String output;
        private final List<ObjectNode> fileRefs = new ArrayList<>();

        private ToolRun(String output) {
            this.output = output;
        }

        private static ToolRun of(String output) {
            return new ToolRun(output);
        }
    }

    private List<FileNode> collectStoryFiles() throws IOException {
        FileNode root;
        if (projectContext != null && projectContext.preparation() != null
            && projectContext.preparation().isVirtualReady()) {
            root = projectContext.preparedWorkspace().getTree();
        } else {
            WorkspaceService workspace = projectContext != null ? projectContext.workspace() : null;
            root = workspace != null ? workspace.getTree("") : null;
        }
        if (root == null) return List.of();
        List<FileNode> nodes = new ArrayList<>();
        collectFiles(root, nodes);
        return nodes.stream()
            .filter(node -> node.getPath() != null)
            .filter(node -> {
                String lower = node.getPath().toLowerCase(Locale.ROOT);
                return lower.startsWith("story/")
                    || lower.startsWith("compendium/")
                    || lower.contains("scn-outline");
            })
            .collect(Collectors.toList());
    }

    private void collectFiles(FileNode node, List<FileNode> out) {
        if (node == null) return;
        if ("file".equalsIgnoreCase(node.getType())) {
            out.add(node);
            return;
        }
        if (node.getChildren() != null) {
            for (FileNode child : node.getChildren()) {
                collectFiles(child, out);
            }
        }
    }

    private ObjectNode withMeta(Object value, double confidence, String basis) {
        ObjectNode node = objectMapper.createObjectNode();
        if (value == null) {
            node.putNull("value");
        } else if (value instanceof Number) {
            node.put("value", ((Number) value).longValue());
        } else {
            node.put("value", value.toString());
        }
        node.put("confidence", confidence);
        node.put("match_basis", basis);
        return node;
    }

    private ArrayNode withKeywords(String path) {
        ArrayNode arr = objectMapper.createArrayNode();
        if (path == null) return arr;
        String base = Path.of(path).getFileName().toString();
        String[] parts = base.split("[^a-zA-Z0-9]+");
        for (String part : parts) {
            if (part.isBlank()) continue;
            ObjectNode obj = arr.addObject();
            obj.put("value", part.toLowerCase(Locale.ROOT));
            obj.put("confidence", 0.4);
            obj.put("match_basis", "FILENAME_MATCH");
        }
        return arr;
    }

    private String guessType(String path) {
        if (path == null) return "unknown";
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("scn-outline")) return "outline";
        if (lower.contains("compendium") || lower.contains("canon")) return "canon";
        if (lower.contains("scene") || lower.contains("scn-scenes")) return "scene";
        return "file";
    }

    private String extractPov(String path) {
        if (path == null) return null;
        String name = Path.of(path).getFileName().toString().toLowerCase(Locale.ROOT);
        String[] parts = name.split("-");
        if (parts.length == 0) return null;
        return parts[parts.length - 1].replace(".md", "");
    }

    private Long extractSceneNumber(String path) {
        if (path == null) return null;
        String name = Path.of(path).getFileName().toString().toLowerCase(Locale.ROOT);
        for (String part : name.split("-")) {
            try {
                return Long.parseLong(part);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private long fileSize(String relativePath) {
        try {
            if (projectContext != null && projectContext.preparation() != null
                && projectContext.preparation().isVirtualReady()) {
                return readFile(relativePath).length();
            }
            WorkspaceService workspace = projectContext != null ? projectContext.workspace() : null;
            if (workspace == null) return 0;
            Path abs = workspace.resolvePath(relativePath);
            return Files.exists(abs) ? Files.size(abs) : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String fileModified(String relativePath) {
        try {
            if (projectContext != null && projectContext.preparation() != null
                && projectContext.preparation().isVirtualReady()) {
                return "virtual";
            }
            WorkspaceService workspace = projectContext != null ? projectContext.workspace() : null;
            if (workspace == null) return "unknown";
            Path abs = workspace.resolvePath(relativePath);
            if (!Files.exists(abs)) return "unknown";
            return Files.getLastModifiedTime(abs).toString();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private String readFile(String relativePath) throws IOException {
        if (projectContext != null && projectContext.preparation() != null
            && projectContext.preparation().isVirtualReady()) {
            PreparedWorkspaceService prepared = projectContext.preparedWorkspace();
            return prepared.readFile(relativePath);
        }
        WorkspaceService workspace = projectContext != null ? projectContext.workspace() : null;
        if (workspace == null) {
            throw new IOException("Workspace unavailable.");
        }
        return workspace.readFile(relativePath);
    }

    private String extractFirstSentence(String content) {
        if (content == null || content.isBlank()) return "";
        String trimmed = content.trim();
        int idx = trimmed.indexOf('.');
        if (idx > 0) {
            return trimmed.substring(0, idx + 1).trim();
        }
        return trimmed.split("\n")[0].trim();
    }

    private int countWords(String content) {
        if (content == null || content.isBlank()) return 0;
        int count = 0;
        for (String w : content.split("\\s+")) {
            if (!w.isBlank()) count++;
        }
        return count;
    }

    private String excerptFirst(String content, int maxChars) {
        if (content == null) return "";
        if (maxChars <= 0) return "";
        String trimmed = content.trim();
        if (trimmed.length() <= maxChars) return trimmed;
        String slice = trimmed.substring(0, maxChars);
        // Prefer cutting at a paragraph boundary or whitespace so we don't end mid-word.
        int nl = slice.lastIndexOf("\n\n");
        if (nl >= 200) return slice.substring(0, nl).trim();
        int sp = Math.max(slice.lastIndexOf(' '), slice.lastIndexOf('\n'));
        if (sp >= 200) return slice.substring(0, sp).trim();
        return slice;
    }

    private String excerptLast(String content, int maxChars) {
        if (content == null) return "";
        if (maxChars <= 0) return "";
        String trimmed = content.trim();
        if (trimmed.length() <= maxChars) return trimmed;
        int start = Math.max(0, trimmed.length() - maxChars);
        String slice = trimmed.substring(start);
        // Prefer starting at a boundary so we don't begin mid-word.
        int boundary = -1;
        int nl = slice.indexOf("\n\n");
        if (nl >= 0 && nl <= 200) boundary = nl + 2;
        if (boundary < 0) {
            for (int i = 0; i < Math.min(200, slice.length()); i++) {
                char c = slice.charAt(i);
                if (c == ' ' || c == '\n' || c == '\t') {
                    boundary = i + 1;
                }
            }
        }
        if (boundary > 0 && boundary < slice.length()) {
            return slice.substring(boundary).trim();
        }
        return slice.trim();
    }

    private String trimQuote(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.length() > 120) {
            return trimmed.substring(0, 117) + "...";
        }
        return trimmed;
    }

    private String routeRole(String request) {
        String lower = request == null ? "" : request.toLowerCase(Locale.ROOT);
        if (lower.contains("outline") || lower.contains("structure") || lower.contains("stakes")) {
            return "planner";
        }
        if (lower.contains("write") || lower.contains("draft") || lower.contains("scene")) {
            return "writer";
        }
        if (lower.contains("edit") || lower.contains("clarity") || lower.contains("line edit")) {
            return "editor";
        }
        if (lower.contains("critique") || lower.contains("reader") || lower.contains("impact")) {
            return "critic";
        }
        if (lower.contains("canon") || lower.contains("continuity") || lower.contains("timeline")) {
            return "continuity";
        }
        return "chief";
    }

    private String roleEvidenceType(String role) {
        if (role == null) return "QUOTE";
        switch (role) {
            case "planner":
                return "LINE_REF";
            case "continuity":
                return "QUOTE";
            case "writer":
            case "editor":
            case "critic":
                return "QUOTE";
            default:
                return "LINE_REF";
        }
    }

    private String stringArg(Map<String, Object> args, String key, String fallback) {
        if (args == null || key == null) return fallback;
        Object value = args.get(key);
        if (value == null) return fallback;
        return String.valueOf(value);
    }

    private boolean boolArg(Map<String, Object> args, String key, boolean fallback) {
        if (args == null || key == null) return fallback;
        Object value = args.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intArg(Map<String, Object> args, String key, int fallback) {
        if (args == null || key == null) return fallback;
        Object value = args.get(key);
        if (value == null) return fallback;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<String> listArg(Map<String, Object> args, String key) {
        if (args == null || key == null) return List.of();
        Object value = args.get(key);
        if (value == null) return List.of();
        if (value instanceof List) {
            List<?> raw = (List<?>) value;
            return raw.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toList());
        }
        String raw = String.valueOf(value);
        if (raw.startsWith("[") && raw.endsWith("]")) {
            String inner = raw.substring(1, raw.length() - 1).trim();
            if (inner.isEmpty()) return List.of();
            String[] parts = inner.split(",");
            List<String> items = new ArrayList<>();
            for (String part : parts) {
                String item = part.trim();
                if (!item.isBlank()) items.add(item.replace("\"", "").replace("'", ""));
            }
            return items;
        }
        return List.of(raw);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
