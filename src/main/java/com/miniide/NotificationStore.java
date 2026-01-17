package com.miniide;

import com.miniide.models.Notification;
import com.miniide.models.Notification.Category;
import com.miniide.models.Notification.Level;
import com.miniide.models.Notification.Scope;
import com.miniide.storage.JsonStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NotificationStore {

    private static final String STORAGE_PATH = "data/notifications.json";
    private final Map<String, Notification> notifications = new ConcurrentHashMap<>();
    private volatile String currentProjectId;

    public NotificationStore() {
        loadFromDisk();
    }

    public void setCurrentProjectId(String projectId) {
        this.currentProjectId = projectId;
    }

    public String getCurrentProjectId() {
        return currentProjectId;
    }

    public Notification push(Level level, Scope scope, String message) {
        return push(level, scope, message, null, Category.INFO, false, null, null, null);
    }

    public Notification push(Level level, Scope scope, String message, String details,
                             Category category, boolean persistent,
                             String actionLabel, Object actionPayload, String source) {
        Notification notification = createNotification(level, scope, category, message, details, persistent,
            actionLabel, actionPayload, source);
        notifications.put(notification.getId(), notification);
        log("Notification pushed: " + notification.getId() + " – " + notification.getMessage());
        saveAll();
        return notification;
    }

    public Notification info(String message, Scope scope) {
        return push(Level.INFO, scope, message);
    }

    public Notification success(String message, Scope scope) {
        return push(Level.SUCCESS, scope, message);
    }

    public Notification warning(String message, Scope scope) {
        return push(Level.WARNING, scope, message);
    }

    public Notification error(String message, Scope scope, boolean blocking) {
        Category category = blocking ? Category.BLOCKING : Category.INFO;
        return push(Level.ERROR, scope, message, null, category, blocking, null, null, "system");
    }

    public Notification editorSaveSuccess(String filePath) {
        return success("Saved " + filePath, Scope.EDITOR);
    }

    public Notification editorSaveFailure(String filePath, String details) {
        return push(Level.ERROR, Scope.EDITOR, "Save failed: " + filePath, details,
            Category.BLOCKING, true, "Retry", null, "editor");
    }

    public Notification editorDiscardWarning(String filePath) {
        return push(Level.WARNING, Scope.EDITOR, "Changes discarded in " + filePath, null,
            Category.ATTENTION, false, null, null, "editor");
    }

    public Notification editorSearchNoResults(String term, boolean workspace) {
        String target = workspace ? "workspace" : "this file";
        return info("No results for \"" + term + "\" in " + target, Scope.EDITOR);
    }

    public Notification issueCreated(int issueId, String title, String author, String assignee) {
        String message = "Issue #" + issueId + " created: " + title;
        String details = "Author: " + author + (assignee != null ? " | Assignee: " + assignee : "");
        return push(Level.INFO, Scope.WORKBENCH, message, details, Category.ATTENTION, false,
            "View Issue", null, "issues");
    }

    public Notification issueClosed(int issueId, String title) {
        return push(Level.SUCCESS, Scope.WORKBENCH, "Issue #" + issueId + " closed: " + title, null,
            Category.INFO, false, "View Issue", null, "issues");
    }

    public Notification issueCommentAdded(int issueId, String author) {
        return push(Level.INFO, Scope.WORKBENCH, "Comment added to Issue #" + issueId, "By: " + author,
            Category.SOCIAL, false, "View Thread", null, "issues");
    }

    public Notification agentPatchProposal(String file, String patchId) {
        String message = "Patch proposed for " + file;
        String details = "Patch: " + patchId;
        return push(Level.INFO, Scope.EDITOR, message, details, Category.ATTENTION, true,
            "Review Patch", null, "agent");
    }

    public List<Notification> getAll() {
        List<Notification> results = new ArrayList<>(notifications.values());
        results.sort(Comparator.comparingLong(Notification::getTimestamp).reversed());
        return results;
    }

    public List<Notification> getByLevelAndScope(Set<Level> levels, Set<Scope> scopes) {
        List<Notification> results = new ArrayList<>();
        for (Notification notification : notifications.values()) {
            if (levels != null && !levels.isEmpty() && !levels.contains(notification.getLevel())) {
                continue;
            }
            if (scopes != null && !scopes.isEmpty() && !scopes.contains(notification.getScope())) {
                continue;
            }
            results.add(notification);
        }
        results.sort(Comparator.comparingLong(Notification::getTimestamp).reversed());
        return results;
    }

    public int getUnreadCount() {
        int count = 0;
        for (Notification notification : notifications.values()) {
            if (!notification.isRead()) {
                count++;
            }
        }
        return count;
    }

    public int getUnreadCountByScope(Scope scope) {
        int count = 0;
        for (Notification notification : notifications.values()) {
            if (!notification.isRead() && scope == notification.getScope()) {
                count++;
            }
        }
        return count;
    }

    public void markRead(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        Notification notification = notifications.get(id);
        if (notification != null) {
            notification.setRead(true);
            saveAll();
        }
    }

    public void dismiss(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        Notification notification = notifications.get(id);
        if (notification != null) {
            notification.setRead(true);
            notification.setPersistent(false);
            saveAll();
        }
    }

    public void markAllRead() {
        for (Notification notification : notifications.values()) {
            notification.setRead(true);
        }
        saveAll();
    }

    public void clearNonErrors() {
        notifications.values().removeIf(n -> n.getLevel() != Level.ERROR);
        saveAll();
    }

    public void clearAll() {
        notifications.clear();
        saveAll();
    }

    public boolean delete(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        Notification removed = notifications.remove(id);
        if (removed != null) {
            log("Notification deleted: " + id);
            saveAll();
            return true;
        }
        return false;
    }

    // Backward-compatible entry point
    public Notification push(String message, String level) {
        return push(Level.fromString(level), Scope.GLOBAL, message);
    }

    public List<Notification> list() {
        return getAll();
    }

    public void clear() {
        clearAll();
    }

    private void loadFromDisk() {
        Path path = Paths.get(STORAGE_PATH);
        if (!Files.exists(path)) {
            logWarning("No notification storage found at " + STORAGE_PATH + "; starting empty.");
            return;
        }

        try {
            List<Notification> stored = JsonStorage.readJsonList(STORAGE_PATH, Notification[].class);
            for (Notification notification : stored) {
                applyDefaults(notification);
                if (notification.getId() != null && !notification.getId().isBlank()) {
                    notifications.put(notification.getId(), notification);
                }
            }
            log("Loaded " + stored.size() + " notification(s) from disk.");
        } catch (Exception e) {
            logWarning("Failed to load notifications from " + STORAGE_PATH + ": " + e.getMessage());
        }
    }

    private Notification createNotification(Level level, Scope scope, Category category, String message,
                                            String details, boolean persistent, String actionLabel,
                                            Object actionPayload, String source) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Notification notification = new Notification(
            id,
            level != null ? level : Level.INFO,
            scope != null ? scope : Scope.GLOBAL,
            category != null ? category : Category.INFO,
            message,
            details,
            source,
            now,
            actionLabel,
            actionPayload,
            persistent,
            false,
            currentProjectId  // Include current project ID
        );

        applyDefaults(notification);
        return notification;
    }

    private void applyDefaults(Notification notification) {
        if (notification == null) {
            return;
        }
        if (notification.getScope() == null) {
            notification.setScope(Scope.GLOBAL);
        }
        if (notification.getCategory() == null) {
            notification.setCategory(Category.INFO);
        }
        if (notification.getLevel() == null) {
            notification.setLevel(Level.INFO);
        }
        if (notification.getTimestamp() <= 0) {
            notification.setTimestamp(System.currentTimeMillis());
        }
        // persistent/read default to false if missing (primitive handles it)
    }

    private void saveAll() {
        try {
            List<Notification> data = new ArrayList<>(notifications.values());
            JsonStorage.writeJsonList(STORAGE_PATH, data);
        } catch (Exception e) {
            logWarning("Failed to save notifications to " + STORAGE_PATH + ": " + e.getMessage());
        }
    }

    private void log(String message) {
        AppLogger logger = AppLogger.get();
        if (logger != null) {
            logger.info("[NotificationStore] " + message);
        } else {
            System.out.println("[NotificationStore] " + message);
        }
    }

    private void logWarning(String message) {
        AppLogger logger = AppLogger.get();
        if (logger != null) {
            logger.warn("[NotificationStore] " + message);
        } else {
            System.out.println("[NotificationStore] " + message);
        }
    }
}
