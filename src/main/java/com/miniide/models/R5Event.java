package com.miniide.models;

import java.util.Map;

/**
 * Raw evidence slice tied to a memory item.
 */
public class R5Event {

    private String id;
    private String memoryItemId;
    private int seq;
    private long ts;
    private String author;
    private String agent;
    private String text;
    private Map<String, Object> meta;

    public R5Event() {
        // Default constructor for Jackson
    }

    public R5Event(String id, String memoryItemId, int seq, long ts, String author, String agent, String text) {
        this.id = id;
        this.memoryItemId = memoryItemId;
        this.seq = seq;
        this.ts = ts;
        this.author = author;
        this.agent = agent;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMemoryItemId() {
        return memoryItemId;
    }

    public void setMemoryItemId(String memoryItemId) {
        this.memoryItemId = memoryItemId;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }
}
