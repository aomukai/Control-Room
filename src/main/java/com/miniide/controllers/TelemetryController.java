package com.miniide.controllers;

import com.miniide.ProjectContext;
import com.miniide.TelemetryStore;
import com.miniide.models.TelemetrySession;
import com.miniide.models.TelemetryTotals;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

public class TelemetryController implements Controller {
    private final ProjectContext projectContext;

    public TelemetryController(ProjectContext projectContext) {
        this.projectContext = projectContext;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/telemetry/summary", this::getSummary);
    }

    private TelemetryStore store() {
        return projectContext != null ? projectContext.telemetry() : null;
    }

    private void getSummary(Context ctx) {
        TelemetryStore store = store();
        if (store == null) {
            ctx.status(500).json(Map.of("error", "Telemetry store unavailable"));
            return;
        }
        TelemetryTotals totals = store.getTotals();
        TelemetrySession session = store.getCurrentSession();
        ctx.json(Map.of(
            "totals", totals,
            "session", session
        ));
    }
}
