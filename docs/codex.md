Session contract:

Step 1: Orientation only. Read these files: docs/index.md, docs/roadmap.md, docs/codex.md, docs/codebase.md.
Step 2: Report understanding + risks + proposed plan. No code changes yet.

Any claim like “file not found” must be verified with ls/test -f/direct open.

# Next-Up Briefing

## Authority & Navigation
roadmap.md is the sole source of truth for implementation status and sequencing. index.md is a router to find the right reference docs and anchors; use it for navigation only.

## What’s Next
Focus only on what’s still pending (see roadmap.md for authoritative status).

### Near-Term Focus
- Conference grounding: evidence format parser, structural claim enforcement, chain-of-thought blocking, and quote verification robustness.
- VFS alignment: outline content parity between VFS (Story/SCN-outline.md) and outline API.
- Prompt tools baseline: file_locator/task_router/canon_checker/outline_analyzer prompts + usage expectations.
- Telemetry: retention verified and log paths confirmed under `.control-room/telemetry/`; dev tools status/test/prune available (see docs/roadmap.md Telemetry + Issue Memory API sections).

## Working Notes
- Conference grounding state machine + requirements tracked in docs/statemachine.md.

## Next Session Plan
1) Fix Evidence line parser to accept **Evidence:** while preserving single-line rule.
2) Enforce structural claim evidence (line/section or outline entry).
3) Add chain-of-thought rejection and plain-text tool-call rejection.
4) Re-run a conference test and capture logs.

## Ops Note
- Use host CLI (Codex CLI) for git push; VS Code Flatpak sandbox can’t access host keyring/gh auth.
- Gradle compile checks must be run in the host environment (JAVA_HOME is not set in the sandbox).

Guardrails:
- Do not redesign Workbench layout or introduce new flows beyond the active milestone scope.
- Update roadmap/docs only when required to reflect completed work or resolve contradictions.
- Prompt hygiene matters: design + test prompts for small/quirky local models (e.g., lfm2, gpt-oss), add validators/retries; if it works there, SOTA should follow.
