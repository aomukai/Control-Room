package com.miniide.models;

/**
 * Represents a segment of a scene file.
 * Used for future scene-aware editing and AI-assisted segment rewriting.
 */
public class SceneSegment {
    private String id;
    private int start;
    private int end;
    private String content;

    public SceneSegment() {}

    public SceneSegment(String id, int start, int end, String content) {
        this.id = id;
        this.start = start;
        this.end = end;
        this.content = content;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getStart() { return start; }
    public void setStart(int start) { this.start = start; }

    public int getEnd() { return end; }
    public void setEnd(int end) { this.end = end; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
