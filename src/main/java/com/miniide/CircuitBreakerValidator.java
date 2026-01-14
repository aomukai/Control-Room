package com.miniide;

import com.miniide.models.Comment;
import com.miniide.models.Issue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CircuitBreakerValidator {

    public ValidationResult validateComment(Comment comment, Issue issue, CircuitBreakerConfig config) {
        List<Violation> violations = new ArrayList<>();

        if (comment == null || issue == null) {
            violations.add(new Violation("invalid-input", "reject", "Missing comment or issue."));
            return new ValidationResult(false, violations);
        }

        String author = safeLower(comment.getAuthor());
        int agentCommentCount = 0;
        for (Comment existing : issue.getComments()) {
            if (existing.getAuthor() != null
                && existing.getAuthor().trim().equalsIgnoreCase(comment.getAuthor())) {
                agentCommentCount += 1;
            }
        }

        if (agentCommentCount >= config.getMaxCommentsPerAgentPerIssue()) {
            violations.add(new Violation(
                "comment-budget-exceeded",
                "freeze",
                comment.getAuthor() + " has used their " + config.getMaxCommentsPerAgentPerIssue() + " comments."
            ));
        }

        if (issue.getComments().size() >= config.getMaxTotalCommentsPerIssue()) {
            violations.add(new Violation(
                "issue-comment-limit",
                "freeze",
                "Issue has reached " + config.getMaxTotalCommentsPerIssue() + " comments without resolution."
            ));
        }

        int bodyLength = comment.getBody() != null ? comment.getBody().length() : 0;
        if (bodyLength < config.getMinCommentLength()) {
            violations.add(new Violation(
                "insufficient-substance",
                "reject",
                "Comment too brief (" + bodyLength + "/" + config.getMinCommentLength() + " chars)."
            ));
        }

        int uniqueWords = countUniqueWords(comment.getBody());
        if (uniqueWords < config.getMinUniqueWords()) {
            violations.add(new Violation(
                "low-vocabulary",
                "reject",
                "Comment has low unique word count (" + uniqueWords + "/" + config.getMinUniqueWords() + ")."
            ));
        }

        int escalationCount = countEscalationKeywords(comment.getBody(), config.getEscalationKeywords());
        if (escalationCount > config.getMaxEscalationKeywordsPerComment()) {
            violations.add(new Violation(
                "escalation-language",
                "freeze",
                "High-intensity language detected (" + escalationCount + " escalation keywords)."
            ));
        }

        if (!issue.getComments().isEmpty()) {
            String lastAuthor = safeLower(issue.getComments().get(issue.getComments().size() - 1).getAuthor());
            if (author != null && lastAuthor != null && !author.equals(lastAuthor)) {
                List<Comment> combined = new ArrayList<>(issue.getComments());
                combined.add(comment);
                int pairExchanges = countPairExchanges(combined, author, lastAuthor);
                if (pairExchanges > config.getMaxConsecutiveSameAgentPair()) {
                    violations.add(new Violation(
                        "ping-pong-detected",
                        "freeze",
                        "Back-and-forth pattern detected between " + comment.getAuthor() + " and " + lastAuthor + "."
                    ));
                }
            }
        }

        if (comment.getAction() != null
            && "resolved".equalsIgnoreCase(comment.getAction().getType())
            && (issue.getComments().size() + 1) < config.getMinTurnsBeforeResolution()) {
            violations.add(new Violation(
                "min-turns-before-resolution",
                "reject",
                "Resolution requires at least " + config.getMinTurnsBeforeResolution() + " turns."
            ));
        }

        if (requiresEvidence(comment.getImpactLevel(), config.getRequireEvidenceForImpactLevel())
            && isEvidenceEmpty(comment.getEvidence())) {
            violations.add(new Violation(
                "missing-evidence-for-impact",
                "reject",
                "High-impact comments require evidence."
            ));
        }

        boolean valid = violations.stream().noneMatch(v -> "reject".equalsIgnoreCase(v.getSeverity()));
        boolean shouldFreeze = violations.stream().anyMatch(v -> "freeze".equalsIgnoreCase(v.getSeverity()));
        return new ValidationResult(valid, violations, shouldFreeze);
    }

    private int countUniqueWords(String body) {
        if (body == null || body.isBlank()) {
            return 0;
        }
        Set<String> unique = new HashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b\\w+\\b").matcher(body.toLowerCase());
        while (matcher.find()) {
            String word = matcher.group();
            if (word != null && !word.isBlank()) {
                unique.add(word);
            }
        }
        return unique.size();
    }

    private int countEscalationKeywords(String body, List<String> keywords) {
        if (body == null || body.isBlank() || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        int count = 0;
        String upper = body.toUpperCase();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            if (upper.contains(keyword.toUpperCase())) {
                count += 1;
            }
        }
        return count;
    }

    private int countPairExchanges(List<Comment> comments, String authorA, String authorB) {
        if (comments == null || comments.size() < 2) {
            return 0;
        }
        int count = 0;
        for (int i = comments.size() - 1; i > 0; i--) {
            String curr = safeLower(comments.get(i).getAuthor());
            String prev = safeLower(comments.get(i - 1).getAuthor());
            if (curr == null || prev == null) {
                break;
            }
            boolean isPair = (curr.equals(authorA) && prev.equals(authorB))
                || (curr.equals(authorB) && prev.equals(authorA));
            if (!isPair) {
                break;
            }
            count += 1;
        }
        return count;
    }

    private boolean requiresEvidence(String impactLevel, String threshold) {
        int impactValue = impactLevelValue(impactLevel);
        int thresholdValue = impactLevelValue(threshold);
        if (impactValue <= 0 || thresholdValue <= 0) {
            return false;
        }
        return impactValue >= thresholdValue;
    }

    private boolean isEvidenceEmpty(Comment.CommentEvidence evidence) {
        if (evidence == null) {
            return true;
        }
        boolean hasFiles = evidence.getFiles() != null && !evidence.getFiles().isEmpty();
        boolean hasIssues = evidence.getIssues() != null && !evidence.getIssues().isEmpty();
        boolean hasCanon = evidence.getCanonRefs() != null && !evidence.getCanonRefs().isEmpty();
        return !(hasFiles || hasIssues || hasCanon);
    }

    private int impactLevelValue(String level) {
        if (level == null) return 0;
        switch (level.trim().toLowerCase()) {
            case "cosmetic":
                return 1;
            case "minor":
                return 2;
            case "structural":
                return 3;
            case "canon-changing":
                return 4;
            default:
                return 0;
        }
    }

    private String safeLower(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase();
    }

    public static class Violation {
        private final String rule;
        private final String severity;
        private final String message;

        public Violation(String rule, String severity, String message) {
            this.rule = rule;
            this.severity = severity;
            this.message = message;
        }

        public String getRule() {
            return rule;
        }

        public String getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<Violation> violations;
        private final boolean shouldFreeze;

        public ValidationResult(boolean valid, List<Violation> violations) {
            this(valid, violations, false);
        }

        public ValidationResult(boolean valid, List<Violation> violations, boolean shouldFreeze) {
            this.valid = valid;
            this.violations = violations != null ? violations : new ArrayList<>();
            this.shouldFreeze = shouldFreeze;
        }

        public boolean isValid() {
            return valid;
        }

        public List<Violation> getViolations() {
            return violations;
        }

        public boolean isShouldFreeze() {
            return shouldFreeze;
        }
    }
}
