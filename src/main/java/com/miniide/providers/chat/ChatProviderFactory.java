package com.miniide.providers.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and caching chat provider instances.
 */
public class ChatProviderFactory {

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final Map<String, ChatProvider> providerCache = new ConcurrentHashMap<>();

    public ChatProviderFactory(ObjectMapper mapper) {
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Get a chat provider for the given provider name.
     * Providers are cached for reuse.
     */
    public ChatProvider getProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            providerName = "custom";
        }

        return providerCache.computeIfAbsent(providerName, this::createProvider);
    }

    private ChatProvider createProvider(String providerName) {
        switch (providerName) {
            case "anthropic":
                return new AnthropicChatProvider(mapper, httpClient);
            case "gemini":
                return new GeminiChatProvider(mapper, httpClient);
            case "ollama":
                return new OllamaChatProvider(mapper, httpClient);
            case "openrouter":
                return new OpenRouterChatProvider(mapper, httpClient);
            case "nanogpt":
                return new NanoGptChatProvider(mapper, httpClient);
            case "openai":
            case "grok":
            case "togetherai":
            case "lmstudio":
            case "jan":
            case "koboldcpp":
            case "custom":
            default:
                return new OpenAiCompatibleChatProvider(mapper, httpClient, providerName);
        }
    }
}
