package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.MemoryItem;
import com.miniide.models.MemoryVersion;
import com.miniide.models.R5Event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Storage for memory items, versions (R1-R5), and raw events.
 * Implements the auto-level + escalation endpoints described in docs/reference/cr_librarian_extension.md.
 */
public class MemoryService {

    private static final String STORAGE_PATH = "data/memory-store.json";
    private static final int DEFAULT_LEVEL = 3;
    private static final long DEFAULT_LOCK_MILLIS = 90 * 60 * 1000L; // 90 minutes
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, MemoryItem> items = new ConcurrentHashMap<>();
    private final Map<String, List<MemoryVersion>> versionsByItem = new ConcurrentHashMap<>();
    private final Map<String, MemoryVersion> versionsById = new ConcurrentHashMap<>();
    private final Map<String, List<R5Event>> eventsByItem = new ConcurrentHashMap<>();
    private final Map<String, R5Event> eventsById = new ConcurrentHashMap<>();

    private final AtomicInteger memoryIdCounter = new AtomicInteger(0);
    private final AtomicInteger versionIdCounter = new AtomicInteger(0);
    private final AtomicInteger eventIdCounter = new AtomicInteger(0);

    private final AppLogger logger = AppLogger.get();

    public MemoryService() {
        loadFromDisk();
    }

    // ----- Public API used by controllers -----

    public boolean memoryExists(String memoryId) {
        return items.containsKey(memoryId);
    }

    public MemoryItem createMemoryItem(String agentId, String topicKey, Integer defaultLevel, Integer pinnedMinLevel) {
        String id = "mem-" + memoryIdCounter.incrementAndGet();
        long now = System.currentTimeMillis();

        MemoryItem item = new MemoryItem(id);
        item.setAgentId(agentId);
        item.setTopicKey(topicKey);
        item.setDefaultLevel(defaultLevel != null ? defaultLevel : DEFAULT_LEVEL);
        item.setPinnedMinLevel(pinnedMinLevel);
        item.setState("active");
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setLastAccessedAt(now);

        items.put(id, item);
        saveSnapshot();
        return item;
    }

    public MemoryVersion addVersion(String memoryId, int repLevel, String content, String derivationKind, String derivedFromVersionId) {
        MemoryItem item = items.get(memoryId);
        if (item == null) {
            throw new IllegalArgumentException("Memory item not found: " + memoryId);
        }
        long now = System.currentTimeMillis();
        String id = "ver-" + versionIdCounter.incrementAndGet();

        MemoryVersion version = new MemoryVersion(id, memoryId, repLevel, content, now);
        version.setDerivationKind(derivationKind);
        version.setDerivedFromVersionId(derivedFromVersionId);

        versionsById.put(id, version);
        versionsByItem.computeIfAbsent(memoryId, k -> new ArrayList<>()).add(version);
        versionsByItem.get(memoryId).sort(Comparator.comparingInt(MemoryVersion::getRepLevel));

        touch(item, now);
        saveSnapshot();
        return version;
    }

    public R5Event addEvent(String memoryId, String author, String agent, String text, Map<String, Object> meta) {
        MemoryItem item = items.get(memoryId);
        if (item == null) {
            throw new IllegalArgumentException("Memory item not found: " + memoryId);
        }

        long now = System.currentTimeMillis();
        int seq = nextSeq(memoryId);
        String id = "evt-" + eventIdCounter.incrementAndGet();

        R5Event event = new R5Event(id, memoryId, seq, now, author, agent, text);
        event.setMeta(meta);

        eventsById.put(id, event);
        eventsByItem.computeIfAbsent(memoryId, k -> new ArrayList<>()).add(event);
        eventsByItem.get(memoryId).sort(Comparator.comparingInt(R5Event::getSeq));

        touch(item, now);
        saveSnapshot();
        return event;
    }

    public MemoryResult getMemoryAtAutoLevel(String memoryId) {
        MemoryItem item = items.get(memoryId);
        if (item == null) {
            return null;
        }
        MemoryVersion version = selectAutoVersion(item);
        if (version == null) {
            return new MemoryResult(item, null, false);
        }
        touch(item, System.currentTimeMillis());
        return new MemoryResult(item, version, false);
    }

    public MemoryResult getMemoryAtNextLevel(String memoryId) {
        MemoryItem item = items.get(memoryId);
        if (item == null) {
            return null;
        }

        MemoryVersion base = selectAutoVersion(item);
        if (base == null) {
            return new MemoryResult(item, null, false);
        }

        List<MemoryVersion> versions = versionsByItem.getOrDefault(memoryId, List.of());
        versions.sort(Comparator.comparingInt(MemoryVersion::getRepLevel));

        MemoryVersion next = null;
        for (MemoryVersion v : versions) {
            if (v.getRepLevel() > base.getRepLevel()) {
                next = v;
                break;
            }
        }

        MemoryVersion chosen = next != null ? next : base;
        boolean escalated = next != null;
        touch(item, System.currentTimeMillis());
        return new MemoryResult(item, chosen, escalated);
    }

    public List<MemoryVersion> getVersions(String memoryId) {
        List<MemoryVersion> versions = new ArrayList<>(versionsByItem.getOrDefault(memoryId, List.of()));
        versions.sort(Comparator
            .comparingInt(MemoryVersion::getRepLevel)
            .thenComparing(MemoryVersion::getCreatedAt));
        return versions;
    }

    public R5Event getEvidence(String memoryId, String witness) {
        if (witness == null || witness.isBlank()) {
            return null;
        }

        R5Event byId = eventsById.get(witness);
        if (byId != null && memoryId.equals(byId.getMemoryItemId())) {
            return byId;
        }

        try {
            int seq = Integer.parseInt(witness.replace("seq:", "").trim());
            Optional<R5Event> match = eventsByItem
                .getOrDefault(memoryId, List.of())
                .stream()
                .filter(e -> e.getSeq() == seq)
                .findFirst();
            return match.orElse(null);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public boolean setActiveVersion(String memoryId, String versionId, long lockMillis, String reason) {
        MemoryItem item = items.get(memoryId);
        MemoryVersion version = versionsById.get(versionId);

        if (item == null || version == null || !memoryId.equals(version.getMemoryItemId())) {
            return false;
        }

        long now = System.currentTimeMillis();
        long lockUntil = now + (lockMillis > 0 ? lockMillis : DEFAULT_LOCK_MILLIS);

        item.setActiveVersionId(versionId);
        item.setActiveLockUntil(lockUntil);
        item.setActiveLockReason(reason != null && !reason.isBlank() ? reason : "manual-promote");
        touch(item, now);
        saveSnapshot();
        return true;
    }

    public boolean setPinnedMinLevel(String memoryId, Integer level) {
        MemoryItem item = items.get(memoryId);
        if (item == null) {
            return false;
        }
        item.setPinnedMinLevel(level);
        touch(item, System.currentTimeMillis());
        saveSnapshot();
        return true;
    }

    public boolean setState(String memoryId, String state) {
        MemoryItem item = items.get(memoryId);
        if (item == null) {
            return false;
        }
        item.setState(state);
        touch(item, System.currentTimeMillis());
        saveSnapshot();
        return true;
    }

    // ----- Internal helpers -----

    private MemoryVersion selectAutoVersion(MemoryItem item) {
        List<MemoryVersion> versions = versionsByItem.getOrDefault(item.getId(), List.of());
        if (versions.isEmpty()) {
            return null;
        }

        MemoryVersion active = null;
        if (item.getActiveVersionId() != null) {
            active = versionsById.get(item.getActiveVersionId());
        }
        if (active != null) {
            return active;
        }

        int targetLevel = item.getDefaultLevel() != null ? item.getDefaultLevel() : DEFAULT_LEVEL;
        if (item.getPinnedMinLevel() != null) {
            targetLevel = Math.max(targetLevel, item.getPinnedMinLevel());
        }

        MemoryVersion exact = versions.stream()
            .filter(v -> v.getRepLevel() == targetLevel)
            .findFirst()
            .orElse(null);
        if (exact != null) {
            return exact;
        }

        MemoryVersion higher = versions.stream()
            .filter(v -> v.getRepLevel() > targetLevel)
            .min(Comparator.comparingInt(MemoryVersion::getRepLevel))
            .orElse(null);
        if (higher != null) {
            return higher;
        }

        return versions.stream()
            .max(Comparator.comparingInt(MemoryVersion::getRepLevel))
            .orElse(null);
    }

    private int nextSeq(String memoryId) {
        return eventsByItem.getOrDefault(memoryId, List.of())
            .stream()
            .map(R5Event::getSeq)
            .max(Integer::compareTo)
            .orElse(0) + 1;
    }

    private void touch(MemoryItem item, long timestamp) {
        item.setUpdatedAt(timestamp);
        item.setLastAccessedAt(timestamp);
    }

    private void loadFromDisk() {
        Path path = Paths.get(STORAGE_PATH);
        if (!Files.exists(path)) {
            return;
        }

        try {
            MemoryStoreSnapshot snapshot = mapper.readValue(path.toFile(), MemoryStoreSnapshot.class);
            if (snapshot.items != null) {
                for (MemoryItem item : snapshot.items) {
                    items.put(item.getId(), item);
                    updateCounter(memoryIdCounter, item.getId());
                }
            }

            if (snapshot.versions != null) {
                for (MemoryVersion version : snapshot.versions) {
                    versionsById.put(version.getId(), version);
                    versionsByItem.computeIfAbsent(version.getMemoryItemId(), k -> new ArrayList<>()).add(version);
                    updateCounter(versionIdCounter, version.getId());
                }
                versionsByItem.values().forEach(list ->
                    list.sort(Comparator.comparingInt(MemoryVersion::getRepLevel)));
            }

            if (snapshot.events != null) {
                for (R5Event event : snapshot.events) {
                    eventsById.put(event.getId(), event);
                    eventsByItem.computeIfAbsent(event.getMemoryItemId(), k -> new ArrayList<>()).add(event);
                    updateCounter(eventIdCounter, event.getId());
                }
                eventsByItem.values().forEach(list ->
                    list.sort(Comparator.comparingInt(R5Event::getSeq)));
            }

            log("Loaded memory store: " + items.size() + " items, "
                + versionsById.size() + " versions, " + eventsById.size() + " events");
        } catch (Exception e) {
            logWarning("Failed to load memory store: " + e.getMessage());
        }
    }

    private void saveSnapshot() {
        MemoryStoreSnapshot snapshot = new MemoryStoreSnapshot();
        snapshot.items = new ArrayList<>(items.values());

        List<MemoryVersion> versions = new ArrayList<>();
        versionsByItem.values().forEach(versions::addAll);
        snapshot.versions = versions;

        List<R5Event> events = new ArrayList<>();
        eventsByItem.values().forEach(events::addAll);
        snapshot.events = events;

        try {
            Path filePath = Paths.get(STORAGE_PATH);
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), snapshot);
        } catch (IOException e) {
            logWarning("Failed to save memory store: " + e.getMessage());
        }
    }

    private void updateCounter(AtomicInteger counter, String id) {
        if (id == null) return;
        String numeric = id.replaceAll("\\D+", "");
        if (numeric.isBlank()) return;
        try {
            int parsed = Integer.parseInt(numeric);
            counter.set(Math.max(counter.get(), parsed));
        } catch (NumberFormatException ignored) {
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.info("[MemoryService] " + message);
        }
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.warn("[MemoryService] " + message);
        }
    }

    // ----- DTO -----
    public static class MemoryResult {
        private final MemoryItem item;
        private final MemoryVersion version;
        private final boolean escalated;

        public MemoryResult(MemoryItem item, MemoryVersion version, boolean escalated) {
            this.item = item;
            this.version = version;
            this.escalated = escalated;
        }

        public MemoryItem getItem() {
            return item;
        }

        public MemoryVersion getVersion() {
            return version;
        }

        public boolean isEscalated() {
            return escalated;
        }
    }

    // ----- Snapshot DTO for persistence -----
    private static class MemoryStoreSnapshot {
        public List<MemoryItem> items;
        public List<MemoryVersion> versions;
        public List<R5Event> events;
    }
}
