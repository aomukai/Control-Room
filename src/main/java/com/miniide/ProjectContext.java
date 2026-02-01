package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.FileService;
import com.miniide.models.TelemetryConfig;

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
    private TieringService tieringService;
    private IssueInterestService issueInterestService;
    private TelemetryStore telemetryStore;
    private TelemetryConfigStore telemetryConfigStore;
    private PatchService patchService;
    private PromptRegistry promptRegistry;
    private ProjectPreparationService preparationService;
    private PreparedWorkspaceService preparedWorkspaceService;
    private AuditStore auditStore;
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
        this.tieringService = new TieringService(workspaceService.getWorkspaceRoot(), objectMapper, agentRegistry);
        this.issueInterestService = new IssueInterestService(workspaceService.getWorkspaceRoot(), agentRegistry);
        this.telemetryConfigStore = new TelemetryConfigStore(workspaceService.getWorkspaceRoot(), objectMapper);
        TelemetryConfig defaultTelemetryConfig = new TelemetryConfig();
        TelemetryConfig telemetryConfig = telemetryConfigStore.loadOrDefault(defaultTelemetryConfig);
        this.telemetryStore = new TelemetryStore(workspaceService.getWorkspaceRoot(), objectMapper, telemetryConfig);
        this.issueInterestService.setTelemetryStore(telemetryStore);
        this.preparedWorkspaceService = new PreparedWorkspaceService(workspaceService.getWorkspaceRoot(), objectMapper);
        this.patchService = new PatchService(workspaceService, preparedWorkspaceService);
        this.promptRegistry = new PromptRegistry(workspaceService.getWorkspaceRoot(), objectMapper);
        this.preparationService = new ProjectPreparationService(workspaceService.getWorkspaceRoot(), workspaceService, objectMapper);
        this.auditStore = new AuditStore(workspaceService.getWorkspaceRoot(), objectMapper);
        logger.info("Project context loaded for " + workspaceRoot);
    }

    public synchronized void switchWorkspace(Path workspaceRoot) throws IOException {
        load(workspaceRoot);
    }

    public WorkspaceService workspace() {
        return workspaceService;
    }

    public PreparedWorkspaceService preparedWorkspace() {
        return preparedWorkspaceService;
    }

    public ProjectPreparationService preparation() {
        return preparationService;
    }

    public AgentRegistry agents() {
        return agentRegistry;
    }

    public AgentEndpointRegistry agentEndpoints() {
        return agentEndpointRegistry;
    }

    public TieringService tiering() {
        return tieringService;
    }

    public IssueInterestService issueInterest() {
        return issueInterestService;
    }

    public TelemetryStore telemetry() {
        return telemetryStore;
    }

    public TelemetryConfigStore telemetryConfigStore() {
        return telemetryConfigStore;
    }

    public PatchService patches() {
        return patchService;
    }

    public PromptRegistry promptTools() {
        return promptRegistry;
    }

    public AuditStore audit() {
        return auditStore;
    }

    public Path currentRoot() {
        return workspaceService != null ? workspaceService.getWorkspaceRoot() : null;
    }
}
