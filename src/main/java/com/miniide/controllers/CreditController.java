package com.miniide.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.CreditStore;
import com.miniide.models.CreditEvent;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

/**
 * Controller for credit events.
 */
public class CreditController implements Controller {

    private final CreditStore creditStore;
    private final ObjectMapper objectMapper;

    public CreditController(CreditStore creditStore, ObjectMapper objectMapper) {
        this.creditStore = creditStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/credits", this::listCredits);
        app.get("/api/credits/{id}", this::getCredit);
        app.post("/api/credits", this::createCredit);
        app.get("/api/credits/assisted-slice", this::computeAssistedSlice);
    }

    private void listCredits(Context ctx) {
        try {
            String agentId = ctx.queryParam("agentId");
            if (agentId != null && !agentId.isBlank()) {
                ctx.json(creditStore.listByAgent(agentId));
            } else {
                ctx.json(creditStore.listAll());
            }
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getCredit(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            CreditEvent event = creditStore.get(id);
            if (event == null) {
                ctx.status(404).json(Map.of("error", "Credit event not found: " + id));
                return;
            }
            ctx.json(event);
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void createCredit(Context ctx) {
        try {
            CreditEvent event = objectMapper.readValue(ctx.body(), CreditEvent.class);
            CreditEvent saved = creditStore.award(event);
            ctx.status(201).json(saved);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void computeAssistedSlice(Context ctx) {
        try {
            double total = ctx.queryParamAsClass("totalCredits", Double.class).get();
            int slices = ctx.queryParamAsClass("slices", Integer.class).get();
            double perSlice = creditStore.computeAssistedCredits(total, slices);
            ctx.json(Map.of(
                "totalCredits", total,
                "slices", slices,
                "perSlice", perSlice
            ));
        } catch (Exception e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }
}
