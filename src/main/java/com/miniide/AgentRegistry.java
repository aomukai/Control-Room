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
import java.util.List;
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
        ensureRegistryExists();
        loadFromDisk();
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
                // Merge updates into existing agent
                // Note: Only update non-null AND non-empty values for collections
                // because Jackson deserializes missing fields as empty collections, not null
                if (updates.getName() != null && !updates.getName().isEmpty()) {
                    existing.setName(updates.getName());
                }
                if (updates.getRole() != null && !updates.getRole().isEmpty()) {
                    existing.setRole(updates.getRole());
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

                existing.setUpdatedAt(System.currentTimeMillis());

                saveToDisk();
                return existing;
            }
        }
        return null;
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

        if (agent.getRole() == null || agent.getRole().trim().isEmpty()) {
            agent.setRole("writer");
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

        agent.setId(newId);
        agent.setCreatedAt(System.currentTimeMillis());
        agent.setUpdatedAt(System.currentTimeMillis());
        agent.setEnabled(true);

        agentsFile.getAgents().add(agent);
        saveToDisk();

        logger.info("Imported agent: " + agent.getName() + " (" + newId + ")");
        return agent;
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
        if (Files.exists(registryPath)) {
            return;
        }

        try {
            Files.createDirectories(registryPath.getParent());
            AgentsFile defaults = createDefaultAgentsFile();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), defaults);
            logger.info("Created default agent registry at " + registryPath);
        } catch (IOException e) {
            logger.error("Failed to create agent registry: " + e.getMessage(), e);
        }
    }

    private void loadFromDisk() {
        try {
            agentsFile = objectMapper.readValue(registryPath.toFile(), AgentsFile.class);
            logger.info("Loaded agent registry: " + registryPath);
        } catch (IOException e) {
            logger.error("Failed to load agent registry: " + e.getMessage(), e);
            agentsFile = null;
        }
    }

    private AgentsFile createDefaultAgentsFile() {
        long now = System.currentTimeMillis();
        List<Agent> agents = new ArrayList<>();

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
            List.of("question", "conflict", "completion"), null, null));
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
