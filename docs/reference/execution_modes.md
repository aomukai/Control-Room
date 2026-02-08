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

Not yet implemented. This section is the design spec.

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
