package com.miniide.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.AgentEndpointConfig;
import com.miniide.providers.chat.ChatProvider;
import com.miniide.providers.chat.ChatProviderFactory;

import java.io.IOException;

/**
 * Service for chat operations with AI providers.
 * Delegates to provider-specific implementations via ChatProviderFactory.
 */
public class ProviderChatService {

    private final ChatProviderFactory providerFactory;

    public ProviderChatService(ObjectMapper mapper) {
        this.providerFactory = new ChatProviderFactory(mapper);
    }

    /**
     * Send a chat message to the specified provider.
     *
     * @param provider The provider name (anthropic, openai, gemini, etc.)
     * @param apiKey The API key (may be null for local providers)
     * @param endpoint The endpoint configuration
     * @param message The user message
     * @return The assistant's response
     */
    public String chat(String provider, String apiKey, AgentEndpointConfig endpoint, String message)
        throws IOException, InterruptedException {

        if (provider == null || provider.isBlank()) {
            throw new IOException("Provider is required.");
        }
        if (endpoint == null) {
            throw new IOException("Endpoint configuration is required.");
        }
        if (endpoint.getModel() == null || endpoint.getModel().isBlank()) {
            throw new IOException("Model is required.");
        }

        ChatProvider chatProvider = providerFactory.getProvider(provider);
        return chatProvider.chat(apiKey, endpoint, message);
    }

    public String chat(String provider, String apiKey, AgentEndpointConfig endpoint, String message,
                       com.fasterxml.jackson.databind.JsonNode responseFormat)
        throws IOException, InterruptedException {

        if (provider == null || provider.isBlank()) {
            throw new IOException("Provider is required.");
        }
        if (endpoint == null) {
            throw new IOException("Endpoint configuration is required.");
        }
        if (endpoint.getModel() == null || endpoint.getModel().isBlank()) {
            throw new IOException("Model is required.");
        }

        ChatProvider chatProvider = providerFactory.getProvider(provider);
        return chatProvider.chat(apiKey, endpoint, message, responseFormat);
    }
}
