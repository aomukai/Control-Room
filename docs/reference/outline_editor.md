# Outline Editor (Story Root)

Scope note: Conceptual design; implementation details are intentionally unspecified.

## Goals
- Provide a dedicated Outline Editor modal for Story structure.
- Store outline data in a JSON file, edited only through the app.
- Emit a single Issue on accept/save, not per edit.
- Keep the outline rooted at Story root (single outline per project).

## Non-goals
- Multiple outlines per target.
- External editing support or sync.
- Mirroring outline data into scene files or metadata.

## UX Summary
- Story explorer shows a virtual file at the root: `SCN-outline.md`.
- Clicking the outline opens a modal editor (not the normal file editor).
- Modal is sticky: only closes via Accept or Cancel.
- Scene cards are listed in order.
- Each card shows a 1-sentence summary (fallback to scene title).
- Each card has Move Up / Move Down controls.
- Clicking a card opens an inline editor for its brief.

## Audit & Issues
- Accept triggers a single Issue with a diff-style summary:
  - Order changes (before/after list of scene IDs/titles).
  - Summary edits (old/new text per scene).
- Cancel discards all changes and emits no Issue.
- Any change within the modal remains local until Accept.

## Data Model (outline.json)
- Location: `.control-room/story/outline.json`
- One outline per project.
- Draft structure:
  - `id`: UUID
  - `title`: string (default "Story Outline")
  - `version`: integer (schema)
  - `createdAt`, `updatedAt`
  - `scenes`: ordered array:
    - `sceneId`: StoryScene.stableId
    - `summary`: string (1 sentence)

## Virtual File Behavior
- `SCN-outline.md` is virtual-only.
- Path maps to the Outline Editor modal.
- It should not open in the Monaco editor.

## Required Events
- Issue creation payload includes:
  - target: `outline`
  - action: `outline.update`
  - payload: `before`, `after`, `summaryChanges`

## Open Questions (deferred)
- Should we allow drag-and-drop reordering in addition to move buttons?
- Do we need per-scene status markers (e.g., draft/polished) within outline?
