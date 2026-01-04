package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists decay scheduler configuration so UI updates survive restarts.
 */
public class DecayConfigStore {

    private static final String STORAGE_PATH = "data/decay-config.json";

    private final ObjectMapper mapper;
    private final AppLogger logger = AppLogger.get();

    public DecayConfigStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public DecayConfig loadOrDefault(DecayConfig defaults) {
        if (!Files.exists(Paths.get(STORAGE_PATH))) {
            return defaults != null ? defaults : new DecayConfig();
        }

        try {
            DecayConfig stored = mapper.readValue(Paths.get(STORAGE_PATH).toFile(), DecayConfig.class);
            return merge(defaults, stored);
        } catch (Exception e) {
            logWarn("Failed to load decay config, using defaults: " + e.getMessage());
            return defaults != null ? defaults : new DecayConfig();
        }
    }

    public void save(DecayConfig config) {
        if (config == null) return;
        try {
            Path path = Paths.get(STORAGE_PATH);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), config);
            log("Saved decay config to " + STORAGE_PATH);
        } catch (IOException e) {
            logWarn("Failed to save decay config: " + e.getMessage());
        }
    }

    private DecayConfig merge(DecayConfig defaults, DecayConfig stored) {
        DecayConfig base = defaults != null ? defaults : new DecayConfig();
        if (stored == null) {
            return base;
        }

        DecayConfig result = new DecayConfig();
        result.setIntervalMinutes(stored.getIntervalMinutes() > 0 ? stored.getIntervalMinutes() : base.getIntervalMinutes());
        result.setArchiveAfterDays(stored.getArchiveAfterDays() > 0 ? stored.getArchiveAfterDays() : base.getArchiveAfterDays());
        result.setExpireAfterDays(stored.getExpireAfterDays() > 0 ? stored.getExpireAfterDays() : base.getExpireAfterDays());
        result.setPruneExpiredR5(stored.isPruneExpiredR5());
        result.setCollectReport(stored.isCollectReport());
        result.setDryRun(stored.isDryRun());
        result.setNotifyOnRun(stored.isNotifyOnRun());
        result.setExcludeTopicKeys(copyListOrDefault(stored.getExcludeTopicKeys(), base.getExcludeTopicKeys()));
        result.setExcludeAgentIds(copyListOrDefault(stored.getExcludeAgentIds(), base.getExcludeAgentIds()));
        return result;
    }

    private List<String> copyListOrDefault(List<String> source, List<String> defaults) {
        if (source != null && !source.isEmpty()) {
            return new ArrayList<>(source);
        }
        return defaults != null ? new ArrayList<>(defaults) : new ArrayList<>();
    }

    private void log(String message) {
        if (logger != null) {
            logger.info("[DecayConfigStore] " + message);
        }
    }

    private void logWarn(String message) {
        if (logger != null) {
            logger.warn("[DecayConfigStore] " + message);
        }
    }

    public static class DecayConfig {
        private long intervalMinutes;
        private long archiveAfterDays;
        private long expireAfterDays;
        private boolean pruneExpiredR5;
        private boolean collectReport;
        private boolean dryRun;
        private boolean notifyOnRun = true;
        private List<String> excludeTopicKeys = new ArrayList<>();
        private List<String> excludeAgentIds = new ArrayList<>();

        public MemoryService.DecaySettings toDecaySettings() {
            MemoryService.DecaySettings settings = new MemoryService.DecaySettings();
            settings.setArchiveAfterMs(toMillis(archiveAfterDays));
            settings.setExpireAfterMs(toMillis(expireAfterDays));
            settings.setPruneExpiredR5(pruneExpiredR5);
            settings.setCollectReport(collectReport);
            settings.setDryRun(dryRun);
            settings.setNotifyOnRun(notifyOnRun);
            settings.setExcludeTopicKeys(excludeTopicKeys);
            settings.setExcludeAgentIds(excludeAgentIds);
            return settings;
        }

        public long toIntervalMs() {
            return intervalMinutes > 0 ? intervalMinutes * 60_000L : 0L;
        }

        private long toMillis(long days) {
            return days > 0 ? days * 24L * 60L * 60L * 1000L : 0L;
        }

        public long getIntervalMinutes() {
            return intervalMinutes;
        }

        public void setIntervalMinutes(long intervalMinutes) {
            this.intervalMinutes = intervalMinutes;
        }

        public long getArchiveAfterDays() {
            return archiveAfterDays;
        }

        public void setArchiveAfterDays(long archiveAfterDays) {
            this.archiveAfterDays = archiveAfterDays;
        }

        public long getExpireAfterDays() {
            return expireAfterDays;
        }

        public void setExpireAfterDays(long expireAfterDays) {
            this.expireAfterDays = expireAfterDays;
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
    }
}
