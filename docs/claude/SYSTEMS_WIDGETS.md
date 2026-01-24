# Workbench Widget System Specification

## 1. Overview

The widget system transforms the Workbench center pane from a static layout into a customizable dashboard. Users can add, remove, arrange, and configure widgets to create their ideal creative workspace.

### Design Philosophy
- **Writer-focused**: Widgets support creative workflows, not just productivity metrics
- **Personal**: Each workspace saves its own layout and preferences
- **Extensible**: Users can create and share custom widgets
- **Soothing**: Visual design matches the calm, creative aesthetic of the app
- **Flexible**: Adapts to different screen sizes and user preferences

## 2. Widget Architecture

### 2.1 Widget Manifest Schema

```json
{
  "id": "widget-writing-streak",
  "name": "Writing Streak",
  "version": "1.0.0",
  "author": "Control Room",
  "description": "Calendar heat map showing daily writing activity",
  "icon": "🔥",

  "size": {
    "default": "medium",
    "allowedSizes": ["small", "medium", "large"]
  },

  "configurable": true,
  "settings": {
    "timeRange": {
      "type": "select",
      "label": "Time Range",
      "default": "30days",
      "options": ["7days", "30days", "90days", "year"]
    },
    "metric": {
      "type": "select",
      "label": "Metric",
      "default": "words",
      "options": ["words", "time", "commits"]
    }
  },

  "permissions": {
    "read": ["files", "workspace"],
    "notifications": false,
    "network": false
  },

  "entrypoint": "widget.html",
  "assets": ["styles.css", "script.js"]
}
```

### 2.2 Widget Sizes

Widgets occupy grid columns based on size:
- **Small**: 1 column (e.g., Quick Stats, Pomodoro Timer)
- **Medium**: 2 columns (e.g., Writing Streak, Recent Files)
- **Large**: 4 columns / full width (e.g., Planner Briefing, Kanban Board)

Layout uses CSS Grid with 4 columns by default. Responsive breakpoints:
- Desktop: 4 columns
- Tablet: 2 columns
- Mobile: 1 column (stacked)

### 2.3 Widget Lifecycle

```javascript
// Widget initialization
class Widget {
  constructor(config) {
    this.id = config.id;
    this.manifest = config.manifest;
    this.settings = config.settings || {};
    this.container = null;
  }

  // Called when widget is added to dashboard
  async mount(container) {
    this.container = container;
    await this.render();
    this.attachEventListeners();
  }

  // Called when widget is removed
  async unmount() {
    this.detachEventListeners();
    this.container = null;
  }

  // Called when settings change
  async update(newSettings) {
    this.settings = { ...this.settings, ...newSettings };
    await this.render();
  }

  // Render widget content
  async render() {
    // Widget-specific rendering logic
  }

  // Event listeners
  attachEventListeners() {}
  detachEventListeners() {}
}
```

## 3. Built-in Widget Library

### 3.1 Informational Widgets

**Planner Briefing** (large) ✅ IMPLEMENTED
- Team lead's daily summary
- Recent issues resolved
- Upcoming tasks
- Styled as team lead "dropping in" for a quick update

**Issue Pulse** (small) ✅ IMPLEMENTED
- Open vs resolved trends
- Total issue count
- Visual stat cards with icons

**Credits Leaderboard** (medium) ✅ IMPLEMENTED
- Agent performance ranking by credits
- Shows which agents are contributing quality work
- Visual indicator of struggling agents (low credits = might need better model)

**Team Activity** (medium) ✅ IMPLEMENTED
- Last 24 hours of agent activity
- Telemetry and token usage (when available)
- Real-time activity stream

**Recent Files** (medium)
- Last 5-10 edited scene files
- Click to open in editor modal
- Show last modified timestamp

**Agent Activity Feed** (medium)
- Live stream of what agents are working on
- "Kermit is reviewing Issue #12"
- "Fozzie Bear posted a comment"

### 3.2 Productivity Widgets

**Writing Streak** (medium)
- Calendar heat map (like GitHub contributions)
- Shows days of consecutive writing activity
- Configurable metric: words written, time spent, commits made

**Word Count Progress** (medium)
- Progress bar toward daily/weekly/monthly goals
- Visual goal tracker with milestones
- Configurable targets

**Pomodoro Timer** (small) ✅ IMPLEMENTED
- 25-min work / 5-min break / 15-min long break cycles
- Session tracking with visual dots (4 sessions until long break)
- Play/pause, reset, skip controls
- Browser notifications on timer completion
- Red glow for focus mode, green glow for break mode
- Completed sessions count persists across sessions

**Goal Visualization** (medium)
- Visual progress toward project goals
- Issues resolved, scenes completed, word count targets
- Customizable metrics

**Upcoming Deadlines** (medium)
- Issues sorted by due date (if we add that field)
- Visual timeline of upcoming tasks
- Color-coded by urgency

### 3.3 Creative Widgets

**Quick Notes** (medium) ✅ IMPLEMENTED
- Simple textarea with auto-save
- Independent of AI/agents/issues
- Jot down quick thoughts without friction
- Markdown support
- Cinema mode: side-by-side editor + live preview

**Mood Board** (large)
- Visual inspiration board
- Pin images, quotes, color palettes, character photos
- Masonry grid layout (like Pinterest)
- Drag to upload images, paste URLs

**Ambient Sound** (small/medium) ✅ IMPLEMENTED
- Dynamic audio file discovery from `public/audio/` directory
- Users can drop their own .mp3/.ogg/.wav files
- Multiple simultaneous sounds (layering for custom soundscapes)
- Volume slider with persistence
- Smart icon detection based on filename keywords
- Bundled sounds: coffee-shop, fireplace, forest, garden, japanese-summer, ocean-waves, rain, typhoon, windchime
- State persists across sessions (active sounds + volume)
- Backend: `AudioController.java` (GET /api/audio)
- Frontend: `AmbientSoundWidget` class in widgets.js

**Warmth** (small) ✅ IMPLEMENTED
- Screen warmth/color temperature slider
- Reduces blue light for late-night writing sessions
- Preset buttons: Off, Low, Medium, High
- Applies CSS filter to entire page
- Persists setting across sessions

### 3.4 Analytics Widgets

**Collaboration Heat Map** (large)
- Visual showing which agents work well together
- Heatmap grid: Agent A × Agent B = collaboration score
- Based on co-assigned issues, comment threads, etc.

**Agent Office Hours** (medium)
- Shows when agents are most productive
- Time-of-day activity chart
- Helps schedule conferences/assignments

## 4. Widget Management UI

### 4.1 Dashboard State

```javascript
// Saved per workspace
interface DashboardLayout {
  workspaceId: string;
  widgets: WidgetPlacement[];
  gridColumns: number; // default 4
  theme: string;
}

interface WidgetPlacement {
  widgetId: string;
  instanceId: string; // unique instance (can have multiple of same widget)
  position: number; // grid order
  size: 'small' | 'medium' | 'large';
  settings: Record<string, any>;
}
```

### 4.2 Add Widget Interface

**Widget Picker Modal**
- Grid of available widgets (built-in + custom)
- Each card shows:
  - Widget icon/preview
  - Name and description
  - Author (for custom widgets)
  - Size options
- Click to add widget to dashboard
- Widget appears at bottom of grid initially
- User can drag to reposition

**Widget Picker UI Mockup:**
```
┌─────────────────────────────────────────┐
│  Add Widget                          [×] │
├─────────────────────────────────────────┤
│  Search widgets...                  🔍  │
├─────────────────────────────────────────┤
│ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐   │
│ │ 🔥   │ │ 📊   │ │ 📝   │ │ 🎨   │   │
│ │Streak│ │Pulse │ │Notes │ │Mood  │   │
│ └──────┘ └──────┘ └──────┘ └──────┘   │
│ ┌──────┐ ┌──────┐ ┌──────┐            │
│ │ 🎵   │ │ ⏱️   │ │ 📈   │            │
│ │Sound │ │Timer │ │Goals │            │
│ └──────┘ └──────┘ └──────┘            │
├─────────────────────────────────────────┤
│ [Import Custom Widget]                  │
└─────────────────────────────────────────┘
```

### 4.3 Widget Controls

Each widget card has:
- **Drag handle** (⋮⋮) - top-left, for reordering
- **Settings gear** (⚙️) - top-right, opens settings modal
- **Remove button** (×) - top-right, removes widget

Hover states show these controls. When not hovering, they're subtle/hidden.

### 4.4 Widget Settings Modal

```
┌─────────────────────────────────────────┐
│  Widget Settings: Writing Streak     [×]│
├─────────────────────────────────────────┤
│  Time Range:                            │
│  ○ Last 7 days                          │
│  ● Last 30 days                         │
│  ○ Last 90 days                         │
│  ○ Full year                            │
│                                         │
│  Metric:                                │
│  ● Words written                        │
│  ○ Time spent writing                   │
│  ○ Commits made                         │
│                                         │
│  Size:                                  │
│  ○ Small  ● Medium  ○ Large            │
├─────────────────────────────────────────┤
│              [Cancel]  [Save]           │
└─────────────────────────────────────────┘
```

## 5. Custom Widget API

### 5.1 Safe Widget Sandbox

Custom widgets run in a sandboxed environment with restricted permissions:
- No direct DOM access to main app
- Can only read data via provided APIs
- Cannot modify workspace data (read-only)
- Network requests require explicit permission
- Rendered in iframe or web component

### 5.2 Widget API Interface

```javascript
// Available to custom widgets via window.WidgetAPI
const WidgetAPI = {
  // Read workspace data
  workspace: {
    async getInfo() { /* returns workspace metadata */ },
    async listFiles() { /* returns file list */ },
    async readFile(path) { /* returns file content */ }
  },

  // Read issues (read-only)
  issues: {
    async list(filters) { /* returns issue list */ },
    async get(id) { /* returns issue details */ }
  },

  // Read agents (read-only)
  agents: {
    async list() { /* returns agent list */ },
    async get(id) { /* returns agent details */ }
  },

  // Widget lifecycle
  widget: {
    async getSettings() { /* returns current settings */ },
    async updateSettings(newSettings) { /* saves settings */ },
    async showNotification(message, level) { /* shows toast */ }
  }
};
```

### 5.3 Custom Widget Structure

```
my-custom-widget/
  ├── manifest.json          # Widget metadata
  ├── widget.html            # Entry point
  ├── styles.css             # Widget styles
  ├── script.js              # Widget logic
  └── icon.png              # Widget icon
```

**Example Custom Widget (Minimal):**

```html
<!-- widget.html -->
<!DOCTYPE html>
<html>
<head>
  <link rel="stylesheet" href="styles.css">
</head>
<body>
  <div class="widget-container">
    <h3>Hello from Custom Widget!</h3>
    <div id="content"></div>
  </div>
  <script src="script.js"></script>
</body>
</html>
```

```javascript
// script.js
(async function() {
  const settings = await WidgetAPI.widget.getSettings();
  const issues = await WidgetAPI.issues.list({ status: 'open' });

  document.getElementById('content').textContent =
    `You have ${issues.length} open issues.`;
})();
```

### 5.4 Widget Import/Export

**Export Format:** `.widget` file (ZIP archive)
- Contains all widget files
- manifest.json at root
- Can be shared via file or URL

**Import Flow:**
1. User clicks "Import Custom Widget"
2. File picker or URL input
3. System validates manifest
4. Checks permissions (warns if network access requested)
5. User approves
6. Widget added to library

## 6. Layout System Implementation

### 6.1 CSS Grid Structure

```css
.workbench-dashboard {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 20px;
  grid-auto-rows: minmax(200px, auto);
}

.widget-small {
  grid-column: span 1;
}

.widget-medium {
  grid-column: span 2;
}

.widget-large {
  grid-column: span 4;
}

/* Responsive breakpoints */
@media (max-width: 1200px) {
  .workbench-dashboard {
    grid-template-columns: repeat(2, 1fr);
  }
  .widget-large {
    grid-column: span 2;
  }
}

@media (max-width: 768px) {
  .workbench-dashboard {
    grid-template-columns: 1fr;
  }
  .widget-small,
  .widget-medium,
  .widget-large {
    grid-column: span 1;
  }
}
```

### 6.2 Drag-and-Drop Behavior

- Use native HTML5 drag-and-drop API or library like Sortable.js
- Visual feedback: semi-transparent ghost during drag
- Drop zones highlighted when dragging over
- Animate widgets into new positions
- Save layout immediately on drop

### 6.3 Dynamic Scaling

When grid is full and user adds widget:
- **Option A (Scroll)**: Grid expands vertically, scrollable
- **Option B (Scale)**: Existing widgets shrink proportionally to fit
- **Preferred**: Combination - allow scrolling but suggest removing widgets after 8-10 visible

Widget suggestion: "Your dashboard is getting full! Consider removing unused widgets for a cleaner view."

## 7. Visual Polish

### 7.1 Ambient Effects

**Subtle background animation:**
- Gentle gradient shift (like breathing)
- Particle effect (dust motes, gentle snow, floating embers)
- Parallax effect on scroll
- Themed to current color scheme

**Implementation:** Canvas overlay or CSS keyframe animations, very subtle, low opacity.

### 7.2 Widget Animations

- **Mount animation**: Fade in + slight scale up
- **Unmount animation**: Fade out + slight scale down
- **Hover**: Subtle lift (translateY -2px) + shadow increase
- **Drag**: Semi-transparent ghost, smooth transitions
- **Settings open**: Modal slide up from widget

### 7.3 Personality in Empty States

When dashboard is empty:
```
┌─────────────────────────────────────────┐
│                                         │
│              ✨ ✨ ✨                   │
│                                         │
│   Your creative workspace awaits!       │
│                                         │
│   Add widgets to customize your         │
│   writer's command center.              │
│                                         │
│         [+ Add Your First Widget]       │
│                                         │
└─────────────────────────────────────────┘
```

When widget has no data:
- **Writing Streak (no activity)**: "Start your streak today! ✨"
- **Credits Leaderboard (no agents)**: "Your team will appear here once agents start contributing."
- **Quick Notes (empty)**: "Jot down your thoughts..."


## 9. Persistence

### 9.1 Storage Format

```javascript
// Saved in workspace config
{
  "workspaceId": "my-novel",
  "dashboard": {
    "layout": {
      "columns": 4,
      "widgets": [
        {
          "widgetId": "widget-planner-briefing",
          "instanceId": "briefing-1",
          "position": 0,
          "size": "large",
          "settings": {}
        },
        {
          "widgetId": "widget-writing-streak",
          "instanceId": "streak-1",
          "position": 1,
          "size": "medium",
          "settings": {
            "timeRange": "30days",
            "metric": "words"
          }
        }
      ]
    },
    "customWidgets": [
      {
        "id": "widget-my-custom",
        "source": "local", // or "url"
        "path": "widgets/my-custom.widget"
      }
    ]
  }
}
```

### 9.2 Save Triggers

- On widget add/remove: Immediate save
- On widget reorder: Debounced save (500ms after drag end)
- On settings change: Immediate save


## Design Philosophy (User Input)

**Key Insights from User:**

1. **Sound/Music Widget** - Definitely wanted! Open Spotify/YouTube Music playlists, popular lofi streams, ambient soundscapes. This is a personal workspace feature, not just productivity.

2. **Widget as "Things on My Desk"** - Beautiful metaphor! Widgets should feel like personal tools you can:
   - Move around (drag & drop)
   - Give attention to (click to enlarge/focus)
   - Customize and personalize
   - Transform the workbench into YOUR space

3. **Community & Sharing** - Import/export widgets just like agents and themes. Modular ecosystem where users exchange favorites. This is CORE to the vision, not an afterthought.

4. **"Docking Clamps" Philosophy** - Make custom widgets dead simple to create. No fancy builder needed, but make it ridiculously easy for people who want to tinker. Think: "drop in a folder, it just works."

5. **Future: Complete Theme System** - Eventually, theme the entire UI (editor + workbench). Theme builder with visual editor: click a button → set font/color → all buttons update. Name, save, export, share.

**Widget Limit Decision:** 10-12 widgets max seems right - enough to feel abundant, not so many it's overwhelming. "Things on my desk" is the perfect mental model.

---


**Finalized Decisions:**

### 1. Widget Data Models

```javascript
// Widget Manifest (defines what a widget is)
const WidgetManifest = {
  id: 'widget-quick-notes',           // Unique identifier
  name: 'Quick Notes',                // Display name
  description: 'Jot down quick thoughts',
  icon: '📝',                         // Emoji or icon class
  author: 'Control Room',             // Built-in or username
  version: '1.0.0',

  // Size constraints
  size: {
    default: 'medium',
    allowedSizes: ['small', 'medium', 'large']
  },

  // Configurable settings
  configurable: true,
  settings: {
    showPreview: {
      type: 'checkbox',
      label: 'Show Markdown Preview',
      default: false
    },
    fontSize: {
      type: 'select',
      label: 'Font Size',
      default: 'medium',
      options: ['small', 'medium', 'large']
    }
  },

  // For custom widgets (future)
  permissions: {
    read: ['files', 'workspace'],
    write: [],
    network: false
  }
};

// Widget Instance (user's copy of a widget)
const WidgetInstance = {
  instanceId: 'notes-1',              // Unique instance ID
  widgetId: 'widget-quick-notes',     // Reference to manifest
  position: 2,                        // Grid order (0-based)
  size: 'medium',                     // Current size
  settings: {                         // User's settings
    showPreview: true,
    fontSize: 'large',
    // Widget-specific data stored here
    content: 'My notes...',
    lastModified: 1704672000000
  }
};

// Dashboard Layout (persisted per workspace)
const DashboardLayout = {
  workspaceId: 'my-novel',
  version: 1,
  columns: 4,                         // Grid columns
  widgets: [                          // Array of WidgetInstance
    { instanceId: 'briefing-1', widgetId: 'widget-planner-briefing', position: 0, size: 'large', settings: {} },
    { instanceId: 'pulse-1', widgetId: 'widget-issue-pulse', position: 1, size: 'small', settings: { timeRange: '7d' } },
    { instanceId: 'notes-1', widgetId: 'widget-quick-notes', position: 2, size: 'medium', settings: { content: '...' } }
  ]
};
```

### 2. Widget Registry Structure

```javascript
// In-memory registry (built-in widgets)
const WidgetRegistry = {
  widgets: new Map(),                 // widgetId -> WidgetManifest

  register(manifest) {
    this.widgets.set(manifest.id, manifest);
  },

  get(widgetId) {
    return this.widgets.get(widgetId);
  },

  list() {
    return Array.from(this.widgets.values());
  },

  createInstance(widgetId, settings = {}) {
    const manifest = this.get(widgetId);
    if (!manifest) throw new Error(`Widget not found: ${widgetId}`);

    return {
      instanceId: generateId(),
      widgetId,
      position: 0,
      size: manifest.size.default,
      settings: { ...getDefaultSettings(manifest), ...settings }
    };
  }
};
```

### 3. Widget Base Class

```javascript
class Widget {
  constructor(instance, manifest) {
    this.instance = instance;       // WidgetInstance data
    this.manifest = manifest;       // WidgetManifest reference
    this.container = null;          // DOM element
    this.mounted = false;
  }

  // Lifecycle methods (subclasses override)
  async mount(container) {
    this.container = container;
    await this.render();
    this.attachEventListeners();
    this.mounted = true;
  }

  async unmount() {
    this.detachEventListeners();
    this.container = null;
    this.mounted = false;
  }

  async render() {
    // Default implementation - subclasses override
    this.container.innerHTML = `
      <div class="widget-content">
        <p>Widget: ${this.manifest.name}</p>
      </div>
    `;
  }

  async update(newSettings) {
    this.instance.settings = { ...this.instance.settings, ...newSettings };
    await this.render();
    this.saveToDashboard();
  }

  attachEventListeners() {}
  detachEventListeners() {}

  saveToDashboard() {
    // Save updated instance to dashboard state
    window.dashboardState.saveWidget(this.instance);
  }
}
```


### 7. Size System (Fixed 3 Sizes)

**Grid Setup:**
- 4 columns on desktop (≥1200px)
- 2 columns on tablet (768-1199px)
- 1 column on mobile (<768px)

**Size Mapping:**
- **Small**: 1 column (stat cards, quick actions)
- **Medium**: 2 columns (most widgets)
- **Large**: 4 columns / full width (hero widgets, dashboards)

### 8. Data Refresh Strategy (v1)

**Manual Refresh:**
- Each widget has a refresh button (🔄) in header
- Clicking calls widget.refresh() method
- Widget implements its own data fetching logic

**Periodic Polling (Later):**
- Dashboard-level interval (configurable, default 60s)
- Calls refresh() on all mounted widgets
- Widget can opt out via `manifest.autoRefresh = false`

**Event-Driven (Future):**
- Widgets subscribe to app events (e.g., 'issue:created')
- Dashboard broadcasts relevant events
- Widgets update reactively

---

**Session 1 is now fully scoped and ready to start!**

The data models are finalized, architectural decisions are locked in, and we have a clear path from simple localStorage persistence to proper backend integration. We'll start with Quick Notes because it's the perfect test case: simple, self-contained, and immediately useful.


### Visual Transformation Journey

**Current State (Hard-coded):**
```
┌─────────────────────────────────────────────────────────────┐
│  Workbench Dashboard                                        │
├─────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────┐  ┌──────────┐          │
│  │ Planner Briefing               │  │ Issue    │          │
│  │ (hard-coded HTML)              │  │ Pulse    │          │
│  └────────────────────────────────┘  └──────────┘          │
│  ┌──────────┐  ┌──────────┐                                │
│  │ Credits  │  │ Team     │                                │
│  │ Leaderbd │  │ Activity │                                │
│  └──────────┘  └──────────┘                                │
└─────────────────────────────────────────────────────────────┘
```

**After Session 2 (Add/Remove):**
```
┌─────────────────────────────────────────────────────────────┐
│  Workbench Dashboard                           [+ Add]      │
├─────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────┐  ┌──────────┐  [×]     │
│  │ 📋 Planner Briefing      [⚙]  │  │📊 Issue  │  [×]     │
│  │ (Widget instance)        [×]   │  │  Pulse   │  [⚙]     │
│  └────────────────────────────────┘  └──────────┘          │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐             │
│  │📝 Quick  │  │🏆 Credits│  │👥 Team       │             │
│  │  Notes   │  │  Leader  │  │  Activity    │             │
│  └──────────┘  └──────────┘  └──────────────┘             │
└─────────────────────────────────────────────────────────────┘
```

**After Session 5 (Drag-and-Drop):**
```
┌─────────────────────────────────────────────────────────────┐
│  Workbench Dashboard                           [+ Add]      │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌────────────────────────────────┐          │
│  │⋮⋮ Quick  │  │⋮⋮ Planner Briefing       [⚙]  │          │
│  │  Notes   │  │  (User dragged to reorder)[×]  │          │
│  └──────────┘  └────────────────────────────────┘          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│  │⋮⋮ Streak │  │⋮⋮ Sound  │  │⋮⋮ Pulse  │                 │
│  │  📅      │  │  🎵      │  │  📊      │                 │
│  └──────────┘  └──────────┘  └──────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

**After Session 9 (Visual Polish):**
```
┌─────────────────────────────────────────────────────────────┐
│  Workbench Dashboard                    [🌡️ ———◉——] [+ Add] │
│  ✨ Subtle ambient gradient breathing animation ✨          │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌────────────────────────────────┐          │
│  │📝 Quick  │  │📋 Planner Briefing        [⚙]  │ ←fade in│
│  │  Notes   │  │  Soft glow, smooth hover  [×]  │  scale up│
│  └──────────┘  └────────────────────────────────┘          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│  │🔥 Streak │  │🎵 Ambient│  │📊 Pulse  │                 │
│  │  [streak]│  │  ♫ playing│  │  [chart]│                 │
│  └──────────┘  └──────────┘  └──────────┘                 │
└─────────────────────────────────────────────────────────────┘
     ↑ Hover lift    ↑ Visualizer    ↑ Live data
```

**Final Vision (Session 15+):**
```
┌─────────────────────────────────────────────────────────────┐
│  My Creative Workspace              [🌡️ ———◉——] [+ Add]     │
│  ✨ Dust motes floating gently ✨                           │
├─────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────┐  ┌──────────────┐      │
│  │📝 Quick Notes             [⚙×] │  │🔥 30-day     │      │
│  │  "Chapter 3 ideas..."          │  │  [██████░░░] │      │
│  │  Last edit: 2m ago             │  │  15 day streak│      │
│  └────────────────────────────────┘  └──────────────┘      │
│  ┌──────────────┐  ┌────────────────────────────────┐      │
│  │🎵 Lofi Beats │  │📋 Planner Briefing        [⚙×] │      │
│  │  ♫ ▌▌▋▍▎▎▏   │  │  "Great momentum! We closed  │      │
│  │  Vol: ▓▓▓░░  │  │  3 issues yesterday..."      │      │
│  └──────────────┘  └────────────────────────────────┘      │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐             │
│  │📚 Recent │  │🎨 Mood   │  │📊 Word Count │ (my custom) │
│  │  Files   │  │  Board   │  │  Progress    │             │
│  └──────────┘  └──────────┘  └──────────────┘             │
└─────────────────────────────────────────────────────────────┘
   ↑ Built-in    ↑ Built-in    ↑ Custom widget I made!
```

---


## Post-Implementation: Future Enhancements

**Not in scope for v1, but documented for later:**

### 1. Widget Marketplace (v2)
- Community widget sharing platform
- Browse, install, rate widgets
- Auto-updates for installed widgets
- Featured widgets, trending, categories
- User profiles and widget portfolios

### 2. Advanced Widget Communication (v2)
- Widgets can subscribe to events
- Cross-widget data flow
- Dashboard-level state management
- Example: Writing Streak widget triggers celebration animation in other widgets when you hit a milestone

### 5. Mobile Responsive Dashboard (v2)
- Touch-friendly drag-and-drop
- Swipe gestures (left/right to switch widgets, up to focus)
- Adaptive layouts for small screens
- Widget "carousel" mode on mobile
- Gesture hints for first-time users

### 6. Widget Analytics (v3)
- Track widget usage (time spent, interactions)
- Show most popular widgets in picker
- Suggest widgets based on usage patterns
- "You might like..." recommendations
- Usage heatmap (which widgets you use when)

### 7. Collaborative Widgets (v4 - Advanced)
- Real-time multi-user widgets
- Shared dashboards for team workspaces
- Presence indicators ("Alice is viewing this widget")
- Collaborative Quick Notes (like Google Docs)
- Live cursor positions

### 8. Widget Animations Library (v2)
- Pre-built entrance/exit animations
- Hover effects library
- Loading state animations
- Celebration effects (confetti, sparkles)
- Widget creators can choose from library

---
