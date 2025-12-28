package com.miniide.models;

public class Notification {

    private String id;
    private String message;
    private String level; // info, success, warn, error
    private long createdAt;
    private boolean read;

    public Notification() {
    }

    public Notification(String id, String message, String level, long createdAt, boolean read) {
        this.id = id;
        this.message = message;
        this.level = level;
        this.createdAt = createdAt;
        this.read = read;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    @Override
    public String toString() {
        return "Notification{" +
            "id='" + id + '\'' +
            ", level='" + level + '\'' +
            ", message='" + message + '\'' +
            ", createdAt=" + createdAt +
            ", read=" + read +
            '}';
    }
}
