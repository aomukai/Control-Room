package com.miniide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.Agent;
import com.miniide.models.AgentEndpointConfig;
import com.miniide.models.Comment;
import com.miniide.models.Issue;
import com.miniide.providers.ProviderChatService;
import com.miniide.settings.SettingsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IssueCompressionService {
    private final ProjectContext projectContext;
    private final SettingsService settingsService;
    private final ProviderChatService providerChatService;
    private final ObjectMapper objectMapper;
    private final AppLogger logger = AppLogger.get();

    public IssueCompressionService(ProjectContext projectContext, SettingsService settingsService,
                                   ProviderChatService providerChatService, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.settingsService = settingsService;
        this.providerChatService = providerChatService;
        this.objectMapper = objectMapper;
    }

    public CompressionResult compressIssue(Issue issue, String agentId) {
        if (issue == null) {
            throw new IllegalArgumentException("Issue is required");
        }
        AgentEndpointConfig endpoint = resolveEndpoint(agentId);
        if (endpoint == null || isBlank(endpoint.getProvider()) || isBlank(endpoint.getModel())) {
            throw new IllegalStateException("No configured agent endpoint for compression");
        }
        String provider = endpoint.getProvider().trim().toLowerCase(Locale.ROOT);
        String apiKey = resolveApiKey(provider, endpoint.getApiKeyRef());
        String prompt = buildPrompt(issue);
        String response;
        try {
            response = providerChatService.chat(provider, apiKey, endpoint, prompt);
        } catch (Exception e) {
            throw new IllegalStateException("Compression provider call failed", e);
        }
        String cleaned = stripThinkingTags(response);
        CompressionResult parsed = parseCompression(cleaned);
        if (parsed == null || parsed.isEmpty()) {
            throw new IllegalStateException("Compression response did not contain valid JSON");
        }
        return parsed;
    }

    private AgentEndpointConfig resolveEndpoint(String agentId) {
        if (projectContext == null) {
            return null;
        }
        Agent agent = null;
        if (agentId != null && !agentId.isBlank()) {
            agent = projectContext.agents().getAgent(agentId);
        }
        if (agent == null) {
            agent = findAssistantAgent();
        }
        if (agent == null) {
            return null;
        }
        AgentEndpointConfig endpoint = projectContext.agentEndpoints().getEndpoint(agent.getId());
        if (endpoint == null) {
            endpoint = agent.getEndpoint();
        }
        return endpoint;
    }

    private Agent findAssistantAgent() {
        if (projectContext == null || projectContext.agents() == null) {
            return null;
        }
        var agents = projectContext.agents().listAllAgents();
        for (Agent agent : agents) {
            if (agent == null || agent.getRole() == null) {
                continue;
            }
            if ("assistant".equalsIgnoreCase(agent.getRole().trim())) {
                return agent;
            }
        }
        return agents.isEmpty() ? null : agents.get(0);
    }

    private String resolveApiKey(String provider, String keyRef) {
        if (settingsService == null) {
            return null;
        }
        if (requiresApiKey(provider)) {
            if (keyRef == null || keyRef.isBlank()) {
                throw new IllegalStateException("API key required for provider " + provider);
            }
            try {
                return settingsService.resolveKey(keyRef);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to resolve API key for provider " + provider, e);
            }
        }
        if (keyRef != null && !keyRef.isBlank()) {
            try {
                return settingsService.resolveKey(keyRef);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to resolve API key for provider " + provider, e);
            }
        }
        return null;
    }

    private boolean requiresApiKey(String provider) {
        if (provider == null) return false;
        switch (provider.toLowerCase(Locale.ROOT)) {
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

    private String buildPrompt(Issue issue) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are compressing an issue thread into long-term memory representations.\n\n");
        builder.append("This is a LOSSY compression task.\n");
        builder.append("Each lower level must REMOVE information, not rephrase it.\n\n");
        builder.append("Return ONLY valid JSON with exactly these keys:\n");
        builder.append("- level1\n");
        builder.append("- level2\n");
        builder.append("- level3\n\n");
        builder.append("No markdown. No extra keys. No commentary.\n\n");
        builder.append("Rules by level:\n\n");
        builder.append("LEVEL 1 -- Semantic Trace\n");
        builder.append("- ONE sentence only.\n");
        builder.append("- Capture the core fact or decision that must remain true.\n");
        builder.append("- Omit motivations, tone, personality, and explanations.\n\n");
        builder.append("LEVEL 2 -- Resolution Summary\n");
        builder.append("- 1-2 sentences.\n");
        builder.append("- State WHAT happened and the final outcome.\n");
        builder.append("- Include concrete entities (agent names, tools, models) if they matter.\n");
        builder.append("- Do not generalize.\n\n");
        builder.append("LEVEL 3 -- Compressed Summary\n");
        builder.append("- Short paragraph (2-4 sentences max).\n");
        builder.append("- This is the LAST memory before full evidence.\n");
        builder.append("- Preserve ALL concrete entities needed to reconstruct the event later\n");
        builder.append("  (names, providers, models, wiring/config facts).\n");
        builder.append("- Do NOT replace specifics with vague phrases like\n");
        builder.append("  \"relevant system\", \"set up\", or \"providing assistance\".\n");
        builder.append("- Exclude personality, marketing language, and generic assistant descriptions.\n\n");
        builder.append("Hard constraints:\n");
        builder.append("- Do NOT invent facts.\n");
        builder.append("- Do NOT paraphrase the same sentence across levels.\n");
        builder.append("- Prefer specific nouns over generic abstractions.\n\n");
        builder.append("Issue:\n");
        builder.append("ID: ").append(issue.getId()).append("\n");
        builder.append("Title: ").append(safe(issue.getTitle())).append("\n");
        builder.append("Tags: ");
        if (issue.getTags() != null && !issue.getTags().isEmpty()) {
            builder.append(String.join(", ", issue.getTags()));
        }
        builder.append("\n\n");
        builder.append("Body:\n").append(safe(issue.getBody())).append("\n\n");
        builder.append("Comments:\n");
        List<Comment> comments = issue.getComments() != null ? issue.getComments() : new ArrayList<>();
        if (!comments.isEmpty()) {
            int limit = Math.min(comments.size(), 12);
            for (int i = 0; i < limit; i++) {
                Comment comment = comments.get(i);
                builder.append("- ")
                    .append(safe(comment.getAuthor()))
                    .append(": ")
                    .append(safe(comment.getBody()))
                    .append("\n");
            }
        }
        return builder.toString();
    }

    private CompressionResult parseCompression(String response) {
        if (response == null) {
            return null;
        }
        String json = extractJson(response);
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String level1 = pick(node, "level1", "L1", "semanticTrace");
            String level2 = pick(node, "level2", "L2", "resolutionSummary");
            String level3 = pick(node, "level3", "L3", "compressedSummary");
            CompressionResult result = new CompressionResult();
            result.level1 = clean(level1);
            result.level2 = clean(level2);
            result.level3 = clean(level3);
            return result;
        } catch (Exception e) {
            logger.warn("Failed to parse compression JSON: " + e.getMessage());
            return null;
        }
    }

    private String pick(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            if (node.has(key) && node.get(key).isTextual()) {
                return node.get(key).asText();
            }
        }
        return null;
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1).trim();
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
        return cleaned.trim();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class CompressionResult {
        public String level1;
        public String level2;
        public String level3;

        public boolean isEmpty() {
            return (level1 == null || level1.isBlank())
                && (level2 == null || level2.isBlank())
                && (level3 == null || level3.isBlank());
        }
    }
}
