package com.miniide.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public final class ReceiptValidator {
    private ReceiptValidator() {
    }

    public static PromptValidationResult validate(JsonNode node) {
        List<String> errors = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            errors.add("receipt:missing");
            return PromptValidationResult.of(errors);
        }

        requireText(node, "receipt_id", errors);
        requireText(node, "packet_id", errors);
        requireText(node, "issue_id", errors);
        requireObject(node, "actor", errors);
        requireText(node, "started_at", errors);
        requireText(node, "finished_at", errors);
        requireArray(node, "inputs_used", errors);
        requireArray(node, "outputs_produced", errors);
        requireText(node, "reasoning_summary", errors);
        requireArray(node, "decisions", errors);
        requireArray(node, "checks_performed", errors);
        requireArray(node, "assumptions", errors);
        requireArray(node, "risks", errors);
        requireObject(node, "next_recommended_action", errors);
        requireObject(node, "stop_hook", errors);
        requireArray(node, "citations", errors);
        requireText(node, "report_excerpt", errors);

        JsonNode actor = node.path("actor");
        if (!actor.isMissingNode() && actor.isObject()) {
            requireText(actor, "agent_id", errors, "actor.agent_id");
            requireText(actor, "provider", errors, "actor.provider");
            requireText(actor, "model", errors, "actor.model");
            requireObject(actor, "decoding", errors, "actor.decoding");
            JsonNode decoding = actor.path("decoding");
            if (!decoding.isMissingNode() && decoding.isObject()) {
                if (!decoding.has("requested")) {
                    errors.add("actor.decoding.requested:missing");
                }
                if (!decoding.has("effective")) {
                    errors.add("actor.decoding.effective:missing");
                }
                if (!decoding.has("source")) {
                    errors.add("actor.decoding.source:missing");
                } else if (!decoding.path("source").isTextual()) {
                    errors.add("actor.decoding.source:invalid");
                }
            }
        }

        JsonNode nextAction = node.path("next_recommended_action");
        if (!nextAction.isMissingNode() && nextAction.isObject()) {
            requireText(nextAction, "intent", errors, "next_recommended_action.intent");
            requireText(nextAction, "reason", errors, "next_recommended_action.reason");
        }

        JsonNode stopHook = node.path("stop_hook");
        if (!stopHook.isMissingNode() && stopHook.isObject()) {
            if (!stopHook.has("triggered")) {
                errors.add("stop_hook.triggered:missing");
            }
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
        requireObject(node, field, errors, field);
    }

    private static void requireObject(JsonNode node, String field, List<String> errors, String label) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || !value.isObject()) {
            errors.add(label + ":missing");
        }
    }

    private static void requireArray(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || !value.isArray()) {
            errors.add(field + ":missing");
        }
    }
}
