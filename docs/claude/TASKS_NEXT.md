# TASKS_NEXT

## Current Focus
**Workbench Widget System** - Design and implement a customizable dashboard widget system that transforms the Workbench into a true writer's creative home base.

### Core Goals
- Make the Workbench feel inviting, warm, and productive
- Support customizable layouts via drag-and-drop widgets
- Enable users to create and share custom widgets (like agents)
- Provide rich, writer-focused widgets (writing streaks, mood boards, ambient sounds, etc.)
- Maintain the soothing, creative aesthetic established in the 1:1 chat modal

### What's Ready
- Current dashboard has 4 prototype widgets: Planner Briefing, Issue Pulse, Credits Leaderboard, Team Activity
- Layout uses CSS Grid with card-based design
- Agent sidebar shows avatars, roles, and availability status
- Right sidebar has newsfeed (notifications)

### Next Steps
1. **Widget System Architecture** (spec below)
2. **Built-in Widget Library** - expand current widgets and add new ones
3. **Widget Builder UI** - add/remove/arrange interface
4. **Custom Widget API** - let users create their own
5. **Visual Polish** - ambient effects, micro-interactions, themed personalities


## Open Questions (Remaining)

1. **Widget Library Location**: Workspace folder (per-project) or global app folder (shared across projects)?
   - **Proposal**: Both! Built-in widgets are global, custom widgets can be workspace-specific OR global (user chooses on import).

2. **Network Permissions**: Allow widgets to fetch external data? (Spotify embeds, YouTube, weather, quotes)
   - **Answer**: Yes! Sound widget needs this. Make permissions explicit and user-approved.

3. **Click-to-Enlarge**: Should clicking a widget expand it temporarily (modal overlay) or just highlight it?
   - **Proposal**: Modal overlay with dimmed background (cinema mode), ESC to close. Widget renders larger version of itself.

---


## 10. Implementation Phases

### Phase 1: Foundation
- [ ] Widget base class and lifecycle
- [ ] Grid layout with responsive breakpoints
- [ ] Widget picker modal UI
- [ ] Add/remove widget functionality
- [ ] Basic persistence (save/load layout)

### Phase 2: Core Widgets
- [ ] Enhance existing 4 widgets (Briefing, Pulse, Leaderboard, Activity)
- [ ] Implement Quick Notes widget
- [ ] Implement Writing Streak widget
- [ ] Implement Ambient Sound Player widget

### Phase 3: Advanced Features
- [ ] Drag-and-drop reordering
- [ ] Widget settings modal
- [ ] Size change (small/medium/large toggle)
- [ ] Custom widget API sandbox
- [ ] Import/export custom widgets

### Phase 4: Polish
- [ ] Ambient background effects
- [ ] Micro-interactions and animations
- [ ] Personality in empty states
- [ ] Warmth slider integration
- [ ] Performance optimization

---


# Implementation Plan: From Spec to Code

This section breaks down the widget system into concrete, actionable coding sessions. Each session is designed to be self-contained and deliverable within your rate limits.

---

## Session Planning Strategy

**Guiding Principles:**
- Each session delivers a visible, testable increment
- No half-finished features that leave you blocked
- Early sessions focus on visual results to build momentum
- Complex architectural work comes after we have working examples
- Always save state that can be resumed if rate-limited

**Session Structure:**
1. **Quick Win** (~15-20 min): Get something visible on screen
2. **Core Work** (~30-45 min): Main implementation
3. **Polish** (~10-15 min): Smooth edges, add personality
4. **Commit Point**: Working state, ready to stop if needed

---

## Pre-Session 0: Current State Audit (Planning Only - Today)

**Goal:** Understand exactly what we have and what we need.

**Tasks:**
- [x] Review current dashboard code ([app.js:3336-3492](src/main/resources/public/app.js))
- [x] Review current dashboard CSS ([styles.css:5452+](src/main/resources/public/styles.css))
- [x] Review chat modal aesthetic as reference ([app.js:8625+](src/main/resources/public/app.js), [styles.css:3352+](src/main/resources/public/styles.css))
- [x] Document current widget cards (Planner Briefing, Issue Pulse, Credits, Team Activity)
- [x] Sketch widget data model
- [x] Define widget registry structure
- [x] Plan persistence strategy (workspace/.control-room/dashboard-layout.json)
- [x] Identify CSS variables we'll need for theming


## Next Session Notes

**Next Step:** Session 11 (Custom Widget API) or polish pass
**Next Session:** Session 2 - Convert existing dashboard widgets OR add new built-in widgets
**Next Session:** Session 3 - Drag-and-drop reordering OR add new creative widgets

## Session 3: Drag-and-Drop Widget Reordering (PLANNED)

**Goal:** Let users reorder widgets by dragging them.

**Quick Win (15 min):**
- [ ] Create widget picker modal shell (reuse createModalShell)
- [ ] Add "Add Widget" button to dashboard header
- [ ] Display grid of 2-3 available widgets (Quick Notes + 2 placeholders)

**Core Work (35 min):**
- [ ] Implement widget card previews in picker:
  - Icon/emoji
  - Name and description
  - "Add" button
- [ ] Wire "Add" button to add widget to dashboard
- [ ] Implement dashboard state management:
  - Active widgets array
  - Widget instance tracking (allow multiple Quick Notes)
- [ ] Persist dashboard state to localStorage
- [ ] Implement remove button on widgets (with confirmation)

**Polish (10 min):**
- [ ] Picker modal animations (slide up from bottom)
- [ ] Widget card hover effects in picker
- [ ] Add widget appears at bottom with slide-in animation
- [ ] "Are you sure?" confirmation for remove

**Deliverable:**
- Full add/remove widget flow
- Multiple instances of same widget supported
- State persists across page reloads

**Commit Message:** `feat: widget picker modal and add/remove flow`

---


## Session 3: Enhance Existing Widgets (Planner Briefing, Issue Pulse)

**Goal:** Convert existing dashboard cards to proper widgets.

**Quick Win (10 min):**
- [ ] Wrap existing Planner Briefing in Widget class
- [ ] Add to widget registry
- [ ] Verify it renders identically

**Core Work (40 min):**
- [ ] Convert Issue Pulse to widget:
  - Extract from hardcoded HTML
  - Add refresh button
  - Add settings: time range filter (24h/7d/30d)
- [ ] Add Credits Leaderboard widget (placeholder → real):
  - Mock data structure for now
  - Top 5 agents by credits
  - Visual bars showing relative performance
- [ ] Add Team Activity widget (placeholder → real):
  - Show last 10 notifications from agent system
  - Filter by agent activity only
  - Timestamp formatting

**Polish (10 min):**
- [ ] Consistent widget chrome (all have settings gear + remove)
- [ ] Loading states for data-driven widgets
- [ ] Error states with retry button
- [ ] Empty states with helpful messages

**Deliverable:**
- All 4 current widgets are proper Widget instances
- Consistent UI patterns across all widgets
- Settings modal for Issue Pulse proves settings pattern

**Commit Message:** `feat: converted dashboard cards to proper widgets`

---


## Session 4: Widget Settings Modal Framework

**Goal:** Create reusable settings modal that any widget can use.

**Quick Win (15 min):**
- [ ] Create `WidgetSettings` modal shell
- [ ] Wire settings gear button to open modal
- [ ] Display widget name and icon in modal header

**Core Work (35 min):**
- [ ] Implement settings field types:
  - Text input
  - Number input
  - Select dropdown
  - Checkbox
  - Radio button group
- [ ] Render settings from widget manifest:
  ```js
  widget.manifest.settings = {
    timeRange: { type: 'select', label: 'Time Range', options: [...] },
    refreshInterval: { type: 'number', label: 'Refresh (seconds)', min: 30 }
  }
  ```
- [ ] Save settings to widget instance state
- [ ] Trigger widget.update(newSettings) on save
- [ ] Add size selector (small/medium/large) in settings

**Polish (10 min):**
- [ ] Unsaved changes warning on close
- [ ] Keyboard shortcuts (Escape to cancel, Ctrl+Enter to save)
- [ ] Focus management (auto-focus first field)
- [ ] Settings groups with visual dividers

**Deliverable:**
- Universal settings modal
- Any widget can define settings declaratively
- Size changes work and persist

**Commit Message:** `feat: universal widget settings modal with size selector`

---


## Session 5: Grid Layout + Drag-and-Drop Reordering

**Goal:** Make widgets rearrangeable via drag-and-drop.

**Quick Win (20 min):**
- [ ] Implement CSS Grid properly:
  ```css
  .workbench-dashboard {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    grid-auto-rows: minmax(200px, auto);
    gap: 16px;
  }
  ```
- [ ] Widget sizes span columns:
  - small: 1 column
  - medium: 2 columns
  - large: 4 columns
- [ ] Test with mixed widget sizes

**Core Work (30 min):**
- [ ] Add HTML5 drag-and-drop:
  - Drag handle (⋮⋮) in widget header
  - Draggable attribute on widgets
  - Drop zone highlighting
- [ ] Implement reorder logic:
  - Track widget positions (0, 1, 2, ...)
  - Reorder array on drop
  - Re-render dashboard
  - Persist new order
- [ ] Visual feedback during drag:
  - Semi-transparent ghost
  - Drop zones highlighted
  - Smooth transitions

**Polish (10 min):**
- [ ] Snap-to-grid animation after drop
- [ ] Drag handle only shows on hover
- [ ] Cursor changes to grab/grabbing
- [ ] Prevent drag on settings/remove buttons

**Deliverable:**
- Fully functional drag-and-drop
- Widget order persists
- Smooth, polished interactions

**Commit Message:** `feat: drag-and-drop widget reordering with grid layout`

---


## Session 6: New Widget - Writing Streak (Calendar Heatmap)

**Goal:** Implement a visually compelling, data-driven widget.

**Quick Win (10 min):**
- [ ] Create Writing Streak widget stub
- [ ] Add to widget registry
- [ ] Render placeholder grid (30 days)

**Core Work (40 min):**
- [ ] Implement calendar heatmap:
  - Grid of cells (one per day)
  - Color intensity based on activity (0-4 levels)
  - Tooltip showing date and count on hover
- [ ] Data collection strategy:
  - For now: track editor saves by date
  - Store in localStorage: `{ "2026-01-07": 3500 }` (words)
- [ ] Settings:
  - Metric selector (words written / time spent / commits)
  - Time range (7d / 30d / 90d / 365d)
- [ ] Visual design:
  - GitHub-style heatmap
  - Color ramp from dark to bright accent
  - Days of week labels on Y-axis
  - Month labels on X-axis

**Polish (10 min):**
- [ ] Smooth color transitions
- [ ] Tooltip animations
- [ ] Current day highlighted with border
- [ ] "Today" indicator
- [ ] Streak counter (consecutive days)

**Deliverable:**
- Beautiful, motivating streak visualization
- Proves complex widget data flow
- Settings integration tested

**Commit Message:** `feat: writing streak widget with calendar heatmap`

---


## Session 7: New Widget - Sound & Music Player

**Goal:** Add a creative, atmospheric widget (tests audio + external resources). Make the workbench feel like YOUR creative space.

**Design Note:** This is a personal workspace feature, not just productivity! Think: "lofi beats to write to" or "rain sounds for focus."

**Quick Win (15 min):**
- [ ] Create Sound & Music widget
- [ ] Embed YouTube iframe (for lofi streams)
- [ ] Add play/pause button

**Core Work (30 min):**
- [ ] Implement preset sources:
  - **YouTube Music/Playlists**: Popular lofi stream ("lofi hip hop radio 📚"), study playlists
  - **Spotify Embeds**: User can paste playlist URL
  - **Ambient Sounds**: Rain, café, forest, ocean, fireplace (via YouTube or direct audio)
  - **Custom URL**: User can add their own sources
- [ ] Player controls:
  - Source selector dropdown (preset or custom)
  - Play/pause toggle
  - Volume slider
  - Mute toggle
  - "Open in new tab" link (for full player)
- [ ] State persistence:
  - Remember last played source
  - Remember volume level
  - Remember if was playing (optional auto-resume)

**Polish (15 min):**
- [ ] Visualizer animation (simple bars synced to audio if possible, or pulsing icon)
- [ ] "Now playing" indicator with song/stream name
- [ ] Smooth fade in/out on play/pause
- [ ] Click-to-enlarge: Opens modal with bigger player + playlist queue (if YouTube/Spotify)
- [ ] Network permission banner (first time): "This widget will connect to YouTube/Spotify"

**Deliverable:**
- Functional sound/music player with YouTube/Spotify embeds
- Proves widget can handle external media sources
- Adds creative atmosphere to workspace
- Tests network permissions flow

**Commit Message:** `feat: sound & music player widget with YouTube/Spotify support`

**Implementation Notes:**
- YouTube IFrame API: `https://www.youtube.com/embed/{videoId}?autoplay=1&mute=0`
- Spotify Web Player: `https://open.spotify.com/embed/playlist/{playlistId}`
- For "lofi beats" default: Use the 24/7 lofi girl stream (video ID: `jfKfPfyJRdk`)
- Volume control: Use iframe postMessage API for YouTube, Spotify has volume in embed
- Click-to-enlarge: Render larger iframe in modal, maintain playback state

---


## Session 8: Widget Persistence + Workspace Integration

**Goal:** Move from localStorage to proper workspace-based persistence.

**Quick Win (15 min):**
- [ ] Create backend endpoint: `GET/PUT /api/dashboard/layout`
- [ ] Load current localStorage state
- [ ] Save to workspace/.control-room/dashboard-layout.json

**Core Work (35 min):**
- [ ] Implement backend persistence:
  ```json
  {
    "workspaceId": "my-novel",
    "columns": 4,
    "widgets": [
      {
        "widgetId": "widget-quick-notes",
        "instanceId": "notes-1",
        "position": 0,
        "size": "medium",
        "settings": { "content": "..." }
      }
    ]
  }
  ```
- [ ] Frontend: switch from localStorage to API
- [ ] Migration: import localStorage layouts on first load
- [ ] Per-workspace layouts (switching workspaces loads different dashboard)

**Polish (10 min):**
- [ ] Loading state while fetching layout
- [ ] Error handling (fallback to default layout)
- [ ] "Reset to default" button in dashboard header
- [ ] Confirmation before reset

**Deliverable:**
- Workspace-aware dashboard layouts
- Backend persistence working
- Multi-workspace support proven

**Commit Message:** `feat: workspace-based dashboard persistence with API`

---


## Session 9: Visual Polish - Ambient Effects + Animations

**Goal:** Make the dashboard feel alive and inviting.

**Quick Win (10 min):**
- [ ] Add CSS variables for theming:
  ```css
  --warmth-hue-shift: 0deg;
  --accent-color: hsl(calc(210 + var(--warmth-hue-shift)), 60%, 50%);
  ```
- [ ] Apply to existing widgets

**Core Work (40 min):**
- [ ] Ambient background effects:
  - Subtle gradient animation (breathing effect)
  - Optional: particle overlay (dust motes, snow, embers)
  - Canvas or CSS animation (performance test both)
- [ ] Widget micro-interactions:
  - Mount: fade + scale up from 0.95
  - Unmount: fade + scale down to 0.95
  - Hover: subtle lift (translateY -2px) + shadow
  - Settings open: slide from widget position
- [ ] Empty state personality:
  - When dashboard is empty: welcoming message
  - Widget-specific empty states with helpful hints
  - Animated illustrations or icons

**Polish (10 min):**
- [ ] Transition timing curves (ease-out for natural feel)
- [ ] Stagger animations when multiple widgets mount
- [ ] Reduce motion support (prefers-reduced-motion media query)
- [ ] Performance check (60fps on dashboard)

**Deliverable:**
- Dashboard feels warm and alive
- Matches chat modal aesthetic
- Smooth, professional animations

**Commit Message:** `feat: ambient effects and micro-interactions for dashboard`

---


## Session 11: Custom Widget API - "Docking Clamps" Foundation

**Goal:** Enable users to create custom widgets safely. Make it DEAD SIMPLE - "drop in a folder, it just works."

**Design Philosophy:** "Docking clamps" - custom widgets should snap in effortlessly, like docking a spaceship. No complex build process, no configuration hell. Just manifest.json + HTML/CSS/JS, and you're done.

**Quick Win (20 min):**
- [ ] Define dead-simple custom widget structure:
  ```
  workspace/.control-room/widgets/my-widget/
    manifest.json          # Name, description, icon - that's it
    widget.html            # Your HTML (access to WidgetAPI via window.WidgetAPI)
    styles.css             # Optional: your styles
    script.js              # Optional: your JS
  ```
- [ ] Auto-discover widgets in folder (no registration needed!)
- [ ] Load custom widgets on dashboard startup
- [ ] Display in widget picker with "Custom" badge and user's icon

**Core Work (30 min):**
- [ ] Implement iframe sandbox:
  - Each custom widget renders in isolated iframe
  - Sandbox attribute restricts permissions
  - PostMessage API for communication
- [ ] Create WidgetAPI bridge:
  ```js
  window.WidgetAPI = {
    workspace: { getInfo, listFiles, readFile },
    issues: { list, get },
    agents: { list, get },
    widget: { getSettings, updateSettings, showNotification }
  }
  ```
- [ ] Inject WidgetAPI into iframe context

**Polish (10 min):**
- [ ] Permission warnings in picker (shows requested permissions)
- [ ] Sandbox violations show helpful errors
- [ ] "Trust this widget" confirmation on first add
- [ ] Developer mode toggle in settings (shows console logs)

**Deliverable:**
- Custom widgets can be loaded
- Safe sandboxing in place
- WidgetAPI working for read-only operations

**Commit Message:** `feat: custom widget API with iframe sandboxing`

---


## Session 12: Custom Widget - Example + Import/Export

**Goal:** Create example custom widget and sharing functionality.

**Quick Win (15 min):**
- [ ] Create example "Hello World" custom widget
- [ ] Load it into dashboard
- [ ] Verify WidgetAPI calls work

**Core Work (35 min):**
- [ ] Create richer example: "Recent Issues" widget
  - Uses WidgetAPI.issues.list()
  - Displays last 5 open issues
  - Clickable to open issue modal
- [ ] Implement export:
  - "Export Widget" button in settings
  - Creates .widget file (ZIP with manifest + files)
  - Downloads to user's machine
- [ ] Implement import:
  - "Import Widget" button in picker
  - File picker for .widget files
  - Validates manifest
  - Installs to workspace/widgets/

**Polish (10 min):**
- [ ] Widget preview in import flow
- [ ] Permission approval UI before install
- [ ] "Widget installed!" success toast
- [ ] Auto-open widget picker after import

**Deliverable:**
- Example custom widgets working
- Import/export flow complete
- Users can share widgets

**Commit Message:** `feat: custom widget import/export with example widgets`

---


## Session 13: Widget Library - Additional Built-in Widgets

**Goal:** Round out the built-in widget collection.

**Widgets to implement (pick 3-4):**
1. **Recent Files** (medium)
   - Last 10 edited files
   - Click to open in editor
   - File icons by type
2. **Word Count Progress** (medium)
   - Progress bar toward daily/weekly goal
   - Configurable target
   - Streak indicator
3. **Upcoming Deadlines** (medium)
   - Issues sorted by due date
   - Visual timeline
   - Color-coded urgency
4. **Agent Office Hours** (medium)
   - Time-of-day activity chart
   - Shows when agents are most active
   - Helps schedule conferences
5. **Mood Board** (large)
   - Image grid (masonry layout)
   - Drag to upload images
   - Inspiration collection

**Time per widget: ~15 min**
**Total: 4 widgets in 60 min**

**Deliverable:**
- Expanded widget library
- Variety of widget types proven
- Dashboard feels feature-complete

**Commit Message:** `feat: additional built-in widgets (Recent Files, Word Count, etc.)`

---


## Session 14: Performance + Error Handling Polish

**Goal:** Ensure dashboard scales well and handles errors gracefully.

**Quick Win (15 min):**
- [ ] Add loading skeletons for data-driven widgets
- [ ] Implement retry logic for failed API calls
- [ ] Add error boundaries for widget crashes

**Core Work (35 min):**
- [ ] Performance optimizations:
  - Lazy load widgets (only render visible ones)
  - Debounce settings saves
  - Throttle drag events
  - Virtualize long lists (if needed)
- [ ] Error handling:
  - Widget crashes don't break dashboard
  - Network errors show retry button
  - Invalid widget manifests show helpful errors
  - Migration errors fallback gracefully
- [ ] Widget limits:
  - Warn at 10 widgets (performance)
  - Suggest removing unused widgets
  - Show performance metrics in dev mode

**Polish (10 min):**
- [ ] Loading state animations
- [ ] Error state illustrations
- [ ] Graceful degradation (features disable if unsupported)
- [ ] Accessibility audit (keyboard nav, screen readers)

**Deliverable:**
- Dashboard is robust and performant
- Errors are user-friendly
- Scales to 10+ widgets smoothly

**Commit Message:** `feat: performance optimizations and error handling polish`

---


## Session 15: Documentation + Developer Experience

**Goal:** Make it easy for users (and future you) to create custom widgets.

**Quick Win (10 min):**
- [ ] Create docs/widgets/README.md
- [ ] Document widget manifest schema
- [ ] Add WidgetAPI reference

**Core Work (40 min):**
- [ ] Write comprehensive widget guide:
  - Getting started (Hello World)
  - Manifest schema reference
  - WidgetAPI documentation
  - Styling best practices
  - Common patterns (data fetching, settings, etc.)
- [ ] Create widget template generator:
  - "New Custom Widget" button in picker
  - Scaffolds manifest.json + widget.html + styles.css
  - Pre-fills with example code
  - Opens in editor for customization
- [ ] Add widget inspector (dev mode):
  - Shows widget state
  - Logs WidgetAPI calls
  - Performance metrics per widget

**Polish (10 min):**
- [ ] Code examples with syntax highlighting
- [ ] Inline docs in WidgetAPI (JSDoc)
- [ ] "Learn More" links in UI
- [ ] Widget showcase (gallery of example widgets)

**Deliverable:**
- Complete widget developer docs
- Easy scaffolding for new widgets
- Dev tools for debugging

**Commit Message:** `docs: comprehensive widget system documentation and dev tools`

---


## Planning Complete - Ready to Build! 🚀

**What We've Planned:**
- 15+ coding sessions, each delivering working features
- Widget system that feels like "things on your desk"
- Sound/music player with YouTube/Spotify support
- Click-to-focus (cinema mode) for "giving attention" to widgets
- Dead-simple custom widget creation ("docking clamps")
- Import/export for community sharing
- Future: complete UI theme system with visual builder

**Key Interactions:**
1. **Drag & drop** - Move widgets around your desk
2. **Click to focus** - Give attention to a widget (modal overlay)
3. **Settings** - Customize each widget (gear icon)
4. **Remove** - Clean up your space (X button)
5. **Add** - Pick from library or import custom (+ button)

**Philosophy:**
- Personal creative space, not just productivity
- Community-driven (share widgets like agents)
- Writer-focused aesthetics (soothing, warm, inviting)
- Simple for users, simple for creators

**Token Budget:** 13% daily used, 1% weekly used - plenty of room!

**Ready when you are!** Just say "let's start Session 1" and we'll begin building the widget foundation with Quick Notes. ☕

---


## Session Quick Links

**Foundation (Sessions 1-5):**
- [Session 1: Widget Foundation + Quick Notes](#session-1-widget-foundation--first-custom-widget-quick-notes)
- [Session 2: Widget Picker Modal](#session-2-widget-picker-modal--addremove-flow)
- [Session 3: Convert Existing Widgets](#session-3-enhance-existing-widgets-planner-briefing-issue-pulse)
- [Session 4: Settings Modal](#session-4-widget-settings-modal-framework)
- [Session 5: Drag-and-Drop](#session-5-grid-layout--drag-and-drop-reordering)

**New Widgets (Sessions 6-7.5):**
- [Session 6: Writing Streak](#session-6-new-widget---writing-streak-calendar-heatmap)
- [Session 7: Sound & Music Player](#session-7-new-widget---sound--music-player)
- [Session 7.5: Click-to-Focus](#session-75-widget-click-to-focus-cinema-mode)

**Advanced (Sessions 8-15):**
- [Session 8: Backend Persistence](#session-8-widget-persistence--workspace-integration)
- [Session 9: Visual Polish](#session-9-visual-polish---ambient-effects--animations)
- [Session 10: Warmth Slider](#session-10-warmth-slider--theme-integration)
- [Session 11: Custom Widget API](#session-11-custom-widget-api---docking-clamps-foundation)
- [Session 12: Import/Export](#session-12-custom-widget---example--importexport)
- [Session 13: More Built-in Widgets](#session-13-widget-library---additional-built-in-widgets)
- [Session 14: Performance & Errors](#session-14-performance--error-handling-polish)
- [Session 15: Documentation](#session-15-documentation--developer-experience)
