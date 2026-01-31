Session contract:

Step 1: Orientation only. Read these files: docs/index.md, docs/roadmap.md, docs/codex.md, docs/codebase.md.
Step 2: Report understanding + risks + proposed plan. No code changes yet.

Any claim like “file not found” must be verified with ls/test -f/direct open.

# Next-Up Briefing

## Authority & Navigation
roadmap.md is the sole source of truth for implementation status and sequencing. index.md is a router to find the right reference docs and anchors; use it for navigation only.

## What?s Next
Focus only on what?s still pending (see roadmap.md for authoritative status).

### Near-Term Focus
- Prompt hardening + validators for agent actions (focus on local models).
- Memory Degradation (Phase 2): access demotion + leech/Wiedervorlage MVP complete; compression prompts tightened (docs/reference/memory_part2.md, docs/reference/memory_part2_1.md).
- Telemetry: retention verified and log paths confirmed under `.control-room/telemetry/`; dev tools status/test/prune available (see docs/roadmap.md Telemetry + Issue Memory API sections).

Guardrails:
- Do not redesign Workbench layout or introduce new flows beyond the active milestone scope.
- Update roadmap/docs only when required to reflect completed work or resolve contradictions.
- Prompt hygiene matters: design + test prompts for small/quirky local models (e.g., lfm2, gpt-oss), add validators/retries; if it works there, SOTA should follow.
