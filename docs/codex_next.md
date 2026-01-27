# Next Steps: Prep Phase Outline + Canon Notes

## Summary of findings (today)
- The outline modal uses `/api/outline` (OutlineController), which reads `outline.json` and only parses `outline.md` (virtual `Story/SCN-outline.md`) when empty.
- It does **not** read the scene registry (`.control-room/story/scenes.json`).
- During prep ingest, scenes **are** created in `scenes.json`, but no `SCN:outline` scene or `outline.md` is created.
- Result: the outline shows "No scenes in outline yet" during prep even though scenes exist.

## Desired behavior
- During the **prep phase**, both **Compendium** and **Story** should be fully editable.
- This is the phase where **retcons cannot exist yet**.
- Canon only becomes canon **after** prep is finished.

## Implementation ideas discussed
1) **Outline fallback to scenes registry during prep**
   - When outline is empty and prep is in draft, build outline scenes from `scenes.json`.
   - Outline becomes useful immediately after ingest.

2) **Prep writes an outline source**
   - During ingest, generate `SCN:outline` (or `outline.json`) from the created scenes.
   - Outline view then shows scenes directly from the prep output.

3) **Hybrid**
   - During prep: outline uses scenes registry.
   - After prep complete: outline persists to `outline.json` and becomes the authoritative source.

## Context for future changes
- The outline modal lives in `src/main/resources/public/app/editor.js`.
- Outline API is in `src/main/resources/public/api.js`.
- Outline controller: `src/main/java/com/miniide/controllers/OutlineController.java`.
- Prep ingest scene creation: `src/main/java/com/miniide/ProjectPreparationService.java`.
- Virtual prep file tree and scene registry access: `src/main/java/com/miniide/PreparedWorkspaceService.java`.
