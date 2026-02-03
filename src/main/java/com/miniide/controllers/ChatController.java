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
import com.miniide.tools.ToolCall;
import com.miniide.tools.ToolCallParser;
import com.miniide.tools.ToolCallParseResult;
import com.miniide.tools.ToolExecutionContext;
import com.miniide.tools.ToolExecutionResult;
import com.miniide.tools.ToolExecutionService;
import com.miniide.tools.ToolArgSpec;
import com.miniide.tools.ToolSchema;
import com.miniide.tools.ToolSchemaRegistry;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final ToolExecutionService toolExecutionService;
    private final ToolCallParser toolCallParser;
    private static final int MAX_TOOL_STEPS = 3;
    private static final int MAX_TOOL_BYTES_PER_TURN = 6000;
    private static final int MAX_TOOL_BYTES_PER_STEP = 2000;
    private final ToolSchemaRegistry toolSchemaRegistry;

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
        this.toolExecutionService = new ToolExecutionService(projectContext, issueService, objectMapper);
        this.toolSchemaRegistry = buildToolSchemas();
        this.toolCallParser = new ToolCallParser(objectMapper, toolSchemaRegistry);
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.post("/api/ai/chat", this::aiChat);
        app.post("/api/ai/chief/route", this::chiefRoute);
        app.post("/api/ai/task/execute", this::executeTaskPacket);
        app.post("/api/ai/playbook/scene", this::runScenePlaybook);
    }

    private void aiChat(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String message = json.has("message") ? json.get("message").asText() : "";
            String agentId = json.has("agentId") ? json.get("agentId").asText() : null;
            String conferenceId = json.has("conferenceId") ? json.get("conferenceId").asText(null) : null;
            String taskId = json.has("taskId") ? json.get("taskId").asText(null) : null;
            String turnId = json.has("turnId") ? json.get("turnId").asText(null) : null;
            String expectSchema = json.has("expectSchema") ? json.get("expectSchema").asText(null) : null;
            String memoryId = json.has("memoryId") ? json.get("memoryId").asText(null) : null;
            boolean reroll = json.has("reroll") && json.get("reroll").asBoolean();
            boolean skipToolCatalog = json.has("skipToolCatalog") && json.get("skipToolCatalog").asBoolean();
            String levelParam = json.has("level") ? json.get("level").asText() : null;
            boolean includeArchived = json.has("includeArchived") && json.get("includeArchived").asBoolean();
            boolean includeExpired = json.has("includeExpired") && json.get("includeExpired").asBoolean();
            boolean needMore = message != null && message.toUpperCase().contains("NEED_MORE_CONTEXT");
            boolean requestMore = reroll || needMore || "more".equalsIgnoreCase(levelParam);
            ToolPolicy toolPolicy = null;
            try {
                toolPolicy = parseToolPolicy(json.get("toolPolicy"));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
                return;
            }

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
                ToolExecutionContext toolContext = new ToolExecutionContext(conferenceId, taskId, turnId, agentId);
                String response = runWithValidation(providerName, keyRef, agentEndpoint, finalPrompt, expectSchema, toolContext, toolPolicy);
            if (projectContext != null && projectContext.telemetry() != null) {
                long tokensIn = TelemetryStore.estimateTokens(finalPrompt);
                long tokensOut = TelemetryStore.estimateTokens(response);
                    if (conferenceId != null && !conferenceId.isBlank()) {
                        projectContext.telemetry().recordTokens(agentId, tokensIn, tokensOut, conferenceId);
                    } else {
                        projectContext.telemetry().recordTokens(agentId, tokensIn, tokensOut);
                    }
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
                                     String prompt, String expectSchema, ToolExecutionContext toolContext, ToolPolicy toolPolicy) {
        final String finalPrompt = prompt;
        if (expectSchema == null || expectSchema.isBlank()) {
            return runWithTools(providerName, apiKey, agentEndpoint, finalPrompt, toolContext, toolPolicy);
        }

        int maxAttempts = 3;
        String currentPrompt = finalPrompt;
        PromptValidationResult lastResult = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            final String promptToSend = currentPrompt;
            String response = runWithTools(providerName, apiKey, agentEndpoint, promptToSend, toolContext, toolPolicy);
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

    private ReceiptAttempt runReceiptWithValidation(String providerName, String apiKey,
                                                    com.miniide.models.AgentEndpointConfig agentEndpoint,
                                                    String prompt, ToolExecutionContext toolContext) {
        int maxAttempts = 3;
        String currentPrompt = prompt;
        PromptValidationResult lastResult = null;
        String lastResponse = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String response = runWithTools(providerName, apiKey, agentEndpoint, currentPrompt, toolContext, null);
            lastResponse = response;
            lastResult = PromptJsonValidator.validateReceipt(response);
            if (lastResult.isValid()) {
                return new ReceiptAttempt(true, attempt, response, lastResult);
            }
            if (attempt < maxAttempts) {
                currentPrompt = currentPrompt + "\n\nYour previous response was invalid. Return ONLY valid JSON. No prose, no markdown, no extra text.";
            }
        }
        return new ReceiptAttempt(false, maxAttempts, lastResponse, lastResult);
    }

    private String runWithTools(String providerName, String apiKey,
                                com.miniide.models.AgentEndpointConfig agentEndpoint,
                                String prompt, ToolExecutionContext toolContext, ToolPolicy toolPolicy) {
        String nonce = generateToolNonce();
        boolean requireToolCall = toolPolicy != null && toolPolicy.getRequireTool() != null
            ? toolPolicy.getRequireTool()
            : shouldRequireToolCall(prompt);
        boolean decisionMode = false;
        int maxToolSteps = toolPolicy != null && toolPolicy.getMaxToolSteps() != null
            ? Math.max(1, toolPolicy.getMaxToolSteps())
            : MAX_TOOL_STEPS;
        java.util.Set<String> allowedTools = toolPolicy != null ? toolPolicy.getAllowedTools() : null;
        String currentPrompt = appendToolProtocol(prompt, nonce, requireToolCall, toolPolicy, maxToolSteps);
        String response = null;
        int injectedBytes = 0;
        int toolCalls = 0;
        int maxTurns = maxToolSteps * 2 + 2;
        for (int turn = 0; turn < maxTurns; turn++) {
            com.fasterxml.jackson.databind.JsonNode responseFormat = null;
            if (decisionMode) {
                responseFormat = buildToolDecisionResponseFormat(nonce, allowedTools);
            } else if (requireToolCall) {
                responseFormat = buildToolCallResponseFormat(nonce, allowedTools);
            }
            response = callAgentWithGate(providerName, apiKey, agentEndpoint, currentPrompt, responseFormat);
            response = stripThinkingTags(response);

            if (decisionMode) {
                ToolDecisionParseResult decision = parseToolDecision(response, nonce);
                if (decision.getErrorCode() != null) {
                    return handleToolCallRejection(toolContext, decision.getErrorCode(), decision.getErrorDetail());
                }
                if (decision.isFinal()) {
                    String finalPrompt = appendFinalResponseProtocol(currentPrompt);
                    String finalResponse = callAgentWithGate(providerName, apiKey, agentEndpoint, finalPrompt, null);
                    finalResponse = stripThinkingTags(finalResponse);
                    if (looksLikeToolCallAttempt(finalResponse)) {
                        return handleToolCallRejection(toolContext, ToolCallParser.ERR_INVALID_FORMAT,
                            "tool call must be strict JSON");
                    }
                    return finalResponse;
                }
                if (toolCalls >= maxToolSteps) {
                    String forcedPrompt = appendFinalResponseProtocol(currentPrompt)
                        + "\nTool step limit reached. Respond now without any tool calls.";
                    String finalResponse = callAgentWithGate(providerName, apiKey, agentEndpoint, forcedPrompt, null);
                    finalResponse = stripThinkingTags(finalResponse);
                    if (looksLikeToolCallAttempt(finalResponse)) {
                        return handleToolCallRejection(toolContext, ToolCallParser.ERR_INVALID_FORMAT,
                            "tool call must be strict JSON");
                    }
                    return finalResponse;
                }
                ToolCall call = decision.getCall();
                if (call == null) {
                    return handleToolCallRejection(toolContext, ToolCallParser.ERR_INVALID_FORMAT, "decision missing tool");
                }
                if (allowedTools != null && !allowedTools.isEmpty() && !allowedTools.contains(call.getName())) {
                    return handleToolCallRejection(toolContext, ToolCallParser.ERR_UNKNOWN_TOOL, "tool not allowed");
                }
                ToolExecutionResult result = toolExecutionService.execute(call, toolContext);
                ToolAppendResult append = appendToolResult(currentPrompt, call, result, nonce, injectedBytes,
                    toolPolicy, maxToolSteps - toolCalls - 1);
                currentPrompt = append.prompt;
                injectedBytes = append.injectedBytes;
                if (append.exceededLimit) {
                    return handleToolCallRejection(toolContext, "tool_call_output_limit", "tool output limit exceeded");
                }
                toolCalls++;
                continue;
            }

            ToolCallParseResult parsed = toolCallParser.parseStrict(response, nonce);
            if (parsed.isToolCall()) {
                ToolCall call = parsed.getCall();
                if (allowedTools != null && !allowedTools.isEmpty() && !allowedTools.contains(call.getName())) {
                    return handleToolCallRejection(toolContext, ToolCallParser.ERR_UNKNOWN_TOOL, "tool not allowed");
                }
                ToolExecutionResult result = toolExecutionService.execute(call, toolContext);
                ToolAppendResult append = appendToolResult(currentPrompt, call, result, nonce, injectedBytes,
                    toolPolicy, maxToolSteps - toolCalls - 1);
                currentPrompt = append.prompt;
                injectedBytes = append.injectedBytes;
                requireToolCall = false;
                decisionMode = true;
                if (append.exceededLimit) {
                    return handleToolCallRejection(toolContext, "tool_call_output_limit", "tool output limit exceeded");
                }
                toolCalls++;
                continue;
            }
            if (parsed.getErrorCode() != null) {
                return handleToolCallRejection(toolContext, parsed.getErrorCode(), parsed.getErrorDetail());
            }
            if (requireToolCall) {
                return handleToolCallRejection(toolContext, ToolCallParser.ERR_INVALID_FORMAT, "tool call required");
            }
            if (looksLikeToolCallAttempt(response)) {
                return handleToolCallRejection(toolContext, ToolCallParser.ERR_INVALID_FORMAT, "tool call must be strict JSON");
            }
            return response;
        }
        return response != null ? response : "";
    }

    private ToolAppendResult appendToolResult(String prompt, ToolCall call, ToolExecutionResult result,
                                              String nonce, int injectedBytes, ToolPolicy toolPolicy,
                                              int remainingSteps) {
        StringBuilder builder = new StringBuilder();
        builder.append(prompt != null ? prompt : "");
        builder.append("\n\nTool call detected:\n");
        builder.append(call.getRaw() != null ? call.getRaw() : call.getName());
        builder.append("\n\nTool result:\n");
        String output = result != null ? result.getOutput() : null;
        int injectedThisStep = 0;
        if (output == null) {
            builder.append("Tool returned no output.");
        } else {
            String truncated = truncateToolOutput(output);
            builder.append(truncated);
            injectedThisStep = truncated.length();
        }
        if (result != null && !result.isOk()) {
            builder.append("\n\nTool error: ").append(result.getError() != null ? result.getError() : "unknown");
        }
        if (result != null && result.getReceiptId() != null) {
            builder.append("\n\nReceipt: ").append(result.getReceiptId());
        }
        builder.append("\n\nDecision rules: choose action=final if the user request is satisfied. ");
        builder.append("Choose action=tool only if another tool is required for the user request. ");
        builder.append("Do not call unrelated tools.\n");
        if (toolPolicy != null && toolPolicy.getAllowedTools() != null && !toolPolicy.getAllowedTools().isEmpty()) {
            builder.append("Allowed tools: ").append(String.join(", ", toolPolicy.getAllowedTools())).append("\n");
        }
        if (remainingSteps >= 0) {
            builder.append("Tool steps remaining: ").append(remainingSteps).append("\n");
        }
        builder.append("Next step (STRICT JSON ONLY): respond with a decision object. No prose, no markdown.\n");
        builder.append("- Another tool: {\"action\":\"tool\",\"tool\":\"<id>\",\"args\":{...},\"nonce\":\"")
            .append(nonce).append("\"}\n");
        builder.append("- Finish: {\"action\":\"final\",\"nonce\":\"").append(nonce).append("\"}");
        int total = injectedBytes + injectedThisStep;
        boolean exceeded = total > MAX_TOOL_BYTES_PER_TURN;
        return new ToolAppendResult(builder.toString(), total, exceeded);
    }

    private String appendFinalResponseProtocol(String prompt) {
        StringBuilder builder = new StringBuilder();
        builder.append(prompt != null ? prompt : "");
        builder.append("\n\nFINAL RESPONSE REQUIRED:\n");
        builder.append("Respond normally (no tools). Follow Evidence line rules exactly.");
        return builder.toString();
    }

    private String truncateToolOutput(String output) {
        if (output == null) return "";
        int max = MAX_TOOL_BYTES_PER_STEP;
        if (output.length() <= max) {
            return output;
        }
        String excerpt = output.substring(0, max);
        String hash = sha256(excerpt);
        return excerpt + "\n\n[truncated: " + output.length() + " chars, sha256=" + hash + "]";
    }

    private String handleToolCallRejection(ToolExecutionContext context, String code, String detail) {
        if (projectContext != null && projectContext.telemetry() != null
            && context != null && context.getSessionId() != null && context.getAgentId() != null) {
            projectContext.telemetry().recordRejection(context.getSessionId(), context.getAgentId(), code);
        }
        return buildToolCallRejection(code, detail);
    }

    private String buildToolCallRejection(String code, String detail) {
        StringBuilder builder = new StringBuilder();
        builder.append("STOP_HOOK: tool_call_rejected\n");
        builder.append("Reason: ").append(code != null ? code : "tool_call_invalid_format");
        if (detail != null && !detail.isBlank()) {
            builder.append(" (").append(detail).append(")");
        }
        return builder.toString();
    }

    private boolean looksLikeToolCallAttempt(String response) {
        if (response == null) return false;
        String trimmed = response.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("```")) {
            return true;
        }
        String lower = trimmed.toLowerCase();
        return lower.contains("file_locator(")
            || lower.contains("outline_analyzer(")
            || lower.contains("canon_checker(")
            || lower.contains("task_router(")
            || lower.contains("search_issues(")
            || lower.contains("\"tool\"")
            || lower.contains("'tool'");
    }

    private String appendToolProtocol(String prompt, String nonce, boolean requireToolCall,
                                      ToolPolicy toolPolicy, int maxToolSteps) {
        StringBuilder builder = new StringBuilder();
        builder.append(prompt != null ? prompt : "");
        builder.append("\n\nTool Call Protocol (STRICT JSON ONLY):\n");
        builder.append("If you need a tool, respond with ONLY a JSON object. No prose, no markdown, no code fences.\n");
        builder.append("Format: {\"tool\":\"<id>\",\"args\":{...},\"nonce\":\"").append(nonce).append("\"}\n");
        if (toolPolicy != null && toolPolicy.getAllowedTools() != null && !toolPolicy.getAllowedTools().isEmpty()) {
            builder.append("Allowed tools: ").append(String.join(", ", toolPolicy.getAllowedTools())).append("\n");
        }
        if (maxToolSteps > 0) {
            builder.append("Max tool steps: ").append(maxToolSteps).append("\n");
        }
        if (requireToolCall) {
            builder.append("TOOL_CALL_REQUIRED: true\n");
        }
        builder.append("If you do not need a tool, respond normally with Evidence line as required.\n");
        builder.append("TOOL_NONCE: ").append(nonce);
        return builder.toString();
    }

    private String generateToolNonce() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private boolean shouldRequireToolCall(String prompt) {
        if (prompt == null) return false;
        String lower = prompt.toLowerCase();
        return lower.contains("use the file_locator tool first")
            || lower.contains("use file_locator tool first")
            || lower.contains("must use file_locator")
            || lower.contains("tool_call_required")
            || lower.contains("return only the json tool call")
            || lower.contains("only the json tool call object")
            || lower.contains("respond with only a json tool call")
            || lower.contains("respond with exactly one json object");
    }

    private com.fasterxml.jackson.databind.JsonNode buildToolCallResponseFormat(String nonce, java.util.Set<String> allowedTools) {
        com.fasterxml.jackson.databind.node.ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_schema");
        com.fasterxml.jackson.databind.node.ObjectNode schemaWrapper = responseFormat.putObject("json_schema");
        schemaWrapper.put("name", "tool_call_envelope");
        com.fasterxml.jackson.databind.node.ObjectNode schema = schemaWrapper.putObject("schema");
        schema.put("type", "object");
        schema.putArray("required").add("tool").add("args").add("nonce");
        schema.put("additionalProperties", false);
        com.fasterxml.jackson.databind.node.ArrayNode oneOf = schema.putArray("oneOf");
        java.util.Set<String> toolIds = allowedTools != null && !allowedTools.isEmpty()
            ? allowedTools
            : (toolSchemaRegistry != null ? toolSchemaRegistry.getToolIds() : java.util.Set.of());
        for (String toolId : toolIds) {
            ToolSchema toolSchema = toolSchemaRegistry.getSchema(toolId);
            com.fasterxml.jackson.databind.node.ObjectNode variant = oneOf.addObject();
            variant.put("type", "object");
            variant.put("additionalProperties", false);
            variant.putArray("required").add("tool").add("args").add("nonce");
            com.fasterxml.jackson.databind.node.ObjectNode properties = variant.putObject("properties");
            com.fasterxml.jackson.databind.node.ObjectNode toolNode = properties.putObject("tool");
            toolNode.put("const", toolId);
            com.fasterxml.jackson.databind.node.ObjectNode nonceNode = properties.putObject("nonce");
            nonceNode.put("const", nonce);
            com.fasterxml.jackson.databind.node.ObjectNode argsNode = properties.putObject("args");
            argsNode.put("type", "object");
            argsNode.put("additionalProperties", false);
            com.fasterxml.jackson.databind.node.ArrayNode requiredArgs = argsNode.putArray("required");
            com.fasterxml.jackson.databind.node.ObjectNode argProps = argsNode.putObject("properties");
            if (toolSchema != null) {
                for (java.util.Map.Entry<String, ToolArgSpec> argEntry : toolSchema.getArgSpecs().entrySet()) {
                    String argName = argEntry.getKey();
                    ToolArgSpec spec = argEntry.getValue();
                    com.fasterxml.jackson.databind.node.ObjectNode argSchema = argProps.putObject(argName);
                    switch (spec.getType()) {
                        case STRING:
                            argSchema.put("type", "string");
                            if (!spec.getAllowedValues().isEmpty()) {
                                com.fasterxml.jackson.databind.node.ArrayNode enums = argSchema.putArray("enum");
                                for (String value : spec.getAllowedValues()) {
                                    enums.add(value);
                                }
                            }
                            break;
                        case INT:
                            argSchema.put("type", "integer");
                            break;
                        case BOOLEAN:
                            argSchema.put("type", "boolean");
                            break;
                        case STRING_ARRAY:
                            argSchema.put("type", "array");
                            argSchema.putObject("items").put("type", "string");
                            break;
                        default:
                            argSchema.put("type", "string");
                            break;
                    }
                    if (spec.isRequired()) {
                        requiredArgs.add(argName);
                    }
                }
            }
        }
        return responseFormat;
    }

    private com.fasterxml.jackson.databind.JsonNode buildToolDecisionResponseFormat(String nonce, java.util.Set<String> allowedTools) {
        com.fasterxml.jackson.databind.node.ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_schema");
        com.fasterxml.jackson.databind.node.ObjectNode schemaWrapper = responseFormat.putObject("json_schema");
        schemaWrapper.put("name", "tool_decision_envelope");
        com.fasterxml.jackson.databind.node.ObjectNode schema = schemaWrapper.putObject("schema");
        schema.put("type", "object");
        schema.putArray("required").add("action").add("nonce");
        schema.put("additionalProperties", false);
        com.fasterxml.jackson.databind.node.ArrayNode oneOf = schema.putArray("oneOf");

        // Final decision variant
        com.fasterxml.jackson.databind.node.ObjectNode finalVariant = oneOf.addObject();
        finalVariant.put("type", "object");
        finalVariant.put("additionalProperties", false);
        finalVariant.putArray("required").add("action").add("nonce");
        com.fasterxml.jackson.databind.node.ObjectNode finalProps = finalVariant.putObject("properties");
        finalProps.putObject("action").put("const", "final");
        finalProps.putObject("nonce").put("const", nonce);

        // Tool decision variants
        java.util.Set<String> toolIds = allowedTools != null && !allowedTools.isEmpty()
            ? allowedTools
            : (toolSchemaRegistry != null ? toolSchemaRegistry.getToolIds() : java.util.Set.of());
        for (String toolId : toolIds) {
            ToolSchema toolSchema = toolSchemaRegistry.getSchema(toolId);
            com.fasterxml.jackson.databind.node.ObjectNode variant = oneOf.addObject();
            variant.put("type", "object");
            variant.put("additionalProperties", false);
            variant.putArray("required").add("action").add("tool").add("args").add("nonce");
            com.fasterxml.jackson.databind.node.ObjectNode properties = variant.putObject("properties");
            properties.putObject("action").put("const", "tool");
            properties.putObject("tool").put("const", toolId);
            properties.putObject("nonce").put("const", nonce);
            com.fasterxml.jackson.databind.node.ObjectNode argsNode = properties.putObject("args");
            argsNode.put("type", "object");
            argsNode.put("additionalProperties", false);
            com.fasterxml.jackson.databind.node.ArrayNode requiredArgs = argsNode.putArray("required");
            com.fasterxml.jackson.databind.node.ObjectNode argProps = argsNode.putObject("properties");
            if (toolSchema != null) {
                for (java.util.Map.Entry<String, ToolArgSpec> argEntry : toolSchema.getArgSpecs().entrySet()) {
                    String argName = argEntry.getKey();
                    ToolArgSpec spec = argEntry.getValue();
                    com.fasterxml.jackson.databind.node.ObjectNode argSchema = argProps.putObject(argName);
                    switch (spec.getType()) {
                        case STRING:
                            argSchema.put("type", "string");
                            if (!spec.getAllowedValues().isEmpty()) {
                                com.fasterxml.jackson.databind.node.ArrayNode enums = argSchema.putArray("enum");
                                for (String value : spec.getAllowedValues()) {
                                    enums.add(value);
                                }
                            }
                            break;
                        case INT:
                            argSchema.put("type", "integer");
                            break;
                        case BOOLEAN:
                            argSchema.put("type", "boolean");
                            break;
                        case STRING_ARRAY:
                            argSchema.put("type", "array");
                            argSchema.putObject("items").put("type", "string");
                            break;
                        default:
                            argSchema.put("type", "string");
                            break;
                    }
                    if (spec.isRequired()) {
                        requiredArgs.add(argName);
                    }
                }
            }
        }
        return responseFormat;
    }

    private ToolPolicy parseToolPolicy(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        java.util.LinkedHashSet<String> allowedTools = null;
        Integer maxToolSteps = null;
        Boolean requireTool = null;

        JsonNode allowedNode = node.get("allowedTools");
        if (allowedNode != null && !allowedNode.isNull()) {
            if (!allowedNode.isArray()) {
                throw new IllegalArgumentException("toolPolicy.allowedTools must be an array");
            }
            allowedTools = new java.util.LinkedHashSet<>();
            for (JsonNode entry : allowedNode) {
                if (entry == null || !entry.isTextual()) {
                    continue;
                }
                String tool = entry.asText();
                if (tool == null || tool.isBlank()) {
                    continue;
                }
                if (toolSchemaRegistry != null && !toolSchemaRegistry.hasTool(tool)) {
                    throw new IllegalArgumentException("toolPolicy.allowedTools includes unknown tool: " + tool);
                }
                allowedTools.add(tool);
            }
            if (allowedTools.isEmpty()) {
                throw new IllegalArgumentException("toolPolicy.allowedTools must include at least one known tool");
            }
        }

        JsonNode maxStepsNode = node.get("maxToolSteps");
        if (maxStepsNode != null && maxStepsNode.isNumber()) {
            maxToolSteps = maxStepsNode.asInt();
        }

        JsonNode requireToolNode = node.get("requireTool");
        if (requireToolNode != null && requireToolNode.isBoolean()) {
            requireTool = requireToolNode.asBoolean();
        }

        if (allowedTools == null && maxToolSteps == null && requireTool == null) {
            return null;
        }

        return new ToolPolicy(allowedTools, maxToolSteps, requireTool);
    }

    private ToolDecisionParseResult parseToolDecision(String content, String expectedNonce) {
        if (content == null) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_FORMAT, "empty");
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_FORMAT, "empty");
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_FORMAT, "not-json-object");
        }
        if (containsMultipleJsonObjects(trimmed)) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_MULTIPLE, null);
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(trimmed);
        } catch (Exception e) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_FORMAT, "parse-error");
        }
        if (node == null || !node.isObject()) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_FORMAT, "not-object");
        }
        JsonNode actionNode = node.get("action");
        JsonNode nonceNode = node.get("nonce");
        if (actionNode == null || !actionNode.isTextual()) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_FORMAT, "missing-action");
        }
        String action = actionNode.asText();
        String nonce = nonceNode != null && nonceNode.isTextual() ? nonceNode.asText() : null;
        if (expectedNonce != null && !expectedNonce.isBlank()) {
            if (nonce == null || !expectedNonce.equals(nonce)) {
                return ToolDecisionParseResult.error(ToolCallParser.ERR_NONCE_INVALID, null);
            }
        }
        if ("final".equals(action)) {
            java.util.Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!"action".equals(field) && !"nonce".equals(field)) {
                    return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_FORMAT, "unknown-field:" + field);
                }
            }
            return ToolDecisionParseResult.finalDecision();
        }
        if (!"tool".equals(action)) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_FORMAT, "invalid-action:" + action);
        }
        JsonNode toolNode = node.get("tool");
        JsonNode argsNode = node.get("args");
        if (toolNode == null || !toolNode.isTextual() || argsNode == null || !argsNode.isObject()) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_FORMAT, "missing-tool-or-args");
        }
        java.util.Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!"action".equals(field) && !"tool".equals(field) && !"args".equals(field) && !"nonce".equals(field)) {
                return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_FORMAT, "unknown-field:" + field);
            }
        }
        String tool = toolNode.asText();
        if (toolSchemaRegistry == null || !toolSchemaRegistry.hasTool(tool)) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_UNKNOWN_TOOL, null);
        }
        ToolSchema schema = toolSchemaRegistry.getSchema(tool);
        String validationError = schema != null ? schema.validate(argsNode) : null;
        if (validationError != null) {
            return ToolDecisionParseResult.error(ToolCallParser.ERR_INVALID_ARGS, validationError);
        }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> args = objectMapper.convertValue(argsNode, java.util.Map.class);
        ToolCall call = new ToolCall(tool, args, trimmed, nonce);
        return ToolDecisionParseResult.tool(call);
    }

    private boolean containsMultipleJsonObjects(String trimmed) {
        int depth = 0;
        boolean seenObject = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '{') {
                depth++;
                if (depth == 1 && seenObject) {
                    return true;
                }
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
                if (depth == 0) {
                    seenObject = true;
                }
            }
        }
        return false;
    }

    private ToolSchemaRegistry buildToolSchemas() {
        ToolSchemaRegistry registry = new ToolSchemaRegistry();
        registry.register(new ToolSchema("file_locator")
            .arg("search_criteria", com.miniide.tools.ToolArgSpec.Type.STRING, true)
            .arg("scan_mode", com.miniide.tools.ToolArgSpec.Type.STRING, false, Set.of("FAST_SCAN", "DEEP_SCAN"))
            .arg("max_results", com.miniide.tools.ToolArgSpec.Type.INT, false)
            .arg("include_globs", com.miniide.tools.ToolArgSpec.Type.BOOLEAN, false)
            .arg("dry_run", com.miniide.tools.ToolArgSpec.Type.BOOLEAN, false)
        );
        registry.register(new ToolSchema("outline_analyzer")
            .arg("outline_path", com.miniide.tools.ToolArgSpec.Type.STRING, false)
            .arg("mode", com.miniide.tools.ToolArgSpec.Type.STRING, false)
            .arg("dry_run", com.miniide.tools.ToolArgSpec.Type.BOOLEAN, false)
        );
        registry.register(new ToolSchema("canon_checker")
            .arg("scene_path", com.miniide.tools.ToolArgSpec.Type.STRING, true)
            .arg("canon_paths", com.miniide.tools.ToolArgSpec.Type.STRING_ARRAY, true)
            .arg("mode", com.miniide.tools.ToolArgSpec.Type.STRING, false)
            .arg("dry_run", com.miniide.tools.ToolArgSpec.Type.BOOLEAN, false)
        );
        registry.register(new ToolSchema("task_router")
            .arg("user_request", com.miniide.tools.ToolArgSpec.Type.STRING, true)
            .arg("dry_run", com.miniide.tools.ToolArgSpec.Type.BOOLEAN, false)
        );
        registry.register(new ToolSchema("search_issues")
            .arg("tags", com.miniide.tools.ToolArgSpec.Type.STRING_ARRAY, false)
            .arg("assignedTo", com.miniide.tools.ToolArgSpec.Type.STRING, false)
            .arg("status", com.miniide.tools.ToolArgSpec.Type.STRING, false, Set.of("open", "closed", "all"))
            .arg("priority", com.miniide.tools.ToolArgSpec.Type.STRING, false, Set.of("low", "normal", "high", "urgent"))
            .arg("personalTags", com.miniide.tools.ToolArgSpec.Type.STRING_ARRAY, false)
            .arg("personalAgent", com.miniide.tools.ToolArgSpec.Type.STRING, false)
            .arg("excludePersonalTags", com.miniide.tools.ToolArgSpec.Type.STRING_ARRAY, false)
            .arg("minInterestLevel", com.miniide.tools.ToolArgSpec.Type.INT, false)
        );
        return registry;
    }

    private static class ToolAppendResult {
        private final String prompt;
        private final int injectedBytes;
        private final boolean exceededLimit;

        private ToolAppendResult(String prompt, int injectedBytes, boolean exceededLimit) {
            this.prompt = prompt;
            this.injectedBytes = injectedBytes;
            this.exceededLimit = exceededLimit;
        }
    }

    private static class ToolPolicy {
        private final java.util.Set<String> allowedTools;
        private final Integer maxToolSteps;
        private final Boolean requireTool;

        private ToolPolicy(java.util.Set<String> allowedTools, Integer maxToolSteps, Boolean requireTool) {
            this.allowedTools = allowedTools != null
                ? java.util.Collections.unmodifiableSet(allowedTools)
                : null;
            this.maxToolSteps = maxToolSteps;
            this.requireTool = requireTool;
        }

        private java.util.Set<String> getAllowedTools() {
            return allowedTools;
        }

        private Integer getMaxToolSteps() {
            return maxToolSteps;
        }

        private Boolean getRequireTool() {
            return requireTool;
        }
    }

    private static class ToolDecisionParseResult {
        private final ToolCall call;
        private final boolean isFinal;
        private final String errorCode;
        private final String errorDetail;

        private ToolDecisionParseResult(ToolCall call, boolean isFinal, String errorCode, String errorDetail) {
            this.call = call;
            this.isFinal = isFinal;
            this.errorCode = errorCode;
            this.errorDetail = errorDetail;
        }

        static ToolDecisionParseResult tool(ToolCall call) {
            return new ToolDecisionParseResult(call, false, null, null);
        }

        static ToolDecisionParseResult finalDecision() {
            return new ToolDecisionParseResult(null, true, null, null);
        }

        static ToolDecisionParseResult error(String code, String detail) {
            return new ToolDecisionParseResult(null, false, code, detail);
        }

        ToolCall getCall() {
            return call;
        }

        boolean isFinal() {
            return isFinal;
        }

        String getErrorCode() {
            return errorCode;
        }

        String getErrorDetail() {
            return errorDetail;
        }
    }

    private String sha256(String value) {
        if (value == null) return "";
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private TaskExecutionResult executeTaskPacketInternal(JsonNode packet, String agentId, boolean skipToolCatalog,
                                                          boolean simulateInvalid, boolean simulateBadOutput) throws Exception {
        String issueId = packet.path("parent_issue_id").asText("");
        String packetId = packet.path("packet_id").asText("");
        if (issueId.isBlank() || packetId.isBlank()) {
            return TaskExecutionResult.error("Packet missing parent_issue_id or packet_id", null, false, null);
        }
        if (!agentsUnlocked()) {
            return TaskExecutionResult.error("Project preparation incomplete. Agents are locked.", null, false, null);
        }
        Agent agent = projectContext.agents().getAgent(agentId);
        if (agent == null) {
            return TaskExecutionResult.error("Agent not found: " + agentId, null, false, null);
        }
        var endpoint = projectContext.agentEndpoints().getEndpoint(agentId);
        if (endpoint == null) {
            endpoint = agent.getEndpoint();
        }
        if (endpoint == null || endpoint.getProvider() == null || endpoint.getProvider().isBlank()) {
            return TaskExecutionResult.error("Agent endpoint not configured", null, false, null);
        }
        if (endpoint.getModel() == null || endpoint.getModel().isBlank()) {
            return TaskExecutionResult.error("Agent model not configured", null, false, null);
        }
        String provider = endpoint.getProvider().trim().toLowerCase();
        String apiKey = null;
        if (requiresApiKey(provider)) {
            String keyRef = endpoint.getApiKeyRef();
            if (keyRef == null || keyRef.isBlank()) {
                return TaskExecutionResult.error("API key required for " + provider, null, false, null);
            }
            apiKey = settingsService.resolveKey(keyRef);
        } else if (endpoint.getApiKeyRef() != null && !endpoint.getApiKeyRef().isBlank()) {
            apiKey = settingsService.resolveKey(endpoint.getApiKeyRef());
        }

        List<String> expectedArtifacts = readExpectedArtifacts(packet);
        String outputMode = packet.path("output_contract").path("output_mode").asText("");
        if (!"json_only".equalsIgnoreCase(outputMode) && expectedArtifacts.isEmpty()) {
            JsonNode receipt = buildStopHookReceipt(packet, agent, endpoint,
                "expected_artifacts missing for output_mode " + outputMode,
                "expected-artifacts");
            writeReceiptAndComment(issueId, packetId, receipt);
            return TaskExecutionResult.error("expected_artifacts required", receipt, true, null);
        }

        if (simulateInvalid) {
            JsonNode receipt = buildStopHookReceipt(packet, agent, endpoint,
                "Simulated invalid receipt for guardrail test.",
                "invalid-receipt");
            writeReceiptAndComment(issueId, packetId, receipt);
            return TaskExecutionResult.error("Simulated invalid receipt", receipt, true, null);
        }
        if (simulateBadOutput) {
            JsonNode receipt = buildStopHookReceipt(packet, agent, endpoint,
                "Simulated outputs not in expected_artifacts for guardrail test.",
                "unexpected-artifacts");
            writeReceiptAndComment(issueId, packetId, receipt);
            return TaskExecutionResult.error("Simulated unexpected outputs", receipt, true, null);
        }

        String packetJson = objectMapper.writeValueAsString(packet);
        String prompt = buildTaskExecutionPrompt(packetJson);
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

        ToolExecutionContext toolContext = new ToolExecutionContext(null, packetId, packetId, agentId);
        ReceiptAttempt attempt = runReceiptWithValidation(provider, apiKey, endpoint, prompt, toolContext);
        if (!attempt.valid) {
            String detail = attempt.validation != null && !attempt.validation.getErrors().isEmpty()
                ? String.join(", ", attempt.validation.getErrors())
                : "invalid-receipt-json";
            JsonNode receipt = buildStopHookReceipt(packet, agent, endpoint,
                "Receipt validation failed: " + detail,
                "invalid-receipt");
            writeReceiptAndComment(issueId, packetId, receipt);
            return TaskExecutionResult.error("Invalid receipt JSON", receipt, true, attempt.response);
        }

        JsonNode receiptNode = objectMapper.readTree(attempt.response);
        String receiptPacketId = receiptNode.path("packet_id").asText("");
        String receiptIssueId = receiptNode.path("issue_id").asText("");
        if (!packetId.equals(receiptPacketId) || !issueId.equals(receiptIssueId)) {
            JsonNode receipt = buildStopHookReceipt(packet, agent, endpoint,
                "Receipt packet_id/issue_id mismatch",
                "mismatched-receipt");
            writeReceiptAndComment(issueId, packetId, receipt);
            return TaskExecutionResult.error("Receipt mismatch", receipt, true, null);
        }

        List<String> outputs = readOutputsProduced(receiptNode);
        List<String> violations = new ArrayList<>();
        for (String out : outputs) {
            if (!expectedArtifacts.contains(out)) {
                violations.add(out);
            }
        }
        if (!violations.isEmpty()) {
            JsonNode receipt = buildStopHookReceipt(packet, agent, endpoint,
                "Outputs not in expected_artifacts: " + String.join(", ", violations),
                "unexpected-artifacts");
            writeReceiptAndComment(issueId, packetId, receipt);
            return TaskExecutionResult.error("Unexpected output paths", receipt, true, null);
        }

        writeReceiptAndComment(issueId, packetId, receiptNode);
        return TaskExecutionResult.success(receiptNode);
    }

    private String callAgentWithGate(String providerName, String apiKey,
                                     com.miniide.models.AgentEndpointConfig agentEndpoint,
                                     String prompt,
                                     com.fasterxml.jackson.databind.JsonNode responseFormat) {
        try {
            return AGENT_TURN_GATE.run(() -> providerChatService.chat(providerName, apiKey, agentEndpoint, prompt, responseFormat));
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

    private void executeTaskPacket(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            JsonNode packetNode = json.get("packet");
            if (packetNode == null || packetNode.isNull()) {
                ctx.status(400).json(Map.of("error", "packet required"));
                return;
            }
            String packetJson = packetNode.isTextual()
                ? packetNode.asText()
                : objectMapper.writeValueAsString(packetNode);
            PromptValidationResult packetValidation = PromptJsonValidator.validateTaskPacket(packetJson);
            if (!packetValidation.isValid()) {
                ctx.status(422).json(Map.of("error", "Invalid task packet", "details", packetValidation.getErrors()));
                return;
            }
            JsonNode packet = objectMapper.readTree(packetJson);

            String agentId = json.has("agentId") ? json.get("agentId").asText(null) : null;
            if (agentId == null || agentId.isBlank()) {
                ctx.status(400).json(Map.of("error", "agentId required"));
                return;
            }

            boolean skipToolCatalog = json.has("skipToolCatalog") && json.get("skipToolCatalog").asBoolean();
            boolean simulateInvalid = json.has("simulateInvalidReceipt") && json.get("simulateInvalidReceipt").asBoolean();
            boolean simulateBadOutput = json.has("simulateUnexpectedOutput") && json.get("simulateUnexpectedOutput").asBoolean();
            TaskExecutionResult result = executeTaskPacketInternal(packet, agentId, skipToolCatalog, simulateInvalid, simulateBadOutput);
            if (result.error != null) {
                ctx.json(Map.of("receipt", result.receipt, "stopHook", result.stopHook, "error", result.error,
                    "content", result.content));
                return;
            }
            ctx.json(Map.of("receipt", result.receipt, "stopHook", result.stopHook));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void runScenePlaybook(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            String issueId = json.has("issueId") ? json.get("issueId").asText(null) : null;
            String message = json.has("message") ? json.get("message").asText() : "";
            String clarificationChoice = json.has("clarificationChoice") ? json.get("clarificationChoice").asText(null) : null;
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

            ChiefPacketResult chiefPacket = routeChiefPacket(issueId, message, null, clarificationChoice);
            if (chiefPacket == null || chiefPacket.packet == null) {
                ctx.status(500).json(Map.of("error", "Chief router failed"));
                return;
            }
            JsonNode packet = chiefPacket.packet;
            String chiefPacketId = packet.path("packet_id").asText("");
            if (projectContext != null && projectContext.audit() != null && !chiefPacketId.isBlank()) {
                projectContext.audit().writePacket(issueId, chiefPacketId, objectMapper.writeValueAsString(packet));
            }
            String intent = packet.path("intent").asText("");
            if ("clarify".equalsIgnoreCase(intent)) {
                ctx.json(Map.of("status", "clarify", "packet", packet, "fallback", chiefPacket.fallback));
                return;
            }

            String runId = "run_" + Instant.now().toString().replace(":", "").replace(".", "");
            String basePacketId = packet.path("packet_id").asText("");
            String issue = packet.path("parent_issue_id").asText(issueId);
            JsonNode target = packet.path("target");
            JsonNode scope = packet.path("scope");
            JsonNode inputs = packet.path("inputs");
            JsonNode constraints = packet.path("constraints");

            List<PlaybookStep> steps = List.of(
                new PlaybookStep("plan_scene", "planner", "continuity_check"),
                new PlaybookStep("continuity_check", "continuity", "write_beat"),
                new PlaybookStep("write_beat", "writer", "critique_scene"),
                new PlaybookStep("critique_scene", "critic", "edit_scene"),
                new PlaybookStep("edit_scene", "editor", "continuity_check"),
                new PlaybookStep("continuity_check", "continuity", "finalize"),
                new PlaybookStep("finalize", "assistant", "")
            );

            List<JsonNode> packets = new ArrayList<>();
            List<JsonNode> receipts = new ArrayList<>();
            String parentPacketId = basePacketId;
            int stepIndex = 0;
            for (PlaybookStep step : steps) {
                stepIndex++;
                Agent agent = resolveAgentForRole(step.roleKey);
                if (agent == null) {
                    ctx.status(404).json(Map.of("error", "Agent not found for role: " + step.roleKey));
                    return;
                }
                JsonNode stepPacket = buildPlaybookPacket(issue, parentPacketId, runId, stepIndex,
                    step.intent, target, scope, inputs, constraints, agent.getId(), step.nextIntent);
                String stepPacketId = stepPacket.path("packet_id").asText("");
                if (projectContext != null && projectContext.audit() != null) {
                    projectContext.audit().writePacket(issue, stepPacketId, objectMapper.writeValueAsString(stepPacket));
                }
                packets.add(stepPacket);
                TaskExecutionResult result = executeTaskPacketInternal(stepPacket, agent.getId(), false, false, false);
                receipts.add(result.receipt);
                if (result.stopHook) {
                    ctx.json(Map.of("status", "stopped", "packets", packets, "receipts", receipts,
                        "stopHook", true, "error", result.error));
                    return;
                }
                parentPacketId = stepPacketId;
            }

            ctx.json(Map.of("status", "completed", "packets", packets, "receipts", receipts));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
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
            ToolExecutionContext toolContext = new ToolExecutionContext(null, issueId, parentPacketId, chief.getId());
            String response = runWithValidation(provider, apiKey, endpoint, prompt, "task_packet", toolContext, null);
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

    private String buildTaskExecutionPrompt(String packetJson) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are executing a task packet. Return ONLY a Receipt JSON (v0.1) and nothing else.\n");
        builder.append("Do not include prose, markdown, or any extra text.\n");
        builder.append("If you cannot complete the task, still return a Receipt JSON with stop_hook.triggered=true.\n");
        builder.append("Ensure outputs_produced only includes paths listed in the packet's output_contract.expected_artifacts.\n");
        builder.append("\nTask Packet JSON:\n");
        builder.append(packetJson);
        return builder.toString().trim();
    }

    private ChiefPacketResult routeChiefPacket(String issueId, String message, String parentPacketId, String clarificationChoice) throws Exception {
        Agent chief = resolveChiefOfStaff();
        if (chief == null) {
            return null;
        }
        var endpoint = projectContext.agentEndpoints().getEndpoint(chief.getId());
        if (endpoint == null) {
            endpoint = chief.getEndpoint();
        }
        if (endpoint == null || endpoint.getProvider() == null || endpoint.getProvider().isBlank()) {
            return null;
        }
        if (endpoint.getModel() == null || endpoint.getModel().isBlank()) {
            return null;
        }
        String provider = endpoint.getProvider().trim().toLowerCase();
        String apiKey = null;
        if (requiresApiKey(provider)) {
            String keyRef = endpoint.getApiKeyRef();
            if (keyRef == null || keyRef.isBlank()) {
                return null;
            }
            apiKey = settingsService.resolveKey(keyRef);
        } else if (endpoint.getApiKeyRef() != null && !endpoint.getApiKeyRef().isBlank()) {
            apiKey = settingsService.resolveKey(endpoint.getApiKeyRef());
        }

        String prompt = buildChiefRouterPrompt(message, issueId, parentPacketId, clarificationChoice);
        String toolCatalog = projectContext.promptTools() != null
            ? projectContext.promptTools().buildCatalogPrompt()
            : "";
        if (toolCatalog != null && !toolCatalog.isBlank()) {
            prompt = toolCatalog + "\n\n" + prompt;
        }
        String grounding = buildEarlyGroundingHeader();
        if (grounding != null && !grounding.isBlank()) {
            prompt = grounding + "\n\n" + prompt;
        }

        ToolExecutionContext toolContext = new ToolExecutionContext(null, issueId, parentPacketId, chief.getId());
        String response = runWithValidation(provider, apiKey, endpoint, prompt, "task_packet", toolContext, null);
        if (response != null && response.startsWith("STOP_HOOK")) {
            JsonNode fallback = buildFallbackPacket(message, issueId, parentPacketId, clarificationChoice);
            return new ChiefPacketResult(fallback, true);
        }
        JsonNode packetNode;
        try {
            packetNode = objectMapper.readTree(response);
        } catch (Exception parseError) {
            JsonNode fallback = buildFallbackPacket(message, issueId, parentPacketId, clarificationChoice);
            return new ChiefPacketResult(fallback, true);
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
            packetNode = obj;
        }
        return new ChiefPacketResult(packetNode, false);
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

    private JsonNode buildPlaybookPacket(String issueId, String parentPacketId, String runId, int stepIndex,
                                         String intent, JsonNode target, JsonNode scope, JsonNode inputs,
                                         JsonNode constraints, String requestedBy, String nextIntent) {
        var om = objectMapper;
        var obj = om.createObjectNode();
        obj.put("packet_id", "pkt_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        obj.put("parent_issue_id", issueId);
        obj.put("parent_packet_id", parentPacketId != null ? parentPacketId : "");
        obj.put("intent", intent);
        obj.put("run_id", runId);
        obj.put("step_id", "step_" + stepIndex);
        obj.put("attempt", 1);

        obj.set("target", target != null && target.isObject() ? target.deepCopy() : defaultTargetNode());
        obj.set("scope", scope != null && scope.isObject() ? scope.deepCopy() : defaultScopeNode());
        obj.set("inputs", inputs != null && inputs.isObject() ? inputs.deepCopy() : defaultInputsNode());
        obj.set("constraints", constraints != null && constraints.isObject() ? constraints.deepCopy() : defaultConstraintsNode());

        var output = om.createObjectNode();
        output.put("output_mode", "json_only");
        output.set("expected_artifacts", om.createArrayNode());
        output.set("stop_conditions", om.createArrayNode());
        obj.set("output_contract", output);

        var handoff = om.createObjectNode();
        handoff.put("required_next_step", nextIntent != null ? nextIntent : "");
        handoff.put("report_to_chief_only", false);
        obj.set("handoff", handoff);

        obj.put("timestamp", Instant.now().toString());
        var requested = om.createObjectNode();
        requested.put("agent_id", requestedBy != null ? requestedBy : "chief");
        requested.put("reason", "playbook");
        obj.set("requested_by", requested);
        return obj;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode defaultTargetNode() {
        var node = objectMapper.createObjectNode();
        node.put("scene_ref", "unknown");
        node.put("resolution_method", "outline_order");
        node.put("canonical_path", "");
        return node;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode defaultScopeNode() {
        var node = objectMapper.createObjectNode();
        node.set("allowed", objectMapper.createArrayNode());
        node.set("blocked", objectMapper.createArrayNode());
        return node;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode defaultInputsNode() {
        var node = objectMapper.createObjectNode();
        node.set("files", objectMapper.createArrayNode());
        node.put("canon", "");
        node.put("context", "");
        return node;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode defaultConstraintsNode() {
        var node = objectMapper.createObjectNode();
        node.set("rules", objectMapper.createArrayNode());
        node.put("pov", "");
        node.put("tense", "");
        node.put("style", "");
        node.set("forbidden", objectMapper.createArrayNode());
        return node;
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

    private List<String> readExpectedArtifacts(JsonNode packet) {
        if (packet == null) {
            return List.of();
        }
        JsonNode expected = packet.path("output_contract").path("expected_artifacts");
        if (!expected.isArray()) {
            return List.of();
        }
        List<String> artifacts = new ArrayList<>();
        for (JsonNode item : expected) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                artifacts.add(item.asText());
            }
        }
        return artifacts;
    }

    private List<String> readOutputsProduced(JsonNode receipt) {
        if (receipt == null) {
            return List.of();
        }
        JsonNode outputs = receipt.path("outputs_produced");
        if (!outputs.isArray()) {
            return List.of();
        }
        List<String> produced = new ArrayList<>();
        for (JsonNode item : outputs) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                produced.add(item.asText());
            }
        }
        return produced;
    }

    private JsonNode buildStopHookReceipt(JsonNode packet, Agent agent,
                                          com.miniide.models.AgentEndpointConfig endpoint,
                                          String detail, String hookType) {
        var om = objectMapper;
        var receipt = om.createObjectNode();
        String packetId = packet.path("packet_id").asText("");
        String issueId = packet.path("parent_issue_id").asText("");
        receipt.put("receipt_id", "rcpt_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        receipt.put("packet_id", packetId);
        receipt.put("issue_id", issueId);

        var actor = om.createObjectNode();
        actor.put("agent_id", agent != null ? safe(agent.getId()) : "unknown");
        actor.put("provider", endpoint != null ? safe(endpoint.getProvider()) : "unknown");
        actor.put("model", endpoint != null ? safe(endpoint.getModel()) : "unknown");
        var decoding = om.createObjectNode();
        var requested = buildDecodingParams(endpoint);
        decoding.set("requested", requested == null ? om.nullNode() : requested);
        decoding.set("effective", requested == null ? om.nullNode() : requested.deepCopy());
        decoding.put("source", "unknown");
        actor.set("decoding", decoding);
        receipt.set("actor", actor);

        String now = Instant.now().toString();
        receipt.put("started_at", now);
        receipt.put("finished_at", now);

        var inputsUsed = om.createArrayNode();
        JsonNode inputs = packet.path("inputs").path("files");
        if (inputs.isArray()) {
            for (JsonNode item : inputs) {
                if (item != null && item.isTextual() && !item.asText().isBlank()) {
                    inputsUsed.add(item.asText());
                }
            }
        }
        inputsUsed.add("task_packet");
        receipt.set("inputs_used", inputsUsed);

        receipt.set("outputs_produced", om.createArrayNode());
        receipt.put("reasoning_summary", "Execution halted due to guardrail enforcement.");
        var decisions = om.createArrayNode();
        decisions.add("STOP_HOOK");
        receipt.set("decisions", decisions);
        var checks = om.createArrayNode();
        checks.add("receipt_validation");
        checks.add("expected_artifacts_check");
        receipt.set("checks_performed", checks);
        receipt.set("assumptions", om.createArrayNode());
        receipt.set("risks", om.createArrayNode());
        var next = om.createObjectNode();
        next.put("intent", "clarify");
        next.put("reason", "Guardrail triggered; user clarification or model adjustment required.");
        receipt.set("next_recommended_action", next);
        var stopHook = om.createObjectNode();
        stopHook.put("triggered", true);
        stopHook.put("type", hookType != null ? hookType : "guardrail");
        stopHook.put("detail", detail != null ? detail : "");
        receipt.set("stop_hook", stopHook);
        receipt.set("citations", om.createArrayNode());
        String excerpt = "Execution halted by guardrail enforcement. " +
            (detail != null && !detail.isBlank()
                ? detail + " Review the receipt detail and retry after fixing the issue."
                : "Review the receipt detail and retry after fixing the issue.");
        receipt.put("report_excerpt", excerpt);
        return receipt;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode buildDecodingParams(com.miniide.models.AgentEndpointConfig endpoint) {
        if (endpoint == null) {
            return null;
        }
        var om = objectMapper;
        var node = om.createObjectNode();
        boolean hasAny = false;
        if (endpoint.getTemperature() != null) {
            node.put("temperature", endpoint.getTemperature());
            hasAny = true;
        }
        if (endpoint.getTopP() != null) {
            node.put("top_p", endpoint.getTopP());
            hasAny = true;
        }
        if (endpoint.getTopK() != null) {
            node.put("top_k", endpoint.getTopK());
            hasAny = true;
        }
        if (endpoint.getMinP() != null) {
            node.put("min_p", endpoint.getMinP());
            hasAny = true;
        }
        if (endpoint.getRepeatPenalty() != null) {
            node.put("repeat_penalty", endpoint.getRepeatPenalty());
            hasAny = true;
        }
        if (endpoint.getMaxOutputTokens() != null) {
            node.put("max_output_tokens", endpoint.getMaxOutputTokens());
            hasAny = true;
        }
        if (endpoint.getTimeoutMs() != null) {
            node.put("timeout_ms", endpoint.getTimeoutMs());
            hasAny = true;
        }
        if (endpoint.getMaxRetries() != null) {
            node.put("max_retries", endpoint.getMaxRetries());
            hasAny = true;
        }
        if (endpoint.getUseProviderDefaults() != null) {
            node.put("use_provider_defaults", endpoint.getUseProviderDefaults());
            hasAny = true;
        }
        return hasAny ? node : null;
    }

    private void writeReceiptAndComment(String issueId, String packetId, JsonNode receipt) {
        if (projectContext != null && projectContext.audit() != null) {
            try {
                projectContext.audit().writeReceipt(issueId, packetId, objectMapper.writeValueAsString(receipt));
            } catch (Exception e) {
                logger.warn("Failed to write receipt for packet " + packetId + ": " + e.getMessage());
            }
        }
        maybeAddReceiptComment(issueId, packetId, receipt);
    }

    private void maybeAddReceiptComment(String issueId, String packetId, JsonNode receipt) {
        if (issueService == null || issueId == null || issueId.isBlank() || receipt == null) {
            return;
        }
        int numericId;
        try {
            numericId = Integer.parseInt(issueId);
        } catch (NumberFormatException e) {
            return;
        }
        String excerpt = receipt.path("report_excerpt").asText("");
        if (excerpt.isBlank()) {
            return;
        }
        String body = "Receipt saved for packet " + packetId + ".\n\n" + excerpt;
        Comment.CommentAction action = new Comment.CommentAction("receipt", "audit");
        try {
            issueService.addComment(numericId, "system", body, action, "normal", null);
        } catch (Exception e) {
            logger.warn("Failed to append receipt comment: " + e.getMessage());
        }
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

    private static final class ReceiptAttempt {
        private final boolean valid;
        private final int attempts;
        private final String response;
        private final PromptValidationResult validation;

        private ReceiptAttempt(boolean valid, int attempts, String response, PromptValidationResult validation) {
            this.valid = valid;
            this.attempts = attempts;
            this.response = response;
            this.validation = validation;
        }
    }

    private static final class TaskExecutionResult {
        private final JsonNode receipt;
        private final boolean stopHook;
        private final String error;
        private final String content;

        private TaskExecutionResult(JsonNode receipt, boolean stopHook, String error, String content) {
            this.receipt = receipt;
            this.stopHook = stopHook;
            this.error = error;
            this.content = content;
        }

        private static TaskExecutionResult success(JsonNode receipt) {
            return new TaskExecutionResult(receipt, false, null, null);
        }

        private static TaskExecutionResult error(String error, JsonNode receipt, boolean stopHook, String content) {
            return new TaskExecutionResult(receipt, stopHook, error, content);
        }
    }

    private static final class ChiefPacketResult {
        private final JsonNode packet;
        private final boolean fallback;

        private ChiefPacketResult(JsonNode packet, boolean fallback) {
            this.packet = packet;
            this.fallback = fallback;
        }
    }

    private static final class PlaybookStep {
        private final String intent;
        private final String roleKey;
        private final String nextIntent;

        private PlaybookStep(String intent, String roleKey, String nextIntent) {
            this.intent = intent;
            this.roleKey = roleKey;
            this.nextIntent = nextIntent;
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

    private Agent resolveAgentForRole(String roleKey) {
        if (projectContext == null || projectContext.agents() == null || roleKey == null) {
            return null;
        }
        String normalized = roleKey.trim().toLowerCase();
        List<Agent> agents = projectContext.agents().listEnabledAgents();
        Agent fallback = null;
        for (Agent agent : agents) {
            if (agent == null) {
                continue;
            }
            String role = agent.getRole() != null ? agent.getRole().trim().toLowerCase() : "";
            if (!role.equals(normalized)) {
                continue;
            }
            if (Boolean.TRUE.equals(agent.getIsPrimaryForRole())) {
                return agent;
            }
            if (fallback == null) {
                fallback = agent;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        if ("assistant".equals(normalized) || "chief".equals(normalized)) {
            return resolveChiefOfStaff();
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
