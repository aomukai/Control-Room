package com.miniide.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class Notification {

    public enum Level {
        INFO,
        SUCCESS,
        WARNING,
        ERROR;

        @JsonCreator
        public static Level fromString(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim().toLowerCase();
            switch (normalized) {
                case "info":
                    return INFO;
                case "success":
                    return SUCCESS;
                case "warn":
                case "warning":
                    return WARNING;
                case "error":
                    return ERROR;
                default:
                    return null;
            }
        }

        @JsonValue
        public String toJson() {
            return name().toLowerCase();
        }
    }

    public enum Scope {
        GLOBAL,
        WORKBENCH,
        EDITOR,
        TERMINAL,
        JOBS;

        @JsonCreator
        public static Scope fromString(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim().toLowerCase();
            switch (normalized) {
                case "global":
                    return GLOBAL;
                case "workbench":
                    return WORKBENCH;
                case "editor":
                    return EDITOR;
                case "terminal":
                    return TERMINAL;
                case "jobs":
                    return JOBS;
                default:
                    return null;
            }
        }

        @JsonValue
        public String toJson() {
            return name().toLowerCase();
        }
    }

    public enum Category {
        BLOCKING,
        ATTENTION,
        SOCIAL,
        INFO;

        @JsonCreator
        public static Category fromString(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim().toLowerCase();
            switch (normalized) {
                case "blocking":
                    return BLOCKING;
                case "attention":
                    return ATTENTION;
                case "social":
                    return SOCIAL;
                case "info":
                    return INFO;
                default:
                    return null;
            }
        }

        @JsonValue
        public String toJson() {
            return name().toLowerCase();
        }
    }

    private String id;
    private Level level;
    private Scope scope;
    private Category category;
    private String message;
    private String details;
    private String source;
    @JsonAlias("createdAt")
    private long timestamp;
    private String actionLabel;
    private Object actionPayload;
    private boolean persistent;
    private boolean read;
    private String projectId;

    public Notification() {
    }

    public Notification(String id, Level level, Scope scope, Category category, String message, String details,
                        String source, long timestamp, String actionLabel, Object actionPayload,
                        boolean persistent, boolean read) {
        this(id, level, scope, category, message, details, source, timestamp, actionLabel, actionPayload, persistent, read, null);
    }

    public Notification(String id, Level level, Scope scope, Category category, String message, String details,
                        String source, long timestamp, String actionLabel, Object actionPayload,
                        boolean persistent, boolean read, String projectId) {
        this.id = id;
        this.level = level;
        this.scope = scope;
        this.category = category;
        this.message = message;
        this.details = details;
        this.source = source;
        this.timestamp = timestamp;
        this.actionLabel = actionLabel;
        this.actionPayload = actionPayload;
        this.persistent = persistent;
        this.read = read;
        this.projectId = projectId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public Object getActionPayload() {
        return actionPayload;
    }

    public void setActionPayload(Object actionPayload) {
        this.actionPayload = actionPayload;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @Override
    public String toString() {
        return "Notification{" +
            "id='" + id + '\'' +
            ", level=" + level +
            ", scope=" + scope +
            ", category=" + category +
            ", message='" + message + '\'' +
            ", timestamp=" + timestamp +
            ", persistent=" + persistent +
            ", read=" + read +
            ", projectId='" + projectId + '\'' +
            '}';
    }
}
