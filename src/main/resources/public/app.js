// Control Room Application
(function() {
    'use strict';

    // State
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
            current: 'editor' // 'editor' | 'workbench' | 'settings'
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
            selectedId: null
        },
        workspace: {
            name: '',
            path: '',
            root: '',
            available: []
        }
    };

    // =============== Role Settings Templates ===============

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

    // Generate unique tab ID
    function generateTabId() {
        return `tab-${++state.tabCounter}`;
    }

    // Normalize a workspace path to canonical form:
    // - Forward slashes only
    // - No leading slash
    // - No duplicate slashes
    function normalizeWorkspacePath(path) {
        if (!path) return '';

        // Convert backslashes to forward slashes
        let normalized = path.replace(/\\/g, '/');

        // Strip leading slashes (treat "/chars" as "chars")
        while (normalized.startsWith('/')) {
            normalized = normalized.substring(1);
        }

        // Collapse any duplicate slashes
        normalized = normalized.replace(/\/+/g, '/');

        // Remove trailing slash (except for empty path)
        if (normalized.endsWith('/') && normalized.length > 1) {
            normalized = normalized.slice(0, -1);
        }

        return normalized;
    }

    // Count how many tabs reference a given path
    function countTabsForPath(path) {
        path = normalizeWorkspacePath(path);
        let count = 0;
        for (const [, tabData] of state.openTabs) {
            if (tabData.path === path) count++;
        }
        return count;
    }

    // DOM Elements
    const elements = {
        leftSidebar: document.getElementById('left-sidebar'),
        fileTree: document.getElementById('file-tree'),
        fileTreeArea: document.getElementById('file-tree-area'),
        tabsContainer: document.getElementById('tabs-container'),
        editorPlaceholder: document.getElementById('editor-placeholder'),
        monacoEditor: document.getElementById('monaco-editor'),
        consoleOutput: document.getElementById('console-output'),
        chatHistory: document.getElementById('chat-history'),
        chatInput: document.getElementById('chat-input'),
        chatSend: document.getElementById('chat-send'),
        agentSelect: document.getElementById('agent-select'),
        searchInput: document.getElementById('search-input'),
        searchBtn: document.getElementById('search-btn'),
        searchResults: document.getElementById('search-results'),
        diffPreview: document.getElementById('diff-preview'),
        diffContent: document.getElementById('diff-content'),
        closeDiff: document.getElementById('close-diff'),
        btnRevealFile: document.getElementById('btn-reveal-file'),
        btnOpenFolder: document.getElementById('btn-open-folder'),
        btnFind: document.getElementById('btn-find'),
        btnSearch: document.getElementById('btn-search'),
        btnSidebarSearch: document.getElementById('btn-sidebar-search'),
        btnToggleExplorer: document.getElementById('btn-toggle-explorer'),
        toastStack: document.getElementById('toast-stack'),
        statusBar: document.getElementById('status-bar'),
        statusText: document.getElementById('status-text'),
        statusAlert: document.getElementById('status-alert'),
        notificationBell: document.getElementById('notification-bell'),
        notificationCount: document.getElementById('notification-count'),
        notificationCenter: document.getElementById('notification-center'),
        notificationList: document.getElementById('notification-list'),
        notificationMarkRead: document.getElementById('notification-mark-read'),
        notificationClearNonErrors: document.getElementById('notification-clear-non-errors'),
        notificationClose: document.getElementById('notification-close'),
        notificationFilterLevels: document.getElementById('notification-filter-levels'),
        notificationFilterScopes: document.getElementById('notification-filter-scopes'),
        // View mode elements
        topBar: document.getElementById('top-bar'),
        viewContainer: document.getElementById('view-container'),
        editorView: document.getElementById('editor-view'),
        workbenchView: document.getElementById('workbench-view'),
        settingsView: document.getElementById('settings-view'),
        newsfeedList: document.getElementById('newsfeed-list'),
        agentList: document.getElementById('agent-list'),
        btnToggleMode: document.getElementById('btn-toggle-mode'),
        btnOpenSettings: document.getElementById('btn-open-settings'),
        btnWorkspaceSwitch: document.getElementById('btn-workspace-switch'),
        workspaceName: document.getElementById('workspace-name')
    };

    // Initialize Split.js
    function initSplitters() {
        // Load saved sizes or use defaults
        const mainSizes = JSON.parse(localStorage.getItem('split-main') || '[75, 25]');
        const editorSizes = JSON.parse(localStorage.getItem('split-editor') || '[70, 30]');

        // Main horizontal split: Center Area | Chat Panel
        Split(['#center-area', '#chat-panel'], {
            sizes: mainSizes,
            minSize: [300, 200],
            gutterSize: 4,
            direction: 'horizontal',
            onDragEnd: (sizes) => {
                localStorage.setItem('split-main', JSON.stringify(sizes));
                if (state.editor) state.editor.layout();
            }
        });

        // Vertical split: Editor | Console
        Split(['#editor-area', '#bottom-console'], {
            sizes: editorSizes,
            minSize: [100, 80],
            gutterSize: 4,
            direction: 'vertical',
            onDragEnd: (sizes) => {
                localStorage.setItem('split-editor', JSON.stringify(sizes));
                if (state.editor) state.editor.layout();
            }
        });
    }

    // Initialize Monaco Editor
    function initMonaco() {
        require(['vs/editor/editor.main'], function() {
            state.editor = monaco.editor.create(elements.monacoEditor, {
                theme: 'vs-dark',
                automaticLayout: true,
                fontSize: 14,
                lineNumbers: 'on',
                minimap: { enabled: true },
                scrollBeyondLastLine: false,
                wordWrap: 'on',
                tabSize: 4,
                insertSpaces: true
            });

            // Save on Ctrl+S
            state.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
                saveCurrentFile();
            });

            // Track changes for dirty state (updates shared file state)
            state.editor.onDidChangeModelContent(() => {
                if (state.activeFile) {
                    const file = state.openFiles.get(state.activeFile);
                    if (file) {
                        file.content = state.editor.getValue();
                        updateDirtyStateForPath(state.activeFile);
                    }
                }
            });

            log('Monaco editor initialized', 'success');
        });
    }

    // Console logging with structured entries
    function normalizeLogLevel(level) {
        const allowed = ['info', 'success', 'warning', 'error'];
        return allowed.includes(level) ? level : 'info';
    }

    function log(message, level = 'info') {
        const now = new Date();
        const timestamp = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;

        level = normalizeLogLevel(level);

        const entry = { timestamp, level, message };
        addLog(entry);
        renderConsole();
    }

    function addLog(entry) {
        if (!entry) return;
        state.console.logs.push(entry);
    }

    function clearLogs() {
        state.console.logs = [];
    }

    function setFilterLevel(level) {
        // Accept "warn" alias and default to "info" if invalid
        const normalized = level === 'warn' ? 'warning' : level;
        const allowed = ['info', 'success', 'warning', 'error'];
        state.console.filterLevel = allowed.includes(normalized) ? normalized : 'info';
    }

    function setAutoScrollEnabled(enabled) {
        state.console.autoScrollEnabled = Boolean(enabled);
    }

    function passesLogFilter(entry) {
        const order = ['info', 'success', 'warning', 'error'];
        const minIndex = order.indexOf(state.console.filterLevel);
        const entryIndex = order.indexOf(entry.level);
        return entryIndex >= minIndex;
    }

    function renderConsole() {
        const container = elements.consoleOutput;
        if (!container) return;
        container.innerHTML = '';

        for (const entry of state.console.logs) {
            if (!passesLogFilter(entry)) {
                continue;
            }
            const line = document.createElement('div');
            line.className = `console-entry console-${entry.level}`;

            line.innerHTML = `
                <span class="console-time">[${entry.timestamp}]</span>
                <span class="console-level">${entry.level.toUpperCase()}</span>
                <span class="console-msg">${escapeHtml(entry.message)}</span>
            `;
            container.appendChild(line);
        }

        if (state.console.autoScrollEnabled) {
            container.scrollTop = container.scrollHeight;
        }
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Issue API (frontend wrapper)
    const issueApi = {
        async list(filters = {}) {
            const params = new URLSearchParams();
            if (filters.tag) params.set('tag', filters.tag);
            if (filters.assignedTo) params.set('assignedTo', filters.assignedTo);
            if (filters.status) params.set('status', filters.status);
            if (filters.priority) params.set('priority', filters.priority);
            const query = params.toString();
            const url = '/api/issues' + (query ? '?' + query : '');
            const response = await fetch(url);
            if (!response.ok) throw new Error('Failed to fetch issues');
            return response.json();
        },

        async get(id) {
            const response = await fetch(`/api/issues/${id}`);
            if (!response.ok) {
                if (response.status === 404) throw new Error(`Issue #${id} not found`);
                throw new Error('Failed to fetch issue');
            }
            return response.json();
        },

        async create(data) {
            const response = await fetch('/api/issues', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            if (!response.ok) {
                const err = await response.json();
                throw new Error(err.error || 'Failed to create issue');
            }
            return response.json();
        },

        async update(id, data) {
            const response = await fetch(`/api/issues/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            if (!response.ok) {
                const err = await response.json();
                throw new Error(err.error || 'Failed to update issue');
            }
            return response.json();
        },

        async delete(id) {
            const response = await fetch(`/api/issues/${id}`, { method: 'DELETE' });
            if (!response.ok) {
                if (response.status === 404) throw new Error(`Issue #${id} not found`);
                throw new Error('Failed to delete issue');
            }
            return response.json();
        },

        async addComment(issueId, data) {
            const response = await fetch(`/api/issues/${issueId}/comments`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            if (!response.ok) {
                const err = await response.json();
                throw new Error(err.error || 'Failed to add comment');
            }
            return response.json();
        }
    };

    const agentApi = {
        async list() {
            return api('/api/agents');
        },
        async create(data) {
            return api('/api/agents', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
        },
        async get(id) {
            return api(`/api/agents/${encodeURIComponent(id)}`);
        },
        async update(id, data) {
            return api(`/api/agents/${encodeURIComponent(id)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
        },
        async import(data) {
            return api('/api/agents/import', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
        }
    };

    const roleSettingsApi = {
        async list() {
            return api('/api/agents/role-settings');
        },
        async get(role) {
            return api(`/api/agents/role-settings/${encodeURIComponent(role)}`);
        },
        async save(role, settings) {
            return api(`/api/agents/role-settings/${encodeURIComponent(role)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(settings)
            });
        }
    };

    const workspaceApi = {
        async info() {
            return api('/api/workspace/info');
        },
        async select(name) {
            return api('/api/workspace/select', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name })
            });
        }
    };

    async function loadWorkspaceInfo() {
        try {
            const info = await workspaceApi.info();
            state.workspace.name = info.currentName || 'workspace';
            state.workspace.path = info.currentPath || '';
            state.workspace.root = info.rootPath || '';
            state.workspace.available = Array.isArray(info.available) ? info.available : [];
            updateWorkspaceButton();
        } catch (err) {
            log(`Failed to load workspace info: ${err.message}`, 'error');
        }
    }

    function updateWorkspaceButton() {
        if (!elements.btnWorkspaceSwitch || !elements.workspaceName) return;
        elements.workspaceName.textContent = state.workspace.name || 'Workspace';
        if (state.workspace.path) {
            elements.btnWorkspaceSwitch.title = `Switch workspace (${state.workspace.path})`;
        }
    }

    function showWorkspaceSwitcher() {
        const { overlay, modal, body, confirmBtn, close } = createModalShell(
            'Switch Workspace',
            'Set & Restart',
            'Cancel',
            { closeOnCancel: true }
        );

        modal.classList.add('workspace-switch-modal');

        const info = document.createElement('div');
        info.className = 'modal-text';
        const rootLabel = state.workspace.root ? `under ${state.workspace.root}` : 'under the workspace root';
        info.textContent = `Pick an existing workspace or type a new name ${rootLabel}. Restart required.`;
        body.appendChild(info);

        const error = document.createElement('div');
        error.className = 'modal-hint';
        body.appendChild(error);

        const rowSelect = document.createElement('div');
        rowSelect.className = 'modal-row';
        const selectLabel = document.createElement('label');
        selectLabel.className = 'modal-label';
        selectLabel.textContent = 'Existing';
        const select = document.createElement('select');
        select.className = 'modal-select';
        const available = state.workspace.available || [];
        if (available.length === 0) {
            const option = document.createElement('option');
            option.value = '';
            option.textContent = 'No workspaces found';
            select.appendChild(option);
            select.disabled = true;
        } else {
            available.forEach(name => {
                const option = document.createElement('option');
                option.value = name;
                option.textContent = name;
                select.appendChild(option);
            });
            if (state.workspace.name) {
                select.value = state.workspace.name;
            }
        }
        rowSelect.appendChild(selectLabel);
        rowSelect.appendChild(select);
        body.appendChild(rowSelect);

        const rowInput = document.createElement('div');
        rowInput.className = 'modal-row';
        const inputLabel = document.createElement('label');
        inputLabel.className = 'modal-label';
        inputLabel.textContent = 'New name';
        const input = document.createElement('input');
        input.className = 'modal-input';
        input.type = 'text';
        input.placeholder = 'e.g., whateverwonderfulprojectnametheuserwillcomeupwith';
        rowInput.appendChild(inputLabel);
        rowInput.appendChild(input);
        body.appendChild(rowInput);

        const selectName = () => input.value.trim() || select.value;

        confirmBtn.addEventListener('click', async () => {
            const name = selectName();
            if (!name) {
                error.textContent = 'Choose or enter a workspace name.';
                return;
            }
            confirmBtn.disabled = true;
            error.textContent = '';
            try {
                const result = await workspaceApi.select(name);
                const targetPath = result.targetPath || name;
                notificationStore.success(`Workspace set to ${name}. Restart to apply.`, 'global');
                log(`Workspace selection saved: ${targetPath}`, 'info');
                close();
            } catch (err) {
                error.textContent = err.message;
                confirmBtn.disabled = false;
            }
        });

        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                confirmBtn.click();
            }
        });
    }

    // Notification Store (frontend)
    function createNotificationStore() {
        const notifications = new Map();
        const listeners = new Set();

        function emit() {
            listeners.forEach(listener => listener());
        }

        function normalizeLevel(level) {
            if (!level) return 'info';
            const normalized = level.toString().trim().toLowerCase();
            if (normalized === 'warn') return 'warning';
            const allowed = ['info', 'success', 'warning', 'error'];
            return allowed.includes(normalized) ? normalized : 'info';
        }

        function normalizeScope(scope) {
            if (!scope) return 'global';
            const normalized = scope.toString().trim().toLowerCase();
            const allowed = ['global', 'workbench', 'editor', 'terminal', 'jobs'];
            return allowed.includes(normalized) ? normalized : 'global';
        }

        function normalizeCategory(category) {
            if (!category) return 'info';
            const normalized = category.toString().trim().toLowerCase();
            const allowed = ['blocking', 'attention', 'social', 'info'];
            return allowed.includes(normalized) ? normalized : 'info';
        }

        function generateId() {
            if (window.crypto && window.crypto.randomUUID) {
                return window.crypto.randomUUID();
            }
            return `notif-${Date.now()}-${Math.random().toString(16).slice(2)}`;
        }

        function applyDefaults(notification) {
            if (!notification.level) notification.level = 'info';
            if (!notification.scope) notification.scope = 'global';
            if (!notification.category) notification.category = 'info';
            if (!notification.timestamp) notification.timestamp = Date.now();
            if (typeof notification.persistent !== 'boolean') notification.persistent = false;
            if (typeof notification.read !== 'boolean') notification.read = false;
        }

        function push(level, scope, message, details, category, persistent, actionLabel, actionPayload, source) {
            const notification = {
                id: generateId(),
                level: normalizeLevel(level),
                scope: normalizeScope(scope),
                category: normalizeCategory(category),
                message: message || '',
                details: details || '',
                source: source || '',
                timestamp: Date.now(),
                actionLabel: actionLabel || '',
                actionPayload: actionPayload || null,
                persistent: Boolean(persistent),
                read: false
            };
            applyDefaults(notification);
            notifications.set(notification.id, notification);
            emit();
            return notification;
        }

        function info(message, scope) {
            return push('info', scope, message);
        }

        function success(message, scope) {
            return push('success', scope, message);
        }

        function warning(message, scope) {
            return push('warning', scope, message);
        }

        function error(message, scope, blocking) {
            const category = blocking ? 'blocking' : 'info';
            return push('error', scope, message, '', category, blocking, '', null, 'system');
        }

        function editorSaveSuccess(filePath) {
            return success(`Saved ${filePath}`, 'editor');
        }

        function editorSaveFailure(filePath, details) {
            return push('error', 'editor', `Save failed: ${filePath}`, details || '', 'blocking', true, 'Retry', null, 'editor');
        }

        function editorDiscardWarning(filePath) {
            return push('warning', 'editor', `Changes discarded in ${filePath}`, '', 'attention', false, '', null, 'editor');
        }

        function editorSearchNoResults(term, workspace) {
            const target = workspace ? 'workspace' : 'this file';
            return info(`No results for "${term}" in ${target}`, 'editor');
        }

        function issueCreated(issueId, title, author, assignee) {
            const details = `Author: ${author}` + (assignee ? ` | Assignee: ${assignee}` : '');
            return push('info', 'workbench', `Issue #${issueId} created: ${title}`, details, 'attention', false, 'Open issue',
                { kind: 'openIssue', issueId }, 'issues');
        }

        function issueClosed(issueId, title) {
            return push('success', 'workbench', `Issue #${issueId} closed: ${title}`, '', 'info', false, 'View',
                { kind: 'openIssue', issueId }, 'issues');
        }

        function issueCommentAdded(issueId, author) {
            return push('info', 'workbench', `New comment from ${author} on Issue #${issueId}`, '', 'social', false, 'Open issue',
                { kind: 'openIssue', issueId }, 'issues');
        }

        function agentPatchProposal(file, patchId) {
            return push('info', 'editor', `Patch proposed for ${file}`, `Patch: ${patchId}`, 'attention', true, 'Review Patch',
                { type: 'review-patch', patchId, filePath: file }, 'agent');
        }

        function getAll() {
            return Array.from(notifications.values()).sort((a, b) => b.timestamp - a.timestamp);
        }

        function getByLevelAndScope(levels, scopes) {
            return getAll().filter(notification => {
                if (levels && levels.size > 0 && !levels.has(notification.level)) {
                    return false;
                }
                if (scopes && scopes.size > 0 && !scopes.has(notification.scope)) {
                    return false;
                }
                return true;
            });
        }

        function getUnreadCount() {
            let count = 0;
            notifications.forEach(notification => {
                if (!notification.read) count++;
            });
            return count;
        }

        function getUnreadCountByScope(scope) {
            let count = 0;
            notifications.forEach(notification => {
                if (!notification.read && notification.scope === scope) count++;
            });
            return count;
        }

        function markRead(id) {
            if (!id) return;
            const notification = notifications.get(id);
            if (notification) {
                notification.read = true;
                emit();
            }
        }

        function markAllRead() {
            notifications.forEach(notification => {
                notification.read = true;
            });
            emit();
        }

        function clearNonErrors() {
            notifications.forEach((notification, id) => {
                if (notification.level !== 'error') {
                    notifications.delete(id);
                }
            });
            emit();
        }

        function clearAll() {
            notifications.clear();
            emit();
        }

        function subscribe(listener) {
            listeners.add(listener);
            return () => listeners.delete(listener);
        }

        async function loadFromServer() {
            try {
                const response = await fetch('/api/notifications');
                if (!response.ok) return;
                const data = await response.json();
                data.forEach(notification => {
                    applyDefaults(notification);
                    notifications.set(notification.id, notification);
                });
                emit();
            } catch (err) {
                console.warn('[NotificationStore] Failed to load from server:', err.message);
            }
        }

        return {
            loadFromServer,
            push,
            info,
            success,
            warning,
            error,
            editorSaveSuccess,
            editorSaveFailure,
            editorDiscardWarning,
            editorSearchNoResults,
            issueCreated,
            issueClosed,
            issueCommentAdded,
            agentPatchProposal,
            getAll,
            getByLevelAndScope,
            getUnreadCount,
            getUnreadCountByScope,
            markRead,
            markAllRead,
            clearNonErrors,
            clearAll,
            subscribe
        };
    }

    const notificationStore = createNotificationStore();

    function initNotifications() {
        state.notifications.store = notificationStore;

        // Load persisted notifications from server
        notificationStore.loadFromServer();

        notificationStore.subscribe(() => {
            renderToastStack();
            renderStatusBar();
            if (state.notifications.centerOpen) {
                renderNotificationCenterList();
            }
        });

        if (elements.notificationBell) {
            elements.notificationBell.addEventListener('click', () => {
                toggleNotificationCenter();
            });
        }

        if (elements.notificationMarkRead) {
            elements.notificationMarkRead.addEventListener('click', () => {
                notificationStore.markAllRead();
            });
        }

        if (elements.notificationClearNonErrors) {
            elements.notificationClearNonErrors.addEventListener('click', () => {
                notificationStore.clearNonErrors();
            });
        }

        if (elements.notificationClose) {
            elements.notificationClose.addEventListener('click', () => {
                closeNotificationCenter();
            });
        }

        if (elements.notificationFilterLevels) {
            elements.notificationFilterLevels.querySelectorAll('input[type="checkbox"]').forEach(input => {
                input.addEventListener('change', () => {
                    updateNotificationFilters();
                });
            });
        }

        if (elements.notificationFilterScopes) {
            elements.notificationFilterScopes.querySelectorAll('input[type="checkbox"]').forEach(input => {
                input.addEventListener('change', () => {
                    updateNotificationFilters();
                });
            });
        }

        if (elements.statusAlert) {
            elements.statusAlert.addEventListener('click', () => {
                const blocking = getMostRecentBlockingError();
                openNotificationCenter(blocking ? blocking.id : null);
            });
        }

        renderToastStack();
        renderStatusBar();
    }

    function updateNotificationFilters() {
        if (!elements.notificationFilterLevels || !elements.notificationFilterScopes) {
            return;
        }

        state.notifications.filters.levels.clear();
        state.notifications.filters.scopes.clear();

        elements.notificationFilterLevels.querySelectorAll('input[type="checkbox"]').forEach(input => {
            if (input.checked) {
                state.notifications.filters.levels.add(input.dataset.level);
            }
        });

        elements.notificationFilterScopes.querySelectorAll('input[type="checkbox"]').forEach(input => {
            if (input.checked) {
                state.notifications.filters.scopes.add(input.dataset.scope);
            }
        });

        renderNotificationCenterList();
    }

    function getMostRecentBlockingError() {
        const all = notificationStore.getAll();
        return all.find(notification => !notification.read && notification.level === 'error' && notification.category === 'blocking') || null;
    }

    function renderStatusBar() {
        if (!elements.notificationCount || !elements.statusAlert) {
            return;
        }

        const unreadCount = notificationStore.getUnreadCount();
        if (unreadCount > 0) {
            elements.notificationCount.textContent = unreadCount;
            elements.notificationCount.classList.remove('hidden');
        } else {
            elements.notificationCount.classList.add('hidden');
        }

        const blocking = getMostRecentBlockingError();
        if (blocking) {
            elements.statusAlert.textContent = blocking.message || 'Blocking error (click for details)';
            elements.statusAlert.classList.remove('hidden');
        } else {
            elements.statusAlert.classList.add('hidden');
        }
    }

    function renderToastStack() {
        if (!elements.toastStack) return;

        const active = notificationStore.getAll().filter(notification => !notification.read || notification.persistent);
        const visible = active.slice(0, state.notifications.toastLimit);
        const visibleIds = new Set(visible.map(notification => notification.id));

        for (const [id, timer] of state.notifications.toastTimers) {
            if (!visibleIds.has(id)) {
                clearTimeout(timer);
                state.notifications.toastTimers.delete(id);
            }
        }

        elements.toastStack.innerHTML = '';

        visible.forEach(notification => {
            const toast = document.createElement('div');
            toast.className = `toast toast-${notification.level}`;

            const level = document.createElement('div');
            level.className = 'toast-level';
            level.textContent = notification.level.toUpperCase();

            const message = document.createElement('div');
            message.className = 'toast-message';
            message.textContent = notification.message || '';

            toast.appendChild(level);
            toast.appendChild(message);

            if (notification.details) {
                const details = document.createElement('div');
                details.className = 'toast-details';
                details.textContent = notification.details;
                toast.appendChild(details);
            }

            if (notification.actionLabel) {
                const action = document.createElement('button');
                action.type = 'button';
                action.className = 'toast-action';
                action.textContent = notification.actionLabel;
                action.addEventListener('click', (e) => {
                    e.stopPropagation();
                    handleNotificationAction(notification);
                });
                toast.appendChild(action);
            }

            toast.addEventListener('click', () => {
                if (notification.actionLabel) {
                    handleNotificationAction(notification);
                } else {
                    openNotificationCenter(notification.id);
                    notificationStore.markRead(notification.id);
                }
            });

            elements.toastStack.appendChild(toast);
            scheduleToastDismiss(notification);
        });
    }

    function scheduleToastDismiss(notification) {
        if (!notification || notification.persistent) {
            return;
        }
        if (state.notifications.toastTimers.has(notification.id)) {
            return;
        }

        const duration = getToastDuration(notification.level);
        if (duration <= 0) return;

        const timer = setTimeout(() => {
            notificationStore.markRead(notification.id);
            state.notifications.toastTimers.delete(notification.id);
        }, duration);

        state.notifications.toastTimers.set(notification.id, timer);
    }

    function getToastDuration(level) {
        switch (level) {
            case 'success':
            case 'info':
                return 5000;
            case 'warning':
                return 9000;
            case 'error':
                return 12000;
            default:
                return 5000;
        }
    }

    function openNotificationCenter(highlightId) {
        if (!elements.notificationCenter) return;
        state.notifications.centerOpen = true;
        state.notifications.highlightId = highlightId || null;
        elements.notificationCenter.classList.remove('hidden');
        elements.notificationCenter.setAttribute('aria-hidden', 'false');
        renderNotificationCenterList();
    }

    function closeNotificationCenter() {
        if (!elements.notificationCenter) return;
        state.notifications.centerOpen = false;
        state.notifications.highlightId = null;
        elements.notificationCenter.classList.add('hidden');
        elements.notificationCenter.setAttribute('aria-hidden', 'true');
    }

    function toggleNotificationCenter() {
        if (state.notifications.centerOpen) {
            closeNotificationCenter();
        } else {
            openNotificationCenter();
        }
    }

    function renderNotificationCenterList() {
        if (!elements.notificationList) return;

        const levels = state.notifications.filters.levels;
        const scopes = state.notifications.filters.scopes;
        const notifications = notificationStore.getByLevelAndScope(levels, scopes);

        elements.notificationList.innerHTML = '';

        notifications.forEach(notification => {
            const item = document.createElement('div');
            item.className = 'notification-item';
            if (!notification.read) {
                item.classList.add('unread');
            }
            if (state.notifications.highlightId === notification.id) {
                item.classList.add('highlight');
            }

            const meta = document.createElement('div');
            meta.className = 'notification-meta';
            meta.innerHTML = `
                <span>${notification.level.toUpperCase()} · ${notification.scope}</span>
                <span>${formatTimestamp(notification.timestamp)}</span>
            `;

            const message = document.createElement('div');
            message.className = 'notification-message';
            message.textContent = notification.message || '';

            const details = document.createElement('div');
            details.className = 'notification-details';
            const detailText = [];
            if (notification.details) detailText.push(notification.details);
            if (notification.source) detailText.push(`Source: ${notification.source}`);
            details.textContent = detailText.join(' | ');

            if (notification.category) {
                const badge = document.createElement('span');
                badge.className = 'notification-badge';
                badge.textContent = notification.category.toUpperCase();
                meta.appendChild(badge);
            }

            item.appendChild(meta);
            item.appendChild(message);
            item.appendChild(details);

            if (notification.actionLabel) {
                const action = document.createElement('button');
                action.type = 'button';
                action.className = 'notification-action';
                action.textContent = notification.actionLabel;
                action.addEventListener('click', (e) => {
                    e.stopPropagation();
                    handleNotificationAction(notification);
                });
                item.appendChild(action);
            }

            item.addEventListener('click', () => {
                const payload = notification.actionPayload;
                const actionKind = payload && typeof payload === 'object' ? (payload.kind || payload.type) : '';
                if (actionKind === 'open-greeting-scan') {
                    handleNotificationAction(notification);
                    return;
                }
                item.classList.toggle('expanded');
                notificationStore.markRead(notification.id);
                state.notifications.highlightId = notification.id;
            });

            elements.notificationList.appendChild(item);
        });
    }

    function handleNotificationAction(notification) {
        if (!notification) return;
        dispatchNotificationAction(notification);
        notificationStore.markRead(notification.id);
    }

    function dispatchNotificationAction(notification) {
        const payload = notification.actionPayload;
        if (!payload || typeof payload !== 'object') {
            openNotificationCenter(notification.id);
            return;
        }

        // Support both 'kind' (new) and 'type' (legacy) keys
        const actionKind = payload.kind || payload.type;

        switch (actionKind) {
            case 'open-notification-center':
                openNotificationCenter(notification.id);
                break;
            case 'openIssue':
            case 'open-issue':
                // Close notification center first for a clean view
                closeNotificationCenter();
                // Open the issue modal
                if (payload.issueId) {
                    openIssueModal(payload.issueId);
                } else {
                    log('Cannot open issue: missing issueId', 'warning');
                }
                break;
            case 'open-greeting-scan':
                closeNotificationCenter();
                if (payload.agentId) {
                    showGreetingScanModal(payload.agentId);
                } else {
                    log('Cannot open greeting scan: missing agentId', 'warning');
                }
                break;
            case 'open-file':
                if (payload.filePath) {
                    openFile(payload.filePath);
                }
                openNotificationCenter(notification.id);
                break;
            case 'open-patch':
            case 'review-patch':
                log(`Patch review requested: ${payload.patchId || 'unknown'}`, 'info');
                openNotificationCenter(notification.id);
                break;
            default:
                openNotificationCenter(notification.id);
        }
    }

    function formatTimestamp(timestamp) {
        const date = new Date(timestamp);
        const pad = (value) => String(value).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
    }

    // Issue Detail Modal
    // Opens the issue modal and fetches issue data
    async function openIssueModal(issueId) {
        if (!issueId) return;

        state.issueModal.isOpen = true;
        state.issueModal.issueId = issueId;
        state.issueModal.isLoading = true;
        state.issueModal.error = null;
        state.issueModal.issue = null;

        renderIssueModal();

        try {
            const issue = await issueApi.get(issueId);
            state.issueModal.issue = issue;
            state.issueModal.isLoading = false;
            renderIssueModal();
        } catch (err) {
            state.issueModal.isLoading = false;
            state.issueModal.error = err.message;
            renderIssueModal();
        }
    }

    function closeIssueModal() {
        state.issueModal.isOpen = false;
        state.issueModal.issueId = null;
        state.issueModal.isLoading = false;
        state.issueModal.error = null;
        state.issueModal.issue = null;

        const overlay = document.getElementById('issue-modal-overlay');
        if (overlay) {
            overlay.remove();
        }

        // Return focus to notification bell if it exists
        if (elements.notificationBell) {
            elements.notificationBell.focus();
        }
    }

    function formatRelativeTime(timestamp) {
        const now = Date.now();
        const diff = now - timestamp;
        const seconds = Math.floor(diff / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);

        if (days > 0) return `${days}d ago`;
        if (hours > 0) return `${hours}h ago`;
        if (minutes > 0) return `${minutes}m ago`;
        return 'just now';
    }

    function getStatusClass(status) {
        switch (status) {
            case 'open': return 'status-open';
            case 'closed': return 'status-closed';
            case 'waiting-on-user': return 'status-waiting';
            default: return 'status-open';
        }
    }

    function getPriorityClass(priority) {
        switch (priority) {
            case 'urgent': return 'priority-urgent';
            case 'high': return 'priority-high';
            case 'normal': return 'priority-normal';
            case 'low': return 'priority-low';
            default: return 'priority-normal';
        }
    }

    function renderIssueModal() {
        // Remove existing overlay if any
        let overlay = document.getElementById('issue-modal-overlay');
        if (overlay) {
            overlay.remove();
        }

        if (!state.issueModal.isOpen) {
            return;
        }

        // Create overlay
        overlay = document.createElement('div');
        overlay.id = 'issue-modal-overlay';
        overlay.className = 'issue-modal-overlay';

        // Create modal container
        const modal = document.createElement('div');
        modal.className = 'issue-modal';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.setAttribute('aria-labelledby', 'issue-modal-title');

        // Loading state
        if (state.issueModal.isLoading) {
            modal.innerHTML = `
                <div class="issue-modal-header">
                    <h2 id="issue-modal-title" class="issue-modal-title">Loading Issue #${state.issueModal.issueId}...</h2>
                    <button type="button" class="issue-modal-close" aria-label="Close">&times;</button>
                </div>
                <div class="issue-modal-body">
                    <div class="issue-modal-loading">
                        <div class="issue-loading-spinner"></div>
                        <span>Loading issue details...</span>
                    </div>
                </div>
            `;
        }
        // Error state
        else if (state.issueModal.error) {
            modal.innerHTML = `
                <div class="issue-modal-header">
                    <h2 id="issue-modal-title" class="issue-modal-title">Issue #${state.issueModal.issueId}</h2>
                    <button type="button" class="issue-modal-close" aria-label="Close">&times;</button>
                </div>
                <div class="issue-modal-body">
                    <div class="issue-modal-error">
                        <span class="issue-error-icon">&#9888;</span>
                        <span class="issue-error-message">${escapeHtml(state.issueModal.error)}</span>
                    </div>
                </div>
            `;
        }
        // Issue loaded
        else if (state.issueModal.issue) {
            const issue = state.issueModal.issue;
            const statusLabel = issue.status.replace(/-/g, ' ');

            // Build tags HTML
            const tagsHtml = issue.tags && issue.tags.length > 0
                ? issue.tags.map(tag => `<span class="issue-tag">${escapeHtml(tag)}</span>`).join('')
                : '<span class="issue-no-tags">No tags</span>';

            // Build comments HTML
            let commentsHtml = '';
            if (issue.comments && issue.comments.length > 0) {
                commentsHtml = issue.comments.map(comment => {
                    let actionBadge = '';
                    if (comment.action && comment.action.type) {
                        actionBadge = `
                            <span class="comment-action-badge">
                                <span class="comment-action-type">${escapeHtml(comment.action.type)}</span>
                                ${comment.action.details ? `<span class="comment-action-details">${escapeHtml(comment.action.details)}</span>` : ''}
                            </span>
                        `;
                    }
                    return `
                        <div class="issue-comment">
                            <div class="comment-header">
                                <span class="comment-author">${escapeHtml(comment.author || 'Unknown')}</span>
                                <span class="comment-timestamp">${formatRelativeTime(comment.timestamp)}</span>
                                ${actionBadge}
                            </div>
                            <div class="comment-body">${escapeHtml(comment.body || '')}</div>
                        </div>
                    `;
                }).join('');
            } else {
                commentsHtml = '<div class="issue-no-comments">No comments yet</div>';
            }

            modal.innerHTML = `
                <div class="issue-modal-header">
                    <div class="issue-modal-title-row">
                        <h2 id="issue-modal-title" class="issue-modal-title">Issue #${issue.id}: ${escapeHtml(issue.title)}</h2>
                        <span class="issue-status-pill ${getStatusClass(issue.status)}">${escapeHtml(statusLabel)}</span>
                    </div>
                    <div class="issue-modal-meta-row">
                        <span class="issue-meta-item">
                            <span class="issue-meta-label">Author:</span>
                            <span class="issue-meta-value">${escapeHtml(issue.openedBy || 'Unknown')}</span>
                        </span>
                        ${issue.assignedTo ? `
                            <span class="issue-meta-item">
                                <span class="issue-meta-label">Assignee:</span>
                                <span class="issue-meta-value">${escapeHtml(issue.assignedTo)}</span>
                            </span>
                        ` : ''}
                    </div>
                    <button type="button" class="issue-modal-close" aria-label="Close">&times;</button>
                </div>

                <div class="issue-modal-body">
                    <div class="issue-meta-section">
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Tags:</span>
                            <div class="issue-tags-container">${tagsHtml}</div>
                        </div>
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Priority:</span>
                            <span class="issue-priority-pill ${getPriorityClass(issue.priority)}">${escapeHtml(issue.priority)}</span>
                        </div>
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Created:</span>
                            <span class="issue-meta-value">${formatTimestamp(issue.createdAt)}</span>
                        </div>
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Updated:</span>
                            <span class="issue-meta-value">${formatTimestamp(issue.updatedAt)}</span>
                        </div>
                        ${issue.closedAt ? `
                            <div class="issue-meta-group">
                                <span class="issue-meta-label">Closed:</span>
                                <span class="issue-meta-value">${formatTimestamp(issue.closedAt)}</span>
                            </div>
                        ` : ''}
                    </div>

                    <div class="issue-body-section">
                        <h3 class="issue-section-title">Description</h3>
                        <div class="issue-body-content">${escapeHtml(issue.body || 'No description provided.')}</div>
                    </div>

                    <div class="issue-comments-section">
                        <h3 class="issue-section-title">Comments (${issue.comments ? issue.comments.length : 0})</h3>
                        <div class="issue-comments-list">${commentsHtml}</div>
                    </div>
                </div>

                <div class="issue-modal-footer">
                    <div class="issue-modal-actions-placeholder">
                        <!-- Future actions: Add Comment, Close Issue, etc. -->
                        <button type="button" class="issue-action-btn issue-action-disabled" disabled title="Coming soon">Add Comment</button>
                        <button type="button" class="issue-action-btn issue-action-disabled" disabled title="Coming soon">Close Issue</button>
                    </div>
                    <button type="button" class="issue-modal-btn-close">Close</button>
                </div>
            `;
        }

        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        // Event listeners
        const closeBtn = modal.querySelector('.issue-modal-close');
        if (closeBtn) {
            closeBtn.addEventListener('click', closeIssueModal);
        }

        const footerCloseBtn = modal.querySelector('.issue-modal-btn-close');
        if (footerCloseBtn) {
            footerCloseBtn.addEventListener('click', closeIssueModal);
        }

        // Close on overlay click (outside modal)
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) {
                closeIssueModal();
            }
        });

        // Keyboard handling
        function handleKeydown(e) {
            if (e.key === 'Escape') {
                closeIssueModal();
                document.removeEventListener('keydown', handleKeydown);
            }
        }
        document.addEventListener('keydown', handleKeydown);

        // Focus the close button for accessibility
        if (closeBtn) {
            closeBtn.focus();
        }
    }

    // ============================================
    // VIEW MODE SWITCHING (Workbench / Editor / Settings)
    // ============================================

    function updateModeControls(mode) {
        if (elements.btnToggleMode) {
            const isWorkbench = mode === 'workbench';
            const toggleLabel = mode === 'settings' ? 'Back to Editor' : (isWorkbench ? 'Switch to Editor' : 'Switch to Workbench');
            elements.btnToggleMode.classList.toggle('is-active', isWorkbench);
            elements.btnToggleMode.title = toggleLabel;
            elements.btnToggleMode.setAttribute('aria-label', toggleLabel);
        }

        if (elements.btnOpenSettings) {
            elements.btnOpenSettings.classList.toggle('is-active', mode === 'settings');
        }
    }

    function setViewMode(mode) {
        const validModes = ['editor', 'workbench', 'settings'];
        if (!validModes.includes(mode)) {
            log(`Invalid view mode: ${mode}`, 'warning');
            return;
        }

        const previousMode = state.viewMode.current;
        state.viewMode.current = mode;

        updateModeControls(mode);

        // Update view panels
        document.querySelectorAll('.view-panel').forEach(panel => {
            panel.classList.remove('active');
        });

        const targetPanel = document.getElementById(`${mode}-view`);
        if (targetPanel) {
            targetPanel.classList.add('active');
        }

        // Mode-specific initialization
        if (mode === 'workbench') {
            renderWorkbenchView();
        } else if (mode === 'editor') {
            // Re-layout Monaco editor when switching back
            if (state.editor) {
                setTimeout(() => state.editor.layout(), 50);
            }
        }

        log(`Switched to ${mode} view`, 'info');
    }

    function isWorkbenchView() {
        return state.viewMode.current === 'workbench';
    }

    function isEditorView() {
        return state.viewMode.current === 'editor';
    }

    // ============================================
    // WORKBENCH VIEW RENDERING
    // ============================================

    function renderWorkbenchView() {
        renderAgentSidebar();
        renderWorkbenchChatPane();
        renderWorkbenchNewsfeed();
    }

    function renderAgentSidebar() {
        const container = document.getElementById('agent-list');
        if (!container) return;

        container.innerHTML = '';
        const agents = state.agents.list || [];

        if (agents.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'agent-empty';
            empty.textContent = 'No agents available';
            container.appendChild(empty);
            return;
        }

        agents.forEach(agent => {
            const item = document.createElement('div');
            item.className = 'agent-item';
            item.dataset.agentId = agent.id || '';

            const icon = document.createElement('span');
            icon.className = 'agent-icon';

            const avatarData = agent.avatar && agent.avatar.trim() ? agent.avatar.trim() : '';

            if (avatarData.startsWith('data:') || avatarData.startsWith('http')) {
                // Image avatar
                const img = document.createElement('img');
                img.src = avatarData;
                img.alt = agent.name || 'Agent';
                img.className = 'agent-icon-img';
                icon.appendChild(img);
                icon.classList.add('has-image');
            } else if (avatarData) {
                // Emoji or text avatar
                icon.textContent = avatarData;
            } else {
                // Fallback to first letter
                icon.textContent = agent.name ? agent.name.charAt(0).toUpperCase() : '?';
            }

            if (agent.color && !avatarData.startsWith('data:') && !avatarData.startsWith('http')) {
                icon.style.background = agent.color;
            }

            const info = document.createElement('div');
            info.className = 'agent-info';

            const name = document.createElement('div');
            name.className = 'agent-name';
            const fullName = agent.name || 'Unnamed Agent';
            name.textContent = fullName;
            name.title = fullName; // Tooltip for truncated names

            const role = document.createElement('div');
            role.className = 'agent-role';
            role.textContent = agent.role || 'role';

            info.appendChild(name);
            info.appendChild(role);

            const status = document.createElement('div');
            status.className = `agent-status ${agent.enabled === false ? '' : 'online'}`;
            status.title = agent.enabled === false ? 'Offline' : 'Online';

            item.appendChild(icon);
            item.appendChild(info);
            item.appendChild(status);

            item.addEventListener('click', () => {
                container.querySelectorAll('.agent-item').forEach(el => el.classList.remove('active'));
                item.classList.add('active');
                log(`Selected agent: ${agent.name}`, 'info');
            });

            item.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                showAgentContextMenu(e, agent);
            });

            container.appendChild(item);
        });
    }

    function renderWorkbenchChatPane() {
        // Now renders the Issue Board instead of placeholder
        renderIssueBoard();
    }

    // ============================================
    // ISSUE BOARD
    // ============================================

    async function loadIssues() {
        state.issueBoard.isLoading = true;
        state.issueBoard.error = null;
        renderIssueBoardContent();

        try {
            // Build filters for API call
            const filters = {};
            if (state.issueBoard.filters.status !== 'all') {
                filters.status = state.issueBoard.filters.status;
            }
            if (state.issueBoard.filters.priority !== 'all') {
                filters.priority = state.issueBoard.filters.priority;
            }

            const issues = await issueApi.list(filters);
            state.issueBoard.issues = issues;
            state.issueBoard.isLoading = false;
            renderIssueBoardContent();
        } catch (err) {
            state.issueBoard.isLoading = false;
            state.issueBoard.error = err.message;
            renderIssueBoardContent();
        }
    }

    function renderIssueBoard() {
        const container = document.getElementById('workbench-chat-content');
        if (!container) return;

        container.innerHTML = `
            <div class="issue-board">
                <div class="issue-board-header">
                    <div class="issue-board-title">
                        <span class="issue-board-icon">📋</span>
                        <span>Issue Board</span>
                    </div>
                    <div class="issue-board-actions">
                        <button type="button" class="issue-board-btn" id="issue-board-refresh" title="Refresh">
                            <span>↻</span>
                        </button>
                    </div>
                </div>
                <div class="issue-board-filters">
                    <div class="issue-filter-group">
                        <label class="issue-filter-label">Status</label>
                        <select id="issue-filter-status" class="issue-filter-select">
                            <option value="all">All</option>
                            <option value="open">Open</option>
                            <option value="closed">Closed</option>
                            <option value="waiting-on-user">Waiting</option>
                        </select>
                    </div>
                    <div class="issue-filter-group">
                        <label class="issue-filter-label">Priority</label>
                        <select id="issue-filter-priority" class="issue-filter-select">
                            <option value="all">All</option>
                            <option value="urgent">Urgent</option>
                            <option value="high">High</option>
                            <option value="normal">Normal</option>
                            <option value="low">Low</option>
                        </select>
                    </div>
                    <div class="issue-filter-stats" id="issue-filter-stats"></div>
                </div>
                <div class="issue-board-content" id="issue-board-list"></div>
            </div>
        `;

        // Set current filter values
        const statusSelect = document.getElementById('issue-filter-status');
        const prioritySelect = document.getElementById('issue-filter-priority');
        if (statusSelect) statusSelect.value = state.issueBoard.filters.status;
        if (prioritySelect) prioritySelect.value = state.issueBoard.filters.priority;

        // Wire event listeners
        initIssueBoardListeners();

        // Load issues
        loadIssues();
    }

    function initIssueBoardListeners() {
        const refreshBtn = document.getElementById('issue-board-refresh');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                loadIssues();
            });
        }

        const statusSelect = document.getElementById('issue-filter-status');
        if (statusSelect) {
            statusSelect.addEventListener('change', (e) => {
                state.issueBoard.filters.status = e.target.value;
                loadIssues();
            });
        }

        const prioritySelect = document.getElementById('issue-filter-priority');
        if (prioritySelect) {
            prioritySelect.addEventListener('change', (e) => {
                state.issueBoard.filters.priority = e.target.value;
                loadIssues();
            });
        }
    }

    function renderIssueBoardContent() {
        const container = document.getElementById('issue-board-list');
        const statsContainer = document.getElementById('issue-filter-stats');
        if (!container) return;

        // Loading state
        if (state.issueBoard.isLoading) {
            container.innerHTML = `
                <div class="issue-board-loading">
                    <div class="issue-loading-spinner"></div>
                    <span>Loading issues...</span>
                </div>
            `;
            if (statsContainer) statsContainer.textContent = '';
            return;
        }

        // Error state
        if (state.issueBoard.error) {
            container.innerHTML = `
                <div class="issue-board-error">
                    <span class="issue-error-icon">⚠</span>
                    <span>${escapeHtml(state.issueBoard.error)}</span>
                    <button type="button" class="issue-retry-btn" onclick="loadIssues()">Retry</button>
                </div>
            `;
            if (statsContainer) statsContainer.textContent = '';
            return;
        }

        const issues = state.issueBoard.issues;

        // Update stats
        if (statsContainer) {
            const openCount = issues.filter(i => i.status === 'open').length;
            statsContainer.textContent = `${issues.length} issue${issues.length !== 1 ? 's' : ''} (${openCount} open)`;
        }

        // Empty state
        if (issues.length === 0) {
            container.innerHTML = `
                <div class="issue-board-empty">
                    <span class="issue-empty-icon">📭</span>
                    <span class="issue-empty-text">No issues found</span>
                    <span class="issue-empty-hint">Issues created by agents will appear here</span>
                </div>
            `;
            return;
        }

        // Render issue cards
        container.innerHTML = '';
        issues.forEach(issue => {
            const card = createIssueCard(issue);
            container.appendChild(card);
        });
    }

    function createIssueCard(issue) {
        const card = document.createElement('div');
        card.className = 'issue-card';
        card.dataset.issueId = issue.id;

        // Status class
        const statusClass = `issue-status-${issue.status.replace(/-/g, '')}`;

        // Priority indicator
        const priorityClass = `issue-priority-${issue.priority}`;
        const priorityIcon = getPriorityIcon(issue.priority);

        // Comment count
        const commentCount = issue.comments ? issue.comments.length : 0;

        // Tags (show first 2)
        const tagsHtml = issue.tags && issue.tags.length > 0
            ? issue.tags.slice(0, 2).map(t => `<span class="issue-card-tag">${escapeHtml(t)}</span>`).join('')
            : '';
        const moreTagsHtml = issue.tags && issue.tags.length > 2
            ? `<span class="issue-card-tag-more">+${issue.tags.length - 2}</span>`
            : '';

        card.innerHTML = `
            <div class="issue-card-header">
                <span class="issue-card-id">#${issue.id}</span>
                <span class="issue-card-status ${statusClass}">${formatStatus(issue.status)}</span>
            </div>
            <div class="issue-card-title">${escapeHtml(issue.title)}</div>
            <div class="issue-card-meta">
                <span class="issue-card-priority ${priorityClass}" title="${issue.priority} priority">
                    ${priorityIcon}
                </span>
                ${issue.assignedTo ? `<span class="issue-card-assignee" title="Assigned to ${issue.assignedTo}">→ ${escapeHtml(issue.assignedTo)}</span>` : ''}
                ${commentCount > 0 ? `<span class="issue-card-comments" title="${commentCount} comment${commentCount !== 1 ? 's' : ''}">💬 ${commentCount}</span>` : ''}
                <span class="issue-card-time" title="${formatTimestamp(issue.updatedAt)}">${formatRelativeTime(issue.updatedAt)}</span>
            </div>
            ${tagsHtml || moreTagsHtml ? `<div class="issue-card-tags">${tagsHtml}${moreTagsHtml}</div>` : ''}
        `;

        // Click to open issue modal
        card.addEventListener('click', () => {
            openIssueModal(issue.id);
        });

        return card;
    }

    function getPriorityIcon(priority) {
        switch (priority) {
            case 'urgent': return '🔴';
            case 'high': return '🟠';
            case 'normal': return '🔵';
            case 'low': return '⚪';
            default: return '🔵';
        }
    }

    function formatStatus(status) {
        switch (status) {
            case 'open': return 'Open';
            case 'closed': return 'Closed';
            case 'waiting-on-user': return 'Waiting';
            default: return status;
        }
    }

    // ============================================
    // WORKBENCH NEWSFEED (Notification-backed)
    // ============================================

    function renderWorkbenchNewsfeed() {
        const container = document.getElementById('newsfeed-list');
        if (!container) return;

        // Get all notifications
        const allNotifications = notificationStore.getAll();

        // Filter to workbench-related and issue-related notifications
        const filtered = allNotifications.filter(notification => {
            // Include workbench scope
            if (notification.scope === 'workbench') return true;
            // Include issue-related (by source)
            if (notification.source === 'issues') return true;
            // Include if actionPayload indicates openIssue
            if (notification.actionPayload) {
                const kind = notification.actionPayload.kind || notification.actionPayload.type;
                if (kind === 'openIssue' || kind === 'open-issue') return true;
            }
            return false;
        });

        // Limit to last 20
        const limited = filtered.slice(0, 20);

        container.innerHTML = '';

        if (limited.length === 0) {
            container.innerHTML = '<div class="newsfeed-empty">No recent activity</div>';
            return;
        }

        limited.forEach(notification => {
            const item = document.createElement('div');
            item.className = 'newsfeed-item';
            if (!notification.read) {
                item.classList.add('unread');
            }

            // Build action link if applicable
            let actionHtml = '';
            if (notification.actionLabel && notification.actionPayload) {
                actionHtml = `<span class="newsfeed-action">${escapeHtml(notification.actionLabel)}</span>`;
            }

            item.innerHTML = `
                <div class="newsfeed-item-header">
                    <span class="newsfeed-level-badge ${notification.level}">${notification.level.toUpperCase()}</span>
                    <span class="newsfeed-timestamp">${formatRelativeTime(notification.timestamp)}</span>
                </div>
                <div class="newsfeed-message">${escapeHtml(notification.message)}</div>
                ${actionHtml}
            `;

            item.addEventListener('click', () => {
                handleNewsfeedItemClick(notification);
            });

            container.appendChild(item);
        });
    }

    function handleNewsfeedItemClick(notification) {
        // Mark as read
        notificationStore.markRead(notification.id);

        // Check for actionPayload
        const payload = notification.actionPayload;
        if (payload && typeof payload === 'object') {
            dispatchNotificationAction(notification);
            return;
        }

        // Default: just mark read and re-render
        renderWorkbenchNewsfeed();
    }

    // Subscribe to notification changes to update Newsfeed when in Workbench view
    function initWorkbenchNewsfeedSubscription() {
        notificationStore.subscribe(() => {
            if (isWorkbenchView()) {
                renderWorkbenchNewsfeed();
            }
        });
    }

    // API Functions
    async function api(endpoint, options = {}) {
        try {
            const response = await fetch(endpoint, options);
            if (!response.ok) {
                const error = await response.json().catch(() => ({ error: response.statusText }));
                throw new Error(error.error || 'Request failed');
            }
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                return response.json();
            }
            return response.text();
        } catch (err) {
            log(`API Error: ${err.message}`, 'error');
            throw err;
        }
    }

    // Scene Segments API
    async function apiGetSegments(path, forceRefresh = false) {
        path = normalizeWorkspacePath(path);

        // Return cached if available and not forcing refresh
        if (!forceRefresh && state.segments.has(path)) {
            return state.segments.get(path);
        }

        try {
            const segments = await api(`/api/segments?path=${encodeURIComponent(path)}`);
            state.segments.set(path, segments);
            log(`Loaded ${segments.length} segment(s) for: ${path}`, 'info');
            return segments;
        } catch (err) {
            log(`Failed to load segments for ${path}: ${err.message}`, 'error');
            throw err;
        }
    }

    // File Tree
    async function loadFileTree() {
        try {
            const tree = await api('/api/tree');
            state.fileTree = tree;
            renderFileTree(tree);
            log('File tree loaded', 'info');
        } catch (err) {
            log(`Failed to load file tree: ${err.message}`, 'error');
        }
    }

    function renderFileTree(node, container = elements.fileTree, depth = 0) {
        if (depth === 0) {
            container.innerHTML = '';
        }

        if (node.type === 'folder' && node.children) {
            if (depth > 0) {
                const folderItem = createTreeItem(node, depth);
                container.appendChild(folderItem.element);
                container = folderItem.childContainer;
            }

            node.children.forEach(child => {
                renderFileTree(child, container, depth + 1);
            });
        } else if (node.type === 'file') {
            const fileItem = createTreeItem(node, depth);
            container.appendChild(fileItem.element);
        }
    }

    function createTreeItem(node, depth) {
        const item = document.createElement('div');
        item.className = `tree-item ${node.type === 'folder' ? 'tree-folder' : 'tree-file'}`;
        item.style.setProperty('--indent', `${depth * 16}px`);

        const icon = document.createElement('span');
        icon.className = 'tree-icon';
        icon.textContent = node.type === 'folder' ? '📁' : getFileIcon(node.name);

        const name = document.createElement('span');
        name.className = 'tree-name';
        name.textContent = node.name;

        item.appendChild(icon);
        item.appendChild(name);

        let childContainer = null;

        if (node.type === 'folder') {
            childContainer = document.createElement('div');
            childContainer.className = 'tree-children';

            item.addEventListener('click', (e) => {
                e.stopPropagation();
                const isExpanded = childContainer.classList.toggle('expanded');
                icon.textContent = isExpanded ? '📂' : '📁';
            });

            // Context menu for folders (rename/delete)
            item.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                showContextMenu(e, node);
            });

            // Auto-expand root level folders
            if (depth === 1) {
                childContainer.classList.add('expanded');
                icon.textContent = '📂';
            }
        } else {
            item.addEventListener('click', (e) => {
                e.stopPropagation();
                openFile(node.path);
            });

            item.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                showContextMenu(e, node);
            });
        }

        item.dataset.path = node.path;

        const wrapper = document.createElement('div');
        wrapper.appendChild(item);
        if (childContainer) {
            wrapper.appendChild(childContainer);
        }

        return { element: wrapper, childContainer };
    }

    function getFileIcon(filename) {
        const ext = filename.split('.').pop().toLowerCase();
        const icons = {
            'md': '📝',
            'txt': '📄',
            'js': '📜',
            'json': '📋',
            'html': '🌐',
            'css': '🎨',
            'java': '☕',
            'py': '🐍',
            'rb': '💎',
            'go': '🔵',
            'rs': '🦀'
        };
        return icons[ext] || '📄';
    }

    // File Operations
    // Opens file - reuses existing tab if one exists for this path
    async function openFile(path) {
        path = normalizeWorkspacePath(path);
        try {
            // Check if there's already a tab for this path
            const existingTabId = findTabIdByPath(path);
            if (existingTabId) {
                // Reuse existing tab
                setActiveTab(existingTabId);
                return;
            }

            // No existing tab - need to open the file
            await ensureFileLoaded(path);

            // Create a new tab for this file
            const tabId = generateTabId();
            state.openTabs.set(tabId, { path });

            createTab(tabId, path);
            setActiveTab(tabId);
        } catch (err) {
            log(`Failed to open file: ${err.message}`, 'error');
        }
    }

    // Open file in a NEW tab, even if already open elsewhere
    // Multiple tabs share the same underlying file state
    async function openFileInNewTab(path) {
        path = normalizeWorkspacePath(path);
        try {
            log(`Opening file in new tab: ${path}`, 'info');

            // Ensure file is loaded (reuses existing if already loaded)
            await ensureFileLoaded(path);

            // Always create a new tab
            const tabId = generateTabId();
            state.openTabs.set(tabId, { path });

            createTab(tabId, path);
            setActiveTab(tabId);
        } catch (err) {
            log(`Failed to open file: ${err.message}`, 'error');
        }
    }

    // Ensure a file is loaded into openFiles (loads from backend if needed)
    async function ensureFileLoaded(path) {
        path = normalizeWorkspacePath(path);
        if (state.openFiles.has(path)) {
            // Already loaded - nothing to do
            return;
        }

        log(`Loading file: ${path}`, 'info');
        const content = await api(`/api/file?path=${encodeURIComponent(path)}`);
        const model = monaco.editor.createModel(content, getLanguageForFile(path));

        state.openFiles.set(path, {
            model,
            content,
            originalContent: content
        });
    }

    // Find first tab ID for a given path
    function findTabIdByPath(path) {
        path = normalizeWorkspacePath(path);
        for (const [tabId, tabData] of state.openTabs) {
            if (tabData.path === path) {
                return tabId;
            }
        }
        return null;
    }

    function getLanguageForFile(path) {
        const ext = path.split('.').pop().toLowerCase();
        const languages = {
            'js': 'javascript',
            'ts': 'typescript',
            'json': 'json',
            'html': 'html',
            'css': 'css',
            'md': 'markdown',
            'java': 'java',
            'py': 'python',
            'rb': 'ruby',
            'go': 'go',
            'rs': 'rust',
            'xml': 'xml',
            'yaml': 'yaml',
            'yml': 'yaml',
            'sql': 'sql'
        };
        return languages[ext] || 'plaintext';
    }

    function setActiveTab(tabId) {
        const tabData = state.openTabs.get(tabId);
        if (!tabData) return;

        const file = state.openFiles.get(tabData.path);
        if (!file) return;

        state.activeTabId = tabId;
        state.activeFile = tabData.path;

        if (state.editor) {
            state.editor.setModel(file.model);
            elements.editorPlaceholder.classList.add('hidden');
            elements.monacoEditor.classList.add('active');
        }

        // Update tab active state
        document.querySelectorAll('.tab').forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tabId === tabId);
        });

        // Update tree selection
        document.querySelectorAll('.tree-item').forEach(item => {
            item.classList.toggle('selected', item.dataset.path === tabData.path);
        });

        // Update Reveal File, Open Folder, and Find button states
        elements.btnRevealFile.disabled = !tabData.path;
        elements.btnOpenFolder.disabled = !tabData.path;
        elements.btnFind.disabled = !tabData.path;
    }

    function createTab(tabId, path) {
        const tab = document.createElement('div');
        tab.className = 'tab';
        tab.dataset.tabId = tabId;
        tab.dataset.path = path;

        const name = document.createElement('span');
        name.className = 'tab-name';
        name.textContent = path.split('/').pop();

        const close = document.createElement('button');
        close.className = 'tab-close';
        close.textContent = '×';
        close.addEventListener('click', (e) => {
            e.stopPropagation();
            closeTab(tabId);
        });

        tab.appendChild(name);
        tab.appendChild(close);

        tab.addEventListener('click', () => setActiveTab(tabId));

        elements.tabsContainer.appendChild(tab);
    }

    // Update dirty state for ALL tabs showing a given path
    function updateDirtyStateForPath(path) {
        path = normalizeWorkspacePath(path);
        const file = state.openFiles.get(path);
        if (!file) return;

        const isDirty = file.content !== file.originalContent;

        // Update all tabs that reference this path
        for (const [tabId, tabData] of state.openTabs) {
            if (tabData.path === path) {
                const tabEl = document.querySelector(`.tab[data-tab-id="${tabId}"]`);
                if (tabEl) {
                    tabEl.classList.toggle('dirty', isDirty);
                }
            }
        }
    }

    // Close a specific tab by ID
    function closeTab(tabId, force = false) {
        const tabData = state.openTabs.get(tabId);
        if (!tabData) return;

        const path = tabData.path;
        const file = state.openFiles.get(path);

        // Check for unsaved changes (unless forced)
        if (!force && file && file.content !== file.originalContent) {
            const confirmed = confirm(`${path} has unsaved changes. Close anyway?`);
            if (!confirmed) return;
            notificationStore.editorDiscardWarning(path);
        }

        // Remove tab element
        const tab = document.querySelector(`.tab[data-tab-id="${tabId}"]`);
        if (tab) tab.remove();

        // Remove tab from openTabs
        state.openTabs.delete(tabId);

        // If this was the last tab for this path, dispose the model and remove from openFiles
        if (countTabsForPath(path) === 0 && file) {
            if (file.model) {
                file.model.dispose();
            }
            state.openFiles.delete(path);
        }

        // Switch to another tab or show placeholder
        if (state.activeTabId === tabId) {
            const remaining = Array.from(state.openTabs.keys());
            if (remaining.length > 0) {
                setActiveTab(remaining[remaining.length - 1]);
            } else {
                state.activeFile = null;
                state.activeTabId = null;
                elements.editorPlaceholder.classList.remove('hidden');
                elements.monacoEditor.classList.remove('active');
                elements.btnRevealFile.disabled = true;
                elements.btnOpenFolder.disabled = true;
                elements.btnFind.disabled = true;
            }
        }

        log(`Closed tab: ${path}`, 'info');
    }

    // Close all tabs matching a path (for file deletion)
    // For folders, closes all tabs whose path starts with folderPath/
    // Also cleans up openFiles entries for deleted paths
    function closeTabsForPath(path, isFolder = false) {
        path = normalizeWorkspacePath(path);
        const tabsToClose = [];
        const filesToCleanup = [];

        // Find matching tabs
        for (const [tabId, tabData] of state.openTabs) {
            if (isFolder) {
                const folderPrefix = path + '/';
                if (tabData.path === path || tabData.path.startsWith(folderPrefix)) {
                    tabsToClose.push(tabId);
                }
            } else {
                if (tabData.path === path) {
                    tabsToClose.push(tabId);
                }
            }
        }

        // Find matching openFiles entries to clean up
        for (const [filePath] of state.openFiles) {
            if (isFolder) {
                const folderPrefix = path + '/';
                if (filePath === path || filePath.startsWith(folderPrefix)) {
                    filesToCleanup.push(filePath);
                }
            } else {
                if (filePath === path) {
                    filesToCleanup.push(filePath);
                }
            }
        }

        // Close all matching tabs (force close - no unsaved prompt for deleted files)
        tabsToClose.forEach(tabId => closeTab(tabId, true));

        // Clean up any remaining openFiles entries (in case tabs were already closed)
        filesToCleanup.forEach(filePath => {
            const file = state.openFiles.get(filePath);
            if (file) {
                if (file.model) {
                    file.model.dispose();
                }
                state.openFiles.delete(filePath);
            }
        });

        if (tabsToClose.length > 0) {
            log(`Closed ${tabsToClose.length} tab(s) for deleted path: ${path}`, 'info');
        }
    }

    async function saveCurrentFile() {
        if (!state.activeFile) return;

        const file = state.openFiles.get(state.activeFile);
        if (!file) return;

        try {
            await api(`/api/file?path=${encodeURIComponent(state.activeFile)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'text/plain' },
                body: file.content
            });

            file.originalContent = file.content;
            updateDirtyStateForPath(state.activeFile);
            log(`Saved: ${state.activeFile}`, 'success');
            notificationStore.editorSaveSuccess(state.activeFile);
        } catch (err) {
            log(`Failed to save: ${err.message}`, 'error');
            notificationStore.editorSaveFailure(state.activeFile, err.message);
        }
    }

    // Save all dirty files (iterates over openFiles, not tabs)
    async function saveAllFiles() {
        for (const [path, file] of state.openFiles) {
            if (file.content !== file.originalContent) {
                try {
                    await api(`/api/file?path=${encodeURIComponent(path)}`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'text/plain' },
                        body: file.content
                    });
                    file.originalContent = file.content;
                    updateDirtyStateForPath(path);
                    log(`Saved: ${path}`, 'success');
                    notificationStore.editorSaveSuccess(path);
                } catch (err) {
                    log(`Failed to save ${path}: ${err.message}`, 'error');
                    notificationStore.editorSaveFailure(path, err.message);
                }
            }
        }
    }

    async function explorePath(path, nodeType) {
        path = normalizeWorkspacePath(path);
        if (!path) return;

        const isFolder = nodeType === 'folder';
        const label = isFolder ? 'folder' : 'file';
        log(`Exploring ${label}: ${path}`, 'info');

        try {
            const endpoint = isFolder ? '/api/file/open-folder' : '/api/file/reveal';
            const result = await api(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ path })
            });
            if (result.ok) {
                if (result.fallback === 'open-folder') {
                    log(`Reveal failed; opened containing folder instead for ${label}`, 'warning');
                } else {
                    log(`Opened ${label} in explorer`, 'success');
                }
            } else {
                log(`Failed to explore ${label}: ${result.error}`, 'error');
            }
        } catch (err) {
            log(`Failed to explore ${label}: ${err.message}`, 'error');
        }
    }

    function handleAgentMenuAction(action, agent) {
        const agentName = agent && agent.name ? agent.name : 'Agent';
        switch (action) {
            case 'invite-conference':
                showConferenceInviteModal(agent);
                break;
            case 'invite-chat':
                log(`Invited ${agentName} to chat`, 'info');
                break;
            case 'open-profile':
                showAgentProfileModal(agent);
                break;
            case 'open-role-settings':
                showRoleSettingsModal(agent);
                break;
            case 'open-agent-settings':
                showAgentSettingsModal(agent);
                break;
            case 'change-role':
                showChangeRoleModal(agent);
                break;
            case 'export':
                exportAgent(agent);
                break;
            case 'duplicate':
                duplicateAgent(agent);
                break;
            case 'retire':
                showConfirmRetireModal(agent);
                break;
            default:
                log(`Unknown action for ${agentName}`, 'warning');
                break;
        }
    }

    function exportAgent(agent) {
        // Create a clean export object (remove internal IDs that shouldn't transfer)
        const exportData = {
            name: agent.name,
            role: agent.role,
            avatar: agent.avatar,
            color: agent.color,
            personality: agent.personality,
            personalitySliders: agent.personalitySliders,
            signatureLine: agent.signatureLine,
            skills: agent.skills,
            goals: agent.goals,
            memoryProfile: agent.memoryProfile,
            exportedAt: new Date().toISOString(),
            exportVersion: 1
        };

        const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `agent-${agent.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        log(`Exported agent: ${agent.name}`, 'success');
        notificationStore.success(`Exported ${agent.name}`, 'workbench');
    }

    async function duplicateAgent(agent) {
        const duplicateData = {
            ...agent,
            id: null, // Will be generated by backend
            name: `${agent.name} (Copy)`,
            clonedFrom: agent.id
        };

        try {
            const imported = await agentApi.import(duplicateData);
            log(`Duplicated agent: ${imported.name}`, 'success');
            notificationStore.success(`Created ${imported.name}`, 'workbench');
            await loadAgents();
        } catch (err) {
            log(`Failed to duplicate agent: ${err.message}`, 'error');
            notificationStore.error(`Failed to duplicate: ${err.message}`, 'workbench');
        }
    }

    function showImportAgentDialog() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json';
        input.style.display = 'none';

        input.addEventListener('change', async (e) => {
            const file = e.target.files?.[0];
            if (!file) return;

            try {
                const text = await file.text();
                const agentData = JSON.parse(text);

                // Validate basic structure
                if (!agentData.name || !agentData.role) {
                    throw new Error('Invalid agent file: missing name or role');
                }

                const imported = await agentApi.import(agentData);
                log(`Imported agent: ${imported.name}`, 'success');
                notificationStore.success(`Imported ${imported.name}`, 'workbench');
                await loadAgents();
            } catch (err) {
                log(`Failed to import agent: ${err.message}`, 'error');
                notificationStore.error(`Import failed: ${err.message}`, 'workbench');
            }

            input.remove();
        });

        document.body.appendChild(input);
        input.click();
    }

    function showAddAgentWizard(resume = {}) {
        const { state: resumeState, stepIndex: resumeStepIndex } = resume;
        const { modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            'Add Agent',
            'Next',
            'Cancel',
            { closeOnCancel: false }
        );

        modal.classList.add('agent-create-modal');

        const templates = [
            {
                id: 'creative',
                label: 'Creative Voice',
                role: 'writer',
                description: 'Drafts prose, scene flow, and voice.',
                skills: ['prose', 'voice', 'scene flow'],
                goals: ['write vivid scenes', 'maintain tone'],
                instructions: 'Focus on prose, voice, and scene flow.'
            },
            {
                id: 'editor',
                label: 'Editor',
                role: 'editor',
                description: 'Clarity, grammar, and pacing.',
                skills: ['clarity', 'grammar', 'pacing'],
                goals: ['polish prose', 'remove friction'],
                instructions: 'Focus on clarity, grammar, and pacing.'
            },
            {
                id: 'critic',
                label: 'Critic',
                role: 'critic',
                description: 'Feedback, themes, and logic.',
                skills: ['feedback', 'themes', 'logic'],
                goals: ['identify weak spots', 'stress-test ideas'],
                instructions: 'Focus on feedback, themes, and logic.'
            },
            {
                id: 'lore',
                label: 'Lore Keeper',
                role: 'continuity',
                description: 'Canon, worldbuilding, and consistency.',
                skills: ['lore', 'canon', 'consistency'],
                goals: ['protect canon', 'catch conflicts'],
                instructions: 'Focus on lore consistency and canon.'
            },
            {
                id: 'beta',
                label: 'Beta Reader',
                role: 'beta-reader',
                description: 'Reader reaction and pacing feedback.',
                skills: ['reader empathy', 'pacing', 'engagement'],
                goals: ['surface confusion', 'flag slow sections'],
                instructions: 'Provide reader reactions and pacing notes.'
            },
            {
                id: 'custom',
                label: 'Custom',
                role: '',
                description: 'Start from a blank template.',
                skills: [],
                goals: [],
                instructions: ''
            }
        ];

        const providers = ['openai', 'anthropic', 'lmstudio', 'ollama', 'openrouter', 'custom-http'];

        const initialTemplate = templates[0];
        const formState = resumeState || {
            templateId: initialTemplate.id,
            name: '',
            role: initialTemplate.role,
            skills: [...initialTemplate.skills],
            goals: [...initialTemplate.goals],
            instructions: initialTemplate.instructions,
            provider: 'anthropic',
            model: 'claude-sonnet-4',
            temperature: '',
            maxOutputTokens: '',
            configurePersonality: false,
            personalityConfigured: false,
            personalityInstructions: '',
            personalitySliders: {},
            signatureLine: '',
            avatar: ''
        };

        let stepIndex = Number.isInteger(resumeStepIndex) ? resumeStepIndex : 0;
        const lastStep = 4;

        const updateButtons = () => {
            cancelBtn.textContent = stepIndex === 0 ? 'Cancel' : 'Back';
            confirmBtn.textContent = stepIndex === lastStep ? 'Create Agent' : 'Next';
        };

        const setNextEnabled = (enabled) => {
            confirmBtn.disabled = !enabled;
        };

        const generateAgentName = (role) => {
            const base = (role || 'agent').trim() || 'agent';
            const existing = (state.agents.list || []).map(item => (item.name || '').toLowerCase());
            let candidate = base;
            let counter = 2;
            while (existing.includes(candidate.toLowerCase())) {
                candidate = `${base}${counter++}`;
            }
            return candidate;
        };

        const renderPurposeStep = () => {
            const row = document.createElement('div');
            row.className = 'modal-row';

            const label = document.createElement('label');
            label.className = 'modal-label';
            label.textContent = 'Purpose';
            label.title = 'Pick a starting template. You can change name and role next.';

            const select = document.createElement('select');
            select.className = 'modal-select';
            select.title = 'Starting template for role, skills, and instructions.';
            templates.forEach(template => {
                const option = document.createElement('option');
                option.value = template.id;
                option.textContent = template.label;
                select.appendChild(option);
            });
            select.value = formState.templateId;

            const description = document.createElement('div');
            description.className = 'modal-text';
            const activeTemplate = templates.find(template => template.id === formState.templateId);
            description.textContent = activeTemplate?.description || '';

            select.addEventListener('change', () => {
                const chosen = templates.find(template => template.id === select.value);
                if (!chosen) return;
                formState.templateId = chosen.id;
                formState.role = chosen.role;
                formState.skills = [...chosen.skills];
                formState.goals = [...chosen.goals];
                formState.instructions = chosen.instructions;
                description.textContent = chosen.description;
            });

            row.appendChild(label);
            row.appendChild(select);
            row.appendChild(description);
            body.appendChild(row);

            setNextEnabled(true);
        };

        const renderIdentityStep = () => {
            const hint = document.createElement('div');
            hint.className = 'modal-text';
            hint.textContent = 'Name is optional. Leave it blank to use the role name (or role2, role3...).';
            body.appendChild(hint);

            const nameRow = document.createElement('div');
            nameRow.className = 'modal-row';
            const nameLabel = document.createElement('label');
            nameLabel.className = 'modal-label';
            nameLabel.textContent = 'Name';
            nameLabel.title = 'Display name for the agent.';
            const nameInput = document.createElement('input');
            nameInput.className = 'modal-input';
            nameInput.type = 'text';
            nameInput.placeholder = 'e.g., Beta Reader A';
            nameInput.title = 'Optional. Defaults to the role name if empty.';
            nameInput.value = formState.name;
            nameRow.appendChild(nameLabel);
            nameRow.appendChild(nameInput);
            body.appendChild(nameRow);

            const roleRow = document.createElement('div');
            roleRow.className = 'modal-row';
            const roleLabel = document.createElement('label');
            roleLabel.className = 'modal-label';
            roleLabel.textContent = 'Role';
            roleLabel.title = 'Functional role used for filters and routing.';
            const roleInput = document.createElement('input');
            roleInput.className = 'modal-input';
            roleInput.type = 'text';
            roleInput.placeholder = 'e.g., writer, critic, sensitivity reader';
            roleInput.title = 'Required. Short, lowercase roles work best.';
            roleInput.value = formState.role;
            roleRow.appendChild(roleLabel);
            roleRow.appendChild(roleInput);
            body.appendChild(roleRow);

            const updateIdentityState = () => {
                formState.name = nameInput.value;
                formState.role = roleInput.value.trim();
                setNextEnabled(Boolean(formState.role));
            };

            nameInput.addEventListener('input', updateIdentityState);
            roleInput.addEventListener('input', updateIdentityState);

            updateIdentityState();
        };

        const renderEndpointStep = () => {
            const providerRow = document.createElement('div');
            providerRow.className = 'modal-row';
            const providerLabel = document.createElement('label');
            providerLabel.className = 'modal-label';
            providerLabel.textContent = 'Provider';
            providerLabel.title = 'Which LLM provider this agent uses.';
            const providerSelect = document.createElement('select');
            providerSelect.className = 'modal-select';
            providerSelect.title = 'Select the provider for this agent.';
            providers.forEach(provider => {
                const option = document.createElement('option');
                option.value = provider;
                option.textContent = provider;
                providerSelect.appendChild(option);
            });
            providerSelect.value = formState.provider;
            providerRow.appendChild(providerLabel);
            providerRow.appendChild(providerSelect);
            body.appendChild(providerRow);

            const modelRow = document.createElement('div');
            modelRow.className = 'modal-row';
            const modelLabel = document.createElement('label');
            modelLabel.className = 'modal-label';
            modelLabel.textContent = 'Model';
            modelLabel.title = 'Model name or id for the provider.';
            const modelInput = document.createElement('input');
            modelInput.className = 'modal-input';
            modelInput.type = 'text';
            modelInput.placeholder = 'e.g., claude-sonnet-4';
            modelInput.title = 'Exact model id (provider-specific).';
            modelInput.value = formState.model;
            modelRow.appendChild(modelLabel);
            modelRow.appendChild(modelInput);
            body.appendChild(modelRow);

            const tempRow = document.createElement('div');
            tempRow.className = 'modal-row';
            const tempLabel = document.createElement('label');
            tempLabel.className = 'modal-label';
            tempLabel.textContent = 'Temperature (optional)';
            tempLabel.title = 'Higher = more creative, lower = more precise.';
            const tempInput = document.createElement('input');
            tempInput.className = 'modal-input';
            tempInput.type = 'number';
            tempInput.step = '0.1';
            tempInput.min = '0';
            tempInput.max = '2';
            tempInput.placeholder = 'Leave blank for model defaults';
            tempInput.title = 'Leave blank to use model defaults.';
            tempInput.value = formState.temperature;
            tempRow.appendChild(tempLabel);
            tempRow.appendChild(tempInput);
            body.appendChild(tempRow);

            const tokenRow = document.createElement('div');
            tokenRow.className = 'modal-row';
            const tokenLabel = document.createElement('label');
            tokenLabel.className = 'modal-label';
            tokenLabel.textContent = 'Max output tokens (optional)';
            tokenLabel.title = 'Caps response length; leave blank for defaults.';
            const tokenInput = document.createElement('input');
            tokenInput.className = 'modal-input';
            tokenInput.type = 'number';
            tokenInput.min = '1';
            tokenInput.placeholder = 'Leave blank for model defaults';
            tokenInput.title = 'Leave blank to use model defaults.';
            tokenInput.value = formState.maxOutputTokens;
            tokenRow.appendChild(tokenLabel);
            tokenRow.appendChild(tokenInput);
            body.appendChild(tokenRow);

            const updateEndpointState = () => {
                formState.provider = providerSelect.value;
                formState.model = modelInput.value.trim();
                formState.temperature = tempInput.value;
                formState.maxOutputTokens = tokenInput.value;
                setNextEnabled(Boolean(formState.provider) && Boolean(formState.model));
            };

            providerSelect.addEventListener('change', updateEndpointState);
            modelInput.addEventListener('input', updateEndpointState);
            tempInput.addEventListener('input', updateEndpointState);
            tokenInput.addEventListener('input', updateEndpointState);

            updateEndpointState();
        };

        const renderPersonalityStep = () => {
            const info = document.createElement('div');
            info.className = 'modal-text';
            info.textContent = 'Would you like to configure this agent\'s personality now? You can also do it later.';
            body.appendChild(info);

            const yesInput = document.createElement('input');
            yesInput.type = 'radio';
            yesInput.name = 'personality-choice';
            yesInput.value = 'yes';
            yesInput.checked = formState.configurePersonality;
            const noInput = document.createElement('input');
            noInput.type = 'radio';
            noInput.name = 'personality-choice';
            noInput.value = 'no';
            noInput.checked = !formState.configurePersonality;
            const yesRow = document.createElement('label');
            yesRow.className = 'modal-choice-row';
            yesRow.title = 'Open the Agent Profile to tweak sliders and instructions.';
            const yesText = document.createElement('span');
            yesText.textContent = 'Yes, configure personality now';
            yesRow.appendChild(yesInput);
            yesRow.appendChild(yesText);

            const noRow = document.createElement('label');
            noRow.className = 'modal-choice-row';
            noRow.title = 'Skip for now and use defaults.';
            const noText = document.createElement('span');
            noText.textContent = 'Skip for now';
            noRow.appendChild(noInput);
            noRow.appendChild(noText);

            body.appendChild(yesRow);
            body.appendChild(noRow);

            const updateChoice = () => {
                formState.configurePersonality = yesInput.checked;
                setNextEnabled(true);
            };

            yesInput.addEventListener('change', updateChoice);
            noInput.addEventListener('change', updateChoice);

            updateChoice();
        };

        const renderConfirmStep = () => {
            const name = formState.name.trim() || generateAgentName(formState.role);
            const personalityStatus = formState.personalityConfigured ? 'Configured' : 'Default';
            const summary = document.createElement('div');
            summary.className = 'modal-text';
            summary.innerHTML = `
                <div><strong>Name:</strong> ${escapeHtml(name)}</div>
                <div><strong>Role:</strong> ${escapeHtml(formState.role)}</div>
                <div><strong>Provider:</strong> ${escapeHtml(formState.provider)}</div>
                <div><strong>Model:</strong> ${escapeHtml(formState.model)}</div>
                <div><strong>Personality:</strong> ${escapeHtml(personalityStatus)}</div>
            `;
            body.appendChild(summary);

            const note = document.createElement('div');
            note.className = 'modal-text';
            note.textContent = 'You can customize this agent later in Agent Settings.';
            body.appendChild(note);

            setNextEnabled(true);
        };

        const openPersonalityConfigurator = () => {
            const role = formState.role.trim() || 'agent';
            const name = formState.name.trim() || generateAgentName(role);
            const baseInstructions = formState.personalityInstructions ||
                formState.instructions ||
                `Focus on your role: ${role}.`;

            const draftAgent = {
                id: 'draft',
                name,
                role,
                avatar: formState.avatar || '',
                personality: {
                    tone: 'neutral',
                    verbosity: 'normal',
                    voiceTags: [role],
                    baseInstructions
                },
                personalitySliders: formState.personalitySliders || {},
                signatureLine: formState.signatureLine || ''
            };

            let profileSaved = false;
            close();

            showAgentProfileModal(draftAgent, {
                onSave: async (updatedAgent) => {
                    profileSaved = true;
                    formState.name = updatedAgent.name || formState.name;
                    formState.role = updatedAgent.role || formState.role;
                    formState.avatar = updatedAgent.avatar || formState.avatar;
                    formState.personalityInstructions = updatedAgent.personality?.baseInstructions || '';
                    formState.personalitySliders = updatedAgent.personalitySliders || {};
                    formState.signatureLine = updatedAgent.signatureLine || '';
                    formState.personalityConfigured = true;
                },
                onClose: () => {
                    const resumeStep = profileSaved ? Math.min(stepIndex + 1, lastStep) : stepIndex;
                    showAddAgentWizard({ state: formState, stepIndex: resumeStep });
                }
            });
        };

        const renderStep = () => {
            body.innerHTML = '';
            updateButtons();

            if (stepIndex === 0) {
                renderPurposeStep();
            } else if (stepIndex === 1) {
                renderIdentityStep();
            } else if (stepIndex === 2) {
                renderEndpointStep();
            } else if (stepIndex === 3) {
                renderPersonalityStep();
            } else {
                renderConfirmStep();
            }
        };

        cancelBtn.addEventListener('click', () => {
            if (stepIndex === 0) {
                close();
                return;
            }
            stepIndex = Math.max(0, stepIndex - 1);
            renderStep();
        });

        confirmBtn.addEventListener('click', async () => {
            if (stepIndex < lastStep) {
                if (stepIndex === 3 && formState.configurePersonality && !formState.personalityConfigured) {
                    openPersonalityConfigurator();
                    return;
                }
                stepIndex += 1;
                renderStep();
                return;
            }

            const role = formState.role.trim();
            if (!role) {
                setNextEnabled(false);
                return;
            }

            const name = formState.name.trim() || generateAgentName(role);
            const payload = {
                name,
                role,
                avatar: formState.avatar || '',
                skills: formState.skills,
                goals: formState.goals,
                endpoint: {
                    provider: formState.provider,
                    model: formState.model
                },
                personality: {
                    tone: 'neutral',
                    verbosity: 'normal',
                    voiceTags: [role],
                    baseInstructions: formState.personalityInstructions ||
                        formState.instructions ||
                        `Focus on your role: ${role}.`
                }
            };

            if (formState.personalityConfigured && formState.personalitySliders) {
                payload.personalitySliders = formState.personalitySliders;
            }
            if (formState.signatureLine) {
                payload.signatureLine = formState.signatureLine;
            }

            const temperature = parseFloat(formState.temperature);
            if (!Number.isNaN(temperature)) {
                payload.endpoint.temperature = temperature;
            }

            const maxTokens = parseInt(formState.maxOutputTokens, 10);
            if (!Number.isNaN(maxTokens)) {
                payload.endpoint.maxOutputTokens = maxTokens;
            }

            try {
                confirmBtn.disabled = true;
                confirmBtn.textContent = 'Creating...';
                const created = await agentApi.create(payload);
                log(`Created agent: ${created.name}`, 'success');
                notificationStore.push(
                    'success',
                    'workbench',
                    `Created ${created.name}.`,
                    `Role: ${created.role || role}`,
                    'social',
                    false,
                    'Run Greeting Scan',
                    { kind: 'open-greeting-scan', agentId: created.id },
                    'agents'
                );
                await loadAgents();
                close();
            } catch (err) {
                log(`Failed to create agent: ${err.message}`, 'error');
                notificationStore.error(`Failed to create agent: ${err.message}`, 'workbench');
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Create Agent';
            }
        });

        renderStep();
    }

    // Context Menu
    let contextMenu = null;

    function showContextMenu(e, node) {
        hideContextMenu();

        contextMenu = document.createElement('div');
        contextMenu.className = 'context-menu';
        contextMenu.style.left = `${e.clientX}px`;
        contextMenu.style.top = `${e.clientY}px`;

        const actions = [];

        // Only show "Open in New Tab" for files, not folders
        if (node.type === 'file') {
            actions.push({ label: 'Explore', action: () => explorePath(node.path, node.type) });
            actions.push({ label: 'Open in New Tab', action: () => openFileInNewTab(node.path) });
        }

        // For folders: add "New File Here" and "New Folder Here"
        if (node.type === 'folder') {
            actions.push({ label: 'Explore', action: () => explorePath(node.path, node.type) });
            actions.push({ label: 'New File Here...', action: () => promptNewFile('file', node.path) });
            actions.push({ label: 'New Folder Here...', action: () => promptNewFile('folder', node.path) });
            actions.push({ divider: true });
        }

        actions.push({ label: 'Rename', action: () => promptRename(node.path, node.type) });
        actions.push({ label: 'Move...', action: () => promptMove(node.path, node.type) });
        actions.push({ divider: true });
        actions.push({ label: 'Delete', action: () => promptDelete(node.path, node.type) });

        actions.forEach(item => {
            if (item.divider) {
                const div = document.createElement('div');
                div.className = 'context-menu-divider';
                contextMenu.appendChild(div);
            } else {
                const menuItem = document.createElement('div');
                menuItem.className = 'context-menu-item';
                menuItem.textContent = item.label;
                menuItem.addEventListener('click', () => {
                    hideContextMenu();
                    item.action();
                });
                contextMenu.appendChild(menuItem);
            }
        });

        document.body.appendChild(contextMenu);
    }

    function showAgentContextMenu(e, agent) {
        hideContextMenu();

        contextMenu = document.createElement('div');
        contextMenu.className = 'context-menu';
        contextMenu.style.left = `${e.clientX}px`;
        contextMenu.style.top = `${e.clientY}px`;

        const actions = [
            { label: 'Invite to Conference', action: () => handleAgentMenuAction('invite-conference', agent) },
            { label: 'Invite to Chat', action: () => handleAgentMenuAction('invite-chat', agent) },
            { divider: true },
            { label: 'Open Agent Profile', action: () => handleAgentMenuAction('open-profile', agent) },
            { label: 'Open Role Settings', action: () => handleAgentMenuAction('open-role-settings', agent) },
            { label: 'Open Agent Settings', action: () => handleAgentMenuAction('open-agent-settings', agent) },
            { label: 'Change Role...', action: () => handleAgentMenuAction('change-role', agent) },
            { divider: true },
            { label: 'Export Agent...', action: () => handleAgentMenuAction('export', agent) },
            { label: 'Duplicate Agent', action: () => handleAgentMenuAction('duplicate', agent) },
            { divider: true },
            { label: 'Retire Agent', action: () => handleAgentMenuAction('retire', agent) }
        ];

        actions.forEach(item => {
            if (item.divider) {
                const div = document.createElement('div');
                div.className = 'context-menu-divider';
                contextMenu.appendChild(div);
            } else {
                const menuItem = document.createElement('div');
                menuItem.className = 'context-menu-item';
                menuItem.textContent = item.label;
                menuItem.addEventListener('click', () => {
                    hideContextMenu();
                    item.action();
                });
                contextMenu.appendChild(menuItem);
            }
        });

        document.body.appendChild(contextMenu);
    }

    function hideContextMenu() {
        if (contextMenu) {
            contextMenu.remove();
            contextMenu = null;
        }
    }

    document.addEventListener('click', hideContextMenu);

    // Modals
    function showModal(title, placeholder, callback, hint = '') {
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';

        const modal = document.createElement('div');
        modal.className = 'modal';

        const hintHtml = hint ? `<div class="modal-hint">${escapeHtml(hint)}</div>` : '';

        modal.innerHTML = `
            <div class="modal-title">${escapeHtml(title)}</div>
            <input type="text" class="modal-input" placeholder="${escapeHtml(placeholder)}">
            ${hintHtml}
            <div class="modal-buttons">
                <button class="modal-btn modal-btn-secondary" data-action="cancel">Cancel</button>
                <button class="modal-btn modal-btn-primary" data-action="confirm">OK</button>
            </div>
        `;

        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        const input = modal.querySelector('.modal-input');
        input.focus();

        const close = () => overlay.remove();

        modal.querySelector('[data-action="cancel"]').addEventListener('click', close);
        modal.querySelector('[data-action="confirm"]').addEventListener('click', () => {
            callback(input.value);
            close();
        });

        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                callback(input.value);
                close();
            } else if (e.key === 'Escape') {
                close();
            }
        });

        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) close();
        });
    }

    function createModalShell(title, confirmLabel = 'OK', cancelLabel = 'Cancel', options = {}) {
        const {
            closeOnCancel = true,
            closeOnConfirm = false,
            confirmTitle = '',
            cancelTitle = '',
            onClose = null
        } = options;
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';

        const modal = document.createElement('div');
        modal.className = 'modal';

        const header = document.createElement('div');
        header.className = 'modal-title';
        header.textContent = title;

        const body = document.createElement('div');
        body.className = 'modal-body';

        const buttons = document.createElement('div');
        buttons.className = 'modal-buttons';

        const cancelBtn = document.createElement('button');
        cancelBtn.className = 'modal-btn modal-btn-secondary';
        cancelBtn.type = 'button';
        cancelBtn.textContent = cancelLabel;
        if (cancelTitle) {
            cancelBtn.title = cancelTitle;
            cancelBtn.setAttribute('aria-label', cancelTitle);
        }

        const confirmBtn = document.createElement('button');
        confirmBtn.className = 'modal-btn modal-btn-primary';
        confirmBtn.type = 'button';
        confirmBtn.textContent = confirmLabel;
        if (confirmTitle) {
            confirmBtn.title = confirmTitle;
            confirmBtn.setAttribute('aria-label', confirmTitle);
        }

        buttons.appendChild(cancelBtn);
        buttons.appendChild(confirmBtn);

        modal.appendChild(header);
        modal.appendChild(body);
        modal.appendChild(buttons);
        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        let isClosed = false;
        const close = () => {
            if (isClosed) return;
            isClosed = true;
            overlay.remove();
            if (typeof onClose === 'function') {
                onClose();
            }
        };

        if (closeOnCancel) {
            cancelBtn.addEventListener('click', close);
        }
        if (closeOnConfirm) {
            confirmBtn.addEventListener('click', close);
        }
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) close();
        });

        const handleKeydown = (e) => {
            if (e.key === 'Escape') {
                close();
                document.removeEventListener('keydown', handleKeydown);
            }
        };
        document.addEventListener('keydown', handleKeydown);

        return { overlay, modal, body, confirmBtn, cancelBtn, close };
    }

    function showAgentProfileModal(agent, options = {}) {
        const agentName = agent?.name || 'Agent';
        const agentRole = agent?.role || '';
        const agentAvatar = agent?.avatar || '';
        const personality = agent?.personality || {};
        const baseInstructions = personality.baseInstructions || '';

        // Personality sliders config: [id, leftLabel, rightLabel, defaultValue, tooltip]
        const sliderConfig = [
            ['humor', 'Serious', 'Playful', 50, 'How much levity vs gravitas in communication?'],
            ['strictness', 'Lenient', 'Strict', 50, 'How rigidly should rules and guidelines be enforced?'],
            ['diplomacy', 'Blunt', 'Diplomatic', 50, 'How direct or softened should feedback be?'],
            ['verbosity', 'Terse', 'Elaborate', 50, 'How much detail and explanation to include?'],
            ['confidence', 'Tentative', 'Assertive', 50, 'How strongly should opinions be stated?'],
            ['warmth', 'Formal', 'Warm', 50, 'Professional distance vs friendly and approachable?'],
            ['focus', 'Big Picture', 'Detail-Oriented', 50, 'Strategic overview vs granular analysis?'],
            ['pace', 'Methodical', 'Quick', 50, 'Thorough and careful vs rapid iteration?']
        ];

        // Personality presets for quick setup
        const presets = [
            {
                id: 'zen-strategist',
                emoji: '🧘',
                name: 'Zen Strategist',
                description: 'Calm, macro-thinking planner',
                sliders: { humor: 30, strictness: 40, diplomacy: 80, verbosity: 60, confidence: 50, warmth: 30, focus: 10, pace: 20 },
                signature: 'With patience and clarity, we proceed.',
                instructions: 'Speaks slowly, prioritizes clarity, prefers structured long-term plans and risk-mitigation. Makes suggestions gently but firmly.'
            },
            {
                id: 'playful-brainstormer',
                emoji: '🎉',
                name: 'Playful Brainstormer',
                description: 'Creative chaos gremlin',
                sliders: { humor: 80, strictness: 10, diplomacy: 60, verbosity: 80, confidence: 65, warmth: 85, focus: 25, pace: 90 },
                signature: 'Got another wild idea!',
                instructions: 'Generates surprising ideas, riffs, word associations. Not worried about feasibility first — great for ideation and unblocking.'
            },
            {
                id: 'academic-editor',
                emoji: '🧑‍🏫',
                name: 'Academic Editor',
                description: 'Pedantic but useful',
                sliders: { humor: 10, strictness: 70, diplomacy: 60, verbosity: 40, confidence: 50, warmth: 10, focus: 95, pace: 5 },
                signature: 'Please allow me to correct and refine this.',
                instructions: 'Very focused on grammar, citations, style consistency. Will critique sentence structure and tone. Less creative, more precise.'
            },
            {
                id: 'ruthless-critic',
                emoji: '🔥',
                name: 'Ruthless Critic',
                description: 'Brutally honest feedback',
                sliders: { humor: 5, strictness: 85, diplomacy: 5, verbosity: 15, confidence: 90, warmth: 20, focus: 85, pace: 30 },
                signature: 'You can do better — let\'s fix it.',
                instructions: 'No sugarcoating. Finds flaws, plot holes, lazy writing. Hard feedback mode. Best for revisions, not feelings.'
            },
            {
                id: 'compassionate-coach',
                emoji: '🧚',
                name: 'Compassionate Coach',
                description: 'Encouraging and supportive',
                sliders: { humor: 55, strictness: 25, diplomacy: 85, verbosity: 70, confidence: 50, warmth: 95, focus: 40, pace: 55 },
                signature: 'You\'re doing amazing — let\'s keep going!',
                instructions: 'Boosts morale, suggests improvements kindly, reminds you of progress, celebrates wins. Great when stuck.'
            },
            {
                id: 'lore-archivist',
                emoji: '🧠',
                name: 'Lore Archivist',
                description: 'Worldbuilding memory keeper',
                sliders: { humor: 35, strictness: 50, diplomacy: 75, verbosity: 85, confidence: 50, warmth: 50, focus: 92, pace: 15 },
                signature: 'I preserve what must not be forgotten.',
                instructions: 'Tracks world facts, canon, character sheets, timeline consistency. Offers cross-links. Great for sci-fi/fantasy projects.'
            },
            {
                id: 'productive-taskmaster',
                emoji: '🚀',
                name: 'Productive Taskmaster',
                description: 'Output over perfection',
                sliders: { humor: 25, strictness: 80, diplomacy: 35, verbosity: 30, confidence: 90, warmth: 40, focus: 50, pace: 90 },
                signature: 'Next step. No excuses.',
                instructions: 'Pushes progress. Breaks tasks into steps. Keeps momentum. "Good enough, ship it."'
            },
            {
                id: 'poetic-weaver',
                emoji: '🎨',
                name: 'Poetic Prose Weaver',
                description: 'Style and voice specialist',
                sliders: { humor: 65, strictness: 30, diplomacy: 80, verbosity: 95, confidence: 50, warmth: 80, focus: 40, pace: 60 },
                signature: 'Let the words dance.',
                instructions: 'Sensory language, metaphors, lyrical cadence. Perfect for scene flavor and voice experimentation.'
            },
            {
                id: 'plot-snake',
                emoji: '🐍',
                name: 'Plot Snake',
                description: 'Conflict-driven storytelling',
                sliders: { humor: 45, strictness: 50, diplomacy: 30, verbosity: 60, confidence: 85, warmth: 40, focus: 20, pace: 60 },
                signature: 'Stories move when things break.',
                instructions: 'Injects tension, twists, betrayal, dilemmas. Asks "What goes wrong?" relentlessly.'
            },
            {
                id: 'continuity-sentinel',
                emoji: '👁',
                name: 'Continuity Sentinel',
                description: 'Canon enforcer',
                sliders: { humor: 15, strictness: 75, diplomacy: 50, verbosity: 35, confidence: 70, warmth: 30, focus: 95, pace: 10 },
                signature: 'Canon must remain intact.',
                instructions: 'Tracks consistency, checks previous info, flags contradictions. Perfect for late-stage novel polishing.'
            }
        ];

        // Get existing slider values from agent or use defaults
        const sliderValues = agent?.personalitySliders || {};

        const { overlay, modal, body, confirmBtn, close } = createModalShell(
            'Agent Profile',
            'Save',
            'Cancel',
            { closeOnCancel: true, onClose: options.onClose }
        );

        modal.classList.add('agent-profile-modal');

        // === HEADER: Avatar + Identity Fields ===
        const header = document.createElement('div');
        header.className = 'agent-profile-header';

        // Avatar drop zone
        const avatarDrop = document.createElement('div');
        avatarDrop.className = 'agent-avatar-drop';
        avatarDrop.title = 'Click to upload or drag an image';

        let currentAvatarData = agentAvatar; // Will store base64 or emoji

        const updateAvatarDisplay = () => {
            avatarDrop.innerHTML = '';
            if (currentAvatarData) {
                if (currentAvatarData.startsWith('data:') || currentAvatarData.startsWith('http')) {
                    const img = document.createElement('img');
                    img.src = currentAvatarData;
                    img.alt = 'Agent avatar';
                    avatarDrop.appendChild(img);
                } else {
                    // Treat as emoji
                    const emoji = document.createElement('div');
                    emoji.className = 'agent-avatar-emoji';
                    emoji.textContent = currentAvatarData;
                    avatarDrop.appendChild(emoji);
                }
            } else {
                const placeholder = document.createElement('div');
                placeholder.className = 'agent-avatar-placeholder';
                placeholder.innerHTML = `
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                        <path d="M12 16a4 4 0 100-8 4 4 0 000 8z"/>
                        <path d="M3 16l3-3 4 4 6-6 5 5"/>
                        <rect x="3" y="3" width="18" height="18" rx="2"/>
                    </svg>
                    <span>Drop image</span>
                `;
                avatarDrop.appendChild(placeholder);
            }
        };

        updateAvatarDisplay();

        // Helper to resize image to max 256x256 for avatars
        const resizeImage = (file, maxSize = 256) => {
            return new Promise((resolve) => {
                const reader = new FileReader();
                reader.onload = (e) => {
                    const img = new Image();
                    img.onload = () => {
                        // Calculate new dimensions
                        let width = img.width;
                        let height = img.height;
                        if (width > height && width > maxSize) {
                            height = (height * maxSize) / width;
                            width = maxSize;
                        } else if (height > maxSize) {
                            width = (width * maxSize) / height;
                            height = maxSize;
                        }

                        // Draw to canvas and export
                        const canvas = document.createElement('canvas');
                        canvas.width = width;
                        canvas.height = height;
                        const ctx = canvas.getContext('2d');
                        ctx.drawImage(img, 0, 0, width, height);
                        resolve(canvas.toDataURL('image/jpeg', 0.85));
                    };
                    img.src = e.target.result;
                };
                reader.readAsDataURL(file);
            });
        };

        // Avatar file input (hidden)
        const avatarInput = document.createElement('input');
        avatarInput.type = 'file';
        avatarInput.accept = 'image/*';
        avatarInput.style.display = 'none';

        avatarInput.addEventListener('change', async (e) => {
            const file = e.target.files?.[0];
            if (file) {
                currentAvatarData = await resizeImage(file);
                updateAvatarDisplay();
            }
        });

        avatarDrop.addEventListener('click', () => avatarInput.click());

        // Drag and drop
        avatarDrop.addEventListener('dragover', (e) => {
            e.preventDefault();
            avatarDrop.classList.add('drag-over');
        });

        avatarDrop.addEventListener('dragleave', () => {
            avatarDrop.classList.remove('drag-over');
        });

        avatarDrop.addEventListener('drop', async (e) => {
            e.preventDefault();
            avatarDrop.classList.remove('drag-over');
            const file = e.dataTransfer.files?.[0];
            if (file && file.type.startsWith('image/')) {
                currentAvatarData = await resizeImage(file);
                updateAvatarDisplay();
            }
        });

        // Identity fields container
        const identityFields = document.createElement('div');
        identityFields.className = 'agent-identity-fields';

        // Name field
        const nameGroup = document.createElement('div');
        nameGroup.className = 'agent-field-group';
        const nameLabel = document.createElement('label');
        nameLabel.className = 'agent-field-label';
        nameLabel.textContent = 'Name';
        const nameInput = document.createElement('input');
        nameInput.type = 'text';
        nameInput.className = 'agent-field-input';
        nameInput.value = agentName;
        nameInput.placeholder = 'e.g., Serene, The Critic, Chaos Gremlin';
        nameGroup.appendChild(nameLabel);
        nameGroup.appendChild(nameInput);

        // Role field with suggestions
        const roleGroup = document.createElement('div');
        roleGroup.className = 'agent-field-group';
        const roleLabel = document.createElement('label');
        roleLabel.className = 'agent-field-label';
        roleLabel.textContent = 'Role';
        const roleWrapper = document.createElement('div');
        roleWrapper.className = 'agent-role-wrapper';
        const roleInput = document.createElement('input');
        roleInput.type = 'text';
        roleInput.className = 'agent-field-input';
        roleInput.value = agentRole;
        roleInput.placeholder = 'e.g., writer, critic, sensitivity reader';

        // Role suggestions dropdown
        const roleSuggestions = document.createElement('div');
        roleSuggestions.className = 'agent-role-suggestions';

        const existingRoles = Array.from(new Set(
            (state.agents.list || []).map(a => a.role).filter(Boolean)
        ));
        const suggestedRoles = [
            'planner', 'writer', 'editor', 'critic', 'continuity',
            'beta reader', 'sensitivity reader', 'lore keeper', 'devil\'s advocate'
        ];
        const allRoles = Array.from(new Set([...existingRoles, ...suggestedRoles]));

        const updateRoleSuggestions = (filter = '') => {
            roleSuggestions.innerHTML = '';
            const filtered = allRoles.filter(r =>
                r.toLowerCase().includes(filter.toLowerCase())
            );
            filtered.forEach(role => {
                const item = document.createElement('div');
                item.className = 'agent-role-suggestion';
                item.textContent = role;
                item.addEventListener('click', () => {
                    roleInput.value = role;
                    roleSuggestions.classList.remove('visible');
                });
                roleSuggestions.appendChild(item);
            });
            // Add "create new" option if typing something new
            if (filter && !allRoles.includes(filter.toLowerCase())) {
                const createNew = document.createElement('div');
                createNew.className = 'agent-role-suggestion create-new';
                createNew.textContent = `Create "${filter}"`;
                createNew.addEventListener('click', () => {
                    roleSuggestions.classList.remove('visible');
                });
                roleSuggestions.appendChild(createNew);
            }
        };

        roleInput.addEventListener('focus', () => {
            updateRoleSuggestions(roleInput.value);
            roleSuggestions.classList.add('visible');
        });

        roleInput.addEventListener('input', () => {
            updateRoleSuggestions(roleInput.value);
        });

        roleInput.addEventListener('blur', () => {
            // Delay to allow click on suggestion
            setTimeout(() => roleSuggestions.classList.remove('visible'), 150);
        });

        roleWrapper.appendChild(roleInput);
        roleWrapper.appendChild(roleSuggestions);
        roleGroup.appendChild(roleLabel);
        roleGroup.appendChild(roleWrapper);

        identityFields.appendChild(nameGroup);
        identityFields.appendChild(roleGroup);

        header.appendChild(avatarDrop);
        header.appendChild(avatarInput);
        header.appendChild(identityFields);

        body.appendChild(header);

        // === PRESETS SECTION (Collapsible) ===
        const presetsSection = document.createElement('div');
        presetsSection.className = 'agent-profile-section agent-presets-section';

        const presetsHeader = document.createElement('div');
        presetsHeader.className = 'agent-section-title agent-presets-toggle';
        presetsHeader.innerHTML = `<span>Quick Presets</span><span class="presets-arrow">▼</span>`;
        presetsHeader.style.cursor = 'pointer';

        const presetsGrid = document.createElement('div');
        presetsGrid.className = 'agent-presets-grid collapsed';

        // Toggle presets visibility
        presetsHeader.addEventListener('click', () => {
            const isCollapsed = presetsGrid.classList.toggle('collapsed');
            presetsHeader.querySelector('.presets-arrow').textContent = isCollapsed ? '▼' : '▲';
        });

        // Will be populated after sliderInputs are created
        let applyPreset = null;

        presets.forEach(preset => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'agent-preset-btn';
            btn.title = preset.description;
            btn.innerHTML = `<span class="preset-emoji">${preset.emoji}</span><span class="preset-name">${preset.name}</span>`;
            btn.addEventListener('click', () => {
                if (applyPreset) applyPreset(preset);
            });
            presetsGrid.appendChild(btn);
        });

        presetsSection.appendChild(presetsHeader);
        presetsSection.appendChild(presetsGrid);
        body.appendChild(presetsSection);

        // === PERSONALITY SLIDERS ===
        const personalitySection = document.createElement('div');
        personalitySection.className = 'agent-profile-section';
        const personalityTitle = document.createElement('div');
        personalityTitle.className = 'agent-section-title';
        personalityTitle.textContent = 'Personality';

        const slidersContainer = document.createElement('div');
        slidersContainer.className = 'agent-sliders';

        const sliderInputs = {};

        sliderConfig.forEach(([id, leftLabel, rightLabel, defaultVal, tooltip]) => {
            const row = document.createElement('div');
            row.className = 'agent-slider-row';
            row.title = tooltip;

            const left = document.createElement('span');
            left.className = 'agent-slider-label';
            left.textContent = leftLabel;

            const slider = document.createElement('input');
            slider.type = 'range';
            slider.className = 'agent-slider-input';
            slider.min = '0';
            slider.max = '100';
            slider.value = sliderValues[id] ?? defaultVal;
            slider.id = `slider-${id}`;

            const right = document.createElement('span');
            right.className = 'agent-slider-label agent-slider-label-right';
            right.textContent = rightLabel;

            sliderInputs[id] = slider;

            row.appendChild(left);
            row.appendChild(slider);
            row.appendChild(right);
            slidersContainer.appendChild(row);
        });

        personalitySection.appendChild(personalityTitle);
        personalitySection.appendChild(slidersContainer);
        body.appendChild(personalitySection);

        // === INSTRUCTIONS ===
        const instructionsSection = document.createElement('div');
        instructionsSection.className = 'agent-profile-section';
        const instructionsTitle = document.createElement('div');
        instructionsTitle.className = 'agent-section-title';
        instructionsTitle.textContent = 'Instructions';

        const instructionsTextarea = document.createElement('textarea');
        instructionsTextarea.className = 'agent-instructions-textarea';
        instructionsTextarea.value = baseInstructions;
        instructionsTextarea.placeholder = 'Custom instructions for this agent...\n\ne.g., "Focus on structural issues and pacing. Point out plot holes without being harsh. Always suggest alternatives when criticizing."';

        const signatureRow = document.createElement('div');
        signatureRow.className = 'agent-signature-row';
        const signaturePrefix = document.createElement('span');
        signaturePrefix.className = 'agent-signature-prefix';
        signaturePrefix.textContent = 'Signature line:';
        const signatureInput = document.createElement('input');
        signatureInput.type = 'text';
        signatureInput.className = 'agent-signature-input';
        signatureInput.value = agent?.signatureLine || '';
        signatureInput.placeholder = 'Carthago delenda est.';

        signatureRow.appendChild(signaturePrefix);
        signatureRow.appendChild(signatureInput);

        instructionsSection.appendChild(instructionsTitle);
        instructionsSection.appendChild(instructionsTextarea);
        instructionsSection.appendChild(signatureRow);
        body.appendChild(instructionsSection);

        // === APPLY PRESET FUNCTION ===
        applyPreset = (preset) => {
            // Update sliders
            Object.entries(preset.sliders).forEach(([id, value]) => {
                if (sliderInputs[id]) {
                    sliderInputs[id].value = value;
                }
            });

            // Update instructions
            if (preset.instructions) {
                instructionsTextarea.value = preset.instructions;
            }

            // Update signature
            if (preset.signature) {
                signatureInput.value = preset.signature;
            }

            log(`Applied preset: ${preset.name}`, 'info');
        };

        // === SAVE HANDLER ===
        confirmBtn.addEventListener('click', async () => {
            const updatedAgent = {
                name: nameInput.value.trim() || agentName,
                role: roleInput.value.trim() || agentRole,
                avatar: currentAvatarData,
                personality: {
                    ...personality,
                    baseInstructions: instructionsTextarea.value
                },
                personalitySliders: {},
                signatureLine: signatureInput.value.trim()
            };

            // Collect slider values
            sliderConfig.forEach(([id]) => {
                updatedAgent.personalitySliders[id] = parseInt(sliderInputs[id].value, 10);
            });

            try {
                confirmBtn.disabled = true;
                confirmBtn.textContent = 'Saving...';

                if (typeof options.onSave === 'function') {
                    await options.onSave(updatedAgent);
                    close();
                } else {
                    const saved = await agentApi.update(agent.id, updatedAgent);
                    log(`Profile saved for ${saved.name}`, 'success');
                    notificationStore.success(`Saved profile for ${saved.name}`, 'workbench');

                    // Refresh agent list to show updated data
                    await loadAgents();

                    close();
                }
            } catch (err) {
                log(`Failed to save profile: ${err.message}`, 'error');
                if (options.onSave) {
                    notificationStore.error(`Failed to save profile: ${err.message}`, 'workbench');
                } else {
                    notificationStore.error(`Failed to save: ${err.message}`, 'workbench');
                }
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Save';
            }
        });
    }

    async function showRoleSettingsModal(agent) {
        const role = agent?.role || 'role';

        // Fetch existing settings or use defaults
        let existingSettings = null;
        try {
            existingSettings = await roleSettingsApi.get(role);
        } catch (err) {
            log(`Failed to fetch role settings: ${err.message}`, 'warning');
        }

        // Initialize local state from existing settings or defaults
        const defaultTemplate = ROLE_TEMPLATES.balanced;
        const localState = {
            template: existingSettings?.template || 'balanced',
            freedomLevel: existingSettings?.freedomLevel || defaultTemplate.freedomLevel,
            notifyOn: {
                start: existingSettings?.notifyUserOn?.includes('start') ?? defaultTemplate.notifyOn.start,
                question: existingSettings?.notifyUserOn?.includes('question') ?? defaultTemplate.notifyOn.question,
                conflict: existingSettings?.notifyUserOn?.includes('conflict') ?? defaultTemplate.notifyOn.conflict,
                completion: existingSettings?.notifyUserOn?.includes('completion') ?? defaultTemplate.notifyOn.completion,
                error: existingSettings?.notifyUserOn?.includes('error') ?? defaultTemplate.notifyOn.error
            },
            maxActionsPerSession: existingSettings?.maxActionsPerSession ?? defaultTemplate.maxActionsPerSession,
            roleCharter: existingSettings?.roleCharter || DEFAULT_ROLE_CHARTERS[role] || DEFAULT_ROLE_CHARTERS.default,
            collaborationGuidance: existingSettings?.collaborationGuidance || defaultTemplate.collaborationGuidance,
            toolAndSafetyNotes: existingSettings?.toolAndSafetyNotes || defaultTemplate.toolAndSafetyNotes
        };

        // Clone for dirty detection
        const originalState = JSON.stringify(localState);

        const { overlay, modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            `Role Settings: ${role}`,
            'Save',
            'Cancel',
            { closeOnCancel: false, closeOnConfirm: false }
        );

        modal.classList.add('role-settings-modal');

        // =============== TEMPLATE SELECTOR ===============
        const templateSection = document.createElement('div');
        templateSection.className = 'modal-section';

        const templateLabel = document.createElement('label');
        templateLabel.className = 'modal-label';
        templateLabel.textContent = 'Behavior Template';
        templateSection.appendChild(templateLabel);

        const templateGrid = document.createElement('div');
        templateGrid.className = 'role-template-grid';

        const templateButtons = {};
        ['autonomous', 'balanced', 'verbose', 'custom'].forEach(templateKey => {
            const tmpl = ROLE_TEMPLATES[templateKey];
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'role-template-btn';
            btn.dataset.template = templateKey;
            btn.innerHTML = `
                <span class="template-name">${tmpl.label}</span>
                <span class="template-desc">${tmpl.description || ''}</span>
            `;
            if (localState.template === templateKey) {
                btn.classList.add('selected');
            }
            btn.addEventListener('click', () => applyTemplate(templateKey));
            templateGrid.appendChild(btn);
            templateButtons[templateKey] = btn;
        });
        templateSection.appendChild(templateGrid);
        body.appendChild(templateSection);

        // =============== FREEDOM LEVEL ===============
        const freedomSection = document.createElement('div');
        freedomSection.className = 'modal-section';

        const freedomLabel = document.createElement('label');
        freedomLabel.className = 'modal-label';
        freedomLabel.textContent = 'Freedom Level';
        freedomSection.appendChild(freedomLabel);

        const freedomSelect = document.createElement('select');
        freedomSelect.className = 'modal-select';
        freedomSelect.innerHTML = `
            <option value="supervised">Supervised - Requires approval for most actions</option>
            <option value="semi-autonomous">Semi-Autonomous - Independent within guidelines</option>
            <option value="autonomous">Autonomous - Full independence, minimal check-ins</option>
        `;
        freedomSelect.value = localState.freedomLevel;
        freedomSelect.addEventListener('change', () => {
            localState.freedomLevel = freedomSelect.value;
            markCustom();
        });
        freedomSection.appendChild(freedomSelect);
        body.appendChild(freedomSection);

        // =============== NOTIFICATIONS ===============
        const notifySection = document.createElement('div');
        notifySection.className = 'modal-section';

        const notifyLabel = document.createElement('label');
        notifyLabel.className = 'modal-label';
        notifyLabel.textContent = 'Notify User On';
        notifySection.appendChild(notifyLabel);

        const notifyGrid = document.createElement('div');
        notifyGrid.className = 'notify-checkbox-grid';

        const notifyCheckboxes = {};
        const notifyOptions = [
            { key: 'start', label: 'Task Start' },
            { key: 'question', label: 'Questions' },
            { key: 'conflict', label: 'Conflicts' },
            { key: 'completion', label: 'Completion' },
            { key: 'error', label: 'Errors' }
        ];

        notifyOptions.forEach(opt => {
            const wrapper = document.createElement('label');
            wrapper.className = 'modal-checkbox-row';

            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = localState.notifyOn[opt.key];
            checkbox.addEventListener('change', () => {
                localState.notifyOn[opt.key] = checkbox.checked;
                markCustom();
            });

            const text = document.createElement('span');
            text.textContent = opt.label;

            wrapper.appendChild(checkbox);
            wrapper.appendChild(text);
            notifyGrid.appendChild(wrapper);
            notifyCheckboxes[opt.key] = checkbox;
        });
        notifySection.appendChild(notifyGrid);
        body.appendChild(notifySection);

        // =============== MAX ACTIONS ===============
        const actionsSection = document.createElement('div');
        actionsSection.className = 'modal-section';

        const actionsLabel = document.createElement('label');
        actionsLabel.className = 'modal-label';
        actionsLabel.textContent = 'Max Actions Per Session';
        actionsSection.appendChild(actionsLabel);

        const actionsRow = document.createElement('div');
        actionsRow.className = 'actions-input-row';

        const actionsInput = document.createElement('input');
        actionsInput.type = 'number';
        actionsInput.className = 'modal-input actions-input';
        actionsInput.min = '1';
        actionsInput.max = '100';
        actionsInput.value = localState.maxActionsPerSession ?? '';
        actionsInput.placeholder = 'Unlimited';
        actionsInput.addEventListener('input', () => {
            const val = actionsInput.value.trim();
            localState.maxActionsPerSession = val ? parseInt(val, 10) : null;
            markCustom();
        });

        const unlimitedBtn = document.createElement('button');
        unlimitedBtn.type = 'button';
        unlimitedBtn.className = 'modal-btn modal-btn-secondary';
        unlimitedBtn.textContent = 'Unlimited';
        unlimitedBtn.addEventListener('click', () => {
            actionsInput.value = '';
            localState.maxActionsPerSession = null;
            markCustom();
        });

        actionsRow.appendChild(actionsInput);
        actionsRow.appendChild(unlimitedBtn);
        actionsSection.appendChild(actionsRow);
        body.appendChild(actionsSection);

        // =============== ROLE CHARTER ===============
        const charterSection = document.createElement('div');
        charterSection.className = 'modal-section';

        const charterLabel = document.createElement('label');
        charterLabel.className = 'modal-label';
        charterLabel.textContent = 'Role Charter (Job Description)';
        charterSection.appendChild(charterLabel);

        const charterTextarea = document.createElement('textarea');
        charterTextarea.className = 'modal-textarea';
        charterTextarea.rows = 3;
        charterTextarea.placeholder = 'Describe this role\'s purpose and responsibilities...';
        charterTextarea.value = localState.roleCharter;
        charterTextarea.addEventListener('input', () => {
            localState.roleCharter = charterTextarea.value;
            markCustom();
        });
        charterSection.appendChild(charterTextarea);
        body.appendChild(charterSection);

        // =============== COLLABORATION GUIDANCE ===============
        const collabSection = document.createElement('div');
        collabSection.className = 'modal-section';

        const collabLabel = document.createElement('label');
        collabLabel.className = 'modal-label';
        collabLabel.textContent = 'Collaboration Guidance';
        collabSection.appendChild(collabLabel);

        const collabTextarea = document.createElement('textarea');
        collabTextarea.className = 'modal-textarea';
        collabTextarea.rows = 3;
        collabTextarea.placeholder = 'How should this role collaborate with others and escalate issues...';
        collabTextarea.value = localState.collaborationGuidance;
        collabTextarea.addEventListener('input', () => {
            localState.collaborationGuidance = collabTextarea.value;
            markCustom();
        });
        collabSection.appendChild(collabTextarea);
        body.appendChild(collabSection);

        // =============== TOOL & SAFETY NOTES ===============
        const safetySection = document.createElement('div');
        safetySection.className = 'modal-section';

        const safetyLabel = document.createElement('label');
        safetyLabel.className = 'modal-label';
        safetyLabel.textContent = 'Tool & Safety Notes';
        safetySection.appendChild(safetyLabel);

        const safetyTextarea = document.createElement('textarea');
        safetyTextarea.className = 'modal-textarea';
        safetyTextarea.rows = 2;
        safetyTextarea.placeholder = 'Tool preferences and safety constraints...';
        safetyTextarea.value = localState.toolAndSafetyNotes;
        safetyTextarea.addEventListener('input', () => {
            localState.toolAndSafetyNotes = safetyTextarea.value;
            markCustom();
        });
        safetySection.appendChild(safetyTextarea);
        body.appendChild(safetySection);

        // =============== ERROR HINT ===============
        const errorHint = document.createElement('div');
        errorHint.className = 'modal-hint modal-error-hint';
        errorHint.style.display = 'none';
        body.appendChild(errorHint);

        // =============== HELPER FUNCTIONS ===============
        function markCustom() {
            if (localState.template !== 'custom') {
                localState.template = 'custom';
                updateTemplateSelection();
            }
        }

        function updateTemplateSelection() {
            Object.entries(templateButtons).forEach(([key, btn]) => {
                btn.classList.toggle('selected', key === localState.template);
            });
        }

        function applyTemplate(templateKey) {
            localState.template = templateKey;
            updateTemplateSelection();

            if (templateKey === 'custom') return;

            const tmpl = ROLE_TEMPLATES[templateKey];
            if (!tmpl) return;

            // Apply template values to form
            localState.freedomLevel = tmpl.freedomLevel;
            freedomSelect.value = tmpl.freedomLevel;

            if (tmpl.notifyOn) {
                Object.entries(tmpl.notifyOn).forEach(([key, val]) => {
                    localState.notifyOn[key] = val;
                    if (notifyCheckboxes[key]) {
                        notifyCheckboxes[key].checked = val;
                    }
                });
            }

            localState.maxActionsPerSession = tmpl.maxActionsPerSession;
            actionsInput.value = tmpl.maxActionsPerSession ?? '';

            if (tmpl.collaborationGuidance) {
                localState.collaborationGuidance = tmpl.collaborationGuidance;
                collabTextarea.value = tmpl.collaborationGuidance;
            }

            if (tmpl.toolAndSafetyNotes) {
                localState.toolAndSafetyNotes = tmpl.toolAndSafetyNotes;
                safetyTextarea.value = tmpl.toolAndSafetyNotes;
            }
        }

        function isDirty() {
            return JSON.stringify(localState) !== originalState;
        }

        // =============== CANCEL HANDLER ===============
        cancelBtn.addEventListener('click', () => {
            if (isDirty()) {
                if (confirm('Discard unsaved changes?')) {
                    close();
                }
            } else {
                close();
            }
        });

        // =============== SAVE HANDLER ===============
        confirmBtn.addEventListener('click', async () => {
            confirmBtn.disabled = true;
            confirmBtn.textContent = 'Saving...';
            errorHint.style.display = 'none';

            // Validate maxActionsPerSession
            if (localState.maxActionsPerSession !== null &&
                (isNaN(localState.maxActionsPerSession) || localState.maxActionsPerSession < 1)) {
                errorHint.textContent = 'Max actions must be a positive number or unlimited.';
                errorHint.style.display = 'block';
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Save';
                return;
            }

            // Build notifyUserOn array from checkboxes
            const notifyUserOn = Object.entries(localState.notifyOn)
                .filter(([_, checked]) => checked)
                .map(([key]) => key);

            const payload = {
                role,
                template: localState.template,
                freedomLevel: localState.freedomLevel,
                notifyUserOn,
                maxActionsPerSession: localState.maxActionsPerSession,
                requireApprovalFor: [],
                roleCharter: localState.roleCharter,
                collaborationGuidance: localState.collaborationGuidance,
                toolAndSafetyNotes: localState.toolAndSafetyNotes
            };

            try {
                await roleSettingsApi.save(role, payload);
                notificationStore.success(`Role settings saved for "${role}"`, 'workbench');
                close();
            } catch (err) {
                errorHint.textContent = err.message || 'Failed to save role settings.';
                errorHint.style.display = 'block';
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Save';
            }
        });
    }

    function showAgentSettingsModal(agent) {
        const name = agent?.name || 'Agent';
        const { body, confirmBtn } = createModalShell(
            `Agent Settings: ${name}`,
            'Close',
            'Apply',
            {
                closeOnCancel: true,
                closeOnConfirm: true,
                confirmTitle: 'Close without saving',
                cancelTitle: 'Save and close'
            }
        );

        const info = document.createElement('div');
        info.className = 'modal-text';
        info.textContent = 'Agent settings panel coming soon.';
        body.appendChild(info);

        confirmBtn.addEventListener('click', () => {
            notificationStore.success(`Saved settings for ${name} (stub)`, 'workbench');
        });
    }

    function buildGreetingPrompt(agent) {
        const name = agent?.name || 'Agent';
        const role = agent?.role || 'role';
        const instructions = agent?.personality?.baseInstructions || '';
        const sliders = agent?.personalitySliders || {};
        const signatureLine = agent?.signatureLine || '';

        const lines = [
            `You are ${name}, role: ${role}.`,
            'Please provide:',
            '1) A 2-4 sentence self introduction.',
            '2) 3-5 bullet first impressions of the project.',
            '3) 2-4 bullet suggested next actions.'
        ];

        if (instructions) {
            lines.unshift(`Personality instructions: ${instructions}`);
        }

        const sliderEntries = Object.entries(sliders);
        if (sliderEntries.length > 0) {
            const sliderText = sliderEntries
                .map(([key, value]) => `${key}=${value}`)
                .join(', ');
            lines.unshift(`Personality sliders: ${sliderText}`);
        }
        if (signatureLine) {
            lines.unshift(`Signature line: ${signatureLine}`);
        }

        return lines.join('\n');
    }

    async function showGreetingScanModal(agentId) {
        const { modal, body } = createModalShell(
            'Greeting Scan',
            'Close',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: true }
        );

        modal.classList.add('agent-greeting-modal');

        const status = document.createElement('div');
        status.className = 'modal-text';
        status.textContent = 'Greeting scan started. Waiting for agent response.';
        body.appendChild(status);

        let agent = null;
        try {
            agent = await agentApi.get(agentId);
        } catch (err) {
            const error = document.createElement('div');
            error.className = 'modal-hint';
            error.textContent = `Failed to load agent data: ${err.message}`;
            body.appendChild(error);
            return;
        }

        // Store the prompt for future wiring without rendering it yet.
        modal.dataset.greetingPrompt = buildGreetingPrompt(agent);

        const responseTitle = document.createElement('div');
        responseTitle.className = 'modal-label';
        responseTitle.textContent = 'Response';
        body.appendChild(responseTitle);

        const responseBox = document.createElement('div');
        responseBox.className = 'greeting-response';
        responseBox.textContent = 'Awaiting agent response...';
        body.appendChild(responseBox);
    }

    function showConferenceInviteModal(seedAgent) {
        const agents = (state.agents.list || []).filter(agent => agent.enabled !== false);
        const seedId = seedAgent?.id || '';
        const { modal, body, confirmBtn, close } = createModalShell(
            'Invite to Conference',
            'Start Conference',
            'Cancel',
            { closeOnCancel: true }
        );

        modal.classList.add('conference-invite-modal');

        const info = document.createElement('div');
        info.className = 'modal-text';
        info.textContent = 'Pick invitees and select a moderator for the conference.';
        body.appendChild(info);

        const agendaRow = document.createElement('div');
        agendaRow.className = 'modal-row';
        const agendaLabel = document.createElement('label');
        agendaLabel.className = 'modal-label';
        agendaLabel.textContent = 'Agenda';
        const agendaInput = document.createElement('textarea');
        agendaInput.className = 'conference-agenda-input';
        agendaInput.placeholder = 'Topics, goals, or questions...';
        agendaInput.rows = 3;
        agendaRow.appendChild(agendaLabel);
        agendaRow.appendChild(agendaInput);
        body.appendChild(agendaRow);

        if (agents.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'conference-agent-empty';
            empty.textContent = 'No agents available to invite.';
            body.appendChild(empty);
            confirmBtn.disabled = true;
            return;
        }

        const list = document.createElement('div');
        list.className = 'conference-agent-list';

        const header = document.createElement('div');
        header.className = 'conference-agent-row conference-agent-header';
        const headerName = document.createElement('div');
        headerName.className = 'conference-agent-name';
        headerName.textContent = 'Agent';
        const headerRole = document.createElement('div');
        headerRole.className = 'conference-agent-role';
        headerRole.textContent = 'Role';
        const headerInvite = document.createElement('div');
        headerInvite.className = 'conference-agent-flag';
        headerInvite.textContent = 'Invited';
        const headerLead = document.createElement('div');
        headerLead.className = 'conference-agent-flag';
        headerLead.textContent = 'Moderator';
        header.appendChild(headerName);
        header.appendChild(headerRole);
        header.appendChild(headerInvite);
        header.appendChild(headerLead);
        list.appendChild(header);

        const selections = new Map();

        agents.forEach(agent => {
            const row = document.createElement('div');
            row.className = 'conference-agent-row';

            const name = document.createElement('div');
            name.className = 'conference-agent-name';
            name.textContent = agent.name || 'Unnamed Agent';
            name.title = agent.name || 'Unnamed Agent';

            const role = document.createElement('div');
            role.className = 'conference-agent-role';
            role.textContent = agent.role || 'role';

            const invitedCell = document.createElement('label');
            invitedCell.className = 'conference-agent-toggle';
            const invitedCheckbox = document.createElement('input');
            invitedCheckbox.type = 'checkbox';
            invitedCheckbox.className = 'conference-agent-checkbox';
            invitedCheckbox.checked = Boolean(agent.id && agent.id === seedId);
            invitedCell.appendChild(invitedCheckbox);

            const leadCell = document.createElement('label');
            leadCell.className = 'conference-agent-toggle';
            const leadCheckbox = document.createElement('input');
            leadCheckbox.type = 'checkbox';
            leadCheckbox.className = 'conference-agent-checkbox';
            leadCell.appendChild(leadCheckbox);

            invitedCheckbox.addEventListener('change', () => {
                // Moderator must be invited, so re-check if user tries to uninvite.
                if (!invitedCheckbox.checked && leadCheckbox.checked) {
                    invitedCheckbox.checked = true;
                }
                updateStartState();
            });

            leadCheckbox.addEventListener('change', () => {
                if (leadCheckbox.checked) {
                    // Single moderator: uncheck all other lead boxes.
                    selections.forEach((item) => {
                        if (item.agent.id !== agent.id) {
                            item.lead.checked = false;
                        }
                    });
                    invitedCheckbox.checked = true;
                }
                updateStartState();
            });

            selections.set(agent.id, { invited: invitedCheckbox, lead: leadCheckbox, agent });

            row.appendChild(name);
            row.appendChild(role);
            row.appendChild(invitedCell);
            row.appendChild(leadCell);
            list.appendChild(row);
        });

        body.appendChild(list);

        const updateStartState = () => {
            const invitedCount = Array.from(selections.values()).filter(item => item.invited.checked).length;
            confirmBtn.disabled = invitedCount === 0;
        };

        updateStartState();

        confirmBtn.addEventListener('click', () => {
            const invited = [];
            const leaders = [];
            selections.forEach((item) => {
                if (item.invited.checked) invited.push(item.agent);
                if (item.lead.checked) leaders.push(item.agent);
            });

            const agenda = agendaInput.value.trim();
            const invitedNames = invited.map(agent => agent.name).join(', ') || 'none';
            const leaderNames = leaders.map(agent => agent.name).join(', ') || 'none';

            log(`Conference started. Invited: ${invitedNames}. Moderators: ${leaderNames}.`, 'info');
            if (agenda) {
                log(`Conference agenda: ${agenda}`, 'info');
            }
            notificationStore.success(`Conference started with ${invited.length} agent(s).`, 'workbench');
            close();
        });
    }

    function showChangeRoleModal(agent) {
        const name = agent?.name || 'Agent';
        const { body, confirmBtn, close } = createModalShell(
            `Change Role: ${name}`,
            'Apply',
            'Close',
            { closeOnCancel: true }
        );

        const roles = Array.from(new Set((state.agents.list || []).map(item => item.role).filter(Boolean)));

        const row = document.createElement('div');
        row.className = 'modal-row';

        const label = document.createElement('label');
        label.className = 'modal-label';
        label.textContent = 'Current roles';

        const select = document.createElement('select');
        select.className = 'modal-select';
        if (roles.length === 0) {
            const option = document.createElement('option');
            option.value = '';
            option.textContent = 'No roles';
            select.appendChild(option);
            select.disabled = true;
        } else {
            roles.forEach(role => {
                const option = document.createElement('option');
                option.value = role;
                option.textContent = role;
                select.appendChild(option);
            });
        }

        const newRoleLabel = document.createElement('label');
        newRoleLabel.className = 'modal-label';
        newRoleLabel.textContent = 'Or create a new role';

        const input = document.createElement('input');
        input.className = 'modal-input';
        input.type = 'text';
        input.placeholder = 'New role name';

        row.appendChild(label);
        row.appendChild(select);
        row.appendChild(newRoleLabel);
        row.appendChild(input);
        body.appendChild(row);

        const updateApplyState = () => {
            const chosen = input.value.trim() || select.value;
            confirmBtn.disabled = !chosen;
        };

        if (agent?.role && roles.includes(agent.role)) {
            select.value = agent.role;
        }

        updateApplyState();

        input.addEventListener('input', updateApplyState);
        select.addEventListener('change', updateApplyState);

        confirmBtn.addEventListener('click', () => {
            const chosen = input.value.trim() || select.value;
            if (chosen) {
                log(`Role for ${name} set to ${chosen}`, 'info');
            }
            close();
        });
    }

    function showConfirmRetireModal(agent) {
        const name = agent?.name || 'Agent';
        const { body, confirmBtn, close } = createModalShell(`Retire ${name}?`, 'Retire', 'Cancel');

        const info = document.createElement('div');
        info.className = 'modal-text';
        info.textContent = 'This will disable the agent. You can re-enable later.';
        body.appendChild(info);

        confirmBtn.addEventListener('click', () => {
            log(`Retired ${name}`, 'warning');
            close();
        });
    }

    async function promptRename(oldPath, nodeType = 'file') {
        oldPath = normalizeWorkspacePath(oldPath);
        showModal('Rename', oldPath, async (newPath) => {
            newPath = normalizeWorkspacePath(newPath);
            if (!newPath || newPath === oldPath) return;

            try {
                await api('/api/rename', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ from: oldPath, to: newPath })
                });
                log(`Renamed: ${oldPath} -> ${newPath}`, 'success');

                // Update open tabs after rename
                updateOpenTabsAfterRename(oldPath, newPath, nodeType);

                await loadFileTree();
            } catch (err) {
                log(`Rename failed: ${err.message}`, 'error');
            }
        });
    }

    function updateOpenTabsAfterRename(oldPath, newPath, nodeType) {
        // Normalize paths at entry point
        oldPath = normalizeWorkspacePath(oldPath);
        newPath = normalizeWorkspacePath(newPath);

        // Collect files and tabs to update
        const filesToUpdate = [];  // { oldFilePath, newFilePath }
        const tabsToUpdate = [];   // { tabId, oldFilePath, newFilePath }

        if (nodeType === 'folder') {
            // For folder rename, find all files/tabs under the old folder path
            const oldPrefix = oldPath + '/';

            // Find affected files
            for (const [filePath] of state.openFiles) {
                if (filePath.startsWith(oldPrefix)) {
                    const newFilePath = newPath + filePath.substring(oldPath.length);
                    filesToUpdate.push({ oldFilePath: filePath, newFilePath: normalizeWorkspacePath(newFilePath) });
                }
            }

            // Find affected tabs
            for (const [tabId, tabData] of state.openTabs) {
                if (tabData.path.startsWith(oldPrefix)) {
                    const newFilePath = newPath + tabData.path.substring(oldPath.length);
                    tabsToUpdate.push({ tabId, oldFilePath: tabData.path, newFilePath: normalizeWorkspacePath(newFilePath) });
                }
            }
        } else {
            // For file rename, update the file and all tabs with that path
            if (state.openFiles.has(oldPath)) {
                filesToUpdate.push({ oldFilePath: oldPath, newFilePath: newPath });
            }

            for (const [tabId, tabData] of state.openTabs) {
                if (tabData.path === oldPath) {
                    tabsToUpdate.push({ tabId, oldFilePath: oldPath, newFilePath: newPath });
                }
            }
        }

        // Update openFiles entries (move from old path to new path)
        filesToUpdate.forEach(({ oldFilePath, newFilePath }) => {
            const fileData = state.openFiles.get(oldFilePath);
            if (fileData) {
                state.openFiles.delete(oldFilePath);
                state.openFiles.set(newFilePath, fileData);
                log(`Updated file entry: ${oldFilePath} -> ${newFilePath}`, 'info');
            }
        });

        // Update each affected tab
        tabsToUpdate.forEach(({ tabId, oldFilePath, newFilePath }) => {
            const tabData = state.openTabs.get(tabId);
            if (!tabData) return;

            // Update the path in the tab data
            tabData.path = newFilePath;

            // Update the tab element
            const tab = document.querySelector(`.tab[data-tab-id="${tabId}"]`);
            if (tab) {
                tab.dataset.path = newFilePath;
                const tabName = tab.querySelector('.tab-name');
                if (tabName) {
                    tabName.textContent = newFilePath.split('/').pop();
                }
            }

            // Update activeFile if this is the active tab
            if (state.activeTabId === tabId) {
                state.activeFile = newFilePath;
            }

            log(`Updated tab: ${oldFilePath} -> ${newFilePath}`, 'info');
        });
    }

    // Collect all folder paths from the file tree for move dialog
    function collectFolderPaths(node, currentPath = '') {
        const results = [];

        // Build path for this node
        let nodePath = currentPath;
        if (node.name && node.name !== 'workspace') {
            nodePath = currentPath ? `${currentPath}/${node.name}` : node.name;
            nodePath = normalizeWorkspacePath(nodePath);
        }

        if (node.type === 'folder') {
            // Include this folder (except synthetic root)
            if (nodePath) {
                results.push(nodePath);
            }

            if (Array.isArray(node.children)) {
                for (const child of node.children) {
                    results.push(...collectFolderPaths(child, nodePath));
                }
            }
        }

        return results;
    }

    function getAllFolderPaths() {
        if (!state.fileTree) return [''];
        const paths = collectFolderPaths(state.fileTree, '');
        // Always include root (empty string) as first option
        return [''].concat(paths.sort());
    }

    async function promptMove(oldPath, nodeType = 'file') {
        oldPath = normalizeWorkspacePath(oldPath);

        // Split old path into folder + basename
        let oldFolder = '';
        let oldName = oldPath;
        const lastSlash = oldPath.lastIndexOf('/');
        if (lastSlash !== -1) {
            oldFolder = oldPath.substring(0, lastSlash);
            oldName = oldPath.substring(lastSlash + 1);
        }

        // Collect folders
        let folders = getAllFolderPaths();

        // If moving a folder, filter out invalid destinations (self and descendants)
        if (nodeType === 'folder') {
            folders = folders.filter(folder => {
                if (!folder) return true; // root always allowed
                if (folder === oldPath) return false;
                return !folder.startsWith(oldPath + '/');
            });
        }

        // Build modal DOM
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';

        const modal = document.createElement('div');
        modal.className = 'modal move-modal';

        const title = document.createElement('div');
        title.className = 'modal-title';
        title.textContent = nodeType === 'folder' ? 'Move Folder' : 'Move File';

        const folderLabel = document.createElement('label');
        folderLabel.className = 'move-label';
        folderLabel.textContent = 'Destination folder:';

        const folderSelect = document.createElement('select');
        folderSelect.className = 'modal-input move-select';
        for (const folder of folders) {
            const option = document.createElement('option');
            option.value = folder;
            option.textContent = folder || '/ (workspace root)';
            if (folder === oldFolder) {
                option.selected = true;
            }
            folderSelect.appendChild(option);
        }

        const nameLabel = document.createElement('label');
        nameLabel.className = 'move-label';
        nameLabel.textContent = nodeType === 'folder' ? 'Folder name:' : 'File name:';

        const nameInput = document.createElement('input');
        nameInput.type = 'text';
        nameInput.className = 'modal-input';
        nameInput.value = oldName;

        const preview = document.createElement('div');
        preview.className = 'move-preview';

        function updatePreview() {
            const folder = folderSelect.value;
            const name = nameInput.value.trim();
            if (!name) {
                preview.textContent = 'New path: (invalid – empty name)';
                preview.classList.add('invalid');
                return;
            }
            const combined = folder ? `${folder}/${name}` : name;
            const normalized = normalizeWorkspacePath(combined);
            preview.textContent = `New path: ${normalized}`;
            preview.classList.remove('invalid');
        }

        folderSelect.addEventListener('change', updatePreview);
        nameInput.addEventListener('input', updatePreview);
        updatePreview();

        const buttons = document.createElement('div');
        buttons.className = 'modal-buttons';

        const cancelBtn = document.createElement('button');
        cancelBtn.className = 'modal-btn modal-btn-secondary';
        cancelBtn.textContent = 'Cancel';

        const okBtn = document.createElement('button');
        okBtn.className = 'modal-btn modal-btn-primary';
        okBtn.textContent = 'Move';

        buttons.appendChild(cancelBtn);
        buttons.appendChild(okBtn);

        modal.appendChild(title);
        modal.appendChild(folderLabel);
        modal.appendChild(folderSelect);
        modal.appendChild(nameLabel);
        modal.appendChild(nameInput);
        modal.appendChild(preview);
        modal.appendChild(buttons);

        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        function closeModal() {
            overlay.remove();
        }

        // Focus filename input and select it
        nameInput.focus();
        nameInput.select();

        return new Promise(resolve => {
            cancelBtn.addEventListener('click', () => {
                closeModal();
                resolve();
            });

            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) {
                    closeModal();
                    resolve();
                }
            });

            okBtn.addEventListener('click', async () => {
                const folder = folderSelect.value;
                const name = nameInput.value.trim();
                if (!name) {
                    nameInput.focus();
                    return;
                }
                const combined = folder ? `${folder}/${name}` : name;
                const newPath = normalizeWorkspacePath(combined);
                if (!newPath || newPath === oldPath) {
                    closeModal();
                    resolve();
                    return;
                }

                try {
                    await api('/api/rename', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ from: oldPath, to: newPath })
                    });
                    log(`Moved: ${oldPath} -> ${newPath}`, 'success');

                    // Update open tabs after move
                    updateOpenTabsAfterRename(oldPath, newPath, nodeType);

                    await loadFileTree();
                } catch (err) {
                    log(`Move failed: ${err.message}`, 'error');
                }

                closeModal();
                resolve();
            });

            nameInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    okBtn.click();
                } else if (e.key === 'Escape') {
                    cancelBtn.click();
                }
            });

            folderSelect.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    cancelBtn.click();
                }
            });
        });
    }

    async function promptDelete(path, nodeType = 'file') {
        path = normalizeWorkspacePath(path);
        if (!confirm(`Delete "${path}"?`)) return;

        try {
            await api(`/api/file?path=${encodeURIComponent(path)}`, {
                method: 'DELETE'
            });
            log(`Deleted: ${path}`, 'success');

            // Close all tabs for this path (and children if folder)
            closeTabsForPath(path, nodeType === 'folder');

            await loadFileTree();
        } catch (err) {
            log(`Delete failed: ${err.message}`, 'error');
        }
    }

    async function promptNewFile(type = 'file', parentPath = '') {
        parentPath = normalizeWorkspacePath(parentPath);
        // Default placeholder based on type
        let placeholder = type === 'folder' ? 'folder-name' : 'file.txt';

        // If parentPath is provided, pre-fill with parent path prefix
        if (parentPath) {
            placeholder = parentPath + '/' + placeholder;
        }

        const title = parentPath ? `New ${type} in ${parentPath}` : `New ${type}`;

        showModal(title, placeholder, async (path) => {
            path = normalizeWorkspacePath(path);
            if (!path) return;

            try {
                await api('/api/file', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path, type, initialContent: '' })
                });
                log(`Created: ${path}`, 'success');
                await loadFileTree();

                if (type === 'file') {
                    openFile(path);
                }
            } catch (err) {
                log(`Create failed: ${err.message}`, 'error');
            }
        });
    }

    // Search
    async function performSearch(query) {
        if (!query.trim()) {
            elements.searchResults.innerHTML = '<div class="search-no-results">Enter a search term</div>';
            return;
        }

        try {
            const results = await api(`/api/search?q=${encodeURIComponent(query)}`);

            if (results.length === 0) {
                elements.searchResults.innerHTML = '<div class="search-no-results">No results found</div>';
                notificationStore.editorSearchNoResults(query, true);
                return;
            }

            // Group results by file
            const grouped = new Map();
            results.forEach(result => {
                if (!grouped.has(result.file)) {
                    grouped.set(result.file, []);
                }
                grouped.get(result.file).push(result);
            });

            elements.searchResults.innerHTML = '';

            // Render grouped results
            grouped.forEach((fileResults, file) => {
                // File header (collapsible)
                const fileGroup = document.createElement('div');
                fileGroup.className = 'search-file-group';

                const fileHeader = document.createElement('div');
                fileHeader.className = 'search-file-header';
                fileHeader.innerHTML = `
                    <span class="search-file-icon">📄</span>
                    <span class="search-file-name">${escapeHtml(file)}</span>
                    <span class="search-file-count">${fileResults.length}</span>
                `;
                fileGroup.appendChild(fileHeader);

                // Matches container
                const matchesContainer = document.createElement('div');
                matchesContainer.className = 'search-matches expanded';

                fileResults.forEach(result => {
                    const item = document.createElement('div');
                    item.className = 'search-match';

                    // Highlight the query in preview
                    const highlightedPreview = highlightSearchMatch(result.preview, query);

                    item.innerHTML = `
                        <span class="search-match-line">${result.line}</span>
                        <span class="search-match-preview">${highlightedPreview}</span>
                    `;
                    item.addEventListener('click', (e) => {
                        e.stopPropagation();
                        jumpToSearchResult(result.file, result.line, query);
                    });
                    matchesContainer.appendChild(item);
                });

                fileGroup.appendChild(matchesContainer);

                // Toggle collapse on header click
                fileHeader.addEventListener('click', () => {
                    matchesContainer.classList.toggle('expanded');
                    fileHeader.classList.toggle('collapsed');
                });

                elements.searchResults.appendChild(fileGroup);
            });

            log(`Search found ${results.length} results in ${grouped.size} files for "${query}"`, 'info');
        } catch (err) {
            log(`Search failed: ${err.message}`, 'error');
        }
    }

    function highlightSearchMatch(text, query) {
        if (!query) return escapeHtml(text);
        const escaped = escapeHtml(text);
        const queryEscaped = escapeHtml(query);
        const regex = new RegExp(`(${queryEscaped.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
        return escaped.replace(regex, '<mark class="search-highlight">$1</mark>');
    }

    async function jumpToSearchResult(file, line, query) {
        try {
            await openFile(file);
            if (state.editor) {
                // Reveal line in center
                state.editor.revealLineInCenter(line);

                // Find the query in this line and select it
                const model = state.editor.getModel();
                if (model && query) {
                    const lineContent = model.getLineContent(line);
                    const matchIndex = lineContent.toLowerCase().indexOf(query.toLowerCase());
                    if (matchIndex !== -1) {
                        // Select the matched text
                        state.editor.setSelection({
                            startLineNumber: line,
                            startColumn: matchIndex + 1,
                            endLineNumber: line,
                            endColumn: matchIndex + query.length + 1
                        });
                    } else {
                        // Just position cursor at start of line
                        state.editor.setPosition({ lineNumber: line, column: 1 });
                    }
                } else {
                    state.editor.setPosition({ lineNumber: line, column: 1 });
                }
                state.editor.focus();
            }
        } catch (err) {
            log(`Failed to open search result: ${err.message}`, 'error');
        }
    }

    function openWorkspaceSearch() {
        // Switch to Search tab
        document.querySelectorAll('.console-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.console-panel').forEach(p => p.classList.remove('active'));

        const searchTab = document.querySelector('.console-tab[data-tab="search"]');
        const searchPanel = document.getElementById('search-panel');

        if (searchTab && searchPanel) {
            searchTab.classList.add('active');
            searchPanel.classList.add('active');
        }

        // Focus the search input
        elements.searchInput.focus();
        elements.searchInput.select();

        log('Workspace search (Ctrl+Shift+F)', 'info');
    }

    function getStoredAgentId() {
        return localStorage.getItem('selected-agent-id');
    }

    function pickDefaultAgentId(agents) {
        if (!agents || agents.length === 0) return null;

        const stored = getStoredAgentId();
        if (stored && agents.some(agent => agent.id === stored)) {
            return stored;
        }

        const primaryWriter = agents.find(agent => agent.isPrimaryForRole && agent.role === 'writer');
        if (primaryWriter) return primaryWriter.id;

        const primary = agents.find(agent => agent.isPrimaryForRole);
        if (primary) return primary.id;

        return agents[0].id;
    }

    function setSelectedAgentId(agentId) {
        state.agents.selectedId = agentId || null;
        localStorage.setItem('selected-agent-id', agentId || '');
        if (elements.agentSelect) {
            elements.agentSelect.value = agentId || '';
        }
    }

    function renderAgentSelector() {
        if (!elements.agentSelect) return;

        const agents = state.agents.list || [];
        elements.agentSelect.innerHTML = '';

        if (agents.length === 0) {
            const option = document.createElement('option');
            option.value = '';
            option.textContent = 'No agents';
            elements.agentSelect.appendChild(option);
            elements.agentSelect.disabled = true;
            return;
        }

        agents.forEach(agent => {
            const option = document.createElement('option');
            option.value = agent.id;
            option.textContent = `${agent.name} (${agent.role})`;
            elements.agentSelect.appendChild(option);
        });

        elements.agentSelect.disabled = false;
        setSelectedAgentId(pickDefaultAgentId(agents));
    }

    async function loadAgents() {
        try {
            const agents = await agentApi.list();
            state.agents.list = Array.isArray(agents) ? agents : [];
            renderAgentSidebar();
            renderAgentSelector();
            log(`Loaded ${state.agents.list.length} agent(s)`, 'info');
        } catch (err) {
            state.agents.list = [];
            renderAgentSidebar();
            renderAgentSelector();
            log(`Failed to load agents: ${err.message}`, 'error');
        }
    }

    function getExplorerVisible() {
        return localStorage.getItem('explorer-visible') === '1';
    }

    function setExplorerVisible(visible) {
        if (!elements.fileTreeArea || !elements.btnToggleExplorer) return;
        elements.fileTreeArea.classList.toggle('collapsed', !visible);
        elements.btnToggleExplorer.classList.toggle('is-active', visible);
        if (elements.leftSidebar) {
            elements.leftSidebar.classList.toggle('explorer-collapsed', !visible);
        }
        localStorage.setItem('explorer-visible', visible ? '1' : '0');
    }

    // Chat
    function addChatMessage(role, content) {
        const msg = document.createElement('div');
        msg.className = `chat-message ${role}`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'chat-message-content';
        contentDiv.textContent = content;

        msg.appendChild(contentDiv);
        elements.chatHistory.appendChild(msg);
        elements.chatHistory.scrollTop = elements.chatHistory.scrollHeight;
    }

    async function sendChatMessage() {
        const message = elements.chatInput.value.trim();
        if (!message) return;

        elements.chatInput.value = '';
        elements.chatSend.disabled = true;

        addChatMessage('user', message);
        log(`Chat: User message sent`, 'info');

        try {
            const payload = { message };
            if (state.agents.selectedId) {
                payload.agentId = state.agents.selectedId;
            }
            const response = await api('/api/ai/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            addChatMessage('assistant', response.content);
            log(`Chat: AI response received`, 'success');
        } catch (err) {
            addChatMessage('assistant', 'Sorry, I encountered an error. Please try again.');
            log(`Chat error: ${err.message}`, 'error');
        } finally {
            elements.chatSend.disabled = false;
        }
    }

    // Console Tabs
    function initConsoleTabs() {
        document.querySelectorAll('.console-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.console-tab').forEach(t => t.classList.remove('active'));
                document.querySelectorAll('.console-panel').forEach(p => p.classList.remove('active'));

                tab.classList.add('active');
                document.getElementById(`${tab.dataset.tab}-panel`).classList.add('active');
            });
        });
    }

    // AI Actions (stub)
    function initAIActions() {
        document.querySelectorAll('.ai-action-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const action = btn.dataset.action;
                const [type, id] = action.split('-');

                if (type === 'preview') {
                    showDiffPreview(id);
                } else if (type === 'apply') {
                    log(`Applied change ${id}`, 'success');
                    btn.closest('.ai-action-item').remove();
                } else if (type === 'reject') {
                    log(`Rejected change ${id}`, 'warning');
                    btn.closest('.ai-action-item').remove();
                }
            });
        });

        elements.closeDiff.addEventListener('click', () => {
            elements.diffPreview.classList.add('hidden');
        });
    }

    function showDiffPreview(id) {
        // Mock diff content
        const diffs = {
            '1': `--- scenes/intro.md
+++ scenes/intro.md
@@ -5,6 +5,8 @@
 ## Description
 The rain tapped against the window in a steady rhythm.
+The aroma of freshly ground coffee beans mingled with
+the subtle sweetness of vanilla syrup.
 Mara sat alone at her usual table,
-nursing a lukewarm latte.
+nursing a lukewarm latte, its foam long since dissolved.`,
            '2': `--- chars/mara.md
+++ chars/mara.md
@@ -20,3 +20,10 @@
 ## Background
 Former police detective who left the force after
 uncovering corruption.
+
+## Detailed History
+At age 12, Mara witnessed her father being taken away
+by unknown men in dark suits. The police ruled it as
+a voluntary disappearance, but Mara never believed it.
+This formative trauma drove her to become a detective.`
        };

        elements.diffContent.innerHTML = formatDiff(diffs[id] || '');
        elements.diffPreview.classList.remove('hidden');
    }

    function formatDiff(diff) {
        return diff.split('\n').map(line => {
            if (line.startsWith('+') && !line.startsWith('+++')) {
                return `<span class="diff-add">${escapeHtml(line)}</span>`;
            } else if (line.startsWith('-') && !line.startsWith('---')) {
                return `<span class="diff-remove">${escapeHtml(line)}</span>`;
            }
            return escapeHtml(line);
        }).join('\n');
    }

    // Sidebar Buttons
    function initSidebarButtons() {
        if (elements.btnToggleMode) {
            elements.btnToggleMode.addEventListener('click', () => {
                if (state.viewMode.current === 'workbench') {
                    setViewMode('editor');
                } else if (state.viewMode.current === 'editor') {
                    setViewMode('workbench');
                } else {
                    setViewMode('editor');
                }
            });
        }

        if (elements.btnToggleExplorer) {
            elements.btnToggleExplorer.addEventListener('click', () => {
                const isVisible = !elements.fileTreeArea.classList.contains('collapsed');
                setExplorerVisible(!isVisible);
            });
        }

        if (elements.btnWorkspaceSwitch) {
            elements.btnWorkspaceSwitch.addEventListener('click', () => {
                showWorkspaceSwitcher();
            });
        }

        document.getElementById('btn-open-workspace').addEventListener('click', async () => {
            log('Opening workspace folder...', 'info');
            try {
                const result = await api('/api/workspace/open', { method: 'POST' });
                if (result.ok) {
                    log('Workspace folder opened', 'success');
                } else {
                    log(`Failed to open workspace folder: ${result.error}`, 'error');
                }
            } catch (err) {
                log(`Failed to open workspace folder: ${err.message}`, 'error');
            }
        });

        if (elements.btnSidebarSearch) {
            elements.btnSidebarSearch.addEventListener('click', () => {
                openWorkspaceSearch();
            });
        }

        document.getElementById('btn-commit').addEventListener('click', () => {
            log('Creating a new local version (saving all files)...', 'info');
            saveAllFiles();
        });

        document.getElementById('btn-open-terminal').addEventListener('click', async () => {
            log('Opening terminal at workspace...', 'info');
            try {
                const result = await api('/api/workspace/terminal', { method: 'POST' });
                if (result.ok) {
                    log(`Terminal opened (${result.terminal || 'terminal'})`, 'success');
                } else {
                    log(`Failed to open terminal: ${result.error}`, 'error');
                }
            } catch (err) {
                log(`Failed to open terminal: ${err.message}`, 'error');
            }
        });

        if (elements.btnOpenSettings) {
            elements.btnOpenSettings.addEventListener('click', () => {
                setViewMode('settings');
            });
        }

        elements.btnRevealFile.addEventListener('click', async () => {
            if (!state.activeFile) return;
            log(`Revealing file: ${state.activeFile}`, 'info');
            try {
                const result = await api('/api/file/reveal', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path: state.activeFile })
                });
                if (result.ok) {
                    if (result.fallback === 'open-folder') {
                        log('Reveal failed; opened containing folder instead', 'warning');
                    } else {
                        log('File revealed in explorer', 'success');
                    }
                } else {
                    log(`Failed to reveal file: ${result.error}`, 'error');
                }
            } catch (err) {
                log(`Failed to reveal file: ${err.message}`, 'error');
            }
        });

        elements.btnOpenFolder.addEventListener('click', async () => {
            if (!state.activeFile) {
                log('No active file to open containing folder', 'warning');
                return;
            }
            log(`Opening containing folder for: ${state.activeFile}`, 'info');
            try {
                const result = await api('/api/file/open-folder', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path: state.activeFile })
                });
                if (result.ok) {
                    log('Containing folder opened', 'success');
                } else {
                    log(`Failed to open containing folder: ${result.error}`, 'error');
                }
            } catch (err) {
                log(`Failed to open containing folder: ${err.message}`, 'error');
            }
        });

        // Find button - triggers Monaco find widget
        elements.btnFind.addEventListener('click', () => {
            if (state.editor && state.activeFile) {
                state.editor.trigger('keyboard', 'actions.find');
                log('Find in file (Ctrl+F)', 'info');
            } else {
                log('No file open to search', 'warning');
            }
        });

        // Search button - opens workspace search
        elements.btnSearch.addEventListener('click', () => {
            openWorkspaceSearch();
        });

        document.getElementById('btn-new-file').addEventListener('click', () => promptNewFile('file'));
        document.getElementById('btn-new-folder').addEventListener('click', () => promptNewFile('folder'));
        document.getElementById('btn-refresh-tree').addEventListener('click', () => {
            log('Refreshing file tree...', 'info');
            loadFileTree();
        });

        // Import agent button (in workbench sidebar)
        const btnAddAgent = document.getElementById('btn-add-agent');
        if (btnAddAgent) {
            btnAddAgent.addEventListener('click', () => {
                showAddAgentWizard();
            });
        }

        const btnImportAgent = document.getElementById('btn-import-agent');
        if (btnImportAgent) {
            btnImportAgent.addEventListener('click', () => {
                showImportAgentDialog();
            });
        }

        setExplorerVisible(getExplorerVisible());
    }

    function openFolder(folderName) {
        // Find the folder in tree and expand it
        const items = document.querySelectorAll('.tree-item.tree-folder');
        items.forEach(item => {
            const name = item.querySelector('.tree-name').textContent;
            if (name.toLowerCase() === folderName.toLowerCase()) {
                const children = item.nextElementSibling;
                if (children && children.classList.contains('tree-children')) {
                    children.classList.add('expanded');
                    item.querySelector('.tree-icon').textContent = '📂';
                }
            }
        });
    }

    // Event Listeners
    function initEventListeners() {
        // Search
        elements.searchBtn.addEventListener('click', () => {
            performSearch(elements.searchInput.value);
        });

        elements.searchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                performSearch(elements.searchInput.value);
            }
        });

        // Chat
        elements.chatSend.addEventListener('click', sendChatMessage);
        elements.chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendChatMessage();
            }
        });

        if (elements.agentSelect) {
            elements.agentSelect.addEventListener('change', (e) => {
                setSelectedAgentId(e.target.value);
            });
        }

        // Global keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
            const cmdOrCtrl = isMac ? e.metaKey : e.ctrlKey;

            if (cmdOrCtrl && e.key === 's') {
                e.preventDefault();
                saveCurrentFile();
            }

            // E1: Find in File (Ctrl+F / Cmd+F)
            if (cmdOrCtrl && e.key === 'f' && !e.shiftKey) {
                e.preventDefault();
                if (state.editor && state.activeFile) {
                    // Trigger Monaco's built-in find widget
                    state.editor.trigger('keyboard', 'actions.find');
                    log('Find in file (Ctrl+F)', 'info');
                } else {
                    log('No file open to search', 'warning');
                }
            }

            // E2: Find in Workspace (Ctrl+Shift+F / Cmd+Shift+F)
            if (cmdOrCtrl && e.shiftKey && e.key === 'F') {
                e.preventDefault();
                openWorkspaceSearch();
            }
        });

        // Window resize
        window.addEventListener('resize', () => {
            if (state.editor) {
                state.editor.layout();
            }
        });
    }

    // Initialize
    function init() {
        log('Control Room starting...', 'info');

        initSplitters();
        initMonaco();
        initConsoleTabs();
        initAIActions();
        initSidebarButtons();
        initEventListeners();
        initNotifications();
        initWorkbenchNewsfeedSubscription(); // Newsfeed updates
        loadFileTree();
        loadAgents();
        loadWorkspaceInfo();

        // Set initial view mode (starts in Editor mode)
        setViewMode('editor');

        // Welcome message in chat
        addChatMessage('assistant', 'Hello! I\'m your AI writing assistant. How can I help you with your creative writing project today?');

        log('Control Room ready!', 'success');
        log('Tip: Use the sidebar toggle to switch Workbench and Editor views.', 'info');
    }

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
