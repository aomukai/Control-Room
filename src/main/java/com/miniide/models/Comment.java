package com.miniide.models;

public class Comment {

    private String author;
    private String body;
    private long timestamp;
    private CommentAction action;

    public Comment() {
    }

    public Comment(String author, String body, long timestamp, CommentAction action) {
        this.author = author;
        this.body = body;
        this.timestamp = timestamp;
        this.action = action;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public CommentAction getAction() {
        return action;
    }

    public void setAction(CommentAction action) {
        this.action = action;
    }

    public static class CommentAction {
        private String type;
        private String details;

        public CommentAction() {
        }

        public CommentAction(String type, String details) {
            this.type = type;
            this.details = details;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }
    }
}
