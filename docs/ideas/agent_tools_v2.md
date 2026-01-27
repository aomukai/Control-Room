# Agent-Generated Tools System Design

## Core Concept
Agents can create, modify, and propose new prompt tools. All proposals go through Chief of Staff review before user approval. Tools are injected as a list (not full content) into every agent call, allowing agents to request tools they think will help.

## Workflow: Agent Proposes New Tool

### Step 1: Agent Creates Tool Proposal
```
Agent (e.g., Writer): "I keep analyzing scene pacing manually. I should create a tool for this."

Agent calls internal function:
POST /api/tools/propose
{
  "proposedBy": "agent-writer-001",
  "proposalType": "new",
  "name": "Scene Pacing Analyzer",
  "archetype": "Writer, Editor",
  "scope": "Any",
  "usageNotes": "Use when evaluating if a scene maintains narrative momentum",
  "goalsAndScope": "Analyzes beat density, dialogue/action ratio, and emotional pacing",
  "guardrails": "Does not rewrite scenes, only provides analysis",
  "promptText": "[Full prompt draft here]",
  "rationale": "I've been asked to check pacing 47 times this month. This tool would standardize the analysis.",
  "usageExamples": [
    "Check pacing in Act 2, Scene 3",
    "Does this dialogue scene drag?"
  ]
}
```

### Step 2: System Creates Issue for Chief of Staff
```
Issue #N: [Tool Proposal] Scene Pacing Analyzer
Opened by: SYSTEM
Assigned to: Chief of Staff

Description:
Writer proposed a new tool: "Scene Pacing Analyzer"

Rationale from Writer:
"I've been asked to check pacing 47 times this month. This tool would standardize the analysis."

Please review:
1. Is the tool scope clear and useful?
2. Are guardrails sufficient?
3. Does it overlap with existing tools?
4. Any suggested improvements?

[Link to draft tool in review queue]
```

### Step 3: Chief of Staff Reviews and Responds
Chief of Staff analyzes:
- Tool necessity (is it redundant?)
- Prompt quality (clear instructions?)
- Guardrails (safe boundaries?)
- Archetype scoping (right agents?)

Chief comments on issue:
```
Analysis:
✓ Non-redundant: Existing "Beat Architect" is structural, this is temporal
✓ Clear scope: Analyzes pacing without rewriting
✗ Guardrails need strengthening: Should specify "no plot suggestions"
✓ Good archetype scoping

Recommended changes:
1. Add guardrail: "Focus on pacing mechanics, not plot direction"
2. Consider adding "Critic" to archetype list
3. Suggest adding example failure cases to usage notes

Overall: APPROVE with modifications
```

### Step 4: System Creates Issue for User
```
Issue #N+1: [New Tool Proposal] Scene Pacing Analyzer
Opened by: Chief of Staff
Assigned to: USER

Description:
Writer has proposed a new tool. Chief of Staff reviewed and recommends APPROVAL with modifications.

**What the tool does:**
Analyzes scene pacing without rewriting content.

**Chief of Staff's evaluation:**
- Non-redundant with existing tools ✓
- Clear scope ✓
- Guardrails need strengthening
- Recommended archetype expansion

**Proposed modifications:**
1. Add guardrail: "Focus on pacing mechanics, not plot direction"
2. Add "Critic" to archetype list
3. Add example failure cases to usage notes

**Actions:**
→ Review tool in Settings → Prompt Tools
→ Approve as-is, approve with modifications, or reject

[View in Tool Editor] [Approve] [Reject]
```

### Step 5: User Reviews and Decides
User opens Prompt Tools editor, sees proposal with Chief's annotations.

Options:
- **Approve as-is**: Tool goes live immediately
- **Approve with modifications**: User edits, saves, tool goes live
- **Send back for revision**: Issue reopened, assigned to proposing agent
- **Reject with feedback**: Agent learns what didn't work

---

## Workflow: Agent Modifies Existing Tool

### Step 1: Agent Proposes Modification
```
Agent (Editor): "Beat Architect v1.0 doesn't handle flashback sequences well. I want to improve it."

POST /api/tools/propose
{
  "proposedBy": "agent-editor-002",
  "proposalType": "modification",
  "targetTool": "beat-architect",
  "currentVersion": "1.0",
  "proposedVersion": "1.1",
  "changeType": "enhancement",
  "modifications": {
    "guardrails": {
      "add": ["Distinguish between main timeline and flashback beats"]
    },
    "promptText": {
      "diff": "[Unified diff showing changes]"
    }
  },
  "rationale": "Failed 12 times on scenes with flashbacks. Users had to manually clarify timeline.",
  "testCases": [
    "Scene with flashback nested in main action",
    "Multiple timeline interleaving"
  ]
}
```

Same review flow as new tools:
1. Chief of Staff reviews modification
2. User gets issue with diff and recommendation
3. User approves/modifies/rejects

---

## Tool Injection System

### Tool List Format (Injected into Every Agent Call)
```json
{
  "availableTools": [
    {
      "id": "beat-architect",
      "name": "Beat Architect",
      "version": "1.0",
      "usageNotes": "Structures narrative beats into three-act framework",
      "archetype": "Writer, Editor",
      "yourRating": null,
      "yourUsageCount": 0,
      "isFavorite": false,
      "communityRating": 4.7,
      "communityUsageCount": 127
    },
    {
      "id": "scene-pacing-analyzer",
      "name": "Scene Pacing Analyzer", 
      "version": "1.0",
      "usageNotes": "Analyzes scene pacing without rewriting",
      "archetype": "Writer, Editor, Critic",
      "yourRating": 5,
      "yourUsageCount": 23,
      "isFavorite": true,
      "communityRating": 4.9,
      "communityUsageCount": 89
    }
  ]
}
```

Agent decides: "I need pacing analysis" → Requests full tool → Uses it

---

## Personal Ratings & Favorites System

### Agent Feedback Collection
After tool use:
```
POST /api/tools/{toolId}/feedback
{
  "agentId": "agent-writer-001",
  "rating": 5,
  "usageSuccess": true,
  "feedback": "Extremely helpful for Act 2 pacing checks",
  "wouldUseAgain": true
}
```

### Favorites Function
Agents can mark tools as favorites:
```
POST /api/tools/{toolId}/favorite
{
  "agentId": "agent-writer-001",
  "favorite": true
}
```

Benefits:
- Favorites appear first in tool list
- Agents don't scroll through 50 tools
- System learns which tools work best for which agents
- Users see "Writer uses this tool constantly" signals

### User-Facing Favorites
User can also favorite tools in Settings → Prompt Tools
- Quick access section at top
- "Your team's favorites" section (what agents use most)

---

## Tool Versioning System

### Version Tracking
```
Beat Architect:
- v1.0 (2026-01-15): Initial user creation
- v1.1 (2026-01-20): Ami suggested flashback handling [APPROVED]
- v1.2 (2026-01-22): User added guardrail about plot suggestions
- v2.0 (2026-01-25): Major overhaul by Editor [APPROVED]
```

### Upgrade Notifications
When a favorited tool gets upgraded:
```
Issue #N: [Tool Upgrade] Beat Architect v1.1 → v2.0
Opened by: SYSTEM
Assigned to: Writer, Editor (users of v1.1)

Description:
Your favorited tool "Beat Architect" has been upgraded to v2.0.

**What changed:**
- Added flashback sequence handling
- Improved three-act boundary detection
- New guardrail: Prevents plot direction suggestions

**Diff:**
[View unified diff of prompt changes]

**Impact on your workflow:**
Based on your usage patterns, this upgrade should improve:
- Flashback scene analysis (you've used this 12 times)
- Act boundary detection (you've used this 34 times)

**Actions:**
→ [View v2.0 in Tool Editor]
→ [See full changelog]
→ [Revert to v1.1 if preferred]

Note: Your rating and favorites have been transferred to v2.0.
```

Agents get similar notifications as issues, can test new version, provide feedback.

---

## Chief of Staff: User Prompt Review

### Workflow: User Creates New Tool
1. User creates tool in Prompt Tools editor
2. User saves tool
3. System triggers Chief of Staff review:
```
Issue #N: [User Tool Review] Aelyth Emotion Checker
Opened by: SYSTEM
Assigned to: Chief of Staff

Description:
User created a new tool: "Aelyth Emotion Checker"

Please analyze:
1. Clarity of instructions
2. Alignment with project lore (Aelyth guidelines)
3. Potential conflicts with existing tools
4. Suggested improvements

[Link to tool in editor]
```

### Chief of Staff Analysis
```
Chief comments on issue:

Analysis of "Aelyth Emotion Checker":

✓ Strengths:
- References Aelyth biological foundations correctly
- Clear usage scope (Aelyth POV scenes only)
- Good guardrails about electromagnetic terminology

⚠ Suggestions:
1. Prompt could reference specific lore documents for consistency
   Suggested addition: "Consult lore-aelyth-cognition.md section 2.3"

2. Consider expanding archetype to include Editor
   Rationale: Editors check consistency, would benefit from this

3. Usage notes could include example failure cases
   Example: "Not suitable for human-Aelyth comparison scenes"

4. Guardrails could specify handling of juvenile Aelyth
   Reference: lore-aelyth-biological-foundations.md section 5

Overall assessment: EXCELLENT
Suggested improvements: OPTIONAL (tool works well as-is)
```

### System Creates Issue for User
```
Issue #N+1: [Tool Review] Aelyth Emotion Checker
Opened by: Chief of Staff
Assigned to: USER

Description:
Your new tool "Aelyth Emotion Checker" has been reviewed.

**Overall assessment:** EXCELLENT ✓

**Chief of Staff's suggestions (optional):**

1. **Lore consistency enhancement:**
   Add reference to lore-aelyth-cognition.md section 2.3 for consistency checks.

2. **Expand archetype:**
   Consider adding "Editor" to archetype list. Editors check consistency and would benefit.

3. **Improve usage notes:**
   Add example failure cases like "Not suitable for human-Aelyth comparison scenes"

4. **Strengthen guardrails:**
   Specify handling of juvenile Aelyth per lore-aelyth-biological-foundations.md section 5

**Actions:**
→ [View tool in Prompt Tools]
→ [Implement suggestions] (optional)
→ [Dismiss review]

Note: Tool is already active. These are suggestions for improvement, not required changes.
```

User can:
- Implement all suggestions
- Cherry-pick specific suggestions
- Dismiss entirely (tool already works)

---

## Implementation Notes

### Database Schema
```sql
-- Tool proposals
CREATE TABLE tool_proposals (
  id UUID PRIMARY KEY,
  proposed_by VARCHAR, -- agent ID or 'USER'
  proposal_type VARCHAR, -- 'new' or 'modification'
  target_tool_id UUID, -- NULL for new tools
  status VARCHAR, -- 'pending_chief', 'pending_user', 'approved', 'rejected'
  created_at TIMESTAMP,
  
  -- Proposal data
  name VARCHAR,
  archetype VARCHAR,
  scope VARCHAR,
  usage_notes TEXT,
  goals_and_scope TEXT,
  guardrails TEXT,
  prompt_text TEXT,
  
  -- Metadata
  rationale TEXT,
  usage_examples JSONB
);

-- Tool versions
CREATE TABLE tool_versions (
  id UUID PRIMARY KEY,
  tool_id UUID REFERENCES tools(id),
  version VARCHAR,
  created_at TIMESTAMP,
  created_by VARCHAR, -- agent or user ID
  
  -- Version data
  prompt_text TEXT,
  guardrails TEXT,
  changelog TEXT,
  diff TEXT -- unified diff from previous version
);

-- Tool usage & ratings
CREATE TABLE tool_feedback (
  id UUID PRIMARY KEY,
  tool_id UUID REFERENCES tools(id),
  agent_id VARCHAR,
  rating INTEGER, -- 1-5
  usage_count INTEGER,
  is_favorite BOOLEAN,
  last_used TIMESTAMP,
  feedback_text TEXT
);
```

### API Endpoints
```
POST   /api/tools/propose                    # Agent proposes tool
GET    /api/tools/proposals                  # List pending proposals
POST   /api/tools/proposals/{id}/approve     # User approves proposal
POST   /api/tools/proposals/{id}/reject      # User rejects proposal

POST   /api/tools/{id}/feedback              # Agent rates tool
POST   /api/tools/{id}/favorite              # Agent favorites tool
GET    /api/tools/{id}/versions              # Get version history
GET    /api/tools/{id}/diff/{v1}/{v2}        # Get diff between versions

POST   /api/tools/request-review             # User requests Chief review
```

### Chief of Staff Configuration
```json
{
  "agentRole": "chief-of-staff",
  "responsibilities": [
    "review_tool_proposals",
    "review_user_tools",
    "analyze_tool_usage_patterns",
    "suggest_tool_improvements"
  ],
  "reviewCriteria": {
    "clarity": "Are instructions clear and unambiguous?",
    "loreConsistency": "Does it reference correct lore documents?",
    "redundancy": "Does it overlap with existing tools?",
    "safety": "Are guardrails sufficient?",
    "scoping": "Is archetype/scope appropriate?"
  },
  "autoReviewTriggers": [
    "new_tool_created",
    "tool_modification_proposed",
    "tool_usage_anomaly_detected"
  ]
}
```

---

## Future Enhancements

### Tool Analytics Dashboard
```
Beat Architect v2.0:
- Used 234 times by 3 agents
- Success rate: 94%
- Average response time: 3.2s
- Most common usage: Act 2 structure (67%)
- Failure patterns: Flashback-heavy scenes (6% failure rate)
- Agent ratings: ★★★★★ (4.8/5)
- User rating: ★★★★★ (5/5)
```

### Community Tool Marketplace
Eventually, allow sharing blessed tools:
```
Import from Community:
- Character Voice Consistency Checker (★★★★★ 1,247 uses)
- Three-Act Structure Validator (★★★★☆ 892 uses)
- Show Don't Tell Analyzer (★★★★★ 2,103 uses)

Each with version history, ratings, usage patterns.
```

### Automated Tool Testing
Before proposing tools, agents run automated tests:
```
Tool Test Results: "Scene Pacing Analyzer"
✓ Parsing test: Handles 500-5000 word scenes
✓ Edge case: Dialogue-only scenes
✓ Edge case: Action-only scenes
✗ Failed: Flashback sequences (needs improvement)
✓ Performance: <2s response time
✓ Safety: No plot suggestions generated

Test score: 83% (5/6 passed)
Recommendation: Fix flashback handling before proposing
```

---

## Philosophy Alignment

This system embodies Control Room's core philosophy:
- **Controlled experimentation**: Agents can try new tools, but with review
- **Transparent failure**: Tool usage tracked, failures analyzed
- **Human-in-loop**: User has final say on all tools
- **Collaborative intelligence**: Agents + Chief + User work together
- **Graceful learning**: System improves through feedback loops

It's the anti-Clawdbot: safe, transparent, collaborative self-improvement.