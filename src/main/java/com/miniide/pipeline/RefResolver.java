package com.miniide.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves $ref objects in recipe args against the task metadata and cache.
 *
 * Syntax:
 *   { "$ref": "task.description" }          -> task metadata
 *   { "$ref": "discovery.matches[0].path" } -> cache slot, array index, field
 *
 * Rules:
 *   - Dot traversal: a.b.c navigates nested objects.
 *   - Array indexing: [N] accesses element at literal integer index.
 *   - Root namespaces: "task.*" = task metadata, anything else = cache slot.
 *   - Null at any step throws RefResolutionException.
 *   - No JSONPath. No filters. No wildcards. No expressions.
 */
public class RefResolver {

    private static final Pattern INDEX_PATTERN = Pattern.compile("^(.+?)\\[(\\d+)]$");

    private final ObjectMapper objectMapper;

    public RefResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Resolve all $ref objects in a recipe step's args.
     * Literal values pass through unchanged. { "$ref": "..." } objects are resolved.
     *
     * @param args        the args object from the recipe step
     * @param taskNode    the "task" metadata (description, initial_args, etc.)
     * @param cacheNode   the current cache (slot names -> slot data)
     * @return resolved args as a Map suitable for ToolCall
     */
    public Map<String, Object> resolveArgs(JsonNode args, JsonNode taskNode, JsonNode cacheNode) {
        if (args == null || args.isNull()) {
            return Map.of();
        }
        JsonNode resolved = resolveNode(args, taskNode, cacheNode, "args");
        return objectMapper.convertValue(resolved, Map.class);
    }

    private JsonNode resolveNode(JsonNode node, JsonNode taskNode, JsonNode cacheNode, String context) {
        if (node == null || node.isNull()) {
            return node;
        }
        // If this is an object with a "$ref" field, resolve the reference
        if (node.isObject() && node.has("$ref")) {
            String refPath = node.get("$ref").asText();
            return resolveRef(refPath, taskNode, cacheNode);
        }
        // If this is a plain object, recurse into its fields
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                result.set(entry.getKey(), resolveNode(entry.getValue(), taskNode, cacheNode, context + "." + entry.getKey()));
            }
            return result;
        }
        // Arrays and scalars pass through
        return node;
    }

    /**
     * Resolve a single $ref path string.
     *
     * @param refPath   e.g. "task.description" or "discovery.matches[0].path"
     * @param taskNode  the task metadata
     * @param cacheNode the cache slots
     * @return the resolved JsonNode
     * @throws RefResolutionException if any segment resolves to null
     */
    public JsonNode resolveRef(String refPath, JsonNode taskNode, JsonNode cacheNode) {
        if (refPath == null || refPath.isBlank()) {
            throw new RefResolutionException(refPath, "(root)", "empty ref path");
        }

        String[] segments = refPath.split("\\.");
        String root = segments[0];

        // Determine the root node
        JsonNode current;
        if ("task".equals(root)) {
            current = taskNode;
            if (current == null || current.isNull()) {
                throw new RefResolutionException(refPath, "task", "task metadata is null");
            }
        } else {
            // It's a cache slot name — check if there's an index on the root
            Matcher m = INDEX_PATTERN.matcher(root);
            if (m.matches()) {
                String slotName = m.group(1);
                int index = Integer.parseInt(m.group(2));
                current = cacheNode != null ? cacheNode.path(slotName) : null;
                if (current == null || current.isMissingNode() || current.isNull()) {
                    throw new RefResolutionException(refPath, slotName, "cache slot not found");
                }
                // For pointer slots, drill into the data inside
                if (current.has("data")) {
                    current = current.get("data");
                }
                if (!current.isArray() || index >= current.size()) {
                    throw new RefResolutionException(refPath, root, "array index out of bounds: " + index);
                }
                current = current.get(index);
            } else {
                current = cacheNode != null ? cacheNode.path(root) : null;
                if (current == null || current.isMissingNode() || current.isNull()) {
                    throw new RefResolutionException(refPath, root, "cache slot not found");
                }
                // For pointer/artifact slots, drill into data if present
                if (current.has("data")) {
                    current = current.get("data");
                }
            }
        }

        // Navigate remaining segments (starting from index 1 for task, 1 for cache)
        int start = "task".equals(root) ? 1 : 1;
        for (int i = start; i < segments.length; i++) {
            String segment = segments[i];
            Matcher m = INDEX_PATTERN.matcher(segment);
            if (m.matches()) {
                String fieldName = m.group(1);
                int index = Integer.parseInt(m.group(2));
                current = current.path(fieldName);
                if (current.isMissingNode() || current.isNull()) {
                    throw new RefResolutionException(refPath, fieldName, "field not found");
                }
                if (!current.isArray() || index >= current.size()) {
                    throw new RefResolutionException(refPath, segment, "array index out of bounds: " + index);
                }
                current = current.get(index);
            } else {
                current = current.path(segment);
                if (current.isMissingNode() || current.isNull()) {
                    throw new RefResolutionException(refPath, segment, "field not found");
                }
            }
        }

        return current;
    }

    public static class RefResolutionException extends RuntimeException {
        private final String refPath;
        private final String failedSegment;

        public RefResolutionException(String refPath, String failedSegment, String detail) {
            super("$ref resolution failed: '" + refPath + "' at segment '" + failedSegment + "': " + detail);
            this.refPath = refPath;
            this.failedSegment = failedSegment;
        }

        public String getRefPath() {
            return refPath;
        }

        public String getFailedSegment() {
            return failedSegment;
        }
    }
}
