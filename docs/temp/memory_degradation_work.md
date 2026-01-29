# Memory Degradation - Work Plan (Temp)

Date: 2026-01-28
Scope: Memory Degradation (Phase 1 MVP) + Early Grounding tweak + Phase 2 (tagging + UI)

## Goals (Today)
- Add interest-level metadata to Issues and default/migration behavior.
- Implement decay + compression (L3/L2/L1) and search filters.
- Add early grounding injection for issues with epistemicStatus >= agreed.

## Required Decisions
- Where to store interest metadata: Issue fields (default) vs separate memory record.
- Epistemic status source: new `epistemicStatus` field vs existing tags.
- Decay thresholds (days since closed / last accessed).

## Implementation Checklist
### Backend
- [x] Update `Issue` model: interestLevel, lastAccessedAt, lastCompressedAt, compressedSummary, semanticTrace, epistemicStatus.
- [x] Update `IssueMemoryService` to default/backfill missing fields on load.
- [x] Add issue list filters: minInterestLevel / maxInterestLevel.
- [x] Add decay/compress routines + scheduler hook.
- [x] Add revive endpoint to restore to L3.
- [x] Update `IssueController` + `api.js` wrappers.

### Frontend
- [x] Add Issue Board filters for interest level (Active vs All).
- [x] Render compressed views based on level.
- [x] Add “Revive Full Thread” action (L1).
- [x] Add Epistemic status control in Issue modal.
- [x] Add memory-levels readout (L1–L5) in Issue modal.
- [x] Add Dev Tools controls for issue decay + revive.
- [x] Add Dev Tools AI compression button for issue summaries.

### Prompt Grounding (Early)
- [x] Inject R1+R3 header for issues with epistemicStatus >= agreed.
- [x] Define small, consistent header template.

## Notes
- Memory Degradation spec anchors: `docs/reference/cr_memory.md#memory-interest-levels` and `#memory-compression-logic`.
- Early grounding idea from Engram paper (conditional memory). R2 concept packets deferred.
- Compression quality is still rough; Phase 2 should swap in prompt-backed summaries + better semantic trace heuristics.
 - Phase 2 progress: personal tags per agent (saved on issue memory record), issue board filter by personal tags + agent, memory-level badge on issue cards.
