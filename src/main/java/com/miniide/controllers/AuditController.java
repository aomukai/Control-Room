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
}
