package com.miniide.tools;

public class ToolExecutionResult {
    private final String output;
    private final boolean ok;
    private final String error;
    private final String receiptId;

    public ToolExecutionResult(String output, boolean ok, String error, String receiptId) {
        this.output = output;
        this.ok = ok;
        this.error = error;
        this.receiptId = receiptId;
    }

    public static ToolExecutionResult ok(String output) {
        return new ToolExecutionResult(output, true, null, null);
    }

    public static ToolExecutionResult error(String output, String error) {
        return new ToolExecutionResult(output, false, error, null);
    }

    public static ToolExecutionResult withReceipt(String output, boolean ok, String error, String receiptId) {
        return new ToolExecutionResult(output, ok, error, receiptId);
    }

    public String getOutput() {
        return output;
    }

    public boolean isOk() {
        return ok;
    }

    public String getError() {
        return error;
    }

    public String getReceiptId() {
        return receiptId;
    }
}
