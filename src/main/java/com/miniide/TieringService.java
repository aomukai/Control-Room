package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.Agent;
import com.miniide.models.AgentCapabilityProfile;
import com.miniide.models.AgentModelRecord;
import com.miniide.models.AgentPerformanceStats;
import com.miniide.models.TierAgentSnapshot;
import com.miniide.models.TierCapFormula;
import com.miniide.models.TierCaps;
import com.miniide.models.TierEvaluation;
import com.miniide.models.TierEvent;
import com.miniide.models.TierPolicy;
import com.miniide.models.TierTaskResult;
import com.miniide.models.TierTaskSnapshot;
import com.miniide.storage.JsonStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class TieringService {
    private final Path policyPath;
    private final Path eventsPath;
    private final ObjectMapper objectMapper;
    private final AgentRegistry agentRegistry;
    private final AppLogger logger;
    private TierPolicy policy;

    public TieringService(Path workspaceRoot, ObjectMapper objectMapper, AgentRegistry agentRegistry) {
        this.objectMapper = objectMapper;
        this.agentRegistry = agentRegistry;
        this.logger = AppLogger.get();
        this.policyPath = workspaceRoot.resolve(".control-room").resolve("tiers").resolve("policy.json");
        this.eventsPath = workspaceRoot.resolve(".control-room").resolve("tiers").resolve("events.json");
        this.policy = loadPolicy();
    }

    public TierPolicy getPolicy() {
        return policy;
    }

    public TierPolicy updatePolicy(TierPolicy updates) {
        TierPolicy normalized = normalizePolicy(updates);
        policy = normalized;
        savePolicy(policy);
        return policy;
    }

    public TierAgentSnapshot getAgentSnapshot(String agentId, String modelId) {
        Agent agent = agentRegistry.getAgent(agentId);
        if (agent == null) {
            return null;
        }
        String resolvedModel = resolveModelId(agent, modelId);
        if (resolvedModel == null) {
            return null;
        }
        AgentModelRecord record = agentRegistry.getOrCreateModelRecord(agentId, resolvedModel);
        if (record == null) {
            return null;
        }
        AgentPerformanceStats perf = ensurePerformance(record);
        normalizePerformance(perf);
        TierCaps caps = computeCaps(perf.getCurrentTier());
        TierCaps effectiveCaps = computeEffectiveCaps(caps, perf);
        TierAgentSnapshot snapshot = new TierAgentSnapshot();
        snapshot.setAgentId(agentId);
        snapshot.setModelId(resolvedModel);
        snapshot.setCurrentTier(perf.getCurrentTier());
        snapshot.setCapRunStreak(perf.getCapRunStreak());
        snapshot.setCapClampRemaining(perf.getCapClampRemaining());
        snapshot.setCapClampFactor(perf.getCapClampFactor());
        snapshot.setCooldownUntil(perf.getCooldownUntil());
        snapshot.setAssistedRateLastN(computeAssistedRate(perf.getRecentTasks(), policy.getRateWindow()));
        snapshot.setVerificationFailRateLastN(computeVerificationFailRate(perf.getRecentTasks(), policy.getRateWindow()));
        snapshot.setWatchlistEventsLastW(computeWatchlistEvents(perf.getRecentTasks(), policy.getWatchlistWindow()));
        snapshot.setCaps(caps);
        snapshot.setEffectiveCaps(effectiveCaps);
        return snapshot;
    }

    public List<TierAgentSnapshot> listAgentSnapshots() {
        List<TierAgentSnapshot> results = new ArrayList<>();
        for (Agent agent : agentRegistry.listAllAgents()) {
            if (agent == null) {
                continue;
            }
            TierAgentSnapshot snapshot = getAgentSnapshot(agent.getId(), null);
            if (snapshot != null) {
                results.add(snapshot);
            }
        }
        results.sort(Comparator.comparing(TierAgentSnapshot::getAgentId));
        return results;
    }

    public TierEvaluation recordTaskResult(String agentId, String modelId, TierTaskResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Task result payload is required");
        }
        Agent agent = agentRegistry.getAgent(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }
        String resolvedModel = resolveModelId(agent, modelId);
        if (resolvedModel == null) {
            throw new IllegalArgumentException("Model is required for tier evaluation");
        }
        AgentModelRecord record = agentRegistry.getOrCreateModelRecord(agentId, resolvedModel);
        if (record == null) {
            throw new IllegalArgumentException("Unable to resolve model record for " + agentId);
        }
        AgentPerformanceStats perf = ensurePerformance(record);
        normalizePerformance(perf);

        long now = System.currentTimeMillis();
        int previousTier = perf.getCurrentTier();
        TierCaps caps = computeCaps(previousTier);

        boolean assisted = Boolean.TRUE.equals(result.getAssisted());
        boolean verified = result.getVerified() == null || result.getVerified();
        boolean failure = Boolean.TRUE.equals(result.getFailure()) || !verified;
        boolean criticalFailure = Boolean.TRUE.equals(result.getCriticalFailure());
        boolean watchlistEvent = Boolean.TRUE.equals(result.getWatchlistEvent());

        TierTaskSnapshot snapshot = new TierTaskSnapshot();
        snapshot.setTimestamp(now);
        snapshot.setTier(previousTier);
        snapshot.setAssisted(assisted);
        snapshot.setAssistedReason(result.getAssistedReason());
        snapshot.setVerified(verified);
        snapshot.setFailure(failure);
        snapshot.setFailureReason(result.getFailureReason());
        snapshot.setCriticalFailure(criticalFailure);
        snapshot.setWatchlistEvent(watchlistEvent);
        snapshot.setConfidenceHigh(result.getConfidenceHigh());
        snapshot.setRetriesUsed(result.getRetriesUsed());
        snapshot.setRequiredStepsEstimate(result.getRequiredStepsEstimate());
        snapshot.setRequiredOutputTokensEstimate(result.getRequiredOutputTokensEstimate());
        snapshot.setRequiredActiveIssuesEstimate(result.getRequiredActiveIssuesEstimate());

        setAtCap(snapshot, caps);

        perf.setTotalTasks(perf.getTotalTasks() + 1);
        if (failure) {
            perf.setFailedTasks(perf.getFailedTasks() + 1);
        } else {
            perf.setSuccessfulTasks(perf.getSuccessfulTasks() + 1);
        }

        if (Boolean.TRUE.equals(result.getScopeExceeded())) {
            perf.setScopeExceededCount(perf.getScopeExceededCount() + 1);
        }
        if (Boolean.TRUE.equals(result.getUncertainty())) {
            perf.setUncertaintyCount(perf.getUncertaintyCount() + 1);
        }
        if (Boolean.TRUE.equals(result.getNoProgress())) {
            perf.setNoProgressCount(perf.getNoProgressCount() + 1);
        }
        if (Boolean.TRUE.equals(result.getHysteria())) {
            perf.setHysteriaCount(perf.getHysteriaCount() + 1);
        }

        boolean capRun = snapshot.isAtCap() && !assisted && verified;
        if (capRun) {
            perf.setCapRunStreak(perf.getCapRunStreak() + 1);
        } else {
            perf.setCapRunStreak(0);
        }

        List<TierTaskSnapshot> recent = perf.getRecentTasks();
        if (recent == null) {
            recent = new ArrayList<>();
            perf.setRecentTasks(recent);
        }
        recent.add(snapshot);
        trimRecentTasks(recent);

        double assistedRate = computeAssistedRate(recent, policy.getRateWindow());
        double verificationFailRate = computeVerificationFailRate(recent, policy.getRateWindow());
        int watchlistEventsLastW = computeWatchlistEvents(recent, policy.getWatchlistWindow());
        int failuresLastM = computeFailures(recent, policy.getDemotionFailureWindow());

        boolean clampStarted = false;
        if (failure && perf.getCapClampRemaining() <= 0) {
            perf.setCapClampRemaining(policy.getClampTasks());
            perf.setCapClampFactor(policy.getClampFactor());
            clampStarted = true;
        }

        boolean demoted = false;
        if (failure) {
            boolean highConfidenceAtCap = snapshot.isAtCap() && Boolean.TRUE.equals(snapshot.getConfidenceHigh());
            if (criticalFailure || failuresLastM >= 2 || highConfidenceAtCap) {
                int nextTier = Math.max(1, previousTier - 1);
                if (nextTier != previousTier) {
                    perf.setCurrentTier(nextTier);
                    perf.setCapRunStreak(0);
                    perf.setCooldownUntil(now + policy.getCooldownMs());
                    perf.setLastDemotionAt(now);
                    perf.setCapClampRemaining(0);
                    demoted = true;
                    recordEvent(buildEvent("demotion", agentId, resolvedModel, previousTier, nextTier, snapshot));
                }
            }
        }

        boolean promoted = false;
        if (!demoted && meetsPromotionCriteria(perf, assistedRate, verificationFailRate, watchlistEventsLastW, now)) {
            int nextTier = perf.getCurrentTier() + 1;
            perf.setCurrentTier(nextTier);
            perf.setCapRunStreak(0);
            perf.setCooldownUntil(now + policy.getCooldownMs());
            perf.setLastPromotionAt(now);
            promoted = true;
            recordEvent(buildEvent("promotion", agentId, resolvedModel, previousTier, nextTier, snapshot));
        }

        if (perf.getCapClampRemaining() > 0 && !clampStarted) {
            perf.setCapClampRemaining(perf.getCapClampRemaining() - 1);
        }

        perf.setLastEvaluatedAt(now);

        AgentCapabilityProfile profile = record.getCapabilityProfile();
        if (profile == null) {
            profile = new AgentCapabilityProfile();
            record.setCapabilityProfile(profile);
        }
        TierCaps currentCaps = computeCaps(perf.getCurrentTier());
        TierCaps finalEffectiveCaps = computeEffectiveCaps(currentCaps, perf);
        profile.setMaxSafeSteps(finalEffectiveCaps.getMaxSafeSteps());
        profile.setMaxTaskDosage(finalEffectiveCaps.getMaxSafeSteps());

        agentRegistry.saveAgent(agent);

        TierEvaluation evaluation = new TierEvaluation();
        evaluation.setAgentId(agentId);
        evaluation.setModelId(resolvedModel);
        evaluation.setPreviousTier(previousTier);
        evaluation.setCurrentTier(perf.getCurrentTier());
        evaluation.setPromoted(promoted);
        evaluation.setDemoted(demoted);
        evaluation.setCapClampApplied(perf.getCapClampRemaining() > 0);
        evaluation.setCapClampRemaining(perf.getCapClampRemaining());
        evaluation.setCapClampFactor(perf.getCapClampFactor());
        evaluation.setAssistedRateLastN(assistedRate);
        evaluation.setVerificationFailRateLastN(verificationFailRate);
        evaluation.setWatchlistEventsLastW(watchlistEventsLastW);
        evaluation.setCapRunStreak(perf.getCapRunStreak());
        evaluation.setCooldownUntil(perf.getCooldownUntil());
        evaluation.setCaps(currentCaps);
        evaluation.setEffectiveCaps(finalEffectiveCaps);
        evaluation.setTask(snapshot);
        return evaluation;
    }

    private TierPolicy loadPolicy() {
        if (Files.exists(policyPath)) {
            try {
                TierPolicy loaded = objectMapper.readValue(policyPath.toFile(), TierPolicy.class);
                return normalizePolicy(loaded);
            } catch (IOException e) {
                logger.warn("Failed to load tier policy: " + e.getMessage());
            }
        }
        TierPolicy defaults = buildDefaultPolicy();
        savePolicy(defaults);
        return defaults;
    }

    private void savePolicy(TierPolicy policy) {
        try {
            Files.createDirectories(policyPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(policyPath.toFile(), policy);
        } catch (IOException e) {
            logger.warn("Failed to save tier policy: " + e.getMessage());
        }
    }

    private TierPolicy buildDefaultPolicy() {
        TierPolicy policy = new TierPolicy();
        policy.setSafeSteps(buildFormula(2, 3.0, 1.45, 40));
        policy.setActiveIssues(buildFormula(1, 1.7, 1.35, 14));
        policy.setOutputTokens(buildFormula(0, 600, 1.60, 12000));
        policy.setToolActions(buildFormula(2, 1.4, 1.50, 20));
        policy.setParallelHandoffs(null);
        policy.setAtCapThreshold(0.8);
        policy.setCapRunStreakRequired(5);
        policy.setAssistedRateMax(0.15);
        policy.setVerificationFailRateMax(0.10);
        policy.setRateWindow(20);
        policy.setWatchlistWindow(20);
        policy.setDemotionFailureWindow(10);
        policy.setClampTasks(3);
        policy.setClampFactor(0.8);
        policy.setCooldownMs(24L * 60L * 60L * 1000L);
        policy.setMaxNewIssuesPerIssue(5);
        policy.setMaxFilesTouchedPerIssue(6);
        policy.setMaxToolActionsPerIssue(20);
        policy.setMaxOutputTokensPerIssue(12000);
        return policy;
    }

    private TierCapFormula buildFormula(double offset, double multiplier, double growth, int ceiling) {
        TierCapFormula formula = new TierCapFormula();
        formula.setOffset(offset);
        formula.setMultiplier(multiplier);
        formula.setGrowth(growth);
        formula.setCeiling(ceiling);
        return formula;
    }

    private TierPolicy normalizePolicy(TierPolicy policy) {
        TierPolicy normalized = policy != null ? policy : new TierPolicy();
        if (normalized.getSafeSteps() == null) {
            normalized.setSafeSteps(buildFormula(2, 3.0, 1.45, 40));
        }
        if (normalized.getActiveIssues() == null) {
            normalized.setActiveIssues(buildFormula(1, 1.7, 1.35, 14));
        }
        if (normalized.getOutputTokens() == null) {
            normalized.setOutputTokens(buildFormula(0, 600, 1.60, 12000));
        }
        if (normalized.getToolActions() == null) {
            normalized.setToolActions(buildFormula(2, 1.4, 1.50, 20));
        }
        if (normalized.getAtCapThreshold() <= 0) {
            normalized.setAtCapThreshold(0.8);
        }
        if (normalized.getCapRunStreakRequired() <= 0) {
            normalized.setCapRunStreakRequired(5);
        }
        if (normalized.getAssistedRateMax() <= 0) {
            normalized.setAssistedRateMax(0.15);
        }
        if (normalized.getVerificationFailRateMax() <= 0) {
            normalized.setVerificationFailRateMax(0.10);
        }
        if (normalized.getRateWindow() <= 0) {
            normalized.setRateWindow(20);
        }
        if (normalized.getWatchlistWindow() <= 0) {
            normalized.setWatchlistWindow(20);
        }
        if (normalized.getDemotionFailureWindow() <= 0) {
            normalized.setDemotionFailureWindow(10);
        }
        if (normalized.getClampTasks() <= 0) {
            normalized.setClampTasks(3);
        }
        if (normalized.getClampFactor() <= 0) {
            normalized.setClampFactor(0.8);
        }
        if (normalized.getCooldownMs() <= 0) {
            normalized.setCooldownMs(24L * 60L * 60L * 1000L);
        }
        return normalized;
    }

    private AgentPerformanceStats ensurePerformance(AgentModelRecord record) {
        AgentPerformanceStats perf = record.getPerformance();
        if (perf == null) {
            perf = new AgentPerformanceStats();
            record.setPerformance(perf);
        }
        return perf;
    }

    private void normalizePerformance(AgentPerformanceStats perf) {
        if (perf.getCurrentTier() <= 0) {
            perf.setCurrentTier(1);
        }
        if (perf.getCapClampFactor() <= 0) {
            perf.setCapClampFactor(policy.getClampFactor());
        }
        if (perf.getRecentTasks() == null) {
            perf.setRecentTasks(new ArrayList<>());
        }
    }

    private TierCaps computeCaps(int tier) {
        TierCaps caps = new TierCaps();
        caps.setMaxSafeSteps(policy.getSafeSteps().compute(tier));
        caps.setMaxActiveIssues(policy.getActiveIssues().compute(tier));
        caps.setMaxOutputTokens(policy.getOutputTokens().compute(tier));
        caps.setMaxToolActions(policy.getToolActions().compute(tier));
        if (policy.getParallelHandoffs() != null) {
            caps.setMaxParallelHandoffs(policy.getParallelHandoffs().compute(tier));
        }
        return caps;
    }

    private TierCaps computeEffectiveCaps(TierCaps caps, AgentPerformanceStats perf) {
        TierCaps effective = new TierCaps();
        double factor = 1.0;
        if (perf.getCapClampRemaining() > 0) {
            factor = perf.getCapClampFactor() > 0 ? perf.getCapClampFactor() : policy.getClampFactor();
        }
        effective.setMaxSafeSteps(scaleCap(caps.getMaxSafeSteps(), factor));
        effective.setMaxActiveIssues(scaleCap(caps.getMaxActiveIssues(), factor));
        effective.setMaxOutputTokens(scaleCap(caps.getMaxOutputTokens(), factor));
        effective.setMaxToolActions(scaleCap(caps.getMaxToolActions(), factor));
        if (caps.getMaxParallelHandoffs() != null) {
            effective.setMaxParallelHandoffs(scaleCap(caps.getMaxParallelHandoffs(), factor));
        }
        return effective;
    }

    private int scaleCap(int value, double factor) {
        return Math.max(1, (int) Math.round(value * factor));
    }

    private void setAtCap(TierTaskSnapshot snapshot, TierCaps caps) {
        double threshold = policy.getAtCapThreshold();
        double bestRatio = 0;
        String bestDimension = null;

        if (snapshot.getRequiredStepsEstimate() != null && caps.getMaxSafeSteps() > 0) {
            double ratio = snapshot.getRequiredStepsEstimate() / (double) caps.getMaxSafeSteps();
            if (ratio >= threshold && ratio > bestRatio) {
                bestRatio = ratio;
                bestDimension = "steps";
            }
        }

        if (snapshot.getRequiredOutputTokensEstimate() != null && caps.getMaxOutputTokens() > 0) {
            double ratio = snapshot.getRequiredOutputTokensEstimate() / (double) caps.getMaxOutputTokens();
            if (ratio >= threshold && ratio > bestRatio) {
                bestRatio = ratio;
                bestDimension = "tokens";
            }
        }

        if (snapshot.getRequiredActiveIssuesEstimate() != null && caps.getMaxActiveIssues() > 0) {
            double ratio = snapshot.getRequiredActiveIssuesEstimate() / (double) caps.getMaxActiveIssues();
            if (ratio >= threshold && ratio > bestRatio) {
                bestRatio = ratio;
                bestDimension = "issues";
            }
        }

        snapshot.setAtCap(bestDimension != null);
        snapshot.setAtCapDimension(bestDimension);
    }

    private void trimRecentTasks(List<TierTaskSnapshot> recent) {
        int maxWindow = Math.max(policy.getRateWindow(),
            Math.max(policy.getWatchlistWindow(), policy.getDemotionFailureWindow()));
        if (maxWindow <= 0) {
            maxWindow = 20;
        }
        while (recent.size() > maxWindow) {
            recent.remove(0);
        }
    }

    private double computeAssistedRate(List<TierTaskSnapshot> recent, int window) {
        if (recent == null || recent.isEmpty()) {
            return 0.0;
        }
        int size = Math.min(window, recent.size());
        int assisted = 0;
        for (int i = recent.size() - size; i < recent.size(); i++) {
            if (recent.get(i).isAssisted()) {
                assisted++;
            }
        }
        return size == 0 ? 0.0 : assisted / (double) size;
    }

    private double computeVerificationFailRate(List<TierTaskSnapshot> recent, int window) {
        if (recent == null || recent.isEmpty()) {
            return 0.0;
        }
        int size = Math.min(window, recent.size());
        int failures = 0;
        for (int i = recent.size() - size; i < recent.size(); i++) {
            if (!recent.get(i).isVerified()) {
                failures++;
            }
        }
        return size == 0 ? 0.0 : failures / (double) size;
    }

    private int computeWatchlistEvents(List<TierTaskSnapshot> recent, int window) {
        if (recent == null || recent.isEmpty()) {
            return 0;
        }
        int size = Math.min(window, recent.size());
        int events = 0;
        for (int i = recent.size() - size; i < recent.size(); i++) {
            if (recent.get(i).isWatchlistEvent()) {
                events++;
            }
        }
        return events;
    }

    private int computeFailures(List<TierTaskSnapshot> recent, int window) {
        if (recent == null || recent.isEmpty()) {
            return 0;
        }
        int size = Math.min(window, recent.size());
        int failures = 0;
        for (int i = recent.size() - size; i < recent.size(); i++) {
            if (recent.get(i).isFailure()) {
                failures++;
            }
        }
        return failures;
    }

    private boolean meetsPromotionCriteria(AgentPerformanceStats perf, double assistedRate,
                                           double verificationFailRate, int watchlistEvents, long now) {
        if (perf.getCapRunStreak() < policy.getCapRunStreakRequired()) {
            return false;
        }
        if (assistedRate > policy.getAssistedRateMax()) {
            return false;
        }
        if (verificationFailRate > policy.getVerificationFailRateMax()) {
            return false;
        }
        if (watchlistEvents > 0) {
            return false;
        }
        if (perf.getCooldownUntil() != null && now < perf.getCooldownUntil()) {
            return false;
        }
        return true;
    }

    private TierEvent buildEvent(String type, String agentId, String modelId, int fromTier, int toTier,
                                 TierTaskSnapshot snapshot) {
        TierEvent event = new TierEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTimestamp(System.currentTimeMillis());
        event.setAgentId(agentId);
        event.setModelId(modelId);
        event.setType(type);
        event.setFromTier(fromTier);
        event.setToTier(toTier);
        if (snapshot != null) {
            String evidence = "cap=" + snapshot.isAtCap()
                + ", assisted=" + snapshot.isAssisted()
                + ", verified=" + snapshot.isVerified();
            if (snapshot.getAtCapDimension() != null) {
                evidence += ", dimension=" + snapshot.getAtCapDimension();
            }
            if (snapshot.getFailureReason() != null) {
                evidence += ", reason=" + snapshot.getFailureReason();
            }
            event.setEvidence(evidence);
        }
        return event;
    }

    private void recordEvent(TierEvent event) {
        if (event == null) {
            return;
        }
        try {
            List<TierEvent> existing = JsonStorage.readJsonList(eventsPath.toString(), TierEvent[].class);
            List<TierEvent> updated = new ArrayList<>(existing);
            updated.add(event);
            JsonStorage.writeJsonList(eventsPath.toString(), updated);
        } catch (IOException e) {
            logger.warn("Failed to write tier event: " + e.getMessage());
        }
    }

    private String resolveModelId(Agent agent, String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            return modelId.trim();
        }
        if (agent.getActiveModelId() != null && !agent.getActiveModelId().isBlank()) {
            return agent.getActiveModelId();
        }
        if (agent.getEndpoint() != null && agent.getEndpoint().getModel() != null) {
            return agent.getEndpoint().getModel();
        }
        return null;
    }
}
