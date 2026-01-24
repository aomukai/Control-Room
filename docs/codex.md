# Next-Up Briefing

## Authority & Navigation
roadmap.md is the sole source of truth for implementation status and sequencing. index.md is a router to find the right reference docs and anchors; use it for navigation only.

## What’s Next
Focus only on what’s still pending (see roadmap.md for authoritative status).

### Near-Term Focus
- Prompt hardening + validators for agent actions (focus on local models).
- Memory Degradation integration (search filters + compression behaviors) (docs/reference/cr_memory.md#memory-interest-levels).
- Personal Tagging (agent-specific issue filtering) (docs/reference/cr_memory.md#memory-personal-tagging).

## User-Requested Next Work (Explorer)
- Compendium multi-select: move + delete (files only; no folder multi-select).
- Compendium folder delete: disallow if not empty; show error and keep folder hidden when empty.

Guardrails:
- Do not redesign Workbench layout or introduce new flows beyond the active milestone scope.
- Update roadmap/docs only when required to reflect completed work or resolve contradictions.
- Prompt hygiene matters: design + test prompts for small/quirky local models (e.g., lfm2, gpt-oss), add validators/retries; if it works there, SOTA should follow.
