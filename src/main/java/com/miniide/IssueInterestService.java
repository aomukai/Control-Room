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
    private Path storagePath;
    private Path activationPath;

    public IssueInterestService(Path workspaceRoot, AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
        switchWorkspace(workspaceRoot);
    }

    public void setIssueMemoryService(IssueMemoryService issueMemoryService) {
        this.issueMemoryService = issueMemoryService;
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
        int floor = calculateFloor(agentId, issueId);
        int desired = Math.max(3, floor);
        record.setInterestLevel(Math.max(floor, capInterest(agentId, desired)));
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
        record.setInterestLevel(Math.max(floor, capInterest(agentId, Math.max(floor, boosted))));
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
        if (epochType == null || epochType.isBlank()) {
            return 0;
        }
        double multiplier = EPOCH_MULTIPLIERS.getOrDefault(epochType.trim().toLowerCase(), 1.0);
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
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        records.put(key, record);
        return record;
    }

    private boolean decayRecord(IssueMemoryRecord record, long activationCount) {
        if (record == null) {
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
