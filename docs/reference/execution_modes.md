# Execution Modes

Purpose: Define how agent work is choreographed in Control Room.

This document specifies the two execution modes (Conference and Pipeline),
the Recipe format that drives Pipeline execution, and the StepRunner contract.

It does NOT specify agent behavioral contracts (see agent_roles.md)
or the tool protocol mechanics (see statemachine.md).


---

# Core Distinction

Control Room has two execution modes for multi-agent work.
Same agents. Same role contracts. Same tools. Different choreography.

**Conference** = parallel perspectives on shared evidence.
"What do you all think about X?"

**Pipeline** = sequential value-add where each agent's output feeds the next.
"Build X step by step."

These modes are NOT interchangeable.
Conference is for discussion. Pipeline is for production.


---

# Mode A: Conference (Parallel Interpretation)

Implemented. See statemachine.md "Conference Round Lifecycle."

Summary:
1. User sends a message.
2. Chief executes tools (phase 1, lean prompt, tools enabled).
3. Tool results are injected to all agents (phase 2, skipTools: true).
4. Each agent interprets from their role perspective or ABSTAINs.
5. Round closes: transcript saved as issue, receipts linked, buffer wiped.

Agents see the same evidence simultaneously.
Agents do NOT see each other's responses.
Order within phase 2 does not affect output.

Use when: quick opinions, multi-perspective review, brainstorming.

## Conference Tool Policy (Phase 1 Only)

Conference exposes a tool-policy control surface that affects **only the Chief's phase 1** tool loop:

- Allowed tools (comma-separated): restricts which tools the Chief may call (server-enforced).
- Require tool on first step: forces at least one tool call to occur (bypasses the heuristic "tool_call_required" detector).

Phase 2 always runs with `skipTools: true` and cannot call tools.

## Conference Evidence Enforcement (Phase 2)

Phase 2 agent replies are validated for grounding:

- Exactly one `Evidence:` line is required.
- Evidence source requirements depend on role (see `docs/reference/agent_roles.md`).
- Quotes are verified against VFS content when a file is cited.
- Tool-backed evidence must include a valid `receipt_id: rcpt_...` that exists in the conference session receipts.

Rejected replies are not treated as "tool failures"; they are evidence/grounding failures in phase 2.


---

# Mode B: Pipeline (Sequential Production)

Not yet fully implemented. This section is the design spec.

## Lifecycle

1. User states intent (1:1 chat with Chief, or Session Plan UI).
2. Chief decomposes intent into discrete tasks with dependencies and DoD.
3. System presents Session Plan for user approval.
4. For each approved task:
   a. task_router selects a Recipe (deterministic, no model).
   b. StepRunner executes the Recipe.
   c. Results are committed (files written, issues created).
5. Chief checks DoD after each task, spawns issues for failures.

## What the user sees

A Session Plan: an ordered checklist of tasks with status indicators.
- Tasks show dependencies (task 2 blocked until task 1 complete).
- User can reorder, remove, or edit tasks before starting.
- User can pause/resume the session at any task boundary.
- Tasks with scoping questions (flagged by Chief) are blocked until answered.

## What the agents see

Each agent in the pipeline sees ONLY:
- The task definition (what to do).
- The cache slots relevant to their step (injected evidence).
- Their role contract (via prompt framing).

Agents do NOT see the full session plan, other agents' outputs from
different tasks, or the pipeline recipe. They see their input and
produce their output. The system handles everything else.


---

# Recipes

A Recipe is a static, declarative execution plan for a single task type.
Recipes are selected by task_router based on task classification.
Recipes are NOT assembled by models at runtime.

## Recipe Structure

```
recipe_id: creative_draft_scene

phase_a:  (tools — system executes, no model)
  steps:
    - tool: file_locator
      args: { search: "$TASK_DESCRIPTION" }
      outputs: [scenePaths, outlinePath, canonPaths]
    - tool: outline_analyzer
      args: { file_path: "$outlinePath" }
      outputs: [outline_beat]
    - tool: canon_checker
      args: { scene_path: "$scenePaths[0]", canon_paths: "$canonPaths" }
      outputs: [canon_context]

phase_b:  (agents — sequential, each feeds the next)
  pipeline:
    - agent: planner
      input: [outline_beat, canon_context]
      output: scene_brief
      prompt_type: refine_brief
    - agent: writer
      input: [scene_brief, canon_context, scenePaths]
      output: draft
      prompt_type: draft_scene
    - agent: editor
      input: [draft]
      output: edited_draft
      prompt_type: polish_draft
    - agent: continuity
      input: [edited_draft, canon_context]
      output: continuity_report
      prompt_type: check_continuity
    - agent: critic
      input: [edited_draft]
      output: critique
      prompt_type: evaluate_reader_experience

dod:  (Definition of Done — machine-checkable where possible)
  - scene_file_exists: "$scenePaths[0]"
  - continuity_pass: "$continuity_report.pass == true"
  - critique_exists: "$critique != null"
```

## Key Principles

1. Phase A is model-free. The system calls tools and caches results.
2. Phase B agents run with skipTools: true. They never emit tool calls.
3. Each Phase B step adds to the cache. Later steps see earlier outputs.
4. Arg wiring uses $ references to cache slots.
5. Recipes are stored as data (JSON or equivalent), not as code.
6. New task types = new recipe files, not new code.


---

# Task Types and Recipe Map

Initial recipe library (to be implemented incrementally):

| Task Type | Recipe ID | Phase A Tools | Phase B Pipeline |
|---|---|---|---|
| Draft a scene | creative_draft_scene | file_locator, outline_analyzer, canon_checker | Planner → Writer → Editor → Continuity → Critic |
| Consistency check | analytical_consistency | file_locator, consistency_checker | Continuity → Editor |
| Pacing/prose review | analytical_prose | file_locator, prose_analyzer | Writer → Critic |
| Foreshadowing audit | analytical_foreshadow | file_locator, stakes_mapper, reader_experience_simulator | Critic → Planner → Writer |
| Scene validation | analytical_scene_validate | file_locator, scene_draft_validator | Planner → Continuity |
| Canon update | structural_canon | file_locator, canon_checker | Continuity → Librarian |
| Outline restructure | structural_outline | file_locator, outline_analyzer | Planner → Critic |

Not all agents participate in every pipeline. The recipe specifies exactly
which agents are needed. Others are not called.


---

# StepRunner Contract

The StepRunner is a system component (Java, server-side) that executes recipes.
It is NOT a model. It does not reason. It follows the recipe mechanically.

## Guarantees

1. Phase A steps execute in order. Each step completes before the next starts.
2. Tool outputs are cached in named slots with receipt IDs.
3. Phase B agents are called in order. Each agent's output is cached before the next agent runs.
4. All agent calls in Phase B use skipTools: true. No tool catalog, no nonce, no JSON schema enforcement.
5. Each agent prompt is built from the recipe's prompt_type + the specified cache slots. Nothing else.
6. If a Phase B agent fails (error, timeout, stop hook), the pipeline halts and the failure is reported to the user. The system does NOT retry automatically — the user decides.
7. All tool executions produce receipts. All receipts are linked to the task's session ID.
8. After the pipeline completes, the DoD is evaluated. Failures produce issues.

## Cache

The cache is an in-memory key-value store scoped to a single task execution.

- Keys are slot names (e.g., "outline_beat", "draft", "continuity_report").
- Values contain: { data, receipt_id, summary, agent_id (if from Phase B) }.
- Full tool payloads are stored in the existing receipt JSONL (not in the cache).
- Cache summaries are injected into prompts. Payloads are available on demand.
- Cache is discarded after the task completes. Persistent artifacts are files and issues.

## Tier-Based Step Sizing

The recipe's Phase B steps can be decomposed differently based on agent tier:

| Tier | Steps per agent turn | Prompt style |
|---|---|---|
| T1 (small/fast) | 3 micro-steps: triage → finding → evidence | Tiny constrained prompts, one decision each |
| T3 (mid-tier) | 1 step: interpret or ABSTAIN | Standard prompt with cache injection |
| T5 (capable) | 1 step: full synthesis | Richer prompt, may include optional "extra insight" |

The StepRunner reads the agent's tier from its configuration and selects
the appropriate prompt template. The recipe itself does not change.


---

# task_router Evolution

Currently: task_router is a tool that the model calls during tool execution.
It pattern-matches a request string and returns routing text.

Target: task_router becomes a system-level service callable from:
1. The StepRunner (to select a recipe for a task).
2. The Chief's decomposition flow (to validate that each task is routable).
3. The existing tool loop (backward compatibility for 1:1 chat).

task_router should return:
- recipe_id: which recipe to use.
- initial_args: seed values derived from the task description.
- routable: boolean. If false, the system falls back to the Chief for scoping.

The routing logic remains deterministic (keyword + pattern matching).
No model is involved in recipe selection.


---

# Chief's Role in Pipeline Mode

Chief does NOT orchestrate the pipeline. The system does.

Chief's responsibilities:
1. **Decompose**: Break user intent into discrete, routable tasks.
2. **Scope**: Ask clarifying questions for ambiguous tasks.
3. **Define DoD**: State what "done" means for each task.
4. **Sequence**: Specify dependencies between tasks.
5. **Review**: After pipeline completes, check DoD and decide next steps.

Chief does NOT:
- Select which tools to run (task_router does this).
- Call tools (StepRunner does this).
- Decide agent order (Recipe defines this).
- Retry failed steps (user decides).

Chief's decomposition is the ONE model call that happens before the pipeline.
Everything after is system-driven.


---

# Session Plan

The Session Plan is the user-facing artifact produced by Chief's decomposition.

## Structure

```
session_id: <uuid>
created_by: chief
tasks:
  - id: 1
    description: "Write Scene 21"
    type: creative_draft_scene
    deps: []
    dod: "Scene file exists, continuity pass"
    status: pending
  - id: 2
    description: "Write Scene 22"
    type: creative_draft_scene
    deps: [1]
    dod: "Scene file exists, continuity pass"
    status: blocked
  - id: 3
    description: "Consistency check (scenes 21–22 vs canon)"
    type: analytical_consistency
    deps: [1, 2]
    dod: "Continuity report, issues created for findings"
    status: blocked
  - id: 4
    description: "Foreshadowing audit"
    type: null
    deps: []
    scoping_question: "Which themes/events need foreshadowing? Which scenes?"
    status: needs_scoping
```

## Task States

- pending: ready to run, all deps satisfied.
- blocked: waiting on dependency tasks.
- needs_scoping: requires user input before it can be routed.
- running: StepRunner is executing the recipe.
- done: DoD satisfied.
- failed: DoD not satisfied; issues created.

## User Controls

- Reorder tasks (within dependency constraints).
- Remove tasks.
- Edit task descriptions.
- Answer scoping questions.
- Start/pause/resume execution.
- Override: force-start a blocked task (at own risk).


---

# Relationship to Existing Systems

| System | Role in Pipeline Mode |
|---|---|
| Conference (Mode A) | Separate mode. Used for discussion, not production. |
| 1:1 Chat | Where the user talks to Chief to create Session Plans. Also unchanged for direct agent interaction. |
| Issue Tracker | Pipeline tasks may create issues (DoD failures, findings). Round-close creates transcript issues. |
| Tool Protocol | Phase A uses the existing tool execution service with receipts and nonces. Unchanged. |
| Evidence Validator | Phase B agents still produce Evidence lines. Validation rules unchanged. |
| Audit Trail | All tool executions and agent responses produce receipts linked to the session. |
| Agent Cards | Card states reflect pipeline activity (executing, processing, idle). |
| Tier Profiles | Drive step decomposition in Phase B. |
| Role Contracts (agent_roles.md) | Define which agents participate in which recipes and what they must/must not do. |


---

# What to Implement First

1. **One recipe end-to-end**: `creative_draft_scene` with StepRunner skeleton.
2. **task_router promotion**: Make it callable from Java (not just as a tool).
3. **Session Plan UI**: Even a basic checklist with "Start" is enough.
4. **Tier-based prompt templates**: Start with T3 (one-step interpret).

Do not build the full recipe library up front. Each recipe is ~20 lines of
declarative config. Add them as needed.


---

# Summary

Tools are atoms. Agents are thinkers. Recipes are assembly instructions.
The StepRunner follows instructions. The Chief writes the work order.
The user approves the plan.

The system handles machinery. Agents handle thinking.


---
---

# Appendix: Concrete Specs (Implementation-Ready)

The sections above define the architecture. The appendices below define the
exact shapes, file layouts, and contracts needed to implement it.


---

# A. Run Persistence Layout

A "run" is a single execution of a recipe. All run artifacts live under:

```
.control-room/runs/{runId}/
├── run.json          manifest (metadata, status, timing, progress)
├── steps.jsonl       append-only log (one line per completed step)
└── cache.json        slot store (written incrementally after each step)
```

Tool receipts are NOT duplicated here. They stay in the existing audit path:
`audit/sessions/{sessionId}/tool_receipts.jsonl`

The run's steps.jsonl references receipt_ids as pointers.


## run.json

```json
{
  "run_id": "run_abc123",
  "recipe_id": "creative_draft_scene",
  "session_id": "sess_xyz",
  "status": "running",
  "created_at": "2026-02-10T14:30:00Z",
  "updated_at": "2026-02-10T14:31:22Z",
  "completed_at": null,
  "task": {
    "description": "Draft scene 21",
    "session_plan_task_id": 1,
    "initial_args": {
      "scene_path": "Story/Scenes/SCN-the-arrival.md",
      "canon_paths": ["Compendium/Characters/CHAR-kael.md"]
    }
  },
  "current_step_index": 2,
  "total_steps": 8,
  "phase": "a",
  "error": null
}
```

Run states: `pending → running → done | failed | cancelled`

Phase field: `"a"` during tool steps, `"b"` during agent steps, `"dod"` during
DoD evaluation, `null` when not running.


## steps.jsonl (one line per completed step)

```json
{
  "step_index": 0,
  "step_id": "discover",
  "phase": "a",
  "tool": "file_locator",
  "agent_archetype": null,
  "agent_id": null,
  "status": "done",
  "output_slot": "discovery",
  "receipt_id": "rcpt_a1b2c3",
  "input_slot_refs": ["task.args"],
  "output_hash": "sha256:deadbeef...",
  "output_preview": "12 files found matching query",
  "started_at": "2026-02-10T14:30:01Z",
  "completed_at": "2026-02-10T14:30:02Z",
  "error": null
}
```

Phase B steps use `agent_archetype` + `agent_id` instead of `tool`, and
`receipt_id` is null (agents don't produce tool receipts).

Fields:
- `input_slot_refs`: which cache slots were read to build this step's input.
  Enables auditing exactly what the agent/tool saw.
- `output_hash`: sha256 of the full output. Detects partial writes.
- `output_preview`: truncated first ~200 chars for quick debugging.


## cache.json (dual-mode slot store)

Written as a full snapshot after each step completes (atomic write-to-temp-
then-rename for crash safety). On restart, StepRunner reads run.json for
current_step_index and cache.json for all completed slots.

Phase A slots store **pointers** (tool payloads live in receipts):

```json
{
  "discovery": {
    "type": "pointer",
    "receipt_id": "rcpt_a1b2c3",
    "sha256": "deadbeef...",
    "summary": "12 files found: 4 scenes, 1 outline, 3 canon cards, 4 other"
  }
}
```

Phase B slots store **artifacts** (no receipt backing exists):

```json
{
  "scene_brief": {
    "type": "artifact",
    "agent_id": "planner-001",
    "text": "Scene 21 opens with Kael arriving at the...",
    "sha256": "cafebabe...",
    "summary": "Scene brief: Kael's arrival, 3 beats, POV Kael"
  }
}
```

This avoids duplicating tool payloads while ensuring Phase B artifacts survive
for restart and inspection.


---

# B. Recipe JSON Schema

Recipes are declarative execution plans. Stored as individual JSON files.

Storage:
- Bundled defaults: `src/main/resources/recipes/*.json`
- Project overrides: `.control-room/recipes/*.json`
- Merge: project recipes shadow bundled recipes by `recipe_id`
  (same pattern as prompts.json layering)


## Full Recipe Example

```json
{
  "recipe_id": "creative_draft_scene",
  "label": "Draft a scene from outline",
  "task_patterns": ["draft scene", "write scene", "scene from outline"],

  "phase_a": [
    {
      "step_id": "discover",
      "tool": "file_locator",
      "args": {
        "search_criteria": { "$ref": "task.description" },
        "scan_mode": "FAST_SCAN",
        "max_results": 12
      },
      "output_slot": "discovery"
    },
    {
      "step_id": "analyze_outline",
      "tool": "outline_analyzer",
      "args": {
        "outline_path": "Story/SCN-outline.md",
        "mode": "structure"
      },
      "output_slot": "outline_beat"
    },
    {
      "step_id": "check_canon",
      "tool": "canon_checker",
      "args": {
        "scene_path": { "$ref": "task.args.scene_path" },
        "canon_paths": { "$ref": "task.args.canon_paths" }
      },
      "output_slot": "canon_context"
    }
  ],

  "phase_b": [
    {
      "step_id": "brief",
      "agent_archetype": "planner",
      "input_slots": ["outline_beat", "canon_context"],
      "output_slot": "scene_brief",
      "prompt_type": "refine_brief"
    },
    {
      "step_id": "draft",
      "agent_archetype": "writer",
      "input_slots": ["scene_brief", "canon_context"],
      "output_slot": "draft",
      "prompt_type": "draft_scene"
    },
    {
      "step_id": "polish",
      "agent_archetype": "editor",
      "input_slots": ["draft"],
      "output_slot": "edited_draft",
      "prompt_type": "polish_draft"
    },
    {
      "step_id": "continuity",
      "agent_archetype": "continuity",
      "input_slots": ["edited_draft", "canon_context"],
      "output_slot": "continuity_report",
      "prompt_type": "check_continuity"
    },
    {
      "step_id": "critique",
      "agent_archetype": "critic",
      "input_slots": ["edited_draft"],
      "output_slot": "critique",
      "prompt_type": "evaluate_reader_experience"
    }
  ],

  "dod": [
    { "check": "slot_not_null", "slot": "draft" },
    { "check": "slot_not_null", "slot": "continuity_report" },
    { "check": "slot_not_null", "slot": "critique" }
  ]
}
```


## Recipe Field Reference

### Top-level

| Field | Type | Description |
|---|---|---|
| recipe_id | string | Unique identifier. Recipes shadow by this key. |
| label | string | Human-readable name. |
| task_patterns | string[] | Keywords/phrases for task_router matching. |
| phase_a | step[] | Tool steps (system executes, no model). |
| phase_b | step[] | Agent steps (sequential, each feeds the next). |
| dod | check[] | Definition of Done conditions. |

### Phase A Step

| Field | Type | Description |
|---|---|---|
| step_id | string | Unique within recipe. Used in logs and $ref paths. |
| tool | string | Tool ID (must be registered in ToolExecutionService). |
| args | object | Tool arguments. Values may be literals or `$ref` objects. |
| output_slot | string | Cache slot name where the tool output is stored. |

### Phase B Step

| Field | Type | Description |
|---|---|---|
| step_id | string | Unique within recipe. |
| agent_archetype | string | Role archetype (planner, writer, editor, etc.). |
| input_slots | string[] | Cache slots injected into the agent's prompt. |
| output_slot | string | Cache slot for the agent's response. |
| prompt_type | string | Registry key for the prompt template. |

### DoD Check

| Field | Type | Description |
|---|---|---|
| check | string | Check type: `slot_not_null`, `slot_field_equals`, `file_exists`. |
| slot | string | Cache slot to inspect. |
| field | string | (slot_field_equals only) Dot-path into the slot value. |
| expected | any | (slot_field_equals only) Expected value. |
| path | string | (file_exists only) VFS path, may use $ref. |


---

# C. Arg Templating ($ref Resolution)

Recipe args use `$ref` objects to reference values from the cache or task
metadata at runtime.

## Syntax

Literal values pass through unchanged:
```json
{ "scan_mode": "FAST_SCAN" }
```

References are resolved by the StepRunner:
```json
{ "scene_path": { "$ref": "task.args.scene_path" } }
{ "outline_path": { "$ref": "discovery.matches[0].path" } }
```

## Resolution Rules

1. Dot traversal: `a.b.c` navigates nested objects.
2. Array indexing: `[N]` accesses element at literal integer index.
3. Root namespaces:
   - `task.*` — task metadata (description, initial_args, session_plan_task_id)
   - Any other root — cache slot name (e.g., `discovery.*`)
4. If resolution produces `null` at any step, the step fails with a clear
   error identifying the broken ref path.
5. No JSONPath. No filters. No wildcards. No expressions.

## Examples

| $ref | Resolves to |
|---|---|
| `task.description` | The task's description string |
| `task.args.scene_path` | An initial arg passed at run start |
| `discovery.matches[0].path` | First match path from file_locator output |
| `outline_beat.scenes[2].title` | Third scene title from outline analysis |
| `canon_context.conflicts` | Conflict list from canon checker |

## Java Implementation

The resolver is ~30 lines: split path on `.`, check each segment for trailing
`[N]` regex, navigate via Jackson JsonNode. Returns JsonNode or throws
RefResolutionException with the full path and failure point.


---

# D. REST Contract (Pipeline Runs)

All endpoints are under `/api/runs`. Follows existing Javalin controller
pattern (Controller interface, errorBody helper, path params, JSON responses).

## Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/runs` | Start a new run |
| GET | `/api/runs` | List runs |
| GET | `/api/runs/{id}` | Run detail (consolidated for polling) |
| GET | `/api/runs/{id}/steps` | Full step log |
| GET | `/api/runs/{id}/cache/{slot}` | Read a cache slot value |
| POST | `/api/runs/{id}/cancel` | Cancel a running run |

## POST /api/runs

Start a new recipe execution.

Request:
```json
{
  "recipe_id": "creative_draft_scene",
  "args": {
    "scene_path": "Story/Scenes/SCN-the-arrival.md",
    "canon_paths": ["Compendium/Characters/CHAR-kael.md"]
  }
}
```

Response (201):
```json
{
  "run_id": "run_abc123",
  "status": "running"
}
```

Errors:
- 400: unknown recipe_id, missing required args
- 409: recipe already running (optional guard for v1)

## GET /api/runs

List runs with optional filters.

Query params: `status` (pending|running|done|failed|cancelled), `recipe_id`.

Response (200): array of run summaries (run_id, recipe_id, status, created_at).

## GET /api/runs/{id} (consolidated polling endpoint)

Returns everything the UI needs in one call.

Response (200):
```json
{
  "run_id": "run_abc123",
  "recipe_id": "creative_draft_scene",
  "status": "running",
  "phase": "b",
  "current_step_index": 5,
  "total_steps": 8,
  "created_at": "2026-02-10T14:30:00Z",
  "updated_at": "2026-02-10T14:31:22Z",
  "completed_at": null,
  "task": {
    "description": "Draft scene 21"
  },
  "steps": [
    {
      "step_id": "discover",
      "phase": "a",
      "status": "done",
      "tool": "file_locator",
      "output_slot": "discovery",
      "output_preview": "12 files found"
    },
    {
      "step_id": "brief",
      "phase": "b",
      "status": "done",
      "agent_archetype": "planner",
      "output_slot": "scene_brief",
      "output_preview": "Scene 21 opens with Kael..."
    },
    {
      "step_id": "draft",
      "phase": "b",
      "status": "running",
      "agent_archetype": "writer",
      "output_slot": "draft"
    }
  ],
  "cache_summary": {
    "discovery": { "type": "pointer", "preview": "12 files found" },
    "outline_beat": { "type": "pointer", "preview": "8 scenes, beat 5 matched" },
    "scene_brief": { "type": "artifact", "preview": "Scene 21 opens with Kael..." }
  },
  "error": null
}
```

## GET /api/runs/{id}/steps

Full step log (parsed steps.jsonl). Response (200): array of full step objects
including all observability fields (input_slot_refs, output_hash, timing).

## GET /api/runs/{id}/cache/{slot}

Drill-down into a specific cache slot.

For pointer slots: returns the pointer metadata (receipt_id, sha256, summary).
The client can fetch the full tool output via the existing audit receipt API.

For artifact slots: returns the full text content.

Response (200):
```json
{
  "slot": "scene_brief",
  "type": "artifact",
  "agent_id": "planner-001",
  "text": "Scene 21 opens with Kael arriving at the fortress...",
  "sha256": "cafebabe...",
  "summary": "Scene brief: Kael's arrival, 3 beats, POV Kael"
}
```

## POST /api/runs/{id}/cancel

Cancel a running run. Sets status to `cancelled`, records current step.

Response (200): `{ "run_id": "...", "status": "cancelled" }`

Errors:
- 404: run not found
- 409: run is not in `running` state


---

# E. task_router as System Service

task_router is promoted from a tool to a system-level Java service.

## Interface

```
TaskRoutingResult route(String taskDescription)

TaskRoutingResult:
  recipeId:    String   (null if not routable)
  initialArgs: Map      (seed values extracted from description)
  routable:    boolean
  reason:      String   (why this recipe, or why not routable)
```

## Routing Logic

Deterministic keyword + pattern matching against `task_patterns` in the recipe
registry. No model is involved in recipe selection.

1. Load all recipes from the registry (bundled + project overrides).
2. For each recipe, check if any `task_patterns` entry matches the description
   (case-insensitive substring or simple regex).
3. If exactly one recipe matches: return it with routable=true.
4. If multiple match: return the best match (longest pattern) with routable=true.
5. If none match: return routable=false. Chief handles scoping.

## Backward Compatibility

The existing `task_router` tool becomes a thin wrapper:

```
execute("task_router", { user_request }) →
  calls route(user_request) →
  formats TaskRoutingResult as tool output text
```

This means 1:1 chat tool calls still work identically.

## Callers

| Caller | Purpose |
|---|---|
| StepRunner | Select recipe for a Session Plan task |
| Chief decomposition | Validate that each task is routable |
| Tool execution (existing) | 1:1 chat backward compat |


---

# F. Prompt Templates (Registry-Backed, File-Based)

Phase B prompt templates are managed through the existing prompt registry,
not as a parallel system alongside recipes.

## Registry Entry (in prompts.json)

```json
{
  "pipeline_templates": {
    "refine_brief": {
      "label": "Refine scene brief from outline + canon",
      "variants": {
        "t1": "templates/refine_brief.t1.md",
        "t3": "templates/refine_brief.t3.md",
        "t5": "templates/refine_brief.t5.md"
      }
    },
    "draft_scene": {
      "label": "Draft scene prose",
      "variants": {
        "t3": "templates/draft_scene.t3.md"
      }
    }
  }
}
```

## File Storage

- Bundled: `src/main/resources/prompts/templates/*.md`
- Project overrides: `.control-room/prompts/templates/*.md`

Paths in the registry are relative to the prompts directory.
The existing 3-layer merge (bundled → user-home → project) applies.

## Template Syntax

Templates use `{{slot_name}}` for cache slot injection:

```markdown
You are a Planner. Your job is to refine a scene brief.

## Outline Beat
{{outline_beat}}

## Canon Context
{{canon_context}}

## Task
Write a concise scene brief covering: ...
```

## Resolution Flow

1. Recipe step has `prompt_type: "refine_brief"`.
2. StepRunner looks up `pipeline_templates.refine_brief` in the registry.
3. Agent's tier determines which variant to load (t1, t3, or t5).
   Falls back to closest available (t3 if t5 missing, t1 if t3 missing).
4. Template file is loaded and `{{slot_name}}` placeholders are replaced
   with cache slot summaries (for pointers) or full text (for artifacts).
5. Assembled prompt is sent to the agent via `/api/ai/chat` with
   `skipTools: true`.

## Tier Variant Guidelines

| Tier | Prompt Style | Typical Length |
|---|---|---|
| T1 | Micro-step: one narrow question, constrained output | ~200 words |
| T3 | Standard: full context, single interpretation/output | ~500 words |
| T5 | Rich: extra context, optional insight, synthesis | ~800 words |


---

# G. Session Plan Data Model (Deferred, Compatibility Reference)

Session Plan implementation is deferred to the Next milestone. This section
documents the data model to ensure the run layer stays compatible.

## Storage

`.control-room/sessions/{sessionId}/plan.json`

A plan can spawn many runs. Plans and runs are separate concerns.

## Shape

```json
{
  "session_id": "sp_uuid",
  "created_by": "chief",
  "status": "running",
  "created_at": "2026-02-10T14:00:00Z",
  "tasks": [
    {
      "id": 1,
      "description": "Write Scene 21",
      "recipe_id": "creative_draft_scene",
      "deps": [],
      "dod": "Scene file exists, continuity pass",
      "status": "done",
      "run_id": "run_abc"
    },
    {
      "id": 2,
      "description": "Write Scene 22",
      "recipe_id": "creative_draft_scene",
      "deps": [1],
      "dod": "Scene file exists, continuity pass",
      "status": "blocked",
      "run_id": null
    },
    {
      "id": 3,
      "description": "Consistency check (scenes 21-22 vs canon)",
      "recipe_id": "analytical_consistency",
      "deps": [1, 2],
      "status": "blocked",
      "run_id": null
    },
    {
      "id": 4,
      "description": "Foreshadowing audit",
      "recipe_id": null,
      "deps": [],
      "scoping_question": "Which themes need foreshadowing? Which scenes?",
      "status": "needs_scoping",
      "run_id": null
    }
  ]
}
```

## Task States

- pending: ready to run (all deps satisfied)
- blocked: waiting on dependency tasks
- needs_scoping: requires user input before routing
- running: StepRunner is executing the recipe (run_id populated)
- done: DoD satisfied
- failed: DoD not satisfied, issues created

## Connection to Runs

Each task maps 1:1 to a run. When a task becomes `pending`, the system calls
`POST /api/runs` with the task's recipe_id and args. The `run_id` field links
the plan task to its execution. The plan is the coordination layer; runs are
the execution layer.
