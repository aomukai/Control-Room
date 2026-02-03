package com.miniide.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ToolSchema {
    private final String toolId;
    private final Map<String, ToolArgSpec> args = new HashMap<>();

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

    public String getToolId() {
        return toolId;
    }

    public Set<String> getArgNames() {
        return args.keySet();
    }

    public Map<String, ToolArgSpec> getArgSpecs() {
        return java.util.Collections.unmodifiableMap(args);
    }

    public String validate(JsonNode argsNode) {
        if (argsNode == null || !argsNode.isObject()) {
            return "args-not-object";
        }
        for (Map.Entry<String, ToolArgSpec> entry : args.entrySet()) {
            ToolArgSpec spec = entry.getValue();
            JsonNode child = argsNode.get(spec.getName());
            String error = spec.validate(child);
            if (error != null) {
                return error;
            }
        }
        java.util.Iterator<String> fields = argsNode.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!args.containsKey(field)) {
                return "unknown-arg:" + field;
            }
        }
        return null;
    }
}
