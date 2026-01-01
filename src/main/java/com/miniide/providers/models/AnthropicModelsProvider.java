package com.miniide.providers.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.ProviderModel;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class AnthropicModelsProvider extends AbstractModelsProvider {

    public AnthropicModelsProvider(ObjectMapper mapper, HttpClient httpClient) {
        super(mapper, httpClient);
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public List<ProviderModel> fetchModels(String apiKey, String baseUrl) throws IOException, InterruptedException {
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
}
