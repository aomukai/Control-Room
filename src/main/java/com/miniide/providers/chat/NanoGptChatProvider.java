package com.miniide.providers.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.models.AgentEndpointConfig;
import java.io.IOException;
import java.net.http.HttpClient;

public class NanoGptChatProvider extends AbstractChatProvider {

    public NanoGptChatProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "nanogpt";
    }

    @Override
    public String chat(String apiKey, AgentEndpointConfig endpoint, String message)
        throws IOException, InterruptedException {
        String base = normalizeNanoGptBaseUrl(endpoint.getBaseUrl(), "https://nano-gpt.com");
        String apiPath = nanoGptApiPath(endpoint.getBaseUrl());
        String url = base + apiPath + "/chat/completions";

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", endpoint.getModel());

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

        JsonNode response = sendJsonPost(url, payload, apiKey == null ? null : "Bearer " + apiKey, null, endpoint.getTimeoutMs());

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

    private String normalizeNanoGptBaseUrl(String baseUrl, String fallback) {
        String url = normalizeBaseUrl(baseUrl, fallback);
        if (url.endsWith("/api/v1legacy")) {
            url = url.substring(0, url.length() - 13);
        }
        if (url.endsWith("/api/v1")) {
            url = url.substring(0, url.length() - 7);
        }
        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }
        return url;
    }

    private String nanoGptApiPath(String baseUrl) {
        if (baseUrl == null) {
            return "/api/v1";
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/api/v1legacy") || trimmed.contains("/api/v1legacy")) {
            return "/api/v1legacy";
        }
        return "/api/v1";
    }
}
