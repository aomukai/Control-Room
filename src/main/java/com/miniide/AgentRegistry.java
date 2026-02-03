package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.Agent;
import com.miniide.models.AgentAutoActionConfig;
import com.miniide.models.AgentEndpointConfig;
import com.miniide.models.AgentMemoryProfile;
import com.miniide.models.AgentPersonalityConfig;
import com.miniide.models.AgentToolCapability;
import com.miniide.models.AgentsFile;
import com.miniide.models.RoleFreedomSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AgentRegistry {

    private final Path registryPath;
    private final ObjectMapper objectMapper;
    private AgentsFile agentsFile;
    private final AppLogger logger;

    public AgentRegistry(Path workspaceRoot, ObjectMapper objectMapper) {
        this.registryPath = workspaceRoot.resolve(".control-room").resolve("agents").resolve("agents.json");
        this.objectMapper = objectMapper;
        this.logger = AppLogger.get();
        if (hasWorkspaceMarker()) {
            ensureRegistryExists();
            loadFromDisk();
        } else {
            initializeEmptyRegistry();
        }
    }

    public List<Agent> listEnabledAgents() {
        if (agentsFile == null || agentsFile.getAgents() == null) {
            return Collections.emptyList();
        }
        return agentsFile.getAgents().stream()
            .filter(agent -> agent != null && agent.isEnabled())
            .collect(Collectors.toList());
    }

    public List<Agent> listAllAgents() {
        if (agentsFile == null || agentsFile.getAgents() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(agentsFile.getAgents());
    }

    public Agent getAgent(String id) {
        if (agentsFile == null || agentsFile.getAgents() == null || id == null) {
            return null;
        }
        return agentsFile.getAgents().stream()
            .filter(a -> id.equals(a.getId()))
            .findFirst()
            .orElse(null);
    }

    public Agent updateAgent(String id, Agent updates) {
        if (agentsFile == null || agentsFile.getAgents() == null || id == null) {
            return null;
        }

        List<Agent> agents = agentsFile.getAgents();
        for (int i = 0; i < agents.size(); i++) {
            Agent existing = agents.get(i);
            if (id.equals(existing.getId())) {
                if (isAssistantRole(existing, updates) && hasAssistedUpdate(updates)) {
                    boolean clearingOnly = Boolean.FALSE.equals(updates.getAssisted())
                        && updates.getAssistedReason() == null
                        && updates.getAssistedSince() == null
                        && updates.getAssistedModel() == null
                        && updates.getAssistedQueueSize() == null
                        && updates.getAssistedTaskDosage() == null
                        && updates.getAssistedNotes() == null;
                    if (!clearingOnly) {
                        throw new IllegalArgumentException("Assistant cannot be set to assisted mode");
                    }
                }
                // Merge updates into existing agent
                // Note: Only update non-null AND non-empty values for collections
                // because Jackson deserializes missing fields as empty collections, not null
                if (updates.getName() != null && !updates.getName().isEmpty()) {
                    existing.setName(updates.getName());
                }
                String roleUpdate = RoleKey.canonicalize(updates.getRole());
                if (roleUpdate != null && !roleUpdate.isEmpty()) {
                    existing.setRole(roleUpdate);
                }
                // Avatar can be empty string (to clear it) or have value
                if (updates.getAvatar() != null) {
                    existing.setAvatar(updates.getAvatar());
                }
                if (updates.getColor() != null && !updates.getColor().isEmpty()) {
                    existing.setColor(updates.getColor());
                }
                if (updates.getPersonality() != null) {
                    existing.setPersonality(updates.getPersonality());
                }
                // PersonalitySliders: only update if not empty (allows partial updates)
                if (updates.getPersonalitySliders() != null && !updates.getPersonalitySliders().isEmpty()) {
                    existing.setPersonalitySliders(updates.getPersonalitySliders());
                }
                // SignatureLine can be empty string (to clear it)
                if (updates.getSignatureLine() != null) {
                    existing.setSignatureLine(updates.getSignatureLine());
                }
                // Skills/Goals: only update if not empty (don't accidentally clear)
                if (updates.getSkills() != null && !updates.getSkills().isEmpty()) {
                    existing.setSkills(updates.getSkills());
                }
                if (updates.getGoals() != null && !updates.getGoals().isEmpty()) {
                    existing.setGoals(updates.getGoals());
                }
                if (updates.getEndpoint() != null) {
                    existing.setEndpoint(updates.getEndpoint());
                    syncModelRecord(existing, updates.getEndpoint().getModel());
                    String newModel = updates.getEndpoint().getModel();
                    if (existing.getAssisted() != null && existing.getAssisted()
                        && existing.getAssistedModel() != null
                        && newModel != null
                        && !newModel.equals(existing.getAssistedModel())) {
                        clearAssistedState(existing);
                    }
                }
                if (updates.getMemoryProfile() != null) {
                    existing.setMemoryProfile(updates.getMemoryProfile());
                }
                if (updates.getIsPrimaryForRole() != null) {
                    existing.setIsPrimaryForRole(updates.getIsPrimaryForRole());
                }
                if (updates.getCanBeTeamLead() != null) {
                    existing.setCanBeTeamLead(updates.getCanBeTeamLead());
                }
                if (updates.getAssisted() != null) {
                    existing.setAssisted(updates.getAssisted());
                    existing.setAssistedReason(updates.getAssistedReason());
                    existing.setAssistedSince(updates.getAssistedSince());
                    existing.setAssistedModel(updates.getAssistedModel());
                }
                if (updates.getAssistedQueueSize() != null) {
                    existing.setAssistedQueueSize(updates.getAssistedQueueSize());
                }
                if (updates.getAssistedTaskDosage() != null) {
                    existing.setAssistedTaskDosage(updates.getAssistedTaskDosage());
                }
                if (updates.getAssistedNotes() != null) {
                    existing.setAssistedNotes(updates.getAssistedNotes());
                }

                existing.setUpdatedAt(System.currentTimeMillis());

                saveToDisk();
                return existing;
            }
        }
        return null;
    }

    public Agent setEnabled(String id, boolean enabled) {
        if (agentsFile == null || agentsFile.getAgents() == null || id == null) {
            return null;
        }
        for (Agent existing : agentsFile.getAgents()) {
            if (id.equals(existing.getId())) {
                existing.setEnabled(enabled);
                existing.setUpdatedAt(System.currentTimeMillis());
                saveToDisk();
                return existing;
            }
        }
        return null;
    }

    public void reorderAgents(List<String> orderedIds) {
        if (agentsFile == null || agentsFile.getAgents() == null || orderedIds == null) {
            return;
        }
        List<Agent> current = agentsFile.getAgents();
        List<Agent> reordered = new ArrayList<>();
        for (String id : orderedIds) {
            if (id == null) continue;
            for (Agent agent : current) {
                if (id.equals(agent.getId())) {
                    reordered.add(agent);
                    break;
                }
            }
        }
        for (Agent agent : current) {
            if (agent == null || agent.getId() == null) continue;
            boolean included = false;
            for (String id : orderedIds) {
                if (agent.getId().equals(id)) {
                    included = true;
                    break;
                }
            }
            if (!included) {
                reordered.add(agent);
            }
        }
        agentsFile.setAgents(reordered);
        saveToDisk();
    }

    public Agent createAgent(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("Agent payload is required");
        }
        if (agent.getName() == null || agent.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Agent name is required");
        }

        if (agentsFile == null) {
            agentsFile = new AgentsFile();
            agentsFile.setVersion(1);
            agentsFile.setAgents(new ArrayList<>());
        }

        String roleKey = RoleKey.canonicalize(agent.getRole());
        if (roleKey == null || roleKey.isEmpty()) {
            roleKey = "writer";
        }
        agent.setRole(roleKey);
        if (isAssistantRole(agent, null) && hasAssistedUpdate(agent)) {
            throw new IllegalArgumentException("Assistant cannot be set to assisted mode");
        }

        String id = agent.getId();
        if (id == null || id.trim().isEmpty()) {
            id = generateUniqueId(agent.getName());
        } else if (getAgent(id) != null) {
            id = generateUniqueId(id);
        }

        agent.setId(id);
        agent.setEnabled(true);

        long now = System.currentTimeMillis();
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);

        // Normalize collections in case nulls were sent.
        agent.setSkills(agent.getSkills());
        agent.setGoals(agent.getGoals());
        agent.setTools(agent.getTools());
        agent.setAutoActions(agent.getAutoActions());
        agent.setPersonalitySliders(agent.getPersonalitySliders());
        agent.setModelRecords(agent.getModelRecords());
        syncModelRecord(agent, agent.getEndpoint() != null ? agent.getEndpoint().getModel() : null);

        agentsFile.getAgents().add(agent);
        if (!saveToDisk()) {
            throw new IllegalStateException("Failed to save agent registry");
        }

        logger.info("Created agent: " + agent.getName() + " (" + id + ")");
        return agent;
    }

    public Agent importAgent(Agent agent) {
        if (agentsFile == null) {
            agentsFile = new AgentsFile();
            agentsFile.setVersion(1);
            agentsFile.setAgents(new ArrayList<>());
        }

        // Generate new ID if importing as new
        String newId = agent.getId();
        if (newId == null || getAgent(newId) != null) {
            newId = generateUniqueId(agent.getName());
        }

        agent.setRole(RoleKey.canonicalize(agent.getRole()));
        agent.setId(newId);
        agent.setCreatedAt(System.currentTimeMillis());
        agent.setUpdatedAt(System.currentTimeMillis());
        agent.setEnabled(true);
        if (isAssistantRole(agent, null) && hasAssistedUpdate(agent)) {
            throw new IllegalArgumentException("Assistant cannot be set to assisted mode");
        }

        agentsFile.getAgents().add(agent);
        saveToDisk();

        logger.info("Imported agent: " + agent.getName() + " (" + newId + ")");
        return agent;
    }

    // =============== Role Settings CRUD ===============

    public List<RoleFreedomSettings> listRoleSettings() {
        if (agentsFile == null || agentsFile.getRoleSettings() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(agentsFile.getRoleSettings());
    }

    public RoleFreedomSettings getRoleSettings(String role) {
        String roleKey = RoleKey.canonicalize(role);
        if (agentsFile == null || agentsFile.getRoleSettings() == null || roleKey == null) {
            return null;
        }
        return agentsFile.getRoleSettings().stream()
            .filter(rs -> roleKey.equals(RoleKey.canonicalize(rs.getRole())))
            .findFirst()
            .orElse(null);
    }

    public RoleFreedomSettings saveRoleSettings(RoleFreedomSettings settings) {
        String roleKey = RoleKey.canonicalize(settings != null ? settings.getRole() : null);
        if (settings == null || roleKey == null || roleKey.isEmpty()) {
            throw new IllegalArgumentException("Role settings and role name are required");
        }
        settings.setRole(roleKey);

        if (agentsFile == null) {
            agentsFile = new AgentsFile();
            agentsFile.setVersion(1);
            agentsFile.setAgents(new ArrayList<>());
            agentsFile.setRoleSettings(new ArrayList<>());
        }

        List<RoleFreedomSettings> roleSettings = agentsFile.getRoleSettings();
        if (roleSettings == null) {
            roleSettings = new ArrayList<>();
            agentsFile.setRoleSettings(roleSettings);
        }

        // Find existing or add new (upsert)
        boolean found = false;
        for (int i = 0; i < roleSettings.size(); i++) {
            if (settings.getRole().equals(RoleKey.canonicalize(roleSettings.get(i).getRole()))) {
                roleSettings.set(i, settings);
                found = true;
                break;
            }
        }
        if (!found) {
            roleSettings.add(settings);
        }

        if (!saveToDisk()) {
            throw new IllegalStateException("Failed to save role settings");
        }

        logger.info("Saved role settings for: " + settings.getRole());
        return settings;
    }

    public boolean deleteRoleSettings(String role) {
        String roleKey = RoleKey.canonicalize(role);
        if (agentsFile == null || agentsFile.getRoleSettings() == null || roleKey == null) {
            return false;
        }
        boolean removed = agentsFile.getRoleSettings().removeIf(
            rs -> roleKey.equals(RoleKey.canonicalize(rs.getRole()))
        );
        if (removed) {
            saveToDisk();
            logger.info("Deleted role settings for: " + roleKey);
        }
        return removed;
    }

    private void clearAssistedState(Agent agent) {
        if (agent == null) {
            return;
        }
        agent.setAssisted(false);
        agent.setAssistedReason(null);
        agent.setAssistedSince(null);
        agent.setAssistedModel(null);
        agent.setAssistedQueueSize(null);
        agent.setAssistedTaskDosage(null);
        agent.setAssistedNotes(null);
    }

    private boolean hasAssistedUpdate(Agent agent) {
        if (agent == null) {
            return false;
        }
        return agent.getAssisted() != null
            || agent.getAssistedReason() != null
            || agent.getAssistedSince() != null
            || agent.getAssistedModel() != null
            || agent.getAssistedQueueSize() != null
            || agent.getAssistedTaskDosage() != null
            || agent.getAssistedNotes() != null;
    }

    private boolean isAssistantRole(Agent existing, Agent updates) {
        String roleValue = null;
        if (updates != null && updates.getRole() != null && !updates.getRole().trim().isEmpty()) {
            roleValue = updates.getRole();
        } else if (existing != null) {
            roleValue = existing.getRole();
        }
        String roleKey = RoleKey.canonicalize(roleValue);
        Boolean canBeTeamLead = null;
        if (updates != null && updates.getCanBeTeamLead() != null) {
            canBeTeamLead = updates.getCanBeTeamLead();
        } else if (existing != null) {
            canBeTeamLead = existing.getCanBeTeamLead();
        }
        return "assistant".equals(roleKey) || Boolean.TRUE.equals(canBeTeamLead);
    }

    private String generateUniqueId(String name) {
        String baseId = slugify(name);
        String id = baseId;
        int counter = 1;
        while (getAgent(id) != null) {
            id = baseId + "-" + counter++;
        }
        return id;
    }

    private boolean saveToDisk() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), agentsFile);
            logger.info("Saved agent registry to " + registryPath);
            return true;
        } catch (IOException e) {
            logger.error("Failed to save agent registry: " + e.getMessage(), e);
            return false;
        }
    }

    private String slugify(String value) {
        if (value == null) {
            return "agent";
        }
        String slug = value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "agent" : slug;
    }

    private void ensureRegistryExists() {
        if (!hasWorkspaceMarker()) {
            logger.info("Skipping agent registry creation - no .control-room marker (NO_PROJECT state)");
            return;
        }
        if (Files.exists(registryPath)) {
            return;
        }

        try {
            Files.createDirectories(registryPath.getParent());
            AgentsFile emptyRegistry = new AgentsFile();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), emptyRegistry);
            logger.info("Created empty agent registry at " + registryPath);
        } catch (IOException e) {
            logger.error("Failed to create agent registry: " + e.getMessage(), e);
        }
    }

    private void loadFromDisk() {
        if (!hasWorkspaceMarker()) {
            initializeEmptyRegistry();
            return;
        }
        try {
            agentsFile = objectMapper.readValue(registryPath.toFile(), AgentsFile.class);
            logger.info("Loaded agent registry: " + registryPath);
            if (normalizeAgentsFile()) {
                saveToDisk();
            }
        } catch (IOException e) {
            logger.error("Failed to load agent registry: " + e.getMessage(), e);
            agentsFile = null;
        }
    }

    private boolean hasWorkspaceMarker() {
        Path controlRoomDir = registryPath.getParent().getParent();
        return Files.exists(controlRoomDir.resolve("workspace.json"));
    }

    private void initializeEmptyRegistry() {
        agentsFile = new AgentsFile();
        agentsFile.setVersion(1);
        agentsFile.setAgents(new ArrayList<>());
        agentsFile.setRoleSettings(new ArrayList<>());
    }

    private boolean normalizeAgentsFile() {
        if (agentsFile == null) {
            return false;
        }
        boolean changed = false;
        List<Agent> agents = agentsFile.getAgents();
        if (agents != null) {
            for (Agent agent : agents) {
                if (agent == null) {
                    continue;
                }
                String roleKey = RoleKey.canonicalize(agent.getRole());
                if (roleKey == null) {
                    if (agent.getRole() != null) {
                        agent.setRole(null);
                        changed = true;
                    }
                } else if (!roleKey.equals(agent.getRole())) {
                    agent.setRole(roleKey);
                    changed = true;
                }
                if (agent.getEndpoint() != null && agent.getEndpoint().getModel() != null) {
                    if (syncModelRecord(agent, agent.getEndpoint().getModel())) {
                        changed = true;
                    }
                }
            }
        }

        List<RoleFreedomSettings> roleSettings = agentsFile.getRoleSettings();
        if (roleSettings != null) {
            Map<String, RoleFreedomSettings> merged = new LinkedHashMap<>();
            for (RoleFreedomSettings settings : roleSettings) {
                if (settings == null) {
                    continue;
                }
                String roleKey = RoleKey.canonicalize(settings.getRole());
                if (roleKey == null) {
                    if (settings.getRole() != null) {
                        settings.setRole(null);
                        changed = true;
                    }
                } else if (!roleKey.equals(settings.getRole())) {
                    settings.setRole(roleKey);
                    changed = true;
                }
                if (merged.containsKey(roleKey)) {
                    merged.remove(roleKey);
                    changed = true;
                }
                merged.put(roleKey, settings);
            }
            if (roleSettings.size() != merged.size()) {
                changed = true;
            }
            if (changed) {
                agentsFile.setRoleSettings(new ArrayList<>(merged.values()));
            }
        }

        return changed;
    }

    public boolean syncModelRecord(String agentId, String modelId) {
        Agent agent = getAgent(agentId);
        if (agent == null) {
            return false;
        }
        boolean updated = syncModelRecord(agent, modelId);
        if (updated) {
            agent.setUpdatedAt(System.currentTimeMillis());
            saveToDisk();
        }
        return updated;
    }

    public com.miniide.models.AgentModelRecord getModelRecord(String agentId, String modelId) {
        Agent agent = getAgent(agentId);
        if (agent == null || modelId == null || modelId.isBlank()) {
            return null;
        }
        List<com.miniide.models.AgentModelRecord> records = agent.getModelRecords();
        if (records == null) {
            return null;
        }
        String normalized = modelId.trim();
        for (com.miniide.models.AgentModelRecord record : records) {
            if (record != null && normalized.equals(record.getModelId())) {
                return record;
            }
        }
        return null;
    }

    public com.miniide.models.AgentModelRecord getOrCreateModelRecord(String agentId, String modelId) {
        Agent agent = getAgent(agentId);
        if (agent == null || modelId == null || modelId.isBlank()) {
            return null;
        }
        String normalized = modelId.trim();
        com.miniide.models.AgentModelRecord existing = getModelRecord(agentId, normalized);
        if (existing != null) {
            return existing;
        }
        List<com.miniide.models.AgentModelRecord> records = agent.getModelRecords();
        if (records == null) {
            records = new ArrayList<>();
            agent.setModelRecords(records);
        }
        com.miniide.models.AgentModelRecord record = new com.miniide.models.AgentModelRecord();
        record.setModelId(normalized);
        record.setRole(agent.getRole());
        long now = System.currentTimeMillis();
        record.setActivatedAt(now);
        record.setActive(false);
        if (agent.getActiveModelId() == null || agent.getActiveModelId().isBlank()) {
            agent.setActiveModelId(normalized);
            record.setActive(true);
        } else if (normalized.equals(agent.getActiveModelId())) {
            record.setActive(true);
        }
        record.setCapabilityProfile(new com.miniide.models.AgentCapabilityProfile());
        record.setPerformance(new com.miniide.models.AgentPerformanceStats());
        records.add(record);
        return record;
    }

    public boolean saveAgent(Agent agent) {
        if (agent == null) {
            return false;
        }
        agent.setUpdatedAt(System.currentTimeMillis());
        return saveToDisk();
    }

    private boolean syncModelRecord(Agent agent, String modelId) {
        if (agent == null || modelId == null || modelId.isBlank()) {
            return false;
        }
        List<com.miniide.models.AgentModelRecord> records = agent.getModelRecords();
        if (records == null) {
            records = new ArrayList<>();
            agent.setModelRecords(records);
        }

        String normalizedModel = modelId.trim();
        String currentActiveModel = agent.getActiveModelId();
        com.miniide.models.AgentModelRecord activeRecord = findActiveRecord(records, currentActiveModel);

        if (currentActiveModel == null) {
            agent.setActiveModelId(normalizedModel);
            activeRecord = null;
        }

        if (activeRecord != null && activeRecord.getModelId() != null
            && activeRecord.getModelId().equals(normalizedModel)) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (activeRecord != null) {
            activeRecord.setActive(false);
            activeRecord.setDeactivatedAt(now);
        }

        com.miniide.models.AgentModelRecord target = findRecord(records, normalizedModel);
        if (target == null) {
            target = createRecord(agent, normalizedModel, now);
            records.add(target);
        }
        target.setActive(true);
        if (target.getActivatedAt() <= 0) {
            target.setActivatedAt(now);
        }
        target.setDeactivatedAt(null);
        agent.setActiveModelId(normalizedModel);
        return true;
    }

    private com.miniide.models.AgentModelRecord findRecord(List<com.miniide.models.AgentModelRecord> records, String modelId) {
        if (records == null || modelId == null) {
            return null;
        }
        for (com.miniide.models.AgentModelRecord record : records) {
            if (record != null && modelId.equals(record.getModelId())) {
                return record;
            }
        }
        return null;
    }

    private com.miniide.models.AgentModelRecord findActiveRecord(List<com.miniide.models.AgentModelRecord> records, String activeModelId) {
        if (records == null) {
            return null;
        }
        for (com.miniide.models.AgentModelRecord record : records) {
            if (record != null && record.isActive()) {
                return record;
            }
        }
        if (activeModelId != null) {
            return findRecord(records, activeModelId);
        }
        return null;
    }

    private com.miniide.models.AgentModelRecord createRecord(Agent agent, String modelId, long now) {
        com.miniide.models.AgentModelRecord record = new com.miniide.models.AgentModelRecord();
        record.setModelId(modelId);
        record.setRole(agent.getRole());
        record.setActive(true);
        record.setActivatedAt(now);
        record.setCapabilityProfile(new com.miniide.models.AgentCapabilityProfile());
        record.setPerformance(new com.miniide.models.AgentPerformanceStats());
        return record;
    }

    private AgentsFile createDefaultAgentsFile() {
        long now = System.currentTimeMillis();
        List<Agent> agents = new ArrayList<>();

        agents.add(createAgent("assistant", "Chief of Staff", "assistant", true, now,
            "coordination", "pacing", "system health",
            "maintain team cadence", "enforce guardrails",
            createMemoryProfile("strong", "#coordination", "#pacing", 5, true)));

        agents.add(createAgent("planner", "Planner", "planner", true, now,
            "structure", "beats", "timeline",
            "maintain story shape", "catch structural issues",
            createMemoryProfile("strong", "#structure", "#timeline", 5, true)));

        agents.add(createAgent("writer", "Writer", "writer", true, now,
            "prose", "voice", "scene flow",
            "write vivid scenes", "maintain tone",
            createMemoryProfile("normal", "#voice", "#prose", 4, false)));

        agents.add(createAgent("editor", "Editor", "editor", true, now,
            "clarity", "grammar", "pacing",
            "polish prose", "remove friction",
            createMemoryProfile("normal", "#editing", "#clarity", 4, false)));

        agents.add(createAgent("critic", "Critic", "critic", true, now,
            "feedback", "themes", "logic",
            "identify weak spots", "stress-test ideas",
            createMemoryProfile("strong", "#themes", "#logic", 5, true)));

        agents.add(createAgent("continuity", "Continuity", "continuity", true, now,
            "lore", "canon", "consistency",
            "protect canon", "catch conflicts",
            createMemoryProfile("strong", "#lore", "#continuity", 5, true)));

        AgentsFile file = new AgentsFile();
        file.setVersion(1);
        file.setAgents(agents);

        List<RoleFreedomSettings> roles = new ArrayList<>();
        roles.add(new RoleFreedomSettings("planner", "semi-autonomous",
            List.of("question", "conflict", "completion"), null));
        file.setRoleSettings(roles);

        return file;
    }

    private Agent createAgent(String id, String name, String role, boolean primary, long now,
                              String skill1, String skill2, String skill3,
                              String goal1, String goal2,
                              AgentMemoryProfile memoryProfile) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(name);
        agent.setRole(role);
        agent.setEnabled(true);
        agent.setAvatar("");
        agent.setIsPrimaryForRole(primary);
        agent.setCanBeTeamLead("assistant".equals(role));
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);

        AgentEndpointConfig endpoint = new AgentEndpointConfig();
        endpoint.setProvider("anthropic");
        endpoint.setModel("claude-sonnet-4");
        agent.setEndpoint(endpoint);

        AgentPersonalityConfig personality = new AgentPersonalityConfig();
        personality.setTone("neutral");
        personality.setVerbosity("normal");
        personality.setVoiceTags(List.of(role));
        personality.setBaseInstructions("Focus on your role: " + role + ".");
        agent.setPersonality(personality);

        agent.setSkills(List.of(skill1, skill2, skill3));
        agent.setGoals(List.of(goal1, goal2));
        agent.setTools(new ArrayList<AgentToolCapability>());
        agent.setAutoActions(new ArrayList<AgentAutoActionConfig>());
        agent.setMemoryProfile(memoryProfile);

        return agent;
    }

    private AgentMemoryProfile createMemoryProfile(String retention, String tag1, String tag2,
                                                   int maxInterestLevel, boolean canPin) {
        AgentMemoryProfile memoryProfile = new AgentMemoryProfile();
        memoryProfile.setRetention(retention);
        memoryProfile.setFocusTags(List.of(tag1, tag2));
        memoryProfile.setMaxInterestLevel(maxInterestLevel);
        memoryProfile.setCanPinIssues(canPin);
        return memoryProfile;
    }
}
