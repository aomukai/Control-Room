package com.miniide.providers.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.models.AgentEndpointConfig;
import java.io.IOException;
import java.net.http.HttpClient;

public class OllamaChatProvider extends AbstractChatProvider {

    public OllamaChatProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public String chat(String apiKey, AgentEndpointConfig endpoint, String message)
        throws IOException, InterruptedException {
        String url = normalizeBaseUrl(endpoint.getBaseUrl(), "http://localhost:11434") + "/api/chat";

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", endpoint.getModel());
        payload.put("stream", false);

        ArrayNode messages = payload.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", message);

        JsonNode response = sendJsonPost(url, payload, null, null, endpoint.getTimeoutMs());

        JsonNode content = response.path("message").path("content");
        if (!content.isMissingNode()) {
            return content.asText();
        }
        JsonNode text = response.path("response");
        if (!text.isMissingNode()) {
            return text.asText();
        }
        return response.toString();
    }
}
