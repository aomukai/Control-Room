package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class PatchProposal {
    private String id;
    private String title;
    private String description;
    private String issueId;
    private String filePath;
    private List<TextEdit> edits = new ArrayList<>();
    private String preview;
    private long createdAt;
    private String status; // pending | applied | rejected
    private List<PatchFileChange> files = new ArrayList<>();
    private PatchProvenance provenance;
    private List<PatchAuditEntry> auditLog = new ArrayList<>();

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String issueId) {
        this.issueId = issueId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public List<TextEdit> getEdits() {
        return edits;
    }

    public void setEdits(List<TextEdit> edits) {
        this.edits = edits != null ? edits : new ArrayList<>();
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<PatchFileChange> getFiles() {
        return files;
    }

    public void setFiles(List<PatchFileChange> files) {
        this.files = files != null ? files : new ArrayList<>();
    }

    public PatchProvenance getProvenance() {
        return provenance;
    }

    public void setProvenance(PatchProvenance provenance) {
        this.provenance = provenance;
    }

    public List<PatchAuditEntry> getAuditLog() {
        return auditLog;
    }

    public void setAuditLog(List<PatchAuditEntry> auditLog) {
        this.auditLog = auditLog != null ? auditLog : new ArrayList<>();
    }
}
