package com.miniide.providers.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Abstract base class for chat providers with shared HTTP logic.
 */
public abstract class AbstractChatProvider implements ChatProvider {

    protected final ObjectMapper mapper;
    protected final HttpClient httpClient;

    protected AbstractChatProvider(ObjectMapper mapper, HttpClient httpClient) {
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    /**
     * Send a JSON POST request and return the parsed response.
     */
    protected JsonNode sendJsonPost(String url, JsonNode payload, String bearerAuth, String anthropicKey)
        throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));

        if (bearerAuth != null && !bearerAuth.isBlank()) {
            builder.header("Authorization", bearerAuth);
        }
        if (anthropicKey != null && !anthropicKey.isBlank()) {
            builder.header("x-api-key", anthropicKey);
            builder.header("anthropic-version", "2023-06-01");
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Chat request failed (" + status + "): " + response.body());
        }
        return mapper.readTree(response.body());
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
}
