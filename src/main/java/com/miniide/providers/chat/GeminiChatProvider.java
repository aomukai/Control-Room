package com.miniide.providers.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.models.AgentEndpointConfig;
import java.io.IOException;
import java.net.http.HttpClient;

public class GeminiChatProvider extends AbstractChatProvider {

    public GeminiChatProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public String chat(String apiKey, AgentEndpointConfig endpoint, String message)
        throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("API key required for gemini");
        }

        String baseUrl = normalizeGeminiBaseUrl(endpoint.getBaseUrl(), "https://generativelanguage.googleapis.com");
        String url = baseUrl + "/v1beta/models/" + endpoint.getModel() + ":generateContent?key=" + apiKey;

        ObjectNode payload = mapper.createObjectNode();
        ArrayNode contents = payload.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", message);

        boolean useDefaults = endpoint.getUseProviderDefaults() != null && endpoint.getUseProviderDefaults();
        if (!useDefaults) {
            ObjectNode generationConfig = payload.putObject("generationConfig");
            if (endpoint.getTemperature() != null) {
                generationConfig.put("temperature", endpoint.getTemperature());
            }
            if (endpoint.getTopP() != null) {
                generationConfig.put("topP", endpoint.getTopP());
            }
            if (endpoint.getTopK() != null) {
                generationConfig.put("topK", endpoint.getTopK());
            }
            if (endpoint.getMaxOutputTokens() != null) {
                generationConfig.put("maxOutputTokens", endpoint.getMaxOutputTokens());
            }
        }

        JsonNode response = sendJsonPost(url, payload, null, null, endpoint.getTimeoutMs());

        JsonNode candidates = response.path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
            JsonNode first = candidates.get(0);
            JsonNode partsNode = first.path("content").path("parts");
            if (partsNode.isArray() && partsNode.size() > 0) {
                return partsNode.get(0).path("text").asText();
            }
        }
        return response.toString();
    }

    private String normalizeGeminiBaseUrl(String baseUrl, String fallback) {
        String url = normalizeBaseUrl(baseUrl, fallback);
        if (url.endsWith("/v1beta")) {
            url = url.substring(0, url.length() - 7);
        }
        if (url.endsWith("/v1beta/models")) {
            url = url.substring(0, url.length() - 14);
        }
        return url;
    }
}
