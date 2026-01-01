package com.miniide.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.ProviderModel;
import com.miniide.providers.models.ModelsProvider;
import com.miniide.providers.models.ModelsProviderFactory;

import java.io.IOException;
import java.util.List;

/**
 * Service for fetching available models from AI providers.
 * Delegates to provider-specific implementations via ModelsProviderFactory.
 */
public class ProviderModelsService {

    private final ModelsProviderFactory providerFactory;

    public ProviderModelsService(ObjectMapper mapper) {
        this.providerFactory = new ModelsProviderFactory(mapper);
    }

    /**
     * Fetch available models from the specified provider.
     *
     * @param provider The provider name (openai, anthropic, gemini, etc.)
     * @param apiKey The API key (may be null for local providers)
     * @param baseUrl The base URL override (may be null for default)
     * @return List of available models
     */
    public List<ProviderModel> fetchModels(String provider, String apiKey, String baseUrl)
        throws IOException, InterruptedException {

        ModelsProvider modelsProvider = providerFactory.getProvider(provider);
        return modelsProvider.fetchModels(apiKey, baseUrl);
    }
}
