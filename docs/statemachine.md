# Prompt Hardening State Machine (v0.1)

Purpose: Track requirements, sequencing, and status for the prompt hardening + receipts effort.
Source of truth for sequencing remains docs/roadmap.md.

## Scope Overview
We are moving from free-text agent coordination to deterministic, validated task packets and receipts.
Primary goals:
- Deterministic routing of user intent into scoped tasks.
- JSON-only contracts with strict validation and retry rules.
- Receipt-based audit trail linked to issues.
- One reference playbook: Write Scene from Outline.

## Task Boundary Rule (Non-Negotiable)
If an agent receives a message tagged as a task packet, it MUST respond with a valid receipt JSON and nothing else.
If the message is not tagged as a task packet, normal conversational behavior applies.

## Requirements (v0.1)
A) Task Packet Contract
- JSON-only, validated against v0.1 schema.
- Required fields: packet_id, parent_issue_id/parent_packet_id, intent, target, scope, inputs,
  constraints, output_contract, handoff, timestamp/requested_by.
- Clarification questionnaire is a task packet variant (intent=clarify) with choices.

B) Receipt Contract
- JSON-only, validated against v0.1 schema.
- Required fields: receipt_id, packet_id, issue_id, actor (agent+model+decoding params used),
  started_at/finished_at, inputs_used, outputs_produced, reasoning_summary (no chain-of-thought),
  decisions, checks_performed, assumptions, risks, next_recommended_action, STOP_HOOK flag, citations.

C) Guardrails
- Reject invalid JSON packets/receipts.
- Retry invalid outputs (max 2) with stricter prompts, then STOP_HOOK.
- Enforce no hallucinated file paths: any output path must be in expected_artifacts or trigger STOP_HOOK.
- Idempotency where possible (no duplicate artifacts on re-run).

D) Audit Trail
- Receipts persisted in project storage and linked to issues/packets.
- UI affordance to open attached report from issue.

E) Playbook (Write Scene from Outline)
- Deterministic state machine:
  Chief -> Planner (plan_scene) -> Continuity (continuity_check) -> Writer loop (write_beat)
  -> Critic (critique_scene) -> Editor (edit_scene) -> Continuity final -> Chief.
- Stop/clarify rules at each gate.

## Storage Decisions
- Preferred: per-project `.control-room/audit/` directory containing:
  - `issues/<issue_id>/<timestamp>__packet__<packet_id>.json`
  - `issues/<issue_id>/<timestamp>__receipt__<packet_id>.json`
  - `issues/<issue_id>/<timestamp>__report__<packet_id>.md` (optional, for long reports)
  - optional `index.json` for quick lookup
- Receipt JSON must include a mandatory `report_excerpt` (2-3 sentence summary).
- Issue comments include the excerpt plus a file pointer when a full report exists.
- UI loads the full report on demand via a backend API endpoint.

## Status (aligns with roadmap)
- [x] PH-0 Task packet + receipt schemas (v0.1)
- [x] PH-1 Receipt storage + audit trail (backend loader)
- [x] PH-2 Chief router (v0.1): user -> packet or clarify
- [ ] PH-3 Agent execution guardrails (validation, retry, path checks)
- [ ] PH-4 UI: attached report view in issue modal
- [ ] PH-5 Playbook: Write Scene from Outline

## Acceptance Tests (per phase)
PH-0: Given a hardcoded sample input, validator accepts a well-formed packet and receipt; rejects 3 known-bad variants (missing required field, invalid intent enum, bad output_contract). (Done)
PH-1: A receipt JSON is written to `.control-room/audit/issues/<issue_id>/`; the backend loader retrieves it by issue_id; the file is present on disk with correct timestamp-sorted naming. (Done)
PH-2: Input “let’s do scene 3” → Chief emits a clarification questionnaire (valid packet, intent=clarify). User selects an option → Chief emits a task packet for the correct next agent. Both are valid JSON per schema.
PH-3: Agent returns invalid JSON → retry fires → if still invalid after 2x, STOP_HOOK receipt is emitted to Chief. Agent references a file path not in expected_artifacts → STOP_HOOK. Both traceable in audit trail.
PH-4: Issue modal loads receipt summary inline (from report_excerpt). “Open attached report” button triggers file load from audit storage. Works with both short (inline-only) and long (external file) receipts.
PH-5: Full “let’s do scene 3” → plan → write → critique → edit pipeline runs end-to-end. Every step has a packet and receipt in audit trail. Audit trail renderer lists them in order with actor, decision summary, artifacts. No step proceeds on invalid packet/receipt.

## Open Questions
- Should JSON-only be enforced for all agent outputs immediately or only for task-driven workflows?

## Decisions (v0.1)
- Canonical scene resolution default: outline order (assumption logged in PH-2).
- 1:1 chat trigger rule: clarification >3 questions or scope conflict detected.
