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
- Tool suite expansion: implement remaining tools (issue_status_summarizer, stakes_mapper, line_editor, scene_impact_analyzer, reader_experience_simulator, timeline_validator, beat_architect) from basic_tool_suite.md.
- Canon index UX: polish indexing flow, handle edge cases (model failure mid-index, re-index trigger).
- Conference grounding hardening: remaining gaps from statemachine.md.

### Recently Completed
- Tool suite: `consistency_checker` — multi-file cross-referencing (entity extraction, shared terms, event markers) for contradiction detection.
- Tool suite: `scene_draft_validator` — auto-matches scene to outline beat + loads POV canon card in one call.
- Tool suite: `prose_analyzer` — quantitative prose metrics (sentence stats, dialogue ratio, repeated words, POV signals).
- Canon Index boot sequence: mandatory LLM-driven indexing of all canon files when CoS is first wired. Frontend-driven loop with progress log. Canon.md served via VFS.
- `skipTools` flag on `/api/ai/chat`: bypasses tool catalog, grounding header, and tool loop for raw LLM calls (used by canon indexing).
- Highlander rule: only one Chief of Staff allowed; template hidden from creation wizard when one exists.
- "Assistant" → "Chief of Staff" rename across all user-facing UI (agent cards, wizards, tooltips, archetype dropdown).
- Context-sensitive agent card clicks: red=model modal, yellow=toast, green(no index)=indexing popup, green(indexed)=1:1 chat.
- Agent creation blocked until canon index is built.

## Working Notes
- Grounding/tooling state machine + current status tracked in docs/statemachine.md.
- Canon index design tracked in memory/canon-index-design.md.
- Tool implementation progress tracked in memory/tool-implementation.md.

## Next Session Plan
1) User compiles and tests consistency_checker + scene_draft_validator end-to-end.
2) Begin implementing next tool from the suite (timeline_validator or line_editor).
3) Canon index UX edge cases (model failure mid-index, re-index trigger).

## Ops Note
- Use host CLI (Codex CLI) for git push; VS Code Flatpak sandbox can’t access host keyring/gh auth.
- Gradle compile checks must be run in the host environment (JAVA_HOME is not set in the sandbox).

Guardrails:
- Do not redesign Workbench layout or introduce new flows beyond the active milestone scope.
- Update roadmap/docs only when required to reflect completed work or resolve contradictions.
- Prompt hygiene matters: design + test prompts for small/quirky local models (e.g., lfm2, gpt-oss). Conference is only a test harness; all fixes must be platform-wide.
