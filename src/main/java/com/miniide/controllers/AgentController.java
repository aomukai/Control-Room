package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.ProjectContext;
import com.miniide.RoleKey;
import com.miniide.models.Agent;
import com.miniide.models.AgentEndpointConfig;
import com.miniide.models.RoleFreedomSettings;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller for agent management.
 * Handles: agent CRUD, status, ordering, import, endpoints, role settings
 */
public class AgentController implements Controller {

    private final ProjectContext projectContext;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public AgentController(ProjectContext projectContext, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        // Agent CRUD
        app.get("/api/agents", this::getAgents);
        app.get("/api/agents/all", this::getAllAgents);
        app.post("/api/agents", this::createAgent);
        app.put("/api/agents/order", this::reorderAgents);
        app.get("/api/agents/{id}", this::getAgent);
        app.put("/api/agents/{id}", this::updateAgent);
        app.put("/api/agents/{id}/status", this::setAgentStatus);
        app.post("/api/agents/import", this::importAgent);

        // Agent endpoints
        app.get("/api/agent-endpoints", this::listAgentEndpoints);
        app.get("/api/agent-endpoints/{id}", this::getAgentEndpoint);
        app.put("/api/agent-endpoints/{id}", this::updateAgentEndpoint);

        // Role settings
        app.get("/api/agents/role-settings", this::getRoleSettings);
        app.get("/api/agents/role-settings/{role}", this::getRoleSettingsByRole);
        app.put("/api/agents/role-settings/{role}", this::saveRoleSettings);
    }

    private void getAgents(Context ctx) {
        try {
            ctx.json(projectContext.agents().listEnabledAgents());
        } catch (Exception e) {
            logger.error("Failed to list agents: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getAllAgents(Context ctx) {
        try {
            ctx.json(projectContext.agents().listAllAgents());
        } catch (Exception e) {
            logger.error("Failed to list agents: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void createAgent(Context ctx) {
        try {
            Agent agent = ctx.bodyAsClass(Agent.class);
            Agent created = projectContext.agents().createAgent(agent);
            if (created.getEndpoint() != null) {
                projectContext.agentEndpoints().upsertEndpoint(created.getId(), created.getEndpoint());
            }
            ctx.status(201).json(created);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (BadRequestResponse e) {
            ctx.status(400).json(Map.of("error", "Invalid agent payload"));
        } catch (Exception e) {
            logger.error("Failed to create agent: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getAgent(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            Agent agent = projectContext.agents().getAgent(id);
            if (agent == null) {
                ctx.status(404).json(Map.of("error", "Agent not found: " + id));
                return;
            }
            ctx.json(agent);
        } catch (Exception e) {
            logger.error("Failed to get agent: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void updateAgent(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            Agent updates = ctx.bodyAsClass(Agent.class);
            Agent updated = projectContext.agents().updateAgent(id, updates);
            if (updated == null) {
                ctx.status(404).json(Map.of("error", "Agent not found: " + id));
                return;
            }
            if (updates.getEndpoint() != null) {
                projectContext.agentEndpoints().upsertEndpoint(id, updates.getEndpoint());
            }
            ctx.json(updated);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Failed to update agent: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void setAgentStatus(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            JsonNode json = objectMapper.readTree(ctx.body());
            boolean enabled = json.has("enabled") && json.get("enabled").asBoolean();
            Agent updated = projectContext.agents().setEnabled(id, enabled);
            if (updated == null) {
                ctx.status(404).json(Map.of("error", "Agent not found: " + id));
                return;
            }
            ctx.json(updated);
        } catch (Exception e) {
            logger.error("Failed to update agent status: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void reorderAgents(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            if (!json.has("order") || !json.get("order").isArray()) {
                ctx.status(400).json(Map.of("error", "order array required"));
                return;
            }
            List<String> order = new ArrayList<>();
            json.get("order").forEach(node -> order.add(node.asText()));
            projectContext.agents().reorderAgents(order);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            logger.error("Failed to reorder agents: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void importAgent(Context ctx) {
        try {
            Agent agent = ctx.bodyAsClass(Agent.class);
            Agent imported = projectContext.agents().importAgent(agent);
            ctx.status(201).json(imported);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Failed to import agent: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void listAgentEndpoints(Context ctx) {
        ctx.json(projectContext.agentEndpoints().listEndpoints());
    }

    private void getAgentEndpoint(Context ctx) {
        String id = ctx.pathParam("id");
        var endpoint = projectContext.agentEndpoints().getEndpoint(id);
        if (endpoint == null) {
            ctx.status(404).json(Map.of("error", "Endpoint not found for agent: " + id));
            return;
        }
        ctx.json(endpoint);
    }

    private void updateAgentEndpoint(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            var config = ctx.bodyAsClass(AgentEndpointConfig.class);
            var saved = projectContext.agentEndpoints().upsertEndpoint(id, config);
            if (saved == null) {
                ctx.status(400).json(Map.of("error", "Invalid agent endpoint payload"));
                return;
            }
            if (config != null && config.getModel() != null) {
                projectContext.agents().syncModelRecord(id, config.getModel());
            }
            Agent agent = projectContext.agents().getAgent(id);
            if (agent != null
                && agent.getAssisted() != null
                && agent.getAssisted()
                && agent.getAssistedModel() != null
                && config.getModel() != null
                && !agent.getAssistedModel().equals(config.getModel())) {
                Agent updates = new Agent();
                updates.setAssisted(false);
                updates.setAssistedReason(null);
                updates.setAssistedSince(null);
                updates.setAssistedModel(null);
                projectContext.agents().updateAgent(id, updates);
            }
            ctx.json(saved);
        } catch (Exception e) {
            logger.error("Failed to update agent endpoint: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getRoleSettings(Context ctx) {
        try {
            ctx.json(projectContext.agents().listRoleSettings());
        } catch (Exception e) {
            logger.error("Failed to list role settings: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getRoleSettingsByRole(Context ctx) {
        try {
            String roleKey = RoleKey.canonicalize(ctx.pathParam("role"));
            RoleFreedomSettings settings = projectContext.agents().getRoleSettings(roleKey);
            if (settings == null) {
                RoleFreedomSettings defaults = new RoleFreedomSettings();
                defaults.setRole(roleKey);
                defaults.setTemplate("balanced");
                defaults.setFreedomLevel("semi-autonomous");
                defaults.setNotifyUserOn(List.of("question", "conflict", "completion", "error"));
                defaults.setMaxActionsPerSession(10);
                defaults.setRoleCharter("");
                defaults.setCollaborationGuidance("");
                defaults.setToolAndSafetyNotes("");
                ctx.json(defaults);
                return;
            }
            ctx.json(settings);
        } catch (Exception e) {
            logger.error("Failed to get role settings: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void saveRoleSettings(Context ctx) {
        try {
            String roleKey = RoleKey.canonicalize(ctx.pathParam("role"));
            RoleFreedomSettings settings = ctx.bodyAsClass(RoleFreedomSettings.class);
            settings.setRole(roleKey);
            RoleFreedomSettings saved = projectContext.agents().saveRoleSettings(settings);
            ctx.json(saved);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Failed to save role settings: " + e.getMessage(), e);
            ctx.status(500).json(Controller.errorBody(e));
        }
    }
}
