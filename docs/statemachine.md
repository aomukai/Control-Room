# Grounding + Tooling State Machine (v0.5)

Purpose: Track requirements, sequencing, and status for grounding + tool execution across the platform.
All tool-call and evidence contracts apply system-wide (conference + 1:1 chat + tasks + any agent loop).
Source of truth for sequencing remains docs/roadmap.md.

## Scope Overview
We are hardening agent responses so agents:
- Ground claims in actual VFS content.
- Use role-specific evidence formats.
- Avoid chain-of-thought leakage and fake tool usage.
- Do not contaminate each other's responses during the same conference turn.
- Use real tool execution with receipts when they claim tool use.

Conference chat uses a **two-phase round model**: the Chief of Staff executes all tool calls (phase 1), then every agent — including the Chief — interprets the results from their role's perspective or abstains (phase 2). Agents never emit tool calls in conference. See "Conference Round Lifecycle" below.

## Non-Negotiable Rules
- Evidence must be explicit: exactly one Evidence line per reply.
- Evidence must reference real VFS sources or request access to them.
- Evidence type must match claim type (content/structural/absence).
- Quotes must be verified against file content before acceptance.
- Conference agents see user messages + injected tool results from Chief's phase 1 (never other agents' replies or raw tool catalogs).

## Evidence Types
1) Content claims: must include a direct quote from a cited file.
2) Structural claims: must include line/section reference or outline entry.
3) Absence claims: must state explicit scope ("checked scenes X–Y").

## VFS Requirements
- File discovery via VFS tree only (no hardcoded paths).
- Outline content must be accessible in VFS (Story/SCN-outline.md or discovered path).
- VFS view must match outline API content.
- Canon.md served in VFS root (alongside Story/ and Compendium/) — backed by `.control-room/canon/canon-index.md`.

## Prompt Tool Contract (baseline)
- Tools are real and executable (no prompt-only fake tools).
- Tool call protocol is strict JSON with nonce (single object only).
- file_locator: discover relevant files before analysis.
- task_router: select agents + required evidence type.
- canon_checker: cannot proceed without canon files.
- outline_analyzer: must locate outline via VFS first.

## Status (aligns with roadmap)
- [x] Global prompt registry merge (tools are platform baseline; project overrides warn).
- [x] Evidence validator with quote verification (file content lookup).
- [x] Evidence parser accepts **Evidence:** while preserving single-line rule.
- [x] Chain-of-thought preface rejection + plain-text tool call rejection.
- [x] Tool execution loop (file_locator, outline_analyzer, canon_checker, task_router, search_issues, prose_analyzer, consistency_checker, scene_draft_validator).
- [x] Signed receipts + per-session audit log.
- [x] Conference save links receipts into issues/L5.
- [x] Telemetry buckets + conference tags.
- [x] Strict JSON tool-call parsing with nonce + schema validation.
- [x] LM Studio JSON schema enforcement for tool-call turns.
- [x] VFS outline path exposed in tree (Story/SCN-outline.md).
- [x] Outline VFS content mapped to outline API output.
- [x] Canon Index boot sequence: LLM-driven extraction → deterministic Canon.md compilation.
- [x] Canon.md served via VFS (PreparedWorkspaceService readFile + getTree).
- [x] `skipTools` bypass on `/api/ai/chat` for raw LLM calls.
- [x] Canon index endpoints: `GET /api/canon/index/status`, `POST /api/canon/index`.

## Current Gaps
- Evidence line can be fabricated without tool-backed excerpts (needs stronger file-read tooling later).
- Tool call required detection in 1:1 chat is heuristic; may need explicit contract flag instead of string matching.
- Conference tool-call compliance is resolved architecturally: agents no longer emit tool calls in conference (Chief handles all tools in phase 1).

## What's Happening In Practice (Tool-Call Compliance)

### Conference (Resolved)
The original problem: most models struggled with conference tool-call prompts because conference stacks role framing, evidence rules, grounding prelude, full tool catalog, and tool protocol into one prompt, then asks the model to emit strict JSON. Even models that handle the tool loop fine in 1:1 (where the prompt is leaner) choked under this prompt load.

**Architectural fix**: Conference now uses a two-phase round model. The Chief of Staff handles all tool calls in phase 1 (identical prompt weight to 1:1 chat, where tools work reliably). All other agents — including the Chief on a second pass — only interpret the pre-fetched results from their role perspective. Agents never see the tool catalog or tool protocol in conference. See "Conference Round Lifecycle" below.

### 1:1 Chat (Ongoing)
Tool-call compliance in 1:1 chat remains an ongoing concern for small/local models. Hardening in place:
- Backend tool protocol loop with strict JSON + nonce + schema checks (ChatController).
- Stop hook parsing in UI recognizes `STOP_HOOK: tool_call_rejected` (underscore type).
- Tool id aliasing in backend parser to map common hallucinations to real tools (small, curated map).
- Arg aliasing and arg-key normalization (trim/lower/separator normalization) to tolerate common key drift.
- Added `file_reader` as a real tool so models that naturally call "read a file excerpt" don't get mis-aliased into `file_locator`.
- JSON schema enforcement via `response_format` for LM Studio models.

Remaining 1:1 friction:
- Receipt reality checks: when a reply claims tool use, verify the receipt exists before accepting the Evidence line.
- Expand aliasing cautiously as new model drift patterns are observed.

## Acceptance Tests (conference — two-phase model)
1) Chief phase 1: tool call JSON accepted, receipt minted, results buffered.
2) Chief phase 1: wrong nonce → rejected, no tool executes.
3) Chief phase 1: invalid args → rejected, no tool executes.
4) Phase 2 agent cites a quote that is not in file → rejected.
5) Phase 2 agent uses **Evidence:** referencing Chief's receipt_id → accepted.
6) Phase 2 agent makes structural claim without line/section → rejected.
7) Phase 2 agent responds with ABSTAIN keyword → detected, card shows yellow "Abstained", no chat message emitted.
8) Agents do not see other agents' replies during the same round (phase 2 isolation preserved).
9) Round close: full transcript saved as issue with linked receipts.
10) Next round: agent history wiped, injection cleared, user sees continuous chat.

## Acceptance Tests (1:1 chat — unchanged)
1) Agent cites a quote that is not in file → rejected.
2) Agent uses **Evidence:** → accepted.
3) Agent makes structural claim without line/section → rejected.
4) Agent attempts tool call in plain text → rejected.
5) Tool call JSON with extra text → rejected.
6) Tool call JSON with wrong nonce → rejected.

## Decisions (v0.4)
- Conference uses a two-phase round model: Chief tool phase → agent interpretation phase.
- Chief of Staff is auto-invited to every conference and cannot be removed.
- Only the Chief's phase 1 prompt includes the tool catalog and tool protocol. All other prompts (including Chief's phase 2) receive injected tool results instead.
- Agents can abstain by responding with a single keyword (ABSTAIN). The system intercepts this and shows a visual indicator on the agent card (yellow "Abstained" subtitle) instead of posting to the chat.
- Each conference round is archived as an issue ("Conference {sessionId} — Turn {n}") with linked receipts.
- Agent history is wiped between rounds. No agent carries context from prior rounds. The user sees a continuous chat in the UI.
- Evidence must be exactly one line per agent response (phase 2 only; Chief phase 1 is tool-only).
- Tool calls remain strict JSON-only with server nonce (applies to Chief phase 1 and 1:1 chat).
- All tool/evidence contracts apply platform-wide (conference + 1:1 + tasks).

## Now: Task Checklist

### Conference Two-Phase Implementation
- [x] Chief of Staff auto-invite enforcement (UI + backend).
- [x] Chief phase 1: lean tool-call prompt (same weight as 1:1), tool execution, results into round buffer.
- [x] Tool result injection framing for phase 2 agents.
- [x] Abstain detection (keyword match on trimmed response) + agent card visual state (yellow "Abstained" subtitle).
- [x] Round-close: save transcript as issue ("Conference {sessionId} — Turn {n}") with receipt linking.
- [x] History wipe between rounds (agents see no prior context; UI maintains continuous view).
- [x] Chief phase 2: second pass as normal attendee with injection.

### Smoke Tests (Conference)
- Chief phase 1: tool call succeeds, receipt minted, results buffered.
- Chief phase 1: wrong nonce → rejected.
- Phase 2 agent: receives injection, responds with role interpretation + evidence.
- Phase 2 agent: responds with ABSTAIN → detected, card goes yellow, no chat message.
- Round close: issue created with full transcript + receipts.
- Next round: buffer wiped, agents have no memory of prior rounds.

### Smoke Tests (1:1 — unchanged)
- JSON-only tool call → receipt minted → Evidence references receipt_id.
- Wrong nonce → rejected with tool_call_nonce_invalid.
- Tool call with extra text → rejected with tool_call_invalid_format.
- Tool syntax in plain text → rejected.

### Files to Read First
- `src/main/resources/public/app/agents.js` (conference prompt + evidence validator + round lifecycle)
- `src/main/java/com/miniide/controllers/ChatController.java` (backend tool-call protocol + JSON schema enforcement)
- `src/main/java/com/miniide/tools/ToolExecutionService.java` (tool execution backends)
- `src/main/java/com/miniide/AuditStore.java` (receipt storage + issue linking)
- `src/main/resources/public/app/util.js` (stripThinkingTags + orphan closing tag handling)
- `src/main/java/com/miniide/providers/chat/OpenAiCompatibleChatProvider.java` (LM Studio response_format)

---

# System Map (Comprehensive, No Dummies)

This section is the full source of truth for how grounding + tools + receipts work end-to-end. It is intentionally exhaustive and should be updated whenever a moving part changes. All rules are platform-wide (conference + 1:1 + tasks).

## Core Principles (Non-Negotiable)
- No prompt-only fake tools. All tools must execute for real and emit signed receipts.
- Evidence must be verifiable and tied to receipts when tools are used.
- Tool calls must be strict JSON with a server nonce. No lenient parsing. No function-like syntax.
- If a tool call is malformed, no tool executes and the turn is rejected with a canonical reason.
- Same contracts apply to conference + 1:1 + tasks + any agent loop. Conference has additional structure (two-phase rounds) but does not relax any core rule.

## Strict Tool Call Protocol (Text-Only Envelope)
- Envelope: single JSON object and nothing else.
- Format: `{"tool":"<id>","args":{...},"nonce":"<server nonce>"}`
- Rules: no extra text, no markdown, no code fences, no multiple JSON objects.
- Validation: tool must be registered, args must match schema, nonce must match current turn.
- Rejections: `tool_call_invalid_format`, `tool_call_unknown_tool`, `tool_call_invalid_args`, `tool_call_multiple`, `tool_call_nonce_invalid`.

### Tool Name Drift (Alias Policy)
Some models reliably produce *nearby* tool names (e.g. `file_viewer`) rather than the exact registered id (`file_locator`).
We support a curated alias map in the backend parser:
- Only map aliases that are unambiguous and safe.
- Prefer adding a real tool (like `file_reader`) over aliasing a distinct tool intent into an existing tool with incompatible args.

## Tool Execution Loop (Backend)
- Entry point: `src/main/java/com/miniide/controllers/ChatController.java`
- Flow: prompt → (tool call?) → execute → append result → repeat up to the tier-based tool budget.
- Max tool calls per turn: derived from tier caps (fallback to `MAX_TOOL_STEPS` if tier info unavailable).
- Conference floor: when `conferenceId` is present, the tool loop enforces a small minimum tool budget so Phase 1 can complete multi-step evidence gathering in one round.
- Tool output injected into prompt is truncated and hashed. Limits are enforced per step and per turn; conferences have a higher budget than default turns.
- Tool call is executed only if strict JSON envelope passes parse + schema + nonce.
- After any tool result, the model must send a **decision JSON** (strict, no extra text):
  - Another tool: `{"action":"tool","tool":"<id>","args":{...},"nonce":"<server nonce>"}`
  - Finish: `{"action":"final","nonce":"<server nonce>"}`
- Decision JSON is schema‑enforced to prevent chain‑of‑thought and partial tool calls.
- If the tool step limit is reached, the system **forces a final response** (no additional tools).
- Tool call retries are not brute-forced; malformed calls are rejected immediately.
- Optional `toolPolicy` can be supplied to `/api/ai/chat` to constrain tools per request:
  - `allowedTools`: list of allowed tool IDs (schema + execution enforced)
  - `requireTool`: force a tool call on the first step (bypasses heuristic)
- **`skipTools` bypass**: When `skipTools: true` is passed to `/api/ai/chat`, the entire tool machinery is bypassed — no tool catalog prepended, no grounding header, no tool protocol appended, no tool loop. The prompt is sent directly to the model via `callAgentWithGate()`. Used by canon indexing for raw LLM extraction calls.
- Agent turns are serialized by `AgentTurnGate` to avoid parallel tool loops.
- Constants (defaults): `MAX_TOOL_STEPS=6`, `MAX_TOOL_BYTES_PER_STEP=8000`, `MAX_TOOL_BYTES_PER_TURN=16000`.
- Constants (conference): `MAX_TOOL_BYTES_PER_STEP_CONFERENCE=12000`, `MAX_TOOL_BYTES_PER_TURN_CONFERENCE=48000`.

## LM Studio Structured Output (JSON Schema Enforcement)
- Provider: `src/main/java/com/miniide/providers/chat/OpenAiCompatibleChatProvider.java`
- When a prompt implies tool call required, we add `response_format` with `json_schema`.
- This is how we force JSON-only output (no chain-of-thought preface).
- The schema enforces the `{ tool, args, nonce }` envelope with correct types and required fields.
- After any tool result, we also enforce a **decision JSON** schema (`action: tool|final`) to avoid mixed prose/tool output.
- Applied via `ChatController` by passing `response_format` into provider chat calls.

## Tool Schema Registry
- Implementation: `src/main/java/com/miniide/tools/ToolSchemaRegistry.java`
- Schemas: `src/main/java/com/miniide/tools/ToolSchema.java`, `ToolArgSpec.java`
- All tool args must be validated by type and allowed keys; unknown keys are rejected.

## Tool IDs (Implemented and Required to Test)
- `file_locator`: locate files in VFS.
- `file_reader`: read a line-range excerpt from a file (used to support grounded quotes and to match common model intent).
- `outline_analyzer`: analyze outline structure.
- `canon_checker`: compare scenes vs canon.
- `task_router`: classify and route tasks by role.
- `search_issues`: search issues by criteria.
- `prose_analyzer`: quantitative prose metrics (sentence stats, dialogue ratio, rhythm).
- `consistency_checker`: multi-file cross-referencing for contradiction detection.
- `scene_draft_validator`: auto-match scene to outline beat + load POV canon card.

## Tool Execution Backends (Current)
- Location: `src/main/java/com/miniide/tools/ToolExecutionService.java`
- `file_locator`: uses VFS tree; metadata-only in MVP.
- `outline_analyzer`: reads outline file and produces structure summary + issue.
- `canon_checker`: compares scene vs canon files; returns quotes and status.
- `task_router`: simple role selection based on request.
- `search_issues`: filters issues from local memory service.
- `prose_analyzer`: computes sentence/paragraph stats, dialogue ratio, repeated words, POV signals, adverb count.
- `consistency_checker`: reads N files, extracts entities and terms, builds cross-reference map; focus modes: characters, terminology, events, general.
- `scene_draft_validator`: reads scene, auto-matches outline beat (3-pass: title_slug → pov_name → scene_number), optionally loads POV canon card.

## Tool Receipts (Non-Forgeable)
- Signed receipts with HMAC; server secret stored at `.control-room/audit/secret.key`.
- Receipt fields: receipt_id, conference_id/task_id, turn_id, agent_id, tool_id, inputs, outputs/excerpt, file_refs, timestamp, signature.
- Receipt ID is always server-generated (never model-provided).

## Audit Logs (Append-Only)
- Session receipts: `.control-room/audit/sessions/<sessionId>/tool_receipts.jsonl`
- Issue audit entries: `.control-room/audit/issues/<issueId>/`
- Linking: when conference is saved, session receipts are linked into issue audit.
- Audit store implementation: `src/main/java/com/miniide/AuditStore.java`

## Evidence Validation (Frontend)
- Location: `src/main/resources/public/app/agents.js`
- Evidence line rules enforced in conference UI:
  - Exactly one `Evidence:` line.
  - Role-specific source requirements.
  - Quote validation for content claims.
  - Tool claim requires receipt_id that must exist in session receipts.
  - Chain-of-thought preface rejected.
  - Tool syntax in text rejected.

## Evidence Validation (Backend)
- Backend does tool-call parsing and tool execution only.
- Evidence checks are currently frontend enforcement for conference UI.
- Backend still strips `<think>`/`[thinking]` style tags via `stripThinkingTags` in `ChatController`.
 - Frontend also strips reasoning tags before UI validation (see `src/main/resources/public/app/util.js`).

## Prompt Registry & Tool Catalog
- Global tools registry: `src/main/resources/prompts/tools.json`
- Project overrides: `workspace/<project>/.control-room/prompts/prompts.json`
- Merge order: global → user-home (optional) → project.
- Warnings on project overrides for core tools.
- Prompt tool definitions include JSON-only tool call format.
- Prompt tool examples updated to JSON-only envelopes with `<TOOL_NONCE>` placeholder.

## Agent & Endpoint Registries
- Agents: `workspace/<project>/.control-room/agents/agents.json`
- Endpoints: `workspace/<project>/.control-room/agents/agent-endpoints.json`
- Providers and keys in Settings.

## Provider Chat Routing
- Entry: `src/main/java/com/miniide/providers/ProviderChatService.java`
- OpenAI-compatible providers: `src/main/java/com/miniide/providers/chat/OpenAiCompatibleChatProvider.java`
- LM Studio uses OpenAI-compatible path with base URL `http://<host>:1234`.
- Tool catalog injection happens in `ChatController` before provider call.

## Telemetry (Rejections + Tokens)
- Backend: `src/main/java/com/miniide/TelemetryStore.java`
- Conference tags: conference_id + agent_id.
- Buckets: evidence_invalid, quote_not_found, tool_syntax, cot_leak, format_error.
- Tool-call failures mapped into tool_syntax or format_error.
- Telemetry API: `src/main/java/com/miniide/controllers/TelemetryController.java`

## Conference Round Lifecycle (Two-Phase Model)

Conference chat is a series of independent rounds. Each round is self-contained: agents carry no context from prior rounds. The user sees a continuous chat in the UI.

### Phase 1: Chief Tool Execution
1. User sends a prompt to the conference.
2. The Chief of Staff is always first in the round (auto-invited, cannot be removed from conference).
3. Chief receives a **lean tool-call prompt** — same weight as a 1:1 chat prompt. No evidence rules, no role framing for other agents, no grounding prelude. Just the user's prompt + tool catalog + tool protocol + nonce.
4. Chief runs all necessary tool calls (e.g. `file_locator` → `consistency_checker` → `prose_analyzer`). The existing tool execution loop handles this identically to 1:1 chat.
5. Tool results and receipt IDs are stored in a **round buffer** (in-memory on the conference session object). No disk I/O needed — the buffer only survives one round.

### Phase 2: Agent Interpretation
6. Chief gets a **second pass** as a normal attendee. The prompt now includes:
   - Role framing (Chief of Staff perspective)
   - Injected tool results from phase 1 (not the tool catalog — the actual data)
   - Injection framing: "The following data was retrieved for this discussion. You are [Role]. If this is relevant to your expertise, provide your perspective. If not, respond with only: ABSTAIN"
   - Evidence rules (single Evidence line, quote verification, etc.)
   - User's original prompt
7. All other agents take their turn in roster order with the same injection + their own role framing.
8. Each agent either:
   - **Responds** with a role-specific interpretation + Evidence line, OR
   - **Abstains** by responding with the ABSTAIN keyword. The system intercepts this: no chat message is posted, and the agent's card shows a yellow "Abstained" subtitle.
9. Agents do NOT see other agents' replies during the same round (phase 2 isolation preserved).

### Round Close
10. After all agents have responded or abstained, the round concludes.
11. The full transcript (user prompt + all agent responses) is saved as an issue: "Conference {sessionId} — Turn {roundNumber}". Receipts from Chief's phase 1 are linked into the issue audit.
12. Round buffer is wiped. Agent history is wiped. No agent will see anything from this round in future rounds.
13. The user sees the full transcript in the UI as a continuous chat. Only the backend forgets.

### Properties
- **Tools run once per round** — not N times for N agents. A 6-agent conference with 3 tool calls = 3 tool calls total, not 18.
- **No JSON gymnastics for agents** — only Chief phase 1 emits tool-call JSON (same as 1:1, where it works). All other prompts are natural language.
- **No exploding context** — each round is a clean slate. Context never grows across rounds.
- **Automatic audit trail** — every round becomes a searchable issue with receipts.
- **Forward-compatible** — when a model can handle the full thing natively, the constraints can be relaxed without changing the architecture.

## Abstain Protocol

Abstain is a first-class conference action. An agent abstains when the tool results and user prompt are not relevant to their role.

- **Detection**: The system checks if the agent's trimmed response equals the ABSTAIN keyword (case-insensitive). A response like "I would not abstain" does NOT trigger it — only a response that IS the keyword.
- **Visual feedback**: The agent's card in the conference attendee list shows a yellow subtitle "Abstained" instead of "Participant". Tooltip: "This agent had nothing to add from their role this round."
- **No chat message**: An abstaining agent produces no visible chat message. The user sees who abstained by glancing at the agent cards.
- **Evidence skip**: Abstaining agents are not subject to evidence validation (there is no response to validate).
- **Credits**: Abstaining does not affect credits positively or negatively. It is a neutral action.

### Agent Card States (Conference)
- **Green subtitle "Moderator"**: Chief of Staff (existing).
- **Default subtitle "Participant"**: Normal attendee, has not yet responded this round.
- **Yellow subtitle "Abstained"**: Agent chose not to contribute this round.
- **Red subtitle "Muted"**: Agent was muted by the user (skipped entirely, existing behavior).

## Conference UI
- Conference panel: `src/main/resources/public/app/agents.js`
- Phase 1 prompt: tool catalog + tool protocol + nonce (lean, no evidence/grounding).
- Phase 2 prompts: role framing + injected tool results + evidence rules (no tool catalog).
- Uses conferenceId session, per-round roundId, and per-turn turnId.
- Agents are run serially within each phase; evidence validated on client for phase 2 responses.
- Evidence validator checks receipt IDs from Chief's phase 1 by calling audit API.
- Abstain detection runs before evidence validation.

## Storage Paths (Critical)
- Conference log: `private/conference_log.md`
- Audit base: `workspace/<project>/.control-room/audit/`
- Session receipts: `workspace/<project>/.control-room/audit/sessions/<sessionId>/tool_receipts.jsonl`
- Issue audit: `workspace/<project>/.control-room/audit/issues/<issueId>/`
- Audit secret: `workspace/<project>/.control-room/audit/secret.key`
- Telemetry: `.control-room/telemetry/` (sessions + totals)
- Issues: `workspace/<project>/.control-room/issues/issues.json`
- Credits: `workspace/<project>/.control-room/credits/credits.json`
- Notifications: `data/notifications.json`
- Memory: `workspace/<project>/.control-room/memory/`
- Prompt registry (project): `workspace/<project>/.control-room/prompts/prompts.json`

## End-to-End Acceptance Criteria (No Shortcuts)

### Conference (two-phase)
- Chief phase 1: JSON-only tool call accepted, receipt minted, results buffered.
- Chief phase 1: wrong nonce / invalid args → rejected, no tool executes.
- Phase 2: agents receive injection, respond with role interpretation + Evidence referencing Chief's receipt_ids.
- Phase 2: agent responds ABSTAIN → detected, card yellow, no chat message, evidence skip.
- Round close: transcript saved as issue with linked receipts.
- Next round: buffer + history wiped; user sees continuous chat.

### 1:1 Chat / Tasks
- JSON-only tool call accepted on first try (no extra text).
- Wrong nonce is rejected and no tool executes.
- Invalid args rejected and no tool executes.
- Valid tool call executes, creates signed receipt, and receipt_id resolves in Evidence.
- Evidence line is rejected if quote not found or receipt_id missing/invalid.

## Known Friction Points
- Small models still emit chain-of-thought unless JSON schema is enforced (affects Chief phase 1 and 1:1 chat).
- File-level evidence still depends on MVP tool outputs; deeper file content tools will be needed.
- Tool call required detection in 1:1 chat is heuristic (string matching in prompt); consider explicit flag later.
- Conference tool-call compliance is resolved architecturally (agents no longer emit tool calls).

## What Must Be Implemented/Extended Next (No Dummies)
- Conference two-phase round model (Chief tool phase → agent interpretation phase → abstain → round close → issue archival → history wipe).
- Chief auto-invite enforcement (UI: non-removable from attendee list; backend: reject conference start without Chief).
- Round buffer for tool results (in-memory on conference session object).
- Tool result injection framing for phase 2 prompts.
- Abstain detection + agent card visual state (yellow "Abstained" subtitle).
- Round-close issue creation with receipt linking.
- History wipe between rounds (backend forgets; UI maintains continuous view).
- Validator cross-check hook: if model claims tool use in 1:1 chat, receipt_id must exist.
- Real file content read tooling for scenes/compendium (grounded quotes).

## Primary Files to Inspect on Each Session
- `docs/roadmap.md` (global status)
- `docs/codex.md` (session contract + next-up)
- `docs/statemachine.md` (this file)
- `src/main/java/com/miniide/controllers/ChatController.java`
- `src/main/java/com/miniide/tools/ToolExecutionService.java`
- `src/main/java/com/miniide/tools/ToolCallParser.java`
- `src/main/java/com/miniide/AuditStore.java`
- `src/main/java/com/miniide/providers/chat/OpenAiCompatibleChatProvider.java`
- `src/main/resources/public/app/agents.js`
- `src/main/resources/public/app.js` (Dev tools receipt listing)

---

# Expanded Inventory (All Connected Details)

This is the full map of everything connected to grounding + tool execution. If it is relevant, it is listed here. No shortcuts.

## Complete Tool List (Real Tools Only)
- `file_locator`
- `outline_analyzer`
- `canon_checker`
- `task_router`
- `search_issues`
- `prose_analyzer`
- `consistency_checker`
- `scene_draft_validator`

## Tool List Sources (Where the Tool Catalog Lives)
- Global tool registry: `src/main/resources/prompts/tools.json`
- Project tool registry overrides: `workspace/<project>/.control-room/prompts/prompts.json`
- Tool schema registry (runtime enforcement): `src/main/java/com/miniide/controllers/ChatController.java` in `buildToolSchemas()`
- Prompt ID vs tool ID mapping: prompt IDs are hyphenated (e.g., `file-locator`) but runtime tool IDs use underscores (e.g., `file_locator`).

## Tool Call Parsing + Validation (Strict JSON)
- Parser: `src/main/java/com/miniide/tools/ToolCallParser.java`
- Parse result wrapper: `src/main/java/com/miniide/tools/ToolCallParseResult.java`
- Tool call model: `src/main/java/com/miniide/tools/ToolCall.java`
- Validation: tool must be registered + args schema valid + nonce matches.
- Unknown top-level JSON fields are rejected.
- Multiple JSON objects in one message rejected.
- Non-JSON tool-call attempts trigger `tool_call_invalid_format` via `ChatController.looksLikeToolCallAttempt(...)`.

## Tool Call Nonce
- Generated server-side per tool-call turn.
- Injected into prompt as `TOOL_NONCE`.
- Enforced in JSON schema and tool-call parsing.

## Tool Call Required Detection
- Heuristic in `ChatController.shouldRequireToolCall()` by keyword/phrase matching.
- Phrases include: “use the file_locator tool first”, “Return ONLY the JSON tool call object”, “respond with exactly one JSON object”.
- Note: Heuristic is a known fragility; consider explicit flag later.
- JSON schema enforcement is applied on the first tool-call step only.

## LM Studio Provider Details
- Provider: `lmstudio` via `OpenAiCompatibleChatProvider`.
- Base URL: `http://<host>:1234`
- Endpoint: `/v1/chat/completions`
- Structured output: `response_format` with `json_schema` applied when tool call required.

## Prompt Injection (Tool Protocol)
- Injection happens in `ChatController.appendToolProtocol(...)`.
- Adds `Tool Call Protocol (STRICT JSON ONLY)` section.
- Adds `TOOL_NONCE`.
- Adds `TOOL_CALL_REQUIRED: true` when tool call required.

## Tool Call Response Format (Schema)
- Built in `ChatController.buildToolCallResponseFormat(...)`.
- Enforces `tool`, `args`, `nonce` required.
- Per-tool arg schema types enforced.
- `additionalProperties: false` at top-level and inside `args`.

## Tool Execution Service (Real Execution)
- `src/main/java/com/miniide/tools/ToolExecutionService.java`
- `file_locator` uses VFS tree and returns metadata.
- `outline_analyzer` reads outline file and returns structure summary + problem.
- `canon_checker` reads scene and canon file, compares quotes.
- `task_router` routes by role; output is structured text.
- `search_issues` filters IssueMemoryService and returns issue summaries.
- `file_locator` args enforced: `search_criteria` (string), `scan_mode` (FAST_SCAN/DEEP_SCAN), `max_results` (int), `include_globs` (bool), `dry_run` (bool).
- `outline_analyzer` args enforced: `outline_path` (string optional), `mode` (string optional), `dry_run` (bool).
- `canon_checker` args enforced: `scene_path` (string), `canon_paths` (string[]), `mode` (string optional), `dry_run` (bool).
- `task_router` args enforced: `user_request` (string), `dry_run` (bool).
- `search_issues` args enforced: `tags` (string[]), `assignedTo` (string), `status` (open|closed|all), `priority` (low|normal|high|urgent), `personalTags` (string[]), `personalAgent` (string), `excludePersonalTags` (string[]), `minInterestLevel` (int).
- `prose_analyzer` args enforced: `scene_path` (string required), `focus` (pacing|voice|rhythm|all), `dry_run` (bool).
- `consistency_checker` args enforced: `file_paths` (string[] required, max 10), `focus` (characters|terminology|events|general), `dry_run` (bool).
- `scene_draft_validator` args enforced: `scene_path` (string required), `outline_path` (string optional), `include_canon` (bool), `dry_run` (bool).
- Tool execution writes a receipt even when tool errors or unknown tool ids occur.

## Tool Result Injection
- Tool output appended into prompt with truncation and sha256 hash.
- Receipt id appended to prompt for evidence line reference.
- Output truncation also recorded in receipts with excerpt + sha256 + full_size.

## Receipts (Signature + Fields)
- Receipt signing: HMAC-SHA256 (secret stored at `.control-room/audit/secret.key`)
- Receipt payload includes:
  - `receipt_id`
  - `conference_id` or `task_id`
  - `turn_id`
  - `agent_id`
  - `tool_id`
  - `inputs`
  - `outputs` or `excerpt` + `sha256` if truncated
  - `file_refs` with excerpt hashes
  - `timestamp`
  - `signature` + `signature_alg`

## Receipt Storage
- Session receipts: `workspace/<project>/.control-room/audit/sessions/<sessionId>/tool_receipts.jsonl`
- Issue audit: `workspace/<project>/.control-room/audit/issues/<issueId>/`
- Session → issue link written on conference save.

## Audit API Endpoints
- `GET /api/audit/sessions/{id}/receipts`
- `GET /api/audit/sessions/{id}/tool-receipts`
- `POST /api/audit/sessions/{id}/link-issue`
- Controller: `src/main/java/com/miniide/controllers/AuditController.java`

## Telemetry (Rejection Reasons)
- Backend mapping: `src/main/java/com/miniide/TelemetryStore.java`
- Canonical tool-call failures:
  - `tool_call_invalid_format`
  - `tool_call_unknown_tool`
  - `tool_call_invalid_args`
  - `tool_call_multiple`
  - `tool_call_nonce_invalid`
  - `tool_call_output_limit`
- Frontend rejection mapping in `src/main/resources/public/app/agents.js`.
- Controller: `src/main/java/com/miniide/controllers/TelemetryController.java`

## Evidence Validation Rules (Conference UI)
- Enforced client-side in `src/main/resources/public/app/agents.js`.
- **Abstain check runs first**: if trimmed response equals ABSTAIN keyword, skip all evidence validation.
- Single Evidence line enforced with regex that accepts `**Evidence:**` or `__Evidence:__`.
- Role-specific source requirements.
- Quote verification against file content for content claims.
- Structural claims require line/section or outline entry.
- Tool claim requires receipt_id that exists in session receipts (receipt IDs come from Chief's phase 1).
- Chain-of-thought preface rejected.
- Plain-text tool syntax rejected.
- Receipt IDs are validated by fetching session receipt list from audit API.
- Note: Evidence validation applies only to phase 2 responses. Chief phase 1 is tool-call-only (no Evidence line expected).

## Evidence Source Types
- Content claim: quote required.
- Structural claim: line/section reference required.
- Absence claim: explicit scope required.

## Conference UI Flow (Two-Phase)
- Conference panel in `src/main/resources/public/app/agents.js`.
- Phase 1: Chief's prompt includes tool catalog + tool protocol + nonce. Chief runs tool calls; results buffered.
- Phase 2: All agents (including Chief on second pass) receive injected tool results + role framing + evidence rules. No tool catalog.
- Conference uses `conferenceId`, per-round `roundId`, and per-turn `turnId`.
- Agents are invoked serially within each phase. Phase 2 agents do not see each other's replies.
- Abstain detection: if trimmed response equals ABSTAIN keyword, intercept → update agent card to yellow "Abstained" → skip evidence validation → no chat message.
- Evidence validated client-side for non-abstaining phase 2 responses; rejections logged to telemetry.
- Round close: full transcript auto-saved as issue ("Conference {sessionId} — Turn {roundNumber}"). Receipts linked via `auditApi.linkSessionToIssue`.
- Round buffer and agent history wiped after close. User UI maintains continuous chat view.
- Dev Tools has a Tool Receipts panel for listing/fetching session receipts: `src/main/resources/public/app.js`.

## Task Execution Flow (Non-Conference)
- Task packets/receipts validated via `PromptJsonValidator`.
- Task execution endpoint: `POST /api/ai/task/execute`.
- Task receipts stored in audit issue folder.
- Task packet + receipt JSON validation returns STOP_HOOK on invalid output.
 - Validator location: `src/main/java/com/miniide/prompt/PromptJsonValidator.java`.

## Prompt Hygiene Rules (System-Wide)
- No fake tool usage in text.
- Tool calls must be JSON-only.
- Evidence line required for grounded responses.
- Receipt ID required when tools were used.

## Known Local Constraints
- Local LM Studio models often emit chain-of-thought unless JSON schema enforced.
- `JAVA_HOME` not set in sandbox, so `./gradlew test` may fail unless environment is set.
- JSON schema enforcement only applies when tool call is required by prompt heuristic.

## Full Storage Path Index
- Conference log: `private/conference_log.md`
- Audit base: `workspace/<project>/.control-room/audit/`
- Audit secret: `workspace/<project>/.control-room/audit/secret.key`
- Session receipts: `workspace/<project>/.control-room/audit/sessions/<sessionId>/tool_receipts.jsonl`
- Issue audit: `workspace/<project>/.control-room/audit/issues/<issueId>/`
- Telemetry totals + sessions: `workspace/<project>/.control-room/telemetry/`
- Issues: `workspace/<project>/.control-room/issues/issues.json`
- Credits: `workspace/<project>/.control-room/credits/credits.json`
- Notifications: `data/notifications.json`
- Memory: `workspace/<project>/.control-room/memory/`
- Agents: `workspace/<project>/.control-room/agents/agents.json`
- Agent endpoints: `workspace/<project>/.control-room/agents/agent-endpoints.json`
- Prompt registry (project): `workspace/<project>/.control-room/prompts/prompts.json`
- Canon index: `workspace/<project>/.control-room/canon/canon-index.md`
- Canon metadata: `workspace/<project>/.control-room/canon/canon-meta.json`
- Pipeline runs: `workspace/<project>/.control-room/runs/<runId>/run.json`
- Pipeline steps: `workspace/<project>/.control-room/runs/<runId>/steps.jsonl`
- Pipeline cache: `workspace/<project>/.control-room/runs/<runId>/cache.json`
- Bundled recipes: `src/main/resources/recipes/*.json`
- Project recipes: `workspace/<project>/.control-room/recipes/*.json`

## REST Endpoints (Relevant to Grounding + Tools)
- `POST /api/ai/chat` (conference + normal chat)
- `POST /api/ai/chief/route`
- `POST /api/ai/task/execute`
- `POST /api/audit/sessions/{id}/link-issue`
- `GET /api/audit/sessions/{id}/receipts`
- `GET /api/audit/sessions/{id}/tool-receipts`
- `POST /api/telemetry/conference`
- `GET /api/canon/index/status`
- `POST /api/canon/index`
- `POST /api/runs` (start pipeline run)
- `GET /api/runs` (list pipeline runs)
- `GET /api/runs/{id}` (consolidated polling)
- `GET /api/runs/{id}/steps` (full step log)
- `GET /api/runs/{id}/cache/{slot}` (cache slot drill-down)
- `POST /api/runs/{id}/cancel` (cancel running run)

## Prompt Tools (JSON-Only Example Formats)
- `file_locator` example:
  `{"tool":"file_locator","args":{"search_criteria":"Story/SCN-outline.md","scan_mode":"FAST_SCAN","max_results":12,"include_globs":false,"dry_run":false},"nonce":"<TOOL_NONCE>"}`
- `outline_analyzer` example:
  `{"tool":"outline_analyzer","args":{"outline_path":"Story/SCN-outline.md","mode":"structure","dry_run":false},"nonce":"<TOOL_NONCE>"}`
- `canon_checker` example:
  `{"tool":"canon_checker","args":{"scene_path":"Story/Scenes/scene.md","canon_paths":["Story/Compendium/Canon.md"],"mode":"strict","dry_run":false},"nonce":"<TOOL_NONCE>"}`
- `task_router` example:
  `{"tool":"task_router","args":{"user_request":"...","dry_run":false},"nonce":"<TOOL_NONCE>"}`
- `search_issues` example:
  `{"tool":"search_issues","args":{"tags":["tag"],"status":"open","priority":"normal"},"nonce":"<TOOL_NONCE>"}`
- `prose_analyzer` example:
  `{"tool":"prose_analyzer","args":{"scene_path":"Story/Scenes/SCN-scene-slug.md","focus":"all","dry_run":false},"nonce":"<TOOL_NONCE>"}`
- `consistency_checker` example:
  `{"tool":"consistency_checker","args":{"file_paths":["Story/Scenes/SCN-slug.md","Compendium/Characters/CHAR-name.md"],"focus":"general","dry_run":false},"nonce":"<TOOL_NONCE>"}`
- `scene_draft_validator` example:
  `{"tool":"scene_draft_validator","args":{"scene_path":"Story/Scenes/SCN-slug.md","include_canon":true,"dry_run":false},"nonce":"<TOOL_NONCE>"}`

## Dev Tools: Receipts Viewer
- UI: `src/main/resources/public/app.js` (Tool Receipts section)
- API helper: `src/main/resources/public/api.js` (`auditApi.listSessionReceipts`, `auditApi.getSessionReceiptsFile`)
 - Conference issue save also uses `auditApi.linkSessionToIssue`.

## Stop Hooks for Tool Failures
- Tool-call rejection returns `STOP_HOOK: tool_call_rejected` with reason code.
- UI uses stop hooks for surface-level warnings; do not ignore in acceptance tests.
