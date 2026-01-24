# Control Room - Editor Mode Full Specification

<a id="editor-overview"></a>

> Complete, holistic document for the Editor workspace of Control Room.
> Standalone reference – no external context required.
> Designed so that each section can be turned into Claude Code prompts.

---
<a id="editor-purpose"></a>
## 1) Purpose & Philosophy

Editor Mode is the **hands-on writing environment** of Control Room.

- Workbench = planning, agents, notifications, decisions
- Editor = where text is read, created, revised, and patched
- Workbench Focus Editor = single-file review/edit modal with TTS and publish-only saves
  - See docs/reference/workbench_editor.md

Design goals:
- Fast & low-friction writing
- Patch-based AI revisions – no silent changes
- Clear `what just happened?` feedback via the Notification + Status layer
- Good performance even on very large files

---
<a id="editor-view-mode"></a>
## 2) View Mode Coexistence

Editor Mode now coexists with Workbench Mode via the global view mode system:

```javascript
state.viewMode.current  // 'editor' | 'workbench' | 'settings'
```

- **Unified Sidebar** provides mode toggle (Workbench/Editor) and all navigation
- No top bar - all controls consolidated into sidebar
- Sidebar shows mode-appropriate buttons:
  - **Editor**: Toggle Explorer, File System, Search, Versioning, Terminal
  - **Workbench**: Issues, Widgets, Patch Review
  - **Both**: Project Switcher, Dev Tools, Settings (in footer)
- Editor behavior is **unchanged** when active
- Switching to Workbench hides the Editor view; switching back restores it
- Monaco editor re-layouts automatically when returning to Editor mode
- The shared Issue Detail Modal can be opened from either view

---

<a id="editor-agent-selection"></a>
## 2.1) Agent Selection (Editor Chat)

- Editor chat header includes an agent selector dropdown
- Populated from the Agent Registry via `/api/agents`
- Selection is persisted in localStorage (`selected-agent-id`)
- The selected agent id is sent with chat payloads as `agentId`

---
<a id="editor-core-features"></a>
## 3) Core Feature Set

- Monaco editor with rich editing support
- File tabs (single logical instance per file)
- Workspace tree integration (open/rename/move/delete)
- Save + autosave (manual save preferred UX)
- Diff modal for AI-generated patches
- In-file search (Ctrl+F) and workspace search (Ctrl+Shift+F)
- Status bar + toast notifications (shared system)

---
<a id="editor-ui-layout"></a>
## 4) UI Layout

High-level layout for Editor Mode:

```text
┌──────────┬──────────────────────────────────────┐
│ Sidebar  │ Tabs + Editor Text Pane + Chat      │
│ [Toggle] │ (Monaco)                             │
│ Explorer │                                      │
│ FileTree │                                      │
│ Search   │                                      │
│ Version  │                                      │
│ Terminal │                                      │
│ ──────── │                                      │
│ Project  │                                      │
│ DevTools │                                      │
│ Settings │                                      │
├──────────┴──────────────────────────────────────┤
│ Status Bar (ready / saving / errors + 🔔 )     │
└─────────────────────────────────────────────────┘
```

Sidebar provides all navigation - no separate top bar needed.

---
<a id="editor-file-flow"></a>
## 5) File & Editing Flow

### 4.1 Opening Files

- Clicking a file in the tree:
  - If not open → creates a new tab and loads file contents
  - If already open → focuses the existing tab
- A given file path corresponds to **one logical editor instance**:
  - Changes in one tab are reflected anywhere that file is shown

### 4.2 Saving

- **Manual save** (Ctrl+S / Save button):
  - Writes through WorkspaceService
  - On success → `success` toast: `Saved <filename>`
  - Status bar shows `Saving…` → then `Saved`
- **Autosave** (optional, configurable):
  - Runs quietly in the background
  - Only emits notifications when a save fails

### 4.3 Closing Tabs

- Closing a clean (non-dirty) tab simply removes it
- Closing a **dirty** tab shows confirmation dialog:
  - `Save` → save then close
  - `Discard` → close and drop changes
  - `Cancel` → keep tab open
- If user chooses **Discard**, emit a `warning` toast:
  - `Changes discarded in <filename>`

### 4.4 Dirty State

- Tab label shows a marker (e.g. •) when dirty
- Status bar may briefly reflect `Dirty` state
- Dirty state resets after successful save

---
<a id="editor-search"></a>
## 6) Search

### 5.1 In-File Search (Ctrl+F)

- Uses Monaco's built-in find widget
- No notifications on normal success
- If **no matches** are found:
  - Emit `info` toast: `No results for "<term>" in this file`

### 5.2 Workspace Search (Ctrl+Shift+F)

- Opens search panel (can be modal or right pane)
- Shows list of matching files + line snippets
- Behavior:
  - While searching: status bar → `Searching for "<term>" in workspace…`
  - On completion with matches: optional `info` toast with counts
  - On **0 matches**: `info` toast: `No results for "<term>" in workspace`

---
<a id="editor-notifications"></a>
## 7) Notification Integration (Editor Side)

Editor Mode is a major producer of events for the global notification system.

### 6.1 Events Emitted from Editor

- Save success / failure
- Autosave failure
- Discarded changes
- Workspace search complete / 0 results
- File missing on disk when trying to save
- Endpoint/AI error when running an AI action from the editor

Each of these becomes a `Notification` object with:
- `scope = "editor"`
- Appropriate `level` (`success` / `info` / `warning` / `error`)

Use NotificationStore helpers where possible:
```
notificationStore.editorSaveSuccess(filePath)
notificationStore.editorSaveFailure(filePath, details)
notificationStore.editorDiscardWarning(filePath)
notificationStore.editorSearchNoResults(term, workspace)
```

Frontend consumes notifications through ToastStack, StatusBar, and NotificationCenter.

Behavior notes:
- Non-persistent notifications vanish after timeout
- Persistent + blocking errors remain visible
- Clicking a toast marks it read and runs `actionPayload` when present
- Notification Center list is newest-first
- Status bar shows a summary if blocking errors exist

### 6.2 Status Bar Behavior

- Normal idle: `Ready`
- On save: `Saving…` then `Saved`
- On long-running tasks (search, AI call):
  - `Running search…` / `Contacting AI…`
- On error:
  - `⛔ Save failed (click for details)` or similar
  - Clicking opens Notification Center focused on the error
- Right side: bell icon `🔔` with unread notification count

### 6.3 Notification Center Usage

- Shared right slide-in panel from the global system
- Editor mode usually filters to `scope = "editor"` by default
- Each entry can be expanded to show:
  - Details (e.g. stack snippet or error message)
  - Buttons: `Retry`, `Open file`, `Open logs`

### 6.4 NotificationStore API (Editor Scope)

Core:
- `push(level, scope, message, details?, category?, persistent?, actionLabel?, actionPayload?, source?)`
- `info(message, scope)` / `success(message, scope)` / `warning(message, scope)` / `error(message, scope, blocking)`

Editor helpers:
- `editorSaveSuccess(filePath)`
- `editorSaveFailure(filePath, details)`
- `editorDiscardWarning(filePath)`
- `editorSearchNoResults(term, workspace)`

Query + state:
- `getAll()` (newest-first)
- `getByLevelAndScope(levels, scopes)`
- `getUnreadCount()` / `getUnreadCountByScope(scope)`
- `markRead(id)` / `markAllRead()`
- `clearNonErrors()` / `clearAll()`

UI usage:
- Toast stack: `info/success/warning/error` helpers
- Status bar: `getUnreadCount()` + blocking errors
- Notification Center: `getByLevelAndScope(...)`, `markRead(...)`, `read` state

---
<a id="editor-patch-review"></a>
## 8) Diff Modal - Patch Review

Central component for approving AI-generated edits.

### 7.1 Layout

```text
┌─────────────────────────────────────────┐
│  Original Text      |   Proposed Text   │
│  (left pane)        |   (right pane)    │
│  with highlights    |   with highlights │
└─────────────────────────────────────────┘
```

- Resizable vertical divider
- Scroll synchronization optional (toggle)
- Highlight insertions, deletions, replacements

### 7.2 Actions

- **Apply All** – accept entire patch
- **Apply Selected** – accept only selected blocks/hunks
- **Reject** – discard patch (but keep a record in logs)
- **Revise & Retry** – open a small text area where the user can give
  feedback ("more subtle", "keep original voice"), then rerun the AI

### 7.3 Data Flow

1. AI tools (Writer/Editor agents) call WorkspaceService to produce a patch
2. Patch is surfaced as a notification & newsfeed item
3. User clicks → Diff Modal opens with original vs proposed
4. On Apply:
   - WorkspaceService applies patch to file
   - Editor reloads updated content
   - Notification emitted: `notificationStore.success("Applied patch to <filename>", "editor")`

---
<a id="editor-ai-interaction"></a>
## 9) AI Interaction from Editor

These behaviors depend on Milestones 6–7 but are defined here:

### 8.1 Available Actions (per selection)

- **Summarize** – short summary of selected text or current scene
- **Explain** – explanation or breakdown of what the text does
- **Rewrite** – rewrite in-place, obeying constraints (tone, POV, tense)
- **Polish** – lighter stylistic cleanup
- **Continue** – generate continuation after cursor

All actions:
- Are initiated by explicit user click/shortcut
- Produce patch proposals, not direct edits
- Feed into Diff Modal for approval

### 8.2 Error Handling

If an AI request fails (endpoint offline, timeout, invalid response):
- Emit `error` notification from `source = "AI"` with `scope = "editor"`
- Show summary in status bar
- Optionally include `Retry` action button

---
<a id="editor-styling"></a>
## 10) Styling & UX Notes

- Support dark and light themes
- Monospace font selection (e.g. Fira Code, JetBrains Mono)
- Reasonable defaults:
  - Word wrap ON
  - Line numbers ON
  - Soft line height for readability
- Keyboard shortcuts clearly documented somewhere (help overlay)

---
<a id="editor-performance"></a>
## 11) Performance & Large Files

- Lazy load very long files to avoid UI blocking
- Use internal segmentation (from WorkspaceService) to:
  - Jump by scene
  - Restrict AI context to smaller chunks
- Consider read-only mode for extremely large files with warning

---
<a id="editor-implementation-hooks"></a>
## 12) Implementation Prompt Hooks

This spec is structured so we can easily carve out Claude Code prompts, e.g.:

1. **Status Bar Component**
   - Implement status bar with left status text and right 🔔 icon
   - Wire it to NotificationStore and editor state

2. **Toast UI + Notification Wiring**
   - Implement toast stack component
   - Subscribe to NotificationStore

3. **Diff Modal**
   - Implement diff viewer with side-by-side panes and actions described

4. **Workspace Search Panel**
   - Implement Ctrl+Shift+F search panel and result list

5. **AI Action Bar**
   - Implement buttons and tool calls for Summarize / Explain / Rewrite / Continue

Each sub-section can be copy-pasted as requirements directly into Claude Code.

---
<a id="editor-final-outcome"></a>
## 13) Final Outcome

With this Editor Mode:
- Writers get a stable, comfortable text surface
- AI suggestions remain transparent and fully under user control
- Notifications clearly answer "what just happened?"
- The Workbench and Editor together act as a complete writing + planning cockpit.

