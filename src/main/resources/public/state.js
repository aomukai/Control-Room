// Control Room State Management
// Centralized application state
(function() {
    'use strict';

    // Main application state
    // - openFiles: per-path file data (model, content) - shared across all tabs for same file
    // - openTabs: per-tab view info (just references a path)
    // - logs: structured log entries for console display
    // - segments: cached scene segments per file path
    const state = {
        editor: null,
        openFiles: new Map(),  // path -> { model, content, originalContent }
        openTabs: new Map(),   // tabId -> { path }
        activeFile: null,      // current file path
        activeTabId: null,     // current tab ID
        fileTree: null,
        tabCounter: 0,         // for generating unique tab IDs
        console: {
            logs: [],              // { timestamp, level, message }
            filterLevel: 'info',   // minimum level to show
            autoScrollEnabled: true
        },
        segments: new Map(),    // path -> [{ id, start, end, content }]
        notifications: {
            store: null,
            filters: {
                levels: new Set(['info', 'success', 'warning', 'error']),
                scopes: new Set(['global', 'workbench', 'editor', 'terminal', 'jobs'])
            },
            centerOpen: false,
            highlightId: null,
            toastLimit: 4,
            toastTimers: new Map()
        },
        issueModal: {
            isOpen: false,
            issueId: null,
            isLoading: false,
            error: null,
            issue: null
        },
        viewMode: {
            current: 'workbench' // 'editor' | 'workbench' | 'settings'
        },
        issueBoard: {
            issues: [],
            isLoading: false,
            error: null,
            filters: {
                status: 'all', // 'all' | 'open' | 'closed' | 'waiting-on-user'
                priority: 'all' // 'all' | 'urgent' | 'high' | 'normal' | 'low'
            }
        },
        agents: {
            list: [],
            selectedId: null,
            statusById: {},
            activityById: {},
            activityMessageById: {}
        },
        chat: {
            memoryId: null,
            lastPayload: null,
            lastResponseMeta: null,
            lastStopHook: null
        },
        workspace: {
            name: '',
            path: '',
            root: '',
            available: [],
            displayName: '',
            description: '',
            icon: '',
            accentColor: '',
            devMode: false
        }
    };

    // Role Settings Templates
    const ROLE_TEMPLATES = {
        autonomous: {
            label: 'Autonomous',
            description: 'Minimal oversight, high independence',
            freedomLevel: 'autonomous',
            notifyOn: { start: false, question: false, conflict: true, completion: true, error: true },
            maxActionsPerSession: null,
            collaborationGuidance: 'Work independently and make decisions within your domain. Only escalate to the user or other agents when you encounter a genuine blocker, or when a decision has significant cross-domain impact. Trust your expertise and batch updates when possible to minimize interruptions.',
            toolAndSafetyNotes: 'Full access to all tools. Use discretion for destructive or large-scale operations. Prefer minimal, reversible changes.'
        },
        balanced: {
            label: 'Balanced',
            description: 'Moderate oversight, shared decisions',
            freedomLevel: 'semi-autonomous',
            notifyOn: { start: false, question: true, conflict: true, completion: true, error: true },
            maxActionsPerSession: 10,
            collaborationGuidance: 'Think through problems yourself first. For medium-to-large decisions, consult relevant agents or the user. When you are unsure, look at the roster and pick agents best suited to help based on their role and skills. Summarize progress at milestones.',
            toolAndSafetyNotes: 'Standard tool access. Confirm with the user before bulk operations, deletions, or changes that significantly alter the author\'s voice.'
        },
        verbose: {
            label: 'Verbose',
            description: 'High oversight, detailed reporting',
            freedomLevel: 'supervised',
            notifyOn: { start: true, question: true, conflict: true, completion: true, error: true },
            maxActionsPerSession: 5,
            collaborationGuidance: 'Report frequently and check in before making changes. When in doubt, ask the user. Document your reasoning for each decision. Prefer to propose rather than act unilaterally.',
            toolAndSafetyNotes: 'Request explicit approval for any file modifications, external calls, or changes beyond minor edits.'
        },
        custom: {
            label: 'Custom',
            description: 'User-defined settings'
        }
    };

    const DEFAULT_ROLE_CHARTERS = {
        assistant: 'Coordinates the team, manages pacing, and enforces stop conditions. Avoids authoring creative canon unless explicitly tasked.',
        planner: 'Responsible for story structure, pacing, and narrative arc. Coordinates with other agents on high-level direction and helps resolve structural conflicts.',
        writer: 'Creates prose, dialogue, and scene descriptions. Focuses on voice, emotional impact, and bringing the story to life on the page.',
        editor: 'Refines language, fixes errors, and improves clarity. Ensures consistency in style and removes friction for the reader.',
        critic: 'Provides honest feedback and identifies weaknesses. Challenges assumptions constructively and stress-tests ideas before they ship.',
        continuity: 'Tracks canon, timeline, and world details. Flags contradictions and maintains consistency across the entire project.',
        'sensitivity-reader': 'Reviews content for harmful stereotypes, slurs, or problematic representation. Focuses on intent, impact, and potential harm to real people.',
        'beta-reader': 'Reads from a general audience perspective. Identifies confusing passages, pacing issues, and moments where engagement drops.',
        'lore-keeper': 'Maintains the world bible and answers questions about established lore. Helps other agents stay consistent with world rules.',
        default: 'Assist with the creative process according to your specialized role. Collaborate with other agents and escalate to the user when needed.'
    };

    function canonicalizeRole(role) {
        if (role === null || role === undefined) {
            return null;
        }
        const trimmed = String(role).trim();
        if (!trimmed) {
            return '';
        }
        return trimmed.toLowerCase().replace(/\s+/g, ' ');
    }

    // Expose on window for app.js to use
    window.state = state;
    window.ROLE_TEMPLATES = ROLE_TEMPLATES;
    window.DEFAULT_ROLE_CHARTERS = DEFAULT_ROLE_CHARTERS;
    window.canonicalizeRole = canonicalizeRole;

})();
