package com.miniide.controllers;

import io.javalin.Javalin;

import java.util.Map;

/**
 * Interface for API controllers.
 * Each controller registers its routes with the Javalin app.
 */
public interface Controller {

    /**
     * Register this controller's routes with the Javalin app.
     */
    void registerRoutes(Javalin app);

    /**
     * Safe error body helper that handles null exception messages.
     * Use this instead of Map.of("error", e.getMessage()) to prevent NPE.
     */
    static Map<String, Object> errorBody(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            m = e.getClass().getSimpleName();
        }
        return Map.of("error", m);
    }
}
