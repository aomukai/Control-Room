package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.CreditStore;
import com.miniide.IssueMemoryService;
import com.miniide.ProjectContext;
import com.miniide.models.Comment;
import com.miniide.models.CreditEvent;
import com.miniide.models.Issue;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Controller for issue management.
 */
public class IssueController implements Controller {

    private final IssueMemoryService issueService;
    private final ProjectContext projectContext;
    private final CreditStore creditStore;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;
    private static final Map<String, Double> COMMENT_CREDIT_AMOUNTS = Map.ofEntries(
        Map.entry("evidence-verified", 1.0),
        Map.entry("evidence-verified-precise", 2.0),
        Map.entry("evidence-outcome-upgrade", 1.0),
        Map.entry("consulted-devils-advocate", 1.0),
        Map.entry("issue-applied-in-work", 2.0),
        Map.entry("comment-under-budget", 1.0),
        Map.entry("proposal-accepted-by-user", 3.0),
        Map.entry("user-marked-helpful", 2.0),
        Map.entry("clean-unfreeze", 1.0),
        Map.entry("moderator-commendation", 1.0),
        Map.entry("evidence-failed-verification", -2.0),
        Map.entry("circuit-breaker-triggered", -1.0),
        Map.entry("issue-marked-leech", -1.0),
        Map.entry("hallucination-detected", -3.0),
        Map.entry("moderator-penalty", -1.0)
    );
    private static final Set<String> SYSTEM_ONLY_REASONS = Set.of(
        "evidence-verified",
        "evidence-verified-precise",
        "evidence-outcome-upgrade",
        "evidence-failed-verification",
        "circuit-breaker-triggered",
        "hallucination-detected"
    );
    private static final Set<String> MODERATOR_REASONS = Set.of(
        "moderator-commendation",
        "moderator-penalty",
        "clean-unfreeze"
    );

    public IssueController(IssueMemoryService issueService, ProjectContext projectContext,
                           CreditStore creditStore, ObjectMapper objectMapper) {
        this.issueService = issueService;
        this.projectContext = projectContext;
        this.creditStore = creditStore;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/issues", this::getIssues);
        app.get("/api/issues/{id}", this::getIssue);
        app.post("/api/issues", this::createIssue);
        app.put("/api/issues/{id}", this::updateIssue);
        app.delete("/api/issues/{id}", this::deleteIssue);
        app.post("/api/issues/{id}/comments", this::addComment);
    }

    private void getIssues(Context ctx) {
        try {
            String tag = ctx.queryParam("tag");
            String assignedTo = ctx.queryParam("assignedTo");
            String status = ctx.queryParam("status");

            List<Issue> issues;

            if (tag != null && !tag.isBlank()) {
                issues = issueService.listIssuesByTag(tag);
            } else if (assignedTo != null && !assignedTo.isBlank()) {
                issues = issueService.listIssuesByAssignee(assignedTo);
            } else {
                issues = issueService.listIssues();
            }

            if (status != null && !status.isBlank()) {
                List<Issue> filtered = new ArrayList<>();
                for (Issue issue : issues) {
                    if (status.equalsIgnoreCase(issue.getStatus())) {
                        filtered.add(issue);
                    }
                }
                issues = filtered;
            }

            ctx.json(issues);
        } catch (Exception e) {
            logger.error("Error getting issues: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void getIssue(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Optional<Issue> issue = issueService.getIssue(id);

            if (issue.isPresent()) {
                ctx.json(issue.get());
            } else {
                ctx.status(404).json(Map.of("error", "Issue not found: #" + id));
            }
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
        } catch (Exception e) {
            logger.error("Error getting issue: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void createIssue(Context ctx) {
        try {
            JsonNode json = objectMapper.readTree(ctx.body());

            String title = json.has("title") ? json.get("title").asText() : null;
            if (title == null || title.isBlank()) {
                ctx.status(400).json(Map.of("error", "Title is required"));
                return;
            }

            String body = json.has("body") ? json.get("body").asText() : "";
            String openedBy = json.has("openedBy") ? json.get("openedBy").asText() : "user";
            String assignedTo = json.has("assignedTo") ? json.get("assignedTo").asText() : null;
            String priority = json.has("priority") ? json.get("priority").asText() : "normal";

            List<String> tags = new ArrayList<>();
            if (json.has("tags") && json.get("tags").isArray()) {
                for (JsonNode tagNode : json.get("tags")) {
                    tags.add(tagNode.asText());
                }
            }

            Issue issue = issueService.createIssue(title, body, openedBy, assignedTo, tags, priority);
            logger.info("Issue created via API: #" + issue.getId() + " - " + issue.getTitle());
            ctx.status(201).json(issue);
        } catch (Exception e) {
            logger.error("Error creating issue: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void updateIssue(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Optional<Issue> existing = issueService.getIssue(id);

            if (existing.isEmpty()) {
                ctx.status(404).json(Map.of("error", "Issue not found: #" + id));
                return;
            }

            JsonNode json = objectMapper.readTree(ctx.body());
            Issue issue = existing.get();

            if (json.has("title")) {
                issue.setTitle(json.get("title").asText());
            }
            if (json.has("body")) {
                issue.setBody(json.get("body").asText());
            }
            if (json.has("openedBy")) {
                issue.setOpenedBy(json.get("openedBy").asText());
            }
            if (json.has("assignedTo")) {
                issue.setAssignedTo(json.get("assignedTo").asText());
            }
            if (json.has("priority")) {
                issue.setPriority(json.get("priority").asText());
            }
            String previousStatus = issue.getStatus();
            if (json.has("status")) {
                issue.setStatus(json.get("status").asText());
            }
            if (json.has("tags") && json.get("tags").isArray()) {
                List<String> tags = new ArrayList<>();
                for (JsonNode tagNode : json.get("tags")) {
                    tags.add(tagNode.asText());
                }
                issue.setTags(tags);
            }

            Issue updated = issueService.updateIssue(issue);
            if (shouldAwardIssueCloseCredit(previousStatus, updated.getStatus())) {
                awardIssueClosedCredit(updated);
            }
            logger.info("Issue updated via API: #" + updated.getId());
            ctx.json(updated);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Error updating issue: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void deleteIssue(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            boolean deleted = issueService.deleteIssue(id);

            if (deleted) {
                logger.info("Issue deleted via API: #" + id);
                ctx.json(Map.of("success", true, "message", "Issue #" + id + " deleted"));
            } else {
                ctx.status(404).json(Map.of("error", "Issue not found: #" + id));
            }
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
        } catch (Exception e) {
            logger.error("Error deleting issue: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void addComment(Context ctx) {
        try {
            int issueId = Integer.parseInt(ctx.pathParam("id"));
            JsonNode json = objectMapper.readTree(ctx.body());

            String author = json.has("author") ? json.get("author").asText() : "user";
            String body = json.has("body") ? json.get("body").asText() : null;

            if (body == null || body.isBlank()) {
                ctx.status(400).json(Map.of("error", "Comment body is required"));
                return;
            }

            Comment.CommentAction action = null;
            if (json.has("action") && json.get("action").isObject()) {
                JsonNode actionNode = json.get("action");
                String type = actionNode.has("type") ? actionNode.get("type").asText() : null;
                String details = actionNode.has("details") ? actionNode.get("details").asText() : null;
                if (type != null) {
                    action = new Comment.CommentAction(type, details);
                }
            }

            Comment comment = issueService.addComment(issueId, author, body, action);
            logger.info("Comment added to Issue #" + issueId + " by " + author);
            awardCommentCredit(comment, issueId);
            ctx.status(201).json(comment);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
        } catch (IllegalArgumentException e) {
            ctx.status(404).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Error adding comment: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private boolean shouldAwardIssueCloseCredit(String previousStatus, String nextStatus) {
        if (previousStatus == null || nextStatus == null) {
            return false;
        }
        return !"closed".equalsIgnoreCase(previousStatus)
            && "closed".equalsIgnoreCase(nextStatus);
    }

    private void awardIssueClosedCredit(Issue issue) {
        if (creditStore == null || issue == null) {
            return;
        }
        String agentId = resolveAgentId(issue.getAssignedTo());
        if (agentId == null) {
            agentId = resolveAgentId(issue.getOpenedBy());
        }
        if (agentId == null) {
            return;
        }

        CreditEvent event = new CreditEvent();
        event.setAgentId(agentId);
        event.setAmount(2);
        event.setReason("resolved-issue");
        event.setVerifiedBy("system");
        event.setTimestamp(System.currentTimeMillis());
        CreditEvent.RelatedEntity related = new CreditEvent.RelatedEntity();
        related.setType("issue");
        related.setId(String.valueOf(issue.getId()));
        event.setRelatedEntity(related);

        try {
            creditStore.award(event);
        } catch (Exception e) {
            logger.warn("Failed to award issue close credit: " + e.getMessage());
        }
    }

    private String resolveAgentId(String value) {
        if (value == null || value.isBlank() || projectContext == null) {
            return null;
        }
        String target = value.trim().toLowerCase();
        var agents = projectContext.agents().listAllAgents();
        for (var agent : agents) {
            if (agent == null) {
                continue;
            }
            if (agent.getId() != null && agent.getId().equalsIgnoreCase(target)) {
                return agent.getId();
            }
            if (agent.getName() != null && agent.getName().trim().equalsIgnoreCase(target)) {
                return agent.getId();
            }
        }
        return null;
    }

    private void awardCommentCredit(Comment comment, int issueId) {
        if (creditStore == null || comment == null || comment.getAction() == null) {
            return;
        }
        String reason = comment.getAction().getType();
        if (reason == null) {
            return;
        }
        reason = reason.trim();
        if (!COMMENT_CREDIT_AMOUNTS.containsKey(reason)) {
            return;
        }
        String agentId = resolveAgentId(comment.getAuthor());
        if (agentId == null) {
            return;
        }

        double amount = COMMENT_CREDIT_AMOUNTS.get(reason);
        String verifiedBy = resolveVerifiedBy(reason);

        CreditEvent event = new CreditEvent();
        event.setAgentId(agentId);
        event.setAmount(amount);
        event.setReason(reason);
        event.setVerifiedBy(verifiedBy);
        event.setTimestamp(System.currentTimeMillis());
        CreditEvent.RelatedEntity related = new CreditEvent.RelatedEntity();
        related.setType("comment");
        related.setId(issueId + ":" + comment.getTimestamp());
        event.setRelatedEntity(related);

        try {
            creditStore.award(event);
        } catch (Exception e) {
            logger.warn("Failed to award comment credit: " + e.getMessage());
        }
    }

    private String resolveVerifiedBy(String reason) {
        if (SYSTEM_ONLY_REASONS.contains(reason)) {
            return "system";
        }
        if (MODERATOR_REASONS.contains(reason)) {
            return "moderator";
        }
        if ("user-marked-helpful".equals(reason) || "proposal-accepted-by-user".equals(reason)) {
            return "user";
        }
        return "system";
    }
}
