# B‑Tiering System

A **self-calibrating, unbounded tier system** for agent capability limits that:

* **Adds tiers automatically** when models prove they can operate safely at the next cap.
* **Never becomes “unlimited”** (caps asymptote under global ceilings).
* **Promotes** only via repeated **at-cap, unassisted, verified** success.
* **Demotes** automatically on **verified failure**, with anti-flap safeguards.
* Makes the Planner’s job **mechanical** (rules + metrics), suitable for small fast models.

---

## 0. Goals and Non‑Goals

### Goals

1. **Tiers are earned** via observed performance.
2. Tiers represent **risk budgets** (caps), not “intelligence.”
3. System supports **infinite tier indices** (`T1+`) without ever granting unlimited authority.
4. Promotion and demotion are **objective**, auditable, and deterministic.

### Non‑Goals

* This system does **not** attempt to measure internal “reasoning.”
* This system does **not** guarantee perfect outputs; it guarantees **bounded blast radius** and **stable progress**.

---

## 1. Definitions

### 1.1 Tier

A **Tier** is an integer index `t ∈ {1,2,3,...}` that defines **caps** (limits) for the agent.

### 1.2 Capability Dimensions (Caps)

Each tier defines caps for these dimensions (start minimal; extend later):

* `max_safe_steps` — maximum steps the agent may execute/own in a single plan/task.
* `max_active_issues` — maximum concurrent issues the agent may hold/own.
* `max_output_tokens` — maximum allowed output payload for a single delivery.
* `max_tool_actions` — maximum tool calls/actions allowed within one issue (optional but recommended).
* `max_parallel_handoffs` — max handoffs the agent may initiate concurrently (optional).

> **Rule:** Caps are enforced by the system (Planner/Assistant), not “requested politely.”

### 1.3 Assistance (boolean)

A task is **assisted** if any of these occurred:

* Leader intervention (split / simplify / rewrite DoD)
* Escalation to a stronger model
* Pacer / Assisted Mode enabled for the agent
* Retry budget exceeded and human/leader guidance applied

Store `assisted: true/false` plus a short reason code.

### 1.4 Verification

Each task has a DoD + verification checklist. A task is **verified** only if checks pass within the retry budget.

### 1.5 Cap‑Run

A **cap‑run** is a task attempt where:

* Task requirements are **at-cap** for the agent’s current tier (see §3)
* `assisted == false`
* `verified == true`

Cap‑runs are the only events that count toward promotion.

---

## 2. Tier Generation (Unbounded tiers, bounded growth)

### 2.1 Caps are Computed, Not Hardcoded

Tiers are not stored as a finite list. They are computed from:

* a cap function (growth curve)
* global ceilings (hard limits)

This allows automatic tier expansion without introducing “T5 = unlimited.”

### 2.2 Global Ceilings (Hard Limits)

Set conservative, **Leader-adjustable** ceilings (changes logged):

* `STEPS_CEILING`
* `ISSUES_CEILING`
* `TOKENS_CEILING`
* `TOOLS_CEILING`

> Even if `t → ∞`, caps never exceed ceilings.

### 2.3 Example Cap Functions (Recommended)

Use curves that grow early, then taper as they approach ceilings:

* `max_safe_steps(t)   = min( round(2 + 3.0*(1.45^(t-1))), STEPS_CEILING )`
* `max_active_issues(t)= min( round(1 + 1.7*(1.35^(t-1))), ISSUES_CEILING )`
* `max_output_tokens(t)= min( round(600 * (1.60^(t-1))), TOKENS_CEILING )`
* `max_tool_actions(t) = min( round(2 + 1.4*(1.50^(t-1))), TOOLS_CEILING )`

Suggested initial ceilings (tune later):

* `STEPS_CEILING = 40`
* `ISSUES_CEILING = 14`
* `TOKENS_CEILING = 12000`
* `TOOLS_CEILING = 20`

---

## 3. “At‑Cap” Detection and Task Sizing

### 3.1 Task Requirement Estimates

Each task/issue should carry estimated requirements:

* `required_steps_estimate`
* `required_output_tokens_estimate`
* `required_active_issues_estimate` (optional)

These can be computed from task templates and/or Planner heuristics.

### 3.2 At‑Cap Rule

A task counts as **at‑cap** for tier `t` if **any** dimension is near the cap:

* `required_steps_estimate >= 0.8 * max_safe_steps(t)` OR
* `required_output_tokens_estimate >= 0.8 * max_output_tokens(t)` OR
* `required_active_issues_estimate >= 0.8 * max_active_issues(t)`

> This prevents gaming the system by completing many trivial tasks.

### 3.3 Splitting Rule

If a task’s estimated requirements exceed the agent’s effective caps, the Planner must:

1. Split into sub‑tasks until each fits caps, OR
2. Reassign to a higher‑tier agent, OR
3. Escalate to a stronger model (explicit).

---

## 4. Promotion (Automatic)

### 4.1 Per‑Agent Performance State

Maintain rolling stats per agent:

* `current_tier` (int)
* `cap_run_streak` (int) — consecutive at‑cap, unassisted, verified successes
* `assisted_rate_lastN`
* `verification_fail_rate_lastN`
* `watchlist_events_lastW`
* `cooldown_until` (timestamp; optional)

Recommended window sizes:

* `N = 20` (rate windows)
* `W = 20` (watchlist lookback)

### 4.2 Promotion Criteria

Promote from `t → t+1` when all are true:

1. `cap_run_streak >= X`
2. `assisted_rate_lastN <= A`
3. `verification_fail_rate_lastN <= F`
4. No watchlist events in last `W` tasks
5. `now >= cooldown_until` (if used)

Recommended defaults:

* `X = 5` (five cap‑runs in a row)
* `A = 0.15` (≤15% assisted)
* `F = 0.10` (≤10% verification failures)

### 4.3 Promotion Effects

On promotion:

* `current_tier += 1`
* `cap_run_streak = 0`
* optionally set `cooldown_until = now + 24h`
* log event: `tier_promotion(agent_id, from_tier, to_tier, evidence)`

---

## 5. Demotion (Automatic)

### 5.1 Failure Definition

A task is a failure if any of the following occur:

* Verification fails and cannot be resolved within retry budget
* Task ends as `BLOCKER` due to inability to complete
* Downstream rework indicates material DoD violation (handoff debt)
* Hard constraint breach (invalid schema, forbidden tool action)

### 5.2 Anti‑Flap Approach: Soft Clamp Then Hard Drop

To avoid oscillation on one bad outcome:

#### Soft Demotion (Cap Clamp)

On the first failure at tier `t`:

* Do not change `current_tier` yet.
* Apply a temporary penalty for next `K` tasks:

  * `effective_caps = caps(t) * 0.8`
* Suggested `K = 3`

#### Hard Demotion

Demote `t → t-1` if either:

* 2 failures within last `M = 10` tasks, OR
* 1 critical failure (schema/tool safety breach), OR
* Failure occurred on an **at-cap** task and the agent reported high confidence (optional)

On hard demotion:

* `current_tier = max(1, t-1)`
* `cap_run_streak = 0`
* optionally set `cooldown_until = now + 24h`
* log event: `tier_demotion(agent_id, from_tier, to_tier, evidence)`

---

## 6. Watchlist → Intervention → Escalation Integration

Tiering feeds the existing stability pipeline:

* Watchlist triggers remain objective (fail rate, reopen rate, blockers).
* When watchlisted:

  * apply/extend cap clamps
  * increase structure requirements (stricter schemas, smaller chunks)
  * consider endpoint/model escalation

The Planner should surface watchlist recommendations to the Leader when thresholds trigger.

---

## 7. Planner/Assistant Responsibilities (Mechanical)

The Planner is expected to operate this system without “cleverness”:

1. Estimate task requirements (steps/tokens/issues).
2. Select candidate agents whose role/skills meet minimums.
3. Compute caps for the agent’s tier (and effective caps if clamped).
4. Split/reassign/escalate if requirements exceed caps.
5. Emit issues with DoD + verification checklist.
6. Update stats from task results; apply promotion/demotion rules.

---

## 8. Minimal Data Structures

### 8.1 TierPolicy (global)

* cap function parameters
* ceilings
* promotion thresholds
* demotion thresholds
* retry budgets
* assistance definition

### 8.2 AgentPerf (per agent)

* `current_tier`
* rolling rates and streaks
* cooldown
* active clamps/penalties

### 8.3 TaskResult (per executed issue)

* `assisted` (bool) + reason
* `at_cap` (bool) + triggering dimension
* `verified` (bool)
* `retries_used`
* `downstream_rework_count`
* optional: `duration_ms`

---

## 9. Safety Valve: Absolute Blast Radius Limits

Even with tiers + ceilings, enforce global per-issue limits unless Leader approves:

* max new issues created per issue
* max files touched per issue
* max total output tokens per issue
* max tool actions per issue

This ensures no tier upgrade creates uncontrolled chaos.

---

## 10. Notes

* Start with conservative ceilings; raise only when system stability proves it.
* Over time, tiers become primarily about **risk budgets** rather than model intelligence.
* This system composes cleanly with credits (credits can be derived from verified work units and quality modifiers).
