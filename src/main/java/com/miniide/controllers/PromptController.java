package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.PromptRegistry;
import com.miniide.ProjectContext;
import com.miniide.models.PromptTool;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public class PromptController implements Controller {
    private final ProjectContext projectContext;
    private final ObjectMapper objectMapper;

    public PromptController(ProjectContext projectContext, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.objectMapper = objectMapper;
    }

    /**
     * Get the prompt registry dynamically to support workspace switching.
     */
    private PromptRegistry promptRegistry() {
        return projectContext.promptTools();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/prompts", this::listPrompts);
        app.get("/api/prompts/{id}", this::getPrompt);
        app.post("/api/prompts", this::createPrompt);
        app.put("/api/prompts/{id}", this::updatePrompt);
        app.delete("/api/prompts/{id}", this::deletePrompt);
    }

    private void listPrompts(Context ctx) {
        List<PromptTool> prompts = promptRegistry().listPrompts();
        ctx.json(prompts);
    }

    private void getPrompt(Context ctx) {
        String id = ctx.pathParam("id");
        PromptTool prompt = promptRegistry().getPrompt(id);
        if (prompt == null) {
            ctx.status(404).json(Map.of("error", "Prompt not found: " + id));
            return;
        }
        ctx.json(prompt);
    }

    private void createPrompt(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            PromptTool prompt = objectMapper.treeToValue(json, PromptTool.class);
            if (prompt.getId() != null && promptRegistry().getPrompt(prompt.getId()) != null) {
                ctx.status(409).json(Map.of("error", "Prompt already exists: " + prompt.getId()));
                return;
            }
            PromptTool saved = promptRegistry().savePrompt(prompt);
            ctx.status(201).json(saved);
        } catch (Exception e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void updatePrompt(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            JsonNode json = objectMapper.readTree(ctx.body());
            PromptTool prompt = objectMapper.treeToValue(json, PromptTool.class);
            if (prompt.getId() == null || prompt.getId().isBlank()) {
                prompt.setId(id);
            }
            if (!id.equals(prompt.getId())) {
                ctx.status(400).json(Map.of("error", "Prompt ID mismatch"));
                return;
            }
            PromptTool saved = promptRegistry().savePrompt(prompt);
            ctx.json(saved);
        } catch (Exception e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void deletePrompt(Context ctx) {
        String id = ctx.pathParam("id");
        boolean deleted = promptRegistry().deletePrompt(id);
        if (!deleted) {
            ctx.status(404).json(Map.of("error", "Prompt not found: " + id));
            return;
        }
        ctx.json(Map.of("success", true));
    }
}
