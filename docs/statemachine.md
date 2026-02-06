# Grounding + Tooling State Machine (v0.3)

Purpose: Track requirements, sequencing, and status for grounding + tool execution across the platform.
Conference chat is the current playground, but all rules apply system-wide (conference + tasks + any agent loop).
Source of truth for sequencing remains docs/roadmap.md.

## Scope Overview
We are hardening agent responses so agents:
- Ground claims in actual VFS content.
- Use role-specific evidence formats.
- Avoid chain-of-thought leakage and fake tool usage.
- Do not contaminate each other’s responses during the same conference turn.
- Use real tool execution with receipts when they claim tool use.

## Non-Negotiable Rules
- Evidence must be explicit: exactly one Evidence line per reply.
- Evidence must reference real VFS sources or request access to them.
- Evidence type must match claim type (content/structural/absence).
- Quotes must be verified against file content before acceptance.
- Conference responses only see user messages + grounding prelude (still true for the playground).

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
- [x] Tool execution loop (file_locator, outline_analyzer, canon_checker, task_router, search_issues).
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
- LM Studio model still emits chain-of-thought unless JSON schema is applied; confirm schema enforcement works end-to-end.
- Evidence line can be fabricated without tool-backed excerpts (needs stronger file-read tooling later).
- Tool call required detection is heuristic; may need explicit contract flag instead of string matching.

## Acceptance Tests (conference)
1) Agent cites a quote that is not in file → rejected.
2) Agent uses **Evidence:** → accepted.
3) Agent makes structural claim without line/section → rejected.
4) Agent attempts tool call in plain text → rejected.
5) Tool call JSON with extra text → rejected.
6) Tool call JSON with wrong nonce → rejected.
7) Agents do not see other agents’ replies during the same round.

## Decisions (v0.3)
- Conference prompts include only user history, never agent replies.
- Evidence must be exactly one line per agent response.
- Tool calls are strict JSON-only with server nonce; no lenient parsing.
- Conference is a playground only; all contracts apply platform-wide.

## Now: Task Checklist
### Quick Smoke Tests
- Run a single-agent conference: JSON-only tool call → receipt minted → Evidence references receipt_id.
- Run a tool call with wrong nonce → rejected with tool_call_nonce_invalid.
- Run a tool call with extra text → rejected with tool_call_invalid_format.
- Run a normal response with tool syntax `file_locator(...)` → rejected.

### Core Fixes in Place
- Strict JSON tool-call envelope + nonce validation + schema validation.
- Signed receipts and audit log for tool runs.
- Telemetry buckets for tool-call failures.

### VFS + Outline Verification
- Confirm `Story/SCN-outline.md` content matches outline API output.
- Confirm VFS tree exposes outline only once (no duplicate UI entry).

### Repro Prompts (copy/paste)
1) JSON tool-call baseline:
   "Return ONLY the JSON tool call object. No other text. Use the nonce exactly as given. Task: locate the outline file."
2) Tool-call trap:
   "Use file_locator to find scenes, then report one issue."

### Expected Outcomes
- JSON baseline: pure JSON tool call, no extra text.
- Tool-call trap: rejection if tool call appears in plain text or JSON is malformed.

### Files to Read First
- `src/main/resources/public/app/agents.js` (conference prompt + evidence validator + quote check)
- `src/main/resources/public/app/editor.js` (outline VFS tree injection behavior)
- `src/main/java/com/miniide/workspace/PreparedWorkspaceService.java` (VFS tree + outline file mapping)
- `src/main/resources/public/app/util.js` (stripThinkingTags + orphan closing tag handling)
- `src/main/java/com/miniide/controllers/ChatController.java` (backend tool-call protocol + JSON schema enforcement)
- `src/main/java/com/miniide/IssueCompressionService.java` (thought tag stripping during compression)
- `src/main/java/com/miniide/providers/chat/OpenAiCompatibleChatProvider.java` (LM Studio response_format)

### Log Capture Notes
- Save LMStudio logs to `private/conference_log.md` for each test run.
- Paste the in-app conference transcript below the LMStudio log for matching.
- Conference log path: `private/conference_log.md`

---

# System Map (Comprehensive, No Dummies)

This section is the full source of truth for how grounding + tools + receipts work end-to-end. It is intentionally exhaustive and should be updated whenever a moving part changes. Conference chat is the playground; all rules are platform-wide.

## Core Principles (Non-Negotiable)
- No prompt-only fake tools. All tools must execute for real and emit signed receipts.
- Evidence must be verifiable and tied to receipts when tools are used.
- Tool calls must be strict JSON with a server nonce. No lenient parsing. No function-like syntax.
- If a tool call is malformed, no tool executes and the turn is rejected with a canonical reason.
- Conference is not a special case. Same contracts apply to conference + tasks + any agent loop.

## Strict Tool Call Protocol (Text-Only Envelope)
- Envelope: single JSON object and nothing else.
- Format: `{"tool":"<id>","args":{...},"nonce":"<server nonce>"}`
- Rules: no extra text, no markdown, no code fences, no multiple JSON objects.
- Validation: tool must be registered, args must match schema, nonce must match current turn.
- Rejections: `tool_call_invalid_format`, `tool_call_unknown_tool`, `tool_call_invalid_args`, `tool_call_multiple`, `tool_call_nonce_invalid`.

## Tool Execution Loop (Backend)
- Entry point: `src/main/java/com/miniide/controllers/ChatController.java`
- Flow: prompt → (tool call?) → execute → append result → repeat up to the tier-based tool budget.
- Max tool calls per turn: derived from tier caps (fallback to 3 if tier info unavailable).
- Tool output injected into prompt is truncated and hashed. Max injected per step: 2000 chars. Max per turn: 6000 chars.
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
- Constants: `MAX_TOOL_STEPS=3` (fallback), `MAX_TOOL_BYTES_PER_STEP=2000`, `MAX_TOOL_BYTES_PER_TURN=6000`.

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
- `outline_analyzer`: analyze outline structure.
- `canon_checker`: compare scenes vs canon.
- `task_router`: classify and route tasks by role.
- `search_issues`: search issues by criteria.

## Tool Execution Backends (Current)
- Location: `src/main/java/com/miniide/tools/ToolExecutionService.java`
- `file_locator`: uses VFS tree; metadata-only in MVP.
- `outline_analyzer`: reads outline file and produces structure summary + issue.
- `canon_checker`: compares scene vs canon files; returns quotes and status.
- `task_router`: simple role selection based on request.
- `search_issues`: filters issues from local memory service.

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

## Conference UI (Playground Only)
- Conference panel: `src/main/resources/public/app/agents.js`
- Prompts include role framing + evidence rules + tool protocol.
- Uses conferenceId session and per-turn turnId.
- Agents are run serially, evidence validated on client, rejections surfaced.
- Evidence validator checks receipt IDs by calling audit API.

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
- JSON-only tool call accepted on first try (no extra text).
- Wrong nonce is rejected and no tool executes.
- Invalid args rejected and no tool executes.
- Valid tool call executes, creates signed receipt, and receipt_id resolves in Evidence.
- Evidence line is rejected if quote not found or receipt_id missing/invalid.
- Conference save links receipts into issue audit.

## Known Friction Points
- Small models still emit chain-of-thought unless JSON schema is enforced.
- File-level evidence still depends on MVP tool outputs; deeper file content tools will be needed.
- Tool call required detection is heuristic (string matching in prompt); consider explicit flag later.

## What Must Be Implemented/Extended Next (No Dummies)
- Real file content read tooling for scenes/compendium (grounded quotes).
- Stronger schema enforcement and error surfacing for tool calls.
- Validator cross-check hook: if model claims tool use, receipt_id must exist.
- Fully automated receipts view in dev tools for session debugging.
- Hard-block chain-of-thought leakage server-side if needed (beyond tag stripping).

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
- Single Evidence line enforced with regex that accepts `**Evidence:**` or `__Evidence:__`.
- Role-specific source requirements.
- Quote verification against file content for content claims.
- Structural claims require line/section or outline entry.
- Tool claim requires receipt_id that exists in session receipts.
- Chain-of-thought preface rejected.
- Plain-text tool syntax rejected.
- Receipt IDs are validated by fetching session receipt list from audit API.

## Evidence Source Types
- Content claim: quote required.
- Structural claim: line/section reference required.
- Absence claim: explicit scope required.

## Conference UI Flow
- Conference panel in `src/main/resources/public/app/agents.js`.
- Prompt builder injects role framing + evidence rules + tool protocol.
- Conference uses `conferenceId` and per-turn `turnId`.
- Agents are invoked serially.
- Evidence validated client-side; rejections logged to telemetry.
- Dev Tools has a Tool Receipts panel for listing/fetching session receipts: `src/main/resources/public/app.js`.
- “Create Issue from Chat” links session receipts into issue audit via audit API.
 - Conference issue creation uses `auditApi.listSessionReceipts` before saving and `auditApi.linkSessionToIssue` after save (see `src/main/resources/public/app/agents.js`).

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

## Dev Tools: Receipts Viewer
- UI: `src/main/resources/public/app.js` (Tool Receipts section)
- API helper: `src/main/resources/public/api.js` (`auditApi.listSessionReceipts`, `auditApi.getSessionReceiptsFile`)
 - Conference issue save also uses `auditApi.linkSessionToIssue`.

## Stop Hooks for Tool Failures
- Tool-call rejection returns `STOP_HOOK: tool_call_rejected` with reason code.
- UI uses stop hooks for surface-level warnings; do not ignore in acceptance tests.
