package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class CanonCard {
    private String origin;
    private String stableId;
    private String displayId;
    private String type;
    private String title;
    private List<String> aliases = new ArrayList<>();
    private List<String> domains = new ArrayList<>();
    private String content;
    private List<String> canonHooks = new ArrayList<>();
    private List<String> entities = new ArrayList<>();
    private boolean continuityCritical;
    private List<IngestPointer> ingestPointers = new ArrayList<>();
    private String createdAt;
    private String updatedAt;
    private String annotationStatus;
    private String status;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases != null ? aliases : new ArrayList<>();
    }

    public List<String> getDomains() {
        return domains;
    }

    public void setDomains(List<String> domains) {
        this.domains = domains != null ? domains : new ArrayList<>();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getCanonHooks() {
        return canonHooks;
    }

    public void setCanonHooks(List<String> canonHooks) {
        this.canonHooks = canonHooks != null ? canonHooks : new ArrayList<>();
    }

    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities != null ? entities : new ArrayList<>();
    }

    public boolean isContinuityCritical() {
        return continuityCritical;
    }

    public void setContinuityCritical(boolean continuityCritical) {
        this.continuityCritical = continuityCritical;
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

    public String getAnnotationStatus() {
        return annotationStatus;
    }

    public void setAnnotationStatus(String annotationStatus) {
        this.annotationStatus = annotationStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
