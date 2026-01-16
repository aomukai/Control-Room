package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class IngestManifest {
    private int schemaVersion;
    private String ingestId;
    private String ingestedAt;
    private String projectSalt;
    private IngestStats stats;
    private List<IngestIgnoredInput> ignoredInputs = new ArrayList<>();
    private String status;
    private String mode;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getIngestId() {
        return ingestId;
    }

    public void setIngestId(String ingestId) {
        this.ingestId = ingestId;
    }

    public String getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(String ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    public String getProjectSalt() {
        return projectSalt;
    }

    public void setProjectSalt(String projectSalt) {
        this.projectSalt = projectSalt;
    }

    public IngestStats getStats() {
        return stats;
    }

    public void setStats(IngestStats stats) {
        this.stats = stats;
    }

    public List<IngestIgnoredInput> getIgnoredInputs() {
        return ignoredInputs;
    }

    public void setIgnoredInputs(List<IngestIgnoredInput> ignoredInputs) {
        this.ignoredInputs = ignoredInputs != null ? ignoredInputs : new ArrayList<>();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
