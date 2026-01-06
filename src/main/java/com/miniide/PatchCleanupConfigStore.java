package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists patch cleanup scheduler configuration so settings survive restarts.
 */
public class PatchCleanupConfigStore {

    private static final String STORAGE_PATH = "data/patch-cleanup-config.json";

    private final ObjectMapper mapper;
    private final AppLogger logger = AppLogger.get();

    public PatchCleanupConfigStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public PatchCleanupConfig loadOrDefault(PatchCleanupConfig defaults) {
        if (!Files.exists(Paths.get(STORAGE_PATH))) {
            return defaults != null ? defaults : new PatchCleanupConfig();
        }

        try {
            PatchCleanupConfig stored = mapper.readValue(Paths.get(STORAGE_PATH).toFile(), PatchCleanupConfig.class);
            return merge(defaults, stored);
        } catch (Exception e) {
            logWarn("Failed to load patch cleanup config, using defaults: " + e.getMessage());
            return defaults != null ? defaults : new PatchCleanupConfig();
        }
    }

    public void save(PatchCleanupConfig config) {
        if (config == null) return;
        try {
            Path path = Paths.get(STORAGE_PATH);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), config);
            log("Saved patch cleanup config to " + STORAGE_PATH);
        } catch (IOException e) {
            logWarn("Failed to save patch cleanup config: " + e.getMessage());
        }
    }

    private PatchCleanupConfig merge(PatchCleanupConfig defaults, PatchCleanupConfig stored) {
        PatchCleanupConfig base = defaults != null ? defaults : new PatchCleanupConfig();
        if (stored == null) {
            return base;
        }

        PatchCleanupConfig result = new PatchCleanupConfig();
        result.setIntervalMinutes(stored.getIntervalMinutes() > 0 ? stored.getIntervalMinutes() : base.getIntervalMinutes());
        result.setRetainDays(stored.getRetainDays() > 0 ? stored.getRetainDays() : base.getRetainDays());
        result.setNotifyOnRun(stored.isNotifyOnRun());
        result.setDryRun(stored.isDryRun());
        result.setStatuses(copyListOrDefault(stored.getStatuses(), base.getStatuses()));
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
            logger.info("[PatchCleanupConfigStore] " + message);
        }
    }

    private void logWarn(String message) {
        if (logger != null) {
            logger.warn("[PatchCleanupConfigStore] " + message);
        }
    }

    public static class PatchCleanupConfig {
        private long intervalMinutes;
        private long retainDays;
        private boolean notifyOnRun = true;
        private boolean dryRun = false;
        private List<String> statuses = new ArrayList<>();

        public long toIntervalMs() {
            return intervalMinutes > 0 ? intervalMinutes * 60_000L : 0L;
        }

        public long toRetentionMs() {
            return retainDays > 0 ? retainDays * 24L * 60L * 60L * 1000L : 0L;
        }

        public long getIntervalMinutes() {
            return intervalMinutes;
        }

        public void setIntervalMinutes(long intervalMinutes) {
            this.intervalMinutes = intervalMinutes;
        }

        public long getRetainDays() {
            return retainDays;
        }

        public void setRetainDays(long retainDays) {
            this.retainDays = retainDays;
        }

        public boolean isNotifyOnRun() {
            return notifyOnRun;
        }

        public void setNotifyOnRun(boolean notifyOnRun) {
            this.notifyOnRun = notifyOnRun;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }

        public List<String> getStatuses() {
            return statuses;
        }

        public void setStatuses(List<String> statuses) {
            this.statuses = statuses != null ? statuses : new ArrayList<>();
        }
    }
}
