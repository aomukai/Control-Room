package com.miniide.providers.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and caching models provider instances.
 */
public class ModelsProviderFactory {

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final Map<String, ModelsProvider> providerCache = new ConcurrentHashMap<>();

    public ModelsProviderFactory(ObjectMapper mapper) {
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Get a models provider for the given provider name.
     * Providers are cached for reuse.
     */
    public ModelsProvider getProvider(String providerName) {
        String normalized = providerName == null ? "" : providerName.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            normalized = "custom";
        }

        final String key = normalized;
        return providerCache.computeIfAbsent(key, this::createProvider);
    }

    private ModelsProvider createProvider(String providerName) {
        switch (providerName) {
            case "openai":
                return new OpenAiModelsProvider(mapper, httpClient);
            case "anthropic":
                return new AnthropicModelsProvider(mapper, httpClient);
            case "gemini":
                return new GeminiModelsProvider(mapper, httpClient);
            case "grok":
                return new GrokModelsProvider(mapper, httpClient);
            case "openrouter":
                return new OpenRouterModelsProvider(mapper, httpClient);
            case "nanogpt":
                return new NanoGptModelsProvider(mapper, httpClient);
            case "togetherai":
                return new TogetherAiModelsProvider(mapper, httpClient);
            case "ollama":
                return new OllamaModelsProvider(mapper, httpClient);
            case "lmstudio":
            case "jan":
            case "koboldcpp":
            case "custom":
            default:
                return new OpenAiCompatibleModelsProvider(mapper, httpClient, providerName);
        }
    }
}
