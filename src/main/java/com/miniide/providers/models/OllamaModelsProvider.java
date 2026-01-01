package com.miniide.providers.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.ProviderModel;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;

public class OllamaModelsProvider extends AbstractModelsProvider {

    public OllamaModelsProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public List<ProviderModel> fetchModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
        String url = normalizeBaseUrl(baseUrl, "http://localhost:11434") + "/api/tags";
        JsonNode root = sendJsonRequest(url, null);
        JsonNode models = root.path("models");

        return dataToModels(models, node -> {
            String id = node.path("name").asText();
            if (id == null || id.isBlank()) return null;
            return new ProviderModel(id, id, false);
        });
    }
}
