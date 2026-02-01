package com.miniide.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public final class PromptJsonValidator {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PromptJsonValidator() {
    }

    public static PromptValidationResult validateTaskPacket(String jsonText) {
        JsonNode node = parseJson(jsonText);
        if (node == null) {
            return PromptValidationResult.of(List.of("packet:invalid_json"));
        }
        return TaskPacketValidator.validate(node);
    }

    public static PromptValidationResult validateReceipt(String jsonText) {
        JsonNode node = parseJson(jsonText);
        if (node == null) {
            return PromptValidationResult.of(List.of("receipt:invalid_json"));
        }
        return ReceiptValidator.validate(node);
    }

    private static JsonNode parseJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(jsonText);
        } catch (Exception e) {
            return null;
        }
    }
}
