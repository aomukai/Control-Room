package com.miniide;

import com.miniide.models.Agent;
import com.miniide.models.AgentMemoryProfile;
import com.miniide.models.IssueMemoryRecord;
import com.miniide.storage.JsonStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IssueInterestService {
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long MONTH_MS = 30L * 24 * 60 * 60 * 1000;
    private static final long TWO_MONTHS_MS = 60L * 24 * 60 * 60 * 1000;
    private static final long THREE_MONTHS_MS = 90L * 24 * 60 * 60 * 1000;
    private static final int DEFAULT_INTEREST_LEVEL = 3;

    private final Map<String, IssueMemoryRecord> records = new ConcurrentHashMap<>();
    private final AgentRegistry agentRegistry;
    private Path storagePath;

    public IssueInterestService(Path workspaceRoot, AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
        switchWorkspace(workspaceRoot);
    }

    public synchronized void switchWorkspace(Path workspaceRoot) {
        this.storagePath = workspaceRoot.resolve(".control-room")
            .resolve("issues")
            .resolve("issue-memory.json");
        records.clear();
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
        Long lastAccess = record.getLastAccessedAt();
        record.setLastAccessedAt(now);
        record.setLastRefreshedAt(now);
        record.setAccessCount(record.getAccessCount() + 1);
        if (lastAccess != null && now - lastAccess <= WEEK_MS) {
            record.setInterestLevel(capInterest(agentId, record.getInterestLevel() + 1));
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
        record.setInterestLevel(capInterest(agentId, record.getInterestLevel() + 2));
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
        if (!record.getPersonalTags().contains("filtered-out")) {
            List<String> tags = new ArrayList<>(record.getPersonalTags());
            tags.add("filtered-out");
            record.setPersonalTags(tags);
        }
        touch(record, now);
        saveAll();
        return record;
    }

    public int decayAll() {
        int decayed = 0;
        for (IssueMemoryRecord record : records.values()) {
            if (record != null && decayRecord(record)) {
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
                if (decayRecord(record)) {
                    decayed++;
                }
            }
        }
        if (decayed > 0) {
            saveAll();
        }
        return decayed;
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
        record.setInterestLevel(capInterest(agentId, DEFAULT_INTEREST_LEVEL));
        record.setAccessCount(0);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        records.put(key, record);
        return record;
    }

    private boolean decayRecord(IssueMemoryRecord record) {
        if (record == null) {
            return false;
        }
        int level = normalizeLevel(record.getInterestLevel());
        if (level >= 5) {
            return false;
        }
        Long refreshedAt = record.getLastRefreshedAt();
        if (refreshedAt == null) {
            refreshedAt = record.getLastAccessedAt();
        }
        if (refreshedAt == null) {
            refreshedAt = record.getCreatedAt();
        }
        long now = System.currentTimeMillis();
        long age = refreshedAt != null ? now - refreshedAt : 0;
        int nextLevel = level;
        if (level == 4 && age > MONTH_MS) {
            nextLevel = 3;
        } else if (level == 3 && age > TWO_MONTHS_MS) {
            nextLevel = 2;
        } else if (level == 2 && age > THREE_MONTHS_MS) {
            nextLevel = 1;
        }

        if (nextLevel != level) {
            record.setInterestLevel(nextLevel);
            record.setLastRefreshedAt(now);
            touch(record, now);
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

    private void touch(IssueMemoryRecord record, long timestamp) {
        record.setUpdatedAt(timestamp);
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(timestamp);
        }
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
    }

    private void saveAll() {
        if (storagePath == null) {
            return;
        }
        try {
            Files.createDirectories(storagePath.getParent());
            List<IssueMemoryRecord> data = new ArrayList<>(records.values());
            JsonStorage.writeJsonList(storagePath.toString(), data);
        } catch (Exception ignored) {
        }
    }
}
