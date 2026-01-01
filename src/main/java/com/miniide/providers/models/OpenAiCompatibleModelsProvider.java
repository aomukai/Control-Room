package com.miniide.providers.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.ProviderModel;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;

/**
 * OpenAI-compatible models provider.
 * Handles: lmstudio, jan, koboldcpp, custom, and any unknown providers.
 */
public class OpenAiCompatibleModelsProvider extends AbstractModelsProvider {

    private final String providerName;

    public OpenAiCompatibleModelsProvider(ObjectMapper mapper, HttpClient httpClient, String providerName) {
        super(mapper, httpClient);
        this.providerName = providerName;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public List<ProviderModel> fetchModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
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
}
