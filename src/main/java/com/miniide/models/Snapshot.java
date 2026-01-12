package com.miniide.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a published snapshot in the versioning history.
 */
public class Snapshot {
    private String id;
    private String name;
    private Instant publishedAt;
    private List<SnapshotFile> files;
    private int addedWords;
    private int removedWords;

    public Snapshot() {
        this.files = new ArrayList<>();
    }

    public Snapshot(String id, String name, Instant publishedAt) {
        this.id = id;
        this.name = name;
        this.publishedAt = publishedAt;
        this.files = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public List<SnapshotFile> getFiles() { return files; }
    public void setFiles(List<SnapshotFile> files) { this.files = files; }

    public int getAddedWords() { return addedWords; }
    public void setAddedWords(int addedWords) { this.addedWords = addedWords; }

    public int getRemovedWords() { return removedWords; }
    public void setRemovedWords(int removedWords) { this.removedWords = removedWords; }

    public void addFile(SnapshotFile file) {
        this.files.add(file);
    }
}
