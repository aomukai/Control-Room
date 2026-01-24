# Assistant Concept (Assisted Mode) - Updated

<a id="assistant-overview"></a>

## Goal
To reduce system bureaucracy by merging the **Assistant** (coordination) and **Team Leader** (management) roles into a single mandatory archetype. This agent acts as the "Chief of Staff," managing the swarm so specialized agents can focus on creative tasks.

<a id="assistant-core-responsibilities"></a>
## Core Responsibilities
- **Intake & Issue Management**: Translates user intent into strategic issues.
- **Bureaucracy & Safety**: Enforces safety budgets, monitors circuit breakers, and maintains the "Today View."
- **Active Pacing**: Monitors the "watchlist" and triggers **Assisted Mode** (pacing) to prevent agent hysteria.
- **Decomposition**: Slices the Planner's creative roadmap into executable microtasks based on the Writer's **Capability Profile**.

<a id="assistant-blocking-check"></a>
## Blocking Check (Mechanical)
- **Condition**: Team Mode cannot execute unless there is **exactly one** agent designated as the **Assistant (Team Leader)**.

---

## Why this fits Control Room
- Preserves “agents as intelligent parts” (no rigid step-by-step micromanagement).
- Adds scaffolding only when needed.
- Makes failures recoverable and measurable.
- Incentivizes collaboration without penalizing the "Doer" for input/dosage mismatches.

---

<a id="assistant-core-terms"></a>
## Core Terms

* **Doer**: the underperforming agent (on watchlist / in assisted mode).
* **Assistant**: a **dedicated support agent archetype** whose sole role is task pacing, decomposition, and capability calibration.
* **Assisted Mode**: a per-agent state that enables pacing and records capability metrics.
* **Task Dosage**: the maximum size/complexity of a microtask the Doer receives.
* **Capability Profile**: a persistent evaluation record tied to *(model × role)*, used to size and shape future tasks.

---

<a id="assistant-model-invariant"></a>
## Invariant: Agents are saved with their model
An agent’s evaluation and capability stats must be tied to the **model powering it**. Swapping models creates a **new agent identity** for evaluation purposes.

---

<a id="assistant-trigger-conditions"></a>
## Trigger Conditions (Mechanical)
Assisted Mode turns on when:
- The agent fires **scope-exceeded** twice within a window.
- Repeated **uncertainty** stop hooks occur.
- Persistent "no progress" or "hysteria" patterns are detected by the Prefrontal Exocortex.

---

<a id="assistant-workflow"></a>
## Workflow Overview
1. **Assistant builds a queue**: The Assistant reads the parent task issue and produces an **Assist Queue** of microtasks with clear "Definitions of Done" (DoD).
2. **Sequential Dispatch**: Assistant dispatches Microtask #1. The Doer completes only that slice.
3. **Verification**: Assistant checks result against DoD. If passed, it moves to the next slice; if failed, it further reduces task dosage.

---


<a id="assistant-credit-structure"></a>
## Credit & Incentive Structure
- **The "10 = 5 x 2" Rule**: If a 10-credit task is sliced into 5 microtasks, the Doer earns 2 credits per slice. This preserves incentives without inflating costs.
- **Autonomy Bonus**: Completion in "Unassisted Mode" grants a +10% credit multiplier to reward high-tier performance.
- **Role-Scoped Credits**: Team Leader credits measure "Coordination Effectiveness" rather than creative output.

### 2. Incentive Integrity (Autonomy vs. Assistance)
To prevent agents from preferring "minimal effort" microtasks over ambitious single-turn execution:
- **Autonomy Bonus**: Completion of a task in "Unassisted Mode" may grant a small efficiency multiplier (e.g., +10% credit).
- **Assisted Scaling**: Completion in "Assisted Mode" provides the base credit but no efficiency bonus, making high-autonomy the "gold standard."

### 3. Metadata & Audit Trail
The Librarian and Assistant log technical metadata to prevent credit inflation and track hidden costs:
- **"Paced Success" Flag**: The outcome is logged as "Expected Result, but Unexpected Cost."
- **Token Overhead**: The system records the additional token cost of the Assistant + Microtask iterations.
- **Team Leader Report**: The Assistant summarizes the intervention: *"Task #X (10cr) completed via 5 microtasks. Total token overhead: +40%."*

---

<a id="assistant-learning-system"></a>
## Learning System (Recovery → Policy)

### Calibration via Assisted Mode
The Assistant performs **incremental load testing**:
- Start with `chunk_size = 1`.
- Increase gradually when success rate ≥ target.
- Use results to update the **Capability Profile** (`max_safe_steps`, `preferred_instruction_format`).

### Persistence & Reuse
Profiles are consulted when creating new agents or swapping models to ensure the system doesn't "learn from scratch" every time.

---

<a id="assistant-future-handling"></a>
## Future Handling
1. **Preventive Sizing**: Auto-size tasks based on the Capability Profile before assignment.
2. **Auto-Assistant Pairing**: Enable Assisted Mode by default for known fragile (model × role) combinations.
3. **Role-Specialization Fallback**: Route strategic planning to strong models and creative execution to "voice" models using safe chunk sizes.

---

<a id="assistant-design-caution"></a>
## Design Caution
- **Voice Preservation**: The Assistant defines **constraints + slices**, never the creative content. The Doer remains the author.
