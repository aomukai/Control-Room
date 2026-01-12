package com.miniide.models;

/**
 * Represents a file entry within a snapshot.
 */
public class SnapshotFile {
    private String path;
    private String status; // "modified", "added", "deleted", "renamed"
    private String contentHash;
    private String previousPath; // For renamed files

    public SnapshotFile() {}

    public SnapshotFile(String path, String status, String contentHash) {
        this.path = path;
        this.status = status;
        this.contentHash = contentHash;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getPreviousPath() { return previousPath; }
    public void setPreviousPath(String previousPath) { this.previousPath = previousPath; }
}
