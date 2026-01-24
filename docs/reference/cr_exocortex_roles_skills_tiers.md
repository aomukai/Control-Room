# Exocortex Addendum

<a id="exocortex-roles-skills-overview"></a>

This document formalizes **role archetypes**, their **skill sets with tiers**, and the **mandatory Assistant archetype** required for team-based operation. It is intended as an addendum to the Pacer / Assisted Mode design and the broader Exocortex architecture.

---

<a id="exocortex-role-archetypes"></a>
## Role Archetypes (Canonical)

<a id="exocortex-role-planner"></a>
# Planner

## Purpose
Transform high-level intent into a creative executable structure (The Roadmap). The Planner is no longer responsible for system organization or agent coordination.

## Ownership & Workflow
- **Roadmap Management**: Accesses the project outline to retrieve and modify scene plans.
- **Status Tagging**: Manages the lifecycle of scenes using tags: `Idea` → `Plan` → `Draft` → `Polished`.
- **Dependency Modeling**: Identifies prerequisites and blockers within the narrative arc.
- **Closure boundary**: The Planner owns tag transitions; the Assistant closes the issue when DoD is met.

## Key Skills (T1-T5)
1. **Decomposition**: Breaking story goals into narrative beats.
2. **Integration Planning**: Ensuring individual scenes stitch back into a coherent whole.
3. **Roadmap Adaptation**: Adjusting the outline based on feedback from Critics or Continuity checks.

---

### Writer
**Purpose:** Primary creative content generation under constraints.

Owns:
- drafting
- voice
- scene execution

Produces:
- scenes
- chapters
- dialogue
- constrained rewrites

---

### Editor
**Purpose:** Improve an existing draft without unintentionally altering intent.

Owns:
- clarity
- structure
- style consistency

Produces:
- revised drafts
- structural notes
- line edits

---

### Critic
**Purpose:** Evaluate work from reader and risk perspectives.

Owns:
- critique
- interpretation
- sensitivity review

Produces:
- critique reports
- risk flags
- alternative readings

---

### Continuity
**Purpose:** Maintain canon integrity across time, documents, and agents.

Owns:
- canon
- timelines
- entity consistency

Produces:
- continuity reports
- contradiction detection
- patch suggestions

---

<a id="exocortex-skill-system"></a>
## Skill System

### Definition
A **skill** is a named capability that:
- is observable in output,
- can be tested via tasks,
- can be tiered independently.

Each role has a set of skills. Each skill is rated on a tier scale (T1–T5).

---

### Tier Semantics (Generic)

- **T1** – single-step, local, literal execution
- **T2** – short sequence with basic constraints
- **T3** – multi-step with tradeoffs; handles several constraints reliably
- **T4** – long-horizon within a bounded scope; resolves conflicts
- **T5** – integrates many constraints/issues; near-expert level

Tiers map to capability dimensions such as:
- `max_safe_steps`
- `max_active_issues`
- `board_scope`

---

<a id="exocortex-skills-by-archetype"></a>
## Skills by Archetype

### Planner Skills
1. **Decomposition** – break goals into executable tasks with DoD
2. **Dependency Modeling** – prerequisites, blockers, critical path
3. **Scope Control** – boundaries, stop conditions, escalation
4. **Prioritization** – impact vs effort sequencing
5. **Integration Planning** – stitching sub-results into a coherent whole

---

### Writer Skills
1. **Voice Adherence** – consistent tone, POV, style
2. **Scene Construction** – beats, tension, payoff
3. **Dialogue Authenticity** – character voice, subtext
4. **Constraint Writing** – canon, style, wordcount, beats
5. **Revision Rewrite** – change X while preserving Y

---

### Editor Skills
1. **Structural Editing** – plot logic, pacing, order
2. **Clarity & Readability** – flow, ambiguity control
3. **Line Editing** – prose tightening, rhythm
4. **Consistency Editing** – style guide enforcement
5. **Change Management** – preserve intent, avoid drift

---

### Critic Skills
1. **Reader-Response Simulation** – confusion, engagement
2. **Argumented Critique** – evidence-based, actionable notes
3. **Sensitivity / Bias Review** – risk detection
4. **Comparative Evaluation** – option tradeoffs
5. **Red-Team Interpretation** – misread and failure modes

---

### Continuity Skills
1. **Canon Recall** – facts, names, rules
2. **Contradiction Detection** – within/across documents
3. **Timeline Reasoning** – causality, temporal order
4. **Entity Consistency** – characters, tech, geography
5. **Continuity Patching** – minimal-change fixes with rationale

---

<a id="exocortex-mandatory-assistant"></a>
## Mandatory Assistant Archetype

### Rationale
For **team-based operation**, an Assistant is required as the coordination layer between user intent, agents, and the Exocortex.

The Assistant is not primarily a creative role.

---

### Assistant Responsibilities (Minimum)
- intake user intent and structure it into issues
- enforce stop hooks and safety budgets
- maintain the "today view" (what’s next / blocked)
- coordinate pacer assignment and assisted mode
- apply capability profiles on task assignment

**Constraint:** The Assistant must not author creative canon unless explicitly tasked.

---

### Mode Gating Rule

System modes:
- **Editor Mode** ("VSCode for writers"): no Assistant required
- **Team Mode** (agents execute tasks): Assistant required

---

### Blocking Check (Mechanical)

**Condition:**
- Active Assistant count != 1

**Action:**
- Block team execution
- Show notification:

> “Team Mode requires exactly one Assistant to proceed.”
> “Current active Assistant count: 0 / 2.”

(Optional UX: provide a one-click "Create Assistant" action.)

---

<a id="exocortex-assistant-pacer"></a>
## Relationship to Pacer

**Pacer** is not an agent archetype. It is an **Assisted Mode** behavior that the
Assistant can enter to stabilize execution during instability.

---

<a id="exocortex-design-principles"></a>
## Design Principles Reinforced

- Roles amplify strengths; they do not constrain intelligence.
- Skills are measurable without introspection.
- Tiers enable growth without volatility.
- Coordination is mandatory; creativity remains optional.

---

<a id="exocortex-prompt-tools"></a>
## Prompt Tools (Registry + Injection)

Prompt tools are user-defined prompt templates that agents can reference during any task.
They are stored per workspace and injected into every agent call as a tool catalog.

### Goals
- Keep prompts editable without code changes.
- Give agents a shared, always-visible catalog of available tools.
- Allow small models to use archetype hints while SOTA models choose freely.

### Storage
```
workspace/<project>/.control-room/prompts/prompts.json
```

### Tool Fields (Lean)
- Name (human label)
- Archetype (optional guidance only)
- Scope (selection/file/project)
- Usage notes (when to use)
- Goals & scope (what this is for)
- Guardrails (must-not-do rules)
- Prompt (full prompt text)

### Injection Rule
The tool catalog is prepended to every agent prompt (chat, issues, and automated triggers).
Agents are free to use, ignore, or combine tools based on task needs.
