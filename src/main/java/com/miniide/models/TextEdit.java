package com.miniide.models;

/**
 * Represents a text edit operation for applying patches.
 * Used by WorkspaceService.applyPatch() for future AI-assisted editing.
 */
public class TextEdit {
    private int startLine;   // 1-based line number
    private int endLine;     // 1-based line number (inclusive)
    private String newText;  // replacement text

    public TextEdit() {}

    public TextEdit(int startLine, int endLine, String newText) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.newText = newText;
    }

    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }

    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }

    public String getNewText() { return newText; }
    public void setNewText(String newText) { this.newText = newText; }
}
