package com.miniide.models;

public class IngestEvidence {
    private String excerptHash;
    private String content;
    private IngestSourceContext originalContext;
    private String ingestedAt;

    public String getExcerptHash() {
        return excerptHash;
    }

    public void setExcerptHash(String excerptHash) {
        this.excerptHash = excerptHash;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public IngestSourceContext getOriginalContext() {
        return originalContext;
    }

    public void setOriginalContext(IngestSourceContext originalContext) {
        this.originalContext = originalContext;
    }

    public String getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(String ingestedAt) {
        this.ingestedAt = ingestedAt;
    }
}
