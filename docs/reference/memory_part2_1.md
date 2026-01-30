<a id="memory-part-2-1"></a>
# Memory Decay Model (Part 2.1)

This addendum defines **Phase 2** conservative leech detection and **manual Wiedervorlage**. It is intentionally strict to avoid false positives and preserve trust.

## Scope
- **Phase 1** remains the deterministic core (access decay, floors, L3 auto‑relearn, L4/L5 gate, epoch one‑time bump).
- **Phase 2** introduces leech detection with **Chief confirmation** and manual Wiedervorlage.

## Leech Detection (Conservative MVP)

### Goal
Prevent agents from repeatedly accessing information they cannot apply correctly, without suppressing useful knowledge.

### Trigger Conditions (ALL must be met)
A potential leech is **flagged for Chief review** only when:
1) **Human‑generated contradiction signal exists**
   - Continuity/Critic/Editor creates an issue that flags a contradiction or misapplication.
   - The signal explicitly references the suspect issue.

2) **Repeated access pattern exists**
   - The agent accessed the suspect issue **3+ times** within a short activation window.
   - The window is activation‑based (not time‑based).

3) **Chief of Staff confirmation required**
   - The system does **not** auto‑mark a leech.
   - Chief reviews evidence and makes the decision.

### Non‑Triggers (Do NOT flag)
- Unused access alone (agent read but didn’t mention it).
- Search repetition alone.
- Single misapplication (one error ≠ pattern).

### Chief Actions (Manual Decision)
When alerted to a potential leech, Chief can:
1) **Defer access (Wiedervorlage)**
2) **Mark as leech**
3) **Dismiss alert**

If marked as leech:
- Agent is demoted to **L1** for that issue.
- **Auto‑relearn to L3 is disabled** for this agent/issue.
- **Chief approval required** even for L3.

---

## Wiedervorlage (Manual + Deterministic)

### Intent
Defer access until it becomes relevant, without erasing memory or blocking future use.

### Allowed Triggers (Deterministic only)
1) `scene_reached(N)`
2) `milestone(NAME)`
3) `tag_appeared(TAG)`

### Not Supported (for now)
- Wall‑clock timers
- Heuristic timing (“when it feels relevant”)
- Agent tier changes

### Data Shape (Reference)
```json
{
  "action": "defer_access",
  "agent": "writer",
  "issue": 43,
  "trigger": {
    "type": "scene_reached",
    "value": 38
  },
  "escalate_to": 3,
  "notify": true,
  "message": "Eli dies in scene 40. This context is now relevant.",
  "reason": "Premature access detected in scenes 17/22/31"
}
```

### Behavior
- On deferral: agent access is set to **L1** (semantic trace only).
- On trigger: access auto‑escalates to **L3**, optionally notifies agent.
- L4/L5 still require Chief approval.

---

## Evidence Package for Chief Review (Reference)
When a potential leech is flagged, the system should provide:
- Access history for the agent/issue pair
- Contradiction issue(s) and excerpts
- Agent role + tier snapshot
- Current task context

---

## Implementation Notes
- This phase is intentionally conservative; it prioritizes **trust** and **explainability** over automation.
- Any automation beyond these rules must be explicitly approved and documented.

## See Also
- [Memory Decay Model (Part 2)](memory_part2.md)
