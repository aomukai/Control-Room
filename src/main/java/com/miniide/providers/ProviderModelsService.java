package com.miniide.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.ProviderModel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ProviderModelsService {
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public ProviderModelsService(ObjectMapper mapper) {
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<ProviderModel> fetchModels(String provider, String apiKey, String baseUrl) throws IOException, InterruptedException {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.US);
        switch (normalized) {
            case "openai":
                return fetchOpenAiModels(apiKey, baseUrl);
            case "anthropic":
                return fetchAnthropicModels(apiKey, baseUrl);
            case "gemini":
                return fetchGeminiModels(apiKey, baseUrl);
            case "grok":
                return fetchGrokModels(apiKey, baseUrl);
            case "openrouter":
                return fetchOpenRouterModels(apiKey, baseUrl);
            case "nanogpt":
                return fetchNanoGptModels(apiKey, baseUrl);
            case "togetherai":
                return fetchTogetherModels(apiKey, baseUrl);
            case "lmstudio":
            case "jan":
            case "koboldcpp":
            case "custom":
                return fetchOpenAiCompatibleModels(baseUrl, apiKey);
            case "ollama":
                return fetchOllamaModels(baseUrl);
            default:
                throw new IOException("Unsupported provider: " + provider);
        }
    }

    private List<ProviderModel> fetchOpenAiModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
        ensureApiKey(apiKey, "openai");
        String url = normalizeOpenAiBaseUrl(baseUrl, "https://api.openai.com") + "/v1/models";
        JsonNode data = sendJsonRequest(url, "Bearer " + apiKey);
        return dataToModels(data, node -> {
            String id = node.path("id").asText();
            if (id == null || id.isBlank()) return null;
            if (!(id.contains("gpt") || id.startsWith("o"))) return null;
            boolean recommended = id.equals("gpt-4o") || id.equals("gpt-4o-mini");
            return new ProviderModel(id, id.toUpperCase(Locale.US).replace("-", " "), recommended);
        });
    }

    private List<ProviderModel> fetchAnthropicModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
        ensureApiKey(apiKey, "anthropic");
        String url = normalizeBaseUrl(baseUrl, "https://api.anthropic.com") + "/v1/models";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("Anthropic model list failed (" + response.statusCode() + ")");
        }
        JsonNode root = mapper.readTree(response.body());
        JsonNode data = root.path("data");
        return dataToModels(data, node -> {
            String id = node.path("id").asText();
            String name = node.path("display_name").asText(id);
            if (id == null || id.isBlank()) return null;
            boolean recommended = id.contains("sonnet");
            return new ProviderModel(id, name, recommended);
        });
    }

    private List<ProviderModel> fetchGeminiModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
        ensureApiKey(apiKey, "gemini");
        String url = normalizeBaseUrl(baseUrl, "https://generativelanguage.googleapis.com")
            + "/v1beta/models?key=" + apiKey;
        JsonNode root = sendJsonRequest(url, null);
        JsonNode models = root.path("models");
        return dataToModels(models, node -> {
            String id = node.path("name").asText();
            if (id == null || id.isBlank()) return null;
            boolean supportsGeneration = false;
            JsonNode methods = node.path("supportedGenerationMethods");
            if (methods.isArray()) {
                for (JsonNode method : methods) {
                    String value = method.asText();
                    if ("generateContent".equals(value) || "streamGenerateContent".equals(value)) {
                        supportsGeneration = true;
                        break;
                    }
                }
            }
            if (!supportsGeneration) return null;
            String name = node.path("displayName").asText(id);
            boolean recommended = id.contains("gemini-1.5") || id.contains("gemini-2.0");
            return new ProviderModel(id, name, recommended);
        });
    }

    private List<ProviderModel> fetchGrokModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
        ensureApiKey(apiKey, "grok");
        String url = normalizeOpenAiBaseUrl(baseUrl, "https://api.x.ai/v1") + "/v1/models";
        JsonNode data = sendJsonRequest(url, "Bearer " + apiKey);
        return dataToModels(data, node -> {
            String id = node.path("id").asText();
            if (id == null || id.isBlank()) return null;
            return new ProviderModel(id, id, id.contains("grok"));
        });
    }

    private List<ProviderModel> fetchOpenRouterModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
        ensureApiKey(apiKey, "openrouter");
        String url = normalizeOpenRouterBaseUrl(baseUrl, "https://openrouter.ai") + "/api/v1/models";
        JsonNode root = sendJsonRequest(url, "Bearer " + apiKey);
        JsonNode data = root.path("data");
        List<ProviderModel> models = dataToModels(data, node -> {
            String id = node.path("id").asText();
            if (id == null || id.isBlank()) return null;
            String name = node.path("name").asText(id);
            boolean recommended = id.contains(":free") || id.contains("gemini");
            return new ProviderModel(id, name, recommended);
        });
        models.sort((a, b) -> {
            boolean aFree = a.getId() != null && a.getId().contains(":free");
            boolean bFree = b.getId() != null && b.getId().contains(":free");
            if (aFree != bFree) {
                return aFree ? -1 : 1;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
        });
        return models;
    }

    private List<ProviderModel> fetchNanoGptModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
        ensureApiKey(apiKey, "nanogpt");
        String base = normalizeNanoGptBaseUrl(baseUrl, "https://nano-gpt.com");
        String apiPath = nanoGptApiPath(baseUrl);
        String url = base + apiPath + "/models";
        String auth = (apiKey == null || apiKey.isBlank()) ? null : "Bearer " + apiKey;
        JsonNode response = sendJsonRequest(url, auth);
        JsonNode data = response.path("data");
        return dataToModels(data, node -> {
            String id = node.path("id").asText();
            if (id == null || id.isBlank()) return null;
            return new ProviderModel(id, id, false);
        });
    }

    private List<ProviderModel> fetchTogetherModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
        ensureApiKey(apiKey, "togetherai");
        String url = normalizeOpenAiBaseUrl(baseUrl, "https://api.together.xyz") + "/v1/models";
        JsonNode root = sendJsonRequest(url, "Bearer " + apiKey);
        JsonNode data = root.path("data");
        return dataToModels(data, node -> {
            String id = node.path("id").asText();
            if (id == null || id.isBlank()) return null;
            return new ProviderModel(id, id, id.contains("turbo"));
        });
    }

    private List<ProviderModel> fetchOpenAiCompatibleModels(String baseUrl, String apiKey) throws IOException, InterruptedException {
        String url = normalizeOpenAiBaseUrl(baseUrl, "http://localhost:1234") + "/v1/models";
        String auth = (apiKey == null || apiKey.isBlank()) ? null : "Bearer " + apiKey;
        JsonNode root = sendJsonRequest(url, auth);
        JsonNode data = root.path("data");
        return dataToModels(data, node -> {
            String id = node.path("id").asText();
            if (id == null || id.isBlank()) return null;
            return new ProviderModel(id, id, false);
        });
    }

    private List<ProviderModel> fetchOllamaModels(String baseUrl) throws IOException, InterruptedException {
        String url = normalizeBaseUrl(baseUrl, "http://localhost:11434") + "/api/tags";
        JsonNode root = sendJsonRequest(url, null);
        JsonNode models = root.path("models");
        return dataToModels(models, node -> {
            String id = node.path("name").asText();
            if (id == null || id.isBlank()) return null;
            return new ProviderModel(id, id, false);
        });
    }

    private void ensureApiKey(String apiKey, String provider) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("API key required for " + provider);
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
        if (url.endsWith("/api/v1/models")) {
            url = url.substring(0, url.length() - 14);
        }
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
        if (url.endsWith("/api/v1/models")) {
            url = url.substring(0, url.length() - 14);
        }
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

    private JsonNode sendJsonRequest(String url, String authorization) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .GET();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IOException("Model list request timed out for " + url);
        }
        if (response.statusCode() >= 300) {
            String body = response.body();
            String snippet = body == null ? "" : body.trim();
            if (snippet.length() > 200) {
                snippet = snippet.substring(0, 200) + "...";
            }
            String details = snippet.isEmpty() ? "" : (": " + snippet);
            throw new IOException("Model list request failed (" + response.statusCode() + ") for " + url + details);
        }
        return mapper.readTree(response.body());
    }

    private interface ModelMapper {
        ProviderModel map(JsonNode node);
    }

    private List<ProviderModel> dataToModels(JsonNode data, ModelMapper mapperFn) {
        List<ProviderModel> models = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                ProviderModel model = mapperFn.map(node);
                if (model != null) {
                    models.add(model);
                }
            }
        }
        return models;
    }
}
