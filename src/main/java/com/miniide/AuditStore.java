package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.AuditIndexEntry;
import com.miniide.models.AuditIndexFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AuditStore {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
        .withLocale(Locale.US)
        .withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final Path auditRoot;

    public AuditStore(Path workspaceRoot, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.auditRoot = workspaceRoot.resolve(".control-room").resolve("audit").resolve("issues");
    }

    public Path getAuditRoot() {
        return auditRoot;
    }

    public AuditEntry writePacket(String issueId, String packetId, String json) throws IOException {
        return writeEntry(issueId, packetId, "packet", "json", json);
    }

    public AuditEntry writeReceipt(String issueId, String packetId, String json) throws IOException {
        return writeEntry(issueId, packetId, "receipt", "json", json);
    }

    public AuditEntry writeReport(String issueId, String packetId, String content) throws IOException {
        return writeEntry(issueId, packetId, "report", "md", content);
    }

    public List<AuditEntry> listIssueEntries(String issueId) throws IOException {
        Path issueDir = issueDirectory(issueId);
        if (!Files.exists(issueDir)) {
            return List.of();
        }
        AuditIndexFile index = loadIndex(issueDir);
        if (index != null && index.getEntries() != null && !index.getEntries().isEmpty()) {
            return index.getEntries().stream()
                .map(entry -> new AuditEntry(entry.getKind(), entry.getPacketId(), entry.getTimestamp(), entry.getFilename(),
                    issueDir.resolve(entry.getFilename()).toString()))
                .toList();
        }
        List<AuditEntry> entries = new ArrayList<>();
        try (var stream = Files.list(issueDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                AuditEntry entry = parseEntry(path);
                if (entry != null) {
                    entries.add(entry);
                }
            });
        }
        entries.sort(Comparator.comparing(AuditEntry::getTimestamp).thenComparing(AuditEntry::getFilename));
        return entries;
    }

    private AuditEntry writeEntry(String issueId, String packetId, String kind, String extension, String content) throws IOException {
        if (issueId == null || issueId.isBlank()) {
            throw new IOException("issueId required");
        }
        if (packetId == null || packetId.isBlank()) {
            throw new IOException("packetId required");
        }
        String timestamp = FILE_TS.format(Instant.now());
        String safeIssue = sanitizeSegment(issueId);
        String safePacket = sanitizeSegment(packetId);
        Path issueDir = auditRoot.resolve(safeIssue);
        Files.createDirectories(issueDir);
        String filename = String.format("%s__%s__%s.%s", timestamp, kind, safePacket, extension);
        Path target = issueDir.resolve(filename);
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        AuditEntry entry = new AuditEntry(kind, safePacket, timestamp, filename, target.toString());
        appendIndex(issueDir, entry);
        return entry;
    }

    private Path issueDirectory(String issueId) {
        return auditRoot.resolve(sanitizeSegment(issueId));
    }

    private String sanitizeSegment(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private AuditEntry parseEntry(Path path) {
        String filename = path.getFileName().toString();
        if ("index.json".equals(filename)) {
            return null;
        }
        String[] parts = filename.split("__");
        if (parts.length < 3) {
            return null;
        }
        String timestamp = parts[0];
        String kind = parts[1];
        String packetPart = parts[2];
        int dot = packetPart.lastIndexOf('.');
        String packetId = dot >= 0 ? packetPart.substring(0, dot) : packetPart;
        return new AuditEntry(kind, packetId, timestamp, filename, path.toString());
    }

    private AuditIndexFile loadIndex(Path issueDir) {
        if (objectMapper == null) {
            return null;
        }
        Path indexPath = issueDir.resolve("index.json");
        if (!Files.exists(indexPath)) {
            return null;
        }
        try {
            AuditIndexFile index = objectMapper.readValue(indexPath.toFile(), AuditIndexFile.class);
            return index != null ? index : new AuditIndexFile();
        } catch (Exception e) {
            return null;
        }
    }

    private void appendIndex(Path issueDir, AuditEntry entry) {
        if (objectMapper == null || entry == null) {
            return;
        }
        Path indexPath = issueDir.resolve("index.json");
        AuditIndexFile index = loadIndex(issueDir);
        if (index == null) {
            index = new AuditIndexFile();
        }
        List<AuditIndexEntry> entries = index.getEntries();
        entries.add(new AuditIndexEntry(entry.getKind(), entry.getPacketId(), entry.getTimestamp(), entry.getFilename()));
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexPath.toFile(), index);
        } catch (Exception ignored) {
        }
    }

    public static final class AuditEntry {
        private final String kind;
        private final String packetId;
        private final String timestamp;
        private final String filename;
        private final String path;

        public AuditEntry(String kind, String packetId, String timestamp, String filename, String path) {
            this.kind = kind;
            this.packetId = packetId;
            this.timestamp = timestamp;
            this.filename = filename;
            this.path = path;
        }

        public String getKind() {
            return kind;
        }

        public String getPacketId() {
            return packetId;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getFilename() {
            return filename;
        }

        public String getPath() {
            return path;
        }
    }
}
