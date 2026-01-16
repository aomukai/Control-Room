package com.miniide.models;

public class WorkspaceMetadata {
    private String displayName;
    private String description;
    private String icon;
    private String accentColor;
    private boolean prepared;
    private String preparedMode;
    private String preparedAt;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public void setPrepared(boolean prepared) {
        this.prepared = prepared;
    }

    public String getPreparedMode() {
        return preparedMode;
    }

    public void setPreparedMode(String preparedMode) {
        this.preparedMode = preparedMode;
    }

    public String getPreparedAt() {
        return preparedAt;
    }

    public void setPreparedAt(String preparedAt) {
        this.preparedAt = preparedAt;
    }
}
