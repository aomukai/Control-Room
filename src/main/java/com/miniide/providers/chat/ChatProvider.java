package com.miniide.providers.chat;

import com.miniide.models.AgentEndpointConfig;
import java.io.IOException;

/**
 * Interface for AI chat providers.
 * Each provider implementation handles the specific API format for that service.
 */
public interface ChatProvider {

    /**
     * Get the provider name this implementation handles.
     */
    String getProviderName();

    /**
     * Send a chat message and get a response.
     *
     * @param apiKey The API key (may be null for local providers)
     * @param endpoint The endpoint configuration with model, baseUrl, etc.
     * @param message The user message to send
     * @return The assistant's response text
     */
    String chat(String apiKey, AgentEndpointConfig endpoint, String message)
        throws IOException, InterruptedException;

    /**
     * Send a chat message with an optional response_format payload (OpenAI-compatible).
     */
    default String chat(String apiKey, AgentEndpointConfig endpoint, String message,
                        com.fasterxml.jackson.databind.JsonNode responseFormat)
        throws IOException, InterruptedException {
        return chat(apiKey, endpoint, message);
    }
}
