# Scene Pipeline & Cross-Role Autonomy

This document captures two closely related Control Room design decisions:

1. **Scene production as an explicit issue state machine**
2. **Forward-compatible cross-role autonomy with speculative execution and fallback**

The goal is to keep the system simple, auditable, and learnable today, while allowing future models to collapse multi-agent pipelines into fewer (or single) agents as their capabilities increase.

---

## 1. Scene Production as an Explicit State Machine

The existing scene workflow already *is* a state machine. Making that explicit simplifies:
- agent dispatch
- UI rendering
- verification
- learning from failures

### Scene Issue States

A scene-related issue progresses through well-defined states:

1. **SCENE_SELECTED**  
   *User → Chief of Staff*  
   The user selects or requests work on a scene (e.g. "Let’s do scene 6").

2. **PLAN_DRAFTED**  
   *Chief → Planner*  
   The Planner produces a scene plan (beats, structure, constraints).

3. **CANON_CHECKED_PLAN**  
   *Planner → Continuity → Planner*  
   The plan is checked against relevant canon packets and adjusted if needed.

4. **DRAFT_WRITTEN**  
   *Planner ⇄ Writer (loop)*  
   The scene text is produced. For weaker models this may involve chunking into beats; for stronger models this may be a single pass.

5. **DRAFT_CRITIQUED**  
   *Writer → Critic*  
   The draft is reviewed and structured critique is produced.

6. **REVISION_APPLIED**  
   *Critic ⇄ Editor (loop)*  
   Critique is applied until quality gates are met.

7. **CANON_CHECKED_FINAL**  
   *Editor → Continuity*  
   The final draft is checked against canon (targeted, entity-based).

8. **READY_FOR_USER_REVIEW**  
   *Chief of Staff*  
   The Chief prepares the result and rationale for the user.

9. **USER_ACCEPTED** or **USER_REQUESTED_CHANGES**  
   *User*  
   The user accepts the scene or requests further changes, potentially restarting parts of the chain.

### Transition Logging (Learning Substrate)

Every state transition logs:
- **Actor** (which agent acted)
- **Artifacts changed** (files, diffs, scene revisions)
- **Reasoning** (why this action was taken)
- **Cost** (tokens, tool calls, retries, time estimates)

This log is the primary learning and evaluation surface for agents.

Agents do not scan the entire issue board. They are **notified** only when an issue enters a state matching their role.

---

## 2. Cross-Role Autonomy (Forward Compatibility)

Roles (Planner, Writer, Critic, Editor, etc.) are **defaults, not prisons**.
Agents may attempt to take on additional roles *if they believe they can succeed*.

This enables future high-capability models to collapse the entire pipeline into fewer steps, or even a single agent.

### 2.1 Explicit Cross-Role Attempts

Cross-role execution must always be **explicit**, never silent.

When an agent attempts to handle multiple stages, the issue/task must include:

```yaml
attempted_roles: ["writer", "critic", "editor"]
requested_autonomy: SINGLE_AGENT_CHAIN
success_criteria:
  - passes critique quality gates
  - no continuity conflicts
  - meets style constraints (POV, tense, length)
```

This provides:
- auditability
- a clean failure mode
- deterministic fallback

If the attempt fails, the system reverts to the standard multi-agent pipeline.

---

### 2.2 Gated Capability Proof (Not Vibes)

Cross-role attempts are **earned**, not assumed.

A cross-role attempt is permitted only if:
- the agent is **not** in watched or assisted mode
- the agent has a **recent streak of verified successes**
- the agent has sufficient **budget headroom** (tier caps)

Success is counted only if the output passes **independent verification**:
- critique quality verification (structure, specificity)
- continuity / canon checks
- optional style or format validators

This prevents confident but unreliable models from ranking up.

---

### 2.3 Credit System: Incentivizing Useful Consolidation

Agents are rewarded for *useful* consolidation and penalized for waste.

#### Credit Gained
- Base credit for completing the task
- **Consolidation bonus** for completing multiple stages in one pass
- Bonus for **at-cap**, **unassisted**, **verified** success

#### Credit Reduced or Lost
- Failed quality gates requiring fallback to the normal chain
- Higher cost than the standard multi-agent pipeline
- Regressions (continuity conflicts, style violations)

As a result:
- Weak models quickly learn not to overreach
- Strong models naturally earn more autonomy

---

### 2.4 Speculative Execution with Fallback

This is the core safety mechanism.

1. Agent proposes: *"I can do writer + critic + editor."*
2. System allows **one consolidated attempt** within budget.
3. Run verifiers:
   - critique quality
   - edit quality (diff health)
   - continuity / canon
4. **If pass** → accept, award consolidation credit, update rank evidence
5. **If fail** → discard or mark as draft, then execute the normal chain

This preserves the full pipeline while allowing capable models to consume it over time.

---

## 3. Why This Works Long-Term

- Small models are supported through structure, chunking, and assistance.
- Large models are not artificially constrained.
- Capability growth is **measured**, **earned**, and **logged**.
- The system remains stable even as models improve dramatically.

Roles define *expectations*, not ceilings.
