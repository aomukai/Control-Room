package com.miniide.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniide.models.AgentEndpointConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ProviderChatService {

    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public ProviderChatService(ObjectMapper mapper) {
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public String chat(String provider, String apiKey, AgentEndpointConfig endpoint, String message)
        throws IOException, InterruptedException {
        if (provider == null || provider.isBlank()) {
            throw new IOException("Provider is required.");
        }
        if (endpoint == null) {
            throw new IOException("Endpoint configuration is required.");
        }
        String model = endpoint.getModel();
        if (model == null || model.isBlank()) {
            throw new IOException("Model is required.");
        }
        String baseUrl = endpoint.getBaseUrl();

        switch (provider) {
            case "anthropic":
                return chatAnthropic(apiKey, baseUrl, model, endpoint, message);
            case "gemini":
                return chatGemini(apiKey, baseUrl, model, message);
            case "ollama":
                return chatOllama(baseUrl, model, message);
            case "openrouter":
                return chatOpenRouter(apiKey, baseUrl, model, endpoint, message);
            case "nanogpt":
                return chatNanoGpt(apiKey, baseUrl, model, endpoint, message);
            case "openai":
            case "grok":
            case "togetherai":
            case "lmstudio":
            case "jan":
            case "koboldcpp":
            case "custom":
            default:
                return chatOpenAiCompatible(apiKey, baseUrl, provider, model, endpoint, message);
        }
    }

    private String chatOpenAiCompatible(String apiKey, String baseUrl, String provider, String model,
                                        AgentEndpointConfig endpoint, String message)
        throws IOException, InterruptedException {
        String url = normalizeOpenAiBaseUrl(baseUrl, defaultOpenAiBase(provider)) + "/v1/chat/completions";
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        ArrayNode messages = payload.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", message);
        if (endpoint.getTemperature() != null) {
            payload.put("temperature", endpoint.getTemperature());
        }
        if (endpoint.getMaxOutputTokens() != null) {
            payload.put("max_tokens", endpoint.getMaxOutputTokens());
        }
        JsonNode response = sendJsonPost(url, payload, apiKey == null ? null : "Bearer " + apiKey, null);
        JsonNode choices = response.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode choice = choices.get(0);
            JsonNode content = choice.path("message").path("content");
            if (!content.isMissingNode() && !content.asText().isBlank()) {
                return content.asText();
            }
            JsonNode text = choice.path("text");
            if (!text.isMissingNode()) {
                return text.asText();
            }
        }
        return response.toString();
    }

    private String chatOpenRouter(String apiKey, String baseUrl, String model, AgentEndpointConfig endpoint,
                                  String message)
        throws IOException, InterruptedException {
        String url = normalizeOpenRouterBaseUrl(baseUrl, "https://openrouter.ai") + "/api/v1/chat/completions";
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        ArrayNode messages = payload.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", message);
        if (endpoint.getTemperature() != null) {
            payload.put("temperature", endpoint.getTemperature());
        }
        if (endpoint.getMaxOutputTokens() != null) {
            payload.put("max_tokens", endpoint.getMaxOutputTokens());
        }
        JsonNode response = sendJsonPost(url, payload, apiKey == null ? null : "Bearer " + apiKey, null);
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

    private String chatNanoGpt(String apiKey, String baseUrl, String model, AgentEndpointConfig endpoint,
                               String message)
        throws IOException, InterruptedException {
        String base = normalizeNanoGptBaseUrl(baseUrl, "https://nano-gpt.com");
        String apiPath = nanoGptApiPath(baseUrl);
        String url = base + apiPath + "/chat/completions";
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        ArrayNode messages = payload.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", message);
        if (endpoint.getTemperature() != null) {
            payload.put("temperature", endpoint.getTemperature());
        }
        if (endpoint.getMaxOutputTokens() != null) {
            payload.put("max_tokens", endpoint.getMaxOutputTokens());
        }
        JsonNode response = sendJsonPost(url, payload, apiKey == null ? null : "Bearer " + apiKey, null);
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

    private String chatAnthropic(String apiKey, String baseUrl, String model, AgentEndpointConfig endpoint,
                                 String message)
        throws IOException, InterruptedException {
        String url = normalizeBaseUrl(baseUrl, "https://api.anthropic.com") + "/v1/messages";
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        payload.put("max_tokens", endpoint.getMaxOutputTokens() != null ? endpoint.getMaxOutputTokens() : 512);
        if (endpoint.getTemperature() != null) {
            payload.put("temperature", endpoint.getTemperature());
        }
        ArrayNode messages = payload.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", message);
        JsonNode response = sendJsonPost(url, payload, null, apiKey);
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

    private String chatGemini(String apiKey, String baseUrl, String model, String message)
        throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("API key required for gemini");
        }
        String url = normalizeGeminiBaseUrl(baseUrl, "https://generativelanguage.googleapis.com")
            + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode contents = payload.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", message);
        JsonNode response = sendJsonPost(url, payload, null, null);
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

    private String chatOllama(String baseUrl, String model, String message)
        throws IOException, InterruptedException {
        String url = normalizeBaseUrl(baseUrl, "http://localhost:11434") + "/api/chat";
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        payload.put("stream", false);
        ArrayNode messages = payload.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", message);
        JsonNode response = sendJsonPost(url, payload, null, null);
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
                return "http://localhost:1234";
            default:
                return "http://localhost:1234";
        }
    }

    private String normalizeBaseUrl(String baseUrl, String fallback) {
        String url = (baseUrl == null || baseUrl.isBlank()) ? fallback : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
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

    private JsonNode sendJsonPost(String url, JsonNode payload, String bearerAuth, String anthropicKey)
        throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));
        if (bearerAuth != null && !bearerAuth.isBlank()) {
            builder.header("Authorization", bearerAuth);
        }
        if (anthropicKey != null && !anthropicKey.isBlank()) {
            builder.header("x-api-key", anthropicKey);
            builder.header("anthropic-version", "2023-06-01");
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Chat request failed (" + status + "): " + response.body());
        }
        return mapper.readTree(response.body());
    }
}
