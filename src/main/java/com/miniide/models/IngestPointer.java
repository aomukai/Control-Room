package com.miniide.models;

public class IngestPointer {
    private String excerptHash;
    private IngestSourceContext originalContext;

    public String getExcerptHash() {
        return excerptHash;
    }

    public void setExcerptHash(String excerptHash) {
        this.excerptHash = excerptHash;
    }

    public IngestSourceContext getOriginalContext() {
        return originalContext;
    }

    public void setOriginalContext(IngestSourceContext originalContext) {
        this.originalContext = originalContext;
    }
}
