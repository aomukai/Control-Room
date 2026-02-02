package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.AgentTurnGate;
import com.miniide.MemoryService;
import com.miniide.IssueMemoryService;
import com.miniide.ProjectContext;
import com.miniide.TelemetryStore;
import com.miniide.models.Agent;
import com.miniide.models.Comment;
import com.miniide.models.Issue;
import com.miniide.models.MemoryItem;
import com.miniide.providers.ProviderChatService;
import com.miniide.prompt.PromptJsonValidator;
import com.miniide.prompt.PromptValidationResult;
import com.miniide.settings.SettingsService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.Instant;
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
        app.post("/api/ai/chief/route", this::chiefRoute);
    }

    private void aiChat(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String message = json.has("message") ? json.get("message").asText() : "";
            String agentId = json.has("agentId") ? json.get("agentId").asText() : null;
            String expectSchema = json.has("expectSchema") ? json.get("expectSchema").asText(null) : null;
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
                if (projectContext != null && projectContext.issueInterest() != null) {
                    projectContext.issueInterest().recordAgentActivation(agentId);
                }
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
                String response = runWithValidation(providerName, keyRef, agentEndpoint, finalPrompt, expectSchema);
                if (projectContext != null && projectContext.telemetry() != null) {
                    long tokensIn = TelemetryStore.estimateTokens(finalPrompt);
                    long tokensOut = TelemetryStore.estimateTokens(response);
                    projectContext.telemetry().recordTokens(agentId, tokensIn, tokensOut);
                }
                ctx.json(buildResponse(response, memoryResult, memoryId, requestMore, memoryItem, memoryExcluded));
                return;
            }

            String response = generateStubResponse(message);

            ctx.json(buildResponse(response, memoryResult, memoryId, requestMore, memoryItem, memoryExcluded));
        } catch (Exception e) {
            if (projectContext != null && projectContext.telemetry() != null) {
                String agentId = null;
                try {
                    JsonNode json = objectMapper.readTree(ctx.body());
                    agentId = json.has("agentId") ? json.get("agentId").asText(null) : null;
                } catch (Exception ignored) {
                }
                projectContext.telemetry().recordError(agentId);
            }
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private String runWithValidation(String providerName, String apiKey, com.miniide.models.AgentEndpointConfig agentEndpoint,
                                     String prompt, String expectSchema) {
        final String finalPrompt = prompt;
        if (expectSchema == null || expectSchema.isBlank()) {
            String response = callAgentWithGate(providerName, apiKey, agentEndpoint, finalPrompt);
            return stripThinkingTags(response);
        }

        int maxAttempts = 3;
        String currentPrompt = finalPrompt;
        PromptValidationResult lastResult = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            final String promptToSend = currentPrompt;
            String response = callAgentWithGate(providerName, apiKey, agentEndpoint, promptToSend);
            response = stripThinkingTags(response);
            lastResult = validateBySchema(expectSchema, response);
            if (lastResult.isValid()) {
                return response;
            }
            if (attempt < maxAttempts) {
                currentPrompt = currentPrompt + "\n\nYour previous response was invalid. Return ONLY valid JSON. No prose, no markdown, no extra text.";
            }
        }

        String errorSummary = lastResult != null && !lastResult.getErrors().isEmpty()
            ? String.join(", ", lastResult.getErrors())
            : "invalid-json";
        return "STOP_HOOK: error\nValidation failed: " + errorSummary;
    }

    private String callAgentWithGate(String providerName, String apiKey,
                                     com.miniide.models.AgentEndpointConfig agentEndpoint,
                                     String prompt) {
        try {
            return AGENT_TURN_GATE.run(() -> providerChatService.chat(providerName, apiKey, agentEndpoint, prompt));
        } catch (Exception e) {
            throw new RuntimeException("Agent chat failed", e);
        }
    }

    private PromptValidationResult validateBySchema(String expectSchema, String response) {
        if ("task_packet".equalsIgnoreCase(expectSchema)) {
            return PromptJsonValidator.validateTaskPacket(response);
        }
        if ("receipt".equalsIgnoreCase(expectSchema)) {
            return PromptJsonValidator.validateReceipt(response);
        }
        return PromptValidationResult.of(List.of("schema:unknown"));
    }

    private void chiefRoute(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String message = json.has("message") ? json.get("message").asText() : "";
            String issueId = json.has("issueId") ? json.get("issueId").asText(null) : null;
            String parentPacketId = json.has("parentPacketId") ? json.get("parentPacketId").asText(null) : null;
            String clarificationChoice = json.has("clarificationChoice") ? json.get("clarificationChoice").asText(null) : null;
            boolean skipToolCatalog = json.has("skipToolCatalog") && json.get("skipToolCatalog").asBoolean();

            if (issueId == null || issueId.isBlank()) {
                ctx.status(400).json(Map.of("error", "issueId required"));
                return;
            }
            if (message == null || message.isBlank()) {
                ctx.status(400).json(Map.of("error", "message required"));
                return;
            }
            if (!agentsUnlocked()) {
                ctx.status(403).json(Map.of("error", "Project preparation incomplete. Agents are locked."));
                return;
            }
            Agent chief = resolveChiefOfStaff();
            if (chief == null) {
                ctx.status(404).json(Map.of("error", "Chief of Staff agent not found"));
                return;
            }
            var endpoint = projectContext.agentEndpoints().getEndpoint(chief.getId());
            if (endpoint == null) {
                endpoint = chief.getEndpoint();
            }
            if (endpoint == null || endpoint.getProvider() == null || endpoint.getProvider().isBlank()) {
                ctx.status(400).json(Map.of("error", "Chief agent endpoint not configured"));
                return;
            }
            if (endpoint.getModel() == null || endpoint.getModel().isBlank()) {
                ctx.status(400).json(Map.of("error", "Chief agent model not configured"));
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

            String prompt = buildChiefRouterPrompt(message, issueId, parentPacketId, clarificationChoice);
            if (!skipToolCatalog) {
                String toolCatalog = projectContext.promptTools() != null
                    ? projectContext.promptTools().buildCatalogPrompt()
                    : "";
                if (toolCatalog != null && !toolCatalog.isBlank()) {
                    prompt = toolCatalog + "\n\n" + prompt;
                }
            }
            String grounding = buildEarlyGroundingHeader();
            if (grounding != null && !grounding.isBlank()) {
                prompt = grounding + "\n\n" + prompt;
            }
            String response = runWithValidation(provider, apiKey, endpoint, prompt, "task_packet");
            if (response != null && response.startsWith("STOP_HOOK")) {
                JsonNode fallback = buildFallbackPacket(message, issueId, parentPacketId, clarificationChoice);
                String packetId = fallback.path("packet_id").asText("");
                if (projectContext != null && projectContext.audit() != null) {
                    projectContext.audit().writePacket(issueId, packetId, objectMapper.writeValueAsString(fallback));
                }
                maybeCreateChiefRouterWarning(issueId, chief, endpoint, response);
                ctx.json(Map.of("packet", fallback, "fallback", true, "error", "Chief router validation failed", "content", response));
                return;
            }
            JsonNode packetNode;
            try {
                packetNode = objectMapper.readTree(response);
            } catch (Exception parseError) {
                JsonNode fallback = buildFallbackPacket(message, issueId, parentPacketId, clarificationChoice);
                String packetId = fallback.path("packet_id").asText("");
                if (projectContext != null && projectContext.audit() != null) {
                    projectContext.audit().writePacket(issueId, packetId, objectMapper.writeValueAsString(fallback));
                }
                maybeCreateChiefRouterWarning(issueId, chief, endpoint, response);
                ctx.json(Map.of("packet", fallback, "fallback", true, "error", "Chief router returned invalid JSON", "content", response));
                return;
            }
            if (packetNode != null && packetNode.isObject()) {
                var obj = (com.fasterxml.jackson.databind.node.ObjectNode) packetNode;
                String currentIssue = obj.path("parent_issue_id").asText("");
                if (!issueId.equals(currentIssue)) {
                    obj.put("parent_issue_id", issueId);
                }
                if (parentPacketId != null && !parentPacketId.isBlank()) {
                    String currentParent = obj.path("parent_packet_id").asText("");
                    if (!parentPacketId.equals(currentParent)) {
                        obj.put("parent_packet_id", parentPacketId);
                    }
                }
                ensureRequiredPacketShape(obj, message, clarificationChoice);
                response = objectMapper.writeValueAsString(obj);
                packetNode = obj;
            }
            PromptValidationResult validation = PromptJsonValidator.validateTaskPacket(response);
            if (!validation.isValid()) {
                JsonNode fallback = buildFallbackPacket(message, issueId, parentPacketId, clarificationChoice);
                String packetId = fallback.path("packet_id").asText("");
                if (projectContext != null && projectContext.audit() != null) {
                    projectContext.audit().writePacket(issueId, packetId, objectMapper.writeValueAsString(fallback));
                }
                maybeCreateChiefRouterWarning(issueId, chief, endpoint, response);
                ctx.json(Map.of("packet", fallback, "fallback", true, "error", "Chief router packet validation failed",
                    "details", validation.getErrors(), "content", response));
                return;
            }

            String packetId = packetNode.path("packet_id").asText("");
            if (packetId == null || packetId.isBlank()) {
                ctx.status(422).json(Map.of("error", "Packet packet_id missing"));
                return;
            }

            if (projectContext != null && projectContext.audit() != null) {
                projectContext.audit().writePacket(issueId, packetId, response);
            }

            ctx.json(Map.of("packet", packetNode, "fallback", false));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private String buildChiefRouterPrompt(String message, String issueId, String parentPacketId, String clarificationChoice) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are the Chief Router. Convert the user request into a Task Packet JSON (v0.1).\n");
        builder.append("Return ONLY valid JSON. No prose, no markdown, no code fences.\n");
        builder.append("The JSON must satisfy the task_packet schema and required fields.\n");
        builder.append("Every required field must be present even if unknown; use empty strings only where allowed.\n");
        builder.append("Do not omit scope, inputs, constraints, handoff, or output_contract. Use empty objects/arrays as needed.\n");
        builder.append("Allowed intent values: clarify, plan_scene, beat_architect, continuity_check, write_beat, critique_scene, edit_scene, summarize_context, finalize, other.\n");
        builder.append("If clarification is needed, use intent=clarify and include a clarification object with question + 2-4 choices.\n");
        builder.append("If a clarification choice is provided, DO NOT use intent=clarify. Use it to produce the next task packet.\n");
        builder.append("Use parent_issue_id exactly as provided.\n");
        if (parentPacketId != null && !parentPacketId.isBlank()) {
            builder.append("Use parent_packet_id exactly as provided.\n");
        }
        builder.append("Use target.scene_ref from the user request if possible; otherwise use \"unknown\".\n");
        builder.append("Use target.resolution_method \"outline_order\" unless a specific file/path is required.\n");
        builder.append("Set output_contract.output_mode to json_only for clarify packets; otherwise choose the best fit.\n");
        builder.append("output_contract.output_mode must be one of: patch, artifact, json_only.\n");
        builder.append("output_contract.expected_artifacts must be an array (can be empty).\n");
        builder.append("timestamp must be ISO-8601 UTC. Use this timestamp: ").append(Instant.now().toString()).append("\n");
        builder.append("requested_by.agent_id must be \"user\".\n");
        builder.append("Return a single JSON object only, with no trailing text.\n");
        builder.append("Template (fill values, keep structure):\n");
        builder.append("{");
        builder.append("\"packet_id\":\"pkt_...\",");
        builder.append("\"parent_issue_id\":\"...\",");
        builder.append("\"parent_packet_id\":\"\",");
        builder.append("\"intent\":\"clarify|plan_scene|...\",");
        builder.append("\"target\":{\"scene_ref\":\"...\",\"resolution_method\":\"outline_order\",\"canonical_path\":\"\"},");
        builder.append("\"scope\":{\"allowed\":[],\"blocked\":[]},");
        builder.append("\"inputs\":{\"files\":[],\"canon\":\"\",\"context\":\"\"},");
        builder.append("\"constraints\":{\"rules\":[],\"pov\":\"\",\"tense\":\"\",\"style\":\"\",\"forbidden\":[]},");
        builder.append("\"output_contract\":{\"output_mode\":\"json_only\",\"expected_artifacts\":[],\"stop_conditions\":[]},");
        builder.append("\"handoff\":{\"required_next_step\":\"\",\"report_to_chief_only\":false},");
        builder.append("\"timestamp\":\"").append(Instant.now().toString()).append("\",");
        builder.append("\"requested_by\":{\"agent_id\":\"user\",\"reason\":\"\"},");
        builder.append("\"clarification\":{\"question\":\"\",\"choices\":[]}");
        builder.append("}\n");
        builder.append("\n");
        builder.append("parent_issue_id: ").append(issueId).append("\n");
        if (parentPacketId != null && !parentPacketId.isBlank()) {
            builder.append("parent_packet_id: ").append(parentPacketId).append("\n");
        }
        builder.append("user_message: ").append(message).append("\n");
        if (clarificationChoice != null && !clarificationChoice.isBlank()) {
            builder.append("clarification_choice: ").append(clarificationChoice).append("\n");
        }
        return builder.toString().trim();
    }

    private void ensureRequiredPacketShape(com.fasterxml.jackson.databind.node.ObjectNode obj, String message, String clarificationChoice) {
        if (obj == null) {
            return;
        }
        var om = objectMapper;
        if (!obj.has("target") || !obj.get("target").isObject()) {
            var target = om.createObjectNode();
            target.put("scene_ref", "unknown");
            target.put("resolution_method", "outline_order");
            target.put("canonical_path", "");
            obj.set("target", target);
        } else {
            var target = (com.fasterxml.jackson.databind.node.ObjectNode) obj.get("target");
            if (!target.has("scene_ref")) target.put("scene_ref", "unknown");
            if (!target.has("resolution_method")) target.put("resolution_method", "outline_order");
            if (!target.has("canonical_path")) target.put("canonical_path", "");
        }
        if (!obj.has("scope") || !obj.get("scope").isObject()) {
            var scope = om.createObjectNode();
            scope.set("allowed", om.createArrayNode());
            scope.set("blocked", om.createArrayNode());
            obj.set("scope", scope);
        }
        if (!obj.has("inputs") || !obj.get("inputs").isObject()) {
            var inputs = om.createObjectNode();
            inputs.set("files", om.createArrayNode());
            inputs.put("canon", "");
            inputs.put("context", "");
            obj.set("inputs", inputs);
        }
        if (!obj.has("constraints") || !obj.get("constraints").isObject()) {
            var constraints = om.createObjectNode();
            constraints.set("rules", om.createArrayNode());
            constraints.put("pov", "");
            constraints.put("tense", "");
            constraints.put("style", "");
            constraints.set("forbidden", om.createArrayNode());
            obj.set("constraints", constraints);
        }
        if (!obj.has("output_contract") || !obj.get("output_contract").isObject()) {
            var output = om.createObjectNode();
            output.put("output_mode", "json_only");
            output.set("expected_artifacts", om.createArrayNode());
            output.set("stop_conditions", om.createArrayNode());
            obj.set("output_contract", output);
        } else {
            var output = (com.fasterxml.jackson.databind.node.ObjectNode) obj.get("output_contract");
            if (!output.has("output_mode")) output.put("output_mode", "json_only");
            if (!output.has("expected_artifacts")) output.set("expected_artifacts", om.createArrayNode());
            if (!output.has("stop_conditions")) output.set("stop_conditions", om.createArrayNode());
        }
        if (!obj.has("handoff") || !obj.get("handoff").isObject()) {
            var handoff = om.createObjectNode();
            handoff.put("required_next_step", "");
            handoff.put("report_to_chief_only", false);
            obj.set("handoff", handoff);
        }
        if (!obj.has("requested_by") || !obj.get("requested_by").isObject()) {
            var requestedBy = om.createObjectNode();
            requestedBy.put("agent_id", "user");
            requestedBy.put("reason", "");
            obj.set("requested_by", requestedBy);
        }
        if (!obj.has("timestamp")) {
            obj.put("timestamp", Instant.now().toString());
        }
        if (!obj.has("intent")) {
            obj.put("intent", "clarify");
        }
        boolean shouldForceClarify = shouldForceClarifyForScene(message, clarificationChoice);
        if (shouldForceClarify) {
            obj.put("intent", "clarify");
        }
        if (!obj.has("packet_id")) {
            obj.put("packet_id", "pkt_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        if ("clarify".equalsIgnoreCase(obj.path("intent").asText())) {
            if (!obj.has("clarification") || !obj.get("clarification").isObject()) {
                var clarification = om.createObjectNode();
                clarification.put("question", "Which scene resolution should we use?");
                var choices = om.createArrayNode();
                choices.add("Outline order (default)");
                choices.add("By filename/path");
                choices.add("Specify a different scene reference");
                clarification.set("choices", choices);
                obj.set("clarification", clarification);
            } else {
                var clarification = (com.fasterxml.jackson.databind.node.ObjectNode) obj.get("clarification");
                if (!clarification.has("question") || clarification.path("question").asText("").isBlank()) {
                    clarification.put("question", "Which scene resolution should we use?");
                }
                if (!clarification.has("choices") || !clarification.get("choices").isArray()
                    || clarification.get("choices").size() < 2) {
                    var choices = om.createArrayNode();
                    choices.add("Outline order (default)");
                    choices.add("By filename/path");
                    choices.add("Specify a different scene reference");
                    clarification.set("choices", choices);
                }
            }
        }
    }

    private boolean shouldForceClarifyForScene(String message, String clarificationChoice) {
        if (clarificationChoice != null && !clarificationChoice.isBlank()) {
            return false;
        }
        if (message == null) {
            return false;
        }
        String trimmed = message.trim().toLowerCase();
        if (trimmed.isBlank()) {
            return false;
        }
        if (trimmed.matches(".*\\bscene\\s*#?\\s*\\d+\\b.*")) {
            return true;
        }
        return trimmed.matches(".*\\bscene\\s+\\w+\\b.*");
    }

    private JsonNode buildFallbackPacket(String message, String issueId, String parentPacketId, String clarificationChoice) {
        var om = objectMapper;
        var obj = om.createObjectNode();
        obj.put("packet_id", "pkt_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        obj.put("parent_issue_id", issueId != null ? issueId : "");
        obj.put("parent_packet_id", parentPacketId != null ? parentPacketId : "");
        String intent = shouldForceClarifyForScene(message, clarificationChoice) ? "clarify" : "plan_scene";
        if (clarificationChoice != null && !clarificationChoice.isBlank()) {
            intent = "plan_scene";
        }
        obj.put("intent", intent);
        var target = om.createObjectNode();
        target.put("scene_ref", "unknown");
        target.put("resolution_method", "outline_order");
        target.put("canonical_path", "");
        obj.set("target", target);
        var scope = om.createObjectNode();
        scope.set("allowed", om.createArrayNode());
        scope.set("blocked", om.createArrayNode());
        obj.set("scope", scope);
        var inputs = om.createObjectNode();
        inputs.set("files", om.createArrayNode());
        inputs.put("canon", "");
        inputs.put("context", "");
        obj.set("inputs", inputs);
        var constraints = om.createObjectNode();
        constraints.set("rules", om.createArrayNode());
        constraints.put("pov", "");
        constraints.put("tense", "");
        constraints.put("style", "");
        constraints.set("forbidden", om.createArrayNode());
        obj.set("constraints", constraints);
        var output = om.createObjectNode();
        output.put("output_mode", "json_only");
        output.set("expected_artifacts", om.createArrayNode());
        output.set("stop_conditions", om.createArrayNode());
        obj.set("output_contract", output);
        var handoff = om.createObjectNode();
        handoff.put("required_next_step", intent);
        handoff.put("report_to_chief_only", false);
        obj.set("handoff", handoff);
        obj.put("timestamp", Instant.now().toString());
        var requestedBy = om.createObjectNode();
        requestedBy.put("agent_id", "user");
        requestedBy.put("reason", "");
        obj.set("requested_by", requestedBy);
        if ("clarify".equals(intent)) {
            var clarification = om.createObjectNode();
            clarification.put("question", "Which scene resolution should we use?");
            var choices = om.createArrayNode();
            choices.add("Outline order (default)");
            choices.add("By filename/path");
            choices.add("Specify a different scene reference");
            clarification.set("choices", choices);
            obj.set("clarification", clarification);
        }
        return obj;
    }

    private void maybeCreateChiefRouterWarning(String issueId, Agent chief, com.miniide.models.AgentEndpointConfig endpoint,
                                               String response) {
        if (issueService == null || issueId == null || issueId.isBlank()) {
            return;
        }
        String tag = "chief-router-warning";
        long now = System.currentTimeMillis();
        long dayMs = 24L * 60 * 60 * 1000;
        List<Issue> existing = issueService.listIssuesByTag(tag);
        for (Issue issue : existing) {
            if (issue == null) {
                continue;
            }
            String title = safe(issue.getTitle()).toLowerCase();
            if (!title.contains(issueId.toLowerCase())) {
                continue;
            }
            if (now - issue.getCreatedAt() < dayMs) {
                return;
            }
        }
        String model = endpoint != null ? safe(endpoint.getModel()) : "";
        String provider = endpoint != null ? safe(endpoint.getProvider()) : "";
        String title = "Chief router struggling for issue " + issueId;
        StringBuilder body = new StringBuilder();
        body.append("Chief router failed to emit a valid task packet and fallback was used.\n\n");
        body.append("**Issue ID**: ").append(issueId).append("\n");
        if (!provider.isBlank() || !model.isBlank()) {
            body.append("**Endpoint**: ").append(provider);
            if (!model.isBlank()) {
                body.append(" / ").append(model);
            }
            body.append("\n");
        }
        if (response != null && !response.isBlank()) {
            String snippet = response.length() > 400 ? response.substring(0, 400) + "..." : response;
            body.append("\n**Model output**:\n").append(snippet).append("\n");
        }
        String assignedTo = chief != null ? chief.getName() : "system";
        issueService.createIssue(
            title,
            body.toString(),
            "system",
            assignedTo,
            List.of("warning", "chief", tag),
            "normal"
        );
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

    private Agent resolveChiefOfStaff() {
        if (projectContext == null || projectContext.agents() == null) {
            return null;
        }
        List<Agent> agents = projectContext.agents().listEnabledAgents();
        for (Agent agent : agents) {
            if (agent == null) {
                continue;
            }
            String role = agent.getRole() != null ? agent.getRole().trim().toLowerCase() : "";
            if ("assistant".equals(role) || "chief of staff".equals(role) || Boolean.TRUE.equals(agent.getCanBeTeamLead())) {
                return agent;
            }
        }
        return null;
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
