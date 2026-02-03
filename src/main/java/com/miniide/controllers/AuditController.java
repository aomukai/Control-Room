package com.miniide.controllers;

import com.miniide.AuditStore;
import com.miniide.ProjectContext;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuditController implements Controller {
    private final ProjectContext projectContext;

    public AuditController(ProjectContext projectContext) {
        this.projectContext = projectContext;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/audit/issues/{id}", this::listIssueAudit);
        app.get("/api/audit/issues/{id}/files/{filename}", this::getIssueAuditFile);
        app.get("/api/audit/sessions/{id}/receipts", this::listSessionReceipts);
        app.get("/api/audit/sessions/{id}/tool-receipts", this::getSessionReceiptFile);
        app.post("/api/audit/sessions/{id}/link-issue", this::linkSessionToIssue);
    }

    private void listIssueAudit(Context ctx) {
        if (projectContext == null || projectContext.audit() == null) {
            ctx.status(500).json(Map.of("error", "Audit store unavailable"));
            return;
        }
        String issueId = ctx.pathParam("id");
        try {
            List<AuditStore.AuditEntry> entries = projectContext.audit().listIssueEntries(issueId);
            List<Map<String, Object>> payload = entries.stream()
                .map(entry -> {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("kind", entry.getKind());
                    item.put("packetId", entry.getPacketId());
                    item.put("timestamp", entry.getTimestamp());
                    item.put("filename", entry.getFilename());
                    return item;
                })
                .collect(Collectors.toList());
            ctx.json(payload);
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getIssueAuditFile(Context ctx) {
        if (projectContext == null || projectContext.audit() == null) {
            ctx.status(500).json(Map.of("error", "Audit store unavailable"));
            return;
        }
        String issueId = ctx.pathParam("id");
        String filename = ctx.pathParam("filename");
        if (filename == null || filename.isBlank()) {
            ctx.status(400).json(Map.of("error", "filename required"));
            return;
        }
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            ctx.status(400).json(Map.of("error", "invalid filename"));
            return;
        }
        try {
            java.nio.file.Path issueDir = projectContext.audit().getAuditRoot()
                .resolve(issueId.replaceAll("[^a-zA-Z0-9._-]+", "_"));
            java.nio.file.Path target = issueDir.resolve(filename).normalize();
            if (!target.startsWith(issueDir)) {
                ctx.status(400).json(Map.of("error", "invalid filename"));
                return;
            }
            if (!java.nio.file.Files.exists(target) || !java.nio.file.Files.isRegularFile(target)) {
                ctx.status(404).json(Map.of("error", "file not found"));
                return;
            }
            String content = java.nio.file.Files.readString(target, java.nio.charset.StandardCharsets.UTF_8);
            if (filename.endsWith(".json")) {
                ctx.contentType("application/json");
            } else if (filename.endsWith(".md")) {
                ctx.contentType("text/markdown; charset=utf-8");
            } else {
                ctx.contentType("text/plain; charset=utf-8");
            }
            ctx.result(content);
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void listSessionReceipts(Context ctx) {
        if (projectContext == null || projectContext.audit() == null) {
            ctx.status(500).json(Map.of("error", "Audit store unavailable"));
            return;
        }
        String sessionId = ctx.pathParam("id");
        try {
            List<String> receipts = projectContext.audit().listSessionReceiptIds(sessionId);
            ctx.json(Map.of("sessionId", sessionId, "receipts", receipts));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getSessionReceiptFile(Context ctx) {
        if (projectContext == null || projectContext.audit() == null) {
            ctx.status(500).json(Map.of("error", "Audit store unavailable"));
            return;
        }
        String sessionId = ctx.pathParam("id");
        try {
            java.nio.file.Path sessionDir = projectContext.audit().getSessionsRoot()
                .resolve(sessionId.replaceAll("[^a-zA-Z0-9._-]+", "_"));
            java.nio.file.Path target = sessionDir.resolve("tool_receipts.jsonl").normalize();
            if (!target.startsWith(sessionDir)) {
                ctx.status(400).json(Map.of("error", "invalid session"));
                return;
            }
            if (!java.nio.file.Files.exists(target) || !java.nio.file.Files.isRegularFile(target)) {
                ctx.status(404).json(Map.of("error", "file not found"));
                return;
            }
            String content = java.nio.file.Files.readString(target, java.nio.charset.StandardCharsets.UTF_8);
            ctx.contentType("text/plain; charset=utf-8").result(content);
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void linkSessionToIssue(Context ctx) {
        if (projectContext == null || projectContext.audit() == null) {
            ctx.status(500).json(Map.of("error", "Audit store unavailable"));
            return;
        }
        String sessionId = ctx.pathParam("id");
        String issueId = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(ctx.body());
                if (json.has("issueId")) {
                    issueId = json.get("issueId").asText(null);
                }
            }
        } catch (Exception ignored) {
        }
        if (issueId == null || issueId.isBlank()) {
            ctx.status(400).json(Map.of("error", "issueId required"));
            return;
        }
        try {
            projectContext.audit().linkSessionToIssue(sessionId, issueId);
            ctx.json(Map.of("status", "ok"));
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }
}
