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
        "outline_analyzer",
        "canon_checker",
        "task_router",
        "search_issues"
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
                case "outline_analyzer":
                    run = executeOutlineAnalyzer(call.getArgs());
                    break;
                case "canon_checker":
                    run = executeCanonChecker(call.getArgs());
                    break;
                case "task_router":
                    run = executeTaskRouter(call.getArgs());
                    break;
                case "search_issues":
                    run = executeSearchIssues(call.getArgs());
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

    private ToolRun executeOutlineAnalyzer(Map<String, Object> args) throws IOException {
        String outlinePath = stringArg(args, "outline_path", "");
        String mode = stringArg(args, "mode", "");
        boolean dryRun = boolArg(args, "dry_run", false);
        if (outlinePath == null || outlinePath.isBlank()) {
            outlinePath = "Story/SCN-outline.md";
        }
        if (dryRun) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "outline_analyzer");
            root.put("outline_path", outlinePath);
            if (!mode.isBlank()) {
                root.put("mode", mode);
            }
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }

        String content = readFile(outlinePath);
        List<String> lines = content != null ? List.of(content.split("\n")) : List.of();
        long headings = lines.stream().filter(line -> line.trim().startsWith("##")).count();
        long nonEmpty = lines.stream().filter(line -> !line.trim().isEmpty()).count();
        String firstQuote = lines.stream().filter(line -> !line.trim().isEmpty()).findFirst().orElse("");
        int firstLine = firstQuote.isEmpty() ? 0 : lines.indexOf(firstQuote) + 1;
        String problem = headings == 0
            ? "Problem Identified: \"No scene headings detected.\" (line " + Math.max(1, firstLine) + ")"
            : "Problem Identified: \"" + trimQuote(firstQuote) + "\" (line " + firstLine + ")";
        String fix = headings == 0
            ? "Suggested Fix: Add scene headings (## Scene N) and short summaries to clarify structure."
            : "Suggested Fix: Add explicit act breaks and stakes escalation notes between scenes.";

        StringBuilder report = new StringBuilder();
        report.append("Structure Overview: ")
            .append(headings).append(" scene headings, ")
            .append(nonEmpty).append(" non-empty lines.\n");
        report.append(problem).append("\n");
        report.append(fix);
        ToolRun run = ToolRun.of(report.toString());
        if (!firstQuote.isBlank()) {
            run.fileRefs.add(buildFileRef(outlinePath, content, firstQuote));
        }
        return run;
    }

    private ToolRun executeCanonChecker(Map<String, Object> args) throws IOException {
        String sceneFile = stringArg(args, "scene_path", "");
        List<String> canonFiles = listArg(args, "canon_paths");
        String mode = stringArg(args, "mode", "");
        boolean dryRun = boolArg(args, "dry_run", false);
        if (canonFiles == null) canonFiles = List.of();
        if (dryRun) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tool", "canon_checker");
            root.put("scene_path", sceneFile);
            ArrayNode files = root.putArray("canon_paths");
            canonFiles.forEach(files::add);
            if (!mode.isBlank()) {
                root.put("mode", mode);
            }
            root.put("dry_run", true);
            return ToolRun.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        }
        if (canonFiles.isEmpty()) {
            return ToolRun.of("CANON_NEEDED: [canon files]\nSTATUS: Cannot validate without canon");
        }
        String scene = readFile(sceneFile);
        String canon = readFile(canonFiles.get(0));
        String sceneQuote = extractFirstSentence(scene);
        String canonQuote = extractFirstSentence(canon);
        StringBuilder report = new StringBuilder();
        report.append("Scene: ").append(sceneFile).append("\n");
        report.append("Canon: ").append(canonFiles.get(0)).append("\n");
        report.append("Scene quote: \"").append(sceneQuote).append("\"\n");
        report.append("Canon quote: \"").append(canonQuote).append("\"\n");
        report.append("Status: Canon gap\n");
        report.append("Recommendation: Flag for review");
        ToolRun run = ToolRun.of(report.toString());
        if (!sceneQuote.isBlank()) {
            run.fileRefs.add(buildFileRef(sceneFile, scene, sceneQuote));
        }
        if (!canonQuote.isBlank()) {
            run.fileRefs.add(buildFileRef(canonFiles.get(0), canon, canonQuote));
        }
        return run;
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
