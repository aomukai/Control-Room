package com.miniide;

import com.miniide.models.Notification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationStore {

    private final Map<String, Notification> notifications = new ConcurrentHashMap<>();

    public Notification push(String message, String level) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Notification notification = new Notification(id, message, level, now, false);
        notifications.put(id, notification);
        log("Notification pushed: " + notification.getId() + " – " + notification.getMessage());
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
        }
    }

    public void clear() {
        notifications.clear();
    }

    private void log(String message) {
        AppLogger logger = AppLogger.get();
        if (logger != null) {
            logger.info("[NotificationStore] " + message);
        } else {
            System.out.println("[NotificationStore] " + message);
        }
    }
}
