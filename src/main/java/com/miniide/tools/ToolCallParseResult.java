package com.miniide.tools;

public class ToolCallParseResult {
    private final ToolCall call;
    private final String errorCode;
    private final String errorDetail;

    private ToolCallParseResult(ToolCall call, String errorCode, String errorDetail) {
        this.call = call;
        this.errorCode = errorCode;
        this.errorDetail = errorDetail;
    }

    public static ToolCallParseResult call(ToolCall call) {
        return new ToolCallParseResult(call, null, null);
    }

    public static ToolCallParseResult error(String errorCode) {
        return new ToolCallParseResult(null, errorCode, null);
    }

    public static ToolCallParseResult error(String errorCode, String detail) {
        return new ToolCallParseResult(null, errorCode, detail);
    }

    public static ToolCallParseResult noCall() {
        return new ToolCallParseResult(null, null, null);
    }

    public boolean isToolCall() {
        return call != null;
    }

    public ToolCall getCall() {
        return call;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorDetail() {
        return errorDetail;
    }
}
