# Workbench Agent Card - Status & Escalation Micro-Icons (Spec)

## Purpose

Improve the Workbench agent cards by replacing loud "fat tags" (e.g. WATCHED / ASSISTED) with subtle, glanceable micro-icons that:

- communicate **readiness** (already exists; keep it primary)
- communicate **activity** (reading / processing / executing)
- communicate **supervision / escalation** (watched / assisted)
- communicate **authority** (lead / primary role)

Design goals:
- no new text labels on cards (tooltips only)
- minimal visual noise; icons appear only when meaningful
- consistent with Heroicons-outline styling; allow Lucide for micro-status icons

Non-goals:
- redesign the entire card layout
- introduce new badge/tags beyond the existing lead ribbon/badge (if present)

---

## Terminology & State Model

### 1) Readiness (existing)
This is the primary "health" signal (green/yellow/red). Keep the current implementation and visual prominence.

Example semantics:
- green: all clear
- yellow: endpoint/model not reachable or not selected
- red: no AI wired / critical

### 2) Activity (ephemeral; mutually exclusive)
Activity answers: "What is the agent doing right now?"
Only one may be active at a time.

Enum:
- `idle` (default; show nothing)
- `reading`
- `processing`
- `executing`

### 3) Supervision / Escalation (persistent ladder)
Supervision answers: "Is this agent being monitored or receiving help?"
At most one visible at a time (severity rule below).

Enum:
- `none` (default)
- `watched`
- `assisted`

Severity rule:
- If both are present due to legacy data, render **ASSISTED only**.

### 4) Authority markers (persistent)
Authority answers: "Is this agent special in the structure?"
These may stack with supervision + activity.

Flags:
- `isLead` (existing: lead ribbon/badge OR icon)
- `isPrimaryRoleAgent` (new or already-existing concept)

---

## Visual Design Rules

### A) No fat tags for supervision
Remove/replace the large WATCHED / ASSISTED labels on the card.
Supervision is represented via **small icons**, not text pills.

### B) Icon cluster
Use a single "status cluster" attached to the card (top-right is preferred).
Icons are small and quiet.

- icon size: **12-14px**
- hover hit area: **18-20px** (padding)
- gap between icons: **4px**
- max visible icons: **3**
- ordering (left-right or inner-outer is fine, but must be consistent):
  1. Activity (if any)
  2. Supervision (if any)
  3. Authority (lead / primary)

### C) Color hierarchy
- Readiness light owns color urgency.
- All micro-icons default to neutral (`--fg-muted`).
- Do not color watched/assisted.
- Only color micro-icons if explicitly required for accessibility (prefer not).

### D) Animation rules
- Only the `processing` icon may animate (gentle rotation).
- No animation for watched/assisted or authority markers.
- Keep animation subtle:
  - duration ~1.0-1.2s
  - linear/ease-in-out OK
  - no bouncing

### E) Tooltip rules
Tooltips are the only text explanation.
- One sentence, human language, not system jargon.
- Appear quickly (0-150ms delay).
- Must work on mouse hover (desktop). Optional long-press on touch later.

---

## Icon Set Strategy

Primary icon set: **Heroicons (outline)** already used across the UI.

Add-on: **Lucide** (installed) only for cases where Heroicons lacks ideal micro-status glyphs
(e.g. loader/spinner, "assisted" metaphor, target/primary).

Target style: stroke-based, outline, monochrome.

---

## Icon Mapping (Final)

### Activity icons
- `reading`
  - Prefer: Heroicons `DocumentTextIcon` (or `BookOpenIcon` if already used elsewhere)
  - Tooltip: "Reviewing context and references."
- `processing`
  - Prefer: Lucide `Loader2` (best micro-spinner)
  - Tooltip: "Thinking through the best next steps."
  - Animation: slow rotate
- `executing`
  - Prefer: Heroicons `BoltIcon` (or Lucide `Play` if "action" reads better)
  - Tooltip: "Producing output / taking action now."

### Supervision icons
- `watched`
  - Prefer: Heroicons `EyeIcon`
  - Tooltip: "This agent is being monitored due to recent uncertainty."
- `assisted`
  - Prefer: Lucide `Users` (or `Link2` if you want "paired support")
  - Tooltip: "Another agent is quietly assisting to ensure accuracy."

### Authority icons
- `isPrimaryRoleAgent`
  - Prefer: Lucide `Target` (or Heroicons `BookmarkIcon` if you avoid Lucide here)
  - Tooltip: "Primary agent for this role."
- `isLead`
  - Keep existing lead ribbon/badge.
  - Optional future: add a small icon in the cluster (Lucide `Crown` / Heroicons `StarIcon`)
  - Tooltip (if icon used): "Team lead."

---

## Layout Recommendation (Agent Card)

Keep the card visually identical except for:
- removing big WATCHED/ASSISTED pills
- adding a top-right micro-icon cluster (or equivalent)
- keeping readiness light visible and dominant (already exists)

Suggested cluster placement:
- top-right corner inside the card
- readiness light stays where it is today (may be left of name or on the right; do not move unless trivial)

Activity icon is allowed to be:
- in the cluster, OR
- bottom-right corner (like a "currently doing" indicator)
Pick ONE approach and standardize.
Preferred: keep everything in the cluster to reduce scattered signals.

---

## Implementation Requirements

### 1) Data model / API
Frontend needs, per agent:
- `readiness` (existing)
- `activityState` (idle/reading/processing/executing) - may come from runtime store
- `supervisionState` (none/watched/assisted)
- `isLead` (existing)
- `isPrimaryRoleAgent` (if present; else add)

If activity is not currently persisted, it can be derived from:
- in-flight request tracking per agent
- task execution state machine in the client store

### 2) Rendering
Implement a small renderer function:
- `renderAgentStatusIcons(agent): HTMLElement` (or your framework equivalent)

Rules enforced by renderer:
- `idle` => no activity icon
- `supervisionState=none` => no supervision icon
- if both watched+assisted exist, show assisted only
- never exceed 3 visible icons; if this would happen, drop lowest priority icon(s) in this order:
  - drop primary-role marker first, then supervision, then activity (but try not to drop activity if it's active)

### 3) Styling
Add CSS classes:
- `.agentCard__statusCluster`
- `.agentCard__statusIcon`
- `.agentCard__statusIcon--processing` (for rotation)
- `.agentCard__statusIcon--hoverable`

Use existing CSS variables:
- `--fg-muted` (or closest)
- `--fg` for hover state (optional)
- keep icons monochrome

Rotation animation:
- `@keyframes cr-spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }`
- apply to processing icon only

### 4) Tooltips
Use existing tooltip system if present.
Tooltips must:
- show on hover of each icon
- use the copy specified above verbatim unless product decides otherwise

---

## Acceptance Criteria

1) WATCHED/ASSISTED fat tags are removed from agent cards.
2) When an agent is `watched`, an Eye micro-icon appears with correct tooltip.
3) When an agent is `assisted`, the Assisted micro-icon appears with correct tooltip.
4) When an agent is processing, a spinner icon appears and rotates gently.
5) When the agent is idle, no activity icon is shown.
6) Readiness light remains visually primary and unchanged.
7) Icons are subtle, monochrome, and consistent with existing UI style.
8) No layout jank: card height/spacing remains stable across state changes.

---

## Scope Notes / Future Extensions (Out of Scope)

- Add "intervened" supervision state (would warrant a stronger signal)
- Add a collapsed "+1" overflow indicator if we ever exceed the icon limit
- Add "Assisted by: <agent>" details in tooltip if desired
- Touch/mobile behavior (long-press tooltips)
