package com.miniide.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ToolSchema {
    private final String toolId;
    private final Map<String, ToolArgSpec> args = new HashMap<>();
    // Alias -> canonical arg name
    private final Map<String, String> argAliases = new HashMap<>();

    public ToolSchema(String toolId) {
        this.toolId = toolId;
    }

    public ToolSchema arg(String name, ToolArgSpec.Type type, boolean required) {
        args.put(name, new ToolArgSpec(name, type, required));
        return this;
    }

    public ToolSchema arg(String name, ToolArgSpec.Type type, boolean required, java.util.Set<String> allowedValues) {
        args.put(name, new ToolArgSpec(name, type, required, allowedValues));
        return this;
    }

    public ToolSchema alias(String alias, String canonical) {
        if (alias == null || alias.isBlank() || canonical == null || canonical.isBlank()) {
            return this;
        }
        argAliases.put(normalizeArgKey(alias), canonical);
        return this;
    }

    public String getToolId() {
        return toolId;
    }

    public Set<String> getArgNames() {
        return args.keySet();
    }

    public Map<String, ToolArgSpec> getArgSpecs() {
        return java.util.Collections.unmodifiableMap(args);
    }

    public JsonNode normalizeArgsNode(JsonNode argsNode) {
        if (argsNode == null || !argsNode.isObject()) {
            return argsNode;
        }
        if (argAliases.isEmpty()) {
            return argsNode;
        }
        // Copy to avoid mutating the parsed JSON tree that might be reused.
        ObjectNode obj;
        try {
            obj = ((ObjectNode) argsNode).deepCopy();
        } catch (ClassCastException e) {
            return argsNode;
        }

        // First: trim whitespace around keys (models sometimes emit "search_criteria ").
        java.util.List<String> keys = new java.util.ArrayList<>();
        java.util.Iterator<String> it = obj.fieldNames();
        while (it.hasNext()) {
            keys.add(it.next());
        }
        for (String key : keys) {
            if (key == null) continue;
            String trimmed = key.trim();
            if (trimmed.isEmpty() || trimmed.equals(key)) continue;
            if (!obj.has(trimmed)) {
                obj.set(trimmed, obj.get(key));
            }
            obj.remove(key);
        }

        // Second: apply canonicalization + curated aliases.
        keys.clear();
        it = obj.fieldNames();
        while (it.hasNext()) {
            keys.add(it.next());
        }
        for (String key : keys) {
            if (key == null) continue;
            if (args.containsKey(key)) continue;

            String norm = normalizeArgKey(key);
            String canonical = null;
            if (args.containsKey(norm)) {
                canonical = norm;
            } else {
                canonical = argAliases.get(norm);
            }
            if (canonical == null || canonical.isBlank()) {
                continue;
            }
            if (!obj.has(canonical)) {
                obj.set(canonical, obj.get(key));
            }
            obj.remove(key);
        }
        return obj;
    }

    private String normalizeArgKey(String key) {
        if (key == null) return "";
        String k = key.trim().toLowerCase();
        if (k.isEmpty()) return "";
        // Unify common separators.
        k = k.replace('-', '_').replace(' ', '_');
        return k;
    }

    public String validate(JsonNode argsNode) {
        JsonNode normalized = normalizeArgsNode(argsNode);
        if (normalized == null || !normalized.isObject()) {
            return "args-not-object";
        }
        for (Map.Entry<String, ToolArgSpec> entry : args.entrySet()) {
            ToolArgSpec spec = entry.getValue();
            JsonNode child = normalized.get(spec.getName());
            String error = spec.validate(child);
            if (error != null) {
                return error;
            }
        }
        java.util.Iterator<String> fields = normalized.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!args.containsKey(field)) {
                return "unknown-arg:" + field;
            }
        }
        return null;
    }
}
