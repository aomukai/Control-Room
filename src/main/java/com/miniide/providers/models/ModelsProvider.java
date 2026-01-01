package com.miniide.providers.models;

import com.miniide.models.ProviderModel;
import java.io.IOException;
import java.util.List;

/**
 * Interface for fetching available models from AI providers.
 */
public interface ModelsProvider {

    /**
     * Get the provider name this implementation handles.
     */
    String getProviderName();

    /**
     * Fetch available models from the provider.
     *
     * @param apiKey The API key (may be null for local providers)
     * @param baseUrl The base URL override (may be null for default)
     * @return List of available models
     */
    List<ProviderModel> fetchModels(String apiKey, String baseUrl)
        throws IOException, InterruptedException;
}
