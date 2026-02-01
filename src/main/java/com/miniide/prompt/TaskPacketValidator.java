package com.miniide.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TaskPacketValidator {
    private static final Set<String> INTENTS = Set.of(
        "clarify",
        "plan_scene",
        "beat_architect",
        "continuity_check",
        "write_beat",
        "critique_scene",
        "edit_scene",
        "summarize_context",
        "finalize",
        "other"
    );

    private static final Set<String> OUTPUT_MODES = Set.of("patch", "artifact", "json_only");

    private TaskPacketValidator() {
    }

    public static PromptValidationResult validate(JsonNode node) {
        List<String> errors = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            errors.add("packet:missing");
            return PromptValidationResult.of(errors);
        }

        requireText(node, "packet_id", errors);
        requireText(node, "parent_issue_id", errors);
        requireText(node, "intent", errors);
        requireObject(node, "target", errors);
        requireObject(node, "scope", errors);
        requireObject(node, "inputs", errors);
        requireObject(node, "constraints", errors);
        requireObject(node, "output_contract", errors);
        requireObject(node, "handoff", errors);
        requireText(node, "timestamp", errors);
        requireObject(node, "requested_by", errors);

        String intent = getText(node, "intent");
        if (intent != null && !INTENTS.contains(intent)) {
            errors.add("intent:invalid");
        }

        JsonNode outputContract = node.path("output_contract");
        if (!outputContract.isMissingNode() && outputContract.isObject()) {
            String outputMode = getText(outputContract, "output_mode");
            if (outputMode == null || outputMode.isBlank()) {
                errors.add("output_contract.output_mode:missing");
            } else if (!OUTPUT_MODES.contains(outputMode)) {
                errors.add("output_contract.output_mode:invalid");
            }
            JsonNode expected = outputContract.path("expected_artifacts");
            if (expected.isMissingNode() || !expected.isArray()) {
                errors.add("output_contract.expected_artifacts:missing");
            }
        }

        JsonNode target = node.path("target");
        if (!target.isMissingNode() && target.isObject()) {
            requireText(target, "scene_ref", errors, "target.scene_ref");
            requireText(target, "resolution_method", errors, "target.resolution_method");
        }

        JsonNode requestedBy = node.path("requested_by");
        if (!requestedBy.isMissingNode() && requestedBy.isObject()) {
            requireText(requestedBy, "agent_id", errors, "requested_by.agent_id");
        }

        return errors.isEmpty() ? PromptValidationResult.ok() : PromptValidationResult.of(errors);
    }

    private static void requireText(JsonNode node, String field, List<String> errors) {
        requireText(node, field, errors, field);
    }

    private static void requireText(JsonNode node, String field, List<String> errors, String label) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            errors.add(label + ":missing");
        }
    }

    private static void requireObject(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || !value.isObject()) {
            errors.add(field + ":missing");
        }
    }

    private static String getText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isTextual()) {
            return null;
        }
        return value.asText();
    }
}
