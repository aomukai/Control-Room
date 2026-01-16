package com.miniide.models;

public class StoryManifest {
    private int schemaVersion;
    private String preparedAt;
    private int sceneCount;
    private String status;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getPreparedAt() {
        return preparedAt;
    }

    public void setPreparedAt(String preparedAt) {
        this.preparedAt = preparedAt;
    }

    public int getSceneCount() {
        return sceneCount;
    }

    public void setSceneCount(int sceneCount) {
        this.sceneCount = sceneCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
