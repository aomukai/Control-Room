package com.miniide.models;

public class Comment {

    private String author;
    private String body;
    private long timestamp;
    private CommentAction action;
    private String impactLevel;
    private CommentEvidence evidence;

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

    public String getImpactLevel() {
        return impactLevel;
    }

    public void setImpactLevel(String impactLevel) {
        this.impactLevel = impactLevel;
    }

    public CommentEvidence getEvidence() {
        return evidence;
    }

    public void setEvidence(CommentEvidence evidence) {
        this.evidence = evidence;
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

    public static class CommentEvidence {
        private java.util.List<FileReference> files;
        private java.util.List<Integer> issues;
        private java.util.List<String> canonRefs;

        public java.util.List<FileReference> getFiles() {
            return files;
        }

        public void setFiles(java.util.List<FileReference> files) {
            this.files = files;
        }

        public java.util.List<Integer> getIssues() {
            return issues;
        }

        public void setIssues(java.util.List<Integer> issues) {
            this.issues = issues;
        }

        public java.util.List<String> getCanonRefs() {
            return canonRefs;
        }

        public void setCanonRefs(java.util.List<String> canonRefs) {
            this.canonRefs = canonRefs;
        }
    }

    public static class FileReference {
        private String path;
        private LineRange lines;
        private String quote;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public LineRange getLines() {
            return lines;
        }

        public void setLines(LineRange lines) {
            this.lines = lines;
        }

        public String getQuote() {
            return quote;
        }

        public void setQuote(String quote) {
            this.quote = quote;
        }
    }

    public static class LineRange {
        private int start;
        private Integer end;

        public int getStart() {
            return start;
        }

        public void setStart(int start) {
            this.start = start;
        }

        public Integer getEnd() {
            return end;
        }

        public void setEnd(Integer end) {
            this.end = end;
        }
    }
}
