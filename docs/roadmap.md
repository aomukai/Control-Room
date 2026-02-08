# Control Room - Roadmap

> Implementation progress and upcoming milestones.

---

## Current Status

Note: some features shipped out of order in the last two days; the lists below reflect what is implemented now, regardless of original sequencing.

Status key (audit-aligned):
- `[x]` Implemented: code exists and is plausibly wired (CODE-MATCH); may still hide edge cases or runtime gaps.
- `[X]` Complete: runtime-verified by user for the primary path (and persistence/reload if relevant); safe to treat as "done".

### Completed Features

#### Core Editor & Project Space
- [x] Monaco editor with tabs and dirty-state tracking (refs: docs/reference/cr_editor.md)
- [x] Project tree (create/rename/delete/move files and folders) (refs: docs/reference/cr_editor.md)
- [x] Path handling with canonical normalization (refs: docs/reference/cr_editor.md)
- [x] Console panel with structured logs and level badges (refs: docs/reference/cr_editor.md)
- [x] Scene segmentation stub (WorkspaceService) (refs: docs/reference/cr_editor.md)
- [x] Editor explorer moved to slide-out panel beside the sidebar (refs: docs/reference/cr_editor.md)
- [x] Story/Compendium explorer split with scoped search; compendium folder rename blocked (refs: docs/reference/cr_editor.md)
- [x] Outline Editor (Story Root) - virtual SCN-outline.md modal + issue-on-accept flow (refs: docs/reference/outline_editor.md)

#### Notification System
- [x] Global NotificationStore (frontend + backend) (refs: docs/reference/cr_memory.md, docs/reference/cr_workbench.md)
- [x] 3-layer UI: ToastStack, StatusBar, NotificationCenter (refs: docs/reference/cr_workbench.md)
- [x] Notification model with Level, Scope, Category enums (refs: docs/reference/cr_memory.md)
- [x] JSON persistence (`data/notifications.json`) (refs: docs/reference/cr_memory.md)
- [x] REST API endpoints for notifications (refs: docs/reference/cr_memory.md)
- [x] Frontend sync on startup (refs: docs/reference/cr_workbench.md)
- [x] Dismissible persistent toasts (patch notifications) (refs: docs/reference/cr_workbench.md, docs/reference/cr_editor.md)

#### Issue System
- [x] Issue model with sequential IDs, priority, comments (refs: docs/reference/cr_memory.md#memory-issue-structure)
- [x] Comment model with author, body, timestamp, action (refs: docs/reference/cr_memory.md#memory-issue-structure)
- [x] IssueMemoryService with JSON persistence (refs: docs/reference/cr_memory.md)
- [x] REST API endpoints (full CRUD + comments) (refs: docs/reference/cr_memory.md#memory-rest-api-issues)
- [x] Frontend issue API wrapper (`issueApi`) (refs: docs/reference/cr_memory.md)
- [x] Planner roadmap status tags UI (Issue modal + issue cards) (refs: docs/reference/cr_memory.md#memory-roadmap-status-tags)
- [x] Issue modal markdown rendering (description + comments) and issue board icon fallbacks
- [x] Issue memory interest tracking (per-agent records + decay endpoints + Issue modal panel) (refs: docs/reference/cr_memory.md#memory-interest-levels)

#### Librarian Memory Substrate (backend)
- [x] MemoryItem + MemoryVersion models with default/active levels, pinning, and active-lock metadata (refs: docs/reference/cr_librarian_extension.md, docs/reference/cr_memory.md)
- [x] R5Event storage for witnessable evidence slices (refs: docs/reference/cr_librarian_extension.md)
- [x] MemoryService with JSON persistence and auto-level selection (R3 default, one-step escalation) (refs: docs/reference/cr_librarian_extension.md)
- [x] REST API: create items/versions/events, `level=auto|more`, evidence lookup, set active with lock guard (refs: docs/reference/cr_librarian_extension.md)

#### Librarian Chat & Moderator Wiring
- [x] Chat reroll button bumps context (auto -> next level) and surfaces `repLevel/escalated` badges (refs: docs/reference/cr_librarian_extension.md)
- [x] Witness chips + evidence fetch with toasts for success/failure (refs: docs/reference/cr_librarian_extension.md)
- [x] Moderator controls in Settings: promote/active-lock, pin min level, archive state with toast feedback (refs: docs/reference/cr_librarian_extension.md, docs/reference/cr_prefrontal_exocortex.md)
- [x] Manual decay/archival trigger with archive/expire thresholds (no pruning; honors pins and active locks) (refs: docs/reference/cr_librarian_extension.md)
- [x] Background decay scheduler (6h cadence, respects pins and active locks) (refs: docs/reference/cr_librarian_extension.md)
- [x] Decay config persistence (scheduler settings saved to `data/decay-config.json` so UI changes survive restarts) (refs: docs/reference/cr_librarian_extension.md)
- [x] Decay filters: exclude topic keys/agent IDs (manual + scheduled) to avoid archiving active threads; persisted with scheduler config (refs: docs/reference/cr_librarian_extension.md)
- [x] Scheduled dry-run mode sends notification reports (no writes) to monitor decay impact (refs: docs/reference/cr_librarian_extension.md)
- [x] Patch Review modal (patch proposals list/detail + apply/reject) with persistent storage and notifications; multi-file diffs, provenance, audit trail, inline errors, delete/cleanup, side-by-side diff table with line numbers, sticky headers, and per-file navigation (pills + prev/next + error badges) (refs: docs/reference/cr_editor.md)
- [x] Patch Review governance: notification breadcrumbs + provenance, apply-failure detail, audit export, scheduled cleanup (refs: docs/reference/cr_editor.md)
- [x] **Patch Review Modal Polish (Session 1)**: Redesigned for writers - wider modal (85-90vw), proper scrolling, agent personality header with avatar, improved diff colors/typography/spacing, visual refinements throughout, test patch button for development (refs: docs/reference/cr_editor.md)
- [x] **Agentic Editing Foundations**: Propose Edit flow, patch proposal contract, replacement-based edits, base-hash safety, and Patch Review integration (refs: docs/reference/cr_editor.md)
- [x] **Patch Review/Issue Wiring**: Patch proposals auto-create issues + issue modal patch review buttons (refs: docs/reference/cr_memory.md, docs/reference/cr_workbench.md)
- [x] **Prepared Mode Patch Support**: Patch diff/apply works on virtual Story/Compendium files (refs: docs/reference/project_preparation.md)

#### Refactors & Architecture
- [x] Controller extraction (per-domain controllers + Controller interface) (refs: docs/reference/cr_workbench.md)
- [x] Safe error responses via `Controller.errorBody` (refs: docs/reference/cr_workbench.md)
- [x] Frontend refactor: extracted `api.js` and `state.js` (refs: docs/reference/cr_workbench.md)
- [x] Provider strategy refactor (chat + models providers) (refs: docs/reference/cr_agents.md#agent-endpoints)
#### Agent System (MVP)
- [x] Agent registry file (`workspace/<workspaceName>/.control-room/agents/agents.json`) (refs: docs/reference/cr_agents.md#agent-storage-api)
- [x] Agent list API (`/api/agents`) (refs: docs/reference/cr_agents.md#agent-storage-api)
- [x] Agent endpoint registry (`agent-endpoints.json`) (refs: docs/reference/cr_agents.md#agent-endpoints)
- [x] Workbench agent sidebar rendered from registry (refs: docs/reference/cr_agents.md, docs/reference/cr_workbench.md)
- [x] Editor agent selector dropdown (refs: docs/reference/cr_agents.md)
- [x] Agent context menus with modal scaffolding (refs: docs/reference/cr_agents.md, docs/reference/cr_workbench.md)
- [x] Add Agent wizard (basic onboarding + POST `/api/agents`) (refs: docs/reference/cr_agents.md#agent-lifecycle)
- [x] Add Agent UI polish (validation, tooltips, post-create profile prompt) (refs: docs/reference/cr_agents.md#agent-lifecycle)
- [x] Chief of Staff (Assistant) leader badge and briefings wired to assistant role (refs: docs/reference/cr_exocortex_roles_skills_tiers.md#exocortex-mandatory-assistant)
- [x] Add Agent wizard forces Chief of Staff creation when missing (refs: docs/reference/cr_exocortex_roles_skills_tiers.md#exocortex-mandatory-assistant)
- [x] Agent reorder persistence fix (PUT `/api/agents/order`) (refs: docs/reference/cr_agents.md#agent-storage-api)
- [x] Add Agent wizard endpoint step uses Agent Settings modal (refs: docs/reference/cr_agents.md)
- [x] Invite to Conference modal (UI wiring) (refs: docs/reference/cr_workbench.md)
- [x] Agent intro issues + greeting comment auto-post (refs: docs/reference/cr_agents.md#agent-lifecycle)
- [x] Agent Settings modal (provider/model/key wiring) (refs: docs/reference/cr_agents.md#agent-endpoints)
- [x] Agent endpoint role presets (temp/top_p/top_k/min_p/repeat_penalty) with provider-default override + auto-apply on role change/empty settings
- [x] Agent status lamp + reachability checks (refs: docs/reference/cr_agents.md)
- [x] Drag-and-drop agent reorder (persisted) (refs: docs/reference/cr_agents.md)
- [x] Retired Agents modal (disable/reactivate) (refs: docs/reference/cr_agents.md#agent-lifecycle)
- [x] Workbench agent UX polish (context menu clamped, retire modal closes immediately, nursing home refresh) (refs: docs/reference/cr_workbench.md)
- [x] Assisted Mode modal + per-agent controls (Workbench sidebar) (refs: docs/reference/cr_assistant_assisted_mode.md)
- [x] Chief of Staff cannot be set to assisted mode (frontend + backend guard) (refs: docs/reference/cr_assistant_assisted_mode.md)
- [x] Highlander rule: only one Chief of Staff allowed; template hidden when one exists (refs: docs/reference/cr_agents.md)
- [x] "Assistant" → "Chief of Staff" rename across all user-facing UI (agent cards, wizards, tooltips, archetype dropdown)

#### Agent Profile System
- [x] **Agent Profile Modal** - Full character sheet UI (refs: docs/reference/cr_agents.md)
  - Avatar upload with drag-drop (auto-resized to 256px)
  - Name and Role fields (role with suggestions dropdown)
  - 8 personality sliders with tooltips
  - Custom instructions textarea
  - Signature line field ("Carthago delenda est.")
- [x] **Quick Presets** - 10 collapsible personality templates (refs: docs/reference/cr_agents.md)
  - Zen Strategist, Playful Brainstormer, Academic Editor
  - Ruthless Critic, Compassionate Coach, Lore Archivist
  - Productive Taskmaster, Poetic Prose Weaver, Plot Snake, Continuity Sentinel
- [x] **Agent Persistence** - Full CRUD API (refs: docs/reference/cr_agents.md#agent-storage-api)
  - GET/PUT `/api/agents/{id}` endpoints
  - POST `/api/agents` create endpoint
  - POST `/api/agents/import` for importing agents
  - Extended Agent model: `personalitySliders`, `signatureLine`
- [x] **Export/Import** - Share agent configurations (refs: docs/reference/cr_agents.md#agent-storage-api)
  - Export agent to JSON file
  - Import agent from JSON file
  - Duplicate agent functionality
  - "+" button in sidebar header for quick import
- [x] **Sidebar Improvements** (refs: docs/reference/cr_agents.md, docs/reference/cr_workbench.md)
  - Image avatars (base64) with fallback to emoji/initials
  - Name truncation with tooltip for long names

#### Role Settings System
- [x] **Role Settings Modal** - Per-role behavioral guidelines (refs: docs/reference/cr_agents.md#agent-freedom)
  - Behavior template selector (Autonomous / Balanced / Verbose / Custom)
  - Freedom level dropdown (supervised / semi-autonomous / autonomous)
  - Notification preferences (5 checkboxes)
  - Role Charter textarea (job description)
  - Collaboration Guidance textarea (escalation rules)
  - Tool & Safety Notes textarea (constraints)
- [x] **Template System** - Pre-fill from templates, auto-switch to Custom on edit (refs: docs/reference/cr_agents.md#agent-freedom)
- [x] **Persistence** - Per-role settings in `agents.json`, REST API endpoints (refs: docs/reference/cr_agents.md#agent-storage-api)
- [x] **Dirty Detection** - Cancel confirmation when unsaved changes exist (refs: docs/reference/cr_agents.md#agent-freedom)

#### Provider & Key Management
- [x] **Provider Model Fetching** - OpenAI/Anthropic/Gemini/Grok/OpenRouter/NanoGPT/TogetherAI + local providers (refs: docs/reference/cr_agents.md#agent-endpoints)
- [x] **Global Key Storage** - Plaintext or encrypted vault, per-provider key metadata (refs: docs/reference/cr_agents.md#agent-provider-keys)
- [x] **Provider Models API** - `/api/providers/models` (refs: docs/reference/cr_agents.md#agent-provider-keys)
- [x] **Security Settings API** - `/api/settings/security` + vault unlock/lock (refs: docs/reference/cr_agents.md#agent-provider-keys)
- [x] **Agent Chat Routing** - `/api/ai/chat` uses agent endpoint/provider when `agentId` is supplied (refs: docs/reference/cr_agents.md#agent-endpoints)

#### Credits & Activity Signals
- [x] **Credits System Backend (MVP)** - credit events storage + profiles API + assisted slicing endpoint (refs: docs/reference/cr_prefrontal_exocortex.md#exocortex-credits, docs/reference/cr_assistant_assisted_mode.md#assistant-credit-structure)
- [x] **Credits Leaderboard Widget** - Workbench widget wired to profiles + manual refresh (refs: docs/reference/cr_prefrontal_exocortex.md#exocortex-credits)
- [x] **Credits Dev Tools** - seed credits + post credit comment for testing (refs: docs/reference/cr_prefrontal_exocortex.md#exocortex-credits)
- [x] **Auto-credit Hooks** - credits on issue close, patch apply, and comment action types (refs: docs/reference/cr_prefrontal_exocortex.md#exocortex-credits)
- [x] **Credit Governance** - evidence context required for evidence reasons; system/moderator-only reasons enforced
- [x] **Credits Scoped per Project** - credits stored under `workspace/<project>/.control-room/credits/credits.json`
- [x] **Agent Activity Icons (MVP)** - micro icon cluster + runtime activity states from chat/issue/patch/conference (refs: docs/reference/agent_cards.md)

#### Safety & Governance
- [x] Stop hooks enforced in main chat + workbench chat (badges + reroll gating)
- [x] Assistant canon constraint injected into chat prompt (approval-required stop hook)
- [x] Circuit Breakers + Model-Locked Eval - runtime safety gates for small/local models
- [x] Tiering System backend (caps, promotion/demotion, clamps, policy + events, API) (refs: docs/reference/tiers.md)
- [x] Prompt hardening + receipts (PH-0..PH-5) - task packet/receipt schemas + validators, disk-backed receipt storage, Chief router, agent execution guardrails, UI attached report viewer, and "Write Scene from Outline" playbook state machine

#### AI Foundation (Read-Only)
- [x] Summarize/Explain/Suggest tools wired in Editor chat (read-only output modal)
- [x] Revisit Explain/Suggest prompts for JSON stability (Summarize is stable)
- [x] Tool suite expansion - additional prompt tools (e.g. `prose_analyzer`, `consistency_checker`, `scene_draft_validator`, `issue_status_summarizer`, `stakes_mapper`, `timeline_validator`)

#### Settings UI
- [x] **Modern Sidebar Layout** - Two-panel design with category navigation + content area (refs: docs/reference/cr_workbench.md)
- [x] **Category Navigation** - 6 sections: Appearance, Editor, Providers, Keys & Security, Backup, Shortcuts (refs: docs/reference/cr_workbench.md)
- [x] **Section Icons** - Heroicons for each category (swatch, pencil-square, server, key, cloud, command-line) (refs: docs/reference/cr_workbench.md)
- [x] **Back Button** - Returns to previous view mode (editor or workbench) (refs: docs/reference/cr_workbench.md)
- [x] **Coming Soon Indicators** - Visual badges and dimmed controls for unimplemented features (refs: docs/reference/cr_workbench.md)
- [x] **Polished Form Controls** - Custom dropdowns, toggle switches with animations, focus states (refs: docs/reference/cr_workbench.md)
- [x] **Keyboard Shortcuts Display** - Styled kbd elements for hotkey reference (refs: docs/reference/cr_workbench.md)
- [x] **Add Key Form** - Inline form with labeled fields for provider key management (refs: docs/reference/cr_workbench.md, docs/reference/cr_agents.md#agent-provider-keys)
- [x] **Responsive Design** - Adapts to smaller viewports with stacked layout (refs: docs/reference/cr_workbench.md)
- [x] **Prompt Tools Editor** - Registry-backed prompt tools with catalog injection for all agent calls (refs: docs/reference/cr_exocortex_roles_skills_tiers.md#exocortex-prompt-tools)
- [x] **Telemetry Settings** - Telemetry logging toggle + retention limits (max sessions, max age days, size cap; 0 = never delete). (refs: docs/reference/cr_workbench.md)

#### Project System (formerly Workspace)
- [x] Project folder layout under `workspace/<projectName>` (refs: docs/reference/cr_editor.md)
- [x] Project switching modal (select existing project or type a new name) (refs: docs/reference/cr_editor.md)
- [x] Persist active project selection; live reload after switching (refs: docs/reference/cr_editor.md)
- [x] Project metadata (display name/description/icon) surfaced in top bar/switcher (refs: docs/reference/cr_editor.md)
- [x] Recent-project quick chips in switcher (refs: docs/reference/cr_editor.md)
- [x] Project Preparation Wizard + ingest (canonical manifests, evidence receipts, prep gating) (refs: docs/reference/project_preparation.md)
- [x] Prepared mode scene reindex endpoint + metadata (hooks-index-based; on-demand) (refs: docs/reference/project_preparation.md)
- [x] Canon Index boot sequence: mandatory LLM-driven indexing when CoS is wired; frontend progress loop; Canon.md served via VFS (refs: docs/reference/project_preparation.md)
- [x] `skipTools` flag on `/api/ai/chat`: bypasses tool catalog/grounding/tool loop for raw LLM calls

#### UI Integration
- [x] **Global Issue Detail Modal** - Shared modal accessible from anywhere (refs: docs/reference/cr_workbench.md#workbench-shared-issue-modal, docs/reference/cr_memory.md#memory-issue-board-panel)
- [x] **Notification-to-Issue routing** - `actionPayload.kind: 'openIssue'` opens modal (refs: docs/reference/cr_memory.md#memory-notifications, docs/reference/cr_workbench.md#workbench-shared-issue-modal)
- [x] **Unified Sidebar Navigation** - Single sidebar for all navigation, no separate top bar; mode-specific buttons (Editor: Explorer/File System/Search/Versioning/Terminal; Workbench: Issues/Widgets/Patch Review); footer buttons always visible (Project Switcher/Dev Tools/Settings); file tree hidden in workbench mode; cleaner, more consistent UX (refs: docs/reference/cr_workbench.md#workbench-view-mode, docs/reference/cr_editor.md#editor-view-mode)
- [x] **Workbench Landing View** - App starts in Workbench mode instead of Editor.
- [x] **Workbench Shell** - 3-panel layout (Agents / Issue Board / Newsfeed) (refs: docs/reference/cr_workbench.md#workbench-issue-board)
- [x] **Workbench Dashboard** - Planner briefing + issue pulse cards (refs: docs/reference/cr_workbench.md)
- [x] **Agent Card Redesign** - Expressive agent panels with status LED, Chief of Staff badge, and status effects bar (refs: docs/claude.md#next-up-agent-card-redesign, docs/agents.jpg)
- [x] **Workbench Widget System (Sessions 1-2)** - Customizable dashboard with 5 widgets: Quick Notes (markdown preview, auto-save), Planner Briefing (team lead summary), Issue Pulse (stats), Credits Leaderboard (stub), Team Activity (stub). Features: WidgetRegistry, Widget base class, DashboardState with localStorage persistence, widget picker modal (900px wide, responsive grid), add/remove flow, resizable widgets (both directions), mount/unmount animations, Widgets button in top toolbar with pulse hint (refs: docs/claude.md#session-1, docs/claude.md#session-2)
- [x] **Widget Click-to-Focus / Cinema Mode (Session 7.5)** - Click widget body to open enlarged modal view, ESC/click-outside to close, smooth scale animations, `isFocused` flag for enhanced rendering, QuickNotes side-by-side editor+preview, smart click detection (ignores interactive elements), widget state syncs on close (refs: docs/claude.md#session-75)
- [x] **Visual Polish (Session 9)** - Ambient breathing gradient background, widget hover lift (+3px translateY, enhanced shadow), empty state floating animation, `prefers-reduced-motion` accessibility support (refs: docs/claude.md#session-9)
- [x] **Writing Streak Widget** - Tracks daily writing activity with streak counter, calendar heatmap visualization, and motivational stats (words written, active days, current streak) (refs: docs/reference/cr_workbench.md)
- [x] **Scene Editor Widget** - Quick scene access from dashboard with focus editing modal; displays scene list from story folder, click-to-edit with distraction-free modal overlay (refs: docs/reference/cr_workbench.md)
- [x] **Issue Board Panel** - Slide-in board with cinema overlay (refs: docs/reference/cr_workbench.md#workbench-issue-board)
- [x] **Conference Panel** - Slide-in conference modal with attendees + roster controls (refs: docs/reference/cr_workbench.md)
- [x] **Conference Chat + Two-Phase Model** - Chief-led Phase 1 tool gathering + Phase 2 grounded role responses with abstain; round transcript auto-saved as an Issue with linked receipts (refs: docs/statemachine.md)
- [x] **Team Lead Marker** - Highlight top agent in roster (refs: docs/reference/cr_agents.md#agent-team-leader, docs/reference/cr_workbench.md)
- [x] **Workbench Newsfeed** - Filtered notification stream with issue actions (refs: docs/reference/cr_workbench.md)
- [x] **Issue Board MVP** - Card-based issue list with filters, click modal (refs: docs/reference/cr_workbench.md#workbench-issue-board, docs/reference/cr_memory.md#memory-issue-board-panel)

#### Recent Polish & Fixes
- [x] **Project Switch Improvements** - Project list excludes stale entries; delete project button in modal
- [x] **Notification Polish** - Left-click opens connected issue; hover exposes close button for deletion
- [x] **Warmth Slider Widget** - Visual polish pass
- [x] **Issues Scoped per Project** - Issues now stored locally per workspace/project
- [x] **NO_PROJECT state** - Clean startup without a project; disk-backed registries dormant until marker exists
- [x] **Prep Explorer Improvements** - Multi-select file move during draft prep; rename modal uses filename-only with invisible canon prefixes
- [x] **Prep Completion Onboarding** - Workbench newsfeed notification opens a welcome issue when clicked
- [x] **Onboarding Copy Fix** - Removed mojibake characters from Welcome/onboarding copy (ASCII punctuation)
- [x] **Supplemental Prep Import** - Add “forgot files?” flow for manuscript/outline/background during draft prep
- [x] **Outline ingest merge** - Merge outline uploads into a single outline and persist `outline.json` during prep
- [x] **Canon Index UX Polish** - Status panel in modal, run report issue on success/failure, retry failed-only action
- [x] **Strip reasoning tags** - Remove <think>/<thinking> and [think]/[thinking] blocks from AI outputs (UI + backend, including orphan closing tags)
- [x] **Chat timeout extended** - Default chat request timeout raised to 300s for slow local models
- [x] **Circuit breaker tuning** - Escalation keyword list trimmed and threshold raised; user-facing/narrative roles bypass gates
- [x] **Issue-linked patches** - Patches can be created from issues and carry `issueId` into notifications/review.
- [x] **Outline virtual source** - Outline parsing reads from `Story/SCN-outline.md` in prepared (virtual) projects.
- [x] **Outline Editor UI Polish** - Wider modal (900px), scene cards with number badges, POV chips, collapsible summaries, change indicators (moved/edited) with teal/blue accents.
- [x] **Patch Review Modal UX Overhaul** - Prose-style diff view (side-by-side Original/Proposed blocks instead of line-by-line), persistent patch list in both Editor/Diff modes, global view toggle, compact refresh icon, fixed modal height (85vh) for consistent sizing, improved file pills (filename with tooltip), overflow menu for secondary actions, dimmed applied/rejected patches, enhanced agent header.
- [x] **Issue Modal UI/UX Overhaul** - Wider modal (900px, fixed 85vh height), agent avatar chips for author/assignee, reorganized meta section with grid layout and inline dropdowns (status/assignee/roadmap), functional comment form with Ctrl+Enter support, close/reopen issue buttons, comment avatars with hover states (refs: docs/ideas/issue_modal_polish.md)
- [x] **Editor UI Cleanup** - Removed dead buttons (Reveal File, Open Folder, Terminal, File System, status bar), moved New Issue and File History to sidebar, toolbar now tabs-only, project badge moved to chat panel header (refs: docs/ideas/editor_ui_rework.md)
- [x] **Markdown rendering in all chat views** - Agent messages rendered with headers, bold, italic, lists, code blocks via `renderSimpleMarkdown()`; user messages stay plain text

---

## Roadmap (Forward)

### Dependency Chain (Why This Order)
Versioning UX polish and Project Preparation Wizard are complete, so canonical data is now available. Next up: agentic editing and downstream features that depend on canonical state (memory degradation, personal tagging, continuity tooling). Pure UI work (Agent Card Redesign) can be scheduled around these without blocking.

### Milestone Definitions
- **Now**: Active focus for the next 1-2 sessions; minimal scope, shippable.
- **Next**: Queued once Now completes; should be well-scoped and near-ready.
- **Medium Term**: Meaningful but not imminent; depends on prior groundwork.
- **Future**: Long-range ideas; likely to be re-scoped later.

### Now (Active)

- [ ] **Execution Modes: Pipeline (StepRunner + Recipes)** - Sequential production mode where each agent's output feeds the next. (refs: docs/reference/execution_modes.md)
  - [ ] StepRunner core (server-side): execute a recipe mechanically (no reasoning), halt on stop hook, persist audit trail.
  - [ ] Run persistence: disk-backed run logs + step outputs (stable IDs, timestamps).
  - [ ] REST API: start run + poll status + fetch run artifacts.
  - [ ] One end-to-end dry-run recipe: deterministic, no model calls required.
  - [ ] Acceptance: happy path + step failure path + restart path (reload and re-open run status).
- [ ] 1:1 chat tool-call reliability: continue hardening strict JSON tool calls and stop hook surfaces for small/local models.
- [ ] Provider resiliency: retries/backoff/timeouts + more actionable UI errors for transient provider/network failures.

### Next (Queued)

- [ ] Pipeline UX: Session Plan UI (ordered checklist with status, pause/resume, dependency blocking).
- [ ] Recipe registry (data, not code): declarative recipes with tools/agents phases and machine-checkable DoD where possible.
- [ ] Deterministic `task_router`: request -> recipe_id + initial args, routable=true/false.
- [ ] Replace the hardcoded "Write Scene from Outline" playbook with an equivalent StepRunner recipe.
- [ ] Dashboard layout backend persistence: migrate widget layout from localStorage to per-project `.control-room/` file (refs: docs/claude/BOOT.md, docs/claude/SYSTEMS_WIDGETS.md)
- [ ] Widget import/export (MVP): export a widget layout + import on another project/machine (refs: docs/claude/BOOT.md, docs/claude/SYSTEMS_WIDGETS.md)
- [ ] Editor bottom panel UX pass: fix scroll issues, default to AI Actions, consider hiding Console by default (refs: docs/ideas/editor_ui_rework.md)
- [ ] Settings "Soon" inventory -> roadmap: backup/cloud, editor settings, other placeholders need scoping and sequencing (refs: Settings UI)

### Medium Term

- (All previous Medium Term items are complete; see Completed Features above.)

### Future

- [ ] **Export Stage** - PDF/EPUB/DOCX manuscript export (refs: docs/reference/cr_editor.md)
- [ ] Custom widgets (needs design): widget sandbox/permissions + per-project storage + import/export of widget bundles (refs: docs/claude/BOOT.md, docs/claude/SYSTEMS_WIDGETS.md)
- [ ] Agent tools governance (needs design): review-gated tool proposals (agent + user), locked core tool suite, plus harness + tool versioning approach (refs: docs/reference/agent_tools.md)
- [ ] Cross-role autonomy + speculative execution (post-Pipeline): allow single-agent chains with explicit attempted roles and deterministic fallback (refs: docs/ideas/scene_pipeline_cross_role_autonomy.md)
- [ ] Theme builder / custom themes (big): theme import/export + visual theme editor (refs: docs/claude/SYSTEMS_THEMES.md)

### Completed Milestones (for reference)

- [x] **Version Control (Manual Save & History)** - Changes list, publish, history list + current-vs-snapshot split view (refs: docs/reference/versioning.md)
- [x] **Milestone 7: The Unified Team Leader** (Assistant gating, assisted mode, planner status tags, credit slicing)
- [x] **Frontend Modularization** - `app.js` split into domain modules (refs: docs/refactor.md)

---

## Architecture Notes

### View Mode System
```javascript
state.viewMode.current   // 'editor' | 'workbench' | 'settings'
state.viewMode.previous  // Tracks last mode for back navigation
setViewMode(mode)        // Switch views, trigger rendering
```

### Issue Modal (Global)
```javascript
state.issueModal        // Modal state (open, loading, error, data)
openIssueModal(id)      // Open from anywhere
closeIssueModal()       // Clean up and close
```

### Issue Board
```javascript
state.issueBoard        // Issues list, loading, error, filters
loadIssues()            // Fetch with current filters
renderIssueBoard()      // Render board structure
createIssueCard(issue)  // Create individual card element
```

### Notification Helpers
```javascript
notificationStore.issueCreated(id, title, author, assignee)
notificationStore.issueClosed(id, title)
notificationStore.issueCommentAdded(id, author)
```

---

## REST API Summary

### Preparation & Canon Index
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/preparation/reindex/scene` | Reindex scene hooks into derived metadata |
| GET | `/api/canon/index/status` | Get canon index status (indexed + manifest metadata: status, fileCount/cardCount, preparedAt, reviewedAt, indexedAt) |
| POST | `/api/canon/index` | Compile canon index from LLM-extracted entries |

### Issues
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/issues` | List issues (filters: tag, assignedTo, status, priority) |
| GET | `/api/issues/{id}` | Get single issue |
| POST | `/api/issues` | Create issue |
| PUT | `/api/issues/{id}` | Update issue |
| DELETE | `/api/issues/{id}` | Delete issue |
| POST | `/api/issues/{id}/comments` | Add comment |
| POST | `/api/issues/{id}/patches` | Create patch proposal linked to issue |

### Agents
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/agents` | List enabled agents |
| GET | `/api/agents/all` | List all agents (including disabled) |
| POST | `/api/agents` | Create agent |
| GET | `/api/agents/{id}` | Get single agent |
| PUT | `/api/agents/{id}` | Update agent |
| PUT | `/api/agents/{id}/status` | Enable/disable agent |
| PUT | `/api/agents/order` | Persist roster ordering |
| POST | `/api/agents/import` | Import agent from JSON |
| GET | `/api/agent-endpoints` | List agent endpoints |
| GET | `/api/agent-endpoints/{id}` | Get agent endpoint config |
| PUT | `/api/agent-endpoints/{id}` | Upsert agent endpoint config |

### Memory (Librarian)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/memory` | Create memory item (agent/topic, default/pinned levels) |
| POST | `/api/memory/{id}/versions` | Add representation (R1-R5) |
| POST | `/api/memory/{id}/events` | Add R5 evidence event |
| GET | `/api/memory/{id}?level=auto|more` | Fetch auto-level or next-level content (returns escalated flag) |
| GET | `/api/memory/{id}/versions` | List version metadata |
| GET | `/api/memory/{id}/evidence?witness=...` | Fetch raw evidence slice by witness |
| PUT | `/api/memory/{id}/active/{versionId}` | Promote/rollback with temporary lock |
| PUT | `/api/memory/{id}/pin` | Set pinned minimum level |
| PUT | `/api/memory/{id}/state` | Update lifecycle state (active/archived/expired) |
| POST | `/api/memory/decay` | Run decay/compression pass (archive/expire; no pruning) |
| GET | `/api/memory/decay/status` | Get scheduler interval, last run, and last results |
| PUT | `/api/memory/decay/config` | Update scheduler interval/thresholds/prune toggle |
| POST | `/api/memory/decay` (dryRun) | Get detailed report (archived/expired/locked, items list) |
| (env) | `CR_DECAY_DRY_RUN`, `CR_DECAY_PRUNE_R5`, `CR_DECAY_REPORT`, `CR_DECAY_INTERVAL_MINUTES`, `CR_DECAY_ARCHIVE_DAYS`, `CR_DECAY_EXPIRE_DAYS` | Scheduler defaults |

### Issue Memory (Per-Agent)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/issue-memory/agents/{agentId}` | List issue memory records for agent |
| GET | `/api/issue-memory/agents/{agentId}/issues/{issueId}` | Get issue memory record |
| POST | `/api/issue-memory/agents/{agentId}/issues/{issueId}/access` | Record access (auto-escalate to L3) |
| POST | `/api/issue-memory/agents/{agentId}/issues/{issueId}/applied` | Mark applied (boost interest) |
| POST | `/api/issue-memory/agents/{agentId}/issues/{issueId}/irrelevant` | Mark irrelevant (demote to L1) |
| POST | `/api/issue-memory/agents/{agentId}/issues/{issueId}/tags` | Update personal tags |
| POST | `/api/issue-memory/agents/{agentId}/activate` | Increment activation counter |
| GET | `/api/issue-memory/agents/{agentId}/activation` | Get activation counter |
| POST | `/api/issue-memory/decay` | Run access-based decay (agent or all) |
| POST | `/api/issue-memory/epoch` | Apply one-time epoch bump by tags |

### Role Settings
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/agents/role-settings` | List all role settings |
| GET | `/api/agents/role-settings/{role}` | Get settings for role (defaults if none) |
| PUT | `/api/agents/role-settings/{role}` | Upsert role settings |

### Provider Models & Keys
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/providers/models` | List models for provider (provider/keyRef/baseUrl) |
| GET | `/api/settings/security` | Get key security mode |
| PUT | `/api/settings/security` | Set key security mode (migration) |
| POST | `/api/settings/security/unlock` | Unlock encrypted vault |
| POST | `/api/settings/security/lock` | Lock encrypted vault |
| GET | `/api/settings/keys` | List stored keys (metadata) |
| POST | `/api/settings/keys` | Add key |
| DELETE | `/api/settings/keys/{provider}/{id}` | Delete key |

### AI Chat
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/ai/chat` | Chat using an agent endpoint when `agentId` is supplied. Supports `skipTools: true` to bypass tool catalog/grounding/tool loop for raw LLM calls. |

### Telemetry
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/telemetry/summary` | Get session + lifetime telemetry totals |
| GET | `/api/telemetry/status` | Get retention snapshot (paths, size, would-delete preview) |
| GET | `/api/telemetry/config` | Get telemetry logging config |
| PUT | `/api/telemetry/config` | Update telemetry logging config |
| POST | `/api/telemetry/test` | Emit a test telemetry event (dev tools) |
| POST | `/api/telemetry/prune` | Force retention pruning (dev tools) |

### Notifications
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/notifications` | List notifications |
| GET | `/api/notifications/unread-count` | Get unread count |
| GET | `/api/notifications/{id}` | Get single notification |
| POST | `/api/notifications` | Create notification |
| PUT | `/api/notifications/{id}` | Update notification |
| DELETE | `/api/notifications/{id}` | Delete notification |
| POST | `/api/notifications/mark-all-read` | Mark all as read |
| POST | `/api/notifications/clear` | Clear notifications |

### Patches
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/patches` | List patches |
| GET | `/api/patches/{id}` | Get patch detail |
| POST | `/api/patches` | Create patch proposal |
| POST | `/api/patches/ai` | Create patch proposal from agentic edit payload |
| POST | `/api/patches/{id}/apply` | Apply patch proposal |
| POST | `/api/patches/{id}/reject` | Reject patch proposal |
| DELETE | `/api/patches/{id}` | Delete patch proposal |
| POST | `/api/patches/cleanup` | Cleanup applied/rejected patches |
| POST | `/api/patches/simulate` | Create simulated patch proposal |
| GET | `/api/patches/{id}/audit` | Export patch audit log |
| GET | `/api/patches/audit/export` | Export audit logs for all patches |

### Outline
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/outline` | Get outline + scene list |
| PUT | `/api/outline` | Save outline document |

### Patch Cleanup Scheduler (env)
| Key | Description |
|-----|-------------|
| `CR_PATCH_CLEANUP_INTERVAL_MINUTES` | Cleanup cadence (minutes) |
| `CR_PATCH_CLEANUP_RETAIN_DAYS` | Retention window for applied/rejected patches |
| `CR_PATCH_CLEANUP_NOTIFY` | Send notification on scheduled cleanup |
| `CR_PATCH_CLEANUP_DRY_RUN` | Run cleanup in dry-run mode |

---

## Out of Scope (Current Phase)

- Plugin marketplace
- Deep Git UI
- Compiler / LSP integration
- Long-running autonomous agents
- Drag-and-drop file operations
