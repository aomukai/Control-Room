package com.miniide;

import java.util.ArrayList;
import java.util.List;

public class CircuitBreakerConfig {
    private int maxCommentsPerAgentPerIssue = 2;
    private int maxTotalCommentsPerIssue = 10;
    private int minCommentLength = 150;
    private int minUniqueWords = 20;
    private int maxConsecutiveSameAgentPair = 2;
    private String requireEvidenceForImpactLevel = "structural";
    private List<String> escalationKeywords = defaultEscalationKeywords();
    private int maxEscalationKeywordsPerComment = 4;
    private int minTurnsBeforeResolution = 3;
    private int frozenIssueCooldownMinutes = 30;

    public int getMaxCommentsPerAgentPerIssue() {
        return maxCommentsPerAgentPerIssue;
    }

    public void setMaxCommentsPerAgentPerIssue(int maxCommentsPerAgentPerIssue) {
        this.maxCommentsPerAgentPerIssue = maxCommentsPerAgentPerIssue;
    }

    public int getMaxTotalCommentsPerIssue() {
        return maxTotalCommentsPerIssue;
    }

    public void setMaxTotalCommentsPerIssue(int maxTotalCommentsPerIssue) {
        this.maxTotalCommentsPerIssue = maxTotalCommentsPerIssue;
    }

    public int getMinCommentLength() {
        return minCommentLength;
    }

    public void setMinCommentLength(int minCommentLength) {
        this.minCommentLength = minCommentLength;
    }

    public int getMinUniqueWords() {
        return minUniqueWords;
    }

    public void setMinUniqueWords(int minUniqueWords) {
        this.minUniqueWords = minUniqueWords;
    }

    public int getMaxConsecutiveSameAgentPair() {
        return maxConsecutiveSameAgentPair;
    }

    public void setMaxConsecutiveSameAgentPair(int maxConsecutiveSameAgentPair) {
        this.maxConsecutiveSameAgentPair = maxConsecutiveSameAgentPair;
    }

    public String getRequireEvidenceForImpactLevel() {
        return requireEvidenceForImpactLevel;
    }

    public void setRequireEvidenceForImpactLevel(String requireEvidenceForImpactLevel) {
        this.requireEvidenceForImpactLevel = requireEvidenceForImpactLevel;
    }

    public List<String> getEscalationKeywords() {
        return escalationKeywords;
    }

    public void setEscalationKeywords(List<String> escalationKeywords) {
        this.escalationKeywords = escalationKeywords != null ? escalationKeywords : defaultEscalationKeywords();
    }

    public int getMaxEscalationKeywordsPerComment() {
        return maxEscalationKeywordsPerComment;
    }

    public void setMaxEscalationKeywordsPerComment(int maxEscalationKeywordsPerComment) {
        this.maxEscalationKeywordsPerComment = maxEscalationKeywordsPerComment;
    }

    public int getMinTurnsBeforeResolution() {
        return minTurnsBeforeResolution;
    }

    public void setMinTurnsBeforeResolution(int minTurnsBeforeResolution) {
        this.minTurnsBeforeResolution = minTurnsBeforeResolution;
    }

    public int getFrozenIssueCooldownMinutes() {
        return frozenIssueCooldownMinutes;
    }

    public void setFrozenIssueCooldownMinutes(int frozenIssueCooldownMinutes) {
        this.frozenIssueCooldownMinutes = frozenIssueCooldownMinutes;
    }

    private List<String> defaultEscalationKeywords() {
        List<String> keywords = new ArrayList<>();
        keywords.add("URGENT");
        keywords.add("CRUCIAL");
        keywords.add("CRITICAL");
        keywords.add("IMMEDIATELY");
        keywords.add("CATASTROPHIC");
        keywords.add("DISASTER");
        keywords.add("EMERGENCY");
        keywords.add("DOOM");
        keywords.add("COLLAPSE");
        keywords.add("FAILURE");
        return keywords;
    }
}
