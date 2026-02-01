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
- Prompt hardening + receipts for agent actions (task packets, receipts, validators, retries; focus on local models). PH-0 and PH-1 are complete; next is PH-2 (Chief router).
- Memory Degradation (Phase 2): access demotion + leech/Wiedervorlage MVP complete; compression prompts tightened (docs/reference/memory_part2.md, docs/reference/memory_part2_1.md).
- Telemetry: retention verified and log paths confirmed under `.control-room/telemetry/`; dev tools status/test/prune available (see docs/roadmap.md Telemetry + Issue Memory API sections).

## Working Notes
- Prompt hardening state machine + requirements tracked in docs/statemachine.md.

## Next Session Plan (PH-2 Chief Router)
1) Add a minimal “Chief router” entry point: user input -> task packet or clarify packet (intent=clarify).
2) Define v0.1 clarification packet format (choices + question) using task packet schema.
3) Wire expectSchema=task_packet for Chief’s routing call; validate JSON-only with retries.
4) Save the packet to audit store (packet write only; receipt storage starts in PH-3).
5) Add a small manual test: “let’s do scene 3” returns clarify packet with choices.

## Ops Note
- Use host CLI (Codex CLI) for git push; VS Code Flatpak sandbox can’t access host keyring/gh auth.
- Gradle compile checks must be run in the host environment (JAVA_HOME is not set in the sandbox).

Guardrails:
- Do not redesign Workbench layout or introduce new flows beyond the active milestone scope.
- Update roadmap/docs only when required to reflect completed work or resolve contradictions.
- Prompt hygiene matters: design + test prompts for small/quirky local models (e.g., lfm2, gpt-oss), add validators/retries; if it works there, SOTA should follow.
