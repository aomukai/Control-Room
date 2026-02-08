# Refactor Plan

This document is a pragmatic, low-risk refactor plan for Control Room. The intent is to keep momentum on feature work (especially Execution Modes / Pipeline) while steadily reducing "god file" pressure and improving testability.

## Goals
- Reduce accidental coupling between domains (agents, conference, canon indexing, issues, pipeline).
- Make upcoming features cheaper to implement (Execution Modes / StepRunner UI + backend).
- Improve debuggability: clearer call stacks, narrower modules, fewer "catch-all" handlers.
- Keep refactors small, reviewable, and reversible.

## Non-Goals
- No redesign of UI flows.
- No large-scale renaming or formatting-only churn.
- No "split everything" efforts without a concrete driver.

## Definitions
- "God file": a file containing multiple unrelated domains, where changes in one area frequently risk regressions in another.
- "Domain module": a module that owns one end-to-end slice (API wrapper + state + UI wiring for that slice).

## Refactor Policy (Hard Rules)
1. One PR = one refactor unit:
   - One file split, or one domain extraction, or one dependency inversion.
2. Refactor PRs must be behavior-preserving.
3. Every refactor PR includes a minimal verification checklist:
   - at least one happy-path manual check
   - at least one persistence/reload check if any file I/O or localStorage is involved
4. Avoid churn:
   - do not re-indent unrelated code
   - do not rename public APIs unless necessary (and then update docs in the same PR)

## Step 0: Create an Inventory (1 pass, mechanical)
Produce a short ranked list of refactor targets based on:
- size (LoC or bytes)
- churn (git commits in last N days)
- domains mixed in one file (qualitative)

Suggested commands (run later):
```sh
# Largest tracked files (rough signal)
git ls-files -z | xargs -0 wc -l | sort -n | tail -n 30

# Highest churn files (rough signal)
git log --name-only --pretty=format: | sed '/^$/d' | sort | uniq -c | sort -nr | head -n 50
```

Output should list each target with:
- why it qualifies (size/churn/domains)
- what domain boundaries to extract
- what risks exist (shared state, implicit ordering, global window exports)

## Step 1: Stabilize a Module Boundary Contract
Before extracting anything, define conventions:
- Backend:
  - prefer package-by-domain under `src/main/java/com/miniide/` (e.g. `pipeline/`, `canon/`, `conference/`)
  - controllers remain thin: validate inputs, call service, map to JSON
  - services own persistence format and invariants
- Frontend:
  - domain modules live under `src/main/resources/public/app/`
  - `window.*` exports are allowed but should be consolidated per domain (minimize global sprawl)
  - API wrappers should live in `src/main/resources/public/api.js` (or a domain wrapper that calls it)

## Step 2: Refactor Backlog (Ordered by Leverage)
This is the recommended order. It is intentionally biased toward enabling Execution Modes / Pipeline work.

1. Pipeline backend isolation (driver: Execution Modes)
- Create `com.miniide.pipeline`:
  - `StepRunner`
  - `RecipeRegistry` (initially static/in-memory)
  - `PipelineRunStore` (disk persistence)
- Create `PipelineController` with `POST /api/pipeline/run`, `GET /api/pipeline/run/{id}`.

2. Pipeline frontend isolation (driver: Execution Modes)
- Create `src/main/resources/public/app/pipeline.js`:
  - API calls
  - run panel renderer (even if minimal)
  - no cross-domain imports (agents/conference code should call into pipeline module via a small exported surface)

3. Agents/conference split points (driver: reduce risk in `agents.js`)
- Extract canon indexing modal + logic:
  - `app/canon_index.js` (UI + run loop)
- Extract conference orchestration:
  - `app/conference.js` (round lifecycle + evidence/rules injection)
- Keep `agents.js` as roster + modals glue that delegates.

4. Backend controller/service tightening (driver: audit)
- Reduce "god controller" growth by extracting controllers by domain (if not already).
- Standardize error responses and ensure every endpoint has a predictable JSON error shape.

## Step 3: Verification Strategy
Prefer "cheap checks" tied to user workflows.

For each refactor unit, document a 3-item checklist:
1) primary happy path
2) reload/persistence path (if applicable)
3) one failure path (invalid input, provider failure, missing project, etc.)

If tests exist for the touched area, run them; if not, add a minimal test only when it materially reduces risk.

## Step 4: Documentation Sync
After each refactor, update docs only when:
- file paths referenced in docs change
- behavior or API contracts change
- audit ledger references need a stable new code anchor

## Exit Criteria (When Refactor Work Is "Enough")
- Execution Modes / Pipeline code lives in dedicated backend + frontend modules.
- `agents.js` no longer contains unrelated large subsystems (canon indexing + conference moved out).
- Audit ledger can link most claims to stable code anchors without spelunking one huge file.

