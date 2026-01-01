package com.miniide.providers.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.ProviderModel;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;

public class GeminiModelsProvider extends AbstractModelsProvider {

    public GeminiModelsProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public List<ProviderModel> fetchModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
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
}
