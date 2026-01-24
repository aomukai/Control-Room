# Control Room – Agent System Design
Indexed in docs/agent_library.md

> Dynamic, configurable agent teams for the Workbench & Editor.

---

<a id="agent-purpose"></a>
## 1) Purpose & Philosophy

Control Room has a clear architectural split:

- **Workbench** – strategic layer (planning, agents, issues, newsfeed)
- **Editor** – tactical layer (hands-on writing, patch review, AI actions)
- **Memory/Issues** – long-term institutional memory of the project

In baseline setups, agents may be represented as a fixed set: Assistant (Team Leader), Planner, Writer, Editor, Critic, Continuity. This document defines how to evolve this into a **dynamic agent workforce**:

- Default team = recommended template, not a hard limit
- Users can **hire** new agents (Beta Reader A, Lore Specialist, Sensitivity Reader)
- Users can **fire or disable** agents they no longer need
- Agents can be **retasked** (Writer → Rewriter during edit phase)
- All systems (Issues, Notifications, Memory, Conference) recognize these agents

**High-level goal:**

> Agents behave like a configurable writer's room: each with a role, personality,
> tools, and memory profile, all wired into the existing infrastructure.

---

## 2) Core Concepts

### 2.1 Agent vs Role

- **Agent** – an individual entity ("Beta Reader A", "Worldbuilding Archivist")
- **Role** – a functional category ("planner", "writer", "beta-reader", "lore-expert")

Multiple agents can share the same role (e.g., 3 beta readers). A role can be specialized per agent via configuration.

<a id="agent-registry"></a>
### 2.2 Agent Registry

All active agents are managed through a centralized **Agent Registry**, replacing the current hard-coded list. Default agents are simply pre-populated entries; nothing in the system relies on hard-coded enums.

<a id="agent-interface"></a>
### 2.3 The Agent Interface

```ts
type AgentId = string;  // e.g. "planner", "writer-primary", "beta-reader-1"

interface Agent {
  id: AgentId;
  name: string;                    // display name ("Writer", "Beta Reader A")
  role: string;                    // functional role ("planner", "writer", "critic")
  enabled: boolean;                // soft delete / active toggle

  // Presentation
  avatar?: string;                 // emoji or icon key
  color?: string;                  // hex or CSS variable

  // Endpoint & Model
  endpoint?: AgentEndpointConfig;  // legacy - primary wiring lives in agent-endpoints.json

  // Behavior
  skills: string[];                // freeform tags: "prose", "pacing", "lore"
  goals: string[];                 // what this agent optimizes for
  personality?: AgentPersonalityConfig;  // legacy (baseInstructions lives here)
  personalitySliders: Record<string, number>;
  signatureLine?: string;

  // Capabilities
  tools: AgentToolCapability[];
  autoActions: AgentAutoActionConfig[];

  // Team dynamics
  isPrimaryForRole?: boolean;      // is this the default agent for its role?
  canBeTeamLead?: boolean;         // can this agent arbitrate conflicts?
  // canBeDevilsAdvocate?: boolean; // optional (see cr_prefrontal_exocortex.md)

  // Memory
  memoryProfile: AgentMemoryProfile;

  // Lineage
  clonedFrom?: AgentId;            // if duplicated from another agent

  // Meta
  createdAt: number;
  updatedAt: number;
}
```

---

<a id="agent-workflow"></a>
## 3) Agent Workflow

### 3.1 The "Morning Coffee" Pattern

Agents don't have a separate inbox. **Issues ARE the inbox.** When an agent begins its turn, it:

1. Scans open issues (assigned to it, or tagged with its focus areas)
2. Checks recent notifications
3. Decides what to work on and in what order

This is conceptually "starting the day with a cup of coffee and reviewing the board."

### 3.2 Single-Active Turn Scheduler

To support local LLM deployments with large context windows, the system must enforce
exactly one active agent at a time. Agents take turns in a serialized queue:

- Only one agent can be active (executing or responding) at any moment.
- New agent triggers (@mentions, assignments, auto-actions) enqueue a turn instead of running concurrently.
- The next agent turn starts only after the current agent yields or hits a stop hook.

This keeps context allocation predictable and prevents parallel calls from exhausting
local model capacity.

### 3.3 Agent-to-Agent Prompting

Agents can activate each other through **@mentions** in issue comments:

```
@continuity: please check section 3, "inciting incident" – does this
conflict with "aelyth perception" in the physiology document?
```

This serves as a direct prompt to the mentioned agent. The system:
- Creates a notification for the target agent
- The mention acts as an activation trigger on that agent's next turn

Agents prompting fellow agents is a **foundational attribute** of the system.

### 3.4 Proactive Interruption

Agents can monitor discussions they weren't assigned to. If Continuity sees a Planner↔Writer thread and spots a problem, it can interject:

```
Let me interrupt – this proposed timeline would conflict with
the established aelyth perception mechanics in Chapter 2.
```

This helps catch cross-domain issues but may increase token usage. Mitigations:
- Clear issue labeling
- Short previews/summaries in issue lists
- Agent focus tags to filter relevance

### 3.5 Triggers & Activation

| Trigger | Description |
|---------|-------------|
| User assigns task | User creates issue or starts conference with assignment |
| Agent @mention | Another agent explicitly prompts this agent |
| Auto-action fires | Configured trigger (on-save, on-export) activates agent |
| Issue assigned | Agent receives assignment via issue `assignee` field |

---

<a id="agent-freedom"></a>
## 4) Agentic Freedom & Stop Hooks

### 4.1 Freedom Levels

Agents operate at different autonomy levels, configured **per-role**:

```ts
type AgenticFreedomLevel =
  | "supervised"      // every action requires user approval
  | "semi-autonomous" // works independently, stops on questions/problems
  | "autonomous";     // full autonomy, notifies on completion only

type RoleBehaviorTemplate = "autonomous" | "balanced" | "verbose" | "custom";
```

### 4.2 Role Settings ✓ IMPLEMENTED

Role Settings define **guidelines and defaults** for all agents sharing a role. These are preferences, not rigid rules—agents use judgment within these boundaries.

```ts
interface RoleSettings {
  role: string;                           // e.g. "writer", "sensitivity-reader"
  template: RoleBehaviorTemplate;         // Quick preset selector
  freedomLevel: AgenticFreedomLevel;
  notifyUserOn: ("start" | "question" | "conflict" | "completion" | "error")[];
  maxActionsPerSession?: number | null;   // null = unlimited
  requireApprovalFor?: AgentToolId[];     // specific tools need approval

  // Free-text guidance fields (injected into system prompts)
  roleCharter: string;                    // Job description, responsibilities
  collaborationGuidance: string;          // How to work with others, escalate
  toolAndSafetyNotes: string;             // Tool preferences, safety constraints
}
```

**Key design principle:** Role Settings provide *guidance*, not routing rules. The `collaborationGuidance` text tells the agent how to choose collaborators, but the agent picks actual helpers from the roster at runtime based on the situation.

### 4.3 Behavior Templates

Three built-in templates provide sensible defaults:

| Template | Freedom | Notifications | Max Actions | Style |
|----------|---------|---------------|-------------|-------|
| **Autonomous** | autonomous | conflict, completion, error | unlimited | Work independently, escalate only for blockers |
| **Balanced** | semi-autonomous | question, conflict, completion, error | 10 | Collaborate on significant changes |
| **Verbose** | supervised | all events | 5 | Report frequently, ask before changing |
| **Custom** | user-defined | user-defined | user-defined | Manual configuration |

When the user selects a template, all fields are pre-filled. Editing any field automatically switches to "Custom".

### 4.4 Role Settings UI

Accessed via right-click on an Agent card in the Workbench sidebar → "Role Settings..."

The modal includes:
- **Template selector** (4-button grid)
- **Freedom level** dropdown
- **Notify user on** checkboxes (5 options)
- **Max actions per session** input + "Unlimited" button
- **Role Charter** textarea (job description)
- **Collaboration Guidance** textarea (how to escalate)
- **Tool & Safety Notes** textarea (constraints)

Settings are persisted per-role in `agents.json` and apply to all agents with that role.

<a id="agent-stop-hooks"></a>
### 4.5 Stop Hooks (Critical Safety Rails)

Stop hooks prevent runaway execution. An agent **must stop and wait** when:

| Hook | Trigger |
|------|---------|
| `question` | Agent has a question it cannot resolve |
| `conflict` | Disagreement with another agent, no resolution |
| `uncertainty` | Low confidence in proposed action |
| `scope-exceeded` | Task requires work outside agent's defined scope |
| `approval-required` | Action requires human sign-off (per freedom settings) |
| `error` | Endpoint failure, unexpected state |
| `budget-limit` | Session action limit reached |

Without proper stop hooks, a system could run for days and rack up massive costs. **This is not optional.**

---

<a id="agent-team-dynamics"></a>
## 5) Team Dynamics

### 5.1 Primary Agent

When multiple agents share a role, one can be designated **primary**:

```ts
interface Agent {
  isPrimaryForRole?: boolean;
}
```

The primary agent:
- Is the default for quick actions in the Editor
- Writes summary issues when the team reaches conclusions
- Represents the role in cross-team discussions

<a id="agent-team-leader"></a>
### 5.2 Team Leader (Assistant) Role

The **Assistant** is the mandatory Team Leader archetype for **Team Mode**. There must be
exactly one active Assistant when Team Mode runs; no Assistant means Team Mode is blocked.
This is a full-time coordination role responsible for task slicing, pacing, and system health.

```ts
interface Agent {
  canBeTeamLead?: boolean;
}
```

Assistant Team Leaders can:
- Arbitrate when agents disagree
- Decide between conflicting patches
- Escalate to user when needed
- Manage task dosage control and Assisted Mode when agents struggle
- Convert Planner roadmaps into executable microtasks with clear DoD

**Voice preservation:** The Assistant defines constraints and slices; the Writer/Doer
retains creative authorship of the content.

The leader is given **suggestions, not hardcoded rules**:
- Call a vote among team members
- Apply a predefined rule
- Notify the user for decision

Modern models are smart enough to choose appropriately.

### 5.3 Conflict Resolution & Voting

When agents disagree:

1. **Detection** – conflicting proposals, contradictory feedback
2. **Escalation** – routed to the Assistant Team Leader or user
3. **Resolution options**:
   - **Vote** – agents vote, majority wins
   - **Defer to primary** – primary agent decides
   - **User arbitration** – human makes the call
   - **Rule-based** – predefined rule applies (e.g., "Continuity always wins on canon")

The resolution method depends on role freedom settings and the specific conflict type.

---

<a id="agent-capabilities"></a>
## 6) Agent Capabilities

### 6.1 Tools

Tools define **what** an agent can do:

```ts
type AgentToolId =
  | "summarize"
  | "explain"
  | "rewrite"
  | "polish"
  | "continue"
  | "continuity-check"
  | "beta-feedback"
  | "worldbuilding-annotate"
  | "patch-generate"
  | "patch-review"
  | "timeline-audit"
  | "custom";

interface AgentToolCapability {
  id: AgentToolId;
  enabled: boolean;
  aggressiveness?: "gentle" | "balanced" | "bold";
  scope?: "selection" | "scene" | "chapter" | "project";
  notes?: string;
}
```

<a id="agent-endpoints"></a>
### 6.2 Endpoint Configuration

Each agent can use a different LLM provider:

```ts
type LLMProvider =
  | "openai"
  | "anthropic"
  | "gemini"
  | "grok"
  | "openrouter"
  | "nanogpt"
  | "togetherai"
  | "lmstudio"
  | "ollama"
  | "jan"
  | "koboldcpp"
  | "custom";

interface AgentEndpointConfig {
  provider: LLMProvider;
  model: string;                   // "gpt-4.1-mini", "claude-sonnet-4", etc.
  baseUrl?: string;                // for local/custom providers
  apiKeyRef?: string;              // reference to global credential store

  // Generation defaults
  temperature?: number;
  maxOutputTokens?: number;

  // Reliability
  timeoutMs?: number;
  maxRetries?: number;
}
```

**Note:** API keys are stored globally (not per workspace). Agents reference a key via
`apiKeyRef` so a single key can be reused across agents. Endpoint wiring is stored in
`agent-endpoints.json`; the `endpoint` field is kept for legacy data.

### 6.3 Personality Configuration

**Implemented: Slider-Based Personality System**

Rather than discrete enums, personality is now configured via 8 continuous sliders (0-100):

```ts
interface Agent {
  personalitySliders: {
    humor: number;        // Serious ←→ Playful
    strictness: number;   // Lenient ←→ Strict
    diplomacy: number;    // Blunt ←→ Diplomatic
    verbosity: number;    // Terse ←→ Elaborate
    confidence: number;   // Tentative ←→ Assertive
    warmth: number;       // Formal ←→ Warm
    focus: number;        // Detail-Oriented ←→ Big-Picture
    pacing: number;       // Methodical ←→ Quick
  };
  instructions: string;   // Custom instructions merged into system prompt
  signatureLine?: string; // Signature phrase (e.g., "Carthago delenda est.")
}
```

**Quick Presets** - 10 collapsible personality templates in the UI:
- Zen Strategist, Playful Brainstormer, Academic Editor
- Ruthless Critic, Compassionate Coach, Lore Archivist
- Productive Taskmaster, Poetic Prose Weaver, Plot Snake, Continuity Sentinel

The `instructions` field handles agent scoping:
- "Focus on tone and voice issues"
- "You are a sensitivity reader checking for unintended triggers"
- "Comment only on POV consistency and style"

### 6.4 Auto Actions

Auto-actions define **when** an agent acts without explicit user trigger:

```ts
interface AgentAutoActionConfig {
  id: string;
  trigger:
    | { type: "on-save"; scope: "file" | "scene" }
    | { type: "on-export"; scope: "project" }
    | { type: "on-issue-created"; tagFilter?: string[] }
    | { type: "on-mention" }
    | { type: "scheduled"; intervalMs: number };
  toolId: AgentToolId;
  enabled: boolean;
  maxRunsPerSession?: number;
  minIntervalMs?: number;
}
```

**Scope note:** Auto-actions are defined in the data model; orchestration is a separate scope boundary.

---

<a id="agent-memory-integration"></a>
## 7) Memory Integration

Agents plug into the existing Memory/Issue system via per-agent memory profiles. The underlying implementation lives in `cr_memory.md`.

### 7.1 Memory Profile

```ts
type InterestLevel = 1 | 2 | 3 | 4 | 5;

interface AgentMemoryProfile {
  retention: "strong" | "normal" | "minimal";
  focusTags: string[];             // tags that get interest boost
  maxInterestLevel: InterestLevel; // cap (beta readers might max at 3)
  canPinIssues: boolean;           // can lock memories to Level 5
}
```

### 7.2 Memory Profile Templates

To avoid manual tuning, provide reusable templates:

```ts
type MemoryProfileTemplateId =
  | "minimal-reader"
  | "normal-worker"
  | "deep-lore"
  | "strict-continuity"
  | "custom";

const MEMORY_TEMPLATES: Record<MemoryProfileTemplateId, AgentMemoryProfile> = {
  "minimal-reader": {
    retention: "minimal",
    focusTags: [],
    maxInterestLevel: 3,
    canPinIssues: false
  },
  "normal-worker": {
    retention: "normal",
    focusTags: [],
    maxInterestLevel: 4,
    canPinIssues: false
  },
  "deep-lore": {
    retention: "strong",
    focusTags: ["#lore", "#worldbuilding"],
    maxInterestLevel: 5,
    canPinIssues: true
  },
  "strict-continuity": {
    retention: "strong",
    focusTags: ["#timeline", "#continuity"],
    maxInterestLevel: 5,
    canPinIssues: true
  }
};
```

When hiring an agent, user picks a template. It's copied into `memoryProfile` and can be customized.

### 7.3 Context Management

Context window limits are a moving target (4K → 2M+ tokens). Rather than over-engineering, we:

1. Defer to `cr_memory.md` for compression/decay logic
2. Trust that context limits will continue expanding
3. Use memory profiles to control retention per agent type

---

<a id="agent-lifecycle"></a>
## 8) Agent Lifecycle

### 8.1 Hiring (Creating) an Agent

**Hiring Wizard** steps:

1. **Purpose & Template**
   - "What is this agent for?" -> Creative Voice, Editor, Critic, Lore Keeper, Beta Reader, Custom
   - Sets suggested role, skills, tools

2. **Identity**
   - Name (optional - if none chosen, role (or role1, role2..) = name)
   - Role label (editable)

3. **Endpoint & Model**
   - Launches the Agent Settings modal for full provider/model/key selection
   - Model list fetched from provider (global keys)
   - Advanced settings optional (base URL, timeout, retries, temperature, max tokens)

4. **Personality (Optional)**
   - Ask whether to configure personality now or skip
   - If yes, open Agent Profile modal, then return to wizard

5. **Confirm & Create**
   - Summary shown
   - Agent saved to `agents.json`
   - Appears in Workbench sidebar

6. **Follow-up**
   - Inform the user that they can customize the agent in Agent Settings
   - Auto-create an intro issue and post the agent's greeting as a comment

**Implementation expectation:** Create via `POST /api/agents`. The Add Agent wizard can hand off to the Agent Profile modal for optional personality setup.

### 8.2 Retasking an Agent

When project phase changes (drafting → editing), retask rather than create new:

**Mini-Wizard steps:**

1. **New Role/Template** – "What should this agent focus on now?"
2. **Tools** – Show current vs. suggested, let user toggle
3. **Memory Strategy**:
   - Keep everything (default)
   - Compress old memories
   - Mark as "past life" but retain pins
4. **Confirm** – Summary of changes

Retasking preserves:
- Agent ID (issues/notifications keep references)
- History and prior comments
- Identity (name, avatar)

### 8.3 Persona Shift Comment

When an agent is retasked, optionally insert a system comment on open issues:

> "System note: This agent's role has changed from Critic to Writer.
> Future work on this issue will be handled in their new capacity."

This maintains audit trail clarity.

### 8.4 Disabling an Agent

Soft delete via `enabled = false`. Before disabling, handle orphaned issues:

```ts
async function disableAgent(agentId: AgentId) {
  const openIssues = await issueApi.list({
    assignedTo: agentId,
    status: "open"
  });

  if (openIssues.length > 0) {
    // Show dialog: "Writer has 7 open issues"
    // Options: Reassign to another agent, or Move to Unassigned
  }

  await agentApi.update(agentId, { enabled: false });
}
```

Disabled agents remain in:
- Historical issues (author/assignee)
- Old notifications
- Memory records

UI offers "Show inactive agents" toggle to re-enable later.

### 8.5 Greeting Scan (Optional)

When an agent is created or re-enabled, optionally run a **greeting scan**:

1. Build context packet (project summary, recent issues)
2. Ask agent for:
   - Self-introduction (2-4 sentences)
   - First impressions (3-5 bullets)
   - Suggested next actions
3. Create an **Agent intro** issue and post the greeting as a comment (prompt hidden)

**🏁 MVP:** Simple one-off call. Later: periodic "state of project" reports.

---

## 9) Reserved Actor IDs

Some actor IDs are reserved and not in the registry:

```ts
type ReservedActorId = "system" | "user";
type ActorId = AgentId | ReservedActorId;
```

- `"system"` – automated notes (persona shifts, system events)
- `"user"` – human user actions

---

<a id="agent-storage-api"></a>
## 10) Storage & API

### 10.1 File Layout

```
workspace/
  <workspaceName>/
    .control-room/
      agents/
        agents.json           # per-workspace agent registry
        agent-endpoints.json  # per-workspace agent endpoint wiring

settings/
  security.json               # global key security mode (plaintext or encrypted)
  agent-keys.json             # global key store (plaintext) or key metadata (encrypted)
  agent-keys.vault            # encrypted key vault (encrypted mode)
```

### 10.2 `agents.json` Example

```json
{
  "version": 1,
  "agents": [
    {
      "id": "planner",
      "name": "Planner",
      "role": "planner",
      "enabled": true,
      "avatar": "🧠",
      "isPrimaryForRole": true,
      "skills": ["structure", "beats", "timeline"],
      "goals": ["maintain story shape", "catch structural issues"],
      "endpoint": {
        "provider": "anthropic",
        "model": "claude-sonnet-4"
      },
      "personality": {
        "baseInstructions": "Focus on high-level structure and pacing."
      },
      "personalitySliders": {
        "focus": 70,
        "verbosity": 40,
        "confidence": 60
      },
      "signatureLine": "Keep the story on the rails.",
      "tools": [
        { "id": "summarize", "enabled": true },
        { "id": "timeline-audit", "enabled": true }
      ],
      "autoActions": [],
      "memoryProfile": {
        "retention": "strong",
        "focusTags": ["#structure", "#timeline"],
        "maxInterestLevel": 5,
        "canPinIssues": true
      },
      "createdAt": 1735530000000,
      "updatedAt": 1735530000000
    }
  ],
  "roleSettings": [
    {
      "role": "planner",
      "template": "balanced",
      "freedomLevel": "semi-autonomous",
      "notifyUserOn": ["question", "conflict", "completion", "error"],
      "maxActionsPerSession": 10,
      "roleCharter": "Owns the narrative roadmap and scene status tags (Idea -> Plan -> Draft -> Polished); focuses on structure and pacing, not team coordination.",
      "collaborationGuidance": "Think through problems yourself first. For medium-to-large decisions, consult relevant agents or the user.",
      "toolAndSafetyNotes": "Standard tool access. Confirm before bulk operations."
    }
  ]
}
```

**Note:** Endpoint wiring is stored in `agent-endpoints.json`. The `endpoint` field
may still appear in `agents.json` for backward compatibility.

### 10.3 REST API

The following endpoints define the agent management surface.

```http
GET    /api/agents              # list all (enabled) agents
GET    /api/agents/all          # list all agents (including disabled)
POST   /api/agents              # create agent
GET    /api/agents/{id}         # get single agent
PUT    /api/agents/{id}         # update agent (partial updates)
PUT    /api/agents/{id}/status  # enable/disable agent
PUT    /api/agents/order        # persist roster ordering
POST   /api/agents/import       # import agent from JSON
GET    /api/agent-endpoints     # list agent endpoints
GET    /api/agent-endpoints/{id}# get agent endpoint config
PUT    /api/agent-endpoints/{id}# upsert agent endpoint config

# Role Settings
GET    /api/agents/role-settings           # list all role settings
GET    /api/agents/role-settings/{role}    # get settings for role (returns defaults if none)
PUT    /api/agents/role-settings/{role}    # upsert role settings

<a id="agent-provider-keys"></a>
# Provider Models + Key Storage
GET    /api/providers/models               # list models for provider (provider, keyRef, baseUrl)
GET    /api/settings/security              # get key security mode
PUT    /api/settings/security              # set key security mode (migration)
POST   /api/settings/security/unlock       # unlock encrypted vault
POST   /api/settings/security/lock         # lock encrypted vault
GET    /api/settings/keys                  # list stored keys (metadata)
POST   /api/settings/keys                  # add key
DELETE /api/settings/keys/{provider}/{id}  # delete key

# Chat
POST   /api/ai/chat                    # chat using agent endpoint when agentId supplied
```

**Note:** PUT endpoint supports partial updates - only non-null/non-empty fields are updated. Server max request size is 10MB to accommodate base64 avatar images.

---

## 11) Default Team Template

On project init, create a default 6-agent team (Team Mode requires exactly one Assistant):

| Agent | Role | Focus | Memory |
|-------|------|-------|--------|
| **Assistant (Team Leader)** | assistant | coordination, pacing, system health | strong, pins allowed |
| **Planner** | planner | narrative roadmap, scene tags, structure | strong, pins allowed |
| **Writer** | writer | prose, voice, scene flow | normal |
| **Editor** | editor | clarity, grammar, pacing | normal |
| **Critic** | critic | feedback, themes, logic | strong, pins allowed |
| **Continuity** | continuity | lore, canon, consistency | strong, pins allowed |

Planner owns the Narrative Roadmap and Scene Tags (`Idea` -> `Plan` -> `Draft` -> `Polished`).
The Assistant handles coordination and task slicing; the Writer/Doer remains the creative author.
These are fully editable. Users can modify, disable, or delete them.

---

## 12) UI Integration

### 12.1 Workbench – Agent Sidebar

- Rendered from Agent Registry (enabled agents only)
- Each item shows: Avatar (image or emoji fallback), Name (truncated with tooltip), Role, Status dot
- Image avatars supported via base64 encoding (auto-resized to 256px max)
- Controls:
  - Add Agent wizard
  - Import Agent (JSON)
  - Retired Agents modal
  - Right-click menu: Invite to Conference/Chat, Agent Profile, Role Settings, Agent Settings, Change Role, Export, Duplicate, Retire

### 12.2 Workbench – Role Settings

- Accessible from sidebar header or settings
- Per-role freedom levels
- Notification preferences
- Action limits

### 12.3 Issue Board & Modal

- Assignee filter populated from active agents
- Cards show `author → assignee` with agent names
- Modal shows agent avatars, allows reassignment

### 12.4 Editor – Agent Selection

For quick actions (Rewrite, Polish, etc.):

- Default: use **primary agent** for the relevant role
- Dropdown: "Run as..." to pick specific agent

```
[ Rewrite ▾ ]
  → Writer (primary)
  → Writer – Alt Voice
  → Line Editor
```

---

### 12.5 Agent Settings Modal

Accessed via right-click on an Agent card in the Workbench sidebar -> "Open Agent Settings".

Includes:
- Provider selection (OpenAI, Anthropic, Gemini, Grok, OpenRouter, NanoGPT, TogetherAI, LM Studio, Ollama, Jan, KoboldCPP, Custom)
- Model list fetched from the provider (with search and recommended markers)
- API key selection and storage (global key vault)
- Advanced settings (base URL, NanoGPT legacy toggle, temperature, max tokens, timeout, retries)

Endpoint settings are stored per-workspace in `agent-endpoints.json`.

## 13) Implementation Roadmap

### 🏁 Phase A – Data & Wiring (MVP)

1. Implement `agents.json` persistence + AgentRegistry service
2. Load agents at startup, expose via `agentApi`
3. Refactor code to use registry instead of hardcoded list
4. Update Issue model: `author: ActorId`, `assignee?: AgentId`

### 🏁 Phase B – Workbench UI

5. Render Agent Sidebar from registry
6. Implement Add Agent wizard (basic version)
7. Add Enable/Disable toggle
8. Update Issue Board filters to use dynamic agent list

### Phase C – Freedom & Safety

9. Implement role freedom settings UI
10. Add stop hook infrastructure
11. Implement agent @mention detection in issues

### Phase D – Team Dynamics

12. Add team leader designation
13. Implement voting mechanism
14. Conflict detection and escalation

### Phase E – Advanced

15. Use autoActions for triggers
16. Integrate memory profiles with decay logic
17. Agent greeting scans
18. Retasking wizard with persona shift notes

---

## 14) Future Considerations

### 14.1 Import/Export Agent Configs ✓ IMPLEMENTED

- ✓ Export agent as JSON file (download from profile modal)
- ✓ Import agent from JSON file (via sidebar "+" button or profile modal)
- ✓ Duplicate agent functionality (creates copy with "-copy" suffix)
- Community agent templates (future)
- "Share my Beta Reader setup" (future - could integrate with community)

### 14.2 Agent Analytics

- Which agents are most useful?
- Token usage per agent
- Issues created/resolved per agent

### 14.3 Custom Quirks ✓ IMPLEMENTED

```ts
interface Agent {
  instructions?: string;            // Custom instructions for agent behavior
  signatureLine?: string;           // "Carthago delenda est."
}
```

Both fields are now editable in the Agent Profile Modal.

---

## 15) Summary

This design turns the agent layer into a proper writer's room simulation:

- **Dynamic roster** – hire, fire, retask as the project evolves
- **Issues as inbox** – agents scan and respond to issues naturally
- **@mentions** – agents prompt and activate each other
- **Stop hooks** – critical safety rails against runaway execution
- **Freedom levels** – per-role autonomy settings
- **Team dynamics** – primary agents, team leads, voting
- **Memory integration** – per-agent retention and focus

The system scales from a simple 1-writer setup to a whole swarm of specialized helpers, without breaking the underlying Issue/Memory/Notification architecture.




