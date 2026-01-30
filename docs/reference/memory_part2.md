<a id="memory-part-2"></a>
# Memory Decay Model (Part 2)

This document codifies the decay model discussed after Phase 1. It is **normative** for behavior and must align with the existing architecture in `docs/reference/`.

## Core Principle
**Decay is access demotion only.**
- No memory is ever deleted or pruned.
- All representation levels (L1–L5) exist in storage at all times.
- Agent decay only changes which level an agent can access by default.
- L1 (Semantic Trace) is permanent and always accessible.

## Two-Layer Model (Recap)
- **Global memory = issue board**. Every issue is stored with all levels (L1–L5).
- **Per-agent memory = access layer**. Each agent has an `interestLevel` per issue that determines which representation is injected into context.

## Access Levels (Behavioral Meaning)
- **L5**: Full logs and highest-fidelity context.
- **L4**: Detailed summaries + key reasoning.
- **L3**: Essential summary for task execution.
- **L2**: High-level concept and resolution only.
- **L1**: Semantic trace (“I knew this once”). Always accessible.

## Re-learning Rules
- **Auto-escalation**: Any agent can freely re-learn up to **L3** when needed.
- **Chief of Staff gate**: L4/L5 access requires Chief of Staff approval.
- **Leech override**: If an issue is marked as a leech for an agent, even L3 re-learning requires Chief of Staff approval.

## Decay Drivers (Three Axes)
Decay is computed per-agent, per-issue, based on three forces:

### 1) Access (Primary Driver)
- Measured in **agent activations** (turns), not wall-clock time.
- Each agent has their own activation counter.
- If an agent doesn’t access an issue for N activations, their access level demotes.

### 2) Distance (Epoch/Milestone Pressure)
- Structural boundaries (chapter/act/draft/arc) apply **one-time artificial aging**.
- Distance does **not** delete or remove data; it only accelerates access demotion.
- Example: finishing Chapter 6 can apply a one-time age bump to Chapter 6–tagged issues.

### 3) Relevance Caps (Floor Constraints)
- Some issues must never demote below a floor level.
- Example floors:
  - **Canon / Worldbuilding / Character Arc**: floor **L3**.
  - **Resolved chapter-specific micro-decisions**: floor **L1**.
- Floors are enforced even during epoch jumps.

## Floor Computation (Single Source of Truth)
Floors are computed by a single resolver: `calculateFloor(agentRole, issueTags)`.

**Global floors (apply to all agents):**
- `canon`: L3
- `worldbuilding`: L3
- `character_core`: L3

**Role-specific floors (examples):**
- Planner: `plot_point`, `foreshadowing`, `timeline` → L3
- Writer: `plot_point`, `foreshadowing`, `character_state` → L3
- Continuity: `plot_point`, `timeline`, `character_state`, `canon` → L3
- Critic: `plot_point`, `foreshadowing` → L2
- Editor: `style_guide` → L3

**Default floor:** L1.

**Rule:** Floors always win. Even during epoch decay, memory cannot drop below floor.

## Leech Detection (Memory Hygiene)
**Not implemented in Phase 1.** This is Phase 2 work.

When implemented (conservative MVP):
- Requires a **human-generated contradiction signal** (Continuity/Critic/Editor issue).
- Requires **repeated access** (e.g., 3+ accesses within a short activation window).
- Requires **Chief of Staff confirmation** before marking as leech.

When an issue is marked as a leech for an agent:
- The agent is demoted to **L1** for that issue.
- Auto-escalation is disabled; **Chief of Staff approval required** even for L3.

## Transparency & Access Policy
- All agents can **search and see** any issue; there are no private threads.
- The memory system controls **how much** detail an agent can see, not **whether** it can see an issue at all.

## Epoch Behavior (Clarification)
Epochs apply a **one-time artificial age** to affected memories; they do **not** permanently change future decay rates.

Example:
- Chapter 6 completes.
- All issues tagged `chapter_6` receive artificial age: `EPOCH_BASE_AGE × 3.0`.
- This triggers an immediate decay check.
- Future decay proceeds with normal thresholds.

**Multiplier stacking rule:** use **max** multiplier when multiple epochs apply.

## High-Level Algorithm (Normative)
```
On each agent activation:
  increment agent.activationCount

  for each issueMemory for this agent:
    if interestLevel == 1: continue  // L1 is terminal

    if activationsSinceLastAccess exceeds threshold for current level:
       interestLevel = max(floor, interestLevel - 1)

On access:
  update lastAccessedAtActivation
  increment accessCount
  if interestLevel < 3 and not leech:
     interestLevel = 3
  if interestLevel < 3 and leech:
     require Chief approval for escalation

Access is recorded whenever the issue modal opens, when the memory agent selector changes, when the user refreshes memory, and when a comment is posted.

On epoch/milestone:
  for issues tagged with milestone scope:
    apply one-time artificial age (max multiplier wins)
    enforce relevance floors
```

## Configuration (Defaults TBD)
- **Thresholds** are activation-based (not time-based).
- **Epoch multipliers** apply as one-time age bumps at milestone boundaries.
- **Relevance floors** are driven by issue tags + agent role and can be tuned.

## Non-Goals
- No wall-clock pruning.
- No memory deletion.
- No hidden or private issues.

---

If this document conflicts with older time-based decay language, this document wins.

## See Also
- [Memory Decay Model (Part 2.1)](memory_part2_1.md)
