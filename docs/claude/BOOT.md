# BOOT

## Quick Reference

**What we're building:**
A customizable dashboard widget system that transforms the Workbench from static cards into a living, breathing creative workspace.

**Key Features (v1):**
- Add/remove/reorder widgets via drag-and-drop
- Built-in widget library (Quick Notes, Writing Streak, Ambient Sound, etc.)
- Widget settings modal (per-widget configuration)
- Workspace-based persistence (each project has its own layout)
- Warmth slider (adjust color temperature)
- Custom widget API (users can create their own)
- Import/export widgets for sharing

**Architecture Decisions (Finalized):**
- Widget manifest + instance model (see Pre-Session 0 below)
- localStorage for Sessions 1-7, backend API from Session 8+
- 3 fixed sizes (small=1 col, medium=2 col, large=4 col)
- Manual refresh + optional periodic polling
- CSS Grid with responsive breakpoints

**Visual Aesthetic:**
- Match the soothing chat modal style (soft gradients, smooth animations)
- Subtle ambient effects (breathing gradients, optional particles)
- Writer-focused, warm, inviting atmosphere

---


## Core Concepts Vocabulary
- Workbench
- Dashboard
- Widgets
- Widget manifest
- Widget instance
- Widget picker modal
- Workspace
- Agents
- Issues
- Widget API
- Warmth slider

## Division of Labor (Models)
- Codex implements; Claude polishes UI/UX; keep Claude context low

## How to work with the repo

### 4. File Organization

```
src/main/resources/public/
  app.js                              # Main app (includes widget system)
  styles.css                          # Main styles (includes widget styles)

workspace/<projectName>/
  .control-room/
    dashboard-layout.json             # Dashboard layout (WidgetInstance[])
    widgets/                          # Custom widgets (future)
      my-custom-widget/
        manifest.json
        widget.html
        styles.css
        script.js
```

### 5. Persistence Strategy

**Phase 1 (Sessions 1-7): localStorage**
- Key: `dashboard-layout-${workspaceId}`
- Value: JSON.stringify(DashboardLayout)
- Pro: No backend changes needed
- Con: Doesn't persist across workspace switches properly

**Phase 2 (Session 8+): Backend API**
- Endpoint: `GET/PUT /api/dashboard/layout`
- Storage: `workspace/<name>/.control-room/dashboard-layout.json`
- Migration: On first load, import from localStorage if exists


## References
- Roadmap: `docs/cr_roadmap.md`
- References: `docs/agent_library.md`
- Workbench redesign ideas: `docs/workbench_redesign.txt`
- Prefrontal exocortex (credits system): `docs/reference/cr_prefrontal_exocortex.md`

---
