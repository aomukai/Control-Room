package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class AuditIndexFile {
    private List<AuditIndexEntry> entries = new ArrayList<>();

    public List<AuditIndexEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<AuditIndexEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }
}
