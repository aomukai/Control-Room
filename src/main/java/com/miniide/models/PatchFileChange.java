package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents changes for a single file inside a patch proposal.
 */
public class PatchFileChange {
    private String filePath;
    private List<TextEdit> edits = new ArrayList<>();
    private List<TextReplace> replacements = new ArrayList<>();
    private String baseHash;
    private String preview;
    /**
     * Computed unified diff (not necessarily persisted).
     */
    private String diff;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public List<TextEdit> getEdits() {
        return edits;
    }

    public void setEdits(List<TextEdit> edits) {
        this.edits = edits != null ? edits : new ArrayList<>();
    }

    public List<TextReplace> getReplacements() {
        return replacements;
    }

    public void setReplacements(List<TextReplace> replacements) {
        this.replacements = replacements != null ? replacements : new ArrayList<>();
    }

    public String getBaseHash() {
        return baseHash;
    }

    public void setBaseHash(String baseHash) {
        this.baseHash = baseHash;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public String getDiff() {
        return diff;
    }

    public void setDiff(String diff) {
        this.diff = diff;
    }
}
