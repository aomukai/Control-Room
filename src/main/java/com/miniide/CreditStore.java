package com.miniide;

import com.miniide.models.CreditEvent;
import com.miniide.storage.JsonStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CreditStore {

    private static final String STORAGE_PATH = "data/credits.json";
    private static final Set<String> SYSTEM_ONLY_REASONS = Set.of(
        "evidence-verified",
        "evidence-verified-precise",
        "evidence-outcome-upgrade",
        "evidence-failed-verification",
        "circuit-breaker-triggered",
        "hallucination-detected"
    );

    private final Map<String, CreditEvent> events = new ConcurrentHashMap<>();

    public CreditStore() {
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

    private void loadFromDisk() {
        Path path = Paths.get(STORAGE_PATH);
        if (!Files.exists(path)) {
            logWarning("No credit storage found at " + STORAGE_PATH + "; starting empty.");
            return;
        }

        try {
            List<CreditEvent> stored = JsonStorage.readJsonList(STORAGE_PATH, CreditEvent[].class);
            for (CreditEvent event : stored) {
                if (event != null && event.getId() != null) {
                    events.put(event.getId(), event);
                }
            }
            log("Loaded " + stored.size() + " credit event(s) from disk.");
        } catch (Exception e) {
            logWarning("Failed to load credits from " + STORAGE_PATH + ": " + e.getMessage());
        }
    }

    private void saveAll() {
        try {
            List<CreditEvent> data = new ArrayList<>(events.values());
            JsonStorage.writeJsonList(STORAGE_PATH, data);
        } catch (Exception e) {
            logWarning("Failed to save credits to " + STORAGE_PATH + ": " + e.getMessage());
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
}
