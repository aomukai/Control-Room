Session contract:

Step 1: Orientation only. Read these files: docs/index.md, docs/roadmap.md, docs/codex.md, docs/codebase.md.
Step 2: Report understanding + risks + proposed plan. No code changes yet.

Any claim like “file not found” must be verified with ls/test -f/direct open.

# Next-Up Briefing

## Authority & Navigation
roadmap.md is the sole source of truth for implementation status and sequencing. index.md is a router to find the right reference docs and anchors; use it for navigation only.

## What's Next
Focus only on what's still pending (see roadmap.md for authoritative status).

### Near-Term Focus
- Execution Modes: Pipeline Phase B (agent calls), task_router promotion to standalone Java service, prompt template registry, Session Plan UI.
- 1:1 chat tool-call reliability: continue hardening for small/local models (aliasing, schema enforcement).

### Recently Completed
- Pipeline Phase A: RecipeRegistry + RefResolver + RunStore + StepRunner + RunController + bundled recipe. POST /api/runs executes file_locator → outline_analyzer → canon_checker end-to-end with run persistence, dual-mode cache, and consolidated polling.
- Conference two-phase redesign: Chief-led tool orchestration (phase 1) + role-based interpretation with abstain (phase 2). Tested end-to-end via conference auto-saved issue receipts (Phase 1 receipts + Phase 2 grounded responses; per-role evidence constraints enforced).
- Tool suite: `consistency_checker` — multi-file cross-referencing (entity extraction, shared terms, event markers) for contradiction detection.
- Tool suite: `scene_draft_validator` — auto-matches scene to outline beat + loads POV canon card in one call.
- Tool suite: `prose_analyzer` — quantitative prose metrics (sentence stats, dialogue ratio, repeated words, POV signals).
- Canon Index boot sequence: mandatory LLM-driven indexing of all canon files when CoS is first wired. Frontend-driven loop with progress log, status panel, run report issue, and "retry failed only" support. Canon.md served via VFS.
- `skipTools` flag on `/api/ai/chat`: bypasses tool catalog, grounding header, and tool loop for raw LLM calls (used by canon indexing).
- Highlander rule: only one Chief of Staff allowed; template hidden from creation wizard when one exists.
- "Assistant" → "Chief of Staff" rename across all user-facing UI (agent cards, wizards, tooltips, archetype dropdown).
- Context-sensitive agent card clicks: red=model modal, yellow=toast, green(no index)=indexing popup, green(indexed)=1:1 chat.
- Agent creation blocked until canon index is built.

## Working Notes
- Grounding/tooling state machine + current status tracked in docs/statemachine.md.
- Pipeline design specs: docs/reference/execution_modes.md appendices A-G. Phase A implemented, Phase B pending.
- Pipeline implementation: `src/main/java/com/miniide/pipeline/` (RecipeRegistry, RefResolver, RunStore, StepRunner) + `controllers/RunController.java`.
- Canon index design tracked in memory/canon-index-design.md.
- Tool implementation progress tracked in memory/tool-implementation.md.

## Next Session Plan
1) Execution Modes: Pipeline Phase B — agent calls via /api/ai/chat with skipTools, prompt template registry, tier-based step sizing. Target: full creative_draft_scene recipe executing Phase A + Phase B end-to-end.
2) task_router promotion: standalone Java service callable from StepRunner + Chief + existing tool loop.
3) Session Plan UI: ordered checklist with status, pause/resume, dependency blocking.
4) 1:1 chat tool-call reliability: continue hardening strict JSON emission and schema enforcement for small/local models.
5) Provider resiliency: reduce response bloat and add retries for transient network/provider failures.

## Ops Note
- Use host CLI (Codex CLI) for git push; VS Code Flatpak sandbox can’t access host keyring/gh auth.
- Gradle compile checks must be run in the host environment (JAVA_HOME is not set in the sandbox).

Guardrails:
- Do not redesign Workbench layout or introduce new flows beyond the active milestone scope.
- Update roadmap/docs only when required to reflect completed work or resolve contradictions.
- Prompt hygiene matters: design + test prompts for small/quirky local models (e.g., lfm2, gpt-oss). Conference is only a test harness; all fixes must be platform-wide.

## Current Reality (Notes For Codex)
- Conference tool-call compliance is resolved architecturally: agents no longer emit tool calls in conference. The Chief of Staff handles all tools in phase 1 (identical to 1:1 chat, where it works). Agents only interpret pre-fetched results.
- The old problem ("models won't emit strict JSON in conference") was caused by prompt overload — conference stacked role framing, evidence rules, grounding prelude, full tool catalog, and tool protocol into one prompt. The two-phase model eliminates this by separating concerns.
- 1:1 chat tool-call compliance remains an ongoing concern for small/local models. When debugging:
  - Prefer STOP_HOOK tool-call failures over Evidence validator failures when `requireTool` is enabled.
  - Use session receipts (`/api/audit/sessions/<id>/receipts` via UI) to confirm tools actually ran instead of trusting model-provided `receipt_id`.
