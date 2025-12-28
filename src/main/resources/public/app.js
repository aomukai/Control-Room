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
        }
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
        fileTree: document.getElementById('file-tree'),
        tabsContainer: document.getElementById('tabs-container'),
        editorPlaceholder: document.getElementById('editor-placeholder'),
        monacoEditor: document.getElementById('monaco-editor'),
        consoleOutput: document.getElementById('console-output'),
        chatHistory: document.getElementById('chat-history'),
        chatInput: document.getElementById('chat-input'),
        chatSend: document.getElementById('chat-send'),
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
        notificationFilterScopes: document.getElementById('notification-filter-scopes')
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
            return push('info', 'workbench', `Issue #${issueId} created: ${title}`, details, 'attention', false, 'View Issue',
                { type: 'open-issue', issueId }, 'issues');
        }

        function issueClosed(issueId, title) {
            return push('success', 'workbench', `Issue #${issueId} closed: ${title}`, '', 'info', false, 'View Issue',
                { type: 'open-issue', issueId }, 'issues');
        }

        function issueCommentAdded(issueId, author) {
            return push('info', 'workbench', `Comment added to Issue #${issueId}`, `By: ${author}`, 'social', false, 'View Thread',
                { type: 'open-issue', issueId }, 'issues');
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

        return {
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

        switch (payload.type) {
            case 'open-notification-center':
                openNotificationCenter(notification.id);
                break;
            case 'open-issue':
                log(`Open issue requested: ${payload.issueId}`, 'info');
                openNotificationCenter(notification.id);
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
            actions.push({ label: 'Open in New Tab', action: () => openFileInNewTab(node.path) });
        }

        // For folders: add "New File Here" and "New Folder Here"
        if (node.type === 'folder') {
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
            const response = await api('/api/ai/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message })
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
        document.getElementById('btn-commit').addEventListener('click', () => {
            log('Saving all files...', 'info');
            saveAllFiles();
        });

        document.getElementById('btn-scenes').addEventListener('click', () => {
            // Filter tree to scenes folder
            log('Showing scenes folder', 'info');
            openFolder('scenes');
        });

        document.getElementById('btn-chars').addEventListener('click', () => {
            log('Showing characters folder', 'info');
            openFolder('chars');
        });

        document.getElementById('btn-etc').addEventListener('click', () => {
            log('Showing notes folder', 'info');
            openFolder('notes');
        });

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
                    log('File revealed in explorer', 'success');
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
        loadFileTree();

        // Welcome message in chat
        addChatMessage('assistant', 'Hello! I\'m your AI writing assistant. How can I help you with your creative writing project today?');

        log('Control Room ready!', 'success');
    }

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
