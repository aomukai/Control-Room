# Control Room - Roadmap

> Implementation progress and upcoming milestones.

---

## Current Status

Note: some features shipped out of order in the last two days; the lists below reflect what is implemented now, regardless of original sequencing.

### Completed Features

#### Core Editor & Project Space
- [x] Monaco editor with tabs and dirty-state tracking (refs: docs/reference/cr_editor.md)
- [x] Project tree (create/rename/delete/move files and folders) (refs: docs/reference/cr_editor.md)
- [x] Path handling with canonical normalization (refs: docs/reference/cr_editor.md)
- [x] Console panel with structured logs and level badges (refs: docs/reference/cr_editor.md)
- [x] Scene segmentation stub (WorkspaceService) (refs: docs/reference/cr_editor.md)

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

#### Librarian Memory Substrate (backend)
- [x] MemoryItem + MemoryVersion models with default/active levels, pinning, and active-lock metadata (refs: docs/reference/cr_librarian_extension.md, docs/reference/cr_memory.md)
- [x] R5Event storage for witnessable evidence slices (refs: docs/reference/cr_librarian_extension.md)
- [x] MemoryService with JSON persistence and auto-level selection (R3 default, one-step escalation) (refs: docs/reference/cr_librarian_extension.md)
- [x] REST API: create items/versions/events, `level=auto|more`, evidence lookup, set active with lock guard (refs: docs/reference/cr_librarian_extension.md)

#### Librarian Chat & Moderator Wiring
- [x] Chat reroll button bumps context (auto -> next level) and surfaces `repLevel/escalated` badges (refs: docs/reference/cr_librarian_extension.md)
- [x] Witness chips + evidence fetch with toasts for success/failure (refs: docs/reference/cr_librarian_extension.md)
- [x] Moderator controls in Settings: promote/active-lock, pin min level, archive state with toast feedback (refs: docs/reference/cr_librarian_extension.md, docs/reference/cr_prefrontal_exocortex.md)
- [x] Manual decay/archival trigger with archive/expire thresholds and optional R5 prune (honors pins and active locks) (refs: docs/reference/cr_librarian_extension.md)
- [x] Background decay scheduler (6h cadence, respects pins and active locks) (refs: docs/reference/cr_librarian_extension.md)
- [x] Decay config persistence (scheduler settings saved to `data/decay-config.json` so UI changes survive restarts) (refs: docs/reference/cr_librarian_extension.md)
- [x] Decay filters: exclude topic keys/agent IDs (manual + scheduled) to avoid archiving active threads; persisted with scheduler config (refs: docs/reference/cr_librarian_extension.md)
- [x] Scheduled dry-run mode sends notification reports (no writes) to monitor decay impact (refs: docs/reference/cr_librarian_extension.md)
- [x] Patch Review modal (patch proposals list/detail + apply/reject) with persistent storage and notifications; multi-file diffs, provenance, audit trail, inline errors, delete/cleanup, side-by-side diff table with line numbers, sticky headers, and per-file navigation (pills + prev/next + error badges) (refs: docs/reference/cr_editor.md)
- [x] Patch Review governance: notification breadcrumbs + provenance, apply-failure detail, audit export, scheduled cleanup (refs: docs/reference/cr_editor.md)
- [x] **Patch Review Modal Polish (Session 1)**: Redesigned for writers - wider modal (85-90vw), proper scrolling, agent personality header with avatar, improved diff colors/typography/spacing, visual refinements throughout, test patch button for development (refs: docs/reference/cr_editor.md)

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
- [x] Chief of Staff (Assistant) leader badge and briefings wired to assistant role (refs: docs/reference/cr_exocortex_roles_skills_tiers.md#exocortex-mandatory-assistant)
- [x] Add Agent wizard forces Chief of Staff creation when missing (refs: docs/reference/cr_exocortex_roles_skills_tiers.md#exocortex-mandatory-assistant)
- [x] Agent reorder persistence fix (PUT `/api/agents/order`) (refs: docs/reference/cr_agents.md#agent-storage-api)
- [x] Add Agent wizard endpoint step uses Agent Settings modal (refs: docs/reference/cr_agents.md)
- [x] Invite to Conference modal (UI wiring) (refs: docs/reference/cr_workbench.md)
- [x] Agent intro issues + greeting comment auto-post (refs: docs/reference/cr_agents.md#agent-lifecycle)
- [x] Agent Settings modal (provider/model/key wiring) (refs: docs/reference/cr_agents.md#agent-endpoints)
- [x] Agent status lamp + reachability checks (refs: docs/reference/cr_agents.md)
- [x] Drag-and-drop agent reorder (persisted) (refs: docs/reference/cr_agents.md)
- [x] Retired Agents modal (disable/reactivate) (refs: docs/reference/cr_agents.md#agent-lifecycle)

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
  - Max actions per session input
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

#### Project System (formerly Workspace)
- [x] Project folder layout under `workspace/<projectName>` (refs: docs/reference/cr_editor.md)
- [x] Project switching modal (select existing project or type a new name) (refs: docs/reference/cr_editor.md)
- [x] Persist active project selection; live reload after switching (refs: docs/reference/cr_editor.md)
- [x] Project metadata (display name/description/icon) surfaced in top bar/switcher (refs: docs/reference/cr_editor.md)
- [x] Recent-project quick chips in switcher (refs: docs/reference/cr_editor.md)

#### UI Integration
- [x] **Global Issue Detail Modal** - Shared modal accessible from anywhere (refs: docs/reference/cr_workbench.md#workbench-shared-issue-modal, docs/reference/cr_memory.md#memory-issue-board-panel)
- [x] **Notification-to-Issue routing** - `actionPayload.kind: 'openIssue'` opens modal (refs: docs/reference/cr_memory.md#memory-notifications, docs/reference/cr_workbench.md#workbench-shared-issue-modal)
- [x] **Unified Sidebar Navigation** - Single sidebar for all navigation, no separate top bar; mode-specific buttons (Editor: Explorer/File System/Search/Versioning/Terminal; Workbench: Issues/Widgets/Patch Review); footer buttons always visible (Project Switcher/Dev Tools/Settings); file tree hidden in workbench mode; cleaner, more consistent UX (refs: docs/reference/cr_workbench.md#workbench-view-mode, docs/reference/cr_editor.md#editor-view-mode)
- [x] **Workbench Shell** - 3-panel layout (Agents / Issue Board / Newsfeed) (refs: docs/reference/cr_workbench.md#workbench-issue-board)
- [x] **Workbench Dashboard** - Planner briefing + issue pulse cards (refs: docs/reference/cr_workbench.md)
- [x] **Workbench Widget System (Sessions 1-2)** - Customizable dashboard with 5 widgets: Quick Notes (markdown preview, auto-save), Planner Briefing (team lead summary), Issue Pulse (stats), Credits Leaderboard (stub), Team Activity (stub). Features: WidgetRegistry, Widget base class, DashboardState with localStorage persistence, widget picker modal (900px wide, responsive grid), add/remove flow, resizable widgets (both directions), mount/unmount animations, Widgets button in top toolbar with pulse hint (refs: docs/claude.md#session-1, docs/claude.md#session-2)
- [x] **Issue Board Panel** - Slide-in board with cinema overlay (refs: docs/reference/cr_workbench.md#workbench-issue-board)
- [x] **Conference Panel** - Slide-in conference modal with attendees + roster controls (refs: docs/reference/cr_workbench.md)
- [x] **Team Lead Marker** - Highlight top agent in roster (refs: docs/reference/cr_agents.md#agent-team-leader, docs/reference/cr_workbench.md)
- [x] **Workbench Newsfeed** - Filtered notification stream with issue actions (refs: docs/reference/cr_workbench.md)
- [x] **Issue Board MVP** - Card-based issue list with filters, click modal (refs: docs/reference/cr_workbench.md#workbench-issue-board, docs/reference/cr_memory.md#memory-issue-board-panel)

---

## Next Milestones

### Near Term

- [x] **Librarian Reroll & Escalation Wiring** - Chat reroll bumps context (auto -> next level), surface `escalated/repLevel` in responses (refs: docs/reference/cr_librarian_extension.md)
- [x] **Witness UI** - Render witness chips on summaries and fetch `/api/memory/{id}/evidence?witness=...` for hover/click popovers (refs: docs/reference/cr_librarian_extension.md)
- [x] **Moderator Controls (Pin/Promote/Archive)** - UI affordances to call active-version + pin settings; respect activeLockUntil (refs: docs/reference/cr_librarian_extension.md, docs/reference/cr_prefrontal_exocortex.md)
- [x] **Decay Scheduler + Status** - Background decay job with env-configurable cadence/thresholds and UI status display (refs: docs/reference/cr_librarian_extension.md)
- [ ] **Add Agent UI (polish)** - Tooltips, validation, and optional post-create profile (refs: docs/reference/cr_agents.md, docs/reference/cr_workbench.md)
- [x] **Global Settings UI** - Polished sidebar navigation, category icons, back button, coming-soon badges (refs: docs/reference/cr_workbench.md)
- [x] **Project Metadata & QoL** - Persisted display name/description/icon (top-bar + switcher); switching now applies live (reload) (refs: docs/reference/cr_editor.md)
- [x] **Agent Chat Wiring** - Connect agent sidebar to functional chat sessions (refs: docs/reference/cr_agents.md)
- [x] **Issue Creation UI** - Create issues from Workbench or Editor (refs: docs/reference/cr_memory.md, docs/reference/cr_workbench.md)
- [x] **Issue Quick Actions** - Close/reopen from board without modal (refs: docs/reference/cr_memory.md, docs/reference/cr_workbench.md)
- [x] **Patch Review Modal** - Patch proposals list/detail + apply/reject; persistent storage + notifications; multi-file diffs, provenance, audit, inline errors, delete/cleanup, side-by-side diff table with line numbers, sticky headers, and per-file navigation (pills + prev/next + error badges) (refs: docs/reference/cr_editor.md)
- [x] **Patch Review Modal Polish (Session 1)** - Redesigned for writers: wider modal, proper scrolling, agent personality header with avatar, improved diff view visual polish (refs: docs/reference/cr_editor.md)
- [x] **Patch Review Editor View (Session 2)** - Writer-friendly view with Track Changes aesthetic: strikethroughs for deletions, underlines for additions, readable prose format, pill-style view switcher, full-width layout, dynamic mode switching (refs: docs/reference/cr_editor.md)
- [x] **Patch Review Editable Content (Session 3)** - Contenteditable editor view: writers can edit content directly, changes saved to files on Apply, visual feedback (hover/focus states), helper hints (refs: docs/reference/cr_editor.md)
- [x] **Patch Review Agent Feedback & UX (Session 4)** - Agent feedback issues on apply/reject, sticky action buttons in header, smart default views (notifications->editor, list->diff), unified apply logic, path validation (refs: docs/reference/cr_editor.md)

### Milestone 7: The Unified Team Leader

- [ ] Implement Mandatory Assistant check (refs: docs/reference/cr_prefrontal_exocortex.md#exocortex-assistant-requirement, docs/reference/cr_agents.md#agent-team-leader, docs/reference/cr_assistant_assisted_mode.md)
- [ ] Code 10 = 5 x 2 Credit Logic (refs: docs/reference/cr_prefrontal_exocortex.md#exocortex-credits, docs/reference/cr_assistant_assisted_mode.md#assistant-credit-structure)
- [ ] Develop Planner Status Tagging system (refs: docs/reference/cr_memory.md#memory-roadmap-status-tags, docs/reference/cr_exocortex_roles_skills_tiers.md)
- [ ] Build Pacer/Assisted Mode state within Team Leader role (UI + persistence) (refs: docs/reference/cr_assistant_assisted_mode.md#assistant-workflow, docs/reference/cr_team_mode_workflow.md#team-mode-workflow)
- [x] Pacer concept merged into Team Leader (docs aligned) (refs: docs/reference/cr_exocortex_roles_skills_tiers.md#exocortex-assistant-pacer, docs/reference/cr_assistant_assisted_mode.md#assistant-overview)

### Medium Term

- [x] **Conference Mode** - Multi-agent discussion with cinema-view layout (refs: docs/reference/cr_workbench.md)
- [ ] **Memory Decay Lifecycle** - Active/archived/expired states with retention rules and compression hammer loop (refs: docs/reference/cr_librarian_extension.md, docs/reference/cr_memory.md)
- [ ] **AI Foundation (Read-Only)** - Summarize/Explain/Suggest tools (refs: docs/reference/cr_agents.md#agent-capabilities)

### Future

- [ ] **Agentic Editing** - AI-proposed patches with approval workflow (refs: docs/reference/cr_editor.md, docs/reference/cr_agents.md)
- [ ] **Memory Degradation** - 5-level interest gradient for issue memory (refs: docs/reference/cr_memory.md#memory-interest-levels)
- [ ] **Personal Tagging** - Agent-specific issue filtering (refs: docs/reference/cr_memory.md#memory-personal-tagging)
- [ ] **Export Stage** - PDF/EPUB/DOCX manuscript export (refs: docs/reference/cr_editor.md)

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

### Issues
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/issues` | List issues (filters: tag, assignedTo, status, priority) |
| GET | `/api/issues/{id}` | Get single issue |
| POST | `/api/issues` | Create issue |
| PUT | `/api/issues/{id}` | Update issue |
| DELETE | `/api/issues/{id}` | Delete issue |
| POST | `/api/issues/{id}/comments` | Add comment |

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
| POST | `/api/memory/decay` | Run decay/compression pass (archive/expire/prune) |
| GET | `/api/memory/decay/status` | Get scheduler interval, last run, and last results |
| PUT | `/api/memory/decay/config` | Update scheduler interval/thresholds/prune toggle |
| POST | `/api/memory/decay` (dryRun) | Get detailed report (archived/expired/prunable/locked, items list) |
| (env) | `CR_DECAY_DRY_RUN`, `CR_DECAY_PRUNE_R5`, `CR_DECAY_REPORT`, `CR_DECAY_INTERVAL_MINUTES`, `CR_DECAY_ARCHIVE_DAYS`, `CR_DECAY_EXPIRE_DAYS` | Scheduler defaults |

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
| POST | `/api/ai/chat` | Chat using an agent endpoint when `agentId` is supplied |

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
| POST | `/api/patches/{id}/apply` | Apply patch proposal |
| POST | `/api/patches/{id}/reject` | Reject patch proposal |
| DELETE | `/api/patches/{id}` | Delete patch proposal |
| POST | `/api/patches/cleanup` | Cleanup applied/rejected patches |
| POST | `/api/patches/simulate` | Create simulated patch proposal |
| GET | `/api/patches/{id}/audit` | Export patch audit log |
| GET | `/api/patches/audit/export` | Export audit logs for all patches |

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

