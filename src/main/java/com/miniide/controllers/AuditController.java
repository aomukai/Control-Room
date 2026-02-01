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
}
