package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.providers.ProviderModelsService;
import com.miniide.settings.AgentKeysMetadataFile;
import com.miniide.settings.SecuritySettings;
import com.miniide.settings.SettingsService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

/**
 * Controller for settings and API key management.
 */
public class SettingsController implements Controller {

    private final SettingsService settingsService;
    private final ProviderModelsService providerModelsService;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public SettingsController(SettingsService settingsService, ProviderModelsService providerModelsService,
                             ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.providerModelsService = providerModelsService;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/settings/security", this::getSecuritySettings);
        app.put("/api/settings/security", this::updateSecuritySettings);
        app.post("/api/settings/security/unlock", this::unlockSecurityVault);
        app.post("/api/settings/security/lock", this::lockSecurityVault);
        app.get("/api/settings/keys", this::listApiKeys);
        app.post("/api/settings/keys", this::addApiKey);
        app.delete("/api/settings/keys/{provider}/{id}", this::deleteApiKey);
        app.get("/api/providers/models", this::getProviderModels);
    }

    private void getSecuritySettings(Context ctx) {
        SecuritySettings settings = settingsService.getSecuritySettings();
        ctx.json(Map.of(
            "keysSecurityMode", settings.getKeysSecurityMode(),
            "vaultUnlocked", settingsService.isVaultUnlocked()
        ));
    }

    private void updateSecuritySettings(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String mode = json.has("keysSecurityMode") ? json.get("keysSecurityMode").asText() : null;
            String password = json.has("password") ? json.get("password").asText() : null;
            SecuritySettings updated = settingsService.updateSecurityMode(mode, password);
            ctx.json(Map.of("keysSecurityMode", updated.getKeysSecurityMode()));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Failed to update security settings: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void unlockSecurityVault(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String password = json.has("password") ? json.get("password").asText() : null;
            if (password == null || password.isBlank()) {
                ctx.status(400).json(Map.of("error", "Password is required"));
                return;
            }
            settingsService.unlockVault(password);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            logger.error("Failed to unlock key vault: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void lockSecurityVault(Context ctx) {
        settingsService.lockVault();
        ctx.json(Map.of("ok", true));
    }

    private void listApiKeys(Context ctx) {
        try {
            AgentKeysMetadataFile metadata = settingsService.listKeyMetadata();
            ctx.json(metadata);
        } catch (Exception e) {
            logger.error("Failed to list API keys: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void addApiKey(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String provider = json.has("provider") ? json.get("provider").asText() : null;
            String label = json.has("label") ? json.get("label").asText() : null;
            String key = json.has("key") ? json.get("key").asText() : null;
            String id = json.has("id") ? json.get("id").asText() : null;
            String password = json.has("password") ? json.get("password").asText() : null;
            String keyRef = settingsService.addKey(provider, label, key, id, password);
            ctx.json(Map.of("keyRef", keyRef));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Failed to add API key: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void deleteApiKey(Context ctx) {
        try {
            String provider = ctx.pathParam("provider");
            String id = ctx.pathParam("id");
            JsonNode json = ctx.body().isBlank() ? null : objectMapper.readTree(ctx.body());
            String password = json != null && json.has("password") ? json.get("password").asText() : null;
            settingsService.deleteKey(provider, id, password);
            ctx.json(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Failed to delete API key: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getProviderModels(Context ctx) {
        try {
            String provider = ctx.queryParam("provider");
            String baseUrl = ctx.queryParam("baseUrl");
            String keyRef = ctx.queryParam("keyRef");
            String apiKey = null;
            if (keyRef != null && !keyRef.isBlank()) {
                apiKey = settingsService.resolveKey(keyRef);
            }
            if (provider == null || provider.isBlank()) {
                ctx.status(400).json(Map.of("error", "provider is required"));
                return;
            }
            ctx.json(providerModelsService.fetchModels(provider, apiKey, baseUrl));
        } catch (IllegalStateException e) {
            ctx.status(401).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Failed to fetch provider models: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }
}
