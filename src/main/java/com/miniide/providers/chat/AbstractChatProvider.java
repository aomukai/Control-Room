package com.miniide.providers.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abstract base class for chat providers with shared HTTP logic.
 */
public abstract class AbstractChatProvider implements ChatProvider {

    protected final ObjectMapper mapper;
    protected final HttpClient httpClient;
    protected static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 300;

    protected AbstractChatProvider(ObjectMapper mapper, HttpClient httpClient) {
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    /**
     * Send a JSON POST request and return the parsed response.
     */
    protected JsonNode sendJsonPost(String url, JsonNode payload, String bearerAuth, String anthropicKey)
        throws IOException, InterruptedException {
        return sendJsonPost(url, payload, bearerAuth, anthropicKey, null);
    }

    protected JsonNode sendJsonPost(String url, JsonNode payload, String bearerAuth, String anthropicKey, Integer timeoutMs)
        throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(resolveTimeout(timeoutMs))
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
     * Best-effort retry wrapper for transient network/provider failures.
     * This is intentionally conservative: callers opt-in by using this method.
     */
    protected JsonNode sendJsonPostWithRetries(String url, JsonNode payload,
                                               String bearerAuth, String anthropicKey,
                                               Integer timeoutMs, Integer maxRetries)
        throws IOException, InterruptedException {
        // Default higher than 2: consumer networks can be lossy, and provider edges can flap.
        int retries = maxRetries != null ? Math.max(0, maxRetries) : 6;
        IOException lastIo = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return sendJsonPost(url, payload, bearerAuth, anthropicKey, timeoutMs);
            } catch (IOException e) {
                lastIo = e;
                if (attempt >= retries || !isRetryableChatFailure(e)) {
                    throw e;
                }
                sleepBackoff(attempt);
            }
        }
        throw lastIo != null ? lastIo : new IOException("Chat request failed");
    }

    private boolean isRetryableChatFailure(IOException e) {
        if (e == null) return false;
        String msg = e.getMessage() != null ? e.getMessage() : "";
        // Retry common transient network errors and provider 5xx/429.
        if (msg.contains("EOF reached while reading")) return true;
        if (msg.contains("Connection reset")) return true;
        if (msg.contains("timed out") || msg.contains("Timeout")) return true;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d{3})\\)").matcher(msg);
        if (m.find()) {
            try {
                int code = Integer.parseInt(m.group(1));
                return code == 429 || (code >= 500 && code <= 599);
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private void sleepBackoff(int attempt) throws InterruptedException {
        // Exponential-ish backoff with jitter: 350ms, 900ms, 1800ms, 2800ms, 4000ms...
        long base;
        if (attempt <= 0) base = 350;
        else if (attempt == 1) base = 900;
        else if (attempt == 2) base = 1800;
        else base = 2800L + 1200L * (attempt - 3);
        long jitter = ThreadLocalRandom.current().nextLong(0, 220);
        Thread.sleep(Math.min(10_000, base + jitter));
    }

    protected Duration resolveTimeout(Integer timeoutMs) {
        if (timeoutMs != null && timeoutMs > 0) {
            return Duration.ofMillis(timeoutMs);
        }
        return Duration.ofSeconds(DEFAULT_REQUEST_TIMEOUT_SECONDS);
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
