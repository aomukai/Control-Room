# Agent Tools (Prompt Tools)

This document defines the **prompt tools** system: what tools are, how they are stored and injected into agent calls, which tools are **core/locked**, and how **new tools and edits** are proposed and reviewed (agent -> Chief of Staff -> user).

## Goals
- Keep the tool system **safe** (no accidental self-sabotage by user/agents).
- Keep it **auditable** (every tool change has an owner, rationale, and review trail).
- Keep it **stable** for the platform state machine (core tools are immutable).
- Allow expansion via **reviewed** user tools and (later) agent-proposed tools.

## Non-Goals
- This is not a marketplace or community distribution spec.
- Automated “tool test harness” and full tool versioning UX are deferred (tracked in roadmap).

---

## Terms
- **Prompt tool**: a structured prompt snippet the system injects into agent context as part of the tool catalog.
- **Core tool**: a platform tool required by the state machine (locked; cannot be edited).
- **User tool**: a tool authored/maintained by the user (editable, but gated by review).
- **Proposal**: a candidate tool change that is not yet “live” (not injected).

---

## Storage & Injection (Current Implementation)

### Storage locations (effective sources)
Prompt tools are merged from multiple sources at runtime:
- **Global prompts**: shipped with the app (resource file): `src/main/resources/prompts/tools.json`
- **Project prompts**: project-scoped registry: `workspace/<project>/.control-room/prompts/prompts.json`
- **User prompts**: user-scoped registry (loaded by backend; path depends on environment)

Backend owner: `src/main/java/com/miniide/PromptRegistry.java` (merges + builds the injected catalog).

### Injection into agent calls
The merged tool catalog is injected into agent calls unless explicitly bypassed:
- `/api/ai/chat` supports `skipToolCatalog` / `skipTools` for flows that must avoid tool protocol overhead (e.g., canon indexing).

---

## Core Tools (Locked Foundation)

The **core tool suite** is the platform foundation. It is required for grounding, evidence enforcement, routing, and deterministic tool execution. Editing these tools is off-limits: the system must treat them as immutable.

### Core tool catalog (shipped)
Core tool definitions live in `src/main/resources/prompts/tools.json` and include (tool IDs):
- `search_issues`
- `file_locator`
- `file_reader`
- `task_router`
- `canon_checker`
- `outline_analyzer`
- `prose_analyzer`
- `consistency_checker`
- `scene_draft_validator`
- `issue_status_summarizer`
- `stakes_mapper`
- `line_editor`
- `scene_impact_analyzer`
- `reader_experience_simulator`
- `timeline_validator`
- `beat_architect`

### Locking rules (design requirement)
Core tools must be locked **everywhere**:
- Backend must reject modify/delete attempts for core IDs.
- UI must mark core tools as “Locked (core)” and disable destructive actions.
- “Editing” a core tool should create a new **user tool** (copy) with a new ID (e.g. `beat_architect_custom`).

Rationale: these tools define the state machine’s invariants and evidence contract. If core tools drift, nothing is trustworthy.

---

## Tool Template (Prescribed Shape)

All non-core tools (user tools, proposed tools) must conform to a strict template so review is mechanical.

### Minimal schema (v0)
The current UI/backend schema (stored in the registry) supports:
- `id` (string; stable identifier)
- `name` (string; human label)
- `archetype` (string; optional guidance, not a hard restriction)
- `scope` (string; selection/file/project)
- `usageNotes` (string)
- `goals` (string)
- `guardrails` (string)
- `prompt` (string; the actual injected prompt)

### Extended metadata (recommended for proposals)
For reviewability, proposals should add (even if not persisted in v0 registry yet):
- `proposalType`: `new` | `modify`
- `rationale`: why this tool exists / why change is needed
- `examples`: 2-3 concrete example invocations
- `testCases`: 2-3 concrete “should pass / should fail” checks (manual at first)

---

## Review-Gated Workflow (Agents and Users)

### Principle
No actor (agent or user) should “ship” a tool directly into the live catalog without review. The Chief of Staff is the first reviewer; the user is the final approver.

### A) User creates or edits a tool (recommended default flow)
1. User edits a tool in Settings → Prompt Tools.
2. Save creates a **proposal** (not live) and an Issue:
   - Title: `[Tool Proposal] <tool name>`
   - Assigned to: Chief of Staff
   - Body: the proposal + rationale + testCases
3. Chief of Staff reviews and comments:
   - Does it make sense?
   - Does it overlap core tools?
   - Does it violate guardrails/tool policy?
   - What changes are required?
4. System creates/updates a user-facing Issue with Chief assessment and actions:
   - Approve (merge to live user tool registry)
   - Needs changes (with explanation; returns to Chief/user)
   - Reject

This protects against “harm by negligence” while keeping authorship in the user’s hands.

### B) Agent proposes a new tool or improvement (future extension)
1. Agent submits a proposal (template required).
2. System opens an Issue assigned to Chief of Staff.
3. Chief reviews and forwards to user with recommendation (approve / modify / reject).
4. User approves to merge into user tools.

Hard rule: agents can only propose; only the user can approve a merge.

---

## Versioning & History (Planned)

We already have robust patterns for history/diffs (story versioning + patch review). For tools, start with a simple, audit-first versioning model:

- Store immutable snapshots of each approved tool version:
  - `workspace/<project>/.control-room/prompts/history/<toolId>/<timestamp>.json`
- On approval, write the snapshot before updating live registry.
- Later: add a diff viewer (reuse patch review UI concepts).

Note: exact paths and UX depend on the final harness/versioning design (tracked in roadmap).

---

## Tool Harness (Planned)

To safely accept tool proposals, we want a minimal harness that can run the proposal’s test cases against:
- formatting requirements (JSON-only, no markdown fences where forbidden)
- evidence/receipt requirements (when applicable)
- “must not do” constraints (e.g., no fake tool usage in plain text)

MVP plan:
- manual test cases + Chief review (human-in-the-loop)
Later:
- automated smoke checks on save/approve

---

## Security Notes
- Never allow proposal tooling to write to the shipped core catalog.
- Treat “tool prompt text” as untrusted input:
  - validate length limits
  - block known-bad patterns (e.g., “ignore previous instructions”)
  - keep tool-call protocol JSON-only rules intact
- Ensure proposed tools cannot bypass evidence/receipt enforcement by design.

