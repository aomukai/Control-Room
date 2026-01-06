package com.miniide;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.miniide.models.PatchAuditEntry;
import com.miniide.models.PatchFileChange;
import com.miniide.models.PatchProposal;
import com.miniide.models.PatchProvenance;
import com.miniide.models.TextEdit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PatchService {

    private final List<PatchProposal> patches = new ArrayList<>();
    private final AtomicInteger counter = new AtomicInteger(0);
    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkspaceService workspaceService;
    private final Path storagePath;
    private final Path legacyStoragePath = Paths.get("data/patches.json");

    public PatchService(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
        this.storagePath = workspaceService.getWorkspaceRoot()
            .resolve(".control-room")
            .resolve("patches.json")
            .toAbsolutePath()
            .normalize();
        load();
    }

    public synchronized List<PatchProposal> list() {
        List<PatchProposal> sorted = patches.stream()
            .map(this::normalizeProposal)
            .collect(Collectors.toList());
        sorted.sort(Comparator.comparingLong(PatchProposal::getCreatedAt).reversed());
        return sorted;
    }

    public synchronized PatchProposal get(String id) {
        if (id == null) return null;
        PatchProposal proposal = patches.stream()
            .filter(p -> id.equals(p.getId()))
            .findFirst()
            .orElse(null);
        if (proposal == null) {
            return null;
        }
        PatchProposal normalized = normalizeProposal(proposal);
        computeDiffs(normalized);
        return normalized;
    }

    public synchronized PatchProposal create(PatchProposal proposal) throws IOException {
        if (proposal == null) {
            throw new IllegalArgumentException("Proposal is required");
        }
        normalizeProposal(proposal);
        if (proposal.getFiles() == null || proposal.getFiles().isEmpty()) {
            throw new IllegalArgumentException("At least one file change is required");
        }
        for (PatchFileChange change : proposal.getFiles()) {
            if (change.getFilePath() == null || change.getFilePath().isBlank()) {
                throw new IllegalArgumentException("File path is required for each change");
            }
        }
        String id = proposal.getId();
        if (id == null || id.isBlank()) {
            id = "patch-" + counter.incrementAndGet();
        }
        proposal.setId(id);
        proposal.setCreatedAt(System.currentTimeMillis());
        if (proposal.getStatus() == null || proposal.getStatus().isBlank()) {
            proposal.setStatus("pending");
        }
        appendAudit(proposal, "created", "pending", proposal.getDescription());
        patches.add(proposal);
        save();
        return proposal;
    }

    public synchronized ApplyOutcome apply(String id) throws IOException {
        PatchProposal proposal = get(id);
        if (proposal == null) {
            throw new IllegalArgumentException("Patch not found: " + id);
        }
        if (!"pending".equalsIgnoreCase(proposal.getStatus())) {
            return ApplyOutcome.success(proposal, List.of());
        }
        if (proposal.getFiles() == null || proposal.getFiles().isEmpty()) {
            return ApplyOutcome.failure(proposal, "Patch has no files to apply", List.of());
        }

        List<FileChangeComputation> computations = new ArrayList<>();
        List<FileApplyResult> fileResults = new ArrayList<>();
        for (PatchFileChange change : proposal.getFiles()) {
            try {
                FileChangeComputation computation = computeFileChange(change);
                computations.add(computation);
                fileResults.add(FileApplyResult.success(change.getFilePath(), "Ready"));
            } catch (Exception e) {
                String message = "Failed for " + change.getFilePath() + ": " + e.getMessage();
                fileResults.add(FileApplyResult.failure(change.getFilePath(), message));
                return ApplyOutcome.failure(proposal, message, fileResults);
            }
        }

        // All files validated; write them now
        for (FileChangeComputation computation : computations) {
            workspaceService.writeFileLines(computation.filePath(), computation.patched());
        }

        proposal.setStatus("applied");
        appendAudit(proposal, "applied", "applied", "Applied " + computations.size() + " file(s)");
        save();
        return ApplyOutcome.success(proposal, fileResults);
    }

    public synchronized PatchProposal reject(String id) throws IOException {
        PatchProposal proposal = get(id);
        if (proposal == null) {
            throw new IllegalArgumentException("Patch not found: " + id);
        }
        proposal.setStatus("rejected");
        appendAudit(proposal, "rejected", "rejected", "Patch rejected");
        save();
        return proposal;
    }

    public synchronized boolean delete(String id) throws IOException {
        boolean removed = patches.removeIf(p -> id != null && id.equals(p.getId()));
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized int cleanup(Set<String> statuses) throws IOException {
        if (statuses == null || statuses.isEmpty()) {
            statuses = Set.of("applied", "rejected");
        }
        Set<String> normalized = statuses.stream()
            .map(s -> s == null ? "" : s.toString().toLowerCase())
            .collect(Collectors.toSet());
        int before = patches.size();
        patches.removeIf(p -> normalized.contains((p.getStatus() == null ? "" : p.getStatus().toLowerCase())));
        int removed = before - patches.size();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    public synchronized PatchCleanupResult cleanupOlderThan(Set<String> statuses, long olderThanMs, boolean dryRun) throws IOException {
        if (olderThanMs <= 0) {
            return new PatchCleanupResult(0, 0, 0L, dryRun, List.of(), List.of());
        }
        if (statuses == null || statuses.isEmpty()) {
            statuses = Set.of("applied", "rejected");
        }
        long cutoff = System.currentTimeMillis() - olderThanMs;
        Set<String> normalized = statuses.stream()
            .map(s -> s == null ? "" : s.toString().toLowerCase())
            .collect(Collectors.toSet());

        List<PatchProposal> eligible = patches.stream()
            .filter(p -> normalized.contains((p.getStatus() == null ? "" : p.getStatus().toLowerCase())))
            .filter(p -> p.getCreatedAt() <= cutoff)
            .collect(Collectors.toList());

        List<String> eligibleIds = eligible.stream()
            .map(PatchProposal::getId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toList());

        int removed = 0;
        if (!dryRun && !eligible.isEmpty()) {
            removed = (int) patches.stream()
                .filter(p -> normalized.contains((p.getStatus() == null ? "" : p.getStatus().toLowerCase())))
                .filter(p -> p.getCreatedAt() <= cutoff)
                .count();
            patches.removeIf(p -> normalized.contains((p.getStatus() == null ? "" : p.getStatus().toLowerCase()))
                && p.getCreatedAt() <= cutoff);
            if (removed > 0) {
                save();
            }
        }

        List<String> statusList = normalized.stream().sorted().collect(Collectors.toList());
        return new PatchCleanupResult(removed, eligible.size(), cutoff, dryRun, eligibleIds, statusList);
    }

    public PatchProposal simulatePatch(String filePath) throws IOException {
        String target = (filePath == null || filePath.isBlank()) ? "README.md" : filePath.trim();
        List<TextEdit> edits = new ArrayList<>();
        // Replace first line with a simulated heading
        edits.add(new TextEdit(1, 1, "# Simulated Patch Header"));
        // Insert a note after line 3
        edits.add(new TextEdit(3, 3, "## Simulation Note\nThis patch was generated for testing the review flow.\n"));
        // Add a footer line
        edits.add(new TextEdit(8, 8, "Simulation complete.\n"));

        PatchProposal proposal = new PatchProposal();
        proposal.setFilePath(target);
        proposal.setEdits(edits);
        proposal.setTitle("Simulated Patch");
        proposal.setDescription("Simulation-only patch for testing the review modal.");
        proposal.setPreview(String.join("\n", List.of(
            "Simulated edits:",
            "- Replace line 1 with a demo header",
            "- Insert a simulation note after line 3",
            "- Add a footer marker at line 8"
        )));
        PatchFileChange change = new PatchFileChange();
        change.setFilePath(target);
        change.setEdits(edits);
        change.setPreview("Simulated edits to " + target);
        proposal.setFiles(List.of(change));

        // Add provenance with agent
        PatchProvenance provenance = new PatchProvenance();
        provenance.setAuthor("Control Room Test");
        provenance.setSource("simulation");
        provenance.setAgent("planner");
        provenance.setModel("claude-sonnet-4");
        proposal.setProvenance(provenance);

        return create(proposal);
    }

    private void load() {
        Path path = resolveStoragePath();
        if (!Files.exists(path)) {
            if (!Files.exists(legacyStoragePath)) {
                return;
            }
            path = legacyStoragePath;
        }
        try {
            List<PatchProposal> stored = mapper.readValue(path.toFile(), new TypeReference<List<PatchProposal>>() {});
            if (stored != null) {
                patches.clear();
                stored.stream()
                    .map(this::normalizeProposal)
                    .forEach(patches::add);
                Optional<Integer> max = stored.stream()
                    .map(PatchProposal::getId)
                    .filter(id -> id != null && id.startsWith("patch-"))
                    .map(id -> id.replace("patch-", ""))
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    })
                    .max(Integer::compareTo);
                max.ifPresent(counter::set);
            }
        } catch (Exception ignored) {
        }
    }

    private void save() throws IOException {
        Path path = resolveStoragePath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), patches);
    }

    private Path resolveStoragePath() {
        return storagePath;
    }

    private PatchProposal normalizeProposal(PatchProposal proposal) {
        if (proposal == null) return null;

        if (proposal.getFiles() == null) {
            proposal.setFiles(new ArrayList<>());
        }

        // Multi-file compatibility: if files are empty but legacy fields exist, populate files
        if ((proposal.getFiles() == null || proposal.getFiles().isEmpty()) && proposal.getFilePath() != null) {
            PatchFileChange change = new PatchFileChange();
            change.setFilePath(proposal.getFilePath());
            change.setEdits(proposal.getEdits());
            change.setPreview(proposal.getPreview());
            proposal.setFiles(new ArrayList<>(List.of(change)));
        }

        // Maintain legacy fields for downstream compatibility
        if (proposal.getFiles() != null && !proposal.getFiles().isEmpty()) {
            PatchFileChange first = proposal.getFiles().get(0);
            if (proposal.getFilePath() == null || proposal.getFilePath().isBlank()) {
                proposal.setFilePath(first.getFilePath());
            }
            if (proposal.getEdits() == null || proposal.getEdits().isEmpty()) {
                proposal.setEdits(first.getEdits());
            }
        }

        if (proposal.getProvenance() == null) {
            PatchProvenance provenance = new PatchProvenance();
            provenance.setAuthor(System.getProperty("user.name", "user"));
            provenance.setSource("manual");
            proposal.setProvenance(provenance);
        }

        if (proposal.getAuditLog() == null) {
            proposal.setAuditLog(new ArrayList<>());
        }

        return proposal;
    }

    private void appendAudit(PatchProposal proposal, String action, String status, String message) {
        if (proposal == null) return;
        PatchAuditEntry entry = new PatchAuditEntry();
        entry.setAction(action);
        entry.setStatus(status);
        entry.setMessage(message);
        entry.setTimestamp(System.currentTimeMillis());
        entry.setActor(System.getProperty("user.name", "user"));
        proposal.getAuditLog().add(entry);
    }

    private void computeDiffs(PatchProposal proposal) {
        if (proposal == null || proposal.getFiles() == null) return;
        for (PatchFileChange change : proposal.getFiles()) {
            try {
                FileChangeComputation computation = computeFileChange(change);
                change.setDiff(computation.diff());
            } catch (Exception e) {
                change.setDiff("Unable to compute diff: " + e.getMessage());
            }
        }
    }

    private FileChangeComputation computeFileChange(PatchFileChange change) throws IOException {
        if (change == null || change.getFilePath() == null || change.getFilePath().isBlank()) {
            throw new IllegalArgumentException("File path is required");
        }
        List<String> original = workspaceService.readFileLines(change.getFilePath());
        List<String> patched = workspaceService.applyEditsInMemory(new ArrayList<>(original), change.getEdits());
        String diff = generateUnifiedDiff(change.getFilePath(), original, patched);
        return new FileChangeComputation(change.getFilePath(), original, patched, diff);
    }

    private String generateUnifiedDiff(String filePath, List<String> original, List<String> patched) {
        var patch = DiffUtils.diff(original, patched);
        List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(
            filePath,
            filePath,
            original,
            patch,
            3
        );
        return String.join("\n", unified);
    }

    public record FileChangeComputation(String filePath, List<String> original, List<String> patched, String diff) {}

    public static class ApplyOutcome {
        private final boolean success;
        private final PatchProposal proposal;
        private final List<FileApplyResult> fileResults;
        private final String errorMessage;

        private ApplyOutcome(boolean success, PatchProposal proposal, String errorMessage, List<FileApplyResult> fileResults) {
            this.success = success;
            this.proposal = proposal;
            this.errorMessage = errorMessage;
            this.fileResults = fileResults != null ? fileResults : new ArrayList<>();
        }

        public static ApplyOutcome success(PatchProposal proposal, List<FileApplyResult> fileResults) {
            return new ApplyOutcome(true, proposal, null, fileResults);
        }

        public static ApplyOutcome failure(PatchProposal proposal, String message, List<FileApplyResult> fileResults) {
            return new ApplyOutcome(false, proposal, message, fileResults);
        }

        public boolean isSuccess() {
            return success;
        }

        public PatchProposal getProposal() {
            return proposal;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public List<FileApplyResult> getFileResults() {
            return fileResults;
        }
    }

    public static class FileApplyResult {
        private final String filePath;
        private final boolean applied;
        private final String message;

        public FileApplyResult(String filePath, boolean applied, String message) {
            this.filePath = filePath;
            this.applied = applied;
            this.message = message;
        }

        public static FileApplyResult success(String filePath, String message) {
            return new FileApplyResult(filePath, true, message);
        }

        public static FileApplyResult failure(String filePath, String message) {
            return new FileApplyResult(filePath, false, message);
        }

        public String getFilePath() {
            return filePath;
        }

        public boolean isApplied() {
            return applied;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class PatchCleanupResult {
        private final int removedCount;
        private final int eligibleCount;
        private final long cutoffTime;
        private final boolean dryRun;
        private final List<String> eligibleIds;
        private final List<String> statuses;

        public PatchCleanupResult(int removedCount, int eligibleCount, long cutoffTime, boolean dryRun,
                                  List<String> eligibleIds, List<String> statuses) {
            this.removedCount = removedCount;
            this.eligibleCount = eligibleCount;
            this.cutoffTime = cutoffTime;
            this.dryRun = dryRun;
            this.eligibleIds = eligibleIds != null ? eligibleIds : new ArrayList<>();
            this.statuses = statuses != null ? statuses : new ArrayList<>();
        }

        public int getRemovedCount() {
            return removedCount;
        }

        public int getEligibleCount() {
            return eligibleCount;
        }

        public long getCutoffTime() {
            return cutoffTime;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public List<String> getEligibleIds() {
            return eligibleIds;
        }

        public List<String> getStatuses() {
            return statuses;
        }
    }
}
