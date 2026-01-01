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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Abstract base class for models providers with shared HTTP and parsing logic.
 */
public abstract class AbstractModelsProvider implements ModelsProvider {

    protected final ObjectMapper mapper;
    protected final HttpClient httpClient;

    protected AbstractModelsProvider(ObjectMapper mapper, HttpClient httpClient) {
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    /**
     * Ensure an API key is provided, throwing if not.
     */
    protected void ensureApiKey(String apiKey, String provider) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("API key required for " + provider);
        }
    }

    /**
     * Normalize a base URL by removing trailing slashes.
     */
    protected String normalizeBaseUrl(String baseUrl, String fallback) {
        String url = (baseUrl == null || baseUrl.isBlank()) ? fallback : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Send a GET request and parse JSON response.
     */
    protected JsonNode sendJsonRequest(String url, String authorization) throws IOException, InterruptedException {
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

    /**
     * Convert a JSON array to a list of models using a mapper function.
     */
    protected List<ProviderModel> dataToModels(JsonNode data, Function<JsonNode, ProviderModel> mapperFn) {
        List<ProviderModel> models = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                ProviderModel model = mapperFn.apply(node);
                if (model != null) {
                    models.add(model);
                }
            }
        }
        return models;
    }
}
