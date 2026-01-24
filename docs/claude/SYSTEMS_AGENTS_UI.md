# SYSTEMS_AGENTS_UI

## Sidebar Notes
- Agent sidebar shows avatars, roles, and availability status
- Right sidebar has newsfeed (notifications)

## Next Up: Agent Card Redesign ⭐

**Goal:** Transform the compact agent cards into expressive, informative panels.

**Current State** (see docs/current.jpg):
- Small square avatar
- Name + gear icon
- Role text below
- "LEAD" badge for Chief of Staff

**Target Design** (see docs/agents.jpg):
```
┌─────────────────────────────────────────────────┐
│  ┌───────┐                           ┌───┐     │
│  │       │  Name                     │ ● │     │
│  │Avatar │                           └───┘     │
│  │       │  Role                      ☆        │
│  └───────┘                    (Chief of Staff) │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ Status: busy · waiting · assisted       │   │
│  └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

**Key Elements:**
1. **Large circular avatar** - Prominent, left-aligned
2. **Name** - Bold, clear typography
3. **Status LED** - Live indicator dot (green=idle, yellow=busy, red=error)
4. **Role** - Secondary text below name
5. **Chief of Staff star** - Special badge for team leader (replaces "LEAD" text)
6. **Status effects bar** - Full-width bottom section showing current states:
   - busy (actively processing)
   - waiting (in queue)
   - assisted (human-assisted mode)
   - idle (available)

**Implementation Tasks:**
- [ ] Restructure agent card HTML layout
- [ ] Large circular avatar with image/emoji/initial fallback
- [ ] Status LED component with color states
- [ ] Chief of Staff star icon (☆ or ⭐)
- [ ] Status effects bar with badges/pills
- [ ] CSS for new layout + animations
- [ ] Wire status LED to agent activity state
- [ ] Hover effects and transitions

**Design Notes:**
- Cards should feel like team member profiles, not just list items
- Status LED should pulse/glow when busy
- Star badge could have subtle shimmer animation
- Status bar should update in real-time as agent state changes
- Consider click-to-expand for more agent details (future)

---
