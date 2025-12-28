package com.miniide;

import com.miniide.models.Notification;
import com.miniide.storage.JsonStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NotificationStore {

    private static final String STORAGE_PATH = "data/notifications.json";
    private final Map<String, Notification> notifications = new ConcurrentHashMap<>();

    public NotificationStore() {
        loadFromDisk();
    }

    public Notification push(String message, String level) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Notification notification = new Notification(id, message, level, now, false);
        notifications.put(id, notification);
        log("Notification pushed: " + notification.getId() + " – " + notification.getMessage());
        saveAll();
        return notification;
    }

    public List<Notification> list() {
        List<Notification> results = new ArrayList<>(notifications.values());
        results.sort(Comparator.comparingLong(Notification::getCreatedAt));
        return results;
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

    public void clear() {
        notifications.clear();
        saveAll();
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
                if (notification.getId() != null && !notification.getId().isBlank()) {
                    notifications.put(notification.getId(), notification);
                }
            }
            log("Loaded " + stored.size() + " notification(s) from disk.");
        } catch (Exception e) {
            logWarning("Failed to load notifications from " + STORAGE_PATH + ": " + e.getMessage());
        }
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
