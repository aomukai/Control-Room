package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.AgentTurnGate;
import com.miniide.MemoryService;
import com.miniide.ProjectContext;
import com.miniide.models.Agent;
import com.miniide.providers.ProviderChatService;
import com.miniide.settings.SettingsService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for AI chat operations.
 */
public class ChatController implements Controller {

    private static final AgentTurnGate AGENT_TURN_GATE = new AgentTurnGate();
    private final ProjectContext projectContext;
    private final SettingsService settingsService;
    private final ProviderChatService providerChatService;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public ChatController(ProjectContext projectContext,
                          SettingsService settingsService, ProviderChatService providerChatService,
                          MemoryService memoryService, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.settingsService = settingsService;
        this.providerChatService = providerChatService;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.post("/api/ai/chat", this::aiChat);
    }

    private void aiChat(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String message = json.has("message") ? json.get("message").asText() : "";
            String agentId = json.has("agentId") ? json.get("agentId").asText() : null;
            String memoryId = json.has("memoryId") ? json.get("memoryId").asText(null) : null;
            boolean reroll = json.has("reroll") && json.get("reroll").asBoolean();
            String levelParam = json.has("level") ? json.get("level").asText() : null;
            boolean needMore = message != null && message.toUpperCase().contains("NEED_MORE_CONTEXT");
            boolean requestMore = reroll || needMore || "more".equalsIgnoreCase(levelParam);

            MemoryService.MemoryResult memoryResult = null;
            if (memoryId != null && !memoryId.isBlank()) {
                memoryResult = requestMore
                    ? memoryService.getMemoryAtNextLevel(memoryId)
                    : memoryService.getMemoryAtAutoLevel(memoryId);
            }

            if (agentId != null && !agentId.isBlank()) {
                Agent agent = projectContext.agents().getAgent(agentId);
                if (agent == null) {
                    ctx.status(404).json(Map.of("error", "Agent not found: " + agentId));
                    return;
                }
                var endpoint = projectContext.agentEndpoints().getEndpoint(agentId);
                if (endpoint == null) {
                    endpoint = agent.getEndpoint();
                }
                if (endpoint == null || endpoint.getProvider() == null || endpoint.getProvider().isBlank()) {
                    ctx.status(400).json(Map.of("error", "Agent endpoint not configured"));
                    return;
                }
                if (endpoint.getModel() == null || endpoint.getModel().isBlank()) {
                    ctx.status(400).json(Map.of("error", "Agent model not configured"));
                    return;
                }
                String provider = endpoint.getProvider().trim().toLowerCase();
                String apiKey = null;
                if (requiresApiKey(provider)) {
                    String keyRef = endpoint.getApiKeyRef();
                    if (keyRef == null || keyRef.isBlank()) {
                        ctx.status(400).json(Map.of("error", "API key required for " + provider));
                        return;
                    }
                    apiKey = settingsService.resolveKey(keyRef);
                } else if (endpoint.getApiKeyRef() != null && !endpoint.getApiKeyRef().isBlank()) {
                    apiKey = settingsService.resolveKey(endpoint.getApiKeyRef());
                }

                memoryService.recordAgentActivation(agentId);
                final String providerName = provider;
                final String keyRef = apiKey;
                final var agentEndpoint = endpoint;
                final String prompt = message;
                String response = AGENT_TURN_GATE.run(() -> providerChatService.chat(providerName, keyRef, agentEndpoint, prompt));
                ctx.json(buildResponse(response, memoryResult, memoryId, requestMore));
                return;
            }

            String response = generateStubResponse(message);

            ctx.json(buildResponse(response, memoryResult, memoryId, requestMore));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private Map<String, Object> buildResponse(String content, MemoryService.MemoryResult memoryResult,
                                              String memoryId, boolean requestMore) {
        Map<String, Object> body = new HashMap<>();
        String enrichedContent = content;

        if (memoryResult != null && memoryResult.getVersion() != null) {
            String snippet = memoryResult.getVersion().getContent();
            if (snippet != null && snippet.length() > 280) {
                snippet = snippet.substring(0, 277) + "...";
            }
            enrichedContent = content + "\n\n[Context R" + memoryResult.getVersion().getRepLevel()
                + (memoryResult.isEscalated() ? "↑" : "") + "] " + (snippet != null ? snippet : "(no content)");
            body.put("memoryId", memoryId);
            body.put("repLevel", memoryResult.getVersion().getRepLevel());
            body.put("escalated", memoryResult.isEscalated());
        } else if (requestMore) {
            body.put("escalated", true);
        }

        body.put("role", "assistant");
        body.put("content", enrichedContent);
        return body;
    }

    private boolean requiresApiKey(String provider) {
        if (provider == null) return false;
        switch (provider.toLowerCase()) {
            case "openai":
            case "anthropic":
            case "gemini":
            case "grok":
            case "openrouter":
            case "nanogpt":
            case "togetherai":
                return true;
            default:
                return false;
        }
    }

    private String generateStubResponse(String message) {
        String lower = message.toLowerCase();

        if (lower.contains("hello") || lower.contains("hi")) {
            return "Hello! I'm your AI writing assistant. I can help you with your creative writing project. " +
                   "Try asking me about character development, plot ideas, or scene descriptions!";
        }

        if (lower.contains("character") || lower.contains("mara")) {
            return "I see you're working on character development! Mara Chen sounds like a compelling protagonist. " +
                   "Some suggestions:\n" +
                   "- Consider adding a personal flaw that creates internal conflict\n" +
                   "- Her past as a detective could inform her investigation methods\n" +
                   "- Think about her relationship with the mysterious Stranger";
        }

        if (lower.contains("scene") || lower.contains("plot")) {
            return "For your scene, consider these elements:\n" +
                   "- **Setting**: Use sensory details (sounds, smells, textures)\n" +
                   "- **Tension**: What's at stake for the characters?\n" +
                   "- **Dialogue**: Keep it natural, with subtext\n" +
                   "- **Pacing**: Vary sentence length for rhythm";
        }

        if (lower.contains("help")) {
            return "I can assist you with:\n" +
                   "- Developing characters and their motivations\n" +
                   "- Crafting compelling dialogue\n" +
                   "- Building your world and setting\n" +
                   "- Plotting story arcs\n" +
                   "- Providing writing prompts and ideas\n\n" +
                   "What would you like to work on?";
        }

        if (lower.contains("write") || lower.contains("draft")) {
            return "I'd be happy to help you draft content! Here's a quick writing prompt:\n\n" +
                   "*The rain had stopped, but Mara knew the real storm was just beginning. " +
                   "She checked her holster, took a deep breath, and pushed open the warehouse door...*\n\n" +
                   "Feel free to modify this or ask for alternatives!";
        }

        return "That's an interesting thought! As your writing assistant, I'm here to help develop your story. " +
               "I can see you're working on a noir-style narrative set in Neo-Seattle. " +
               "Would you like me to help with character development, plot structure, or scene descriptions?";
    }
}
