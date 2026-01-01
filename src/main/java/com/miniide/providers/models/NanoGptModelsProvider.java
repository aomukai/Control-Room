package com.miniide.providers.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.ProviderModel;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;

public class NanoGptModelsProvider extends AbstractModelsProvider {

    public NanoGptModelsProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "nanogpt";
    }

    @Override
    public List<ProviderModel> fetchModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
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
