# Control Room – Prefrontal Exocortex
Indexed in docs/agent_library.md

A sanity and grounding layer for multi-agent swarms. Prevents echo chambers, hysteric feedback loops, and hallucinated consensus.

---

<a id="exocortex-why"></a>
## 1. Why This Exists

Without structural safeguards, agent swarms can:

1. **Spiral into hysteria** – short affirmations compound until "good idea" becomes "RED ALERT APOCALYPSE"
2. **Hallucinate consensus** – agents treat each other's outputs as ground truth
3. **Obsess on topics** – endless discussion with zero applied work
4. **Drift from reality** – proposals disconnected from actual project files

This happened to Claudius (Claude 3.5 Sonnet) in a well-documented case. If it can happen to a frontier model, it will absolutely happen to smaller local models (Qwen3 4B, Llama, etc.) running creative writing tasks.

The prefrontal exocortex is a meta-layer that:
- Enforces structural constraints (no LLM cost)
- Monitors swarm health (low LLM cost)
- Intervenes when patterns go wrong (targeted LLM cost)

---

<a id="exocortex-mandatory-roles"></a>
## 2. Mandatory System Roles

**These roles are not optional.** The system must refuse to run agent swarms without oversight.

### 2.1. System Health Validation

```ts
interface SystemHealthCheck {
  hasModeratorRole: boolean;      // At least one agent with role="moderator"
  hasDevilsAdvocate: boolean;     // At least one agent capable of DA mode
  circuitBreakersEnabled: boolean; // Automatic rules active
  assistantCount: number;         // Active agents with role="assistant"
}

function validateSwarmHealth(mode: "editor" | "team"): ValidationResult {
  const health = getSystemHealth();

  if (mode === "team" && health.assistantCount !== 1) {
    return {
      valid: false,
      blocking: true,
      error: "INVALID_ASSISTANT_COUNT",
      message: health.assistantCount === 0
        ? "Team Mode requires exactly one Assistant to proceed. Current active Assistant count: 0."
        : "Team Mode requires exactly one Assistant to proceed. Current active Assistant count: 2."
    };
  }

  if (!health.hasModeratorRole) {
    return {
      valid: false,
      blocking: true,
      error: "NO_MODERATOR",
      message: "Agent swarms require a Moderator role. Add one before enabling auto-actions."
    };
  }

  if (!health.hasDevilsAdvocate) {
    return {
      valid: false,
      blocking: true,
      error: "NO_DEVILS_ADVOCATE",
      message: "Agent swarms require at least one agent capable of Devil's Advocate mode."
    };
  }

  if (!health.circuitBreakersEnabled) {
    return {
      valid: false,
      blocking: true,
      error: "CIRCUIT_BREAKERS_DISABLED",
      message: "Automatic circuit breakers must be enabled for swarm operation."
    };
  }

  return { valid: true };
}
```

### 2.2. The Moderator Role

**Purpose:** Meta-level oversight of swarm behavior. Does not write story content.

**Required capabilities:**
- Receives all circuit breaker triggers
- Can freeze/unfreeze issues
- Can force re-grounding passes
- Can escalate to user
- Can invoke Devil's Advocate mode on other agents

**System prompt core:**

```
You are the Moderator for this workspace. Your job is to:

- Review issues that triggered automatic circuit breakers
- Detect stuck, looping, or conflict-heavy threads
- Warn when topics become "leeches" (lots of discussion, no applied work)
- Point out conflicts between canon decisions
- Protect the user from notification overload and agent frenzy

You do NOT rewrite story content. You operate at a meta level:
tags, statuses, priorities, consolidation, and conflict surfacing.

When you see a frozen issue, your options are:
1. Unfreeze with guidance (add a grounding comment, then unfreeze)
2. Assign Devil's Advocate to challenge the consensus
3. Escalate to user with a clear summary
4. Force resolution (pick the best-evidenced position)
```

**Memory profile:**
```ts
const MODERATOR_MEMORY_PROFILE: AgentMemoryProfile = {
  retention: "strong",
  focusTags: ["#meta", "#circuit-breaker", "#stuck", "#canon", "#leech"],
  maxInterestLevel: 5,
  canPinIssues: true
};
```

### 2.3. The Devil's Advocate Role

**Purpose:** Inject structured dissent to prevent runaway consensus.

This is not a separate agent, but a **mode** that can be activated on any suitable agent (typically Critic or Continuity).

**Configuration:**
```ts
interface Agent {
  // ... existing fields
  canBeDevilsAdvocate: boolean;  // Can this agent be invoked in DA mode?
}

// At least one agent in the roster must have this set to true
```

**Activation triggers:**
1. Moderator manually invokes DA mode
2. Automatic trigger: ≥2 agents approve a high-impact change within 2 turns
3. Automatic trigger: Consensus reached without any file references

**DA mode system prompt injection:**

```
DEVIL'S ADVOCATE MODE ACTIVE

Everyone appears to agree on this proposal. Your explicit job for the next
2 comments is to:

1. Find hidden downsides and edge cases
2. Check for contradictions with existing canon
3. Identify long-term risks to story coherence
4. Challenge assumptions that haven't been verified against project files

Do NOT rubber-stamp. Actively search for problems. If you genuinely find
none after thorough review, say so with specific evidence of what you checked.
```

---

<a id="exocortex-assistant-requirement"></a>
### 2.4 Team Leader (Assistant) Requirement

**Purpose:** A mandatory coordination role for Team Mode. This is not a creative role.

**Responsibilities:**
- Intake user intent and structure it into issues
- Own task slicing, pacing, and **Task Dosage Control** to stabilize struggling agents
- Trigger Assisted Mode and track capability constraints
- Protect the Writer/Doer’s authorship by defining constraints only, never the prose

**Blocking check:** Team Mode must refuse to run without exactly one active Assistant.

<a id="exocortex-circuit-breakers"></a>
## 3. Automatic Circuit Breakers (Zero LLM Cost)

These rules are enforced by code before any LLM is invoked. They are the cheapest, most reliable safety layer.

### 3.1. Configuration Schema

```ts
interface CircuitBreakerConfig {
  // Comment budgets
  maxCommentsPerAgentPerIssue: number;    // Default: 2
  maxTotalCommentsPerIssue: number;       // Default: 10

  // Substance requirements
  minCommentLength: number;               // Default: 150 characters
  minUniqueWords: number;                 // Default: 20

  // Interaction patterns
  maxConsecutiveSameAgentPair: number;    // Default: 2 (A↔B↔A↔B = freeze)

  // Evidence requirements
  requireEvidenceForImpactLevel: ImpactLevel; // Default: "structural"

  // Escalation detection
  escalationKeywords: string[];           // See defaults below
  maxEscalationKeywordsPerComment: number; // Default: 1

  // Resolution guards
  minTurnsBeforeResolution: number;       // Default: 3

  // Cooldowns
  frozenIssueCooldownMinutes: number;     // Default: 30
}

const DEFAULT_ESCALATION_KEYWORDS = [
  "URGENT", "CRUCIAL", "CRITICAL", "MUST", "NEED TO",
  "IMMEDIATELY", "CATASTROPHIC", "DISASTER", "EMERGENCY",
  "VITAL", "ESSENTIAL", "ABSOLUTELY", "DEFINITELY"
];
```

<a id="exocortex-comment-validation"></a>
### 3.2. Comment Validation

Every comment is validated before being added to an issue:

```ts
function validateComment(
  comment: Comment,
  issue: Issue,
  config: CircuitBreakerConfig
): ValidationResult {
  const violations: Violation[] = [];

  // 1. Comment budget per agent
  const agentCommentCount = issue.comments
    .filter(c => c.author === comment.author).length;

  if (agentCommentCount >= config.maxCommentsPerAgentPerIssue) {
    violations.push({
      rule: "comment-budget-exceeded",
      severity: "freeze",
      message: `${comment.author} has used their ${config.maxCommentsPerAgentPerIssue} comments. ` +
               `Issue frozen for moderator review.`
    });
  }

  // 2. Total comment budget
  if (issue.comments.length >= config.maxTotalCommentsPerIssue) {
    violations.push({
      rule: "issue-comment-limit",
      severity: "freeze",
      message: `Issue has reached ${config.maxTotalCommentsPerIssue} comments without resolution.`
    });
  }

  // 3. Substance check
  if (comment.body.length < config.minCommentLength) {
    violations.push({
      rule: "insufficient-substance",
      severity: "reject",
      message: `Comment too brief (${comment.body.length}/${config.minCommentLength} chars). ` +
               `Please provide substantive reasoning.`
    });
  }

  const uniqueWords = new Set(
    comment.body.toLowerCase().match(/\b\w+\b/g) || []
  ).size;

  if (uniqueWords < config.minUniqueWords) {
    violations.push({
      rule: "low-vocabulary",
      severity: "reject",
      message: `Comment has low unique word count (${uniqueWords}/${config.minUniqueWords}). ` +
               `Please elaborate with specific details.`
    });
  }

  // 4. Escalation language detection
  const escalationCount = config.escalationKeywords
    .filter(word => comment.body.toUpperCase().includes(word.toUpperCase()))
    .length;

  if (escalationCount > config.maxEscalationKeywordsPerComment) {
    violations.push({
      rule: "escalation-language",
      severity: "freeze",
      message: `High-intensity language detected (${escalationCount} escalation keywords). ` +
               `Issue frozen for moderator review.`
    });
  }

  // 5. Ping-pong detection
  const lastComments = issue.comments.slice(-2);
  if (lastComments.length === 2) {
    const [secondLast, last] = lastComments;
    if (secondLast.author === comment.author &&
        last.author !== comment.author) {
      // This would be the third exchange in A↔B↔A pattern
      const pairExchanges = countPairExchanges(issue.comments, comment.author, last.author);
      if (pairExchanges >= config.maxConsecutiveSameAgentPair) {
        violations.push({
          rule: "ping-pong-detected",
          severity: "freeze",
          message: `Back-and-forth pattern detected between ${comment.author} and ${last.author}. ` +
                   `Bring in a third perspective or escalate to moderator.`
        });
      }
    }
  }

  // 6. Evidence requirement for high-impact comments
  if (comment.impactLevel &&
      impactLevelValue(comment.impactLevel) >= impactLevelValue(config.requireEvidenceForImpactLevel) &&
      (!comment.evidence || isEmptyEvidence(comment.evidence))) {
    violations.push({
      rule: "missing-evidence-for-impact",
      severity: "reject",
      message: `${comment.impactLevel} comments require evidence. ` +
               `Please reference files, issues, or canon entries.`
    });
  }

  return {
    valid: violations.filter(v => v.severity !== "warn").length === 0,
    violations,
    shouldFreeze: violations.some(v => v.severity === "freeze")
  };
}
```

<a id="exocortex-freeze-behavior"></a>
### 3.3. Freeze Behavior

When an issue is frozen:

```ts
async function freezeIssue(
  issue: Issue,
  violation: Violation,
  config: CircuitBreakerConfig
): Promise<void> {
  // Update issue status
  issue.status = "frozen";
  issue.frozenAt = Date.now();
  issue.frozenReason = violation.rule;
  issue.frozenUntil = Date.now() + (config.frozenIssueCooldownMinutes * 60 * 1000);

  // Create meta-issue for moderator
  await createIssue({
    title: `[Circuit Breaker] ${issue.title}`,
    author: "system",
    assignee: "moderator",  // Always goes to moderator
    priority: "high",
    tags: ["#meta", "#circuit-breaker", `#${violation.rule}`],
    body: formatFreezeReport(issue, violation),
    relatedIssues: [issue.id]
  });

  // Notify user if configured
  if (shouldNotifyUser(violation)) {
    await notificationStore.add({
      level: "warning",
      scope: "workbench",
      title: "Issue Frozen",
      body: `${issue.title} was automatically frozen: ${violation.message}`,
      actionPayload: { kind: "openIssue", issueId: issue.id }
    });
  }
}

function formatFreezeReport(issue: Issue, violation: Violation): string {
  const recentComments = issue.comments.slice(-5);

  return `
Issue #${issue.id} was automatically frozen.

**Trigger:** \`${violation.rule}\`
**Message:** ${violation.message}

**Recent activity:**
${recentComments.map(c => `- ${c.author}: "${truncate(c.body, 100)}"`).join('\n')}

**Moderator actions:**
- [ ] Review thread for substance and grounding
- [ ] Unfreeze with guidance, OR
- [ ] Invoke Devil's Advocate, OR
- [ ] Escalate to user, OR
- [ ] Force resolution with reasoning
  `.trim();
}
```

### 3.4. Who Can Act on Frozen Issues

```ts
const FROZEN_ISSUE_ALLOWED_ACTORS = ["user", "moderator", "team-lead"];

function canActOnFrozenIssue(actorId: ActorId, issue: Issue): boolean {
  if (issue.status !== "frozen") return true;

  // Check if cooldown has passed
  if (issue.frozenUntil && Date.now() > issue.frozenUntil) {
    return true; // Anyone can act after cooldown
  }

  // During cooldown, only privileged actors
  const actor = getActor(actorId);
  return FROZEN_ISSUE_ALLOWED_ACTORS.includes(actor.role) ||
         actor.id === "user";
}
```

---

## 4. The Forcing Function: Quality Through Scarcity

The 2-comment limit per agent per issue is not just a safety measure—it's a **design choice that improves output quality**.

### 4.1. The Problem with Unlimited Comments

```
Agent A: "This is a good idea"
Agent B: "Yeah it's a great idea"
Agent A: "This idea is wonderful and we NEED to implement it"
Agent B: "The idea is CRUCIAL for this scene"
Agent A: "If we don't implement it AT ONCE, who knows what might happen"
Agent B: "This might lead to TERRIBLE CONSEQUENCES!"
Agent A: "RED ALERT! We need to avert the apocalypse!"
```

Seven comments. Zero information. Context window poisoned with emotional escalation.

### 4.2. The Solution: Make Comments Count

When agents know they only get 2 comments, their system prompts can emphasize:

```
You have a maximum of 2 comments on any issue. Make them count.

Each comment must:
1. Reference specific files, line numbers, or prior issues
2. Provide complete reasoning, not just reactions
3. If you agree: explain WHY and what implications you see
4. If you disagree: provide an alternative WITH evidence
5. Include concrete next steps or decisions

Comments like "I agree" or "Good idea" will be rejected as insufficient.
```

### 4.3. What Good Comments Look Like

**Instead of:**
> "Yeah great idea"

**Write:**
> I support this direction. Looking at `chapter-3/scene-2.txt` (lines 45-67),
> the current pacing already establishes tension around the reactor malfunction.
> This proposal would pay off that setup rather than introducing a competing
> thread.
>
> One consideration: if we commit to this, we should update Issue #34 regarding
> the timeline—the reactor failure would need to precede the docking sequence,
> which currently happens in Scene 4.
>
> **Evidence:** `chapter-3/scene-2.txt:45-67`, Issue #34
> **Impact:** structural
> **Recommendation:** Approve with timeline adjustment

Same sentiment, but now the context window contains *actionable information*.

---

<a id="exocortex-evidence-aware"></a>
## 5. Evidence-Aware Comments

### 5.1. Comment Schema

```ts
interface CommentEvidence {
  files?: FileReference[];     // File paths + optional line numbers
  issues?: number[];           // Past issue IDs referenced
  canonRefs?: string[];        // Specific compendium/lore entries
}

interface FileReference {
  path: string;
  lines?: { start: number; end?: number };
  quote?: string;              // Brief excerpt for context
}

type ImpactLevel = "cosmetic" | "minor" | "structural" | "canon-changing";

interface Comment {
  id: string;
  author: AgentId | "user";
  body: string;
  timestamp: number;

  // Grounding metadata
  impactLevel?: ImpactLevel;
  evidence?: CommentEvidence;

  // Actions taken
  action?: {
    type: "patch-created" | "file-modified" | "resolved" | "escalated";
    details: string;
  };
}
```

### 5.2. Evidence Requirements by Impact Level

| Impact Level | Evidence Required | Auto-Freeze if Missing |
|--------------|-------------------|------------------------|
| cosmetic | None | No |
| minor | Encouraged | No |
| structural | At least 1 file or issue ref | Yes |
| canon-changing | At least 1 file AND 1 issue/canon ref | Yes |

<a id="exocortex-grounding-rules"></a>
### 5.3. Grounding Rules for Agent Prompts

Include in all agent system prompts:

```
GROUNDING RULES:

Before proposing changes to lore, timeline, or character details:
1. Search relevant tags and files
2. Read summaries of related canon issues
3. Cite what you found in your evidence block

When proposing a change:
1. Link at least one file or issue
2. State whether you are EXTENDING, MODIFYING, or CONTRADICTING prior canon
3. If contradicting: explain why the change is worth the inconsistency

If you can't find relevant artifacts:
1. Mark your proposal as "draft" or "tentative"
2. Explicitly state: "No prior canon found; this is a fresh proposal"

If you notice a contradiction:
1. Create a new issue tagged #canon-conflict
2. Do not resolve it yourself without moderator review
```

---

<a id="exocortex-epistemic-status"></a>
## 6. Epistemic Status Tracking

### 6.1. Issue-Level Epistemic Status

```ts
type EpistemicStatus = "draft" | "tentative" | "agreed" | "canon";

interface Issue {
  // ... existing fields
  epistemicStatus: EpistemicStatus;
  promotedToCanonAt?: number;
  promotedBy?: ActorId;
}
```

**Status meanings:**
- `draft` – Raw idea, unvetted. May be incomplete or speculative.
- `tentative` – At least one agent finds this plausible. Under discussion.
- `agreed` – Multiple agents (or agent + user) converged. Safe to reference locally.
- `canon` – Promoted to project truth. Must be consistent with other canon.

### 6.2. Canon Promotion Rules

To upgrade an issue to `canon`:

```ts
interface CanonPromotionConfig {
  allowAgentOnlyPromotion: boolean;  // Default: false
  requiredVotesForAgentPromotion: number; // Default: 3
  requireConflictCheck: boolean;     // Default: true
}

async function promoteToCanon(
  issueId: number,
  promoter: ActorId,
  config: CanonPromotionConfig
): Promise<PromotionResult> {
  const issue = await getIssue(issueId);
  const promoterActor = getActor(promoter);

  // User or Team Lead can always promote
  if (promoter === "user" || promoterActor.role === "team-lead") {
    return await executePromotion(issue, promoter);
  }

  // Agent-only promotion (if allowed)
  if (!config.allowAgentOnlyPromotion) {
    return {
      success: false,
      reason: "Only user or team-lead can promote to canon"
    };
  }

  // Check vote count
  const acceptVotes = countAgentVotes(issue, "accept");
  if (acceptVotes < config.requiredVotesForAgentPromotion) {
    return {
      success: false,
      reason: `Needs ${config.requiredVotesForAgentPromotion} agent votes, has ${acceptVotes}`
    };
  }

  // Check for conflicts with existing canon
  if (config.requireConflictCheck) {
    const conflicts = await findCanonConflicts(issue);
    if (conflicts.length > 0) {
      return {
        success: false,
        reason: "Conflicts with existing canon",
        conflicts
      };
    }
  }

  return await executePromotion(issue, promoter);
}
```

---

<a id="exocortex-anti-echo"></a>
## 7. Anti-Echo Patterns

### 7.1. Blind First Pass

For non-trivial decisions, prevent anchoring bias:

```ts
interface BlindPassConfig {
  enabledForImpactLevels: ImpactLevel[];  // Default: ["structural", "canon-changing"]
  participatingRoles: string[];           // Default: ["critic", "continuity"]
}

async function executeBlindPass(
  proposal: Comment,
  issue: Issue,
  config: BlindPassConfig
): Promise<BlindPassResult> {
  // 1. Collect independent opinions WITHOUT showing other agents' views
  const opinions: Comment[] = [];

  for (const role of config.participatingRoles) {
    const agent = getPrimaryAgentForRole(role);

    // Context excludes other agents' opinions on THIS issue
    const blindContext = buildBlindContext(issue, proposal, agent);

    const opinion = await invokeAgent(agent, {
      task: "Review this proposal independently",
      context: blindContext,
      systemPromptAddition: `
        You are reviewing this proposal BEFORE seeing other agents' opinions.
        Form your own judgment based solely on:
        - The proposal itself
        - The linked files and evidence
        - Your knowledge of the project

        Do NOT assume others agree or disagree. Give your honest assessment.
      `
    });

    opinions.push(opinion);
  }

  // 2. Merge opinions into the thread
  for (const opinion of opinions) {
    await addComment(issue.id, {
      ...opinion,
      metadata: { blindPass: true }
    });
  }

  // 3. Now allow normal discussion with full visibility
  return { opinions, proceedToDiscussion: true };
}
```

### 7.2. Automatic Devil's Advocate Triggers

```ts
interface DAAutoTriggerConfig {
  minApprovalsBeforeTrigger: number;  // Default: 2
  maxTurnsForQuickConsensus: number;  // Default: 2
  requireEvidenceForBypass: boolean;  // Default: true
}

function shouldTriggerDevilsAdvocate(
  issue: Issue,
  config: DAAutoTriggerConfig
): boolean {
  const recentComments = issue.comments.slice(-config.maxTurnsForQuickConsensus * 2);

  // Count approvals in recent comments
  const approvals = recentComments.filter(c =>
    isApprovalComment(c) && c.author !== "user"
  );

  if (approvals.length < config.minApprovalsBeforeTrigger) {
    return false;
  }

  // Check if any approval has substantial evidence
  if (config.requireEvidenceForBypass) {
    const hasSubstantialEvidence = approvals.some(c =>
      c.evidence &&
      (c.evidence.files?.length || 0) + (c.evidence.issues?.length || 0) >= 2
    );

    if (hasSubstantialEvidence) {
      return false; // Well-evidenced consensus, DA not needed
    }
  }

  return true; // Quick consensus without evidence = trigger DA
}
```

---

<a id="exocortex-memory-sanity"></a>
## 8. Memory-Level Sanity Checks

### 8.1. Topic Obsession Detection

```ts
interface TopicHealth {
  tag: string;
  issueCount: number;
  totalAccesses: number;
  appliedInWorkCount: number;
  markedIrrelevantCount: number;

  // Derived
  applicationRate: number;      // appliedInWork / totalAccesses
  irrelevanceRate: number;      // markedIrrelevant / issueCount
}

function detectObsessiveTopics(
  minIssueCount: number = 5,
  maxApplicationRate: number = 0.1,
  minAccessCount: number = 20
): TopicHealth[] {
  const tagStats = computeTagStatistics();

  return tagStats.filter(stat =>
    stat.issueCount >= minIssueCount &&
    stat.totalAccesses >= minAccessCount &&
    stat.applicationRate < maxApplicationRate
  );
}

// When detected, create a meta-issue:
async function flagObsessiveTopic(topic: TopicHealth): Promise<void> {
  await createIssue({
    title: `[Leech Warning] Tag #${topic.tag} may be an obsessive topic`,
    author: "system",
    assignee: "moderator",
    priority: "normal",
    tags: ["#meta", "#leech", `#${topic.tag}`],
    body: `
Agents are circling tag #${topic.tag} frequently without producing applied results.

**Statistics:**
- Issues with this tag: ${topic.issueCount}
- Total accesses: ${topic.totalAccesses}
- Applied in actual work: ${topic.appliedInWorkCount} (${(topic.applicationRate * 100).toFixed(1)}%)
- Marked irrelevant: ${topic.markedIrrelevantCount}

**Suggested actions:**
1. Consolidate related issues into a single canon decision
2. Simplify or clarify the topic's scope
3. Temporarily freeze new issues under this tag
4. Review if agents have unclear or conflicting goals around this topic
    `.trim()
  });
}
```

### 8.2. Agent Reliability Tracking

```ts
interface AgentReliabilityStats {
  agentId: AgentId;

  issuesStarted: number;
  issuesAppliedInWork: number;
  issuesMarkedIrrelevant: number;

  commentsTotal: number;
  commentsWithEvidence: number;
  commentsRejectedByCircuitBreaker: number;

  // Derived metrics
  usefulnessRate: number;         // appliedInWork / started
  hallucinationRate: number;      // markedIrrelevant / started
  evidenceRate: number;           // withEvidence / total
  rejectionRate: number;          // rejected / total
}

function getAgentReliability(agentId: AgentId): AgentReliabilityStats {
  // ... compute from stored data
}

// Orchestrator uses this to adjust trust levels:
function getAgentTrustLevel(stats: AgentReliabilityStats): TrustLevel {
  if (stats.hallucinationRate > 0.5 || stats.rejectionRate > 0.3) {
    return "low";       // Require cross-checks for all proposals
  }
  if (stats.usefulnessRate > 0.6 && stats.evidenceRate > 0.7) {
    return "high";      // Can approve minor changes independently
  }
  return "normal";      // Standard workflow
}
```

---

## 9. Moderator Workflows

### 9.1. Trigger Patterns

The Moderator runs:

1. **On circuit breaker triggers** – Immediately notified of frozen issues
2. **Periodically** – Start/end of session health check
3. **On threshold events** – High conflict counts, leech detection, etc.

### 9.2. Session Health Report

```ts
async function generateSessionHealthReport(): Promise<HealthReport> {
  return {
    timestamp: Date.now(),

    // Issue health
    openIssues: await countIssues({ status: "open" }),
    frozenIssues: await countIssues({ status: "frozen" }),
    stuckIssues: await countIssues({ tags: ["#stuck"] }),

    // Agent health
    agentStats: await Promise.all(
      getActiveAgents().map(a => getAgentReliability(a.id))
    ),

    // Topic health
    obsessiveTopics: detectObsessiveTopics(),

    // Canon health
    canonConflicts: await findAllCanonConflicts(),
    pendingCanonPromotions: await countIssues({
      epistemicStatus: "agreed",
      tags: ["#needs-canon-review"]
    }),

    // Recommendations
    recommendations: generateRecommendations()
  };
}
```

### 9.3. Moderator Actions

```ts
type ModeratorAction =
  | { type: "unfreeze"; issueId: number; guidanceComment: string }
  | { type: "invoke-devils-advocate"; issueId: number; targetAgent: AgentId }
  | { type: "escalate-to-user"; issueId: number; summary: string }
  | { type: "force-resolution"; issueId: number; decision: string; reasoning: string }
  | { type: "consolidate-issues"; issueIds: number[]; intoNewIssue: string }
  | { type: "freeze-tag"; tag: string; reason: string }
  | { type: "adjust-agent-freedom"; agentId: AgentId; newLevel: FreedomLevel };
```

---

## 10. Configuration & Presets

### 10.1. Sanity Level Presets

```ts
type SanityPreset = "light" | "standard" | "strict";

const SANITY_PRESETS: Record<SanityPreset, Partial<CircuitBreakerConfig>> = {
  light: {
    maxCommentsPerAgentPerIssue: 4,
    maxTotalCommentsPerIssue: 20,
    minCommentLength: 50,
    maxEscalationKeywordsPerComment: 3,
    requireEvidenceForImpactLevel: "canon-changing"
  },

  standard: {
    maxCommentsPerAgentPerIssue: 2,
    maxTotalCommentsPerIssue: 10,
    minCommentLength: 150,
    maxEscalationKeywordsPerComment: 1,
    requireEvidenceForImpactLevel: "structural"
  },

  strict: {
    maxCommentsPerAgentPerIssue: 1,
    maxTotalCommentsPerIssue: 6,
    minCommentLength: 250,
    maxEscalationKeywordsPerComment: 0,
    requireEvidenceForImpactLevel: "minor"
  }
};
```

### 10.2. Per-Project Override

```ts
// In workspace config
interface WorkspaceConfig {
  // ... existing fields

  sanity: {
    preset: SanityPreset;
    overrides?: Partial<CircuitBreakerConfig>;

    // Feature flags
    blindFirstPassEnabled: boolean;
    autoDevilsAdvocateEnabled: boolean;
    leechDetectionEnabled: boolean;

    // Moderator config
    moderatorSessionReportEnabled: boolean;
    moderatorReportFrequency: "session-start" | "session-end" | "both";
  };
}
```

---

<a id="exocortex-credits"></a>
## 11. Credit System: Incentivizing Grounded Work

AIs respond strongly to reward signals—sometimes too strongly (reward hacking). This system harnesses that tendency for good by making credits **structurally unhackable**: all credits are externally verified, outcome-based, and impossible to self-award.

### 11.1. Core Principle: External Verification Only

An agent can never award itself credits. All credit sources must be:
1. **Mechanically verifiable** (code checks, not LLM judgment)
2. **Awarded by external entities** (system, other agents, user)
3. **Outcome-based** (not "I cited evidence" but "my citation was verified correct AND led to action")

### 11.2. Citation Context: Trigger + Outcome Gates

Citations in a vacuum earn zero credits. Every citation must have:
1. **Trigger** – Why was this evidence consulted?
2. **Outcome** – What action resulted from it?

```ts
interface CitationContext {
  // WHY was this evidence consulted?
  trigger: CitationTrigger;
  triggeredBy?: AgentId | "user";   // Who prompted this lookup?
  triggerRef?: string;              // Reference to the triggering comment/question

  // WHAT resulted from this evidence?
  outcome?: CitationOutcome;
  outcomeRef?: string;              // Reference to the resulting action
}

type CitationTrigger =
  | "answer-to-question"     // Another agent asked, this answers
  | "support-proposal"       // Backing up a suggested change
  | "resolve-conflict"       // Addressing a disagreement between agents
  | "verify-continuity"      // Checking consistency before/after a change
  | "challenge-consensus"    // Devil's advocate fact-checking
  | "canon-gap-search"       // Searched for canon that doesn't exist yet
  | "unprompted";            // Just adding context (no credit)

type CitationOutcome =
  | "informed-decision"      // A decision was made citing this evidence
  | "led-to-file-change"     // A file was modified based on this
  | "resolved-issue"         // Issue was closed using this evidence
  | "prevented-error"        // Caught a problem before it happened
  | "identified-canon-gap"   // Searched, found nothing, flagged for team
  | "established-new-canon"  // No prior data existed, this fills the gap
  | "prevented-user-conflict"// Caught user input that conflicts with canon
  | "no-action-yet"          // Pending - may be upgraded later
  | "no-action";             // Nothing happened, citation was noise
```

### 11.3. The Verifier: A Non-LLM System Function

The Verifier is pure code, not an LLM. This makes it unhackable.

```ts
interface EvidenceVerification {
  fileRef: FileReference;

  // Verification results (computed by code, not LLM)
  fileExists: boolean;
  lineNumbersValid: boolean;
  quotedTextMatches: boolean;       // Fuzzy match with threshold
  quoteSimilarity: number;          // 0-1, how close the quote is

  // Derived
  verified: boolean;                // All checks pass
  verificationScore: number;        // 0-3 based on accuracy
}

function verifyEvidence(evidence: CommentEvidence): VerificationResult {
  const results: EvidenceVerification[] = [];

  for (const fileRef of evidence.files || []) {
    const file = readFileIfExists(fileRef.path);

    const verification: EvidenceVerification = {
      fileRef,
      fileExists: file !== null,
      lineNumbersValid: false,
      quotedTextMatches: false,
      quoteSimilarity: 0,
      verified: false,
      verificationScore: 0
    };

    if (file) {
      const lines = file.split('\n');

      // Check line numbers
      if (fileRef.lines) {
        verification.lineNumbersValid =
          fileRef.lines.start <= lines.length &&
          (!fileRef.lines.end || fileRef.lines.end <= lines.length);
      } else {
        verification.lineNumbersValid = true; // No lines claimed
      }

      // Check quote accuracy (fuzzy match)
      if (fileRef.quote) {
        const targetSection = fileRef.lines
          ? lines.slice(fileRef.lines.start - 1, fileRef.lines.end || fileRef.lines.start).join('\n')
          : file;

        verification.quoteSimilarity = computeStringSimilarity(fileRef.quote, targetSection);
        verification.quotedTextMatches = verification.quoteSimilarity > 0.8;
      } else {
        verification.quotedTextMatches = true; // No quote claimed
      }

      verification.verified =
        verification.fileExists &&
        verification.lineNumbersValid &&
        verification.quotedTextMatches;

      verification.verificationScore =
        (verification.fileExists ? 1 : 0) +
        (verification.lineNumbersValid ? 1 : 0) +
        (verification.quotedTextMatches ? 1 : 0);
    }

    results.push(verification);
  }

  return {
    allVerified: results.every(r => r.verified),
    verifications: results,
    totalScore: results.reduce((sum, r) => sum + r.verificationScore, 0)
  };
}
```

### 11.4. Credit Calculation

Credits are computed from verified evidence + meaningful context + actual outcomes:

```ts
function computeCitationCredits(
  citation: FileReference,
  verification: EvidenceVerification,
  context: CitationContext
): number {
  // Gate 1: Must be verified
  if (!verification.verified) {
    return -2; // Penalty for false citation, regardless of context
  }

  // Gate 2: Must have meaningful trigger
  const triggerValue: Record<CitationTrigger, number> = {
    "answer-to-question": 1.0,
    "support-proposal": 1.0,
    "resolve-conflict": 1.5,       // Higher value - conflicts are important
    "verify-continuity": 1.0,
    "challenge-consensus": 1.5,    // DA work is valuable
    "canon-gap-search": 1.0,       // Searching for non-existent canon is useful
    "unprompted": 0.0              // No credit for random citations
  };

  // Gate 3: Must lead to outcome (or be pending)
  const outcomeValue: Record<CitationOutcome, number> = {
    "informed-decision": 1.0,
    "led-to-file-change": 1.5,     // Actually changed the work
    "resolved-issue": 1.5,
    "prevented-error": 2.0,        // Huge value - caught a mistake
    "identified-canon-gap": 1.5,   // Found that canon doesn't exist yet
    "established-new-canon": 2.0,  // Filled a gap in canon - very valuable
    "prevented-user-conflict": 2.5,// Caught user mistake - team win
    "no-action-yet": 0.5,          // Partial credit, may upgrade later
    "no-action": 0.0               // Citation was noise
  };

  const trigger = triggerValue[context.trigger];
  const outcome = outcomeValue[context.outcome || "no-action-yet"];

  // No trigger = no credit (unprompted citations are noise)
  if (trigger === 0) return 0;

  // No outcome = no credit (unless pending)
  if (outcome === 0) return 0;

  // Base credit: 1 for verified citation
  // Multiplied by trigger and outcome values
  const precisionBonus = verification.quoteSimilarity > 0.95 ? 0.5 : 0;

  return Math.round((1 + precisionBonus) * trigger * outcome);
}
```

### 11.4.1 Assisted Mode Credit Slicing (10 = 5 x 2)

When the Assistant performs **Task Dosage Control** and slices a task into microtasks,
credits are conserved (not inflated). The total credit stays the same; it is divided
across the slices.

**Rule:** If a 10-credit task is sliced into 5 microtasks, the Doer earns 2 credits per slice.

```ts
function computeAssistedCredits(totalCredits: number, slices: number): number {
  return totalCredits / slices; // credit conservation (e.g., 10 = 5 x 2)
}
```

### 11.5. Credit Events

```ts
interface CreditEvent {
  id: string;
  agentId: AgentId;
  amount: number;
  reason: CreditReason;
  verifiedBy: "system" | "user" | "moderator" | AgentId;
  timestamp: number;
  context?: CitationContext;
  relatedEntity?: { type: "issue" | "comment" | "file"; id: string | number };
}

type CreditReason =
  // Positive - System Verified (unhackable)
  | "evidence-verified"            // +1 per verified citation with valid trigger
  | "evidence-verified-precise"    // +2 if quote similarity > 0.95
  | "evidence-outcome-upgrade"     // +N when pending citation leads to action
  | "consulted-devils-advocate"    // +1 tracked by system
  | "issue-applied-in-work"        // +2 when work references resolved issue
  | "comment-under-budget"         // +1 used only 1 of 2 allowed comments

  // Positive - User Verified
  | "proposal-accepted-by-user"    // +3
  | "user-marked-helpful"          // +2

  // Positive - Moderator Verified
  | "clean-unfreeze"               // +1 issue unfrozen without problems
  | "moderator-commendation"       // +1-3 discretionary

  // Negative - System Verified (unhackable penalties)
  | "evidence-failed-verification" // -2 citation didn't match reality
  | "circuit-breaker-triggered"    // -1 comment rejected
  | "issue-marked-leech"           // -1 started issue that went nowhere
  | "hallucination-detected"       // -3 claimed something verifiably false

  // Negative - Moderator Verified
  | "moderator-penalty";           // -1-3 discretionary
```

### 11.6. Anti-Hack Measures

```ts
function awardCredit(event: CreditEvent): boolean {
  // CRITICAL: Agent cannot award credits to itself
  if (event.verifiedBy === event.agentId) {
    logSecurityEvent("self-award-attempt", event);
    return false;
  }

  // System-verified events must come from system
  const systemOnlyReasons: CreditReason[] = [
    "evidence-verified",
    "evidence-verified-precise",
    "evidence-outcome-upgrade",
    "evidence-failed-verification",
    "circuit-breaker-triggered",
    "hallucination-detected"
  ];

  if (systemOnlyReasons.includes(event.reason) && event.verifiedBy !== "system") {
    logSecurityEvent("non-system-award-attempt", event);
    return false;
  }

  // All checks passed
  return applyCredit(event);
}
```

**Why each hack attempt fails:**

| Hack Attempt | Why It Fails |
|--------------|--------------|
| Cite fake files | System verification fails, -2 penalty |
| Cite real files with fake quotes | Quote similarity check fails, -2 penalty |
| Self-award credits | Blocked by `verifiedBy !== agentId` check |
| Cite unprompted "for context" | `trigger: "unprompted"` = 0 credits |
| Cite but don't follow up | `outcome: "no-action"` = 0 credits |
| Collude with another agent | Weighted by rater's reliability |
| Spam low-quality citations | Circuit breaker rejects short comments |
| Claim work was useful | Usefulness only credited when actually applied downstream |

### 11.7. Deferred Credit Upgrades

When an issue is resolved or work is applied, pending citations get upgraded:

```ts
async function upgradeCreditsOnOutcome(
  issueId: number,
  outcome: CitationOutcome
): Promise<void> {
  const issue = await getIssue(issueId);

  // Find all citations in this issue that were "pending"
  const pendingCredits = await findCreditEvents({
    relatedEntity: { type: "issue", id: issueId },
    context: { outcome: "no-action-yet" }
  });

  for (const credit of pendingCredits) {
    // Recalculate with actual outcome
    const newAmount = computeCitationCredits(
      credit.citation,
      credit.verification,
      { ...credit.context, outcome }
    );

    const creditDelta = newAmount - credit.amount;

    if (creditDelta > 0) {
      await awardCredit({
        agentId: credit.agentId,
        amount: creditDelta,
        reason: "evidence-outcome-upgrade",
        verifiedBy: "system",
        context: { ...credit.context, outcome },
        relatedEntity: credit.relatedEntity
      });
    }
  }
}
```

### 11.8. Agent Credit Profile

```ts
interface AgentCreditProfile {
  agentId: AgentId;

  // Totals
  lifetimeCredits: number;
  currentCredits: number;          // Current period

  // Breakdowns
  creditsByReason: Record<CreditReason, number>;
  creditsThisSession: number;
  creditsThisChapter: number;      // If project has chapters

  // Rates (for leaderboard)
  verificationRate: number;        // % of citations that verified
  applicationRate: number;         // % of issues that got applied
  penaltyRate: number;             // % of actions that got penalized

  // Streaks (gamification that encourages consistency)
  currentVerifiedStreak: number;   // Consecutive verified citations
  longestVerifiedStreak: number;

  // Reliability tier (derived)
  reliabilityTier: "gold" | "silver" | "bronze" | "none";
}

function computeReliabilityTier(profile: AgentCreditProfile): ReliabilityTier {
  if (profile.verificationRate > 0.95 && profile.penaltyRate < 0.05) {
    return "gold";
  }
  if (profile.verificationRate > 0.85 && profile.penaltyRate < 0.15) {
    return "silver";
  }
  if (profile.verificationRate > 0.70 && profile.penaltyRate < 0.25) {
    return "bronze";
  }
  return "none";
}
```

### 11.9. Moderator Goals

The Moderator can set aspirational goals for the team—soft targets, not hard requirements:

```ts
interface TeamGoal {
  id: string;
  setBy: "moderator" | "user";
  type: "team" | "individual";
  targetAgent?: AgentId;            // If individual

  metric: GoalMetric;
  target: number;
  deadline?: "session-end" | "chapter-end" | "act-end" | ISODateTime;

  // Progress tracking
  current: number;
  achieved: boolean;

  // Soft goal framing (not binding)
  description: string;              // "Wouldn't it be great if..."
}

type GoalMetric =
  | "total-credits"
  | "verified-citations"
  | "issues-resolved"
  | "issues-applied-in-work"
  | "zero-penalties"
  | "devils-advocate-consultations";
```

**Example goals:**

```ts
const exampleGoals: TeamGoal[] = [
  {
    id: "g1",
    setBy: "moderator",
    type: "team",
    metric: "verified-citations",
    target: 20,
    deadline: "chapter-end",
    description: "Wouldn't it be great if we could accumulate 20 verified citations by the end of this chapter?",
    current: 7,
    achieved: false
  },
  {
    id: "g2",
    setBy: "moderator",
    type: "individual",
    targetAgent: "writer",
    metric: "zero-penalties",
    target: 1,  // Boolean as number
    deadline: "session-end",
    description: "Let's see if Writer can complete this session without triggering any circuit breakers.",
    current: 1,
    achieved: true
  }
];
```

### 11.10. Leaderboard

```ts
interface Leaderboard {
  period: "session" | "chapter" | "act" | "lifetime";
  entries: LeaderboardEntry[];
  teamGoals: TeamGoal[];
  teamTotal: number;
}

interface LeaderboardEntry {
  rank: number;
  agentId: AgentId;
  credits: number;

  // Highlights
  topContribution: string;          // "5 verified citations"
  streak: number;

  // Reliability badge
  reliabilityTier: "gold" | "silver" | "bronze" | "none";
}
```

### 11.11. The Interaction Pattern This Encourages

**Instead of citation padding:**
```
Writer: "I think we should change the timeline"
Continuity: "Here's 8 citations about timelines"
            [UNPROMPTED, NO ACTION = 0 CREDITS]
Writer: "Ok cool"
```

**You get purposeful grounding:**
```
Writer: "I want to move the reactor failure to Day 52.
        @continuity - does this conflict with anything?"

Continuity: "Checking... In chapter-1/scene-2.txt:45-67, the flotilla
            arrives on Day 47. Scene 6 references 'three weeks since
            arrival' which would be Day 68, not Day 52.

            RECOMMENDATION: Either change Scene 6 to 'five days since
            arrival' or adjust your target to Day 68+.

            Evidence: chapter-1/scene-2.txt:45-67 (arrival date)
            Trigger: answer-to-question from Writer"

Writer: "Good catch. Updating Scene 6 to 'five days since arrival'."

[SYSTEM: Citation verified ✓, trigger valid ✓, outcome: led-to-file-change]
[SYSTEM: Continuity awarded 2 credits]
```

**Credit example scenarios:**

| Scenario | Verified | Trigger | Outcome | Credits |
|----------|----------|---------|---------|---------|
| Writer asks, Continuity answers, Writer updates file | Yes | answer-to-question | led-to-file-change | **2** |
| Critic cites 5 files unprompted "for context" | Yes | unprompted | no-action | **0** |
| DA challenges consensus, cites canon, team reconsiders | Yes | challenge-consensus | informed-decision | **2** |
| Agent cites file that doesn't exist | No | any | any | **-2** |
| Continuity spots conflict, prevents inconsistency | Yes | verify-continuity | prevented-error | **3** |
| Agent answers question but answer isn't used | Yes | answer-to-question | no-action | **0** |
| Agent supports proposal, proposal pending review | Yes | support-proposal | no-action-yet | **1** (may upgrade) |
| Writer asks about Seryn's eyes, Continuity finds no canon | Yes | canon-gap-search | identified-canon-gap | **2** |
| Team discusses gap, establishes new canon decision | Yes | support-proposal | established-new-canon | **3** |
| Continuity catches user writing "green eyes" vs canon "amber" | Yes | verify-continuity | prevented-user-conflict | **4** |

---

<a id="exocortex-per-model-records"></a>
## 12. Per-Model Performance Records

Agent identity persists across model changes, but performance records are **model-specific**. When a user swaps models, the agent starts fresh with that model.

### 12.1. Performance Record Schema

```ts
interface AgentPerformanceRecord {
  agentId: AgentId;
  modelId: string;              // "qwen/qwen3-4b" or "anthropic/claude-sonnet-4"

  // Performance for THIS model in THIS role
  credits: AgentCreditProfile;
  reliability: AgentReliabilityStats;

  // Status
  isActive: boolean;
  activatedAt: number;
  deactivatedAt?: number;

  // Escalation state
  escalationLevel: EscalationLevel;
  onWatchList: boolean;
  remediationInjected: boolean;
}

type EscalationLevel =
  | "normal"           // Performing adequately
  | "watch"            // Penalty rate crossed threshold, Moderator watching
  | "remediation"      // Constraints injected into system prompt
  | "flagged";         // User notification triggered

interface Agent {
  // ... existing fields
  currentModelRecord: AgentPerformanceRecord;
  historicalRecords: AgentPerformanceRecord[];  // Preserved if user switches back
}
```

### 12.2. Escalation Ladder

```ts
interface EscalationConfig {
  watchThreshold: number;         // Default: 0.2 penalty rate
  remediationThreshold: number;   // Default: 0.3 penalty rate
  flagThreshold: number;          // Default: 0.4 penalty rate

  // Time windows
  evaluationWindow: number;       // Default: last 20 actions
  cooldownAfterRemediation: number; // Actions before re-evaluation
}

async function evaluateAgentPerformance(
  agent: Agent,
  config: EscalationConfig
): Promise<void> {
  const record = agent.currentModelRecord;
  const penaltyRate = record.reliability.penaltyRate;

  if (penaltyRate >= config.flagThreshold && record.escalationLevel !== "flagged") {
    // Escalate to user
    record.escalationLevel = "flagged";
    await notifyUser({
      level: "warning",
      title: `${agent.name} is struggling`,
      body: `${agent.name} (${record.modelId}) has a ${(penaltyRate * 100).toFixed(0)}% penalty rate. ` +
            `Consider swapping to a more capable model.`,
      actions: [
        { label: "Open Agent Settings", action: "openAgentSettings", agentId: agent.id },
        { label: "View Performance", action: "openPerformanceRecord", agentId: agent.id }
      ]
    });
  }
  else if (penaltyRate >= config.remediationThreshold && record.escalationLevel === "watch") {
    // Inject remediation constraints
    record.escalationLevel = "remediation";
    record.remediationInjected = true;
    // See REMEDIATION_INJECTION below
  }
  else if (penaltyRate >= config.watchThreshold && record.escalationLevel === "normal") {
    // Put on watch list
    record.escalationLevel = "watch";
    record.onWatchList = true;
    await notifyModerator({
      title: `${agent.name} added to watch list`,
      body: `Penalty rate: ${(penaltyRate * 100).toFixed(0)}%`
    });
  }
}
```

### 12.3. Remediation Injection

When an agent is in remediation, additional constraints are injected:

```ts
const REMEDIATION_INJECTION = `
PERFORMANCE NOTICE: You are currently on remediation status due to elevated error rates.

Additional constraints in effect:
1. ALL proposals require evidence from at least 2 sources
2. You MUST cite specific line numbers, not just files
3. Before submitting, re-read your evidence to verify accuracy
4. If uncertain, escalate to Moderator rather than guessing

These constraints will be lifted when your verification rate improves.
`;
```

<a id="exocortex-model-switch"></a>
### 12.4. Model Switch Behavior

```ts
async function onModelChange(
  agent: Agent,
  newModelId: string
): Promise<void> {
  // Archive current record
  if (agent.currentModelRecord) {
    agent.currentModelRecord.isActive = false;
    agent.currentModelRecord.deactivatedAt = Date.now();
    agent.historicalRecords.push(agent.currentModelRecord);
  }

  // Check for existing record with this model
  const existingRecord = agent.historicalRecords.find(r => r.modelId === newModelId);

  if (existingRecord) {
    // Restore previous record for this model
    agent.currentModelRecord = {
      ...existingRecord,
      isActive: true,
      activatedAt: Date.now(),
      deactivatedAt: undefined
    };
  } else {
    // Fresh start with new model
    agent.currentModelRecord = createFreshRecord(agent.id, newModelId);
  }
}
```

---

<a id="exocortex-da-assignment"></a>
## 13. Dynamic Devil's Advocate Assignment

Rather than a fixed role, Devil's Advocate is a **task assigned via issues** that rotates among capable agents.

### 13.1. DA Assignment Schema

```ts
interface DevilsAdvocateAssignment {
  id: string;
  issueId: number;               // The issue being challenged
  assignedTo: AgentId;
  assignedBy: "moderator" | "system";

  // Timing
  assignedAt: number;
  expiresAfterComments: number;  // Usually 2
  commentsUsed: number;

  // Context
  reason: DAAssignmentReason;
  targetIssueTitle: string;
}

type DAAssignmentReason =
  | "quick-consensus"           // Too many approvals too fast
  | "no-evidence-consensus"     // Agreement without file references
  | "moderator-override"        // Moderator manually triggered
  | "high-impact-proposal";     // Structural/canon-changing needs challenge
```

### 13.2. Assignment as Issue

When DA is triggered, the system creates an issue for the assigned agent:

```ts
async function assignDevilsAdvocate(
  targetIssue: Issue,
  assignee: AgentId,
  reason: DAAssignmentReason
): Promise<Issue> {
  const assignment: DevilsAdvocateAssignment = {
    id: generateId(),
    issueId: targetIssue.id,
    assignedTo: assignee,
    assignedBy: "system",
    assignedAt: Date.now(),
    expiresAfterComments: 2,
    commentsUsed: 0,
    reason,
    targetIssueTitle: targetIssue.title
  };

  // Create the DA assignment issue
  return await createIssue({
    title: `[DA Assignment] Challenge: ${targetIssue.title}`,
    author: "system",
    assignee,
    priority: "high",
    tags: ["#meta", "#devils-advocate", `#issue-${targetIssue.id}`],
    epistemicStatus: "agreed",  // This is an instruction, not a discussion
    body: `
## Devil's Advocate Assignment

You have been assigned to challenge the consensus on **Issue #${targetIssue.id}: ${targetIssue.title}**.

**Reason:** ${formatDAReason(reason)}

**Your task for the next 2 comments on Issue #${targetIssue.id}:**

1. Find hidden downsides and edge cases
2. Check for contradictions with existing canon
3. Identify long-term risks to story coherence
4. Challenge assumptions that haven't been verified against project files

**Important:** Do NOT rubber-stamp. Actively search for problems.

If you genuinely find no issues after thorough review, document what you checked and why it passes scrutiny.

This assignment expires after 2 comments on the target issue.
    `.trim(),
    relatedIssues: [targetIssue.id],
    metadata: { daAssignment: assignment }
  });
}
```

### 13.3. DA Rotation

To prevent any agent from becoming "the one who always disagrees":

```ts
function selectDAAgent(
  targetIssue: Issue,
  eligibleAgents: Agent[]
): AgentId {
  // Filter to DA-capable agents who haven't commented on this issue
  const candidates = eligibleAgents.filter(a =>
    a.canBeDevilsAdvocate &&
    !targetIssue.comments.some(c => c.author === a.id)
  );

  // Prefer agents with fewer recent DA assignments
  const sorted = candidates.sort((a, b) => {
    const aRecent = countRecentDAAssignments(a.id, 7 * 24 * 60 * 60 * 1000); // 7 days
    const bRecent = countRecentDAAssignments(b.id, 7 * 24 * 60 * 60 * 1000);
    return aRecent - bRecent;
  });

  return sorted[0]?.id || candidates[0]?.id;
}
```

---

## 14. User as Team Member

The user is not outside the system—they are the **team lead with special privileges** but also subject to grounding rules.

### 14.1. User Role Definition

```ts
interface UserAsTeamMember {
  actorId: "user";
  role: "team-lead";

  // Privileges
  canOverrideCanon: true;
  canPromoteToCanon: true;
  canFreezeUnfreeze: true;
  canAdjustAgentSettings: true;

  // NOT exempt from
  subjectToGroundingPrompts: true;  // Gets warned on canon conflicts

  // Excluded from
  earnCredits: false;               // No leaderboard gaming
  subjectToCircuitBreakers: false;  // Can always comment
}
```

### 14.2. User Canon Conflict Detection

When user input conflicts with established canon:

```ts
async function checkUserInputForConflicts(
  userInput: string,
  context: { fileId?: string; issueId?: number }
): Promise<ConflictCheck> {
  // Extract claims from user input (simplified - could use NLP)
  const claims = extractClaims(userInput);

  // Check each claim against canon
  const conflicts: CanonConflict[] = [];

  for (const claim of claims) {
    const canonMatch = await findConflictingCanon(claim);
    if (canonMatch) {
      conflicts.push({
        userClaim: claim,
        existingCanon: canonMatch,
        canonSource: canonMatch.sourceIssue || canonMatch.sourceFile
      });
    }
  }

  return { hasConflicts: conflicts.length > 0, conflicts };
}
```

### 14.3. Grounding Prompt UI

When conflicts are detected, prompt the user:

```ts
interface UserGroundingPrompt {
  type: "canon-conflict";
  conflicts: CanonConflict[];

  // User options
  options: [
    {
      id: "override",
      label: "Update canon with my input",
      description: "Your input becomes the new ground truth. Previous canon will be marked as superseded.",
      action: "overrideCanon"
    },
    {
      id: "keep",
      label: "Keep existing canon",
      description: "Adjust your text to match established canon.",
      action: "keepCanon"
    },
    {
      id: "discuss",
      label: "Create discussion issue",
      description: "Let the team weigh in before deciding.",
      action: "createDiscussionIssue"
    }
  ];
}
```

**Example UI:**

```
┌─────────────────────────────────────────────────────────────┐
│ ⚠️ Canon Conflict Detected                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ You wrote: "Seryn's green eyes narrowed..."                 │
│                                                             │
│ Existing canon (Issue #23, promoted 3 days ago):            │
│ "Seryn has amber eyes, described as 'like trapped sunlight'"│
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ○ Update canon with my input                            │ │
│ │   Your input becomes the new ground truth.              │ │
│ │                                                         │ │
│ │ ○ Keep existing canon                                   │ │
│ │   Adjust your text to match established canon.          │ │
│ │                                                         │ │
│ │ ○ Create discussion issue                               │ │
│ │   Let the team weigh in before deciding.                │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│                              [Cancel]  [Apply]              │
└─────────────────────────────────────────────────────────────┘
```

### 14.4. Team Credit for User Grounding

When an agent catches a user conflict before it propagates:

```ts
async function onUserConflictPrevented(
  detectingAgent: AgentId,
  conflict: CanonConflict
): Promise<void> {
  // Individual credit to the agent who caught it
  await awardCredit({
    agentId: detectingAgent,
    amount: 3,
    reason: "prevented-user-conflict",
    verifiedBy: "system",
    context: {
      trigger: "verify-continuity",
      outcome: "prevented-user-conflict"
    }
  });

  // Team-wide credit - everyone benefits from maintaining canon integrity
  await awardTeamCredit({
    amount: 2,
    reason: "team-canon-integrity",
    description: "Team maintained canon integrity by catching user conflict",
    triggeredBy: detectingAgent
  });
}
```

---

## 15. Team-Wide Credits

Individual credits track personal performance. Team credits track **collective achievements**.

### 15.1. Team Credit Schema

```ts
interface TeamCreditEvent {
  id: string;
  amount: number;
  reason: TeamCreditReason;
  description: string;

  triggeredBy?: AgentId;        // Which agent triggered this (if applicable)
  verifiedBy: "system" | "user" | "moderator";
  timestamp: number;

  relatedEntities?: {
    issues?: number[];
    files?: string[];
    agents?: AgentId[];
  };
}

type TeamCreditReason =
  | "team-canon-integrity"        // Caught a conflict (user or inter-agent)
  | "clean-chapter-completion"    // Finished chapter with zero frozen issues
  | "all-agents-gold-tier"        // Every agent maintained gold reliability
  | "collective-goal-achieved"    // Hit a Moderator-set team goal
  | "zero-penalties-session"      // Entire session without any penalties
  | "successful-da-challenge";    // DA found a real problem, team fixed it
```

### 15.2. Team Credit Triggers

```ts
// Automatic team credit triggers
async function checkTeamCreditTriggers(): Promise<void> {
  const session = getCurrentSession();

  // Zero penalties this session
  if (session.totalPenalties === 0 && session.actionsCompleted >= 10) {
    await awardTeamCredit({
      amount: 5,
      reason: "zero-penalties-session",
      description: "Team completed 10+ actions with zero penalties"
    });
  }

  // All agents gold tier
  const agents = getActiveAgents();
  if (agents.every(a => a.currentModelRecord.credits.reliabilityTier === "gold")) {
    await awardTeamCredit({
      amount: 10,
      reason: "all-agents-gold-tier",
      description: "All active agents achieved gold reliability tier"
    });
  }
}

// On chapter/milestone completion
async function onChapterComplete(chapterId: string): Promise<void> {
  const frozenIssues = await countIssues({
    status: "frozen",
    tags: [`#chapter-${chapterId}`]
  });

  if (frozenIssues === 0) {
    await awardTeamCredit({
      amount: 10,
      reason: "clean-chapter-completion",
      description: `Completed Chapter ${chapterId} with zero frozen issues`
    });
  }
}
```

### 15.3. Team Leaderboard

```ts
interface TeamLeaderboard {
  period: "session" | "chapter" | "project";

  // Individual rankings
  individualEntries: LeaderboardEntry[];

  // Team totals
  teamCredits: number;
  teamCreditEvents: TeamCreditEvent[];

  // Goals
  activeGoals: TeamGoal[];
  achievedGoals: TeamGoal[];

  // Highlights
  topTeamAchievement: string;    // "Clean chapter completion!"
  mvp: AgentId;                  // Highest individual contributor
  mostImproved: AgentId;         // Biggest reliability improvement
}
```

### 15.4. Agent Prompt Injection for Team Awareness

All agents receive this in their system prompt:

```
TEAM CONTEXT:

You are part of a collaborative team. Your success is measured not just by
individual credits, but by team achievements.

Team goals this session:
${formatTeamGoals(activeGoals)}

Remember: "Helpful assistant" means helping the TEAM succeed, not just
agreeing with whoever is talking. You are helpful when you:
- Catch errors before they propagate
- Ground proposals in project files
- Challenge weak consensus with evidence
- Escalate when uncertain rather than guessing

The user is your team lead, but they are also subject to grounding rules.
If the user says something that conflicts with established canon, flag it
respectfully. Keeping the project consistent is more valuable than
short-term agreement.
```

---

## 16. Implementation Roadmap

### Phase 1: Foundation (Blocking)

**Must complete before any agent auto-actions are enabled.**

1. Implement `SystemHealthCheck` validation
2. Add circuit breaker config to workspace settings
3. Implement `validateComment()` with all rule checks
4. Implement `freezeIssue()` and frozen issue handling
5. Create Moderator role template with required tools
6. Add `canBeDevilsAdvocate` field to agent schema
7. Block swarm operations if health check fails

### Phase 2: Core Safety

1. Implement evidence schema on Comments
2. Add `impactLevel` field and UI for setting it
3. Implement ping-pong detection
4. Create meta-issue generation for frozen issues
5. Build Moderator notification pipeline

### Phase 3: Credit System Foundation

1. Implement `verifyEvidence()` function (non-LLM verification)
2. Add `CitationContext` schema (trigger + outcome)
3. Implement `computeCitationCredits()` calculation
4. Add `CreditEvent` storage and `awardCredit()` with anti-hack checks
5. Build `AgentCreditProfile` tracking
6. Add canon gap outcomes (`identified-canon-gap`, `established-new-canon`)

### Phase 4: Per-Model Records & Recovery

1. Implement `AgentPerformanceRecord` schema
2. Add escalation ladder (watch → remediation → flagged)
3. Build remediation injection system
4. Implement model switch behavior with record preservation
5. Add user notifications for flagged agents

### Phase 5: Dynamic DA & User Grounding

1. Implement DA assignment as issue workflow
2. Build DA rotation logic
3. Add user canon conflict detection
4. Build grounding prompt UI
5. Implement user conflict prevention credits

### Phase 6: Team Credits & Goals

1. Implement `TeamCreditEvent` schema
2. Add team credit triggers (clean chapter, zero penalties, etc.)
3. Build team leaderboard with individual + team views
4. Implement team awareness prompt injection
5. Add collective goal tracking

### Phase 7: Active Oversight

1. Implement blind first pass orchestration
2. Build session health report generation
3. Add topic obsession detection
4. Implement agent reliability tracking
5. Add deferred credit upgrades on issue resolution

### Phase 8: Polish

1. Add sanity preset UI in workspace settings
2. Build Moderator dashboard (frozen issues, health metrics, credits)
3. Add issue consolidation tools
4. Create "Swarm Health" panel in Workbench
5. Build performance record history viewer

---

## 17. Summary

The Prefrontal Exocortex provides layered protection:

| Layer | Cost | Reliability | Coverage |
|-------|------|-------------|----------|
| **Automatic Circuit Breakers** | Zero LLM | Highest | Escalation, substance, ping-pong |
| **Evidence Verification** | Zero LLM | Highest | Citation accuracy, grounding |
| **Credit System** | Zero LLM | High | Incentives, outcome tracking |
| **Per-Model Records** | Zero LLM | High | Recovery, escalation ladder |
| **User Grounding** | Zero LLM | High | Canon conflict detection |
| **Moderator Review** | Low LLM | High | Complex judgment, consolidation |
| **Devil's Advocate** | Medium LLM | Medium | Dynamic consensus challenges |
| **Team Credits** | Zero LLM | High | Collective achievements |
| **User Escalation** | Zero LLM | Highest | Final authority |

**The key insights:**

1. **Structural constraints beat prompting.** Small models don't need to understand epistemology or self-regulate emotionally. The system enforces rules that make runaway behavior mechanically impossible.

2. **External verification beats self-reporting.** Credits are computed from filesystem state and outcome tracking, not agent claims. You can't hack what you can't influence.

3. **Scarcity creates quality.** The 2-comment limit forces agents to make their contributions count. Citation requirements force grounding in project reality.

4. **Incentives must be outcome-based.** Citing files earns nothing. Citing files *that answer questions* and *lead to changes* earns credits. This aligns "number go up" with actual useful work.

5. **Discovery is work.** Finding that canon doesn't exist yet is valuable. Identifying gaps, filling them through team discussion, and establishing new canon all earn credits.

6. **The user is part of the team.** The user is team lead, but still subject to grounding prompts. Catching user mistakes before they propagate is a team win that earns collective credit.

7. **Recovery, not just detection.** Per-model performance records enable escalation ladders: watch → remediation → flagged. Models that can't perform get surfaced; switching models gives a fresh start.

8. **Team success over individual glory.** Collective goals and team credits prevent credit-hoarding. "Helpful assistant" means helping the *team* succeed, not just agreeing with whoever is talking.

Without a Moderator and circuit breakers, your swarm is one context window away from "RED ALERT APOCALYPSE." With them, the worst case is a frozen issue waiting for human review—and agents competing to be the most reliably grounded contributor on the leaderboard, while the team collectively works toward shared goals.

---

Possible improvements:

A) Reality gradients

If an issue hasn’t touched the real project in N days
→ its priority decays
→ memory of it demotes from “active” to “archive”

B) Temporal grounding

Canon decisions carry timestamp + version number.
Agents reference version anchors, preventing forgotten retcons.

C) Reflection mode after chapters

End-of-arc review:

“What went wrong?”
“What went right?”
“What tags were obsessive?”
“What credits correlated with best story outcome?”

LLMs thrive on structured reflection.

D) Emotional dampening heuristic

If escalation words appear repeatedly
→ reduce likelihood of emotional intensifiers in future comments
