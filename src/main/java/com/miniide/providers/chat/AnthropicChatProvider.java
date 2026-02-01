package com.miniide.providers.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.models.AgentEndpointConfig;
import java.io.IOException;
import java.net.http.HttpClient;

public class AnthropicChatProvider extends AbstractChatProvider {

    public AnthropicChatProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public String chat(String apiKey, AgentEndpointConfig endpoint, String message)
        throws IOException, InterruptedException {
        String url = normalizeBaseUrl(endpoint.getBaseUrl(), "https://api.anthropic.com") + "/v1/messages";

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", endpoint.getModel());
        boolean useDefaults = endpoint.getUseProviderDefaults() != null && endpoint.getUseProviderDefaults();
        int maxTokens = endpoint.getMaxOutputTokens() != null && !useDefaults
            ? endpoint.getMaxOutputTokens()
            : 512;
        payload.put("max_tokens", maxTokens);

        if (!useDefaults) {
            if (endpoint.getTemperature() != null) {
                payload.put("temperature", endpoint.getTemperature());
            }
            if (endpoint.getTopP() != null) {
                payload.put("top_p", endpoint.getTopP());
            }
            if (endpoint.getTopK() != null) {
                payload.put("top_k", endpoint.getTopK());
            }
        }

        ArrayNode messages = payload.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", message);

        JsonNode response = sendJsonPost(url, payload, null, apiKey, endpoint.getTimeoutMs());

        JsonNode content = response.path("content");
        if (content.isArray() && content.size() > 0) {
            JsonNode first = content.get(0);
            JsonNode text = first.path("text");
            if (!text.isMissingNode()) {
                return text.asText();
            }
        }
        return response.toString();
    }
}
