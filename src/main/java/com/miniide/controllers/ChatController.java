package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.AgentTurnGate;
import com.miniide.MemoryService;
import com.miniide.IssueMemoryService;
import com.miniide.ProjectContext;
import com.miniide.models.Agent;
import com.miniide.models.Comment;
import com.miniide.models.Issue;
import com.miniide.models.MemoryItem;
import com.miniide.providers.ProviderChatService;
import com.miniide.settings.SettingsService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
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
    private final IssueMemoryService issueService;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public ChatController(ProjectContext projectContext,
                          SettingsService settingsService, ProviderChatService providerChatService,
                          MemoryService memoryService, IssueMemoryService issueService, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.settingsService = settingsService;
        this.providerChatService = providerChatService;
        this.memoryService = memoryService;
        this.issueService = issueService;
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
            boolean skipToolCatalog = json.has("skipToolCatalog") && json.get("skipToolCatalog").asBoolean();
            String levelParam = json.has("level") ? json.get("level").asText() : null;
            boolean includeArchived = json.has("includeArchived") && json.get("includeArchived").asBoolean();
            boolean includeExpired = json.has("includeExpired") && json.get("includeExpired").asBoolean();
            boolean needMore = message != null && message.toUpperCase().contains("NEED_MORE_CONTEXT");
            boolean requestMore = reroll || needMore || "more".equalsIgnoreCase(levelParam);

            MemoryService.MemoryResult memoryResult = null;
            MemoryItem memoryItem = null;
            boolean memoryExcluded = false;
            if (memoryId != null && !memoryId.isBlank()) {
                memoryItem = memoryService.getMemoryItem(memoryId);
                if (memoryItem != null && isStateExcluded(memoryItem, includeArchived, includeExpired)) {
                    memoryExcluded = true;
                } else {
                    memoryResult = requestMore
                        ? memoryService.getMemoryAtNextLevel(memoryId)
                        : memoryService.getMemoryAtAutoLevel(memoryId);
                }
            }

            if (agentId != null && !agentId.isBlank()) {
                if (!agentsUnlocked()) {
                    ctx.status(403).json(Map.of("error", "Project preparation incomplete. Agents are locked."));
                    return;
                }
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
                String prompt = message;
                if (skipToolCatalog) {
                    logger.info("Skipping prompt tool catalog (skipToolCatalog=true).");
                } else {
                    String toolCatalog = projectContext.promptTools() != null
                        ? projectContext.promptTools().buildCatalogPrompt()
                        : "";
                    if (toolCatalog != null && !toolCatalog.isBlank()) {
                        prompt = toolCatalog + "\n\n" + (prompt != null ? prompt : "");
                    }
                }
                String grounding = buildEarlyGroundingHeader();
                if (grounding != null && !grounding.isBlank()) {
                    prompt = grounding + "\n\n" + (prompt != null ? prompt : "");
                }
                final String finalPrompt = prompt;
                String response = AGENT_TURN_GATE.run(() -> providerChatService.chat(providerName, keyRef, agentEndpoint, finalPrompt));
                response = stripThinkingTags(response);
                ctx.json(buildResponse(response, memoryResult, memoryId, requestMore, memoryItem, memoryExcluded));
                return;
            }

            String response = generateStubResponse(message);

            ctx.json(buildResponse(response, memoryResult, memoryId, requestMore, memoryItem, memoryExcluded));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private boolean agentsUnlocked() {
        return projectContext != null
            && projectContext.preparation() != null
            && projectContext.preparation().areAgentsUnlocked();
    }

    private Map<String, Object> buildResponse(String content, MemoryService.MemoryResult memoryResult,
                                              String memoryId, boolean requestMore,
                                              MemoryItem memoryItem, boolean memoryExcluded) {
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

        if (memoryItem != null) {
            body.put("memoryState", memoryItem.getState());
        }
        if (memoryExcluded) {
            body.put("memoryExcluded", true);
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

    private String stripThinkingTags(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String cleaned = content;
        cleaned = cleaned.replaceAll("(?is)<thinking>.*?</thinking>", "").trim();
        cleaned = cleaned.replaceAll("(?is)<think>.*?</think>", "").trim();
        cleaned = cleaned.replaceAll("(?is)\\[thinking\\].*?\\[/thinking\\]", "").trim();
        cleaned = cleaned.replaceAll("(?is)\\[think\\].*?\\[/think\\]", "").trim();
        cleaned = cleaned.replaceAll("(?is)\\[thought\\].*?\\[/thought\\]", "").trim();
        cleaned = stripOrphanClosingThink(cleaned, "</thinking>");
        cleaned = stripOrphanClosingThink(cleaned, "</think>");
        cleaned = stripOrphanClosingThink(cleaned, "[/thinking]");
        cleaned = stripOrphanClosingThink(cleaned, "[/think]");
        cleaned = stripOrphanClosingThink(cleaned, "[/thought]");
        return cleaned;
    }

    private String stripOrphanClosingThink(String content, String closingTag) {
        if (content == null || content.isBlank() || closingTag == null || closingTag.isBlank()) {
            return content;
        }
        String lower = content.toLowerCase();
        String lowerTag = closingTag.toLowerCase();
        int idx = lower.indexOf(lowerTag);
        if (idx == -1) {
            return content;
        }
        String after = content.substring(idx + closingTag.length());
        return after.trim();
    }

    private boolean isStateExcluded(MemoryItem item, boolean includeArchived, boolean includeExpired) {
        if (item == null) return true;
        String state = item.getState();
        if ("expired".equalsIgnoreCase(state)) {
            return !includeExpired;
        }
        if ("archived".equalsIgnoreCase(state)) {
            return !includeArchived;
        }
        return false;
    }

    private String buildEarlyGroundingHeader() {
        if (issueService == null) {
            return "";
        }
        List<Issue> agreed = issueService.listIssuesByEpistemicStatus("agreed");
        if (agreed == null || agreed.isEmpty()) {
            return "";
        }
        int limit = Math.min(agreed.size(), 10);
        StringBuilder builder = new StringBuilder();
        builder.append("Early Grounding (R1 + R3, epistemicStatus >= agreed):\n");
        for (int i = 0; i < limit; i++) {
            Issue issue = agreed.get(i);
            if (issue == null) {
                continue;
            }
            String title = safe(issue.getTitle());
            String trace = issue.getSemanticTrace();
            if (trace == null || trace.isBlank()) {
                trace = buildSemanticTrace(issue);
            }
            String summary = issue.getCompressedSummary();
            if (summary == null || summary.isBlank()) {
                summary = buildCompressedSummary(issue);
            }
            builder.append("- #").append(issue.getId());
            if (!title.isBlank()) {
                builder.append(" ").append(truncate(title, 120));
            }
            builder.append("\n  R1: ").append(truncate(trace, 220));
            builder.append("\n  R3: ").append(truncate(summary, 240)).append("\n");
        }
        return builder.toString().trim();
    }

    private String buildCompressedSummary(Issue issue) {
        if (issue == null) {
            return "";
        }
        String title = safe(issue.getTitle());
        String bodySnippet = truncate(safe(issue.getBody()), 220);
        StringBuilder summary = new StringBuilder();
        if (!title.isBlank()) {
            summary.append(title);
        }
        if (!bodySnippet.isBlank()) {
            if (summary.length() > 0) summary.append(" - ");
            summary.append(bodySnippet);
        }
        List<Comment> comments = issue.getComments();
        if (comments != null && !comments.isEmpty()) {
            String first = truncate(safe(comments.get(0).getBody()), 180);
            String last = truncate(safe(comments.get(comments.size() - 1).getBody()), 180);
            if (!first.isBlank()) {
                summary.append(" | First: ").append(first);
            }
            if (!last.isBlank() && !last.equals(first)) {
                summary.append(" | Last: ").append(last);
            }
        }
        return summary.toString().trim();
    }

    private String buildSemanticTrace(Issue issue) {
        if (issue == null) {
            return "";
        }
        String title = safe(issue.getTitle());
        String base = safe(issue.getResolutionSummary());
        if (base.isBlank()) {
            base = truncate(safe(issue.getBody()), 200);
        }
        if (base.isBlank()) {
            base = truncate(title, 200);
        }
        if (title.isBlank()) {
            return base;
        }
        return (title + ": " + base).trim();
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
