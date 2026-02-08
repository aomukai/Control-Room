# Control Room Audit Playbook

## Purpose
Run an adversarial, evidence-driven audit of the repository to find doc/roadmap claims that are **missing, stubbed, reward-hacked, sloppy, or only partially implemented**.

Core premise:
> **The roadmap is a lie.** Assume everything is implemented lazily or incomplete. Assume the implementing model tried to reward hack.

Deliverables:
- **`docs/audit/audit_ledger.md`**: the living audit ledger (PASS/FAIL/UNKNOWN) that records where each claim lives in docs and code/runtime.
- Optional: **`docs/audit/checklist.md`**: a prioritized fix list derived from the ledger's FAIL/UNKNOWN items (P0/P1/P2).

This playbook is designed to be repeatable (multiple passes), like editing a book.

---

## Definitions

### Implementation tiers
Use these tiers per claim/feature:
- **I0 — Not implemented:** docs/roadmap says it exists; code doesn’t.
- **I1 — Stub / partial:** skeleton exists, but core wiring/logic missing.
- **I2 — MVP:** works end-to-end for at least one happy path; rough edges OK.
- **I3 — Polished:** edge cases, errors, persistence, and UX are solid; docs match reality.

### Severity
- **P0:** correctness, data loss, security, or core flow broken / claim materially false.
- **P1:** partially implemented, missing persistence/validation/error handling, sloppy shortcuts.
- **P2:** polish, UX roughness, non-blocking improvements.

### Verification types
Each audited claim gets exactly one verification type:
- **CODE-MATCH:** doc claim is satisfied by code wiring you can point to (file + symbol/function + grepable anchors).
- **RUNTIME-VERIFIED:** can't be proven by code inspection alone; requires testing in the app.
- **UNKNOWN:** can't find the code and can't confidently verify in runtime yet (treat as P0 until proven otherwise).

### Receipts (evidence)
"Receipt" is contextual and must be concrete:
- **Code receipt:** doc anchor + code anchor (file + symbol/function) + a grepable string/identifier.
- **Runtime receipt:** "tested by user" + date + minimal repro steps + observed result (screenshots optional).

### Status markers (for ledger/checklist)
- `[ ]` open
- `[~]` partial (I1 / incomplete)
- `[x]` verified once (first pass)
- `[X]` confirmed (verified in 2+ separate passes, or verified once plus a second independent check such as restart/persistence)

---

## Audit ledger format
All audit results go into **`docs/audit/audit_ledger.md`**.

**Rule:** No item without:
- Doc anchor (what claim you audited)
- Verification type (CODE-MATCH / RUNTIME-VERIFIED / UNKNOWN)
- Evidence/receipt (code or runtime)
- If FAIL/UNKNOWN: an acceptance test

Template:
```md
# Audit Ledger

Legend:
- Status: [ ] open | [~] partial | [x] verified once | [X] confirmed
- Severity: P0 (must fix) / P1 (should fix) / P2 (nice)
- Implementation: I0/I1/I2/I3
- Verification: CODE-MATCH / RUNTIME-VERIFIED / UNKNOWN

---

## Core Flows (parent-first)
Audit parent systems first, then children:
- Prep -> Canon -> Agents -> Chat -> Conference -> Issues -> Persistence/Reload

## Design Docs (doc-by-doc)
One section per file in `docs/reference/`.

---

## P0 — Breaks promised behavior / correctness / security (derived checklist)

### [ ] A-001 — <short title>
**Claim (docs/UX/roadmap):** <what is promised>
**Implementation tier:** I0/I1/I2/I3
**Verification:** CODE-MATCH / RUNTIME-VERIFIED / UNKNOWN
**Reality:** <what actually happens>
**Why it matters:** <user-visible impact / data loss / correctness>
**Evidence:**
- Doc anchor: `docs/reference/foo.md#anchor` (or heading)
- Code anchor: `path/to/file` + `fnName()` + grepable string(s)
- Or runtime receipt: tested by user (YYYY-MM-DD) + steps + observed result
**Fix suggestion:** <smallest fix that makes the claim true>
**Acceptance test:** <how to prove it’s fixed>

---

## P1 — Sloppy / incomplete / reward-hacky

### [ ] A-0xx — ...
...

## P2 — Polish / QoL

### [ ] A-0xx — ...
...
```

---

## Audit method (mechanical, high-signal)

### 1) Start from claims, not code
Extract claims from:
- Roadmap
- Docs
- UI labels/tooltips/settings descriptions
- README/promised workflows

Turn each into a ledger entry and trace to code/runtime.

### 2) Trace end-to-end wiring
For each claim, verify the pipeline:
- UI action → handler → state update → persistence → reload → provider call
- If any link is missing, it’s **I0/I1**.

### 3) Hunt reward hacks
Look for:
- TODO/FIXME stubs
- dead code / uncalled functions
- placeholder text / mocked data
- feature flags that default to “on” or “success”
- broad `try/catch` that eats errors
- silent fallbacks that mask failure
- “looks implemented” UI that doesn’t do the thing

### 4) Prove mismatch with receipts
Use mechanical proof:
- `rg "<UI string>"` to find wiring
- follow references until provider call
- show where settings are (or aren’t) applied
- show persistence keys and reload paths
- if no match found: record searches + “no matches” as evidence

### 5) Prioritize ruthlessly
- Fix P0 first.
- Don’t let P2 polish derail P0/P1.

### 6) Re-check policy (avoid retesting forever)
- Default: aim for **2 checks** per PASS claim before marking `[X]`.
- For anything involving persistence/reload/concurrency: aim for **3 checks**:
  1) happy path
  2) restart/refresh path
  3) one negative/edge case
- Don't keep re-testing beyond `[X]` unless the relevant code moved or the claim changed.

---

## Codex prompt: Adversarial Auditor (copy/paste)
Use this prompt when running the audit:

```text
You are the project auditor.

Premise: the roadmap is a lie. Assume features are implemented lazily, incompletely, or via reward hacking (UI stubs, hardcoded shortcuts, silent failures, missing persistence, missing edge cases). Your job is to find and call out all the bullshit with receipts.

Deliverable: write / update `docs/audit/audit_ledger.md` (and optionally `docs/audit/checklist.md`).

Rules:
1) Every checklist item MUST include:
   - a clear claim (from docs/UI/roadmap)
   - what the code actually does
   - why it is incomplete/sloppy
   - evidence (file paths + function names + line ranges; include grep results when helpful)
   - an acceptance test (how to prove it's fixed)
2) Prioritize findings:
   - P0: correctness/data loss/security, or core flow broken
   - P1: partial implementations, missing persistence/validation/error handling
   - P2: polish, UX roughness
3) Be adversarial and skeptical. Treat placeholders, TODOs, "later", fallbacks, broad try/catch, and mocked data as failures unless explicitly documented as MVP.
4) Prefer smallest fixes that make the claim true; avoid big refactors unless necessary.
5) Audit method:
   - Start with docs/roadmap -> extract claims.
   - Map each claim to implementation locations.
   - Validate: is it wired end-to-end? persisted? error-handled? tested?
   - Look for stubs, dead code, incomplete UI handlers, missing state transitions.
6) If you cannot locate implementation for a claim, create a P0 item "Unimplemented claim" with evidence (search commands + no matches).
```

---

## Second pass: Fix-and-Prove workflow
After fixes land, run a verifier pass:

1) Take each FAIL/UNKNOWN item in `docs/audit/checklist.md` (or derive from `docs/audit/audit_ledger.md`).
2) Implement the **acceptance test**:
   - unit/integration tests, OR
   - scripted CLI run, OR
   - reproducible UI steps with logs/screenshots.
3) Mark as **[x] fixed** only when evidence is produced.

Repeat passes until core flows are I2/I3.

---

## Optional: Add a tiny AUDIT_RULES.md (recommended)
A short file that defines:
- tiers (I0–I3)
- severities (P0–P2)
- what counts as MVP vs stub

This prevents arguments about standards during repeated audit passes.
