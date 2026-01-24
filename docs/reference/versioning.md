# Versioning & Snapshot History (Writer-Focused)

This spec defines a writer-first versioning system that mirrors familiar source control
concepts without exposing Git jargon. It is local-only for now.

## Goals
- Preserve a reliable, local history of project states.
- Keep writers in flow: autosave is silent and always on.
- Make explicit saves meaningful and named.
- Provide read-only history inspection with easy restoration per file.

## Terminology
- **Autosave (Working State):** Silent background saving of open files.
- **Changes List:** Files that differ from the last published snapshot (your last intentional save).
- **Publish to History:** Manual save + publish of current changes into history, with Chief of Staff notification.

## UX Surface
### Version Control panel
Triggered by the existing toolbar button (tooltip: "Manual Save & History"). This panel replaces
the file tree when active, similar to VS Code's Source Control panel.

Panel layout:
1. **Snapshot name input**
   - Placeholder: auto-generated name (see naming rules).
2. **Publish to History button**
   - Disabled when no changes.
   - Tooltip when disabled: "No changes to save."
3. **Changes list**
   - Shows modified/added/renamed/deleted files and folders.
   - Indicate unpublished changes in the file tree and tabs (dot/asterisk).
   - Context menu on each file:
     - "Discard changes and restore last published snapshot"
   - Panel action: "Discard all changes and restore last published snapshot for all changed files"
4. **Recent published snapshots**
   - List of published entries (latest first).
   - Hover tooltip shows files changed in that snapshot (optionally include word deltas).

### History access
- Context menu on file tree item: "View history"
- Editor tab action: "View history"
- Selecting history opens read-only split view:
  - Left: current file (editable)
  - Right: historical version (read-only)
  - History views are ephemeral and do not alter editor state.

### Workbench focus editor
- The Workbench focus editor modal is a primary publish surface.
- It uses the same publish workflow defined here.
- See docs/reference/workbench_editor.md.

## Naming Rules
Default snapshot name uses project name + timestamp:
- Format: `{project_name_normalized}_{YYYYMMDD_HHMM}`
- Example: `the_static_horizon_20260112_1405`
- If the user leaves the name blank, use the default.

Normalization:
- Lowercase
- Replace spaces and punctuation with `_`
- Collapse repeated `_`
- Trim leading/trailing `_`

Snapshot names are not required to be unique; snapshots are identified internally by ID.
If a default name collides within the same minute, append a suffix (`_2`, `_3`, ...).

## Autosave and Queue Behavior
- Autosave writes the working state without prompting.
- Any file that changes since the last published snapshot appears in the changes list.
- Closing a file does not remove it from the list.
- Show a summary in the panel (e.g., "5 files changed, +2,341 / -183 words since last publish").

## Publish Behavior
- "Publish to History" publishes the current changes as a single snapshot.
- When published, a notification issue is created for the Chief of Staff.
- After publish:
  - Changes list clears immediately.
 - Published snapshots may be referenced by agents as stable project states.

## Restore/Discard Behavior
- Per-file discard restores that file to the last published snapshot.
- Restore is explicit and read-only history prevents accidental edits.
- Restore should be labeled and framed as higher-stakes than standard undo.
- Safety note: restoration should be confirmed for open files or be immediately undoable.

## Retention and Cleanup
- Keep all published snapshots by default.
- Provide a "Cleanup" action:
  - Modal: "Keep last N snapshots (per project)"
  - Removes all but the last N published snapshots.
  - Cleanup permanently deletes snapshots and cannot be undone.

## Data Model (Conceptual)
- **Project**: `projectId`, `projectName`, `rootPath`
- **File**: `fileId`, `currentPath`, `status`
- **Published Snapshot**: `snapshotId`, `name`, `publishedAt`, `files[]`
- **File entry**: `fileId`, `path`, `status`, `contentHash`, `content`

## Implementation Notes
### Frontend module map
- `src/main/resources/public/app/versioning.js` - Panel UI, changes list, publish action, cleanup modal.
- `src/main/resources/public/app/history.js` - Read-only history viewer + split view wiring.
- `src/main/resources/public/api.js` - `versioningApi` endpoints.
- `src/main/resources/public/state.js` - `state.versioning` (status, history list, summaries).
- `src/main/resources/public/app/boot.js` - Init/versioning wiring.

### Backend module map
- `src/main/java/com/miniide/controllers/VersioningController.java`
- `src/main/java/com/miniide/VersioningService.java`
- `src/main/java/com/miniide/models/Snapshot.java`
- `src/main/java/com/miniide/models/SnapshotFile.java`
- Storage: `workspace/<project>/.control-room/history/`

## Open Questions (Future)
- Backups to cloud providers and restore flow.
- Delta storage vs full content snapshots.
- Optional diff viewer in the Version Control panel.
- Optional snapshot intent tags (draft/milestone/experiment/backup).
- Soft publish reminders when changes sit unpublished.
- Optional snapshot descriptions (collapsed by default, never required).
- Draft collision handling for simultaneous agent/human edits.

## Integrations (Future)
### Patch-only agent workflow
- Agents never write directly to working files; they submit patch proposals.
- When publishing, warn if pending patches touch the same files or lines.

### Draft collision resolution
- Soft-lock files when an agent is actively working on them.
- If human edits diverge from the agent’s base hash, the Moderator creates a decision issue.
- Resolution UI should compare "Your draft" vs "Agent proposal" in a diff modal.

### Restore impact framing
- Highlight that restore is higher-stakes than undo.
- Before confirming restore, show a brief impact summary (word delta + key changes).
- Optionally surface authorship trace using Librarian witness pointers.

### Chief of Staff snapshot loop
- On publish, Chief of Staff creates a structured summary of the diff.
- Published snapshots can surface these summaries in the history list.
