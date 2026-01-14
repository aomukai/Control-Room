package com.miniide;

import com.miniide.models.Comment;
import com.miniide.models.Issue;
import com.miniide.storage.JsonStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IssueMemoryService {

    private static final String STORAGE_PATH = "data/issues.json";
    private static final Map<String, String> ROADMAP_STATUS_TAGS = Map.of(
        "idea", "Idea",
        "plan", "Plan",
        "draft", "Draft",
        "polished", "Polished"
    );
    private final Map<Integer, Issue> issues = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    public IssueMemoryService() {
        loadFromDisk();
    }

    public Issue createIssue(String title, String body, String openedBy, String assignedTo,
                             List<String> tags, String priority) {
        int id = idCounter.incrementAndGet();
        long now = System.currentTimeMillis();
        List<String> normalizedTags = normalizeTags(tags);

        Issue issue = new Issue(
            id,
            title,
            body,
            openedBy,
            assignedTo,
            normalizedTags,
            priority,
            "open",
            now,
            now
        );

        issues.put(id, issue);
        log("Issue created: #" + issue.getId() + " – " + issue.getTitle());
        saveAll();
        return issue;
    }

    public Optional<Issue> getIssue(int id) {
        return Optional.ofNullable(issues.get(id));
    }

    public List<Issue> listIssues() {
        List<Issue> results = new ArrayList<>(issues.values());
        results.sort(Comparator.comparingLong(Issue::getCreatedAt).reversed());
        return results;
    }

    public List<Issue> listIssuesByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return listIssues();
        }
        List<Issue> results = new ArrayList<>();
        for (Issue issue : issues.values()) {
            if (issue.getTags() != null && issue.getTags().contains(tag)) {
                results.add(issue);
            }
        }
        results.sort(Comparator.comparingLong(Issue::getCreatedAt).reversed());
        return results;
    }

    public List<Issue> listIssuesByAssignee(String assignedTo) {
        if (assignedTo == null || assignedTo.isBlank()) {
            return new ArrayList<>();
        }
        List<Issue> results = new ArrayList<>();
        for (Issue issue : issues.values()) {
            if (assignedTo.equalsIgnoreCase(issue.getAssignedTo())) {
                results.add(issue);
            }
        }
        results.sort(Comparator.comparingLong(Issue::getCreatedAt).reversed());
        return results;
    }

    public List<Issue> listIssuesByPriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return listIssues();
        }
        List<Issue> results = new ArrayList<>();
        for (Issue issue : issues.values()) {
            if (priority.equalsIgnoreCase(issue.getPriority())) {
                results.add(issue);
            }
        }
        results.sort(Comparator.comparingLong(Issue::getCreatedAt).reversed());
        return results;
    }

    public Issue updateIssue(Issue updated) {
        if (updated == null || updated.getId() <= 0) {
            throw new IllegalArgumentException("Issue id is required for update");
        }

        Issue existing = issues.get(updated.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Issue not found: #" + updated.getId());
        }

        existing.setTitle(updated.getTitle());
        existing.setBody(updated.getBody());
        existing.setOpenedBy(updated.getOpenedBy());
        existing.setAssignedTo(updated.getAssignedTo());
        existing.setTags(normalizeTags(updated.getTags()));
        existing.setPriority(updated.getPriority());
        existing.setStatus(updated.getStatus());
        existing.setFrozenAt(updated.getFrozenAt());
        existing.setFrozenUntil(updated.getFrozenUntil());
        existing.setFrozenReason(updated.getFrozenReason());
        existing.setUpdatedAt(System.currentTimeMillis());

        saveAll();
        return existing;
    }

    public Comment addComment(int issueId, String author, String body, Comment.CommentAction action,
                              String impactLevel, Comment.CommentEvidence evidence) {
        Issue issue = issues.get(issueId);
        if (issue == null) {
            throw new IllegalArgumentException("Issue not found: #" + issueId);
        }

        Comment comment = new Comment(author, body, System.currentTimeMillis(), action);
        comment.setImpactLevel(impactLevel);
        comment.setEvidence(evidence);
        issue.addComment(comment);
        log("Comment added to Issue #" + issueId + " by " + author);
        saveAll();
        return comment;
    }

    public boolean deleteIssue(int id) {
        Issue removed = issues.remove(id);
        if (removed != null) {
            log("Issue deleted: #" + removed.getId() + " – " + removed.getTitle());
            saveAll();
        }
        return removed != null;
    }

    private void loadFromDisk() {
        Path path = Paths.get(STORAGE_PATH);
        if (!Files.exists(path)) {
            logWarning("No issue storage found at " + STORAGE_PATH + "; starting empty.");
            return;
        }

        try {
            List<Issue> stored = JsonStorage.readJsonList(STORAGE_PATH, Issue[].class);
            int maxId = 0;
            for (Issue issue : stored) {
                if (issue.getId() > 0) {
                    issues.put(issue.getId(), issue);
                    if (issue.getId() > maxId) {
                        maxId = issue.getId();
                    }
                }
            }
            idCounter.set(maxId);
            log("Loaded " + stored.size() + " issue(s) from disk. Next ID: " + (maxId + 1));
        } catch (Exception e) {
            logWarning("Failed to load issues from " + STORAGE_PATH + ": " + e.getMessage());
        }
    }

    private void saveAll() {
        try {
            List<Issue> data = new ArrayList<>(issues.values());
            JsonStorage.writeJsonList(STORAGE_PATH, data);
        } catch (Exception e) {
            logWarning("Failed to save issues to " + STORAGE_PATH + ": " + e.getMessage());
        }
    }

    private void log(String message) {
        AppLogger logger = AppLogger.get();
        if (logger != null) {
            logger.info("[IssueMemoryService] " + message);
        }
    }

    private void logWarning(String message) {
        AppLogger logger = AppLogger.get();
        if (logger != null) {
            logger.warn("[IssueMemoryService] " + message);
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> normalized = new ArrayList<>();
        String roadmapTag = null;

        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String canonicalRoadmap = ROADMAP_STATUS_TAGS.get(trimmed.toLowerCase());
            if (canonicalRoadmap != null) {
                roadmapTag = canonicalRoadmap;
                continue;
            }
            if (!normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }

        if (roadmapTag != null) {
            normalized.add(roadmapTag);
        }

        return normalized;
    }
}
