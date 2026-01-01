package com.miniide.providers.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.ProviderModel;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;

public class GrokModelsProvider extends AbstractModelsProvider {

    public GrokModelsProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "grok";
    }

    @Override
    public List<ProviderModel> fetchModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
        ensureApiKey(apiKey, "grok");
        String url = normalizeOpenAiBaseUrl(baseUrl, "https://api.x.ai/v1") + "/v1/models";
        JsonNode data = sendJsonRequest(url, "Bearer " + apiKey);

        return dataToModels(data, node -> {
            String id = node.path("id").asText();
            if (id == null || id.isBlank()) return null;
            return new ProviderModel(id, id, id.contains("grok"));
        });
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
