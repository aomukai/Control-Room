package com.miniide.models;

public class CanonManifest {
    private int schemaVersion;
    private String preparedAt;
    private int cardCount;
    private String status;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getPreparedAt() {
        return preparedAt;
    }

    public void setPreparedAt(String preparedAt) {
        this.preparedAt = preparedAt;
    }

    public int getCardCount() {
        return cardCount;
    }

    public void setCardCount(int cardCount) {
        this.cardCount = cardCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
