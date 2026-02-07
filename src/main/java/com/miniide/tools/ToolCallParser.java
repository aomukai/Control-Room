package com.miniide.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public class ToolCallParser {
    public static final String ERR_INVALID_FORMAT = "tool_call_invalid_format";
    public static final String ERR_MULTIPLE = "tool_call_multiple";
    public static final String ERR_UNKNOWN_TOOL = "tool_call_unknown_tool";
    public static final String ERR_INVALID_ARGS = "tool_call_invalid_args";
    public static final String ERR_NONCE_INVALID = "tool_call_nonce_invalid";

    private final ObjectMapper objectMapper;
    private final ToolSchemaRegistry schemaRegistry;
    private static final java.util.Map<String, String> TOOL_ALIASES = buildToolAliases();

    public ToolCallParser(ObjectMapper objectMapper, ToolSchemaRegistry schemaRegistry) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.schemaRegistry = schemaRegistry;
    }

    public ToolCallParseResult parseStrict(String content, String expectedNonce) {
        if (content == null) {
            return ToolCallParseResult.noCall();
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return ToolCallParseResult.noCall();
        }
        trimmed = unwrapStrictJsonCodeFence(trimmed);
        trimmed = stripInvisibleEdgeChars(trimmed);
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return ToolCallParseResult.noCall();
        }
        if (containsMultipleJsonObjects(trimmed)) {
            return ToolCallParseResult.error(ERR_MULTIPLE);
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(trimmed);
        } catch (Exception e) {
            return ToolCallParseResult.error(ERR_INVALID_FORMAT);
        }
        if (node == null || !node.isObject()) {
            return ToolCallParseResult.error(ERR_INVALID_FORMAT);
        }
        JsonNode toolNode = node.get("tool");
        JsonNode argsNode = node.get("args");
        JsonNode nonceNode = node.get("nonce");
        if (toolNode == null || !toolNode.isTextual() || argsNode == null || !argsNode.isObject()) {
            return ToolCallParseResult.error(ERR_INVALID_FORMAT);
        }
        java.util.Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!"tool".equals(field) && !"args".equals(field) && !"nonce".equals(field)) {
                return ToolCallParseResult.error(ERR_INVALID_FORMAT, "unknown-field:" + field);
            }
        }
        String toolRaw = toolNode.asText();
        String tool = toolRaw != null ? toolRaw.trim() : null;
        if (tool == null || tool.isBlank()) {
            return ToolCallParseResult.error(ERR_INVALID_FORMAT, "blank-tool");
        }
        // Be slightly forgiving: models sometimes emit tool ids with stray whitespace, case, or '-' separators.
        // We keep the registry authoritative; we only normalize if it results in a known tool id.
        String canonical = tool;
        if (schemaRegistry != null && !schemaRegistry.hasTool(canonical)) {
            String lower = canonical.toLowerCase();
            String alias = TOOL_ALIASES.get(lower);
            if (alias != null) {
                canonical = alias;
            }
            String underscored = canonical.replace('-', '_');
            String underscoredLower = underscored.toLowerCase();
            String alias2 = TOOL_ALIASES.get(underscoredLower);
            if (alias2 != null) {
                canonical = alias2;
                underscored = canonical.replace('-', '_');
                underscoredLower = underscored.toLowerCase();
            }
            if (schemaRegistry.hasTool(lower)) canonical = lower;
            else if (schemaRegistry.hasTool(underscored)) canonical = underscored;
            else if (schemaRegistry.hasTool(underscoredLower)) canonical = underscoredLower;
        }
        if (schemaRegistry == null || !schemaRegistry.hasTool(canonical)) {
            String detail = toolRaw != null ? ("unknown-tool:" + truncate(toolRaw, 60)) : "unknown-tool";
            return ToolCallParseResult.error(ERR_UNKNOWN_TOOL, detail);
        }
        tool = canonical;
        String nonce = nonceNode != null && nonceNode.isTextual() ? nonceNode.asText().trim() : null;
        if (expectedNonce != null && !expectedNonce.isBlank()) {
            if (nonce == null || !expectedNonce.equals(nonce)) {
                return ToolCallParseResult.error(ERR_NONCE_INVALID);
            }
        }
        ToolSchema schema = schemaRegistry.getSchema(tool);
        JsonNode normalizedArgsNode = schema != null ? schema.normalizeArgsNode(argsNode) : argsNode;
        String validationError = schema != null ? schema.validate(normalizedArgsNode) : null;
        if (validationError != null) {
            return ToolCallParseResult.error(ERR_INVALID_ARGS, validationError);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> args = objectMapper.convertValue(normalizedArgsNode, Map.class);
        ToolCall call = new ToolCall(tool, args, trimmed, nonce);
        return ToolCallParseResult.call(call);
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        String v = value.trim();
        if (max <= 0) return "";
        if (v.length() <= max) return v;
        return v.substring(0, max) + "...";
    }

    private String unwrapStrictJsonCodeFence(String trimmed) {
        if (trimmed == null) return "";
        String t = trimmed.trim();
        if (!t.startsWith("```")) return t;
        // Accept ONLY a single JSON object wrapped in a single code fence:
        // ```json
        // {...}
        // ```
        // No leading/trailing prose.
        String[] lines = t.split("\n", -1);
        if (lines.length < 3) return t;
        String first = lines[0].trim();
        String last = lines[lines.length - 1].trim();
        if (!first.startsWith("```") || !"```".equals(last)) return t;
        // Drop first and last line; keep the middle verbatim.
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < lines.length - 1; i++) {
            sb.append(lines[i]);
            if (i < lines.length - 2) sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String stripInvisibleEdgeChars(String value) {
        if (value == null) return null;
        String v = value;
        // Strip common invisible chars that models/providers may prepend/append.
        // This preserves strictness while avoiding false negatives.
        int start = 0;
        int end = v.length();
        while (start < end) {
            char c = v.charAt(start);
            if (c == '\uFEFF' || c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u2060') {
                start++;
                continue;
            }
            break;
        }
        while (end > start) {
            char c = v.charAt(end - 1);
            if (c == '\uFEFF' || c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u2060') {
                end--;
                continue;
            }
            break;
        }
        if (start == 0 && end == v.length()) return v;
        return v.substring(start, end).trim();
    }

    private boolean containsMultipleJsonObjects(String trimmed) {
        int depth = 0;
        boolean seenObject = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '{') {
                depth++;
                if (depth == 1 && seenObject) {
                    return true;
                }
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
                if (depth == 0) {
                    seenObject = true;
                }
            }
        }
        return false;
    }

    private static java.util.Map<String, String> buildToolAliases() {
        // Curated aliases for common model hallucinations / naming drift.
        // Keep this list small and high-confidence to avoid masking real integration bugs.
        HashMap<String, String> map = new HashMap<>();
        map.put("file_viewer", "file_locator");
        map.put("fileviewer", "file_locator");
        map.put("read_file", "file_reader");
        map.put("readfile", "file_reader");
        map.put("file_browser", "file_locator");
        map.put("filebrowser", "file_locator");
        map.put("browse_files", "file_locator");
        map.put("list_files", "file_locator");
        map.put("listfiles", "file_locator");

        map.put("issue_search", "search_issues");
        map.put("issues_search", "search_issues");
        map.put("issue_finder", "search_issues");
        map.put("issue_tracker_search", "search_issues");

        map.put("issue_summary", "issue_status_summarizer");
        map.put("issue_status_summary", "issue_status_summarizer");
        map.put("status_summarizer", "issue_status_summarizer");

        map.put("stakes_map", "stakes_mapper");
        map.put("stakes_mapping", "stakes_mapper");

        map.put("line_edit", "line_editor");
        map.put("line_editor_tool", "line_editor");

        map.put("impact_analyzer", "scene_impact_analyzer");
        map.put("scene_analyzer", "scene_impact_analyzer");

        map.put("reader_simulator", "reader_experience_simulator");
        map.put("reader_sim", "reader_experience_simulator");

        map.put("timeline_check", "timeline_validator");
        map.put("timeline_checker", "timeline_validator");

        map.put("beat_builder", "beat_architect");
        map.put("beat_generator", "beat_architect");
        return java.util.Collections.unmodifiableMap(map);
    }
}
