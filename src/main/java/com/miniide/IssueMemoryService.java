package com.miniide;

import com.miniide.models.Issue;
import com.miniide.storage.JsonStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IssueMemoryService {

    private static final String STORAGE_PATH = "data/issues.json";
    private final Map<String, Issue> issues = new ConcurrentHashMap<>();

    public IssueMemoryService() {
        loadFromDisk();
    }

    public Issue createIssue(String title, String body, String openedBy, String assignedTo, List<String> tags) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Issue issue = new Issue(
            id,
            title,
            body,
            openedBy,
            assignedTo,
            tags,
            "open",
            now,
            now
        );

        issues.put(id, issue);
        log("Issue created: " + issue.getId() + " – " + issue.getTitle());
        saveAll();
        return issue;
    }

    public Optional<Issue> getIssue(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(issues.get(id));
    }

    public List<Issue> listIssues() {
        List<Issue> results = new ArrayList<>(issues.values());
        results.sort(Comparator.comparingLong(Issue::getCreatedAt));
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
        results.sort(Comparator.comparingLong(Issue::getCreatedAt));
        return results;
    }

    public List<Issue> listIssuesByAssignee(String assignedTo) {
        if (assignedTo == null || assignedTo.isBlank()) {
            return new ArrayList<>();
        }
        List<Issue> results = new ArrayList<>();
        for (Issue issue : issues.values()) {
            if (assignedTo.equals(issue.getAssignedTo())) {
                results.add(issue);
            }
        }
        results.sort(Comparator.comparingLong(Issue::getCreatedAt));
        return results;
    }

    public Issue updateIssue(Issue updated) {
        if (updated == null || updated.getId() == null || updated.getId().isBlank()) {
            throw new IllegalArgumentException("Issue id is required for update");
        }

        Issue existing = issues.get(updated.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Issue not found: " + updated.getId());
        }

        existing.setTitle(updated.getTitle());
        existing.setBody(updated.getBody());
        existing.setOpenedBy(updated.getOpenedBy());
        existing.setAssignedTo(updated.getAssignedTo());
        existing.setTags(updated.getTags());
        existing.setStatus(updated.getStatus());
        existing.setUpdatedAt(System.currentTimeMillis());

        saveAll();
        return existing;
    }

    public boolean deleteIssue(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        Issue removed = issues.remove(id);
        if (removed != null) {
            log("Issue deleted: " + removed.getId() + " – " + removed.getTitle());
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
            for (Issue issue : stored) {
                if (issue.getId() != null && !issue.getId().isBlank()) {
                    issues.put(issue.getId(), issue);
                }
            }
            log("Loaded " + stored.size() + " issue(s) from disk.");
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
}
