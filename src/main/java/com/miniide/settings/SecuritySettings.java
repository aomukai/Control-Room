package com.miniide.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SecuritySettings {
    private String keysSecurityMode = "plaintext";
    private long updatedAt = System.currentTimeMillis();

    public String getKeysSecurityMode() {
        return keysSecurityMode;
    }

    public void setKeysSecurityMode(String keysSecurityMode) {
        this.keysSecurityMode = keysSecurityMode;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
