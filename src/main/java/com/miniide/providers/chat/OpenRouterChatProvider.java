package com.miniide.providers.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.models.AgentEndpointConfig;
import java.io.IOException;
import java.net.http.HttpClient;

public class OpenRouterChatProvider extends AbstractChatProvider {

    public OpenRouterChatProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "openrouter";
    }

    @Override
    public String chat(String apiKey, AgentEndpointConfig endpoint, String message)
        throws IOException, InterruptedException {
        String url = normalizeOpenRouterBaseUrl(endpoint.getBaseUrl(), "https://openrouter.ai") + "/api/v1/chat/completions";

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", endpoint.getModel());
        // Reduce response size and provider-side work; OpenRouter may include reasoning fields by default.
        // This materially helps on slow/flaky connections.
        payload.put("include_reasoning", false);

        ArrayNode messages = payload.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", message);

        if (endpoint.getUseProviderDefaults() == null || !endpoint.getUseProviderDefaults()) {
            if (endpoint.getTemperature() != null) {
                payload.put("temperature", endpoint.getTemperature());
            }
            if (endpoint.getTopP() != null) {
                payload.put("top_p", endpoint.getTopP());
            }
            if (endpoint.getTopK() != null) {
                payload.put("top_k", endpoint.getTopK());
            }
            if (endpoint.getMinP() != null) {
                payload.put("min_p", endpoint.getMinP());
            }
            if (endpoint.getRepeatPenalty() != null) {
                payload.put("repeat_penalty", endpoint.getRepeatPenalty());
            }
            if (endpoint.getMaxOutputTokens() != null) {
                payload.put("max_tokens", endpoint.getMaxOutputTokens());
            }
        }

        JsonNode response = sendJsonPostWithRetries(
            url,
            payload,
            apiKey == null ? null : "Bearer " + apiKey,
            null,
            endpoint.getTimeoutMs(),
            endpoint.getMaxRetries()
        );

        JsonNode choices = response.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode choice = choices.get(0);
            JsonNode content = choice.path("message").path("content");
            if (!content.isMissingNode() && !content.asText().isBlank()) {
                return content.asText();
            }
        }
        return response.toString();
    }

    private String normalizeOpenRouterBaseUrl(String baseUrl, String fallback) {
        String url = normalizeBaseUrl(baseUrl, fallback);
        if (url.endsWith("/api/v1")) {
            url = url.substring(0, url.length() - 7);
        }
        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }
        return url;
    }
}
