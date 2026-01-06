package com.miniide;

import com.miniide.PatchCleanupConfigStore.PatchCleanupConfig;
import com.miniide.PatchService.PatchCleanupResult;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Background cleanup runner for patch proposals.
 */
public class PatchCleanupScheduler {

    private final ProjectContext projectContext;
    private final NotificationStore notificationStore;
    private long intervalMs;
    private PatchCleanupConfig settings;
    private final ScheduledExecutorService executor;
    private java.util.concurrent.ScheduledFuture<?> future;
    private final AppLogger logger = AppLogger.get();
    private volatile long lastRunAt = 0L;
    private volatile PatchCleanupResult lastResult = null;

    public PatchCleanupScheduler(ProjectContext projectContext, NotificationStore notificationStore,
                                 long intervalMs, PatchCleanupConfig settings) {
        this.projectContext = Objects.requireNonNull(projectContext, "projectContext");
        this.notificationStore = notificationStore;
        this.intervalMs = intervalMs > 0 ? intervalMs : TimeUnit.HOURS.toMillis(24);
        this.settings = settings != null ? settings : defaultSettings();
        this.executor = Executors.newSingleThreadScheduledExecutor(cleanupThreadFactory());
    }

    public void start() {
        future = executor.scheduleAtFixedRate(this::runOnce, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log("Scheduled patch cleanup every " + (intervalMs / 1000 / 60) + " minutes");
    }

    public void stop() {
        executor.shutdownNow();
    }

    public synchronized void updateConfig(long newIntervalMs, PatchCleanupConfig newSettings) {
        if (newIntervalMs > 0) {
            this.intervalMs = newIntervalMs;
        }
        if (newSettings != null) {
            this.settings = newSettings;
        }
        if (future != null) {
            future.cancel(false);
        }
        future = executor.scheduleAtFixedRate(this::runOnce, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log("Updated patch cleanup schedule: every " + (intervalMs / 1000 / 60) + " minutes");
    }

    private void runOnce() {
        try {
            Set<String> statuses = settings.getStatuses().stream()
                .map(s -> s == null ? "" : s.toLowerCase())
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
            PatchCleanupResult result = projectContext.patches().cleanupOlderThan(
                statuses, settings.toRetentionMs(), settings.isDryRun());
            lastRunAt = System.currentTimeMillis();
            lastResult = result;
            log("Patch cleanup run: eligible=" + result.getEligibleCount()
                + ", removed=" + result.getRemovedCount()
                + ", dryRun=" + result.isDryRun());
            if (settings.isNotifyOnRun()) {
                sendNotification(result);
            }
        } catch (Exception e) {
            logWarning("Patch cleanup failed: " + e.getMessage());
        }
    }

    public PatchCleanupStatus getStatus() {
        PatchCleanupStatus status = new PatchCleanupStatus();
        status.intervalMs = intervalMs;
        status.settings = settings;
        status.lastRunAt = lastRunAt;
        status.lastResult = lastResult;
        return status;
    }

    private PatchCleanupConfig defaultSettings() {
        PatchCleanupConfig config = new PatchCleanupConfig();
        config.setIntervalMinutes(24 * 60);
        config.setRetainDays(30);
        config.setNotifyOnRun(false);
        config.setDryRun(false);
        config.setStatuses(java.util.List.of("applied", "rejected"));
        return config;
    }

    private ThreadFactory cleanupThreadFactory() {
        return r -> {
            Thread t = new Thread(r, "patch-cleanup-runner");
            t.setDaemon(true);
            return t;
        };
    }

    private void sendNotification(PatchCleanupResult result) {
        if (notificationStore == null) return;
        String mode = result.isDryRun() ? "Patch cleanup (dry run)" : "Patch cleanup";
        String msg = mode + ": eligible " + result.getEligibleCount()
            + ", removed " + result.getRemovedCount();
        notificationStore.push(
            com.miniide.models.Notification.Level.INFO,
            com.miniide.models.Notification.Scope.GLOBAL,
            msg,
            "",
            com.miniide.models.Notification.Category.INFO,
            false,
            "",
            null,
            "patch"
        );
    }

    private void log(String message) {
        if (logger != null) {
            logger.info("[PatchCleanupScheduler] " + message);
        }
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.warn("[PatchCleanupScheduler] " + message);
        }
    }

    public static class PatchCleanupStatus {
        public long intervalMs;
        public long lastRunAt;
        public PatchCleanupConfig settings;
        public PatchCleanupResult lastResult;
    }
}
