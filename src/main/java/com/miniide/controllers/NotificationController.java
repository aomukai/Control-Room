package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.NotificationStore;
import com.miniide.models.Notification;
import com.miniide.models.Notification.Category;
import com.miniide.models.Notification.Level;
import com.miniide.models.Notification.Scope;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Controller for notification management.
 */
public class NotificationController implements Controller {

    private final NotificationStore notificationStore;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;

    public NotificationController(NotificationStore notificationStore, ObjectMapper objectMapper) {
        this.notificationStore = notificationStore;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/notifications", this::getNotifications);
        app.get("/api/notifications/unread-count", this::getUnreadCount);
        app.get("/api/notifications/{id}", this::getNotification);
        app.post("/api/notifications", this::createNotification);
        app.put("/api/notifications/{id}", this::updateNotification);
        app.delete("/api/notifications/{id}", this::deleteNotification);
        app.post("/api/notifications/mark-all-read", this::markAllNotificationsRead);
        app.post("/api/notifications/clear", this::clearNotifications);
    }

    private void getNotifications(Context ctx) {
        try {
            String levelParam = ctx.queryParam("level");
            String scopeParam = ctx.queryParam("scope");

            Set<Level> levels = null;
            Set<Scope> scopes = null;

            if (levelParam != null && !levelParam.isBlank()) {
                levels = new HashSet<>();
                for (String l : levelParam.split(",")) {
                    Level level = Level.fromString(l.trim());
                    if (level != null) {
                        levels.add(level);
                    }
                }
            }

            if (scopeParam != null && !scopeParam.isBlank()) {
                scopes = new HashSet<>();
                for (String s : scopeParam.split(",")) {
                    Scope scope = Scope.fromString(s.trim());
                    if (scope != null) {
                        scopes.add(scope);
                    }
                }
            }

            List<Notification> notifications;
            if (levels != null || scopes != null) {
                notifications = notificationStore.getByLevelAndScope(levels, scopes);
            } else {
                notifications = notificationStore.getAll();
            }

            ctx.json(notifications);
        } catch (Exception e) {
            logger.error("Error getting notifications: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getNotification(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            List<Notification> all = notificationStore.getAll();
            Optional<Notification> found = all.stream()
                .filter(n -> id.equals(n.getId()))
                .findFirst();

            if (found.isPresent()) {
                ctx.json(found.get());
            } else {
                ctx.status(404).json(Map.of("error", "Notification not found: " + id));
            }
        } catch (Exception e) {
            logger.error("Error getting notification: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getUnreadCount(Context ctx) {
        try {
            String scopeParam = ctx.queryParam("scope");
            int count;

            if (scopeParam != null && !scopeParam.isBlank()) {
                Scope scope = Scope.fromString(scopeParam);
                if (scope != null) {
                    count = notificationStore.getUnreadCountByScope(scope);
                } else {
                    count = notificationStore.getUnreadCount();
                }
            } else {
                count = notificationStore.getUnreadCount();
            }

            ctx.json(Map.of("unreadCount", count));
        } catch (Exception e) {
            logger.error("Error getting unread count: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void createNotification(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());

            String message = json.has("message") ? json.get("message").asText() : null;
            if (message == null || message.isBlank()) {
                ctx.status(400).json(Map.of("error", "Message is required"));
                return;
            }

            Level level = Level.INFO;
            if (json.has("level")) {
                Level parsed = Level.fromString(json.get("level").asText());
                if (parsed != null) level = parsed;
            }

            Scope scope = Scope.GLOBAL;
            if (json.has("scope")) {
                Scope parsed = Scope.fromString(json.get("scope").asText());
                if (parsed != null) scope = parsed;
            }

            Category category = Category.INFO;
            if (json.has("category")) {
                Category parsed = Category.fromString(json.get("category").asText());
                if (parsed != null) category = parsed;
            }

            String details = json.has("details") ? json.get("details").asText() : null;
            boolean persistent = json.has("persistent") && json.get("persistent").asBoolean();
            String actionLabel = json.has("actionLabel") ? json.get("actionLabel").asText() : null;
            Object actionPayload = json.has("actionPayload") ? json.get("actionPayload") : null;
            String source = json.has("source") ? json.get("source").asText() : null;

            Notification notification = notificationStore.push(level, scope, message, details,
                category, persistent, actionLabel, actionPayload, source);

            logger.info("Notification created via API: " + notification.getId());
            ctx.status(201).json(notification);
        } catch (Exception e) {
            logger.error("Error creating notification: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void updateNotification(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            JsonNode json = objectMapper.readTree(ctx.body());

            boolean changed = false;

            if (json.has("dismissed") && json.get("dismissed").asBoolean()) {
                notificationStore.dismiss(id);
                logger.info("Notification dismissed: " + id);
                changed = true;
            } else if (json.has("persistent") && !json.get("persistent").asBoolean()) {
                notificationStore.dismiss(id);
                logger.info("Notification persistence cleared: " + id);
                changed = true;
            }

            if (json.has("read") && json.get("read").asBoolean()) {
                notificationStore.markRead(id);
                logger.info("Notification marked as read: " + id);
                changed = true;
            }

            if (changed) {
                ctx.json(Map.of("success", true, "message", "Notification updated"));
            } else {
                ctx.json(Map.of("success", true, "message", "No changes made"));
            }
        } catch (Exception e) {
            logger.error("Error updating notification: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void deleteNotification(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            boolean deleted = notificationStore.delete(id);

            if (deleted) {
                logger.info("Notification deleted: " + id);
                ctx.json(Map.of("success", true, "message", "Notification deleted"));
            } else {
                ctx.status(404).json(Map.of("error", "Notification not found: " + id));
            }
        } catch (Exception e) {
            logger.error("Error deleting notification: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void markAllNotificationsRead(Context ctx) {
        try {
            notificationStore.markAllRead();
            logger.info("All notifications marked as read");
            ctx.json(Map.of("success", true, "message", "All notifications marked as read"));
        } catch (Exception e) {
            logger.error("Error marking all notifications read: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void clearNotifications(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());
            boolean clearAll = json.has("all") && json.get("all").asBoolean();

            if (clearAll) {
                notificationStore.clearAll();
                logger.info("All notifications cleared");
                ctx.json(Map.of("success", true, "message", "All notifications cleared"));
            } else {
                notificationStore.clearNonErrors();
                logger.info("Non-error notifications cleared");
                ctx.json(Map.of("success", true, "message", "Non-error notifications cleared"));
            }
        } catch (Exception e) {
            logger.error("Error clearing notifications: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }
}
