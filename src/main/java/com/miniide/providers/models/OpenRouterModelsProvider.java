package com.miniide.providers.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.ProviderModel;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;

public class OpenRouterModelsProvider extends AbstractModelsProvider {

    public OpenRouterModelsProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "openrouter";
    }

    @Override
    public List<ProviderModel> fetchModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
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

        // Sort with free models first
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
}
