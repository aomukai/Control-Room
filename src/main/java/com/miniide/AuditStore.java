package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.AuditIndexEntry;
import com.miniide.models.AuditIndexFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AuditStore {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
        .withLocale(Locale.US)
        .withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final Path auditBaseRoot;
    private final Path auditRoot;
    private final Path sessionsRoot;
    private final Path secretPath;
    private byte[] secretKey;

    public AuditStore(Path workspaceRoot, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.auditBaseRoot = workspaceRoot.resolve(".control-room").resolve("audit");
        this.auditRoot = auditBaseRoot.resolve("issues");
        this.sessionsRoot = auditBaseRoot.resolve("sessions");
        this.secretPath = auditBaseRoot.resolve("secret.key");
        this.secretKey = loadOrCreateSecret();
    }

    public Path getAuditRoot() {
        return auditRoot;
    }

    public Path getSessionsRoot() {
        return sessionsRoot;
    }

    public String signPayload(String payload) throws IOException {
        if (payload == null) {
            payload = "";
        }
        byte[] key = secretKey != null ? secretKey : loadOrCreateSecret();
        if (key == null) {
            throw new IOException("Audit secret unavailable");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new IOException("Failed to sign payload: " + e.getMessage(), e);
        }
    }

    public void appendSessionToolReceipt(String sessionId, String jsonLine) throws IOException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IOException("sessionId required");
        }
        Path sessionDir = sessionsRoot.resolve(sanitizeSegment(sessionId));
        Files.createDirectories(sessionDir);
        Path target = sessionDir.resolve("tool_receipts.jsonl");
        try (BufferedWriter writer = Files.newBufferedWriter(
            target,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND
        )) {
            writer.write(jsonLine == null ? "" : jsonLine);
            writer.newLine();
        }
    }

    public List<String> listSessionReceiptIds(String sessionId) throws IOException {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        Path target = sessionsRoot.resolve(sanitizeSegment(sessionId)).resolve("tool_receipts.jsonl");
        if (!Files.exists(target)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            try {
                Map<?, ?> node = objectMapper.readValue(line, Map.class);
                Object id = node.get("receipt_id");
                if (id != null) {
                    ids.add(id.toString());
                }
            } catch (Exception ignored) {
            }
        }
        return ids;
    }

    public AuditEntry linkSessionToIssue(String sessionId, String issueId) throws IOException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IOException("sessionId required");
        }
        if (issueId == null || issueId.isBlank()) {
            throw new IOException("issueId required");
        }
        Path sessionFile = sessionsRoot.resolve(sanitizeSegment(sessionId)).resolve("tool_receipts.jsonl");
        if (!Files.exists(sessionFile)) {
            throw new IOException("Session receipts not found");
        }
        String content = Files.readString(sessionFile, StandardCharsets.UTF_8);
        AuditEntry entry = writeEntry(issueId, sessionId, "session-receipts", "jsonl", content);
        String ref = "{\"session_id\":\"" + sessionId + "\",\"source\":\"" + sessionFile + "\"}";
        writeEntry(issueId, sessionId, "session-receipts-ref", "json", ref);
        return entry;
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

    private byte[] loadOrCreateSecret() {
        try {
            Files.createDirectories(auditBaseRoot);
            if (Files.exists(secretPath)) {
                String encoded = Files.readString(secretPath, StandardCharsets.UTF_8).trim();
                if (!encoded.isBlank()) {
                    return Base64.getDecoder().decode(encoded);
                }
            }
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            String encoded = Base64.getEncoder().encodeToString(secret);
            Files.writeString(secretPath, encoded, StandardCharsets.UTF_8);
            return secret;
        } catch (Exception e) {
            return null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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
