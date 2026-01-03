package com.miniide.models;

/**
 * Represents a specific representation level of a memory item.
 */
public class MemoryVersion {

    private String id;
    private String memoryItemId;
    private int repLevel;
    private String content;
    private String derivedFromVersionId;
    private String derivationKind; // compress | normalize | summarize | trace | merge | split
    private long createdAt;
    private Double qualityScore;

    public MemoryVersion() {
        // Default constructor for Jackson
    }

    public MemoryVersion(String id, String memoryItemId, int repLevel, String content, long createdAt) {
        this.id = id;
        this.memoryItemId = memoryItemId;
        this.repLevel = repLevel;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMemoryItemId() {
        return memoryItemId;
    }

    public void setMemoryItemId(String memoryItemId) {
        this.memoryItemId = memoryItemId;
    }

    public int getRepLevel() {
        return repLevel;
    }

    public void setRepLevel(int repLevel) {
        this.repLevel = repLevel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDerivedFromVersionId() {
        return derivedFromVersionId;
    }

    public void setDerivedFromVersionId(String derivedFromVersionId) {
        this.derivedFromVersionId = derivedFromVersionId;
    }

    public String getDerivationKind() {
        return derivationKind;
    }

    public void setDerivationKind(String derivationKind) {
        this.derivationKind = derivationKind;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
    }
}
