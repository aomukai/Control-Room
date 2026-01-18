package com.miniide.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.DashboardLayoutStore;
import com.miniide.DashboardLayoutStore.DashboardLayout;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

/**
 * Controller for dashboard widget layout persistence.
 */
public class DashboardController implements Controller {

    private final DashboardLayoutStore store;
    private final ObjectMapper objectMapper;

    public DashboardController(DashboardLayoutStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/dashboard/layout", this::getLayout);
        app.post("/api/dashboard/layout", this::saveLayout);
    }

    /**
     * GET /api/dashboard/layout
     * Returns the saved dashboard layout, or null if none exists.
     */
    private void getLayout(Context ctx) {
        try {
            DashboardLayout layout = store.load();
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("layout", layout);
            ctx.json(response);
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    /**
     * POST /api/dashboard/layout
     * Saves the dashboard layout.
     * Expected body: { "layout": { workspaceId, version, columns, widgets } }
     */
    private void saveLayout(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Object layoutData = body.get("layout");

            if (layoutData == null) {
                ctx.status(400).json(Map.of("error", "Missing 'layout' field in request body"));
                return;
            }

            // Convert the layout data to DashboardLayout
            DashboardLayout layout = objectMapper.convertValue(layoutData, DashboardLayout.class);
            store.save(layout);

            ctx.json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }
}
