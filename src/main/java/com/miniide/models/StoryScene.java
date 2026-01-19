package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class StoryScene {
    private String origin;
    private String stableId;
    private String displayId;
    private String title;
    private String chapterId;
    private int order;
    private String content;
    private List<IngestPointer> ingestPointers = new ArrayList<>();
    private String createdAt;
    private String updatedAt;
    private String status;
    private String lastIndexedHash;
    private String indexStatus;
    private List<String> linkedCardStableIds = new ArrayList<>();
    private List<String> linkedHookIds = new ArrayList<>();
    private List<HookMatch> hookMatches = new ArrayList<>();

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getStableId() {
        return stableId;
    }

    public void setStableId(String stableId) {
        this.stableId = stableId;
    }

    public String getDisplayId() {
        return displayId;
    }

    public void setDisplayId(String displayId) {
        this.displayId = displayId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getChapterId() {
        return chapterId;
    }

    public void setChapterId(String chapterId) {
        this.chapterId = chapterId;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<IngestPointer> getIngestPointers() {
        return ingestPointers;
    }

    public void setIngestPointers(List<IngestPointer> ingestPointers) {
        this.ingestPointers = ingestPointers != null ? ingestPointers : new ArrayList<>();
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastIndexedHash() {
        return lastIndexedHash;
    }

    public void setLastIndexedHash(String lastIndexedHash) {
        this.lastIndexedHash = lastIndexedHash;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus) {
        this.indexStatus = indexStatus;
    }

    public List<String> getLinkedCardStableIds() {
        return linkedCardStableIds;
    }

    public void setLinkedCardStableIds(List<String> linkedCardStableIds) {
        this.linkedCardStableIds = linkedCardStableIds != null ? linkedCardStableIds : new ArrayList<>();
    }

    public List<String> getLinkedHookIds() {
        return linkedHookIds;
    }

    public void setLinkedHookIds(List<String> linkedHookIds) {
        this.linkedHookIds = linkedHookIds != null ? linkedHookIds : new ArrayList<>();
    }

    public List<HookMatch> getHookMatches() {
        return hookMatches;
    }

    public void setHookMatches(List<HookMatch> hookMatches) {
        this.hookMatches = hookMatches != null ? hookMatches : new ArrayList<>();
    }
}
