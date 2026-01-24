# Workbench Editor (Focus Mode)

This doc defines the Workbench editor modal used for focused editing and review.
It is distinct from the main IDE editor (see cr_editor.md).

## Purpose
- Provide a distraction-free, single-file editing experience.
- Support quick review with TTS playback.
- Route saves through the Version Control "Publish to History" workflow.

## Entry Points
- Recent Files widget: click a file to open in the Workbench editor modal.
- File tree context menu: "Open in Focus Editor" (optional).

## UI/Behavior
- **Cinema mode**: darkened + blurred surroundings; modal floats above Workbench.
- **Single file only**: no tabs, one file per session.
- **No autosave**: edits are ephemeral until the user explicitly publishes.
- **Explicit save copy**: show a persistent hint like "Not saved until you publish."
- **Publish to History**: primary action; disabled when no changes.
- **Discard changes**: restores to last published snapshot.
- **Open in main editor**: escape hatch to full IDE editor; transfers dirty state.
 - **Dirty definition**: dirty = differs from the last loaded baseline (even if unpublished).
 - **No file locking**: conflicts are resolved at publish time via versioning.
 - **Close with unsaved changes**: warn and allow Publish / Discard / Cancel.

## TTS (Focus)
- One-button TTS toggle (play/pause).
- Voice selection comes from Settings (TTS tab).
- No per-session TTS configuration in the modal.
- Read from cursor or selection (default: cursor if active selection is empty).

## Versioning Integration
- Uses Version Control snapshot naming defaults.
- Publish creates a snapshot and notifies the Chief of Staff.
- Focus Mode publishes the active file only; warn if other files have unpublished changes.
- Shows a summary line: "Last published: {time} · +X / -Y words".
- Publish also creates an Issue containing the diff (or a link to it).
- If publish fails, keep the modal open with changes intact and show error feedback.
 - Pre-flight collision check: if the file changed on disk since open, warn and offer
   "Overwrite with this draft" or "Open in main editor to resolve".

## Data Source
- Reuse the main editor file load/save API as the source of truth.

## Visual Notes
- Keep typography plain-text oriented; no rich formatting.
- Minimal toolbar: Publish, Discard, TTS, Open in main editor, Close.

## Cross-References
- Versioning spec: docs/reference/versioning.md
- IDE editor spec: docs/reference/cr_editor.md
