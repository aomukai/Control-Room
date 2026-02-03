package com.miniide.tools;

public class ToolExecutionContext {
    private final String sessionId;
    private final String taskId;
    private final String turnId;
    private final String agentId;

    public ToolExecutionContext(String sessionId, String taskId, String turnId, String agentId) {
        this.sessionId = sessionId;
        this.taskId = taskId;
        this.turnId = turnId;
        this.agentId = agentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTurnId() {
        return turnId;
    }

    public String getAgentId() {
        return agentId;
    }
}
