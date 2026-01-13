package com.miniide;

import com.miniide.models.AgentCreditProfile;
import com.miniide.models.CreditEvent;
import com.miniide.storage.JsonStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CreditStore {
    private static final Set<String> SYSTEM_ONLY_REASONS = Set.of(
        "evidence-verified",
        "evidence-verified-precise",
        "evidence-outcome-upgrade",
        "evidence-failed-verification",
        "circuit-breaker-triggered",
        "hallucination-detected"
    );
    private static final Set<String> EVIDENCE_REASONS = Set.of(
        "evidence-verified",
        "evidence-verified-precise",
        "evidence-outcome-upgrade",
        "evidence-failed-verification"
    );
    private static final Set<String> VERIFIED_REASONS = Set.of(
        "evidence-verified",
        "evidence-verified-precise"
    );
    private static final Set<String> FAILED_VERIFICATION_REASONS = Set.of(
        "evidence-failed-verification",
        "hallucination-detected"
    );

    private final Map<String, CreditEvent> events = new ConcurrentHashMap<>();
    private Path storagePath;

    public CreditStore(Path workspaceRoot) {
        switchWorkspace(workspaceRoot);
    }

    public synchronized void switchWorkspace(Path workspaceRoot) {
        this.storagePath = resolveStoragePath(workspaceRoot);
        loadFromDisk();
    }

    public List<CreditEvent> listAll() {
        List<CreditEvent> results = new ArrayList<>(events.values());
        results.sort(Comparator.comparingLong(CreditEvent::getTimestamp).reversed());
        return results;
    }

    public List<CreditEvent> listByAgent(String agentId) {
        List<CreditEvent> results = new ArrayList<>();
        if (agentId == null || agentId.isBlank()) {
            return results;
        }
        for (CreditEvent event : events.values()) {
            if (agentId.equals(event.getAgentId())) {
                results.add(event);
            }
        }
        results.sort(Comparator.comparingLong(CreditEvent::getTimestamp).reversed());
        return results;
    }

    public CreditEvent get(String id) {
        if (id == null) {
            return null;
        }
        return events.get(id);
    }

    public CreditEvent award(CreditEvent event) {
        validateEvent(event);
        CreditEvent normalized = normalizeEvent(event);
        events.put(normalized.getId(), normalized);
        log("Credit awarded: " + normalized.getId() + " -> " + normalized.getAgentId()
            + " (" + normalized.getAmount() + ")");
        saveAll();
        return normalized;
    }

    public double computeAssistedCredits(double totalCredits, int slices) {
        if (slices <= 0) {
            throw new IllegalArgumentException("Slices must be >= 1");
        }
        return totalCredits / slices;
    }

    public List<AgentCreditProfile> listProfiles() {
        Map<String, List<CreditEvent>> byAgent = groupByAgent();
        List<AgentCreditProfile> results = new ArrayList<>();
        for (Map.Entry<String, List<CreditEvent>> entry : byAgent.entrySet()) {
            results.add(buildProfile(entry.getKey(), entry.getValue()));
        }
        results.sort(Comparator.comparingDouble(AgentCreditProfile::getCurrentCredits).reversed());
        return results;
    }

    public AgentCreditProfile getProfile(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        List<CreditEvent> agentEvents = new ArrayList<>();
        for (CreditEvent event : events.values()) {
            if (agentId.equals(event.getAgentId())) {
                agentEvents.add(event);
            }
        }
        return buildProfile(agentId, agentEvents);
    }

    private void validateEvent(CreditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Credit event payload is required");
        }
        if (event.getAgentId() == null || event.getAgentId().isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        if (event.getVerifiedBy() == null || event.getVerifiedBy().isBlank()) {
            throw new IllegalArgumentException("verifiedBy is required");
        }
        if (event.getAgentId().equals(event.getVerifiedBy())) {
            throw new IllegalArgumentException("agent cannot award credits to itself");
        }
        if (event.getReason() == null || event.getReason().isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        if (SYSTEM_ONLY_REASONS.contains(event.getReason())
            && !"system".equals(event.getVerifiedBy())) {
            throw new IllegalArgumentException("system-only credit reason requires verifiedBy=system");
        }
        if (EVIDENCE_REASONS.contains(event.getReason())) {
            CreditEvent.CreditContext context = event.getContext();
            if (context == null || isBlank(context.getTrigger())) {
                throw new IllegalArgumentException("evidence credit requires trigger context");
            }
            if ("evidence-outcome-upgrade".equals(event.getReason())
                && isBlank(context.getOutcome())) {
                throw new IllegalArgumentException("evidence-outcome-upgrade requires outcome context");
            }
        }
    }

    private CreditEvent normalizeEvent(CreditEvent event) {
        if (event.getId() == null || event.getId().isBlank()) {
            event.setId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() <= 0) {
            event.setTimestamp(System.currentTimeMillis());
        }
        return event;
    }

    private Map<String, List<CreditEvent>> groupByAgent() {
        Map<String, List<CreditEvent>> grouped = new HashMap<>();
        for (CreditEvent event : events.values()) {
            if (event == null || event.getAgentId() == null) {
                continue;
            }
            grouped.computeIfAbsent(event.getAgentId(), key -> new ArrayList<>()).add(event);
        }
        return grouped;
    }

    private AgentCreditProfile buildProfile(String agentId, List<CreditEvent> agentEvents) {
        AgentCreditProfile profile = new AgentCreditProfile();
        profile.setAgentId(agentId);
        if (agentEvents == null || agentEvents.isEmpty()) {
            profile.setReliabilityTier("none");
            return profile;
        }

        double totalCredits = 0;
        Map<String, Double> creditsByReason = new HashMap<>();
        int verifiedCount = 0;
        int failedVerificationCount = 0;
        int penaltyCount = 0;

        agentEvents.sort(Comparator.comparingLong(CreditEvent::getTimestamp));
        int longestStreak = 0;
        int currentStreak = 0;
        double recentDelta = 0;

        for (CreditEvent event : agentEvents) {
            if (event == null) {
                continue;
            }
            double amount = event.getAmount();
            recentDelta = amount;
            totalCredits += amount;
            String reason = event.getReason();
            if (reason != null) {
                creditsByReason.put(reason, creditsByReason.getOrDefault(reason, 0.0) + amount);
            }

            boolean isVerified = reason != null && VERIFIED_REASONS.contains(reason);
            boolean isFailedVerification = reason != null && FAILED_VERIFICATION_REASONS.contains(reason);
            if (isVerified) {
                verifiedCount++;
            }
            if (isFailedVerification) {
                failedVerificationCount++;
            }
            if (amount < 0 || isFailedVerification) {
                penaltyCount++;
            }

            if (isVerified) {
                currentStreak++;
                if (currentStreak > longestStreak) {
                    longestStreak = currentStreak;
                }
            } else {
                currentStreak = 0;
            }
        }

        int totalVerificationAttempts = verifiedCount + failedVerificationCount;
        double verificationRate = totalVerificationAttempts == 0
            ? 0.0
            : (double) verifiedCount / totalVerificationAttempts;
        double penaltyRate = agentEvents.isEmpty()
            ? 0.0
            : (double) penaltyCount / agentEvents.size();

        int trailingStreak = 0;
        for (int i = agentEvents.size() - 1; i >= 0; i--) {
            CreditEvent event = agentEvents.get(i);
            String reason = event != null ? event.getReason() : null;
            if (reason != null && VERIFIED_REASONS.contains(reason)) {
                trailingStreak++;
            } else {
                break;
            }
        }

        profile.setLifetimeCredits(totalCredits);
        profile.setCurrentCredits(totalCredits);
        profile.setCreditsByReason(creditsByReason);
        profile.setCreditsThisSession(totalCredits);
        profile.setCreditsThisChapter(0);
        profile.setVerificationRate(verificationRate);
        profile.setApplicationRate(0);
        profile.setPenaltyRate(penaltyRate);
        profile.setCurrentVerifiedStreak(trailingStreak);
        profile.setLongestVerifiedStreak(longestStreak);
        profile.setReliabilityTier(computeReliabilityTier(verificationRate, penaltyRate));
        profile.setRecentDelta(recentDelta);
        return profile;
    }

    private String computeReliabilityTier(double verificationRate, double penaltyRate) {
        if (verificationRate > 0.95 && penaltyRate < 0.05) {
            return "gold";
        }
        if (verificationRate > 0.85 && penaltyRate < 0.15) {
            return "silver";
        }
        if (verificationRate > 0.70 && penaltyRate < 0.25) {
            return "bronze";
        }
        return "none";
    }

    private void loadFromDisk() {
        events.clear();
        if (storagePath == null) {
            logWarning("No credit storage path resolved; starting empty.");
            return;
        }
        Path path = storagePath;
        if (!Files.exists(path)) {
            logWarning("No credit storage found at " + path + "; starting empty.");
            return;
        }

        try {
            List<CreditEvent> stored = JsonStorage.readJsonList(path.toString(), CreditEvent[].class);
            for (CreditEvent event : stored) {
                if (event != null && event.getId() != null) {
                    events.put(event.getId(), event);
                }
            }
            log("Loaded " + stored.size() + " credit event(s) from disk.");
        } catch (Exception e) {
            logWarning("Failed to load credits from " + path + ": " + e.getMessage());
        }
    }

    private void saveAll() {
        if (storagePath == null) {
            logWarning("Skipping credit save: no storage path resolved.");
            return;
        }
        try {
            Files.createDirectories(storagePath.getParent());
            List<CreditEvent> data = new ArrayList<>(events.values());
            JsonStorage.writeJsonList(storagePath.toString(), data);
        } catch (Exception e) {
            logWarning("Failed to save credits to " + storagePath + ": " + e.getMessage());
        }
    }

    private void log(String message) {
        AppLogger logger = AppLogger.get();
        if (logger != null) {
            logger.info("[CreditStore] " + message);
        }
    }

    private void logWarning(String message) {
        AppLogger logger = AppLogger.get();
        if (logger != null) {
            logger.warn("[CreditStore] " + message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Path resolveStoragePath(Path workspaceRoot) {
        if (workspaceRoot == null) {
            return Paths.get("data/credits.json");
        }
        return workspaceRoot.resolve(".control-room").resolve("credits").resolve("credits.json");
    }
}
