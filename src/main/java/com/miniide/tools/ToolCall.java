package com.miniide.tools;

import java.util.Map;

public class ToolCall {
    private final String name;
    private final Map<String, Object> args;
    private final String raw;
    private final String nonce;

    public ToolCall(String name, Map<String, Object> args, String raw, String nonce) {
        this.name = name;
        this.args = args;
        this.raw = raw;
        this.nonce = nonce;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public String getRaw() {
        return raw;
    }

    public String getNonce() {
        return nonce;
    }
}
