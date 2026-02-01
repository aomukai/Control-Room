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

        boolean useDefaults = endpoint.getUseProviderDefaults() != null && endpoint.getUseProviderDefaults();
        if (!useDefaults) {
            ObjectNode options = payload.putObject("options");
            if (endpoint.getTemperature() != null) {
                options.put("temperature", endpoint.getTemperature());
            }
            if (endpoint.getTopP() != null) {
                options.put("top_p", endpoint.getTopP());
            }
            if (endpoint.getTopK() != null) {
                options.put("top_k", endpoint.getTopK());
            }
            if (endpoint.getMinP() != null) {
                options.put("min_p", endpoint.getMinP());
            }
            if (endpoint.getRepeatPenalty() != null) {
                options.put("repeat_penalty", endpoint.getRepeatPenalty());
            }
            if (endpoint.getMaxOutputTokens() != null) {
                options.put("num_predict", endpoint.getMaxOutputTokens());
            }
        }

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
