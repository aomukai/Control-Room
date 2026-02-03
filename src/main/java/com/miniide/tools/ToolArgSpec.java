package com.miniide.tools;

import com.fasterxml.jackson.databind.JsonNode;

public class ToolArgSpec {
    public enum Type {
        STRING,
        INT,
        BOOLEAN,
        STRING_ARRAY
    }

    private final String name;
    private final Type type;
    private final boolean required;
    private final java.util.Set<String> allowedValues;

    public ToolArgSpec(String name, Type type, boolean required) {
        this(name, type, required, null);
    }

    public ToolArgSpec(String name, Type type, boolean required, java.util.Set<String> allowedValues) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.allowedValues = allowedValues;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public java.util.Set<String> getAllowedValues() {
        return allowedValues == null ? java.util.Set.of() : java.util.Collections.unmodifiableSet(allowedValues);
    }

    public String validate(JsonNode node) {
        if (node == null || node.isNull()) {
            return required ? "missing-required:" + name : null;
        }
        switch (type) {
            case STRING:
                if (!node.isTextual()) {
                    return "invalid-type:" + name;
                }
                if (allowedValues != null && !allowedValues.isEmpty()) {
                    String value = node.asText();
                    if (!allowedValues.contains(value)) {
                        return "invalid-enum:" + name;
                    }
                }
                return null;
            case INT:
                return node.isInt() ? null : "invalid-type:" + name;
            case BOOLEAN:
                return node.isBoolean() ? null : "invalid-type:" + name;
            case STRING_ARRAY:
                if (!node.isArray()) {
                    return "invalid-type:" + name;
                }
                for (JsonNode child : node) {
                    if (!child.isTextual()) {
                        return "invalid-type:" + name;
                    }
                }
                return null;
            default:
                return "invalid-type:" + name;
        }
    }
}
