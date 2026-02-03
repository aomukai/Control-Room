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
- Grounding + evidence enforcement across all contexts (conference chat is just the playground).
- Tool execution + receipts + audit log: strict JSON tool-call protocol with nonce, schema validation, and signed receipts.
- VFS alignment: outline content parity between VFS (Story/SCN-outline.md) and outline API.
- Prompt tools baseline: file_locator/task_router/canon_checker/outline_analyzer prompts + JSON-only usage expectations.
- Telemetry: rejection buckets + conference tags working and visible in dev tools.

## Working Notes
- Grounding/tooling state machine + current status tracked in docs/statemachine.md.

## Next Session Plan
1) Verify LM Studio JSON schema enforcement: tool call returns pure JSON with nonce on first try.
2) Run single-agent conference test: tool call → receipt minted → Evidence line references receipt_id.
3) If any JSON-only violations remain, adjust schema or provider parameters (no brute-force retries).

## Ops Note
- Use host CLI (Codex CLI) for git push; VS Code Flatpak sandbox can’t access host keyring/gh auth.
- Gradle compile checks must be run in the host environment (JAVA_HOME is not set in the sandbox).

Guardrails:
- Do not redesign Workbench layout or introduce new flows beyond the active milestone scope.
- Update roadmap/docs only when required to reflect completed work or resolve contradictions.
- Prompt hygiene matters: design + test prompts for small/quirky local models (e.g., lfm2, gpt-oss). Conference is only a test harness; all fixes must be platform-wide.
