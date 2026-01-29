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

    private static final Map<String, String> ROADMAP_STATUS_TAGS = Map.of(
        "idea", "Idea",
        "plan", "Plan",
        "draft", "Draft",
        "polished", "Polished"
    );
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long MONTH_MS = 30L * DAY_MS;
    private static final long TWO_MONTHS_MS = 60L * DAY_MS;
    private static final long THREE_MONTHS_MS = 90L * DAY_MS;
    private static final int MAX_MEMORY_LEVEL = 5;
    private static final int MIN_MEMORY_LEVEL = 1;
    private final Map<Integer, Issue> issues = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private Path storagePath;

    public IssueMemoryService(Path workspacePath) {
        switchWorkspace(workspacePath);
    }

    public synchronized void switchWorkspace(Path workspacePath) {
        this.storagePath = workspacePath.resolve(".controlroom").resolve("state").resolve("issues.json");
        issues.clear();
        idCounter.set(0);
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
        applyMemoryDefaults(issue);
        log("Issue created: #" + issue.getId() + " – " + issue.getTitle());
        saveAll();
        return issue;
    }

    public Optional<Issue> getIssue(int id) {
        return Optional.ofNullable(issues.get(id));
    }

    public void recordAccess(int id) {
        Issue issue = issues.get(id);
        if (issue == null) {
            return;
        }
        touchAccess(issue);
        saveAll();
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
        existing.setEpistemicStatus(updated.getEpistemicStatus());
        existing.setMemoryLevel(updated.getMemoryLevel());
        existing.setLastAccessedAt(updated.getLastAccessedAt());
        existing.setLastCompressedAt(updated.getLastCompressedAt());
        existing.setCompressedSummary(updated.getCompressedSummary());
        existing.setResolutionSummary(updated.getResolutionSummary());
        existing.setSemanticTrace(updated.getSemanticTrace());
        existing.setUpdatedAt(System.currentTimeMillis());
        touchAccess(existing);
        applyMemoryDefaults(existing);

        saveAll();
        return existing;
    }

    public Issue updateIssueCompressionFields(int issueId, String level1, String level2, String level3) {
        Issue issue = issues.get(issueId);
        if (issue == null) {
            throw new IllegalArgumentException("Issue not found: #" + issueId);
        }
        if (level1 != null) {
            issue.setSemanticTrace(level1);
        }
        if (level2 != null) {
            issue.setResolutionSummary(level2);
        }
        if (level3 != null) {
            issue.setCompressedSummary(level3);
        }
        issue.setLastCompressedAt(System.currentTimeMillis());
        saveAll();
        return issue;
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
        touchAccess(issue);
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
        if (!Files.exists(storagePath)) {
            log("No issue storage found at " + storagePath + "; starting empty.");
            return;
        }

        try {
            List<Issue> stored = JsonStorage.readJsonList(storagePath.toString(), Issue[].class);
            int maxId = 0;
            for (Issue issue : stored) {
                if (issue.getId() > 0) {
                    applyMemoryDefaults(issue);
                    issues.put(issue.getId(), issue);
                    if (issue.getId() > maxId) {
                        maxId = issue.getId();
                    }
                }
            }
            idCounter.set(maxId);
            log("Loaded " + stored.size() + " issue(s) from disk. Next ID: " + (maxId + 1));
        } catch (Exception e) {
            logWarning("Failed to load issues from " + storagePath + ": " + e.getMessage());
        }
    }

    private void saveAll() {
        try {
            // Ensure parent directories exist
            Path parent = storagePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            List<Issue> data = new ArrayList<>(issues.values());
            JsonStorage.writeJsonList(storagePath.toString(), data);
        } catch (Exception e) {
            logWarning("Failed to save issues to " + storagePath + ": " + e.getMessage());
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

    public List<Issue> listIssuesByEpistemicStatus(String minimumStatus) {
        int threshold = epistemicRank(minimumStatus);
        if (threshold <= 0) {
            return new ArrayList<>();
        }
        List<Issue> results = new ArrayList<>();
        for (Issue issue : issues.values()) {
            if (issue == null) {
                continue;
            }
            if (epistemicRank(issue.getEpistemicStatus()) >= threshold) {
                results.add(issue);
            }
        }
        results.sort(Comparator.comparingLong(Issue::getUpdatedAt).reversed());
        return results;
    }

    public Issue reviveIssue(int issueId) {
        Issue issue = issues.get(issueId);
        if (issue == null) {
            throw new IllegalArgumentException("Issue not found: #" + issueId);
        }
        issue.setMemoryLevel(Math.max(3, normalizeMemoryLevel(issue.getMemoryLevel())));
        issue.setCompressedSummary(buildCompressedSummary(issue));
        issue.setResolutionSummary(buildResolutionSummary(issue));
        issue.setSemanticTrace(buildSemanticTrace(issue));
        issue.setLastCompressedAt(System.currentTimeMillis());
        touchAccess(issue);
        saveAll();
        return issue;
    }

    public DecayResult runDecay(boolean dryRun) {
        int decayed = 0;
        List<Integer> updated = new ArrayList<>();
        for (Issue issue : issues.values()) {
            if (issue == null) {
                continue;
            }
            if ("open".equalsIgnoreCase(issue.getStatus())) {
                continue;
            }
            int level = normalizeMemoryLevel(issue.getMemoryLevel());
            long age = resolveAgeMs(issue);
            int nextLevel = level;
            if (level == 5 && age > MONTH_MS) {
                nextLevel = 4;
            } else if (level == 4 && age > MONTH_MS) {
                nextLevel = 3;
            } else if (level == 3 && age > TWO_MONTHS_MS) {
                nextLevel = 2;
            } else if (level == 2 && age > THREE_MONTHS_MS) {
                nextLevel = 1;
            }
            if (nextLevel != level) {
                decayed++;
                updated.add(issue.getId());
                if (!dryRun) {
                    issue.setMemoryLevel(nextLevel);
                    compressIssue(issue, nextLevel);
                    issue.setLastCompressedAt(System.currentTimeMillis());
                    issues.put(issue.getId(), issue);
                }
            }
        }
        if (!dryRun && decayed > 0) {
            saveAll();
        }
        DecayResult result = new DecayResult();
        result.decayed = decayed;
        result.updatedIssueIds = updated;
        return result;
    }

    private void compressIssue(Issue issue, int targetLevel) {
        if (issue == null) {
            return;
        }
        int level = normalizeMemoryLevel(targetLevel);
        if (level <= 3) {
            issue.setCompressedSummary(buildCompressedSummary(issue));
        }
        if (level <= 2) {
            issue.setResolutionSummary(buildResolutionSummary(issue));
        }
        if (level <= 1) {
            issue.setSemanticTrace(buildSemanticTrace(issue));
        }
    }

    private void applyMemoryDefaults(Issue issue) {
        if (issue == null) {
            return;
        }
        Integer level = issue.getMemoryLevel();
        if (level == null) {
            level = "open".equalsIgnoreCase(issue.getStatus()) ? 5 : 3;
            issue.setMemoryLevel(level);
        } else {
            issue.setMemoryLevel(normalizeMemoryLevel(level));
        }
        if (issue.getLastAccessedAt() == null) {
            long fallback = issue.getUpdatedAt() > 0 ? issue.getUpdatedAt() : issue.getCreatedAt();
            issue.setLastAccessedAt(fallback > 0 ? fallback : System.currentTimeMillis());
        }
    }

    private void touchAccess(Issue issue) {
        if (issue == null) {
            return;
        }
        issue.setLastAccessedAt(System.currentTimeMillis());
        if ("open".equalsIgnoreCase(issue.getStatus())) {
            issue.setMemoryLevel(5);
        } else {
            int level = normalizeMemoryLevel(issue.getMemoryLevel());
            if (level < 3) {
                issue.setMemoryLevel(3);
            }
        }
    }

    private long resolveAgeMs(Issue issue) {
        long now = System.currentTimeMillis();
        long base = issue.getLastAccessedAt() != null ? issue.getLastAccessedAt() : issue.getUpdatedAt();
        if (base <= 0 && issue.getClosedAt() != null) {
            base = issue.getClosedAt();
        }
        if (base <= 0) {
            base = issue.getCreatedAt();
        }
        return base > 0 ? now - base : 0;
    }

    private int normalizeMemoryLevel(Integer level) {
        int raw = level == null ? 3 : level;
        return Math.max(MIN_MEMORY_LEVEL, Math.min(MAX_MEMORY_LEVEL, raw));
    }

    private int epistemicRank(String status) {
        if (status == null) {
            return 0;
        }
        switch (status.trim().toLowerCase()) {
            case "tentative":
                return 1;
            case "proposed":
                return 2;
            case "agreed":
                return 3;
            case "verified":
                return 4;
            default:
                return 0;
        }
    }

    private String buildCompressedSummary(Issue issue) {
        if (issue == null) {
            return "";
        }
        String title = safe(issue.getTitle());
        String bodySnippet = truncate(safe(issue.getBody()), 220);
        StringBuilder summary = new StringBuilder();
        if (!title.isBlank()) {
            summary.append(title);
        }
        if (!bodySnippet.isBlank()) {
            if (summary.length() > 0) summary.append(" - ");
            summary.append(bodySnippet);
        }
        List<Comment> comments = issue.getComments();
        if (comments != null && !comments.isEmpty()) {
            String first = truncate(safe(comments.get(0).getBody()), 180);
            String last = truncate(safe(comments.get(comments.size() - 1).getBody()), 180);
            if (!first.isBlank()) {
                summary.append(" | First: ").append(first);
            }
            if (!last.isBlank() && !last.equals(first)) {
                summary.append(" | Last: ").append(last);
            }
        }
        return summary.toString().trim();
    }

    private String buildResolutionSummary(Issue issue) {
        if (issue == null) {
            return "";
        }
        List<Comment> comments = issue.getComments();
        String resolution = "";
        if (comments != null && !comments.isEmpty()) {
            resolution = truncate(safe(comments.get(comments.size() - 1).getBody()), 240);
        }
        if (resolution.isBlank()) {
            resolution = truncate(safe(issue.getBody()), 240);
        }
        if (resolution.isBlank()) {
            resolution = truncate(safe(issue.getTitle()), 240);
        }
        String tags = issue.getTags() != null && !issue.getTags().isEmpty()
            ? " Tags: " + String.join(" ", issue.getTags())
            : "";
        return ("Resolution: " + resolution + tags).trim();
    }

    private String buildSemanticTrace(Issue issue) {
        if (issue == null) {
            return "";
        }
        String title = safe(issue.getTitle());
        String base = issue.getResolutionSummary();
        if (base == null || base.isBlank()) {
            base = buildResolutionSummary(issue);
        }
        base = truncate(base, 200);
        if (title.isBlank()) {
            return base;
        }
        return (title + ": " + base).trim();
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class DecayResult {
        private int decayed;
        private List<Integer> updatedIssueIds = new ArrayList<>();

        public int getDecayed() {
            return decayed;
        }

        public List<Integer> getUpdatedIssueIds() {
            return updatedIssueIds;
        }
    }
}
