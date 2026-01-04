package com.miniide;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.PatchProposal;
import com.miniide.models.TextEdit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class PatchService {

    private static final String STORAGE_PATH = "data/patches.json";

    private final List<PatchProposal> patches = new ArrayList<>();
    private final AtomicInteger counter = new AtomicInteger(0);
    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkspaceService workspaceService;

    public PatchService(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
        load();
    }

    public synchronized List<PatchProposal> list() {
        List<PatchProposal> sorted = new ArrayList<>(patches);
        sorted.sort(Comparator.comparingLong(PatchProposal::getCreatedAt).reversed());
        return sorted;
    }

    public synchronized PatchProposal get(String id) {
        if (id == null) return null;
        return patches.stream()
            .filter(p -> id.equals(p.getId()))
            .findFirst()
            .orElse(null);
    }

    public synchronized PatchProposal create(PatchProposal proposal) throws IOException {
        if (proposal == null) {
            throw new IllegalArgumentException("Proposal is required");
        }
        if (proposal.getFilePath() == null || proposal.getFilePath().isBlank()) {
            throw new IllegalArgumentException("filePath is required");
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
        patches.add(proposal);
        save();
        return proposal;
    }

    public synchronized PatchProposal apply(String id) throws IOException {
        PatchProposal proposal = get(id);
        if (proposal == null) {
            throw new IllegalArgumentException("Patch not found: " + id);
        }
        if (!"pending".equalsIgnoreCase(proposal.getStatus())) {
            return proposal;
        }
        workspaceService.applyPatch(proposal.getFilePath(), proposal.getEdits());
        proposal.setStatus("applied");
        save();
        return proposal;
    }

    public synchronized PatchProposal reject(String id) throws IOException {
        PatchProposal proposal = get(id);
        if (proposal == null) {
            throw new IllegalArgumentException("Patch not found: " + id);
        }
        proposal.setStatus("rejected");
        save();
        return proposal;
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
        proposal.setTitle("Simulated Patch");
        proposal.setDescription("Simulation-only patch for testing the review modal.");
        proposal.setPreview(String.join("\n", List.of(
            "Simulated edits:",
            "- Replace line 1 with a demo header",
            "- Insert a simulation note after line 3",
            "- Add a footer marker at line 8"
        )));
        proposal.setEdits(edits);
        return create(proposal);
    }

    private void load() {
        Path path = Paths.get(STORAGE_PATH);
        if (!Files.exists(path)) {
            return;
        }
        try {
            List<PatchProposal> stored = mapper.readValue(path.toFile(), new TypeReference<List<PatchProposal>>() {});
            if (stored != null) {
                patches.clear();
                patches.addAll(stored);
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
        Path path = Paths.get(STORAGE_PATH);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), patches);
    }
}
