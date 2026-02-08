package com.miniide.providers.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.models.AgentEndpointConfig;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Set;

/**
 * OpenAI-compatible chat provider.
 * Handles: openai, grok, togetherai, lmstudio, jan, koboldcpp, custom
 */
public class OpenAiCompatibleChatProvider extends AbstractChatProvider {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
        "openai", "grok", "togetherai", "lmstudio", "jan", "koboldcpp", "custom"
    );

    private final String providerName;

    public OpenAiCompatibleChatProvider(ObjectMapper mapper, HttpClient httpClient, String providerName) {
        super(mapper, httpClient);
        this.providerName = providerName;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    public static boolean supportsProvider(String provider) {
        return SUPPORTED_PROVIDERS.contains(provider);
    }

    @Override
    public String chat(String apiKey, AgentEndpointConfig endpoint, String message)
        throws IOException, InterruptedException {
        return chat(apiKey, endpoint, message, null);
    }

    @Override
    public String chat(String apiKey, AgentEndpointConfig endpoint, String message, JsonNode responseFormat)
        throws IOException, InterruptedException {
        String url = normalizeOpenAiBaseUrl(endpoint.getBaseUrl(), defaultOpenAiBase(providerName)) + "/v1/chat/completions";

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", endpoint.getModel());

        ArrayNode messages = payload.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", message);

        if (responseFormat != null && !responseFormat.isNull()) {
            payload.set("response_format", responseFormat);
        }

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
            JsonNode messageNode = choice.path("message");
            JsonNode content = messageNode.path("content");
            if (!content.isMissingNode() && !content.asText().isBlank()) {
                return content.asText();
            }
            JsonNode reasoning = messageNode.path("reasoning");
            if (!reasoning.isMissingNode() && !reasoning.asText().isBlank()) {
                return reasoning.asText();
            }
            JsonNode text = choice.path("text");
            if (!text.isMissingNode()) {
                return text.asText();
            }
        }
        return response.toString();
    }

    private String defaultOpenAiBase(String provider) {
        switch (provider) {
            case "openai":
                return "https://api.openai.com";
            case "grok":
                return "https://api.x.ai/v1";
            case "togetherai":
                return "https://api.together.xyz";
            case "lmstudio":
            case "jan":
            case "koboldcpp":
            default:
                return "http://localhost:1234";
        }
    }

    private String normalizeOpenAiBaseUrl(String baseUrl, String fallback) {
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
