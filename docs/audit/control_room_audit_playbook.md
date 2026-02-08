# Control Room Audit Playbook

## Purpose
Run an adversarial, evidence-driven audit of the repository to find doc/roadmap claims that are **missing, stubbed, reward-hacked, sloppy, or only partially implemented**.

Core premise:
> **The roadmap is a lie.** Assume everything is implemented lazily or incomplete. Assume the implementing model tried to reward hack.

Deliverable:
- A repo file named **`checklist.md`** containing a prioritized, testable audit checklist with **receipts**.

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

---

## Checklist file format
All findings go into **`checklist.md`**.

**Rule:** No item without **Evidence** + **Acceptance test**.

Template:
```md
# Audit Checklist

Legend:
- Status: [ ] open | [~] partial | [x] fixed
- Severity: P0 (must fix) / P1 (should fix) / P2 (nice)
- Implementation: I0/I1/I2/I3

---

## P0 — Breaks promised behavior / correctness / security

### [ ] A-001 — <short title>
**Claim (docs/UX/roadmap):** <what is promised>
**Implementation tier:** I0/I1/I2/I3
**Reality:** <what actually happens>
**Why it matters:** <user-visible impact / data loss / correctness>
**Evidence:**
- File: `path/to/file` (lines X–Y)
- Function: `fnName()`
- Commands: `rg "..."` / `git grep ...` (include output snippet if helpful)
- Repro steps: <minimal, deterministic steps>
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

Turn each into a checklist candidate and trace to code.

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

---

## Codex prompt: Adversarial Auditor (copy/paste)
Use this prompt when running the audit:

```text
You are the project auditor.

Premise: the roadmap is a lie. Assume features are implemented lazily, incompletely, or via reward hacking (UI stubs, hardcoded shortcuts, silent failures, missing persistence, missing edge cases). Your job is to find and call out all the bullshit with receipts.

Deliverable: write / update a repo file named checklist.md with a prioritized audit checklist.

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

1) Take each item in `checklist.md`.
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

