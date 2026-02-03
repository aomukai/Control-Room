package com.miniide.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class ToolCallParser {
    public static final String ERR_INVALID_FORMAT = "tool_call_invalid_format";
    public static final String ERR_MULTIPLE = "tool_call_multiple";
    public static final String ERR_UNKNOWN_TOOL = "tool_call_unknown_tool";
    public static final String ERR_INVALID_ARGS = "tool_call_invalid_args";
    public static final String ERR_NONCE_INVALID = "tool_call_nonce_invalid";

    private final ObjectMapper objectMapper;
    private final ToolSchemaRegistry schemaRegistry;

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
        String tool = toolNode.asText();
        if (schemaRegistry == null || !schemaRegistry.hasTool(tool)) {
            return ToolCallParseResult.error(ERR_UNKNOWN_TOOL);
        }
        String nonce = nonceNode != null && nonceNode.isTextual() ? nonceNode.asText() : null;
        if (expectedNonce != null && !expectedNonce.isBlank()) {
            if (nonce == null || !expectedNonce.equals(nonce)) {
                return ToolCallParseResult.error(ERR_NONCE_INVALID);
            }
        }
        ToolSchema schema = schemaRegistry.getSchema(tool);
        String validationError = schema != null ? schema.validate(argsNode) : null;
        if (validationError != null) {
            return ToolCallParseResult.error(ERR_INVALID_ARGS, validationError);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> args = objectMapper.convertValue(argsNode, Map.class);
        ToolCall call = new ToolCall(tool, args, trimmed, nonce);
        return ToolCallParseResult.call(call);
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
}
