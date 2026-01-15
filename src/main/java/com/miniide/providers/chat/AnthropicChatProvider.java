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
        payload.put("max_tokens", endpoint.getMaxOutputTokens() != null ? endpoint.getMaxOutputTokens() : 512);

        if (endpoint.getTemperature() != null) {
            payload.put("temperature", endpoint.getTemperature());
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
