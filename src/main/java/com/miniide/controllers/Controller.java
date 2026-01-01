package com.miniide.controllers;

import io.javalin.Javalin;

/**
 * Interface for API controllers.
 * Each controller registers its routes with the Javalin app.
 */
public interface Controller {

    /**
     * Register this controller's routes with the Javalin app.
     */
    void registerRoutes(Javalin app);
}
