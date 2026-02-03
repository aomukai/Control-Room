package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.TelemetryConfig;
import com.miniide.models.TelemetryCounters;
import com.miniide.models.TelemetryIndex;
import com.miniide.models.TelemetrySession;
import com.miniide.models.TelemetrySessionInfo;
import com.miniide.models.TelemetryTotals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TelemetryStore {
    private static final DateTimeFormatter SESSION_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy_MMM_dd_HHmm", Locale.ENGLISH);
    private static final long MB = 1024L * 1024L;

    private final ObjectMapper objectMapper;
    private TelemetryConfig config;
    private Path telemetryRoot;
    private Path sessionsDir;
    private Path totalsPath;
    private Path indexPath;

    private TelemetryTotals totals;
    private TelemetrySession currentSession;
    private TelemetryIndex index;
    private String currentSessionId;

    public TelemetryStore(Path workspaceRoot, ObjectMapper objectMapper, TelemetryConfig config) {
        this.objectMapper = objectMapper;
        configure(workspaceRoot, config);
    }

    public synchronized void configure(Path workspaceRoot, TelemetryConfig config) {
        this.config = config != null ? config : new TelemetryConfig();
        this.telemetryRoot = workspaceRoot.resolve(".control-room").resolve("telemetry");
        this.sessionsDir = telemetryRoot.resolve("sessions");
        this.totalsPath = telemetryRoot.resolve("totals.json");
        this.indexPath = telemetryRoot.resolve("index.json");
        load();
        startNewSessionIfNeeded();
    }

    public synchronized TelemetryTotals getTotals() {
        return totals;
    }

    public synchronized TelemetrySession getCurrentSession() {
        return currentSession;
    }

    public synchronized void updateConfig(TelemetryConfig config) {
        if (config == null) {
            return;
        }
        this.config = config;
        saveAll();
        pruneIfNeeded();
    }

    public synchronized Map<String, Object> getStatusSnapshot() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", isEnabled());
        status.put("telemetryRoot", telemetryRoot != null ? telemetryRoot.toString() : null);
        status.put("sessionsDir", sessionsDir != null ? sessionsDir.toString() : null);
        status.put("totalsPath", totalsPath != null ? totalsPath.toString() : null);
        status.put("indexPath", indexPath != null ? indexPath.toString() : null);
        status.put("currentSessionId", currentSessionId);
        status.put("sessionCount", index != null && index.getSessions() != null ? index.getSessions().size() : 0);
        status.put("totalSizeBytes", totalSessionSizeBytes());
        status.put("maxSessions", config != null ? config.getMaxSessions() : null);
        status.put("maxAgeDays", config != null ? config.getMaxAgeDays() : null);
        status.put("maxTotalMb", config != null ? config.getMaxTotalMb() : null);
        RetentionPreview preview = buildRetentionPreview();
        status.put("wouldDeleteCount", preview.toDeleteCount);
        status.put("wouldDeleteIds", preview.toDeleteIds);
        status.put("oldestSessionStartedAt", preview.oldestStartedAt);
        status.put("newestSessionStartedAt", preview.newestStartedAt);
        return status;
    }

    public synchronized int pruneNow() {
        return prune(true).deletedCount;
    }

    public synchronized void recordActivation(String agentId, int count) {
        if (!isEnabled() || agentId == null || agentId.isBlank()) {
            return;
        }
        long delta = Math.max(1, count);
        TelemetryCounters agentTotals = getAgentTotals(agentId);
        TelemetryCounters agentSession = getAgentSession(agentId);
        agentTotals.incrementActivations(delta);
        agentSession.incrementActivations(delta);
        totals.getTotals().incrementActivations(delta);
        currentSession.getTotals().incrementActivations(delta);
        touch();
    }

    public synchronized void recordIssueAccess(String agentId) {
        if (!isEnabled() || agentId == null || agentId.isBlank()) {
            return;
        }
        TelemetryCounters agentTotals = getAgentTotals(agentId);
        TelemetryCounters agentSession = getAgentSession(agentId);
        agentTotals.incrementIssueAccesses(1);
        agentSession.incrementIssueAccesses(1);
        totals.getTotals().incrementIssueAccesses(1);
        currentSession.getTotals().incrementIssueAccesses(1);
        touch();
    }

    public synchronized void recordIssueDemotion(String agentId) {
        if (!isEnabled() || agentId == null || agentId.isBlank()) {
            return;
        }
        TelemetryCounters agentTotals = getAgentTotals(agentId);
        TelemetryCounters agentSession = getAgentSession(agentId);
        agentTotals.incrementIssueDemotions(1);
        agentSession.incrementIssueDemotions(1);
        totals.getTotals().incrementIssueDemotions(1);
        currentSession.getTotals().incrementIssueDemotions(1);
        touch();
    }

    public synchronized void recordTokens(String agentId, long tokensIn, long tokensOut) {
        if (!isEnabled() || agentId == null || agentId.isBlank()) {
            return;
        }
        TelemetryCounters agentTotals = getAgentTotals(agentId);
        TelemetryCounters agentSession = getAgentSession(agentId);
        agentTotals.incrementTokensIn(tokensIn);
        agentTotals.incrementTokensOut(tokensOut);
        agentSession.incrementTokensIn(tokensIn);
        agentSession.incrementTokensOut(tokensOut);
        totals.getTotals().incrementTokensIn(tokensIn);
        totals.getTotals().incrementTokensOut(tokensOut);
        currentSession.getTotals().incrementTokensIn(tokensIn);
        currentSession.getTotals().incrementTokensOut(tokensOut);
        touch();
    }

    public synchronized void recordTokens(String agentId, long tokensIn, long tokensOut, String conferenceId) {
        recordTokens(agentId, tokensIn, tokensOut);
        if (conferenceId != null && !conferenceId.isBlank()) {
            recordConferenceTokens(conferenceId, agentId, tokensIn, tokensOut);
        }
    }

    public synchronized void recordError(String agentId) {
        if (!isEnabled()) {
            return;
        }
        if (agentId != null && !agentId.isBlank()) {
            TelemetryCounters agentTotals = getAgentTotals(agentId);
            TelemetryCounters agentSession = getAgentSession(agentId);
            agentTotals.incrementErrors(1);
            agentSession.incrementErrors(1);
        }
        totals.getTotals().incrementErrors(1);
        currentSession.getTotals().incrementErrors(1);
        touch();
    }

    public synchronized void recordConferenceEvent(String conferenceId, String agentId, String type) {
        if (!isEnabled() || conferenceId == null || conferenceId.isBlank()) {
            return;
        }
        TelemetryCounters sessionTotals = getConferenceTotals(currentSession, conferenceId);
        TelemetryCounters totalTotals = getConferenceTotals(totals, conferenceId);
        TelemetryCounters sessionAgent = getConferenceAgentCounters(currentSession, conferenceId, agentId);
        TelemetryCounters totalAgent = getConferenceAgentCounters(totals, conferenceId, agentId);
        incrementConferenceCounters(sessionTotals, type);
        incrementConferenceCounters(totalTotals, type);
        if (sessionAgent != null) {
            incrementConferenceCounters(sessionAgent, type);
        }
        if (totalAgent != null) {
            incrementConferenceCounters(totalAgent, type);
        }
        touch();
    }

    public synchronized void recordRejection(String conferenceId, String agentId, String reason) {
        if (!isEnabled() || conferenceId == null || conferenceId.isBlank()) {
            return;
        }
        String type = mapRejectionReason(reason);
        if (type == null) {
            return;
        }
        recordConferenceEvent(conferenceId, agentId, type);
    }

    private void recordConferenceTokens(String conferenceId, String agentId, long tokensIn, long tokensOut) {
        if (!isEnabled() || conferenceId == null || conferenceId.isBlank()) {
            return;
        }
        TelemetryCounters sessionTotals = getConferenceTotals(currentSession, conferenceId);
        TelemetryCounters totalTotals = getConferenceTotals(totals, conferenceId);
        sessionTotals.incrementTokensIn(tokensIn);
        sessionTotals.incrementTokensOut(tokensOut);
        totalTotals.incrementTokensIn(tokensIn);
        totalTotals.incrementTokensOut(tokensOut);
        TelemetryCounters sessionAgent = getConferenceAgentCounters(currentSession, conferenceId, agentId);
        TelemetryCounters totalAgent = getConferenceAgentCounters(totals, conferenceId, agentId);
        if (sessionAgent != null) {
            sessionAgent.incrementTokensIn(tokensIn);
            sessionAgent.incrementTokensOut(tokensOut);
        }
        if (totalAgent != null) {
            totalAgent.incrementTokensIn(tokensIn);
            totalAgent.incrementTokensOut(tokensOut);
        }
        touch();
    }

    public static long estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, Math.round(text.length() / 4.0));
    }

    private boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    private void load() {
        totals = loadTotals();
        index = loadIndex();
    }

    private TelemetryTotals loadTotals() {
        if (totalsPath == null || !Files.exists(totalsPath)) {
            TelemetryTotals fresh = new TelemetryTotals();
            long now = System.currentTimeMillis();
            fresh.setCreatedAt(now);
            fresh.setUpdatedAt(now);
            return fresh;
        }
        try {
            TelemetryTotals loaded = objectMapper.readValue(totalsPath.toFile(), TelemetryTotals.class);
            return loaded != null ? loaded : new TelemetryTotals();
        } catch (Exception ignored) {
            return new TelemetryTotals();
        }
    }

    private TelemetryIndex loadIndex() {
        if (indexPath == null || !Files.exists(indexPath)) {
            return new TelemetryIndex();
        }
        try {
            TelemetryIndex loaded = objectMapper.readValue(indexPath.toFile(), TelemetryIndex.class);
            return loaded != null ? loaded : new TelemetryIndex();
        } catch (Exception ignored) {
            return new TelemetryIndex();
        }
    }

    private void startNewSessionIfNeeded() {
        long now = System.currentTimeMillis();
        currentSessionId = formatSessionId(now);
        currentSession = new TelemetrySession();
        currentSession.setId(currentSessionId);
        currentSession.setStartedAt(now);
        currentSession.setUpdatedAt(now);
        ensureSessionInIndex();
        saveAll();
    }

    private void ensureSessionInIndex() {
        if (index == null) {
            index = new TelemetryIndex();
        }
        List<TelemetrySessionInfo> sessions = index.getSessions();
        TelemetrySessionInfo info = sessions.stream()
            .filter(entry -> currentSessionId.equals(entry.getId()))
            .findFirst()
            .orElse(null);
        if (info == null) {
            info = new TelemetrySessionInfo();
            info.setId(currentSessionId);
            info.setFilename(currentSessionId + ".json");
            info.setStartedAt(currentSession.getStartedAt());
            info.setUpdatedAt(currentSession.getUpdatedAt());
            sessions.add(info);
        } else {
            info.setUpdatedAt(currentSession.getUpdatedAt());
        }
    }

    private TelemetryCounters getAgentTotals(String agentId) {
        Map<String, TelemetryCounters> agents = totals.getAgents();
        return agents.computeIfAbsent(agentId, key -> new TelemetryCounters());
    }

    private TelemetryCounters getAgentSession(String agentId) {
        Map<String, TelemetryCounters> agents = currentSession.getAgents();
        return agents.computeIfAbsent(agentId, key -> new TelemetryCounters());
    }

    private TelemetryCounters getConferenceTotals(TelemetrySession session, String conferenceId) {
        if (session == null || conferenceId == null || conferenceId.isBlank()) {
            return null;
        }
        Map<String, TelemetryCounters> conferences = session.getConferences();
        return conferences.computeIfAbsent(conferenceId, key -> new TelemetryCounters());
    }

    private TelemetryCounters getConferenceTotals(TelemetryTotals totals, String conferenceId) {
        if (totals == null || conferenceId == null || conferenceId.isBlank()) {
            return null;
        }
        Map<String, TelemetryCounters> conferences = totals.getConferences();
        return conferences.computeIfAbsent(conferenceId, key -> new TelemetryCounters());
    }

    private TelemetryCounters getConferenceAgentCounters(TelemetrySession session, String conferenceId, String agentId) {
        if (session == null || conferenceId == null || conferenceId.isBlank() || agentId == null || agentId.isBlank()) {
            return null;
        }
        Map<String, Map<String, TelemetryCounters>> conferenceAgents = session.getConferenceAgents();
        Map<String, TelemetryCounters> agents = conferenceAgents.computeIfAbsent(conferenceId, key -> new ConcurrentHashMap<>());
        return agents.computeIfAbsent(agentId, key -> new TelemetryCounters());
    }

    private TelemetryCounters getConferenceAgentCounters(TelemetryTotals totals, String conferenceId, String agentId) {
        if (totals == null || conferenceId == null || conferenceId.isBlank() || agentId == null || agentId.isBlank()) {
            return null;
        }
        Map<String, Map<String, TelemetryCounters>> conferenceAgents = totals.getConferenceAgents();
        Map<String, TelemetryCounters> agents = conferenceAgents.computeIfAbsent(conferenceId, key -> new ConcurrentHashMap<>());
        return agents.computeIfAbsent(agentId, key -> new TelemetryCounters());
    }

    private void incrementConferenceCounters(TelemetryCounters counters, String type) {
        if (counters == null || type == null) {
            return;
        }
        switch (type) {
            case "evidence_invalid":
            case "reject_evidence_missing_or_invalid":
                counters.incrementRejectEvidenceMissingOrInvalid(1);
                break;
            case "quote_not_found":
            case "reject_quote_not_found":
                counters.incrementRejectQuoteNotFound(1);
                break;
            case "tool_syntax":
            case "reject_tool_syntax_in_text":
                counters.incrementRejectToolSyntaxInText(1);
                break;
            case "cot_leak":
            case "cot_leak_detected":
                counters.incrementCotLeakDetected(1);
                break;
            case "format_error":
                counters.incrementFormatError(1);
                break;
            default:
                break;
        }
    }

    private String mapRejectionReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        switch (reason) {
            case "evidence_invalid":
            case "quote_not_found":
            case "tool_syntax":
            case "cot_leak":
            case "format_error":
                return reason;
            case "tool_call_invalid_format":
            case "tool_call_unknown_tool":
            case "tool_call_invalid_args":
                return "tool_syntax";
            case "tool_call_multiple":
            case "tool_call_nonce_invalid":
            case "tool_call_output_limit":
                return "format_error";
            case "reject_evidence_missing_or_invalid":
                return "evidence_invalid";
            case "reject_quote_not_found":
                return "quote_not_found";
            case "reject_tool_syntax_in_text":
                return "tool_syntax";
            case "cot_leak_detected":
                return "cot_leak";
            default:
                return "evidence_invalid";
        }
    }

    private void touch() {
        long now = System.currentTimeMillis();
        totals.setUpdatedAt(now);
        currentSession.setUpdatedAt(now);
        ensureSessionInIndex();
        saveAll();
        pruneIfNeeded();
    }

    private void saveAll() {
        try {
            Files.createDirectories(telemetryRoot);
            Files.createDirectories(sessionsDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(totalsPath.toFile(), totals);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexPath.toFile(), index);
            Path sessionPath = sessionsDir.resolve(currentSessionId + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(sessionPath.toFile(), currentSession);
        } catch (Exception ignored) {
        }
    }

    private void pruneIfNeeded() {
        prune(false);
    }

    private RetentionResult prune(boolean force) {
        RetentionResult result = new RetentionResult();
        if (!force && !isEnabled()) {
            return result;
        }
        if (index == null || index.getSessions() == null) {
            return result;
        }
        List<TelemetrySessionInfo> sessions = new ArrayList<>(index.getSessions());
        if (sessions.isEmpty()) {
            return result;
        }
        sessions.sort(Comparator.comparingLong(TelemetrySessionInfo::getStartedAt).reversed());
        int maxSessions = config != null ? config.getMaxSessions() : 200;
        int maxAgeDays = config != null ? config.getMaxAgeDays() : 90;
        int maxTotalMb = config != null ? config.getMaxTotalMb() : 50;

        List<TelemetrySessionInfo> keep = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (TelemetrySessionInfo info : sessions) {
            if (info.getId() != null && info.getId().equals(currentSessionId)) {
                keep.add(info);
                continue;
            }
            if (maxSessions > 0 && keep.size() >= maxSessions) {
                deleteSession(info);
                result.deletedCount++;
                result.deletedIds.add(info.getId());
                continue;
            }
            if (maxAgeDays > 0) {
                long ageMs = now - info.getStartedAt();
                long maxAgeMs = maxAgeDays * 24L * 60 * 60 * 1000;
                if (ageMs > maxAgeMs) {
                    deleteSession(info);
                    result.deletedCount++;
                    result.deletedIds.add(info.getId());
                    continue;
                }
            }
            keep.add(info);
        }
        if (maxTotalMb > 0) {
            long limit = maxTotalMb * MB;
            List<TelemetrySessionInfo> sizeSorted = new ArrayList<>(keep);
            sizeSorted.sort(Comparator.comparingLong(TelemetrySessionInfo::getStartedAt));
            long totalSize = sizeSorted.stream().mapToLong(this::sessionSize).sum();
            int idx = 0;
            while (totalSize > limit && idx < sizeSorted.size()) {
                TelemetrySessionInfo info = sizeSorted.get(idx);
                if (info.getId().equals(currentSessionId)) {
                    idx++;
                    continue;
                }
                long size = sessionSize(info);
                deleteSession(info);
                keep.remove(info);
                result.deletedCount++;
                result.deletedIds.add(info.getId());
                totalSize = Math.max(0L, totalSize - size);
                idx++;
            }
        }
        index.setSessions(keep);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexPath.toFile(), index);
        } catch (Exception ignored) {
        }
        return result;
    }

    private long totalSessionSizeBytes() {
        if (index == null || index.getSessions() == null) {
            return 0L;
        }
        return index.getSessions().stream().mapToLong(this::sessionSize).sum();
    }

    private RetentionPreview buildRetentionPreview() {
        RetentionPreview preview = new RetentionPreview();
        if (index == null || index.getSessions() == null) {
            return preview;
        }
        List<TelemetrySessionInfo> sessions = new ArrayList<>(index.getSessions());
        if (sessions.isEmpty()) {
            return preview;
        }
        sessions.sort(Comparator.comparingLong(TelemetrySessionInfo::getStartedAt).reversed());
        preview.newestStartedAt = sessions.get(0).getStartedAt();
        preview.oldestStartedAt = sessions.get(sessions.size() - 1).getStartedAt();

        int maxSessions = config != null ? config.getMaxSessions() : 200;
        int maxAgeDays = config != null ? config.getMaxAgeDays() : 90;
        int maxTotalMb = config != null ? config.getMaxTotalMb() : 50;

        List<TelemetrySessionInfo> keep = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (TelemetrySessionInfo info : sessions) {
            if (info.getId() != null && info.getId().equals(currentSessionId)) {
                keep.add(info);
                continue;
            }
            if (maxSessions > 0 && keep.size() >= maxSessions) {
                preview.toDeleteIds.add(info.getId());
                continue;
            }
            if (maxAgeDays > 0) {
                long ageMs = now - info.getStartedAt();
                long maxAgeMs = maxAgeDays * 24L * 60 * 60 * 1000;
                if (ageMs > maxAgeMs) {
                    preview.toDeleteIds.add(info.getId());
                    continue;
                }
            }
            keep.add(info);
        }
        if (maxTotalMb > 0) {
            long limit = maxTotalMb * MB;
            List<TelemetrySessionInfo> sizeSorted = new ArrayList<>(keep);
            sizeSorted.sort(Comparator.comparingLong(TelemetrySessionInfo::getStartedAt));
            long totalSize = sizeSorted.stream().mapToLong(this::sessionSize).sum();
            int idx = 0;
            while (totalSize > limit && idx < sizeSorted.size()) {
                TelemetrySessionInfo info = sizeSorted.get(idx);
                if (info.getId().equals(currentSessionId)) {
                    idx++;
                    continue;
                }
                long size = sessionSize(info);
                preview.toDeleteIds.add(info.getId());
                totalSize = Math.max(0L, totalSize - size);
                idx++;
            }
        }
        preview.toDeleteCount = preview.toDeleteIds.size();
        return preview;
    }

    private static class RetentionPreview {
        private int toDeleteCount = 0;
        private List<String> toDeleteIds = new ArrayList<>();
        private Long oldestStartedAt;
        private Long newestStartedAt;
    }

    private static class RetentionResult {
        private int deletedCount = 0;
        private List<String> deletedIds = new ArrayList<>();
    }

    private void deleteSession(TelemetrySessionInfo info) {
        if (info == null || info.getFilename() == null) {
            return;
        }
        try {
            Files.deleteIfExists(sessionsDir.resolve(info.getFilename()));
        } catch (Exception ignored) {
        }
    }

    private long sessionSize(TelemetrySessionInfo info) {
        if (info == null || info.getFilename() == null) {
            return 0L;
        }
        try {
            return Files.size(sessionsDir.resolve(info.getFilename()));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String formatSessionId(long epochMillis) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return SESSION_FORMATTER.format(dateTime).toLowerCase();
    }
}
