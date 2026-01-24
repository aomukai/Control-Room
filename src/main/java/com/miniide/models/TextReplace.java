package com.miniide.models;

/**
 * Represents a substring replacement for patch proposals.
 */
public class TextReplace {
    private String before;
    private String after;
    private Integer occurrence;

    public TextReplace() {}

    public TextReplace(String before, String after, Integer occurrence) {
        this.before = before;
        this.after = after;
        this.occurrence = occurrence;
    }

    public String getBefore() {
        return before;
    }

    public void setBefore(String before) {
        this.before = before;
    }

    public String getAfter() {
        return after;
    }

    public void setAfter(String after) {
        this.after = after;
    }

    public Integer getOccurrence() {
        return occurrence;
    }

    public void setOccurrence(Integer occurrence) {
        this.occurrence = occurrence;
    }
}
