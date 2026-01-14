package com.miniide.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Issue {

    private int id;
    private String title;
    private String body;
    private String openedBy;
    private String assignedTo;
    private List<String> tags = new ArrayList<>();
    private String priority = "normal";  // low, normal, high, urgent
    private String status = "open";      // open, closed, waiting-on-user
    private List<Comment> comments = new ArrayList<>();
    private long createdAt;
    private long updatedAt;
    private Long closedAt;
    private Long frozenAt;
    private Long frozenUntil;
    private String frozenReason;

    public Issue() {
    }

    public Issue(int id, String title, String body, String openedBy, String assignedTo,
                 List<String> tags, String priority, String status, long createdAt, long updatedAt) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.openedBy = openedBy;
        this.assignedTo = assignedTo;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        this.priority = priority != null ? priority : "normal";
        this.status = status != null ? status : "open";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
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

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority != null ? priority : "normal";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        String oldStatus = this.status;
        this.status = status != null ? status : "open";
        // Auto-set closedAt when transitioning to closed
        if ("closed".equalsIgnoreCase(this.status) && !"closed".equalsIgnoreCase(oldStatus)) {
            this.closedAt = System.currentTimeMillis();
        }
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments != null ? new ArrayList<>(comments) : new ArrayList<>();
    }

    public void addComment(Comment comment) {
        if (comment != null) {
            this.comments.add(comment);
            this.updatedAt = System.currentTimeMillis();
        }
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

    public Long getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Long closedAt) {
        this.closedAt = closedAt;
    }

    public Long getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(Long frozenAt) {
        this.frozenAt = frozenAt;
    }

    public Long getFrozenUntil() {
        return frozenUntil;
    }

    public void setFrozenUntil(Long frozenUntil) {
        this.frozenUntil = frozenUntil;
    }

    public String getFrozenReason() {
        return frozenReason;
    }

    public void setFrozenReason(String frozenReason) {
        this.frozenReason = frozenReason;
    }

    @Override
    public String toString() {
        return "Issue{" +
            "id=" + id +
            ", title='" + title + '\'' +
            ", priority='" + priority + '\'' +
            ", status='" + status + '\'' +
            ", openedBy='" + openedBy + '\'' +
            ", assignedTo='" + assignedTo + '\'' +
            ", tags=" + tags +
            ", comments=" + comments.size() +
            ", createdAt=" + createdAt +
            ", updatedAt=" + updatedAt +
            ", closedAt=" + closedAt +
            ", frozenAt=" + frozenAt +
            ", frozenUntil=" + frozenUntil +
            ", frozenReason='" + frozenReason + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Issue issue = (Issue) o;
        return id == issue.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
