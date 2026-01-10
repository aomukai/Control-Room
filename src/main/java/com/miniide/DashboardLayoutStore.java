package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.storage.JsonStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages persistence of dashboard widget layouts per workspace.
 */
public class DashboardLayoutStore {

    private final Path storagePath;
    private final ObjectMapper objectMapper;

    public DashboardLayoutStore(Path workspacePath, ObjectMapper objectMapper) {
        Path storageDir = workspacePath.resolve(".controlroom").resolve("state");
        this.storagePath = storageDir.resolve("dashboard-layout.json");
        this.objectMapper = objectMapper;
    }

    /**
     * Save the dashboard layout.
     */
    public void save(DashboardLayout layout) throws IOException {
        Path parent = storagePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), layout);
    }

    /**
     * Load the dashboard layout.
     * Returns null if no layout exists.
     */
    public DashboardLayout load() throws IOException {
        if (!Files.exists(storagePath)) {
            return null;
        }
        return objectMapper.readValue(storagePath.toFile(), DashboardLayout.class);
    }

    /**
     * Dashboard layout data structure.
     */
    public static class DashboardLayout {
        private String workspaceId;
        private int version;
        private int columns;
        private Object[] widgets; // Store as Object[] to accept any widget instance structure

        public DashboardLayout() {
            this.workspaceId = "default";
            this.version = 1;
            this.columns = 4;
            this.widgets = new Object[0];
        }

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public int getColumns() {
            return columns;
        }

        public void setColumns(int columns) {
            this.columns = columns;
        }

        public Object[] getWidgets() {
            return widgets;
        }

        public void setWidgets(Object[] widgets) {
            this.widgets = widgets;
        }
    }
}
