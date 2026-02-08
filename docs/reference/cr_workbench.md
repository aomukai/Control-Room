# Control Room - Workbench

<a id="workbench-overview"></a>
Indexed in docs/agent_library.md

> Specification and scope notes (status lives in roadmap.md).

---

<a id="workbench-overview-section"></a>
## Overview

The Workbench is the **strategic layer** of Control Room - a space for planning, agent collaboration, and project oversight. While the Editor is the hands-on writing surface, the Workbench serves as the command center for managing agents, tracking issues, and monitoring project activity.

Control Room operates on one active workspace at a time. A workspace corresponds to a folder under `workspace/<workspaceName>`, and the Workbench reflects the agents, issues, and notifications for the currently selected workspace.

**Metaphor:** Workbench = Writer's Room / Command Center

---

<a id="workbench-current-implementation"></a>
## Current Implementation

<a id="workbench-view-mode"></a>
### View Mode System

The application supports three top-level views controlled by `state.viewMode`:

```
+------------------------------------------------------+
| Sidebar  | View-specific content                     |
| [Toggle] |                                            |
| Issues   | (Workbench: Agents/Issues/Newsfeed)        |
| Widgets  | (Editor: Monaco + tabs + chat)             |
| Patch    | (Settings: Configuration panels)           |
| -------- |                                            |
| Project  |                                            |
| DevTools |                                            |
| Settings |                                            |
+------------------------------------------------------+
```

- **Workbench** - Strategic planning and agent oversight
- **Editor** - Text editing and file management (default)
- **Settings** - Configuration

**Unified Sidebar Architecture:**
- No separate top bar - all navigation in sidebar
- Mode-specific buttons shown/hidden dynamically:
  - **Editor only**: Toggle Explorer, File System, Search, Versioning, Terminal
  - **Workbench only**: Issues, Widgets, Patch Review
  - **Always visible**: Project Switcher, Dev Tools, Settings (footer)
- Switching views is handled by `setViewMode(mode)` which updates DOM visibility and triggers view-specific rendering
- `updateModeControls(mode)` shows/hides appropriate sidebar buttons

---

<a id="workbench-workspace-switching"></a>
### Workspace Switching

- Sidebar footer button (with server icon) shows the current workspace name and opens the Switch Workspace modal
- Modal includes a dropdown of existing workspaces and a text field to type a new name
- `Set & Restart` persists the selection and restarts the app to load the chosen workspace

---

<a id="workbench-layout"></a>
### Workbench Layout (3-Panel)

```
+--------------------------------------------------------------+
| Agent Sidebar | Issue Board                      | Newsfeed  |
| (220px)       | (flex)                           | (280px)   |
| - Planner     | - Filter by status/priority      | - Filtered|
| - Writer      | - Issue cards                    |   notices |
| - Editor      | - Click -> Issue Modal           | - Events  |
| - Critic      |                                  |           |
| - Continuity  |                                  |           |
+--------------------------------------------------------------+
```

<a id="workbench-agent-sidebar"></a>
#### Agent Sidebar
- Populated from the dynamic Agent Registry (enabled agents only)
- Each agent shows avatar/initial, name, role, and status indicator
- Click to select (visual feedback, logging only for now)
- Right-click context menu: invite to conference/chat, agent profile, role settings, agent settings, change role, export, duplicate, retire
- Agent Settings modal supports provider/model/key configuration per agent
- Status lamp: green = ready, yellow = unreachable or missing model, red = unconfigured, spinner while checking
- Drag-and-drop to reorder agents (persists order)
- Retired Agents modal lists disabled agents and allows reactivation

<a id="workbench-issue-board"></a>
#### Issue Board (Center Pane)
- Card-based list of all issues from the backend
- **Header**: Title + refresh button
- **Filters**: Status (All/Open/Closed/Waiting) and Priority (All/Urgent/High/Normal/Low)
- **Stats**: Shows total count and open count
- **Issue Cards** display:
  - Issue ID and status pill
  - Title (prominent)
  - Priority labels (urgent, high, normal, low)
  - Assignee, comment count, relative timestamp
  - Tags (first 2, then "+N more")
- Click any card -> opens the **shared Issue Detail Modal**
- States: Loading spinner, error with retry, empty state

<a id="workbench-newsfeed"></a>
#### Newsfeed Panel
- Real-time feed from `NotificationStore`
- Filters to show:
  - Notifications with `scope: "workbench"`
  - Notifications with `source: "issues"`
  - Notifications with `actionPayload.kind: "openIssue"`
- Limited to 20 most recent items
- Click behavior:
  - Marks notification as read
  - If issue-related, opens the **shared Issue Detail Modal**

---

<a id="workbench-shared-issue-modal"></a>
## Shared Issue Detail Modal

A single, globally-accessible modal for viewing issue details. Used consistently across the application:

- From Issue Board (click any card)
- From Workbench Newsfeed
- From NotificationCenter
- From Toasts with issue actions

### Opening the Modal

```javascript
openIssueModal(issueId)  // Opens modal and fetches issue data
closeIssueModal()        // Closes modal and cleans up
```

### Modal Content

- **Header**: Issue ID, title, status pill, author/assignee
- **Meta Section**: Tags, priority, timestamps
- **Body Section**: Issue description
- **Comments Section**: Threaded comments with action badges
- **Footer**: Placeholder action buttons (Add Comment, Close Issue)

### States

- **Loading**: Spinner while fetching from `/api/issues/{id}`
- **Error**: Human-readable error message
- **Loaded**: Full issue details with comments

---

<a id="workbench-state"></a>
## State Management

```javascript
state.viewMode = {
  current: 'editor'  // 'editor' | 'workbench' | 'settings'
}

state.issueModal = {
  isOpen: false,
  issueId: null,
  isLoading: false,
  error: null,
  issue: null
}

state.issueBoard = {
  issues: [],
  isLoading: false,
  error: null,
  filters: {
    status: 'all',    // 'all' | 'open' | 'closed' | 'waiting-on-user'
    priority: 'all'   // 'all' | 'urgent' | 'high' | 'normal' | 'low'
  }
}
```

---

<a id="workbench-key-functions"></a>
## Key Functions

| Function | Purpose |
|----------|---------|
| `setViewMode(mode)` | Switch between Workbench/Editor/Settings |
| `loadAgents()` | Load agent registry into Workbench sidebar |
| `isWorkbenchView()` | Check if currently in Workbench mode |
| `renderWorkbenchView()` | Render all three panels |
| `renderIssueBoard()` | Render Issue Board structure and load issues |
| `loadIssues()` | Fetch issues from API with current filters |
| `createIssueCard(issue)` | Create DOM element for a single issue card |
| `renderWorkbenchNewsfeed()` | Populate newsfeed from notifications |
| `openIssueModal(id)` | Open shared issue modal |

Issue memory access hooks:
- Opening the issue modal records access for the selected memory agent.
- Switching the memory agent, refreshing memory, or posting a comment also records access.

---

<a id="workbench-todo"></a>
## Scope Notes (Non-Status)

This section outlines intended scope boundaries, without implying current implementation status.

### Near-Term Scope
- **Agent Chat Wiring** - Connect agent sidebar to chat functionality
- **Agent Context Actions** - Wire remaining menu actions to real flows
- **Global Settings UI** - Manage keys, security mode, and provider defaults
- **Issue Creation UI** - Create issues from Workbench
- **Quick Actions** - Close/reopen issues from board without opening modal

### Near-Term Scope
- **Conference Two-Phase Model** - Chief-led tool orchestration (phase 1) + role-based interpretation with abstain (phase 2). Per-round issue archival, history wipe between rounds, continuous UI for the user. Chief auto-invited and non-removable. Agent cards show conference state: yellow "Abstained", red "Muted". See `docs/statemachine.md` (Conference Round Lifecycle) for full protocol.

### Future Scope
- **Agent Status** - Online/thinking/idle indicators (beyond availability lamp)
- **Patch Review** - View and approve agent patches

---

<a id="workbench-css"></a>
## CSS Classes

Key styling classes for Workbench components:

- `#workbench-view` - Main container
- `#workbench-layout` - 3-column flex layout
- `#workbench-agent-sidebar` - Left panel
- `#workbench-chat-pane` - Center panel (Issue Board)
- `#workbench-newsfeed` - Right panel
- `.agent-item` - Individual agent entry
- `.issue-board` - Issue Board container
- `.issue-card` - Individual issue card
- `.newsfeed-item` - Individual notification entry
- `.issue-modal-overlay` - Modal backdrop
- `.issue-modal` - Modal container
