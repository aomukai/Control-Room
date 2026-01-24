# Workflow: Team Mode Execution

<a id="team-mode-workflow"></a>
## Team Mode Workflow

Team Mode requires exactly one **Assistant (Team Leader)**. The Assistant runs coordination,
task slicing, pacing, and system health while preserving creative authorship by the Doer.
The Writer/Doer owns the voice and final prose; the Assistant only defines constraints and slices.

## Phase 1: Initiation
1. **User Intent**: User provides a high-level goal (e.g., "Write the bridge scene").
2. **TL Strategy**: The **Assistant (Team Leader)** opens a new Scene Issue and assigns the **Planner**.

## Phase 2: Creative Roadmapping
3. **Outline Retrieval**: The **Planner** retrieves the Narrative Roadmap and drafts the scene plan.
4. **Lore Validation**: The **Continuity** agent checks the plan against the Lore Bank. If a contradiction is found, the Assistant routes it back to the Planner.
5. **Tag Update**: Once lore-safe, the Planner updates the Scene Tag to `Plan` (tags progress `Idea` -> `Plan` -> `Draft` -> `Polished`).

## Phase 3: Tactical Execution
6. **Task Slicing**: The **Assistant (Team Leader)** decomposes the `Plan` into microtasks based on the **Writer's** current Capability Profile.
7. **Paced Dispatch**: The Assistant dispatches tasks. If the Writer hits a stop hook, the Assistant activates **Assisted Mode** to stabilize.

## Phase 4: Review & Finalization
8. **Feedback Loop**: **Critics** and **Editors** review the output.
9. **Roadmap Close**: If the Definition of Done is met, the **Planner** updates the tag to `Polished` and the **Assistant** closes the issue.
