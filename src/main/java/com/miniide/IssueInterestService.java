package com.miniide;

import com.miniide.models.Agent;
import com.miniide.models.AgentMemoryProfile;
import com.miniide.models.Issue;
import com.miniide.models.IssueAgentActivation;
import com.miniide.models.IssueMemoryRecord;
import com.miniide.storage.JsonStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class IssueInterestService {
    private static final int DEFAULT_INTEREST_LEVEL = 3;
    private static final int EPOCH_BASE_AGE = 50;
    private static final int LEECH_ACCESS_THRESHOLD = 3;
    private static final int LEECH_ACCESS_WINDOW = 20;
    private static final Map<Integer, Integer> DECAY_THRESHOLDS = Map.of(
        5, 30,
        4, 60,
        3, 120,
        2, 200,
        1, Integer.MAX_VALUE
    );
    private static final Map<String, Double> EPOCH_MULTIPLIERS = Map.of(
        "chapter_complete", 3.0,
        "act_complete", 5.0,
        "draft_complete", 2.0,
        "character_arc_resolved", 5.0,
        "worldbuilding_locked", 0.5
    );

    private final Map<String, IssueMemoryRecord> records = new ConcurrentHashMap<>();
    private final Map<String, Integer> agentActivationCounts = new ConcurrentHashMap<>();
    private final AgentRegistry agentRegistry;
    private IssueMemoryService issueMemoryService;
    private TelemetryStore telemetryStore;
    private NotificationStore notificationStore;
    private Path storagePath;
    private Path activationPath;

    public IssueInterestService(Path workspaceRoot, AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
        switchWorkspace(workspaceRoot);
    }

    public void setIssueMemoryService(IssueMemoryService issueMemoryService) {
        this.issueMemoryService = issueMemoryService;
    }

    public void setTelemetryStore(TelemetryStore telemetryStore) {
        this.telemetryStore = telemetryStore;
    }

    public void setNotificationStore(NotificationStore notificationStore) {
        this.notificationStore = notificationStore;
    }

    public synchronized void switchWorkspace(Path workspaceRoot) {
        this.storagePath = workspaceRoot.resolve(".control-room")
            .resolve("issues")
            .resolve("issue-memory.json");
        this.activationPath = workspaceRoot.resolve(".control-room")
            .resolve("issues")
            .resolve("issue-activations.json");
        records.clear();
        agentActivationCounts.clear();
        loadFromDisk();
    }

    public IssueMemoryRecord getRecord(String agentId, int issueId) {
        if (agentId == null || agentId.isBlank() || issueId <= 0) {
            return null;
        }
        return records.get(key(agentId, issueId));
    }

    public List<IssueMemoryRecord> listForAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Collections.emptyList();
        }
        List<IssueMemoryRecord> results = new ArrayList<>();
        for (IssueMemoryRecord record : records.values()) {
            if (record != null && agentId.equalsIgnoreCase(record.getAgentId())) {
                results.add(record);
            }
        }
        results.sort(Comparator.comparingInt(IssueMemoryRecord::getIssueId));
        return results;
    }

    public IssueMemoryRecord recordAccess(String agentId, int issueId) {
        IssueMemoryRecord record = getOrCreate(agentId, issueId);
        long now = System.currentTimeMillis();
        record.setLastAccessedAt(now);
        record.setLastRefreshedAt(now);
        record.setAccessCount(record.getAccessCount() + 1);
        long activationCount = getAgentActivationCount(agentId);
        record.setLastAccessedAtActivation(activationCount);
        updateAccessWindow(record, activationCount);
        int floor = calculateFloor(agentId, issueId);
        int desired = Math.max(3, floor);
        if (isLeechLocked(record) || isDeferred(record)) {
            record.setInterestLevel(1);
        } else {
            record.setInterestLevel(Math.max(floor, capInterest(agentId, desired)));
        }
        evaluateLeechCandidate(record);
        if (telemetryStore != null) {
            telemetryStore.recordIssueAccess(agentId);
        }
        touch(record, now);
        saveAll();
        return record;
    }

    public IssueMemoryRecord recordApplied(String agentId, int issueId) {
        IssueMemoryRecord record = getOrCreate(agentId, issueId);
        long now = System.currentTimeMillis();
        record.setAppliedInWork(true);
        record.setWasUseful(true);
        record.setLastRefreshedAt(now);
        long activationCount = getAgentActivationCount(agentId);
        record.setLastAccessedAtActivation(activationCount);
        int floor = calculateFloor(agentId, issueId);
        int boosted = record.getInterestLevel() + 2;
        if (isLeechLocked(record) || isDeferred(record)) {
            record.setInterestLevel(1);
        } else {
            record.setInterestLevel(Math.max(floor, capInterest(agentId, Math.max(floor, boosted))));
        }
        evaluateLeechCandidate(record);
        if (telemetryStore != null) {
            telemetryStore.recordIssueAccess(agentId);
        }
        touch(record, now);
        saveAll();
        return record;
    }

    public IssueMemoryRecord markIrrelevant(String agentId, int issueId, String note) {
        IssueMemoryRecord record = getOrCreate(agentId, issueId);
        long now = System.currentTimeMillis();
        record.setWasUseful(false);
        record.setAppliedInWork(false);
        record.setInterestLevel(1);
        record.setLastRefreshedAt(now);
        record.setNote(note);
        clearDeferral(record);
        clearLeechReview(record, now, note);
        List<String> tags = new ArrayList<>(record.getPersonalTags());
        if (!tags.contains("filtered-out")) {
            tags.add("filtered-out");
        }
        record.setPersonalTags(normalizePersonalTags(tags));
        touch(record, now);
        saveAll();
        return record;
    }

    public IssueMemoryRecord updatePersonalTags(String agentId, int issueId, List<String> tags, String note) {
        IssueMemoryRecord record = getOrCreate(agentId, issueId);
        long now = System.currentTimeMillis();
        record.setPersonalTags(normalizePersonalTags(tags));
        if (note != null) {
            record.setNote(note);
        }
        record.setLastRefreshedAt(now);
        touch(record, now);
        saveAll();
        return record;
    }

    public List<Integer> listIssueIdsForAgentWithTags(String agentId, List<String> includeTags, List<String> excludeTags) {
        if (agentId == null || agentId.isBlank()) {
            return Collections.emptyList();
        }
        List<String> include = normalizePersonalTags(includeTags);
        List<String> exclude = normalizePersonalTags(excludeTags);
        List<Integer> results = new ArrayList<>();
        for (IssueMemoryRecord record : listForAgent(agentId)) {
            if (record == null) {
                continue;
            }
            List<String> tags = normalizePersonalTags(record.getPersonalTags());
            if (!include.isEmpty()) {
                boolean hasAny = false;
                for (String tag : include) {
                    if (tags.contains(tag)) {
                        hasAny = true;
                        break;
                    }
                }
                if (!hasAny) {
                    continue;
                }
            }
            if (!exclude.isEmpty()) {
                boolean blocked = false;
                for (String tag : exclude) {
                    if (tags.contains(tag)) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) {
                    continue;
                }
            }
            results.add(record.getIssueId());
        }
        return results;
    }

    public int decayAll() {
        int decayed = 0;
        for (IssueMemoryRecord record : records.values()) {
            if (record != null && decayRecord(record, getAgentActivationCount(record.getAgentId()))) {
                decayed++;
            }
        }
        if (decayed > 0) {
            saveAll();
        }
        return decayed;
    }

    public int decayAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return 0;
        }
        int decayed = 0;
        for (IssueMemoryRecord record : records.values()) {
            if (record != null && agentId.equalsIgnoreCase(record.getAgentId())) {
                if (decayRecord(record, getAgentActivationCount(agentId))) {
                    decayed++;
                }
            }
        }
        if (decayed > 0) {
            saveAll();
        }
        return decayed;
    }

    public int recordAgentActivation(String agentId) {
        return recordAgentActivations(agentId, 1);
    }

    public int recordAgentActivations(String agentId, int count) {
        if (agentId == null || agentId.isBlank()) {
            return 0;
        }
        int safeCount = Math.max(1, count);
        int next = agentActivationCounts.merge(agentId.toLowerCase(), safeCount, Integer::sum);
        if (telemetryStore != null) {
            telemetryStore.recordActivation(agentId, safeCount);
        }
        saveAll();
        decayAgent(agentId);
        return next;
    }

    public int getAgentActivationCount(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return 0;
        }
        return agentActivationCounts.getOrDefault(agentId.toLowerCase(), 0);
    }

    public int triggerEpoch(String epochType, List<String> affectedTags) {
        List<String> epochTypes = new ArrayList<>();
        if (epochType != null && !epochType.isBlank()) {
            epochTypes.add(epochType);
        }
        return triggerEpoch(epochTypes, affectedTags);
    }

    public int triggerEpoch(List<String> epochTypes, List<String> affectedTags) {
        if (epochTypes == null || epochTypes.isEmpty()) {
            return 0;
        }
        double multiplier = resolveEpochMultiplier(epochTypes);
        if (multiplier <= 0) {
            return 0;
        }
        List<String> tags = normalizePersonalTags(affectedTags);
        if (tags.isEmpty()) {
            return 0;
        }
        int demoted = 0;
        for (IssueMemoryRecord record : records.values()) {
            if (record == null) {
                continue;
            }
            if (!issueHasAnyTag(record.getIssueId(), tags)) {
                continue;
            }
            long activationCount = getAgentActivationCount(record.getAgentId());
            long artificialAge = Math.round(EPOCH_BASE_AGE * multiplier);
            if (applyEpochDecay(record, activationCount, artificialAge)) {
                demoted++;
            }
        }
        if (demoted > 0) {
            saveAll();
        }
        return demoted;
    }

    public List<IssueMemoryRecord> listLeechCandidates(String agentId) {
        List<IssueMemoryRecord> results = new ArrayList<>();
        for (IssueMemoryRecord record : records.values()) {
            if (record == null) {
                continue;
            }
            if (agentId != null && !agentId.isBlank() && !agentId.equalsIgnoreCase(record.getAgentId())) {
                continue;
            }
            if (Boolean.TRUE.equals(record.getLeechReviewPending())) {
                results.add(record);
            }
        }
        results.sort(Comparator.comparingInt(IssueMemoryRecord::getIssueId));
        return results;
    }

    public IssueMemoryRecord recordContradictionSignal(String agentId, int issueId, int contradictionIssueId, String note) {
        if (contradictionIssueId <= 0) {
            throw new IllegalArgumentException("contradictionIssueId is required");
        }
        IssueMemoryRecord record = getOrCreate(agentId, issueId);
        List<Integer> contradictionIds = new ArrayList<>(record.getLeechContradictionIssueIds());
        if (!contradictionIds.contains(contradictionIssueId)) {
            contradictionIds.add(contradictionIssueId);
        }
        record.setLeechContradictionIssueIds(contradictionIds);
        if (note != null && !note.isBlank()) {
            record.setLeechNote(note);
        }
        long now = System.currentTimeMillis();
        long activationCount = getAgentActivationCount(agentId);
        evaluateLeechCandidate(record);
        touch(record, now);
        saveAll();
        return record;
    }

    public IssueMemoryRecord confirmLeech(String agentId, int issueId, String confirmedBy, String note) {
        IssueMemoryRecord record = getOrCreate(agentId, issueId);
        long now = System.currentTimeMillis();
        record.setLeechMarked(true);
        record.setLeechReviewPending(false);
        record.setLeechConfirmedAt(now);
        if (confirmedBy != null && !confirmedBy.isBlank()) {
            record.setLeechConfirmedBy(confirmedBy);
        }
        if (note != null && !note.isBlank()) {
            record.setLeechNote(note);
        }
        record.setInterestLevel(1);
        clearDeferral(record);
        touch(record, now);
        saveAll();
        return record;
    }

    public IssueMemoryRecord dismissLeech(String agentId, int issueId, String note) {
        IssueMemoryRecord record = getOrCreate(agentId, issueId);
        long now = System.currentTimeMillis();
        record.setLeechReviewPending(false);
        record.setLeechDismissedAt(now);
        if (note != null && !note.isBlank()) {
            record.setLeechNote(note);
        }
        touch(record, now);
        saveAll();
        return record;
    }

    public IssueMemoryRecord deferAccess(String agentId, int issueId, String triggerType, String triggerValue,
                                         Integer escalateTo, Boolean notify, String message, String reason, String deferredBy) {
        if (triggerType == null || triggerType.isBlank()) {
            throw new IllegalArgumentException("triggerType is required");
        }
        if (triggerValue == null || triggerValue.isBlank()) {
            throw new IllegalArgumentException("triggerValue is required");
        }
        IssueMemoryRecord record = getOrCreate(agentId, issueId);
        long now = System.currentTimeMillis();
        record.setDeferredAccess(true);
        record.setDeferredTriggerType(triggerType.trim().toLowerCase());
        record.setDeferredTriggerValue(triggerValue.trim());
        record.setDeferredEscalateTo(escalateTo != null ? normalizeLevel(escalateTo) : 3);
        record.setDeferredNotify(Boolean.TRUE.equals(notify));
        record.setDeferredMessage(message);
        record.setDeferredReason(reason);
        record.setDeferredAt(now);
        record.setDeferredBy(deferredBy);
        record.setInterestLevel(1);
        record.setLeechReviewPending(false);
        touch(record, now);
        saveAll();
        return record;
    }

    public int triggerDeferrals(String triggerType, String triggerValue, String agentId) {
        if (triggerType == null || triggerType.isBlank() || triggerValue == null || triggerValue.isBlank()) {
            return 0;
        }
        String normalizedType = triggerType.trim().toLowerCase();
        int updated = 0;
        for (IssueMemoryRecord record : records.values()) {
            if (record == null || !Boolean.TRUE.equals(record.getDeferredAccess())) {
                continue;
            }
            if (agentId != null && !agentId.isBlank() && !agentId.equalsIgnoreCase(record.getAgentId())) {
                continue;
            }
            if (!matchesTrigger(record, normalizedType, triggerValue.trim())) {
                continue;
            }
            if (isLeechLocked(record)) {
                continue;
            }
            int floor = calculateFloor(record.getAgentId(), record.getIssueId());
            int target = record.getDeferredEscalateTo() != null ? record.getDeferredEscalateTo() : 3;
            record.setInterestLevel(Math.max(floor, capInterest(record.getAgentId(), target)));
            boolean notify = Boolean.TRUE.equals(record.getDeferredNotify());
            String message = record.getDeferredMessage();
            clearDeferral(record);
            touch(record, System.currentTimeMillis());
            if (notify) {
                notifyDeferredTriggered(record, message);
            }
            updated++;
        }
        if (updated > 0) {
            saveAll();
        }
        return updated;
    }

    public IssueMemoryRecord approveAccess(String agentId, int issueId, int level, String approvedBy, String note) {
        IssueMemoryRecord record = getOrCreate(agentId, issueId);
        int target = Math.max(1, Math.min(5, level));
        int floor = calculateFloor(agentId, issueId);
        record.setInterestLevel(Math.max(floor, capInterest(agentId, target)));
        clearDeferral(record);
        if (approvedBy != null && !approvedBy.isBlank()) {
            record.setLeechConfirmedBy(approvedBy);
        }
        if (note != null && !note.isBlank()) {
            record.setLeechNote(note);
        }
        record.setLastRefreshedAt(System.currentTimeMillis());
        touch(record, System.currentTimeMillis());
        saveAll();
        return record;
    }

    private IssueMemoryRecord getOrCreate(String agentId, int issueId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        if (issueId <= 0) {
            throw new IllegalArgumentException("issueId is required");
        }
        String key = key(agentId, issueId);
        IssueMemoryRecord existing = records.get(key);
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        IssueMemoryRecord record = new IssueMemoryRecord();
        record.setAgentId(agentId);
        record.setIssueId(issueId);
        int floor = calculateFloor(agentId, issueId);
        record.setInterestLevel(Math.max(floor, capInterest(agentId, DEFAULT_INTEREST_LEVEL)));
        record.setAccessCount(0);
        record.setLastAccessedAtActivation((long) getAgentActivationCount(agentId));
        record.setAccessWindowCount(0);
        record.setAccessWindowStartActivation((long) getAgentActivationCount(agentId));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        records.put(key, record);
        return record;
    }

    private boolean decayRecord(IssueMemoryRecord record, long activationCount) {
        if (record == null) {
            return false;
        }
        if (isLeechLocked(record) || isDeferred(record)) {
            return false;
        }
        int level = normalizeLevel(record.getInterestLevel());
        if (level <= 1) {
            return false;
        }
        int floor = calculateFloor(record.getAgentId(), record.getIssueId());
        if (level <= floor) {
            return false;
        }
        long lastAccessActivation = record.getLastAccessedAtActivation() != null
            ? record.getLastAccessedAtActivation()
            : 0L;
        long activationsSince = Math.max(0L, activationCount - lastAccessActivation);
        int nextLevel = level;
        while (nextLevel > floor) {
            int threshold = DECAY_THRESHOLDS.getOrDefault(nextLevel, Integer.MAX_VALUE);
            if (activationsSince < threshold) {
                break;
            }
            nextLevel -= 1;
        }

        if (nextLevel != level) {
            record.setInterestLevel(nextLevel);
            touch(record, System.currentTimeMillis());
            if (telemetryStore != null) {
                telemetryStore.recordIssueDemotion(record.getAgentId());
            }
            return true;
        }
        return false;
    }

    private int capInterest(String agentId, int level) {
        int capped = normalizeLevel(level);
        Agent agent = agentRegistry != null ? agentRegistry.getAgent(agentId) : null;
        AgentMemoryProfile profile = agent != null ? agent.getMemoryProfile() : null;
        if (profile != null && profile.getMaxInterestLevel() != null) {
            int maxLevel = normalizeLevel(profile.getMaxInterestLevel());
            return Math.min(capped, maxLevel);
        }
        return capped;
    }

    private int normalizeLevel(int level) {
        return Math.max(1, Math.min(5, level));
    }

    private int calculateFloor(String agentId, int issueId) {
        List<String> tags = normalizePersonalTags(resolveIssueTags(issueId));
        if (tags.contains("canon") || tags.contains("worldbuilding") || tags.contains("character_core") || tags.contains("character-core")) {
            return 3;
        }
        String role = resolveAgentRole(agentId);
        if (role == null) {
            return 1;
        }
        Map<String, Integer> roleFloors = roleFloorMap().get(role);
        if (roleFloors == null) {
            return 1;
        }
        for (String tag : tags) {
            String normalized = normalizeTagKey(tag);
            Integer floor = roleFloors.get(tag);
            if (floor == null) {
                floor = roleFloors.get(normalized);
            }
            if (floor != null) {
                return floor;
            }
        }
        return 1;
    }

    private Map<String, Map<String, Integer>> roleFloorMap() {
        Map<String, Map<String, Integer>> floors = new HashMap<>();
        floors.put("planner", Map.of(
            "plot_point", 3,
            "plot-point", 3,
            "foreshadowing", 3,
            "timeline", 3
        ));
        floors.put("writer", Map.of(
            "plot_point", 3,
            "plot-point", 3,
            "foreshadowing", 3,
            "character_state", 3,
            "character-state", 3
        ));
        floors.put("continuity", Map.of(
            "plot_point", 3,
            "plot-point", 3,
            "timeline", 3,
            "character_state", 3,
            "character-state", 3,
            "canon", 3
        ));
        floors.put("critic", Map.of(
            "plot_point", 2,
            "plot-point", 2,
            "foreshadowing", 2
        ));
        floors.put("editor", Map.of(
            "style_guide", 3,
            "style-guide", 3
        ));
        return floors;
    }

    private String normalizeTagKey(String tag) {
        if (tag == null) {
            return null;
        }
        return tag.trim().toLowerCase().replace('-', '_');
    }

    private String resolveAgentRole(String agentId) {
        if (agentRegistry == null || agentId == null) {
            return null;
        }
        Agent agent = agentRegistry.getAgent(agentId);
        if (agent == null || agent.getRole() == null) {
            return null;
        }
        return agent.getRole().trim().toLowerCase();
    }

    private List<String> resolveIssueTags(int issueId) {
        if (issueMemoryService == null || issueId <= 0) {
            return new ArrayList<>();
        }
        Issue issue = issueMemoryService.getIssue(issueId).orElse(null);
        if (issue == null || issue.getTags() == null) {
            return new ArrayList<>();
        }
        List<String> tags = new ArrayList<>();
        for (String tag : issue.getTags()) {
            if (tag != null && !tag.isBlank()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private boolean issueHasAnyTag(int issueId, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        List<String> issueTags = normalizePersonalTags(resolveIssueTags(issueId));
        for (String tag : tags) {
            if (issueTags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private boolean applyEpochDecay(IssueMemoryRecord record, long activationCount, long artificialAge) {
        if (record == null) {
            return false;
        }
        if (isLeechLocked(record) || isDeferred(record)) {
            return false;
        }
        int level = normalizeLevel(record.getInterestLevel());
        if (level <= 1) {
            return false;
        }
        int floor = calculateFloor(record.getAgentId(), record.getIssueId());
        if (level <= floor) {
            return false;
        }
        long lastAccessActivation = record.getLastAccessedAtActivation() != null
            ? record.getLastAccessedAtActivation()
            : 0L;
        long activationsSince = Math.max(0L, activationCount - lastAccessActivation);
        long effectiveAge = activationsSince + Math.max(0L, artificialAge);

        int nextLevel = level;
        while (nextLevel > floor) {
            int threshold = DECAY_THRESHOLDS.getOrDefault(nextLevel, Integer.MAX_VALUE);
            if (effectiveAge < threshold) {
                break;
            }
            nextLevel -= 1;
        }
        if (nextLevel != level) {
            record.setInterestLevel(nextLevel);
            touch(record, System.currentTimeMillis());
            if (telemetryStore != null) {
                telemetryStore.recordIssueDemotion(record.getAgentId());
            }
            return true;
        }
        return false;
    }

    private void touch(IssueMemoryRecord record, long timestamp) {
        record.setUpdatedAt(timestamp);
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(timestamp);
        }
    }

    private void updateAccessWindow(IssueMemoryRecord record, long activationCount) {
        Long windowStart = record.getAccessWindowStartActivation();
        Integer windowCount = record.getAccessWindowCount();
        if (windowStart == null || windowCount == null) {
            record.setAccessWindowStartActivation(activationCount);
            record.setAccessWindowCount(1);
            return;
        }
        long delta = activationCount - windowStart;
        if (delta > LEECH_ACCESS_WINDOW) {
            record.setAccessWindowStartActivation(activationCount);
            record.setAccessWindowCount(1);
            return;
        }
        record.setAccessWindowCount(windowCount + 1);
    }

    private void evaluateLeechCandidate(IssueMemoryRecord record) {
        if (record == null) {
            return;
        }
        if (isLeechLocked(record) || Boolean.TRUE.equals(record.getLeechReviewPending())) {
            return;
        }
        if (!hasContradictionSignal(record)) {
            return;
        }
        Integer windowCount = record.getAccessWindowCount();
        if (windowCount == null || windowCount < LEECH_ACCESS_THRESHOLD) {
            return;
        }
        record.setLeechReviewPending(true);
        record.setLeechFlaggedAt(System.currentTimeMillis());
        notifyLeechReview(record);
    }

    private boolean hasContradictionSignal(IssueMemoryRecord record) {
        return record.getLeechContradictionIssueIds() != null && !record.getLeechContradictionIssueIds().isEmpty();
    }

    private void notifyLeechReview(IssueMemoryRecord record) {
        if (notificationStore == null) {
            return;
        }
        String message = "Potential leech: " + record.getAgentId() + " + Issue #" + record.getIssueId();
        String details = "Access window: " + (record.getAccessWindowCount() != null ? record.getAccessWindowCount() : 0)
            + " over " + LEECH_ACCESS_WINDOW + " activations. Contradiction issue(s): "
            + record.getLeechContradictionIssueIds();
        notificationStore.push(
            com.miniide.models.Notification.Level.WARNING,
            com.miniide.models.Notification.Scope.WORKBENCH,
            message,
            details,
            com.miniide.models.Notification.Category.ATTENTION,
            true,
            "Review",
            Map.of("kind", "openIssue", "issueId", record.getIssueId()),
            "issue-memory"
        );
    }

    private void notifyDeferredTriggered(IssueMemoryRecord record, String message) {
        if (notificationStore == null) {
            return;
        }
        String base = "Wiedervorlage: Issue #" + record.getIssueId() + " reopened for " + record.getAgentId();
        notificationStore.push(
            com.miniide.models.Notification.Level.INFO,
            com.miniide.models.Notification.Scope.WORKBENCH,
            base,
            message,
            com.miniide.models.Notification.Category.INFO,
            false,
            "Open",
            Map.of("kind", "openIssue", "issueId", record.getIssueId()),
            "issue-memory"
        );
    }

    private void clearDeferral(IssueMemoryRecord record) {
        record.setDeferredAccess(false);
        record.setDeferredTriggerType(null);
        record.setDeferredTriggerValue(null);
        record.setDeferredEscalateTo(null);
        record.setDeferredNotify(null);
        record.setDeferredMessage(null);
        record.setDeferredReason(null);
        record.setDeferredAt(null);
        record.setDeferredBy(null);
    }

    private void clearLeechReview(IssueMemoryRecord record, long now, String note) {
        record.setLeechReviewPending(false);
        record.setLeechDismissedAt(now);
        if (note != null && !note.isBlank()) {
            record.setLeechNote(note);
        }
    }

    private boolean isLeechLocked(IssueMemoryRecord record) {
        return record != null && Boolean.TRUE.equals(record.getLeechMarked());
    }

    private boolean isDeferred(IssueMemoryRecord record) {
        return record != null && Boolean.TRUE.equals(record.getDeferredAccess());
    }

    private boolean matchesTrigger(IssueMemoryRecord record, String triggerType, String triggerValue) {
        if (record == null) {
            return false;
        }
        String storedType = record.getDeferredTriggerType();
        String storedValue = record.getDeferredTriggerValue();
        if (storedType == null || storedValue == null) {
            return false;
        }
        if (!storedType.equalsIgnoreCase(triggerType)) {
            return false;
        }
        switch (storedType) {
            case "scene_reached":
                return compareNumericTrigger(storedValue, triggerValue);
            case "milestone":
            case "tag_appeared":
                return storedValue.equalsIgnoreCase(triggerValue);
            default:
                return false;
        }
    }

    private boolean compareNumericTrigger(String storedValue, String triggerValue) {
        try {
            long stored = Long.parseLong(storedValue.trim());
            long current = Long.parseLong(triggerValue.trim());
            return current >= stored;
        } catch (NumberFormatException e) {
            return storedValue.equalsIgnoreCase(triggerValue);
        }
    }

    private double resolveEpochMultiplier(List<String> epochTypes) {
        double multiplier = 1.0;
        for (String type : epochTypes) {
            if (type == null || type.isBlank()) {
                continue;
            }
            double candidate = EPOCH_MULTIPLIERS.getOrDefault(type.trim().toLowerCase(), 1.0);
            if (candidate > multiplier) {
                multiplier = candidate;
            }
        }
        return multiplier;
    }

    private List<String> normalizePersonalTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim().toLowerCase();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private String key(String agentId, int issueId) {
        return agentId.toLowerCase() + ":" + issueId;
    }

    private void loadFromDisk() {
        if (storagePath == null || !Files.exists(storagePath)) {
            return;
        }
        try {
            List<IssueMemoryRecord> stored = JsonStorage.readJsonList(storagePath.toString(), IssueMemoryRecord[].class);
            for (IssueMemoryRecord record : stored) {
                if (record == null) {
                    continue;
                }
                String key = key(record.getAgentId(), record.getIssueId());
                records.put(key, record);
            }
        } catch (Exception ignored) {
        }
        if (activationPath == null || !Files.exists(activationPath)) {
            return;
        }
        try {
            List<IssueAgentActivation> stored = JsonStorage.readJsonList(activationPath.toString(), IssueAgentActivation[].class);
            for (IssueAgentActivation activation : stored) {
                if (activation == null || activation.getAgentId() == null) {
                    continue;
                }
                agentActivationCounts.put(activation.getAgentId().toLowerCase(), activation.getActivationCount());
            }
        } catch (Exception ignored) {
        }
    }

    private void saveAll() {
        if (storagePath == null) {
            return;
        }
        try {
            Files.createDirectories(storagePath.getParent());
            List<IssueMemoryRecord> data = new ArrayList<>(records.values());
            JsonStorage.writeJsonList(storagePath.toString(), data);
            saveActivationCounts();
        } catch (Exception ignored) {
        }
    }

    private void saveActivationCounts() {
        if (activationPath == null) {
            return;
        }
        try {
            Files.createDirectories(activationPath.getParent());
            List<IssueAgentActivation> data = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : agentActivationCounts.entrySet()) {
                IssueAgentActivation activation = new IssueAgentActivation();
                activation.setAgentId(entry.getKey());
                activation.setActivationCount(entry.getValue());
                activation.setUpdatedAt(System.currentTimeMillis());
                data.add(activation);
            }
            JsonStorage.writeJsonList(activationPath.toString(), data);
        } catch (Exception ignored) {
        }
    }
}
