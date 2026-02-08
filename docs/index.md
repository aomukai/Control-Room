> This file is a navigation index only.
> Implementation status and sequencing live exclusively in roadmap.md.
> If there is any conflict, roadmap.md is authoritative.
> Use this file to locate the correct document and anchor.
> Do not infer implementation state from this index.

# Master Index (Agent, Memory, and Safety Systems)

This document is the router for agent design, memory systems, and anti-spiral safeguards.
It summarizes key concepts and links into stable anchors in the source docs.

## Memory & Exocortex

### Memory Bank (issue-based institutional memory)
- Doc type: Mixed
- Purpose: Centralize decisions, collaboration, and recall via issues and comments.
- Triggers/Interfaces: Issue REST API, issue modal, notification events.
- Implementation prerequisites:
- prereq: Issue REST API
- prereq: Notification store events
- Primary refs: [Architecture summary](cr_memory.md#memory-architecture), [Issue structure](cr_memory.md#memory-issue-structure)
- Related refs: [Agent workflows](cr_memory.md#memory-agent-workflows), [Issue board panel](cr_memory.md#memory-issue-board-panel), [Shared issue modal](cr_workbench.md#workbench-shared-issue-modal)

### Interest Levels & Compression (memory decay)
- Doc type: Spec
- Purpose: Keep memory useful by decaying detail and compressing over time.
- Triggers/Interfaces: Interest levels, compression rules, pruning/trace generation.
- Implementation prerequisites:
- prereq: Issue storage format
- prereq: Interest level tracking
- Primary refs: [Interest levels](cr_memory.md#memory-interest-levels), [Design alignment](cr_memory.md#design-alignment-global-representations-vs-per-agent-access), [Compression logic](cr_memory.md#memory-compression-logic)
- Related refs: [Leech detection](cr_memory.md#memory-leech-detection), [Semantic trace](cr_memory.md#memory-semantic-trace), [Memory sanity checks](cr_prefrontal_exocortex.md#exocortex-memory-sanity)

### Memory Decay Model (Part 2 + 2.1)
- Doc type: Spec
- Purpose: Define access demotion rules, epoch behavior, floors, and conservative leech/Wiedervorlage.
- Triggers/Interfaces: Agent activations, epoch triggers, Chief of Staff gating.
- Primary refs: [Memory Decay Model Part 2](reference/memory_part2.md#memory-part-2), [Memory Decay Model Part 2.1](reference/memory_part2_1.md#memory-part-2-1)

### Librarian Compression Hammer (R5/R3/R1 with witnesses)
- Doc type: Spec
- Purpose: Maintain multiple memory representations and preserve evidence trails.
- Triggers/Interfaces: Compression policy and moderator controls.
- Implementation prerequisites:
- prereq: R5 event storage
- prereq: Witness pointer schema
- Primary refs: [Compression hammer](cr_librarian_extension.md#librarian-compression-hammer), [Representation levels](cr_librarian_extension.md#librarian-representation-levels)
- Related refs: [Witness pointers](cr_librarian_extension.md#librarian-witness-pointers), [V1 checklist](cr_librarian_extension.md#librarian-v1-checklist), [Compression logic](cr_memory.md#memory-compression-logic)

### Rollback on Demand (auto-level escalation)
- Doc type: Spec
- Purpose: Escalate context fidelity only when needed and support dispute rollback.
- Triggers/Interfaces: Auto retrieval policy, reroll bumps, evidence endpoints.
- Implementation prerequisites:
- prereq: Memory versioning model
- prereq: Escalation endpoints
- Primary refs: [Rollback on demand](cr_librarian_extension.md#librarian-rollback-on-demand), [Retrieval policy](cr_librarian_extension.md#librarian-retrieval-policy)
- Related refs: [Reroll behavior](cr_librarian_extension.md#librarian-reroll-behavior), [Endpoint contract](cr_librarian_extension.md#librarian-endpoint-contract), [Witness UI](cr_librarian_extension.md#librarian-witness-ui)

## Agents & Roles

### Dynamic roster and registry
- Doc type: Mixed
- Purpose: Replace fixed agents with configurable, role-based roster entries.
- Triggers/Interfaces: Agent registry and role definitions.
- Implementation prerequisites:
- prereq: Agent registry persistence
- prereq: Agents API
- Primary refs: [Purpose & philosophy](cr_agents.md#agent-purpose), [Agent registry](cr_agents.md#agent-registry)
- Related refs: [Agent interface](cr_agents.md#agent-interface), [Team dynamics](cr_agents.md#agent-team-dynamics), [Storage & API](cr_agents.md#agent-storage-api)

### Agent interface and capabilities
- Doc type: Mixed
- Purpose: Define identity, tools, personality, and memory wiring for each agent.
- Triggers/Interfaces: Agent interface, tools, and capability schemas.
- Implementation prerequisites:
- prereq: Agent schema fields
- prereq: Tool capability model
- Primary refs: [Agent interface](cr_agents.md#agent-interface), [Agent capabilities](cr_agents.md#agent-capabilities)
- Related refs: [Endpoint configuration](cr_agents.md#agent-endpoints), [Memory integration](cr_agents.md#agent-memory-integration)

### Agent workflow and collaboration
- Doc type: Mixed
- Purpose: Describe how agents pick work, prompt each other, and activate.
- Triggers/Interfaces: Issues-as-inbox, @mentions, activation triggers.
- Implementation prerequisites:
- prereq: Issue system
- prereq: @mention notifications
- Primary refs: [Agent workflow](cr_agents.md#agent-workflow), [Agent workflows in memory](cr_memory.md#memory-agent-workflows)
- Related refs: [Notifications as entry points](cr_memory.md#memory-notifications), [Shared issue modal](cr_workbench.md#workbench-shared-issue-modal)

### Assistant and Assisted Mode (pacing + dosage)
- Doc type: Spec
- Purpose: Define the mandatory Team Leader, assisted mode triggers, and task dosage control.
- Triggers/Interfaces: Team Mode gating, assisted mode, capability profiles, credit slicing.
- Implementation prerequisites:
- prereq: Assistant (Team Leader) role
- prereq: Stop hooks + circuit breaker signals
- Primary refs: [Assistant concept](cr_assistant_assisted_mode.md), [Team Leader requirement](cr_prefrontal_exocortex.md#exocortex-assistant-requirement)
- Related refs: [Team Mode workflow](cr_team_mode_workflow.md#team-mode-workflow), [Credits system](cr_prefrontal_exocortex.md#exocortex-credits)

### Role archetypes, skills, and tiers
- Doc type: Spec
- Purpose: Canonical role definitions, skill sets, and tier semantics.
- Triggers/Interfaces: Role definitions, capability profiles, tiered evaluation.
- Implementation prerequisites:
- prereq: Role registry + skill taxonomy
- Primary refs: [Roles/skills/tiers addendum](cr_exocortex_roles_skills_tiers.md), [Prompt tools registry](cr_exocortex_roles_skills_tiers.md#exocortex-prompt-tools)
- Related refs: [Agent interface](cr_agents.md#agent-interface), [Assistant concept](cr_assistant_assisted_mode.md)

### Agent role contracts (archetype responsibilities & abstain boundaries)
- Doc type: Reference
- Purpose: Define behavioral contracts for each archetype (Chief, Planner, Writer, Editor, Critic, Continuity, etc.), including responsibilities, scope limits, and when to abstain in conference.
- Triggers/Interfaces: Conference interpretation phase, role prompts, agent configuration, hiring/specialization.
- Implementation prerequisites:
- prereq: Role registry
- prereq: Conference two-phase lifecycle
- Primary refs: [Agent role contracts](reference/agent_roles.md)
- Related refs: [Role archetypes, skills, and tiers](cr_exocortex_roles_skills_tiers.md), [Agent workflow](cr_agents.md#agent-workflow), [Conference round lifecycle](statemachine.md#conference-round-lifecycle-two-phase-model)

### Tiering system (cap-based progression)
- Doc type: Spec
- Purpose: Define unbounded tiers, caps, promotion/demotion, and safety valves.
- Triggers/Interfaces: Planner/Assistant tier enforcement and task sizing.
- Implementation prerequisites:
- prereq: Task verification pipeline
- prereq: Planner task sizing estimates
- Primary refs: [Tiering system](reference/tiers.md)

### Execution modes (Conference + Pipeline)
- Doc type: Reference
- Purpose: Define the two choreography modes — Conference (parallel interpretation) and Pipeline (sequential production) — plus the Recipe format, StepRunner contract, and Session Plan lifecycle.
- Triggers/Interfaces: Conference UI, Session Plan UI, task_router, StepRunner, tier-based step sizing.
- Implementation prerequisites:
- prereq: Conference two-phase lifecycle (implemented)
- prereq: task_router promotion to system service
- prereq: StepRunner + Recipe registry
- Primary refs: [Execution modes](reference/execution_modes.md)
- Related refs: [Agent role contracts](reference/agent_roles.md), [Conference round lifecycle](statemachine.md#conference-round-lifecycle-two-phase-model), [Assistant concept](cr_assistant_assisted_mode.md), [Tiering system](reference/tiers.md)

### Team Mode workflow
- Doc type: Reference
- Purpose: Define the mandatory Assistant-led execution flow from intent to polished scenes.
- Triggers/Interfaces: Team Mode, scene issues, capability profiles, assisted mode.
- Implementation prerequisites:
- prereq: Assistant (Team Leader) role
- prereq: Planner roadmap + status tags
- Primary refs: [Team Mode workflow](cr_team_mode_workflow.md#team-mode-workflow), [Assistant Team Leader](cr_agents.md#agent-team-leader)

### Role settings, autonomy, and stop hooks
- Doc type: Mixed
- Purpose: Define autonomy levels and mandatory stop conditions per role.
- Triggers/Interfaces: Role settings UI, freedom levels, stop hooks.
- Implementation prerequisites:
- prereq: Role settings UI
- prereq: Stop hook enforcement
- Primary refs: [Freedom & stop hooks](cr_agents.md#agent-freedom), [Stop hooks](cr_agents.md#agent-stop-hooks)
- Related refs: [Circuit breakers](cr_prefrontal_exocortex.md#exocortex-circuit-breakers), [Grounding rules](cr_prefrontal_exocortex.md#exocortex-grounding-rules)

### Team dynamics and conflict resolution
- Doc type: Spec
- Purpose: Coordinate multi-agent decisions via primaries, leads, and voting.
- Triggers/Interfaces: Primary agent, team lead arbitration, voting rules.
- Implementation prerequisites:
- prereq: Team lead designation
- prereq: Issue discussion threads
- Primary refs: [Team dynamics](cr_agents.md#agent-team-dynamics), [Assistant Team Leader](cr_agents.md#agent-team-leader), [Devil's Advocate assignment](cr_prefrontal_exocortex.md#exocortex-da-assignment)
- Related refs: [Anti-echo patterns](cr_prefrontal_exocortex.md#exocortex-anti-echo), [Epistemic status](cr_prefrontal_exocortex.md#exocortex-epistemic-status)

### Agent lifecycle (hire/retask/disable)
- Doc type: Mixed
- Purpose: Create, retask, or retire agents while preserving history.
- Triggers/Interfaces: Hiring wizard, retasking flow, disable workflow.
- Implementation prerequisites:
- prereq: Agents API
- prereq: Agent registry persistence
- Primary refs: [Agent lifecycle](cr_agents.md#agent-lifecycle), [Storage & API](cr_agents.md#agent-storage-api)
- Related refs: [Memory integration](cr_agents.md#agent-memory-integration), [Issue workflows](cr_memory.md#memory-agent-workflows)

## Safety/Anti-spiral mechanisms

### Prefrontal exocortex core
- Doc type: Spec
- Purpose: Prevent hype/doom spirals and hallucinated consensus in swarms.
- Triggers/Interfaces: Swarm health validation and mandatory roles.
- Implementation prerequisites:
- prereq: Mandatory roles defined
- prereq: Circuit breaker config
- Primary refs: [Why this exists](cr_prefrontal_exocortex.md#exocortex-why), [Mandatory roles](cr_prefrontal_exocortex.md#exocortex-mandatory-roles)
- Related refs: [Memory sanity checks](cr_prefrontal_exocortex.md#exocortex-memory-sanity), [Credits system](cr_prefrontal_exocortex.md#exocortex-credits)

### Circuit breakers and freeze workflow
- Doc type: Spec
- Purpose: Enforce hard limits on escalation, ping-pong, and low-substance comments.
- Triggers/Interfaces: Comment validation, freeze behavior, moderator review.
- Implementation prerequisites:
- prereq: Comment validation pipeline
- prereq: Moderator role
- Primary refs: [Circuit breakers](cr_prefrontal_exocortex.md#exocortex-circuit-breakers), [Freeze behavior](cr_prefrontal_exocortex.md#exocortex-freeze-behavior)
- Related refs: [Comment validation](cr_prefrontal_exocortex.md#exocortex-comment-validation), [Moderator role](cr_prefrontal_exocortex.md#exocortex-mandatory-roles)

### Evidence and grounding rules
- Doc type: Spec
- Purpose: Require citations and explicit grounding for structural/canon changes.
- Triggers/Interfaces: Evidence schema and grounding prompt rules.
- Implementation prerequisites:
- prereq: Evidence schema on comments
- prereq: File/issue reference format
- Primary refs: [Evidence-aware comments](cr_prefrontal_exocortex.md#exocortex-evidence-aware), [Grounding rules](cr_prefrontal_exocortex.md#exocortex-grounding-rules)
- Related refs: [Issue structure](cr_memory.md#memory-issue-structure), [Stop hooks](cr_agents.md#agent-stop-hooks)

### Anti-echo patterns and Devil's Advocate
- Doc type: Spec
- Purpose: Reduce consensus bias and force independent review on high-impact changes.
- Triggers/Interfaces: Blind first pass and DA assignment triggers.
- Implementation prerequisites:
- prereq: DA-capable agent flag
- prereq: Issue comment workflow
- Primary refs: [Anti-echo patterns](cr_prefrontal_exocortex.md#exocortex-anti-echo), [DA assignment](cr_prefrontal_exocortex.md#exocortex-da-assignment)
- Related refs: [Team dynamics](cr_agents.md#agent-team-dynamics), [Epistemic status](cr_prefrontal_exocortex.md#exocortex-epistemic-status)

### Epistemic status and canon promotion
- Doc type: Spec
- Purpose: Track decision maturity and gate canon updates.
- Triggers/Interfaces: Issue status fields and promotion rules.
- Implementation prerequisites:
- prereq: Issue schema extension
- prereq: Canon promotion rules
- Primary refs: [Epistemic status](cr_prefrontal_exocortex.md#exocortex-epistemic-status), [Grounding rules](cr_prefrontal_exocortex.md#exocortex-grounding-rules)
- Related refs: [Issue structure](cr_memory.md#memory-issue-structure), [Anti-echo patterns](cr_prefrontal_exocortex.md#exocortex-anti-echo)

### Credits and reliability tracking
- Doc type: Spec
- Purpose: Incentivize grounded work and track model reliability over time.
- Triggers/Interfaces: Credit events, performance records, remediation ladder.
- Implementation prerequisites:
- prereq: Verification function
- prereq: Performance record schema
- Primary refs: [Credit system](cr_prefrontal_exocortex.md#exocortex-credits), [Per-model records](cr_prefrontal_exocortex.md#exocortex-per-model-records)
- Related refs: [Memory sanity checks](cr_prefrontal_exocortex.md#exocortex-memory-sanity), [Model switch behavior](cr_prefrontal_exocortex.md#exocortex-model-switch)

## Issues/Boards/Notifications

### Issue model and API
- Doc type: API
- Purpose: Provide structured issue data and CRUD endpoints.
- Triggers/Interfaces: Issue REST API and storage model.
- Implementation prerequisites:
- prereq: Issue data model
- prereq: IssueMemoryService persistence
- Primary refs: [Issue structure](cr_memory.md#memory-issue-structure), [Issue REST API](cr_memory.md#memory-rest-api-issues)
- Related refs: [Personal tagging](cr_memory.md#memory-personal-tagging), [Agent workflows](cr_memory.md#memory-agent-workflows)

### Notifications as entry points
- Doc type: Reference
- Purpose: Route issue activity into a single shared issue modal.
- Triggers/Interfaces: Notification events and openIssue modal behavior.
- Implementation prerequisites:
- prereq: Notification store
- prereq: Shared issue modal
- Primary refs: [Notifications as entry points](cr_memory.md#memory-notifications), [Shared issue modal](cr_workbench.md#workbench-shared-issue-modal)
- Related refs: [Architecture summary](cr_memory.md#memory-architecture), [Issue REST API](cr_memory.md#memory-rest-api-issues)

### Issue board UI
- Doc type: UI behavior
- Purpose: Surface issues in Workbench and support filtering.
- Triggers/Interfaces: Workbench issue board panel and issue list filtering.
- Implementation prerequisites:
- prereq: Workbench layout panel
- prereq: Issue list filters
- Primary refs: [Issue board panel](cr_memory.md#memory-issue-board-panel), [Workbench issue board](cr_workbench.md#workbench-issue-board)
- Related refs: [Shared issue modal](cr_workbench.md#workbench-shared-issue-modal), [Interest levels](cr_memory.md#memory-interest-levels)

### Agent issue workflows
- Doc type: Reference
- Purpose: Define create/respond/search patterns for agent collaboration.
- Triggers/Interfaces: Issue search, comment, and tagging flows.
- Implementation prerequisites:
- prereq: Issue REST API
- prereq: Shared issue modal
- prereq: Notifications openIssue
- Primary refs: [Agent workflows](cr_memory.md#memory-agent-workflows), [Personal tagging](cr_memory.md#memory-personal-tagging)
- Related refs: [Agent workflow](cr_agents.md#agent-workflow), [Issue REST API](cr_memory.md#memory-rest-api-issues)

## Project Preparation & Canon

### Project Preparation Wizard (one-way ingest + virtual content)
- Doc type: Spec
- Purpose: One-time project founding ingest into canonical Story + Compendium state; virtual-only editor/explorer.
- Triggers/Interfaces: New Project wizard, Compendium/Story views, ingest evidence and indices.
- Implementation prerequisites:
- prereq: Project creation flow
- Primary refs: [Project preparation spec](reference/project_preparation.md)

## Workbench & Editor UI

### Workbench shell and panels
- Doc type: UI behavior
- Purpose: Define the Workbench layout, panels, and issue/newsfeed behavior.
- Triggers/Interfaces: Workbench view mode, issue board, newsfeed.
- Implementation prerequisites:
- prereq: View mode routing
- prereq: Issue modal wiring
- Primary refs: [Workbench UI](cr_workbench.md)
- Related refs: [Issue board panel](cr_memory.md#memory-issue-board-panel), [Shared issue modal](cr_workbench.md#workbench-shared-issue-modal)

### Editor view and authoring UX
- Doc type: UI behavior
- Purpose: Define editor-centric workflows, file operations, and review surfaces.
- Triggers/Interfaces: Monaco editor, file tree, patch review entry points.
- Implementation prerequisites:
- prereq: Editor view mode
- prereq: File tree + persistence
- Primary refs: [Editor UX](cr_editor.md)
- Related refs: [Agent tools](cr_agents.md#agent-capabilities), [Workbench UI](cr_workbench.md)

### Outline editor (Story root)
- Doc type: Spec
- Purpose: Dedicated modal editor for story outlines with scene cards and ordered moves.
- Triggers/Interfaces: Story explorer outline entry, outline editor modal, issue audit on accept.
- Implementation prerequisites:
- prereq: Story registry stable IDs
- Primary refs: [Outline editor plan](reference/outline_editor.md)

### Workbench focus editor
- Doc type: UI behavior
- Purpose: Single-file focus editor modal with TTS and publish-only saves.
- Triggers/Interfaces: Recent Files widget, focus editor modal, TTS settings.
- Implementation prerequisites:
- prereq: Version Control panel + publish workflow
- Primary refs: [Workbench focus editor](reference/workbench_editor.md)
- Related refs: [Editor UX](cr_editor.md), [Versioning spec](reference/versioning.md)

### Versioning & snapshots
- Doc type: Spec
- Purpose: Writer-focused versioning with autosave, snapshots, and history.
- Triggers/Interfaces: Version Control panel, commit queue, history viewer.
- Implementation prerequisites:
- prereq: Editor file operations
- Primary refs: [Versioning spec](reference/versioning.md)

## Provider/Model routing

### Agent endpoint configuration and key storage
- Doc type: Architecture
- Purpose: Route agents to providers/models and store credential references.
- Triggers/Interfaces: Endpoint config, provider keys, and storage layout.
- Implementation prerequisites:
- prereq: Agent endpoint storage
- prereq: Key store and security mode
- Primary refs: [Endpoint configuration](cr_agents.md#agent-endpoints), [Provider keys](cr_agents.md#agent-provider-keys)
- Related refs: [Storage & API](cr_agents.md#agent-storage-api), [Per-model records](cr_prefrontal_exocortex.md#exocortex-per-model-records)

### Per-model performance records and switching
- Doc type: Spec
- Purpose: Track reliability per model and handle model swaps cleanly.
- Triggers/Interfaces: Performance record schema and model switch behavior.
- Implementation prerequisites:
- prereq: Performance record schema
- prereq: Model switch handling
- Primary refs: [Per-model records](cr_prefrontal_exocortex.md#exocortex-per-model-records), [Model switch behavior](cr_prefrontal_exocortex.md#exocortex-model-switch)
- Related refs: [Credit system](cr_prefrontal_exocortex.md#exocortex-credits), [Endpoint configuration](cr_agents.md#agent-endpoints)

## Glossary
- R5/R3/R1: Memory representation levels from raw log to structured summary to semantic trace.
- witness: Pointer from a summary to specific evidence slices in higher-fidelity logs.
- escalation: Automatic step-up in context fidelity when evidence is needed.
- lock: Temporary override that prevents compression from demoting an active version.
- epoch: Project state boundary used to trigger decay/archival decisions.
