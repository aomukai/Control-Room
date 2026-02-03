package com.miniide.tools;

import java.util.HashMap;
import java.util.Map;

public class ToolSchemaRegistry {
    private final Map<String, ToolSchema> schemas = new HashMap<>();

    public ToolSchemaRegistry register(ToolSchema schema) {
        if (schema != null && schema.getToolId() != null) {
            schemas.put(schema.getToolId(), schema);
        }
        return this;
    }

    public boolean hasTool(String toolId) {
        return toolId != null && schemas.containsKey(toolId);
    }

    public ToolSchema getSchema(String toolId) {
        return toolId != null ? schemas.get(toolId) : null;
    }

    public java.util.Set<String> getToolIds() {
        return java.util.Collections.unmodifiableSet(schemas.keySet());
    }
}
