package com.miniide;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Background decay/compression runner for the librarian memory store.
 */
public class MemoryDecayScheduler {

    private final MemoryService memoryService;
    private long intervalMs;
    private MemoryService.DecaySettings settings;
    private final ScheduledExecutorService executor;
    private java.util.concurrent.ScheduledFuture<?> future;
    private final AppLogger logger = AppLogger.get();
    private volatile long lastRunAt = 0L;
    private volatile MemoryService.DecayResult lastResult = null;

    public MemoryDecayScheduler(MemoryService memoryService, long intervalMs, MemoryService.DecaySettings settings) {
        this.memoryService = Objects.requireNonNull(memoryService, "memoryService");
        this.intervalMs = intervalMs > 0 ? intervalMs : TimeUnit.HOURS.toMillis(6);
        this.settings = settings != null ? settings : defaultSettings();
        this.executor = Executors.newSingleThreadScheduledExecutor(decayThreadFactory());
    }

    public void start() {
        future = executor.scheduleAtFixedRate(this::runOnce, this.intervalMs, this.intervalMs, TimeUnit.MILLISECONDS);
        log("Scheduled memory decay every " + (intervalMs / 1000 / 60) + " minutes");
    }

    public void stop() {
        executor.shutdownNow();
    }

    public synchronized void updateConfig(long newIntervalMs, MemoryService.DecaySettings newSettings) {
        if (newIntervalMs > 0) {
            this.intervalMs = newIntervalMs;
        }
        if (newSettings != null) {
            this.settings = newSettings;
        }
        if (future != null) {
            future.cancel(false);
        }
        future = executor.scheduleAtFixedRate(this::runOnce, this.intervalMs, this.intervalMs, TimeUnit.MILLISECONDS);
        log("Updated decay schedule: every " + (intervalMs / 1000 / 60) + " minutes");
    }

    private void runOnce() {
        try {
            MemoryService.DecayResult result = memoryService.runDecay(settings);
            lastRunAt = System.currentTimeMillis();
            lastResult = result;
            log("Decay run: archived=" + result.getArchivedIds().size()
                + ", expired=" + result.getExpiredIds().size()
                + ", prunedEvents=" + result.getPrunedEvents()
                + ", lockedSkipped=" + result.getLockedItems());
        } catch (Exception e) {
            logWarning("Memory decay failed: " + e.getMessage());
        }
    }

    public DecayStatus getStatus() {
        DecayStatus status = new DecayStatus();
        status.intervalMs = intervalMs;
        status.settings = settings;
        status.lastRunAt = lastRunAt;
        status.lastResult = lastResult;
        return status;
    }

    private MemoryService.DecaySettings defaultSettings() {
        MemoryService.DecaySettings s = new MemoryService.DecaySettings();
        s.setArchiveAfterMs(TimeUnit.DAYS.toMillis(14));
        s.setExpireAfterMs(TimeUnit.DAYS.toMillis(30));
        s.setPruneExpiredR5(false);
        return s;
    }

    private ThreadFactory decayThreadFactory() {
        return r -> {
            Thread t = new Thread(r, "memory-decay-runner");
            t.setDaemon(true);
            return t;
        };
    }

    private void log(String message) {
        if (logger != null) {
            logger.info("[MemoryDecayScheduler] " + message);
        }
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.warn("[MemoryDecayScheduler] " + message);
        }
    }

    public static class DecayStatus {
        public long intervalMs;
        public long lastRunAt;
        public MemoryService.DecaySettings settings;
        public MemoryService.DecayResult lastResult;
    }
}
