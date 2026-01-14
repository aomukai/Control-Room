package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.MemoryItem;
import com.miniide.models.MemoryVersion;
import com.miniide.models.R5Event;

import java.io.IOException;
import java.util.Collection;
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
    private final Map<String, Integer> agentActivationCounts = new ConcurrentHashMap<>();

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

    public MemoryItem getMemoryItem(String memoryId) {
        if (memoryId == null) {
            return null;
        }
        return items.get(memoryId);
    }

    public MemoryItem createMemoryItem(String agentId, String topicKey, Integer defaultLevel, Integer pinnedMinLevel,
                                       List<String> tags) {
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
        item.setLastAccessedActivation((long) currentActivationCount(agentId));
        item.setTotalAccessCount(0);
        item.setTags(tags != null ? new ArrayList<>(tags) : new ArrayList<>());

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

        touch(item, now, false);
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

        touch(item, now, false);
        saveSnapshot();
        return event;
    }

    public int recordAgentActivation(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return 0;
        }
        int next = agentActivationCounts.merge(agentId, 1, Integer::sum);
        saveSnapshot();
        return next;
    }

    public int getAgentActivationCount(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return 0;
        }
        return agentActivationCounts.getOrDefault(agentId, 0);
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
        touch(item, System.currentTimeMillis(), true);
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
        touch(item, System.currentTimeMillis(), true);
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
        touch(item, now, false);
        saveSnapshot();
        return true;
    }

    public boolean setPinnedMinLevel(String memoryId, Integer level) {
        MemoryItem item = items.get(memoryId);
        if (item == null) {
            return false;
        }
        item.setPinnedMinLevel(level);
        touch(item, System.currentTimeMillis(), false);
        saveSnapshot();
        return true;
    }

    public boolean setState(String memoryId, String state) {
        MemoryItem item = items.get(memoryId);
        if (item == null) {
            return false;
        }
        item.setState(state);
        touch(item, System.currentTimeMillis(), false);
        saveSnapshot();
        return true;
    }

    public DecayResult runDecay(DecaySettings settings) {
        return runDecay(settings, false);
    }

    public DecayResult runDecay(DecaySettings settings, boolean dryRun) {
        long now = System.currentTimeMillis();
        DecayResult result = new DecayResult();

        long archiveAfterMs = settings.getArchiveAfterMs();
        long expireAfterMs = settings.getExpireAfterMs();
        boolean pruneExpiredR5 = settings.isPruneExpiredR5();
        boolean collectReport = dryRun || settings.isCollectReport();
        List<String> excludeTopicKeys = settings.getExcludeTopicKeys();
        List<String> excludeAgentIds = settings.getExcludeAgentIds();
        Map<Integer, Integer> decayThresholds = settings.getDecayThresholds();
        Map<String, Double> relevanceWeights = settings.getRelevanceWeights();
        Map<String, Double> epochMultipliers = settings.getEpochMultipliers();
        double defaultRelevanceWeight = settings.getDefaultRelevanceWeight();
        double globalEpochMultiplier = settings.getGlobalEpochMultiplier();

        for (MemoryItem item : items.values()) {
            if (isExcluded(item, excludeTopicKeys, excludeAgentIds)) {
                result.filteredItems++;
                if (collectReport) {
                    result.filteredIds.add(item.getId());
                }
                continue;
            }

            if (settings.isUseActivationDecay()) {
                int currentLevel = normalizeLevel(item.getDefaultLevel());
                int floorLevel = item.getPinnedMinLevel() != null
                    ? Math.max(1, item.getPinnedMinLevel())
                    : 1;
                if (currentLevel < floorLevel) {
                    currentLevel = floorLevel;
                }

                int activationCount = getAgentActivationCount(item.getAgentId());
                long lastAccessActivation = item.getLastAccessedActivation() != null
                    ? item.getLastAccessedActivation()
                    : 0L;
                long activationsSince = Math.max(0L, activationCount - lastAccessActivation);
                int accessCount = item.getTotalAccessCount() != null ? item.getTotalAccessCount() : 0;

                double relevanceWeight = resolveRelevanceWeight(item, relevanceWeights, defaultRelevanceWeight);
                double epochMultiplier = resolveEpochMultiplier(item, epochMultipliers, globalEpochMultiplier);

                double decayScore = (activationsSince * relevanceWeight * epochMultiplier)
                    / (accessCount + 1.0);

                int nextLevel = currentLevel;
                while (nextLevel > floorLevel) {
                    int threshold = decayThresholds.getOrDefault(nextLevel, defaultDecayThreshold(nextLevel));
                    if (decayScore < threshold) {
                        break;
                    }
                    nextLevel -= 1;
                }

                if (nextLevel != currentLevel) {
                    if (!dryRun) {
                        item.setDefaultLevel(nextLevel);
                        touch(item, now, false);
                    }
                    result.demotedIds.add(item.getId());
                    if (collectReport) {
                        result.items.add(new DecayItemReport(item.getId(), "demoted", currentLevel, nextLevel));
                    }
                }
            }

            long lastAccess = item.getLastAccessedAt() != null
                ? item.getLastAccessedAt()
                : (item.getCreatedAt() != null ? item.getCreatedAt() : now);

            if (item.getActiveLockUntil() != null && item.getActiveLockUntil() > now) {
                result.lockedItems++;
                if (collectReport) {
                    result.lockedIds.add(item.getId());
                }
                continue;
            }

            if ("active".equalsIgnoreCase(item.getState())
                && archiveAfterMs > 0
                && now - lastAccess >= archiveAfterMs) {
                if (!dryRun) {
                    item.setState("archived");
                    touch(item, now, false);
                }
                result.archivedIds.add(item.getId());
                if (collectReport) {
                    result.items.add(new DecayItemReport(item.getId(), "archived"));
                }
            }

            if ("archived".equalsIgnoreCase(item.getState())
                && expireAfterMs > 0
                && now - lastAccess >= expireAfterMs) {
                if (!dryRun) {
                    item.setState("expired");
                    touch(item, now, false);
                }
                result.expiredIds.add(item.getId());
                if (collectReport) {
                    result.items.add(new DecayItemReport(item.getId(), "expired"));
                }

                if (!dryRun && pruneExpiredR5 && item.getPinnedMinLevel() == null) {
                    int pruned = pruneR5(item.getId());
                    result.prunedEvents += pruned;
                } else if (dryRun && pruneExpiredR5 && item.getPinnedMinLevel() == null) {
                    result.prunableIds.add(item.getId());
                }
            }
        }

        if (!dryRun) {
            saveSnapshot();
        }
        return result;
    }

    private boolean isExcluded(MemoryItem item, Collection<String> topicKeys, Collection<String> agentIds) {
        boolean excludedByTopic = topicKeys != null
            && item.getTopicKey() != null
            && topicKeys.stream().anyMatch(k -> k != null && k.equalsIgnoreCase(item.getTopicKey()));
        boolean excludedByAgent = agentIds != null
            && item.getAgentId() != null
            && agentIds.stream().anyMatch(a -> a != null && a.equalsIgnoreCase(item.getAgentId()));
        return excludedByTopic || excludedByAgent;
    }

    private int pruneR5(String memoryId) {
        List<R5Event> events = eventsByItem.getOrDefault(memoryId, List.of());
        int removed = events.size();
        for (R5Event ev : events) {
            eventsById.remove(ev.getId());
        }
        eventsByItem.remove(memoryId);

        List<MemoryVersion> versions = versionsByItem.getOrDefault(memoryId, new ArrayList<>());
        versions.removeIf(v -> v.getRepLevel() >= 5);
        versionsByItem.put(memoryId, versions);
        versionsById.entrySet().removeIf(e -> memoryId.equals(e.getValue().getMemoryItemId())
            && e.getValue().getRepLevel() >= 5);
        return removed;
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
        final int targetLevelFinal = targetLevel;

        MemoryVersion exact = versions.stream()
            .filter(v -> v.getRepLevel() == targetLevelFinal)
            .findFirst()
            .orElse(null);
        if (exact != null) {
            return exact;
        }

        MemoryVersion higher = versions.stream()
            .filter(v -> v.getRepLevel() > targetLevelFinal)
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

    private void touch(MemoryItem item, long timestamp, boolean countAccess) {
        item.setUpdatedAt(timestamp);
        item.setLastAccessedAt(timestamp);
        if (countAccess) {
            int accessCount = item.getTotalAccessCount() != null ? item.getTotalAccessCount() : 0;
            item.setTotalAccessCount(accessCount + 1);
            item.setLastAccessedActivation((long) currentActivationCount(item.getAgentId()));
        }
    }

    private void loadFromDisk() {
        Path path = Paths.get(STORAGE_PATH);
        if (!Files.exists(path)) {
            return;
        }

        try {
            MemoryStoreSnapshot snapshot = mapper.readValue(path.toFile(), MemoryStoreSnapshot.class);
            if (snapshot.agentActivationCounts != null) {
                agentActivationCounts.putAll(snapshot.agentActivationCounts);
            }
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
        snapshot.agentActivationCounts = new HashMap<>(agentActivationCounts);

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

    private int currentActivationCount(String agentId) {
        return getAgentActivationCount(agentId);
    }

    private int normalizeLevel(Integer level) {
        if (level == null) return DEFAULT_LEVEL;
        return Math.max(1, Math.min(5, level));
    }

    private int defaultDecayThreshold(int level) {
        switch (level) {
            case 5:
                return 30;
            case 4:
                return 60;
            case 3:
                return 120;
            case 2:
                return 200;
            case 1:
                return 500;
            default:
                return 120;
        }
    }

    private double resolveRelevanceWeight(MemoryItem item, Map<String, Double> weights, double defaultWeight) {
        List<String> tags = new ArrayList<>();
        if (item.getTags() != null) {
            tags.addAll(item.getTags());
        }
        if (item.getTopicKey() != null && !item.getTopicKey().isBlank()) {
            tags.add(item.getTopicKey());
        }
        double weight = defaultWeight;
        for (String tag : tags) {
            if (tag == null) continue;
            Double tagged = weights.get(tag);
            if (tagged != null && tagged > 0) {
                weight = Math.min(weight, tagged);
            }
        }
        return weight > 0 ? weight : defaultWeight;
    }

    private double resolveEpochMultiplier(MemoryItem item, Map<String, Double> multipliers, double defaultMultiplier) {
        List<String> tags = new ArrayList<>();
        if (item.getTags() != null) {
            tags.addAll(item.getTags());
        }
        if (item.getProjectEpoch() != null && !item.getProjectEpoch().isBlank()) {
            tags.add(item.getProjectEpoch());
        }
        double multiplier = defaultMultiplier;
        for (String tag : tags) {
            if (tag == null) continue;
            Double tagged = multipliers.get(tag);
            if (tagged != null && tagged > multiplier) {
                multiplier = tagged;
            }
        }
        return multiplier > 0 ? multiplier : defaultMultiplier;
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

    public static class DecaySettings {
        private long archiveAfterMs;
        private long expireAfterMs;
        private boolean pruneExpiredR5;
        private boolean collectReport;
        private boolean dryRun;
        private boolean notifyOnRun = true;
        private boolean useActivationDecay = true;
        private List<String> excludeTopicKeys = new ArrayList<>();
        private List<String> excludeAgentIds = new ArrayList<>();
        private Map<Integer, Integer> decayThresholds = new HashMap<>();
        private Map<String, Double> relevanceWeights = new HashMap<>();
        private Map<String, Double> epochMultipliers = new HashMap<>();
        private double defaultRelevanceWeight = 1.0;
        private double globalEpochMultiplier = 1.0;

        public long getArchiveAfterMs() {
            return archiveAfterMs;
        }

        public void setArchiveAfterMs(long archiveAfterMs) {
            this.archiveAfterMs = archiveAfterMs;
        }

        public long getExpireAfterMs() {
            return expireAfterMs;
        }

        public void setExpireAfterMs(long expireAfterMs) {
            this.expireAfterMs = expireAfterMs;
        }

        public boolean isPruneExpiredR5() {
            return pruneExpiredR5;
        }

        public void setPruneExpiredR5(boolean pruneExpiredR5) {
            this.pruneExpiredR5 = pruneExpiredR5;
        }

        public boolean isCollectReport() {
            return collectReport;
        }

        public void setCollectReport(boolean collectReport) {
            this.collectReport = collectReport;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }

        public boolean isNotifyOnRun() {
            return notifyOnRun;
        }

        public void setNotifyOnRun(boolean notifyOnRun) {
            this.notifyOnRun = notifyOnRun;
        }

        public boolean isUseActivationDecay() {
            return useActivationDecay;
        }

        public void setUseActivationDecay(boolean useActivationDecay) {
            this.useActivationDecay = useActivationDecay;
        }

        public List<String> getExcludeTopicKeys() {
            return excludeTopicKeys;
        }

        public void setExcludeTopicKeys(List<String> excludeTopicKeys) {
            this.excludeTopicKeys = excludeTopicKeys != null ? excludeTopicKeys : new ArrayList<>();
        }

        public List<String> getExcludeAgentIds() {
            return excludeAgentIds;
        }

        public void setExcludeAgentIds(List<String> excludeAgentIds) {
            this.excludeAgentIds = excludeAgentIds != null ? excludeAgentIds : new ArrayList<>();
        }

        public Map<Integer, Integer> getDecayThresholds() {
            return decayThresholds;
        }

        public void setDecayThresholds(Map<Integer, Integer> decayThresholds) {
            this.decayThresholds = decayThresholds != null ? decayThresholds : new HashMap<>();
        }

        public Map<String, Double> getRelevanceWeights() {
            return relevanceWeights;
        }

        public void setRelevanceWeights(Map<String, Double> relevanceWeights) {
            this.relevanceWeights = relevanceWeights != null ? relevanceWeights : new HashMap<>();
        }

        public Map<String, Double> getEpochMultipliers() {
            return epochMultipliers;
        }

        public void setEpochMultipliers(Map<String, Double> epochMultipliers) {
            this.epochMultipliers = epochMultipliers != null ? epochMultipliers : new HashMap<>();
        }

        public double getDefaultRelevanceWeight() {
            return defaultRelevanceWeight;
        }

        public void setDefaultRelevanceWeight(double defaultRelevanceWeight) {
            if (defaultRelevanceWeight > 0) {
                this.defaultRelevanceWeight = defaultRelevanceWeight;
            }
        }

        public double getGlobalEpochMultiplier() {
            return globalEpochMultiplier;
        }

        public void setGlobalEpochMultiplier(double globalEpochMultiplier) {
            if (globalEpochMultiplier > 0) {
                this.globalEpochMultiplier = globalEpochMultiplier;
            }
        }
    }

    public static class DecayResult {
        private final List<String> archivedIds = new ArrayList<>();
        private final List<String> expiredIds = new ArrayList<>();
        private final List<String> demotedIds = new ArrayList<>();
        private final List<String> prunableIds = new ArrayList<>();
        private final List<String> lockedIds = new ArrayList<>();
        private final List<String> filteredIds = new ArrayList<>();
        private final List<DecayItemReport> items = new ArrayList<>();
        private int prunedEvents = 0;
        private int lockedItems = 0;
        private int filteredItems = 0;

        public List<String> getArchivedIds() {
            return archivedIds;
        }

        public List<String> getExpiredIds() {
            return expiredIds;
        }

        public List<String> getDemotedIds() {
            return demotedIds;
        }

        public List<String> getPrunableIds() {
            return prunableIds;
        }

        public int getPrunedEvents() {
            return prunedEvents;
        }

        public void setPrunedEvents(int prunedEvents) {
            this.prunedEvents = prunedEvents;
        }

        public int getLockedItems() {
            return lockedItems;
        }

        public void setLockedItems(int lockedItems) {
            this.lockedItems = lockedItems;
        }

        public List<String> getLockedIds() {
            return lockedIds;
        }

        public List<String> getFilteredIds() {
            return filteredIds;
        }

        public List<DecayItemReport> getItems() {
            return items;
        }

        public int getFilteredItems() {
            return filteredItems;
        }
    }

    public static class DecayItemReport {
        private String memoryId;
        private String action; // archived | expired
        private Integer fromLevel;
        private Integer toLevel;

        public DecayItemReport() {}

        public DecayItemReport(String memoryId, String action) {
            this.memoryId = memoryId;
            this.action = action;
        }

        public DecayItemReport(String memoryId, String action, Integer fromLevel, Integer toLevel) {
            this.memoryId = memoryId;
            this.action = action;
            this.fromLevel = fromLevel;
            this.toLevel = toLevel;
        }

        public String getMemoryId() {
            return memoryId;
        }

        public void setMemoryId(String memoryId) {
            this.memoryId = memoryId;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Integer getFromLevel() {
            return fromLevel;
        }

        public void setFromLevel(Integer fromLevel) {
            this.fromLevel = fromLevel;
        }

        public Integer getToLevel() {
            return toLevel;
        }

        public void setToLevel(Integer toLevel) {
            this.toLevel = toLevel;
        }
    }

    // ----- Snapshot DTO for persistence -----
    private static class MemoryStoreSnapshot {
        public List<MemoryItem> items;
        public List<MemoryVersion> versions;
        public List<R5Event> events;
        public Map<String, Integer> agentActivationCounts;
    }
}
