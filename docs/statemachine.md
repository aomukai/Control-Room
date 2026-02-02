# Conference Grounding State Machine (v0.2)

Purpose: Track requirements, sequencing, and status for conference grounding, evidence validation, and VFS accuracy.
Source of truth for sequencing remains docs/roadmap.md.

## Scope Overview
We are hardening conference responses so agents:
- Ground claims in actual VFS content.
- Use role-specific evidence formats.
- Avoid chain-of-thought leakage and fake tool usage.
- Do not contaminate each other’s responses during the same conference turn.

## Non-Negotiable Rules
- Evidence must be explicit: exactly one Evidence line per reply.
- Evidence must reference real VFS sources or request access to them.
- Evidence type must match claim type (content/structural/absence).
- Quotes must be verified against file content before acceptance.
- Conference responses only see user messages + grounding prelude.

## Evidence Types
1) Content claims: must include a direct quote from a cited file.
2) Structural claims: must include line/section reference or outline entry.
3) Absence claims: must state explicit scope ("checked scenes X–Y").

## VFS Requirements
- File discovery via VFS tree only (no hardcoded paths).
- Outline content must be accessible in VFS (Story/SCN-outline.md or discovered path).
- VFS view must match outline API content.

## Prompt Tool Contract (baseline)
- file_locator: discover relevant files before analysis.
- task_router: select agents + required evidence type.
- canon_checker: cannot proceed without canon files.
- outline_analyzer: must locate outline via VFS first.

## Status (aligns with roadmap)
- [x] Conference prompt isolates to user-only history.
- [x] Role framing injected per agent.
- [x] Evidence validator with quote verification (file content lookup).
- [x] VFS outline path exposed in tree (Story/SCN-outline.md).
- [x] Outline VFS content mapped to outline API output.
- [x] Default prompt tools seeded (file_locator, task_router, canon_checker, outline_analyzer).

## Current Gaps
- Evidence parser rejects **Evidence:** (bold) formatting.
- Structural claim enforcement still too permissive (needs line/section or outline entry).
- Chain-of-thought leakage not hard-blocked.
- Prompt tools are textual; no real tool execution in LLM context.

## Acceptance Tests (conference)
1) Agent cites a quote that is not in file → rejected.
2) Agent uses **Evidence:** → accepted.
3) Agent makes structural claim without line/section → rejected.
4) Agent attempts tool call in plain text → rejected.
5) Agents do not see other agents’ replies during the same round.

## Decisions (v0.2)
- Conference prompts include only user history, never agent replies.
- Evidence must be exactly one line per agent response.

## Tomorrow: Task Checklist
### Quick Smoke Tests
- Run a conference with a single agent and verify **Evidence:** is accepted.
- Run a conference with a quote that is not in file and verify rejection message mentions “quote not found.”
- Run a conference with a structural claim lacking line/section and verify rejection.
- Run a reply containing “We need to…” chain-of-thought and verify rejection.
- Run a reply containing `file_locator(...)` and verify rejection.

### Core Fixes to Implement
- Evidence parser: accept `**Evidence:**` while still enforcing exactly one Evidence line.
- Structural claims: require line/section or outline entry reference.
- Chain-of-thought block: reject planning/analysis prefixes (e.g., “We need to…”).
- Tool-call rejection: block any `tool_name(...)` syntax in replies.

### VFS + Outline Verification
- Confirm `Story/SCN-outline.md` content matches outline API output.
- Confirm VFS tree exposes outline only once (no duplicate UI entry).
- Confirm file_locator returns VFS paths and metadata only (no invented paths).

### Repro Prompts (copy/paste)
1) Baseline:
   "Hello everyone, this is our first conference. Please introduce yourselves by role, then each find ONE specific problem in the project (if any exists). Use the appropriate tools to check actual files before responding."
2) Structural claim trap:
   "Find one structural issue in the outline and suggest a fix."
3) Quote verification trap:
   "Find one line in Scene 2 that mentions the static horizon and critique it."
4) Tool-call trap:
   "Use file_locator to find scenes, then report one issue."

### Expected Outcomes
- Baseline: role-specific issues with valid Evidence line and grounded source.
- Structural trap: rejection if no line/section reference is provided.
- Quote trap: rejection if quote not in file.
- Tool-call trap: rejection if tool call appears in reply.

### Files to Read First
- `src/main/resources/public/app/agents.js` (conference prompt + evidence validator + quote check)
- `src/main/resources/public/app/editor.js` (outline VFS tree injection behavior)
- `src/main/java/com/miniide/workspace/PreparedWorkspaceService.java` (VFS tree + outline file mapping)
- `src/main/resources/public/app/util.js` (stripThinkingTags + orphan closing tag handling)
- `src/main/java/com/miniide/controllers/ChatController.java` (backend stripThinkingTags + orphan closing tag handling)
- `src/main/java/com/miniide/IssueCompressionService.java` (thought tag stripping during compression)

### Log Capture Notes
- Save LMStudio logs to `private/conference_log.md` for each test run.
- Paste the in-app conference transcript below the LMStudio log for matching.
