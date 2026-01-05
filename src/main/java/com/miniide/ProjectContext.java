package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.FileService;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Runtime holder for project-scoped services that need to refresh when
 * the active project/workspace changes.
 */
public class ProjectContext {
    private final ObjectMapper objectMapper;
    private WorkspaceService workspaceService;
    private AgentRegistry agentRegistry;
    private AgentEndpointRegistry agentEndpointRegistry;
    private PatchService patchService;
    private final AppLogger logger = AppLogger.get();

    public ProjectContext(Path workspaceRoot, ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        load(workspaceRoot);
    }

    public synchronized void load(Path workspaceRoot) throws IOException {
        new FileService(workspaceRoot.toString());
        this.workspaceService = new WorkspaceService(workspaceRoot);
        this.agentRegistry = new AgentRegistry(workspaceService.getWorkspaceRoot(), objectMapper);
        this.agentEndpointRegistry = new AgentEndpointRegistry(workspaceService.getWorkspaceRoot(), objectMapper);
        this.patchService = new PatchService(workspaceService);
        logger.info("Project context loaded for " + workspaceRoot);
    }

    public synchronized void switchWorkspace(Path workspaceRoot) throws IOException {
        load(workspaceRoot);
    }

    public WorkspaceService workspace() {
        return workspaceService;
    }

    public AgentRegistry agents() {
        return agentRegistry;
    }

    public AgentEndpointRegistry agentEndpoints() {
        return agentEndpointRegistry;
    }

    public PatchService patches() {
        return patchService;
    }

    public Path currentRoot() {
        return workspaceService != null ? workspaceService.getWorkspaceRoot() : null;
    }
}
