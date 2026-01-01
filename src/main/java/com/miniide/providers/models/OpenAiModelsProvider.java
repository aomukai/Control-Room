package com.miniide.providers.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.ProviderModel;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Locale;

public class OpenAiModelsProvider extends AbstractModelsProvider {

    public OpenAiModelsProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public List<ProviderModel> fetchModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
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
