# Control Room Memory V1 Additions
Indexed in docs/agent_library.md

This doc captures two build-ready mechanisms:

A) **The Moderator’s Compression Hammer** (a pragmatic “dialectics” adaptation)
B) **Staged Memory Rollback on Demand** (full memory history DB + escalation policy)

---

<a id="librarian-compression-hammer"></a>
## A) The Moderator’s Compression Hammer (Dialectics → Product Feature)

### Goal
Keep the exocortex **lean, useful, and non-noisy** by applying **compression pressure** over time—without trusting hallucinated “reconstruction” as truth.

### Core idea
Treat every memory as a **family of representations** produced by transforms that compete on utility vs size.

- High reps preserve evidence (big, truthful)
- Low reps are cheap and routable (small, lossy)
- The system periodically **pushes memories downward** (compression), but always keeps a path back to evidence.

<a id="librarian-representation-levels"></a>
### Memory representation levels (V1)
You may implement all 5 or skip R2 initially (recommended below).

- **R5 — Raw Log**: original events/transcripts/build logs (stored as events/chunks)
- **R4 — Clean Log**: deduped/normalized R5 (optional)
- **R3 — Structured Summary**: sections + bullets + decisions/endpoints/features **with witness pointers**
- **R2 — Concept Packet**: tiny anchors + retrieval hints (optional in v1)
- **R1 — Semantic Trace**: ultra-compact “what matters” index

**Recommended V1 set:** R5 + R3 + R1 (plus R4 optional). Consider skipping R2 initially.

<a id="librarian-witness-pointers"></a>
### Witness pointers (V1: REQUIRED)
Witness pointers make “escalate to evidence” efficient for long threads.

**Store R5 as event/chunk records** (even if UI presents a single thread):

**R5Event**
- `id`
- `memoryItemId`
- `seq` (monotonic)
- `ts`
- `author` / `agent`
- `text`
- `meta` (optional)

**R3 bullets reference evidence**:
- `witnesses: [{ eventId | seqRange | chunkId, reason }]`

### “Compression hammer” policy
A periodic process (or manual moderator action) that:

1) **Detects candidates** for compression
   - old / cold memories (not accessed recently)
   - noisy memories (lots of duplication)
   - overlong summaries

2) **Applies transforms** to produce lower reps
   - R5 → R4 (normalize/dedup)
   - R5/R4 → R3 (structured summary + witnesses)
   - R3 → R1 (semantic trace)
   - (optional later) merge/split memories by topicKey

3) **Scores the result** (simple heuristics)
   - usefulness: was this memory later used successfully?
   - size penalty: smaller is better when usefulness equal
   - user pin/star overrides all

4) **Promotes winners**
   - sets a preferred “default injection” level (typically R3)

### Moderator controls (V1 UI)
- **Compress now** (per memory / per topic)
- **Pin** (never compress below R3)
- **Archive** (exclude from default retrieval)
- **Promote version** (choose which derived rep is “active”)

### Design principles
- Compression is **lossy but reversible** via stored higher reps.
- Don’t “prove” hallucinations with hashes—**escalate to evidence** instead.
- Keep transforms auditable (version links, timestamps, derivation kind).

---

<a id="librarian-rollback-on-demand"></a>
## B) Staged Memory Rollback on Demand (Full History DB + Escalation)

### Goal
Avoid asking agents to choose memory levels. Provide a deterministic **auto-level** policy that:

- injects the right amount of context by default
- escalates only when needed
- supports rollback and dispute resolution

### Data model (minimal)
**MemoryItem**
- `id`
- `agentId`
- `topicKey` (optional; used for clustering and issue-board alignment)
- `defaultLevel` (recommended: R3)
- `activeVersionId` (optional; points to “current best” rep)
- `pinnedMinLevel` (optional; e.g. never below R3)
- `state` (`active | archived | expired`)
- `lastAccessedAt`
- `projectEpoch` (or `projectStateHash` / `workspaceRevision`)

**Active-version lock (V1: REQUIRED)**
When a user explicitly promotes an older version (e.g., an R5) to active, apply a **temporary lock** so the compression hammer doesn’t immediately compress it back down while the user is working.

- `activeLockUntil` (timestamp; e.g. now + 30–120 minutes; extend on access)
- `activeLockReason` (manual-promote | dispute | audit)

Compression hammer must **skip** any MemoryItem with `activeLockUntil > now`.

**MemoryVersion**
- `id`
- `memoryItemId`
- `repLevel` (1..5)
- `content` (plain text)
- `derivedFromVersionId` (nullable)
- `derivationKind` (compress | normalize | summarize | trace | merge | split)
- `createdAt`
- `qualityScore` (optional)

> Store stages as **plain text** (R3/R1) plus **event/chunk records** for R5. Stages are **not directly accessible** except through policy-controlled endpoints.

<a id="librarian-retrieval-policy"></a>
### Retrieval policy (AUTO)
**Default injection:** R3

**Escalation ladder:** R3 → R4 → R5 (via witness slices)

Agents are never asked “which level do you want?”
They can only request **more evidence**.

#### When to escalate
Escalate one step if any of the following occurs:
- agent emits a structured request token: `NEED_MORE_CONTEXT`
- task class requires higher fidelity (audit/quote/dispute)
- contradiction/uncertainty signals detected
- user triggers reroll / marks output incorrect

<a id="librarian-reroll-behavior"></a>
#### Reroll behavior (V1: REQUIRED)
When a user triggers reroll, the system **automatically bumps** the level for that turn:
- first attempt: R3
- reroll #1: R4 (and/or R3 + evidence slices)
- reroll #2+: R5 slices via witnesses

Never reroll with identical context.

<a id="librarian-endpoint-contract"></a>
#### Endpoint contract
- `GET /api/memory/{id}?level=auto`
  - returns content at the current auto-chosen level (usually R3)
- `GET /api/memory/{id}?level=more`
  - returns the next level up (R3→R4, R4→R5)
- `GET /api/memory/{id}/evidence?witness=...`
  - returns only the referenced R5 events/chunks
- `GET /api/memory/{id}/versions`
  - returns metadata list (repLevel, timestamps, derivationKind)
- `PUT /api/memory/{id}/active/{versionId}`
  - rollback/promote a version as active (admin/moderator only)
  - sets/extends `activeLockUntil`

#### Implicit vs. explicit escalation (choose per UX)
When an agent requests more context (`NEED_MORE_CONTEXT`) there are two valid behaviors:

**Mode A — Auto re-run (fast default)**
- Server bumps level (R3→R4, then R5 slices) and **automatically replays** the same request.
- UI receives the final answer plus an `escalated:true` flag and the `usedLevel`.

**Mode B — UI-first (transparent default)**
- Server returns `409 NEED_MORE_CONTEXT` (or `428 PRECONDITION_REQUIRED`) with payload:
  - `recommendedNextLevel` (e.g. R4)
  - `witnessesAvailable` (bool)
  - `reason` (audit/dispute/uncertainty)
- UI shows “Fetch more evidence?” and then retries.

Recommendation: implement **Mode A** first (speed), but always surface in the UI that an escalation occurred.

<a id="librarian-witness-ui"></a>
### Witness UI (V1: HIGH-LEVERAGE)
Make the librarian tangible by letting users inspect receipts in-place:

- When an agent cites a witness pointer, render it as a clickable chip/link.
- On click/hover, fetch `/api/memory/{id}/evidence?witness=...` and show the raw fragment in a tooltip/popover.
- Display:
  - author/agent
  - timestamp
  - sequence number
  - raw text
  - optional “Open in thread” deep link (scroll to event)

This keeps long threads usable and turns justification into a first-class UX.

<a id="librarian-decay-archival"></a>
### Memory decay / archival lifecycle (V1)
Compression hammer should eventually include **expiry/archival** for memories no longer relevant.

**States:**
- `active`: included in default retrieval
- `archived`: excluded by default but searchable and eligible for escalation
- `expired`: hidden unless explicitly requested; never pruned or deleted

**Archival triggers (examples):**
- not accessed in N days **and** projectEpoch advanced
- superseded by a newer decision memory tagged `DECISION/SUPERSEDES`
- explicit user action: Archive

**Retention policy:**
- never prune or delete any memory representations
- pins only affect retrieval minimums, not retention

---

## How these two mechanisms work together

- The **Compression Hammer** creates/updates R3/R1 (and optionally R4) to keep memory compact.
- The **Rollback on Demand** policy ensures correctness by escalating to higher fidelity when needed.
- No “reconstruction correctness” problem: when precision matters, you **show more evidence**.

---

<a id="librarian-v1-checklist"></a>
## V1 Build Checklist
1) Implement MemoryItem + MemoryVersion tables (+ R5Event storage)
2) Add endpoints: `level=auto`, `level=more`, `evidence`, `versions`, `set active`
3) Implement default injection = R3
4) Implement escalation ladder R3→R4→R5 (witness slices)
5) Implement reroll auto-bump behavior
6) Add moderator UI: History + Promote/Rollback + Pin + Archive
7) Add decay lifecycle: active/archived/expired + retention rules


### Goal
Avoid asking agents to choose memory levels. Provide a deterministic **auto-level** policy that:

- injects the right amount of context by default
- escalates only when needed
- supports rollback and dispute resolution

### Data model (minimal)
**MemoryItem**
- `id`
- `agentId`
- `topicKey` (optional; used for clustering and issue-board alignment)
- `defaultLevel` (recommended: R3)
- `activeVersionId` (optional; points to “current best” rep)
- `pinnedMinLevel` (optional; e.g. never below R3)

**MemoryVersion**
- `id`
- `memoryItemId`
- `repLevel` (1..5)
- `content` (plain text)
- `derivedFromVersionId` (nullable)
- `derivationKind` (compress | normalize | summarize | trace | merge | split)
- `createdAt`
- `qualityScore` (optional)

> Store all stages as **plain text**. They are **not directly accessible** except through policy-controlled endpoints.

### Retrieval policy (AUTO)
**Default injection:** R3

**Escalation ladder:** R3 → R4 → R5

Agents are never asked “which level do you want?”
They can only request **more evidence**.

#### When to escalate
Escalate one step if any of the following occurs:
- agent emits a structured request token: `NEED_MORE_CONTEXT`
- task class requires higher fidelity (audit/quote/dispute)
- contradiction/uncertainty signals detected
- user triggers reroll / marks output incorrect

#### Endpoint contract
- `GET /api/memory/{id}?level=auto`
  - returns content at the current auto-chosen level (usually R3)
- `GET /api/memory/{id}?level=more`
  - returns the next level up (R3→R4, R4→R5)
- `GET /api/memory/{id}/versions`
  - returns metadata list (repLevel, timestamps, derivationKind)
- `PUT /api/memory/{id}/active/{versionId}`
  - rollback/promote a version as active (admin/moderator only)

### Reroll behavior
On reroll/dispute:
1) bump one level (R3→R4)
2) retry
3) if still wrong, bump to R5 (preferably *sliced excerpts* if available)

### Optional: Evidence pointers (high leverage)
Add to R3 (and/or store alongside it):
- **witness pointers** into R5/R4 (event IDs, offsets, or chunk IDs)

Then escalation can fetch **only the relevant slices** instead of dumping full logs.

### Storage & retention (practical)
- Keep R1/R3 indefinitely (small)
- Keep R4/R5 with retention unless pinned (or chunked blobs)
- Always preserve at least one high-rep ancestor for any active low-rep version

---

## How these two mechanisms work together

- The **Compression Hammer** creates/updates R3/R1 (and optionally R4) to keep memory compact.
- The **Rollback on Demand** policy ensures correctness by escalating to higher fidelity when needed.
- No “reconstruction correctness” problem: when precision matters, you **show more evidence**.

---

## V1 Build Checklist
1) Implement MemoryItem + MemoryVersion tables
2) Add endpoints: `level=auto`, `level=more`, `versions`, `set active`
3) Implement default injection = R3
4) Implement escalation ladder R3→R4→R5
5) Add moderator UI: History + Promote/Rollback + Pin
6) (Optional) Add witness pointers for slice-based escalation
