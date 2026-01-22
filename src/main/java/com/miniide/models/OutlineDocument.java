package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class OutlineDocument {
    private String id;
    private String title;
    private int version;
    private long createdAt;
    private long updatedAt;
    private List<OutlineScene> scenes = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<OutlineScene> getScenes() {
        return scenes;
    }

    public void setScenes(List<OutlineScene> scenes) {
        this.scenes = scenes != null ? scenes : new ArrayList<>();
    }
}
