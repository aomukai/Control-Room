package com.miniide.models;

public class IngestSourceContext {
    private String filename;
    private String heading;
    private int[] byteRange;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public int[] getByteRange() {
        return byteRange;
    }

    public void setByteRange(int[] byteRange) {
        this.byteRange = byteRange;
    }
}
