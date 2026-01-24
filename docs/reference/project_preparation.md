# Project Preparation & Canon Ingestion
**Implementation Notes (v1)**

## Scope Notes (Non-Status)
This document defines the intended behavior and boundaries for project preparation and canon ingest.

---

## 1. Purpose

Project Preparation is a one-time ingest step that converts user material into
canonical internal state so agents can navigate Story and Compendium without
scanning large external files.

---

## 2. Definitions

- **Compendium**: Structured worldbuilding reference data for a project.
- **Canon Card**: An indexed Compendium entry (character, location, lore, etc).
- **Prepared**: Project state indicating canonical internal structure exists.
- **Ingest**: One-way import of user-provided material into canonical state.
- **Story**: Canonical representation of the manuscript (scenes list).

---

## 3. One-Way Ingest Contract (Scope)

During the wizard:
- User supplies manuscript and/or canon files.
- Files are read and converted into canonical state.
- Immutable evidence excerpts are stored under `.control-room/ingest/`.

After the wizard:
- Original files are not copied into the project folder.
- No re-import mechanism exists.
- Canonical truth lives under `.control-room/` only.
- Editor and Explorer operate on virtual documents in prepared mode.

---

## 4. Current Implementation Overview

### 4.1 Entry Points
The wizard exposes two modes:
- **Empty**: `POST /api/preparation/empty`
- **Ingest**: `POST /api/preparation/ingest`

### 4.2 Ingest Behavior
- Manuscript files become **Story scenes** (one file == one scene).
- Canon files become **Canon cards** (one file == one card).
- Card type is inferred from filename/content heuristics (no LLM annotation).
- Each scene/card stores an `ingestPointers` array linking to evidence excerpts.

### 4.3 Canon Indices
During ingest, two indices are written:
- `.control-room/canon/entities.json`
- `.control-room/canon/hooks-index.json`

These are generated from each card's title/aliases/headings. There is no
model-based annotation pass in v1.

### 4.4 Prepared State
- `markPrepared` sets `prepStage=draft`, `prepared=false`, `agentsUnlocked=false`.
- `finalizePreparation` sets `prepStage=prepared`, `prepared=true`, `agentsUnlocked=true`.
- Ingest sets `canon.manifest.status=draft`; confirm sets it to `prepared`.
- Empty mode sets canon status to `prepared` immediately.

### 4.5 Virtual File System (Prepared Mode)
Once prepared, the file tree is virtual-only:

```
Story/
  Scenes/
    SCN-<slug>.md
Compendium/
  Characters/
  Locations/
  Lore/
  Factions/
  Technology/
  Culture/
  Events/
  Themes/
  Glossary/
  Misc/
```

Virtual reads/writes map to:
- `Story/Scenes/...` -> `.control-room/story/scenes.json`
- `Compendium/...` -> `.control-room/canon/cards/*.json`

### 4.6 Scene Reindexing (Derived Metadata)
Endpoint:
- `POST /api/preparation/reindex/scene`

This is on-demand only. It hashes the scene content and uses
`hooks-index.json` to populate derived metadata on the `StoryScene` record:
- `lastIndexedHash`
- `indexStatus` (`ok`, `missing`, `error`)
- `linkedCardStableIds`
- `linkedHookIds`
- `hookMatches` (hook, stableId, matchType, confidence, offsets)

No scan occurs on save.

---

## 5. Data Models (Current)

### 5.1 Canon Card (JSON)
Key fields used by the implementation:
- `origin` (`ingest` | `native`)
- `stableId`
- `displayId` (e.g., `CHAR:seryn`)
- `type`
- `title`
- `aliases`
- `domains`
- `content`
- `canonHooks`
- `entities`
- `continuityCritical`
- `ingestPointers`
- `createdAt`, `updatedAt`
- `annotationStatus` (always `complete` in v1)
- `status` (active)

### 5.2 Story Scene (JSON)
Key fields used by the implementation:
- `origin` (`ingest` | `native`)
- `stableId`
- `displayId` (e.g., `SCN:opening`)
- `title`
- `chapterId` (unused)
- `order`
- `content`
- `ingestPointers`
- `createdAt`, `updatedAt`
- `status` (active)
- `lastIndexedHash`, `indexStatus`
- `linkedCardStableIds`, `linkedHookIds`
- `hookMatches`

---

## 6. Stable ID Rules (Implemented)

- **Ingest objects**: `stableId = hash(projectSalt + ingestId + excerptHash + anchorKey)`
- **Wizard native objects (empty mode)**: `stableId = UUID`
- **Post-wizard native objects**: `stableId = "ID-" + UUID` (created via prepared editor flows)

---

## 7. Storage Layout (Implemented)

```
.control-room/
  ingest/
    manifest.json
    excerpts/
      <sha256>.json

  canon/
    manifest.json
    entities.json
    hooks-index.json
    cards/
      CHAR-*.json
      LOC-*.json
      CONCEPT-*.json
      ...

  story/
    manifest.json
    scenes.json
    chapters.json   # empty list in v1
```

---

## 8. Agent Consumption (Scope)

- Continuity and Planner can read scene text and query canon indices.
- The reindex endpoint provides fast scene -> canon linkage without scanning
  all canon cards.
- Agents should not scan external files; all canonical truth is in `.control-room/`.

---

## 9. Out of Scope (Future)

- Scene segmentation beyond file boundaries.
- LLM-based annotation and Review Gate.
- Per-scene version history in canonical state.
- Retcon workflows and issue reference integration.
- Re-ingest or ongoing import.

---

## 10. Summary

Project Preparation is defined as a one-way ingest that builds canonical
Story + Compendium state under `.control-room/`, with virtual-only file access
in prepared mode and optional on-demand scene reindexing.
