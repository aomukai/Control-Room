package com.miniide;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Background decay runner for issue memory compression.
 */
public class IssueMemoryDecayScheduler {
    private final IssueMemoryService issueService;
    private final ScheduledExecutorService executor;
    private java.util.concurrent.ScheduledFuture<?> future;
    private long intervalMs;
    private final AppLogger logger = AppLogger.get();
    private volatile long lastRunAt = 0L;

    public IssueMemoryDecayScheduler(IssueMemoryService issueService, long intervalMs) {
        this.issueService = Objects.requireNonNull(issueService, "issueService");
        this.intervalMs = intervalMs > 0 ? intervalMs : TimeUnit.HOURS.toMillis(24);
        this.executor = Executors.newSingleThreadScheduledExecutor(decayThreadFactory());
    }

    public void start() {
        future = executor.scheduleAtFixedRate(this::runOnce, this.intervalMs, this.intervalMs, TimeUnit.MILLISECONDS);
        log("Scheduled issue memory decay every " + (intervalMs / 1000 / 60) + " minutes");
    }

    public void stop() {
        executor.shutdownNow();
    }

    public synchronized void updateInterval(long newIntervalMs) {
        if (newIntervalMs > 0) {
            this.intervalMs = newIntervalMs;
        }
        if (future != null) {
            future.cancel(false);
        }
        future = executor.scheduleAtFixedRate(this::runOnce, this.intervalMs, this.intervalMs, TimeUnit.MILLISECONDS);
        log("Updated issue decay schedule: every " + (intervalMs / 1000 / 60) + " minutes");
    }

    public long getLastRunAt() {
        return lastRunAt;
    }

    private void runOnce() {
        try {
            IssueMemoryService.DecayResult result = issueService.runDecay(false);
            lastRunAt = System.currentTimeMillis();
            log("Issue decay run: decayed=" + result.getDecayed());
        } catch (Exception e) {
            logWarning("Issue decay failed: " + e.getMessage());
        }
    }

    private ThreadFactory decayThreadFactory() {
        return r -> {
            Thread t = new Thread(r, "issue-memory-decay-runner");
            t.setDaemon(true);
            return t;
        };
    }

    private void log(String message) {
        if (logger != null) {
            logger.info("[IssueMemoryDecayScheduler] " + message);
        }
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.warn("[IssueMemoryDecayScheduler] " + message);
        }
    }
}
