package com.miniide.models;

public class SearchResult {
    private String file;
    private int line;
    private String preview;

    public SearchResult(String file, int line, String preview) {
        this.file = file;
        this.line = line;
        this.preview = preview;
    }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }

    public String getPreview() { return preview; }
    public void setPreview(String preview) { this.preview = preview; }
}
