package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents changes for a single file inside a patch proposal.
 */
public class PatchFileChange {
    private String filePath;
    private List<TextEdit> edits = new ArrayList<>();
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
