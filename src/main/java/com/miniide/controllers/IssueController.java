package com.miniide.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.AppLogger;
import com.miniide.CircuitBreakerConfig;
import com.miniide.CircuitBreakerValidator;
import com.miniide.CreditStore;
import com.miniide.IssueMemoryService;
import com.miniide.NotificationStore;
import com.miniide.ProjectContext;
import com.miniide.models.Comment;
import com.miniide.models.CreditEvent;
import com.miniide.models.Issue;
import com.miniide.models.PatchProposal;
import com.miniide.models.Agent;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final NotificationStore notificationStore;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;
    private final CircuitBreakerConfig circuitBreakerConfig;
    private final CircuitBreakerValidator circuitBreakerValidator;
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
    private static final Set<String> EVIDENCE_REASONS = Set.of(
        "evidence-verified",
        "evidence-verified-precise",
        "evidence-outcome-upgrade",
        "evidence-failed-verification"
    );
    private static final Set<String> MODERATOR_REASONS = Set.of(
        "moderator-commendation",
        "moderator-penalty",
        "clean-unfreeze"
    );

    public IssueController(IssueMemoryService issueService, ProjectContext projectContext,
                           CreditStore creditStore, NotificationStore notificationStore, ObjectMapper objectMapper) {
        this.issueService = issueService;
        this.projectContext = projectContext;
        this.creditStore = creditStore;
        this.notificationStore = notificationStore;
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
        this.circuitBreakerConfig = new CircuitBreakerConfig();
        this.circuitBreakerValidator = new CircuitBreakerValidator();
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/issues", this::getIssues);
        app.get("/api/issues/{id}", this::getIssue);
        app.post("/api/issues", this::createIssue);
        app.put("/api/issues/{id}", this::updateIssue);
        app.delete("/api/issues/{id}", this::deleteIssue);
        app.post("/api/issues/{id}/comments", this::addComment);
        app.post("/api/issues/{id}/patches", this::createIssuePatch);
        app.post("/api/issues/{id}/revive", this::reviveIssue);
        app.post("/api/issues/decay", this::runIssueDecay);
    }

    private void createIssuePatch(Context ctx) {
        try {
            int issueId = Integer.parseInt(ctx.pathParam("id"));
            Optional<Issue> issueOpt = issueService.getIssue(issueId);
            if (issueOpt.isEmpty()) {
                ctx.status(404).json(Map.of("error", "Issue not found: #" + issueId));
                return;
            }

            JsonNode json = objectMapper.readTree(ctx.body());
            PatchProposal proposal = objectMapper.convertValue(json, PatchProposal.class);
            proposal.setIssueId(String.valueOf(issueId));

            PatchProposal created = projectContext.patches().create(proposal);
            sendPatchNotification(created);
            createPatchIssueComment(issueId, created);
            ctx.status(201).json(created);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
        } catch (Exception e) {
            logger.error("Error creating patch for issue: " + e.getMessage(), e);
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private void getIssues(Context ctx) {
        try {
            String tag = ctx.queryParam("tag");
            String assignedTo = ctx.queryParam("assignedTo");
            String status = ctx.queryParam("status");
            String priority = ctx.queryParam("priority");
            Integer minInterestLevel = parseIntQuery(ctx, "minInterestLevel");

            List<Issue> issues;

            if (tag != null && !tag.isBlank()) {
                issues = issueService.listIssuesByTag(tag);
            } else if (assignedTo != null && !assignedTo.isBlank()) {
                issues = issueService.listIssuesByAssignee(assignedTo);
            } else if (priority != null && !priority.isBlank()) {
                issues = issueService.listIssuesByPriority(priority);
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

            if (minInterestLevel != null) {
                List<Issue> filtered = new ArrayList<>();
                for (Issue issue : issues) {
                    Integer level = issue.getMemoryLevel();
                    int normalized = level != null ? level : 3;
                    if (normalized >= minInterestLevel) {
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
                issueService.recordAccess(id);
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
            String epistemicStatus = json.has("epistemicStatus") ? json.get("epistemicStatus").asText() : null;

            List<String> tags = new ArrayList<>();
            if (json.has("tags") && json.get("tags").isArray()) {
                for (JsonNode tagNode : json.get("tags")) {
                    tags.add(tagNode.asText());
                }
            }

            Issue issue = issueService.createIssue(title, body, openedBy, assignedTo, tags, priority);
            if (epistemicStatus != null && !epistemicStatus.isBlank()) {
                issue.setEpistemicStatus(epistemicStatus);
                issue = issueService.updateIssue(issue);
            }
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
            if (json.has("epistemicStatus")) {
                JsonNode statusNode = json.get("epistemicStatus");
                if (statusNode == null || statusNode.isNull()) {
                    issue.setEpistemicStatus(null);
                } else {
                    issue.setEpistemicStatus(statusNode.asText());
                }
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

    private void reviveIssue(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Issue revived = issueService.reviveIssue(id);
            ctx.json(revived);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Invalid issue ID format"));
        } catch (IllegalArgumentException e) {
            ctx.status(404).json(Controller.errorBody(e));
        } catch (Exception e) {
            logger.error("Error reviving issue: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void runIssueDecay(Context ctx) {
        try {
            boolean dryRun = false;
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode json = objectMapper.readTree(ctx.body());
                if (json.has("dryRun")) {
                    dryRun = json.get("dryRun").asBoolean(false);
                }
            }
            IssueMemoryService.DecayResult result = issueService.runDecay(dryRun);
            ctx.json(Map.of(
                "decayed", result.getDecayed(),
                "updatedIssueIds", result.getUpdatedIssueIds(),
                "dryRun", dryRun
            ));
        } catch (Exception e) {
            logger.error("Error running issue decay: " + e.getMessage());
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private Integer parseIntQuery(Context ctx, String key) {
        String raw = ctx.queryParam(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
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

            Optional<Issue> issueOpt = issueService.getIssue(issueId);
            if (issueOpt.isEmpty()) {
                ctx.status(404).json(Map.of("error", "Issue not found: #" + issueId));
                return;
            }
            Issue issue = issueOpt.get();
            if (isIssueFrozen(issue) && !canActOnFrozenIssue(author, issue)) {
                ctx.status(403).json(Map.of("error", "Issue is frozen until " + issue.getFrozenUntil()));
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

            String impactLevel = json.has("impactLevel") ? json.get("impactLevel").asText(null) : null;
            Comment.CommentEvidence evidence = parseEvidence(json.get("evidence"));

            Comment comment = new Comment(author, body, System.currentTimeMillis(), action);
            comment.setImpactLevel(impactLevel);
            comment.setEvidence(evidence);

            if (!isUserAuthor(comment) && !shouldSkipCircuitBreaker(comment, issue)) {
                CircuitBreakerValidator.ValidationResult validation = circuitBreakerValidator
                    .validateComment(comment, issue, circuitBreakerConfig);
                if (!validation.isValid()) {
                    ctx.status(400).json(Map.of(
                        "error", "Comment rejected by circuit breaker validation",
                        "violations", validation.getViolations()
                    ));
                    return;
                }
                if (validation.isShouldFreeze()) {
                    CircuitBreakerValidator.Violation violation = validation.getViolations().stream()
                        .filter(v -> "freeze".equalsIgnoreCase(v.getSeverity()))
                        .findFirst()
                        .orElse(null);
                    Issue frozen = freezeIssue(issue, violation);
                    ctx.status(409).json(Map.of(
                        "error", "Issue frozen by circuit breaker",
                        "issueId", frozen.getId(),
                        "frozenUntil", frozen.getFrozenUntil(),
                        "reason", frozen.getFrozenReason(),
                        "violations", validation.getViolations()
                    ));
                    return;
                }
            }

            Comment stored = issueService.addComment(issueId, author, body, action, impactLevel, evidence);
            logger.info("Comment added to Issue #" + issueId + " by " + author);
            awardCommentCredit(stored, issueId);
            ctx.status(201).json(stored);
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

    private void sendPatchNotification(PatchProposal proposal) {
        if (notificationStore == null || proposal == null) return;
        String fileLabel = proposal.getFiles() != null && !proposal.getFiles().isEmpty()
            ? proposal.getFiles().get(0).getFilePath()
            : proposal.getFilePath();
        String projectName = null;
        try {
            projectName = projectContext.workspace().loadMetadata().getDisplayName();
        } catch (Exception ignored) {
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("kind", "review-patch");
        payload.put("patchId", proposal.getId());
        payload.put("issueId", proposal.getIssueId());
        payload.put("filePath", fileLabel);
        payload.put("filePaths", proposal.getFiles() != null
            ? proposal.getFiles().stream().map(f -> f.getFilePath()).collect(java.util.stream.Collectors.toList())
            : List.of());
        payload.put("patchTitle", proposal.getTitle());
        if (proposal.getProvenance() != null) {
            payload.put("provenance", proposal.getProvenance());
        }
        if (projectName != null && !projectName.isBlank()) {
            payload.put("projectName", projectName);
        }
        notificationStore.push(
            com.miniide.models.Notification.Level.INFO,
            com.miniide.models.Notification.Scope.EDITOR,
            "Patch proposed for " + fileLabel,
            proposal.getDescription() != null ? proposal.getDescription() : "",
            com.miniide.models.Notification.Category.ATTENTION,
            true,
            "Review Patch",
            payload,
            "patch"
        );
    }

    private void createPatchIssueComment(int issueId, PatchProposal proposal) {
        if (proposal == null) return;
        String title = proposal.getTitle();
        String message = title != null && !title.isBlank()
            ? "Patch proposed: " + title
            : "Patch proposed: " + proposal.getId();
        String author = "system";
        if (proposal.getProvenance() != null && proposal.getProvenance().getAgent() != null
            && !proposal.getProvenance().getAgent().isBlank()) {
            author = proposal.getProvenance().getAgent();
        } else if (proposal.getProvenance() != null && proposal.getProvenance().getAuthor() != null
            && !proposal.getProvenance().getAuthor().isBlank()) {
            author = proposal.getProvenance().getAuthor();
        }
        Comment.CommentAction action = new Comment.CommentAction(
            "patch-proposed",
            "patchId: " + proposal.getId()
        );
        try {
            issueService.addComment(issueId, author, message, action, null, null);
        } catch (Exception e) {
            logger.warn("Failed to log patch proposal on Issue #" + issueId + ": " + e.getMessage());
        }
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
        if (SYSTEM_ONLY_REASONS.contains(reason) && !isSystemAuthor(comment)) {
            logger.warn("Skipped system-only credit reason from non-system comment: " + reason);
            return;
        }
        if (MODERATOR_REASONS.contains(reason) && !isModeratorAuthor(comment)) {
            logger.warn("Skipped moderator-only credit reason from non-moderator comment: " + reason);
            return;
        }
        String agentId = resolveAgentId(comment.getAuthor());
        if (agentId == null) {
            return;
        }

        double amount = COMMENT_CREDIT_AMOUNTS.get(reason);
        String verifiedBy = resolveVerifiedBy(reason);
        CreditEvent.CreditContext context = null;
        if (EVIDENCE_REASONS.contains(reason)) {
            context = parseCreditContext(comment.getAction().getDetails());
            if (context == null || isBlank(context.getTrigger())) {
                logger.warn("Skipped evidence credit without trigger context: " + reason);
                return;
            }
            if ("evidence-outcome-upgrade".equals(reason) && isBlank(context.getOutcome())) {
                logger.warn("Skipped evidence-outcome-upgrade without outcome context");
                return;
            }
            if (isBlank(context.getOutcome())) {
                context.setOutcome("no-action-yet");
            }
        }

        CreditEvent event = new CreditEvent();
        event.setAgentId(agentId);
        event.setAmount(amount);
        event.setReason(reason);
        event.setVerifiedBy(verifiedBy);
        event.setTimestamp(System.currentTimeMillis());
        if (context != null) {
            event.setContext(context);
        }
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

    private boolean isSystemAuthor(Comment comment) {
        String author = comment != null ? comment.getAuthor() : null;
        return author != null && author.trim().equalsIgnoreCase("system");
    }

    private boolean isModeratorAuthor(Comment comment) {
        String author = comment != null ? comment.getAuthor() : null;
        return author != null && author.trim().equalsIgnoreCase("moderator");
    }

    private boolean isUserAuthor(Comment comment) {
        String author = comment != null ? comment.getAuthor() : null;
        return author != null && author.trim().equalsIgnoreCase("user");
    }

    private boolean shouldSkipCircuitBreaker(Comment comment, Issue issue) {
        if (comment == null || issue == null) {
            return false;
        }
        if (isSystemAuthor(comment)) {
            return true;
        }
        return isUserFacingIssue(issue)
            || hasTag(issue, "agent-intro")
            || hasTag(issue, "onboarding")
            || hasTag(issue, "welcome")
            || isNarrativeRoleComment(comment);
    }

    private boolean isUserFacingIssue(Issue issue) {
        if (issue == null) {
            return false;
        }
        String openedBy = issue.getOpenedBy();
        String assignedTo = issue.getAssignedTo();
        if (openedBy != null && openedBy.trim().equalsIgnoreCase("user")) {
            return true;
        }
        return assignedTo != null && assignedTo.trim().equalsIgnoreCase("user");
    }

    private boolean hasTag(Issue issue, String tag) {
        if (issue == null || tag == null || tag.isBlank()) {
            return false;
        }
        for (String existing : issue.getTags()) {
            if (existing != null && existing.trim().equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNarrativeRoleComment(Comment comment) {
        String author = comment != null ? comment.getAuthor() : null;
        if (author == null || author.isBlank() || projectContext == null) {
            return false;
        }
        String agentId = resolveAgentId(author);
        if (agentId == null) {
            return false;
        }
        Agent agent = projectContext.agents().getAgent(agentId);
        if (agent == null || agent.getRole() == null) {
            return false;
        }
        String role = agent.getRole().trim().toLowerCase();
        return role.equals("writer") || role.equals("editor") || role.equals("critic");
    }

    private boolean isIssueFrozen(Issue issue) {
        if (issue == null) {
            return false;
        }
        return "frozen".equalsIgnoreCase(issue.getStatus());
    }

    private boolean canActOnFrozenIssue(String author, Issue issue) {
        if (author == null) {
            return false;
        }
        if (!isIssueFrozen(issue)) {
            return true;
        }
        Long until = issue.getFrozenUntil();
        if (until != null && System.currentTimeMillis() > until) {
            return true;
        }
        String role = author.trim().toLowerCase();
        return role.equals("user") || role.equals("moderator") || role.equals("team-lead");
    }

    private Issue freezeIssue(Issue issue, CircuitBreakerValidator.Violation violation) {
        long now = System.currentTimeMillis();
        Issue frozen = issue;
        frozen.setStatus("frozen");
        frozen.setFrozenAt(now);
        frozen.setFrozenUntil(now + circuitBreakerConfig.getFrozenIssueCooldownMinutes() * 60_000L);
        frozen.setFrozenReason(violation != null ? violation.getRule() : "circuit-breaker");
        Issue updated = issueService.updateIssue(frozen);

        String violationMessage = violation != null ? violation.getMessage() : "Circuit breaker triggered.";
        String report = formatFreezeReport(updated, violationMessage);
        issueService.createIssue(
            "[Circuit Breaker] " + updated.getTitle(),
            report,
            "system",
            "moderator",
            List.of("#meta", "#circuit-breaker", "#" + updated.getFrozenReason(), "#issue-" + updated.getId()),
            "high"
        );

        if (notificationStore != null) {
            notificationStore.warning(
                "Issue #" + updated.getId() + " frozen: " + violationMessage,
                com.miniide.models.Notification.Scope.WORKBENCH
            );
        }

        return updated;
    }

    private String formatFreezeReport(Issue issue, String message) {
        StringBuilder report = new StringBuilder();
        report.append("Issue #").append(issue.getId()).append(" was automatically frozen.\n\n");
        report.append("Reason: ").append(message).append("\n");
        report.append("Frozen until: ").append(issue.getFrozenUntil()).append("\n\n");
        report.append("Recent activity:\n");
        List<Comment> comments = issue.getComments();
        int start = Math.max(0, comments.size() - 5);
        for (int i = start; i < comments.size(); i++) {
            Comment comment = comments.get(i);
            String snippet = truncate(comment.getBody(), 100);
            report.append("- ").append(comment.getAuthor()).append(": \"").append(snippet).append("\"\n");
        }
        return report.toString().trim();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private Comment.CommentEvidence parseEvidence(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Comment.CommentEvidence evidence = new Comment.CommentEvidence();

        if (node.has("issues") && node.get("issues").isArray()) {
            List<Integer> issues = new ArrayList<>();
            for (JsonNode issueNode : node.get("issues")) {
                if (issueNode.isInt()) {
                    issues.add(issueNode.asInt());
                }
            }
            evidence.setIssues(issues);
        }

        if (node.has("canonRefs") && node.get("canonRefs").isArray()) {
            List<String> refs = new ArrayList<>();
            for (JsonNode refNode : node.get("canonRefs")) {
                if (refNode.isTextual()) {
                    refs.add(refNode.asText());
                }
            }
            evidence.setCanonRefs(refs);
        }

        if (node.has("files") && node.get("files").isArray()) {
            List<Comment.FileReference> files = new ArrayList<>();
            for (JsonNode fileNode : node.get("files")) {
                if (!fileNode.isObject()) {
                    continue;
                }
                Comment.FileReference ref = new Comment.FileReference();
                if (fileNode.has("path")) {
                    ref.setPath(fileNode.get("path").asText());
                }
                if (fileNode.has("quote")) {
                    ref.setQuote(fileNode.get("quote").asText());
                }
                if (fileNode.has("lines") && fileNode.get("lines").isObject()) {
                    JsonNode lines = fileNode.get("lines");
                    Comment.LineRange range = new Comment.LineRange();
                    if (lines.has("start")) {
                        range.setStart(lines.get("start").asInt());
                    }
                    if (lines.has("end")) {
                        range.setEnd(lines.get("end").asInt());
                    }
                    ref.setLines(range);
                }
                files.add(ref);
            }
            evidence.setFiles(files);
        }

        return evidence;
    }

    private CreditEvent.CreditContext parseCreditContext(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        String trimmed = details.trim();
        CreditEvent.CreditContext context = new CreditEvent.CreditContext();
        boolean setAny = false;

        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                if (node.hasNonNull("trigger")) {
                    context.setTrigger(node.get("trigger").asText());
                    setAny = true;
                }
                if (node.hasNonNull("triggeredBy")) {
                    context.setTriggeredBy(node.get("triggeredBy").asText());
                    setAny = true;
                }
                if (node.hasNonNull("triggerRef")) {
                    context.setTriggerRef(node.get("triggerRef").asText());
                    setAny = true;
                }
                if (node.hasNonNull("outcome")) {
                    context.setOutcome(node.get("outcome").asText());
                    setAny = true;
                }
                if (node.hasNonNull("outcomeRef")) {
                    context.setOutcomeRef(node.get("outcomeRef").asText());
                    setAny = true;
                }
            } catch (Exception ignored) {
                // fall through to string parsing
            }
        }

        if (!setAny) {
            String trigger = extractDetailValue(trimmed, "trigger");
            if (!isBlank(trigger)) {
                context.setTrigger(trigger);
                setAny = true;
            }
            String triggeredBy = extractDetailValue(trimmed, "triggeredBy");
            if (!isBlank(triggeredBy)) {
                context.setTriggeredBy(triggeredBy);
                setAny = true;
            }
            String triggerRef = extractDetailValue(trimmed, "triggerRef");
            if (!isBlank(triggerRef)) {
                context.setTriggerRef(triggerRef);
                setAny = true;
            }
            String outcome = extractDetailValue(trimmed, "outcome");
            if (!isBlank(outcome)) {
                context.setOutcome(outcome);
                setAny = true;
            }
            String outcomeRef = extractDetailValue(trimmed, "outcomeRef");
            if (!isBlank(outcomeRef)) {
                context.setOutcomeRef(outcomeRef);
                setAny = true;
            }
        }

        return setAny ? context : null;
    }

    private String extractDetailValue(String text, String key) {
        if (text == null || key == null) {
            return null;
        }
        String lower = text.toLowerCase();
        String lowerKey = key.toLowerCase();
        int idx = lower.indexOf(lowerKey);
        if (idx < 0) {
            return null;
        }
        int colon = lower.indexOf(':', idx);
        int equals = lower.indexOf('=', idx);
        int sep = colon == -1 ? equals : (equals == -1 ? colon : Math.min(colon, equals));
        if (sep == -1 || sep + 1 >= text.length()) {
            return null;
        }
        String tail = text.substring(sep + 1).trim();
        int end = tail.length();
        for (int i = 0; i < tail.length(); i++) {
            char ch = tail.charAt(i);
            if (ch == ';' || ch == '\n' || ch == ',') {
                end = i;
                break;
            }
        }
        String value = tail.substring(0, end).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
