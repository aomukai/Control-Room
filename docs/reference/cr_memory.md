# Agent Memory Bank
Indexed in docs/agent_library.md

> Issue-based memory system for agent collaboration and knowledge retention.

---

<a id="memory-architecture"></a>
## Architecture Summary

### Centralized Issue Interactions
All issue-related interactions are centralized through a **shared Issue Detail Modal**:
- Notifications, Workbench Newsfeed, and future Issue Board all use the same modal
- `openIssueModal(issueId)` is the single entry point from anywhere in the app
- No duplicate modal implementations per view/mode

<a id="memory-notifications"></a>
### Notifications as Event Entry Points
Notifications serve as universal event dispatchers:
- Issue creation, closure, and comments emit notifications via `notificationStore`
- Notifications carry `actionPayload.kind: 'openIssue'` for issue-related actions
- Clicking any issue notification (toast, center, newsfeed) opens the shared modal

### Workbench Replaces Duplicate UIs
The Workbench Newsfeed provides an integrated view of issue activity:
- Filters NotificationStore for workbench/issue scope
- Reuses existing notification click handling
- No separate "issue list" component needed in MVP

---

## Core Concepts

- Agent-to-agent async communication (no need for human to relay messages)
- Searchable institutional memory (agents learn from past issues)
- Personal context curation (agents filter noise autonomously)
- Audit trail (every decision is documented with reasoning)

This is essentially GitHub Issues + Slack + Personal Knowledge Management for your agent swarm.

---

## Current Implementation Status

### Completed
- **Issue model** (`Issue.java`) with sequential IDs, priority, comments, closedAt
- **Comment model** (`Comment.java`) with author, body, timestamp, action
- **IssueMemoryService** with JSON persistence, sequential ID generation, comment support
- **Notification model** (`Notification.java`) with full enum support (Level, Scope, Category)
- **NotificationStore** with JSON persistence (`data/notifications.json`)
- **REST API endpoints** for Issues and Notifications (see below)
- **Frontend notification sync** on startup (loads from `/api/notifications`)
- **Frontend issue API wrapper** (`issueApi` in app.js)

<a id="memory-rest-api-issues"></a>
### REST API — Issues
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/issues` | List issues (filters: `?tag=...&assignedTo=...&status=...&priority=...`) |
| GET | `/api/issues/{id}` | Get single issue (sequential int ID) |
| POST | `/api/issues` | Create issue (`{title, body?, openedBy?, assignedTo?, tags?, priority?}`) |
| PUT | `/api/issues/{id}` | Update issue (partial update supported) |
| DELETE | `/api/issues/{id}` | Delete issue |
| POST | `/api/issues/{id}/comments` | Add comment (`{author?, body, action?: {type, details}}`) |

### REST API — Notifications
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/notifications` | List all (filters: `?level=error,warning&scope=editor`) |
| GET | `/api/notifications/unread-count` | Get unread count (optional `?scope=editor`) |
| GET | `/api/notifications/{id}` | Get single notification |
| POST | `/api/notifications` | Create notification |
| PUT | `/api/notifications/{id}` | Update (mark as read) |
| DELETE | `/api/notifications/{id}` | Delete notification |
| POST | `/api/notifications/mark-all-read` | Mark all as read |
| POST | `/api/notifications/clear` | Clear notifications (`{all: true}` or non-errors only) |

### Frontend API (`app.js`)
```javascript
// Issue API wrapper
issueApi.list(filters?)      // {tag?, assignedTo?, status?, priority?}
issueApi.get(id)
issueApi.create(data)        // {title, body?, openedBy?, assignedTo?, tags?, priority?}
issueApi.update(id, data)
issueApi.delete(id)
issueApi.addComment(id, data) // {author?, body, action?: {type, details}}

// Notification store loads from server on startup
notificationStore.loadFromServer()
```

### Not Yet Implemented
- Issue Board UI
- Memory degradation system (5-level interest gradient)
- Personal tagging system
- Agent tool schema integration
- Leech detection

#Detailed System Design

<a id="memory-issue-structure"></a>
##Issue Structure
```
interface Issue {
  id: number;                    // Sequential: #1, #2, #3...
  title: string;                 // Human-readable summary
  status: "open" | "closed" | "waiting-on-user";
  author: AgentRole;             // Who created it
  assignee?: AgentRole;          // Who should handle it
  tags: string[];                // Shared tags: #continuity, #deep-current
  priority: "low" | "normal" | "high" | "urgent";
  
  body: string;                  // Initial report/question
  comments: Comment[];           // Thread of responses
  
  createdAt: timestamp;
  updatedAt: timestamp;
  closedAt?: timestamp;
  
  // Linked entities
  relatedFiles?: string[];       // Paths to relevant scenes/docs
  relatedIssues?: number[];      // References to other issues
  
  // Visibility
  isPrivate: boolean;            // Only visible to author + assignee + user
}

interface Comment {
  author: AgentRole | "user";
  body: string;
  timestamp: number;
  
  // Actions taken
  action?: {
    type: "patch-created" | "file-modified" | "resolved";
    details: string;
  };
}```

<a id="memory-roadmap-status-tags"></a>
## Planner Roadmap Status Tags

Planner-owned Scene Tags track narrative maturity and must stay in sync with the
Roadmap state. These tags live in `Issue.tags` and follow a single active status:
`Idea` -> `Plan` -> `Draft` -> `Polished`.

- The Planner updates the Scene Tag when the roadmap advances.
- The Issue `status` remains "open" until the scene is truly complete; `Polished` is a
  roadmap tag, not a closure signal on its own.
- The Assistant closes the issue when Definition of Done is met.

<a id="memory-personal-tagging"></a>
##Personal Tagging System
```
interface AgentPersonalTag {
  agentId: AgentRole;
  issueId: number;
  tag: "interesting" | "irrelevant" | "follow-up" | "resolved";
  note?: string;                 // Optional private note
  timestamp: number;
}```

**How it works:**
- Agent reads Issue #42
- Agent decides: "This taught me something important about Deep Current mechanics"
- Agent adds personal tag: `interesting` + note: "Reference this when writing EM perception scenes"
- Later, when searching `#deep-current`, Issue #42 appears with ⭐ marker
- Issues tagged `irrelevant` are automatically filtered from future searches

---

## UI/UX Design

<a id="memory-issue-board-panel"></a>
### Issue Board Panel (in Workbench)
```
┌─────────────────────────────────────────────┐
│ Issue Board                    [New Issue]  │
├─────────────────────────────────────────────┤
│ Filters: ○ All  ● Open  ○ Assigned to me    │
│ Tags: [#continuity] [#deep-current] [Clear] │
├─────────────────────────────────────────────┤
│ #103 ⚠️ Timeline conflict in Scene 6         │
│ Continuity → Writer  |  2 comments  |  1h   │
│ Tags: #continuity #scene-6 #timeline        │
├─────────────────────────────────────────────┤
│ #102 ⭐ Deep Current visualization idea      │
│ Writer → Planner  |  Closed  |  5h          │
│ Tags: #deep-current #visualization          │
│ Personal: interesting ⭐                     │
├─────────────────────────────────────────────┤
│ #101 📋 Character name consistency check     │
│ Editor → Continuity  |  Open  |  12h        │
│ Tags: #characters #names                    │
│ Personal: irrelevant 🚫                     │
└─────────────────────────────────────────────┘```

**Interaction:**
- Click issue → Opens detail modal or inline expansion
- Filter by status, tags, assignee, personal tags
- Create new issue: Quick modal with title/tags/assignee

---

### Issue Detail View
```
┌──────────────────────────────────────────────────┐
│ Issue #103: Timeline conflict in Scene 6         │
│ Continuity → Writer  |  Open  |  Updated 1h ago  │
│ Tags: #continuity #scene-6 #timeline             │
│ Files: story/chapter-1/scene-2.txt,              │
│        story/chapter-1/scene-6.txt               │
├──────────────────────────────────────────────────┤
│ Continuity (2h ago):                             │
│ Scene 2 establishes flotilla arrival at planet  │
│ on Day 47. Scene 6 references "three weeks      │
│ since arrival" but is set on Day 52. This is    │
│ only 5 days, not 21.                             │
│                                                  │
│ Recommendation: Change Scene 6 to "five days    │
│ since arrival" or adjust the day number.        │
│                                                  │
│ [Open Scene 2] [Open Scene 6]                   │
├──────────────────────────────────────────────────┤
│ Writer (1h ago):                                 │
│ Good catch. I've created a patch that changes   │
│ the dialogue to "barely a week since arrival"   │
│ which maintains the urgency while being         │
│ timeline-accurate.                               │
│                                                  │
│ Reasoning: "Three weeks" was meant to convey    │
│ exhaustion, but "barely a week" achieves the    │
│ same effect while respecting canon.             │
│                                                  │
│ Action: Patch created (patch_42.diff)           │
│ [View Patch]                                     │
├──────────────────────────────────────────────────┤
│ Your comment:                                    │
│ ┌────────────────────────────────────────────┐  │
│ │ Great fix! Approved.                       │  │
│ └────────────────────────────────────────────┘  │
│ [Add Comment] [Close Issue] [Assign to...]      │
├──────────────────────────────────────────────────┤
│ Personal Tags (visible only to you):             │
│ ○ Interesting  ● Irrelevant  ○ Follow-up        │
│ Note: ┌──────────────────────────────────────┐  │
│       │ Minor timeline fix, no broader impact│  │
│       └──────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘```

<a id="memory-agent-workflows"></a>
##Agent Workflows

1. Continuity Agent Creates Issue
Trigger: Finds inconsistency during scene validation
Action:
```
createIssue({
  title: "Character eye color mismatch",
  author: "continuity",
  assignee: "writer",
  priority: "normal",
  tags: ["#continuity", "#characters", "#seryn"],
  body: `
    In compendium/characters/seryn.md, Seryn's eyes are described 
    as "dark amber."
    
    In story/chapter-2/scene-3.txt, line 47 reads: "Her green eyes 
    reflected the console glow."
    
    Recommendation: Change "green" to "amber" or update compendium 
    if eye color has changed.
  `,
  relatedFiles: [
    "compendium/characters/seryn.md",
    "story/chapter-2/scene-3.txt"
  ]
})


**Notification emitted:**

```
notificationStore.issueCreated(104, "Character eye color mismatch", "Continuity", "Writer")
```

##Writer Reads Issue & Responds
Context: Writer is working on a new scene, checks issue board first
Tool call:
```
searchIssues({
  assignedTo: "writer",
  status: "open",
  excludePersonallyTagged: ["irrelevant"]
})
// Returns: Issue #104```
Writer reads issue, replies:
```
addComment({
  issueId: 104,
  author: "writer",
  body: `
    Checked the compendium. You're right—Seryn's eyes are amber.
    This is my error from drafting too quickly.
    
    I'll create a patch to fix line 47. The green eyes were probably
    a copy-paste artifact from another character.
  `,
  action: {
    type: "patch-created",
    details: "Changed 'green eyes' to 'amber eyes' in scene-3.txt:47"
  }
})

addPersonalTag({
  issueId: 104,
  tag: "irrelevant", // Won't see this type of issue again unless critical
  note: "Simple typo fix, no thematic implications"
})```

##Planner Researches Past Decisions
Context: User asks Planner to structure a new Deep Current crisis scene
Tool call:
```searchIssues({
  tags: ["#deep-current"],
  personalTags: ["interesting"],  // Only show ones I've marked important
  status: "all"
})```

**Results:**
```
Issue #67: "Deep Current stability affects oxygen recyclers" ⭐
Issue #89: "EM perception metaphors for Deep Current" ⭐
Issue #102: "Visualization ideas for Current failure" ⭐```
Planner reads these, then:
```
// Uses learned context to structure beat clusters
generateBeatClusters({
  sceneContext: "Seryn discovers recycler failure",
  priorKnowledge: [
    "Issue #67 established Current→recycler connection",
    "Issue #89 provides EM perception language",
    "Issue #102 suggests visualization via console shimmer"
  ]
})```
Result: Planner creates more consistent scenes by referencing institutional memory instead of starting from scratch each time.

#Integration with Existing Systems

Issue Board as Notification Source
Issues integrate with your 3-layer notification system:
When issue is created:
```
notificationStore.issueCreated(104, "Character eye color mismatch", "Continuity", "Writer")
```
When assigned issue is updated:
```
notificationStore.issueCommentAdded(104, "Writer")
```

Frontend consumes notifications through ToastStack, StatusBar, and NotificationCenter.

**User clicks notification → Opens issue detail modal**

---

### Issue Board in Workbench Layout

**Add to Section 3 of `cr_workbench.md`:**
```
┌─────────────────────────────────────────────────────────┐
│   Top Bar: Workbench | Editor | Settings (⚙)           │
├───────────┬─────────────────────────────┬───────────────┤
│ Agent     │ Chat / Discussion Pane      │ Newsfeed      │
│ Sidebar   │                             │               │
│           │ ┌─────────────────────────┐ │               │
│           │ │ [Chat] [Issue Board]    │ │               │
│           │ └─────────────────────────┘ │               │
│           │                             │               │
└───────────┴─────────────────────────────┴───────────────┘```
Issue Board is a tab in the main panel, switchable with Chat.
Or, alternative design: Issue Board is always visible as a bottom drawer that slides up.

#Agent Tool Schema
New Tools for Agents
```// Create a new issue
create_issue(
  title: string,
  assignee?: AgentRole,
  tags: string[],
  priority: "low" | "normal" | "high" | "urgent",
  body: string,
  relatedFiles?: string[],
  isPrivate?: boolean
)

// Search issues
search_issues(
  tags?: string[],
  assignedTo?: AgentRole,
  status?: "open" | "closed" | "all",
  personalTags?: string[],  // Filter by agent's personal tags
  excludePersonallyTagged?: string[]  // Exclude issues tagged as irrelevant
)

// Read issue details
read_issue(issueId: number)

// Comment on issue
add_comment(
  issueId: number,
  body: string,
  action?: { type: string, details: string }
)

// Close/reopen issue
update_issue_status(issueId: number, status: "open" | "closed")

// Personal tagging (invisible to other agents)
add_personal_tag(
  issueId: number,
  tag: "interesting" | "irrelevant" | "follow-up" | "resolved",
  note?: string
)

// Assign issue to different agent
reassign_issue(issueId: number, newAssignee: AgentRole)```

##Advanced Features
1. Issue Templates
```**Timeline Conflict Template:**
Title: [Scene X vs Scene Y timeline conflict]
Tags: #continuity, #timeline
Assignee: @writer

Body:
- Scene A establishes: [event] on [date/time]
- Scene B references: [conflicting detail]
- Discrepancy: [explain]
- Recommendation: [suggest fix]
- Related files: [list]```
2. Issue Relationships
```// Link related issues
linkIssues(issueId: 104, relatedId: 67, relationship: "blocks" | "relates-to" | "duplicates")

// Example:
Issue #104: "Fix Seryn eye color"
  → blocks → Issue #109: "Final character description audit"```
  3. Automatic Issue Creation
  ```
  // Continuity agent automatically creates issues when detecting problems
if (continuityCheck.hasConflict) {
  createIssue({
    title: `Conflict: ${conflict.description}`,
    author: "continuity",
    assignee: "writer",
    priority: conflict.severity,
    tags: ["#continuity", `#${conflict.category}`],
    body: formatConflictReport(conflict)
  })
}```
4. Issue Analytics
```// Dashboard showing:
- Open issues by agent
- Average resolution time
- Most common tags
- Issues marked "interesting" (knowledge highlights)


---

## Implementation Roadmap

### Phase 1: Core Issue System (Milestone 6.5)
- Issue data model + storage (JSON files or SQLite)
- Basic CRUD operations
- Agent tool schema implementation
- Simple list view in Workbench

### Phase 2: Personal Tagging (Milestone 6.5)
- Personal tag system
- Search filtering by personal tags
- Agent context hygiene logic

### Phase 3: UI Polish (Milestone 7)
- Rich issue detail modal
- Inline issue creation from chat
- Issue board filters and sorting
- Notification integration

### Phase 4: Advanced Features (Milestone 7+)
- Issue templates
- Relationship mapping
- Automatic issue creation from continuity checks
- Analytics dashboard

---

## Storage Format

### Simple JSON approach:

.control-room/
  issues/
    issue-001.json
    issue-002.json
    issue-103.json
  personal-tags/
    planner.json
    writer.json
    editor.json```
issue-103.json:
```
{
  "id": 103,
  "title": "Timeline conflict in Scene 6",
  "status": "open",
  "author": "continuity",
  "assignee": "writer",
  "priority": "normal",
  "tags": ["#continuity", "#scene-6", "#timeline"],
  "body": "Scene 2 establishes flotilla arrival...",
  "comments": [
    {
      "author": "writer",
      "body": "Good catch. I've created a patch...",
      "timestamp": 1735546800000,
      "action": {
        "type": "patch-created",
        "details": "Changed to 'barely a week since arrival'"
      }
    }
  ],
  "relatedFiles": [
    "story/chapter-1/scene-2.txt",
    "story/chapter-1/scene-6.txt"
  ],
  "createdAt": 1735543200000,
  "updatedAt": 1735546800000
}
```
--------------------------------------------------------------------------------------------------------------
#Refined Memory Degradation System

Interest Gradient (Let's Try 5 Levels)
```
type InterestLevel = 1 | 2 | 3 | 4 | 5;

interface IssueMemory {
  issueId: number;
  agentId: AgentRole;
  
  // Interest tracking
  interestLevel: InterestLevel;  // 1 = compressed qualia, 5 = full detail
  lastAccessed: timestamp;
  accessCount: number;
  
  // Decay tracking
  createdAt: timestamp;
  lastRefreshed: timestamp;
  
  // Learning signals
  wasUseful: boolean | null;     // Did this help solve a problem?
  appliedInWork: boolean;        // Did agent reference this in actual output?
  
  // Personal notes
  privateNote?: string;
  personalTags: string[];        // "character-reference", "world-building", etc.
}
```

<a id="memory-interest-levels"></a>
## Interest Level Definitions

**Level 5 – Active Working Memory**
```
Status: Currently relevant to active work
Storage: Full thread, all comments, all context
Decay: None (refreshed on every access)
Example: "Issue #203: Deep Current EM wavelength calculations"
         (Agent is currently writing a scene using this info)
```

**Level 4 — Recent & Useful**
```
Status: Used in past 2-4 weeks, proven helpful
Storage: Full thread, summary at top
Decay: Slow (drops to 3 after 1 month of non-use)
Example: "Issue #156: Seryn's mantle reaction patterns"
         (Referenced in 3 recent scenes, might need again)
```

**Level 3 — Background Knowledge**
```
Status: Understood but not actively used
Storage: Summary + first/last comments + key exchanges
Decay: Moderate (drops to 2 after 2 months)
Example: "Issue #089: Flotilla navigation protocols"
         (Good to know, not currently writing nav scenes)
```

**Level 2 — Dormant But Indexed**
```
Status: Old knowledge, rarely accessed
Storage: Title + resolution summary + tags
Decay: Fast (drops to 1 after 3 months)
Example: "Issue #034: Character naming conventions — Eastern European bias"
         (Decision was made, now just follow the rule)
```

**Level 1 – Semantic Trace (Qualia)**
```
Status: "I learned this once but it's compressed now"
Storage: Title + 1-sentence summary + tags only
Decay: None (terminal state, can be revived if accessed)
Example: "Issue #012: Aelyth color perception – decided on tetrachromatic"
         (Compressed to: "Aelyth see more colors than humans")```

## Design Alignment: Global Representations vs Per-Agent Access

This system uses existing IssueMemory interest levels without changing schemas:

- IssueMemory.interestLevel (1-5) remains per-agent and drives access demotion.
- Level 1 (Semantic Trace) is terminal and always accessible.
- All 5 detail levels are stored globally on each issue (compressionState).
- Representations can be sparse when an issue lacks substance (e.g., only L1/L3/L5 cached).
- Per-agent access determines which global level is injected into context.
- IssueMemoryService persists the per-agent access metadata.
- Epoch triggers (scene/chapter/act/draft) can scale decay pressure.
- Wall-clock fallback only handles abandoned projects and archives (visibility only), not deletions or pruning.
		 
##Dynamic Interest Management

Automatic Interest Adjustment
```
// When agent accesses an issue
function onIssueAccess(agentId: AgentRole, issueId: number) {
  const memory = getAgentMemory(agentId, issueId);
  
  // Refresh timestamp
  memory.lastAccessed = Date.now();
  memory.accessCount++;
  
  // Interest boost logic
  if (memory.interestLevel < 5) {
    // Reading something again signals it's useful
    if (timeSince(memory.lastAccessed) < ONE_WEEK) {
      // Repeated access in short time → boost interest
      memory.interestLevel = Math.min(5, memory.interestLevel + 1);
    }
  }
  
  memory.lastRefreshed = Date.now();
}

// When agent references issue in actual work (patch, comment, etc.)
function onIssueApplied(agentId: AgentRole, issueId: number) {
  const memory = getAgentMemory(agentId, issueId);
  
  memory.appliedInWork = true;
  memory.wasUseful = true;
  
  // Major interest boost — this is actionable knowledge
  memory.interestLevel = Math.min(5, memory.interestLevel + 2);
  memory.lastRefreshed = Date.now();
}

// When agent marks issue as not useful
function onIssueMarkedIrrelevant(agentId: AgentRole, issueId: number) {
  const memory = getAgentMemory(agentId, issueId);
  
  memory.wasUseful = false;
  
  // Rapid decay
  memory.interestLevel = 1;
  memory.personalTags.push("filtered-out");
}```

Decay Over Time (Background Process)
```
// Run nightly or weekly
function decayAgentMemories(agentId: AgentRole) {
  const memories = getAllMemories(agentId);
  
  memories.forEach(memory => {
    const timeSinceRefresh = Date.now() - memory.lastRefreshed;
    
    // Skip active working memory
    if (memory.interestLevel === 5) return;
    
    // Decay thresholds
    const decayRules = {
      4: { threshold: ONE_MONTH, decayTo: 3 },
      3: { threshold: TWO_MONTHS, decayTo: 2 },
      2: { threshold: THREE_MONTHS, decayTo: 1 },
      1: { threshold: Infinity, decayTo: 1 }  // Terminal state
    };
    
    const rule = decayRules[memory.interestLevel];
    
    if (timeSinceRefresh > rule.threshold) {
      // Decay one level
      memory.interestLevel = rule.decayTo;
      
      // Compress storage based on new level
      compressIssueStorage(memory);
    }
  });
}```
<a id="memory-compression-logic"></a>
##Issue Compression Logic

Storage Optimization by Interest Level
```
interface StoredIssue {
  base: {
    id: number;
    title: string;
    tags: string[];
    status: "open" | "closed";
    relatedFiles?: string[];
    relatedIssues?: number[];
  };
  repr: {
    L5?: { fullThread: Comment[] };
    L4?: { summary: string; fullThread: Comment[] };
    L3?: { summary: string; firstComment: Comment; lastComment: Comment; keyExchanges: Comment[] };
    L2?: { resolutionSummary: string };
    L1?: { semanticTrace: string };
  };
  reprComputedAt?: {
    L5?: timestamp;
    L4?: timestamp;
    L3?: timestamp;
    L2?: timestamp;
    L1?: timestamp;
  };
}

// The Chief of Staff can keep this sparse. If an issue is too small to justify
// all levels (e.g., only L1 + L3 + L5), missing levels are simply omitted.
function compressIssueStorage(memory: IssueMemory) {
  const issue = getIssue(memory.issueId);
  const stored = getStoredIssue(issue.id);
  const level = memory.interestLevel;

  // Compute/caches representation for the requested level only.
  switch(level) {
    case 5:
      stored.repr.L5 = { fullThread: issue.comments };
      stored.reprComputedAt.L5 = Date.now();
      break;

    case 4:
      stored.repr.L4 = {
        summary: generateSummary(issue),
        fullThread: issue.comments
      };
      stored.reprComputedAt.L4 = Date.now();
      break;

    case 3:
      stored.repr.L3 = {
        summary: generateSummary(issue),
        firstComment: issue.comments[0],
        lastComment: issue.comments[issue.comments.length - 1],
        keyExchanges: extractKeyExchanges(issue.comments)
      };
      stored.reprComputedAt.L3 = Date.now();
      break;

    case 2:
      stored.repr.L2 = {
        resolutionSummary: generateResolutionSummary(issue)
      };
      stored.reprComputedAt.L2 = Date.now();
      break;

    case 1:
      stored.repr.L1 = { semanticTrace: generateSemanticTrace(issue) };
      stored.reprComputedAt.L1 = Date.now();
      break;
  }

  saveStoredIssue(stored);
}
```

### Compression Examples

**Original Issue (Level 5):**
```
Issue #089: Flotilla Navigation Protocols

Planner (14 comments): 
[Full detailed exchange about navigation, sensor arrays,
 decision-making hierarchy, emergency protocols, etc.]
 
Resolution: Established 3-tier command structure with
automated failover. Seryn has override authority in
Deep Current events.

Total tokens: ~4,200
```

**Level 4 (Recent & Useful):**
```
Issue #089: Flotilla Navigation Protocols

Summary: Discussed navigation command structure and emergency
protocols. Established 3-tier system with Seryn override during
Deep Current events.

[Full 14-comment thread preserved]

Total tokens: ~4,300 (summary added)
```

**Level 3 (Background Knowledge):**
```
Issue #089: Flotilla Navigation Protocols

Summary: Navigation uses 3-tier command structure. Seryn can
override during Deep Current events. Automated failover exists.

First: "Planner: We need to establish clear navigation protocols..."
Last: "Resolution: 3-tier structure approved with emergency overrides."

Key exchanges:
- Tier 1: Automated systems
- Tier 2: Officer discretion  
- Tier 3: Commander override (Seryn in DC events)

Total tokens: ~800
```

**Level 2 (Dormant):**
```
Issue #089: Flotilla Navigation Protocols

Resolution: Navigation follows 3-tier command structure:
automated → officer → commander. Seryn override in
Deep Current events. Automated failover implemented.

Tags: #navigation #protocols #command-structure

Total tokens: ~150
```

**Level 1 (Semantic Trace):**
```
Issue #089: Flotilla Navigation Protocols

Trace: Navigation hierarchy established. Seryn has emergency authority.

Tags: #navigation #protocols

Total tokens: ~40```

<a id="memory-leech-detection"></a>
##The Leech Detection System

Identifying Failed Memories
```
interface LeechDetection {
  issueId: number;
  agentId: AgentRole;
  
  // Leech signals
  highAccessCount: boolean;      // Read many times
  lowApplication: boolean;       // Never used in work
  rapidDecay: boolean;          // Keeps dropping interest levels
  
  // Meta-memory of failure
  consecutiveNonUseful: number;  // "I keep reading this and it doesn't help"
}

function detectLeeches(agentId: AgentRole): LeechDetection[] {
  const memories = getAllMemories(agentId);
  
  return memories
    .filter(m => {
      return (
        m.accessCount > 5 &&              // Read frequently
        !m.appliedInWork &&               // Never actually used
        m.interestLevel <= 2              // But keeps decaying
      );
    })
    .map(m => ({
      issueId: m.issueId,
      agentId,
      highAccessCount: m.accessCount > 5,
      lowApplication: !m.appliedInWork,
      rapidDecay: hasRapidDecay(m),
      consecutiveNonUseful: countConsecutiveNonUseful(m)
    }));
}

// Leech intervention
function handleLeech(leech: LeechDetection) {
  const memory = getAgentMemory(leech.agentId, leech.issueId);
  
  // Option 1: Auto-mark as irrelevant
  memory.personalTags.push("leech-detected", "auto-filtered");
  memory.interestLevel = 1;
  
  // Option 2: Notify user (create issue about the issue!)
  createIssue({
    title: `Agent ${leech.agentId} struggling with Issue #${leech.issueId}`,
    author: "system",
    assignee: "user",
    tags: ["#meta", "#leech-detection"],
    body: `
      ${leech.agentId} has read Issue #${leech.issueId} ${memory.accessCount} times
      but never successfully applied it. This might indicate:
      
      - Information is irrelevant to ${leech.agentId}'s role
      - Issue is poorly formatted for agent comprehension
      - Conflict with other knowledge
      
      Recommendation: Review issue and either reformat, reassign, or mark irrelevant.
    `
  });
}```

##No Pruning or Deletion

We never prune or delete memories. All representations (L1–L5) are conserved.
Interest levels only control which representation is shown by default.
```
function shouldPrune(issue: Issue): boolean {
  return false;
}

function pruneIssue(issueId: number) {
  // No-op by policy: never delete or prune memories.
}```

<a id="memory-semantic-trace"></a>
##Semantic Trace Generation

This is the "qualia of memory" — what remains when everything else fades.
```
function generateSemanticTrace(issue: Issue): string {
  // Extract the essential lesson/decision
  const keyDecision = extractKeyDecision(issue);
  const impact = extractImpact(issue);
  
  // Compress to single sentence
  return `${issue.title}: ${keyDecision}. ${impact}`;
}

// Examples:
generateSemanticTrace(issue_089)
// → "Navigation protocols: 3-tier command with Seryn override. Affects emergency scenes."

generateSemanticTrace(issue_034)
// → "Character names: Eastern European bias corrected. Use diverse origins."

generateSemanticTrace(issue_156)
// → "Seryn's mantle: Tightens when stressed, ripples when curious. Key emotional indicator."
```

---

## UI Implications

### Issue Board with Interest Levels
```
┌─────────────────────────────────────────────────┐
│ Issue Board         [Show: ● Active  ○ All]     │
├─────────────────────────────────────────────────┤
│ #203 ⭐⭐⭐⭐⭐ Deep Current EM wavelengths        │
│ Planner → Writer  |  Working memory  |  2h ago  │
│ Full thread (12 comments) available             │
├─────────────────────────────────────────────────┤
│ #156 ⭐⭐⭐⭐☆ Seryn mantle reactions             │
│ Continuity → Writer  |  Recent useful  |  5d    │
│ Summary + thread available                      │
├─────────────────────────────────────────────────┤
│ #089 ⭐⭐⭐☆☆ Navigation protocols               │
│ Planner → All  |  Background  |  2mo            │
│ Summary + key points only                       │
├─────────────────────────────────────────────────┤
│ #034 ⭐⭐☆☆☆ Character naming conventions        │
│ Planner → Writer  |  Dormant  |  4mo            │
│ Resolution summary only                         │
├─────────────────────────────────────────────────┤
│ #012 ⭐☆☆☆☆ Aelyth color perception             │
│ Continuity → All  |  Archived  |  8mo           │
│ Trace: "Tetrachromatic vision decided"          │
│ [Revive Full Thread]                            │
└─────────────────────────────────────────────────┘
```

**Clicking a Level 1 issue:**
```
┌─────────────────────────────────────────────────┐
│ Issue #012: Aelyth Color Perception             │
│ Status: Archived (Level 1 memory)               │
├─────────────────────────────────────────────────┤
│ Semantic Trace:                                 │
│ "Aelyth have tetrachromatic vision, see more   │
│  colors than humans. UV perception canonical."  │
│                                                 │
│ Original thread archived 8 months ago.          │
│                                                 │
│ [Revive Full Thread] [Keep Compressed]          │
└─────────────────────────────────────────────────┘```
Reviving restores it to Level 3 and regenerates summary from archived data.

#Agent Experience

##Writer Agent Workflow

Morning routine (checks board):
```
const myIssues = searchIssues({
  assignedTo: "writer",
  minInterestLevel: 3,  // Only show background+ knowledge
  status: "open"
});

// Sees only:
// - Active issues assigned to me
// - Background knowledge I might need
// - Filters out compressed/archived automatically```
During scene writing:
```
// Writer references Deep Current mechanics
const relevantKnowledge = searchIssues({
  tags: ["#deep-current"],
  minInterestLevel: 2,  // Include dormant knowledge
  appliedInWork: true    // Show what actually helped before
});

// Reads Issue #203 (Level 5)
// → Interest stays high
// → Used in scene → appliedInWork = true

// Skips Issue #012 (Level 1) in search results
// unless explicitly requested
```

**Personal knowledge emerges:**
```
Writer's issue board becomes personalized:
- High interest: Deep Current, Seryn's mantle, EM perception
- Low interest: Navigation protocols, political structure
- Filtered: Medical terminology, planetary geology
```

---

## Why This Solves Your German/Japanese Problem

The 骨盤/三角筋 example is perfect (worked for a 接骨院 years ago, forgot most anatomical terms since):

**Traditional system:**
```
Memory: "骨盤 = pelvis, 三角筋 = deltoid"
Status: Forever in knowledge base at same priority
Problem: Useless to you now, but still taking up mental space


**This system:**

Memory created (Level 4): Medical terms actively used
↓ 2 years pass, no longer teaching anatomy
Decays to Level 2: "I learned anatomy terms once"
↓ 3 more years, never accessed
Decays to Level 1: "Semantic trace: Worked at 接骨院, knew body parts"
↓ If needed again → Revive to Level 3
The key insight: Memories that aren't used should degrade, but not disappear. The semantic trace ("I once knew this") is enough to trigger revival if needed again.

#Implementation Priority

##Phase 1 (MVP):

5-level interest system
Basic decay rules (monthly background process)
Compression to Level 3, 2, 1 formats
Search filtering by interest level

##Phase 2:

Leech detection
No pruning or deletion at any level
Semantic trace generation
Revival mechanism

##Phase 3:

Usage analytics (what gets applied in work?)
Predictive interest boosting
Cross-agent knowledge sharing (Planner marks something interesting → suggests to Writer)

--------------------------------------------------------------------------------------------------------------

#Potential Problems & Room for Improvement

A. The "Summarization Bias" Risk

When a memory decays to Level 2 or 1, a "Summary" or "Trace" is generated.

    The Risk: AI summaries often strip away the "voice" or "nuance" of a scene. If a memory decays too fast, the agent might lose the very thing that made the story feel alive.
    Improvement: When generating a Semantic Trace (Level 1), consider including one "Signature Quote" from the original text. For example: "Trace: Seryn's eyes are amber. 'Like trapped sunlight,' as the Critic put it." This small piece of prose helps the agent re-anchor to the "voice."
		Add signatureQuote to semantic trace (as above).
		For style/lore issues, maybe never decay below Level 2 unless user explicitly allows it.
		
B. Collaborative Memory vs. Personal Memory

In our spec, agents have Personal Tags (interesting/irrelevant).

    The Problem: What if the Planner thinks a world-building detail is "irrelevant" and lets it decay to Level 1, but the Writer needs it at Level 5 to write a specific scene?
    Improvement: We might need a "Request Revival" tool for agents. If the Writer sees a Level 1 trace they need, they should be able to "ping" the system to restore that issue to Level 4 for the duration of their task.
		Distinguish global importance from personal importance:
			Global: annotate Issues themselves with globalImportance: "core" | "normal" | "minor".
			Personal: IssueMemory.interestLevel + personalTags.
		Decay logic only touches global importance if all agents agree it’s minor.	

C. The "German/Japanese" Decay Logic

The example of forgetting medical terms (骨盤/三角筋) but remembering the "trace" that I once knew them might be useful.

    Technical Implementation: In cr_memory.md, we suggest a "nightly or weekly" decay. For a novelist, "Project Milestones" might be a better trigger. When a user finishes "Act 1," the system could trigger a "Deep Decay" for all issues specifically resolved in Act 1, clearing mental space for Act 2.
		Add both triggers: wall-clock and milestone.
		Milestone-based “Deep Decay” is perfect for acts or drafts.
	
D. The "Semantic Trace" (Level 1) Implementation

The 5-level interest gradient is a very sophisticated way to handle the "forgetting curve".
    Trace Recovery: Storing only the Title + 1-sentence summary + tags for Level 1 is extremely token-efficient.
    Refinement Idea (Manual Pinning): We might consider adding a isPinned: boolean flag to the IssueMemory interface. This would allow a user (or a high-level Planner) to "Lock" an issue at Level 5 (Active) regardless of the decay timer—essential for core thematic elements or "Golden Rules" of the magic system that must never decay.
	
--------------------------------------------------------------------------------------------------------------

1. The "Bulletin Board" Transformation (Cinema View 2.0)

We can reuse the CSS and layout logic from Conference Mode (Cinema View) in cr_workbench.md.
    Trigger: A toggle button on the Newsfeed header (e.g., "Expand to Board").
    Layout Shift: * The Agent Sidebar and Chat Panel compress and desaturate.
        The Newsfeed expands from its ~250px width to become the central "raised table" (~1050px).
        The visual treatment (shadows, background shifts) remains consistent with the Conference UI to ensure a unified design language.

2. Filtering & Sorting (Powered by the Memory Bank)
Because our Issue model in cr_memory.md is already so robust, the filtering logic is essentially a set of database queries:
    Agent Filter: This maps directly to the author and assignee fields. Selecting "Writer" would display issues where the Writer is either the lead or has contributed comments.
    Recency Filter: This uses the updatedAt timestamp. You could even tie this to the Decay Logic—showing only "Level 5" (Active) and "Level 4" (Warm) issues by default to keep the board clean.
    Severity Filter: This utilizes the priority field (low to urgent).
        Sorting Logic: Urgent + Waiting-on-User items would naturally bubble to the top.

3. The "Forum/GitHub" UI Design
Instead of a simple feed, the "Bulletin Board" view would transform each notification into an Issue Card:
    Header: Title, Issue ID (#104), Priority Badge, and Status.
    Sub-line: "Last activity by [Agent] X minutes ago".
    Expansion: Clicking a card doesn't just open a toast; it opens the full comment thread and linked entities (related files/scenes) directly in the board.
    Actions: Users could "Resolve," "Invite to Conference," or "Assign to [Agent]" directly from the card.

4. Why this fits our "Writer-First" Philosophy
    Contextual Awareness: By making the board a transformation of the feed, you prevent the user from feeling like they've left their book to enter a project management app.
    Strategic vs. Tactical: The Chat Panel remains the place for "Tactical" work (writing prose, immediate feedback), while the Bulletin Board becomes the place for "Strategic" work (fixing plot holes, managing the agent swarm).
    Roadmap Alignment: This fits naturally into Milestone 6.5 (AI Orchestration). It becomes the "Newsfeed" on steroids, providing the "Narrative Command Dashboard" you've planned.

Potential Risk: Conflict with Chat
If the user is in "Bulletin Board Mode" and an agent sends an urgent chat message, where does it go?
    Solution: Use the Status Bar. A small indicator could pulse next to the "Chat" icon: "Writer sent a message in Scene 4." This allows the user to finish their strategic review before switching back to tactical editing.
