package com.miniide.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Issue {

    private String id;
    private String title;
    private String body;
    private String openedBy;
    private String assignedTo;
    private List<String> tags = new ArrayList<>();
    private String status;
    private long createdAt;
    private long updatedAt;

    public Issue() {
    }

    public Issue(String id, String title, String body, String openedBy, String assignedTo, List<String> tags,
                 String status, long createdAt, long updatedAt) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.openedBy = openedBy;
        this.assignedTo = assignedTo;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

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

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getOpenedBy() {
        return openedBy;
    }

    public void setOpenedBy(String openedBy) {
        this.openedBy = openedBy;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    @Override
    public String toString() {
        return "Issue{" +
            "id='" + id + '\'' +
            ", title='" + title + '\'' +
            ", status='" + status + '\'' +
            ", openedBy='" + openedBy + '\'' +
            ", assignedTo='" + assignedTo + '\'' +
            ", tags=" + tags +
            ", createdAt=" + createdAt +
            ", updatedAt=" + updatedAt +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Issue issue = (Issue) o;
        return Objects.equals(id, issue.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
