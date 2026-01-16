package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class StoryRegistry {
    private int schemaVersion;
    private List<StoryScene> scenes = new ArrayList<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public List<StoryScene> getScenes() {
        return scenes;
    }

    public void setScenes(List<StoryScene> scenes) {
        this.scenes = scenes != null ? scenes : new ArrayList<>();
    }
}
