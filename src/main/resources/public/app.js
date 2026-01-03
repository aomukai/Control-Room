// Control Room Application
// State and API are loaded from state.js and api.js (window.state, window.api, etc.)
(function() {
    'use strict';

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
        chatMemoryId: document.getElementById('chat-memory-id'),
        chatMemorySet: document.getElementById('chat-memory-set'),
        chatMemoryBadge: document.getElementById('chat-memory-badge'),
        chatEscalate: document.getElementById('chat-escalate'),
        agentSelect: document.getElementById('agent-select'),
        searchInput: document.getElementById('search-input'),
        searchBtn: document.getElementById('search-btn'),
        searchResults: document.getElementById('search-results'),
        diffPreview: document.getElementById('diff-preview'),
        diffContent: document.getElementById('diff-content'),
        closeDiff: document.getElementById('close-diff'),
        btnCreateIssue: document.getElementById('btn-create-issue'),
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
        btnToggleModeTop: document.getElementById('btn-toggle-mode-top'),
        btnOpenIssues: document.getElementById('btn-open-issues'),
        btnStartConference: document.getElementById('btn-start-conference'),
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

    // escapeHtml is now in modals.js (window.modals.escapeHtml)
    const escapeHtml = window.modals.escapeHtml;

    // API objects are loaded from api.js (window.issueApi, window.agentApi, etc.)

    const PROVIDERS_REQUIRE_KEY = new Set([
        'openai', 'anthropic', 'gemini', 'grok', 'openrouter', 'nanogpt', 'togetherai'
    ]);
    const LOCAL_PROVIDERS = new Set(['lmstudio', 'ollama', 'jan', 'koboldcpp']);

    function isEndpointWired(endpoint) {
        if (!endpoint || !endpoint.provider || !endpoint.model) return false;
        const keyRef = endpoint.keyRef || endpoint.apiKeyRef || '';
        if (PROVIDERS_REQUIRE_KEY.has(endpoint.provider) && !keyRef) return false;
        return true;
    }

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
    // Notification store is now in notifications.js (window.createNotificationStore)
    const notificationStore = window.createNotificationStore();

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

        if (elements.btnToggleModeTop) {
            const isWorkbench = mode === 'workbench';
            const toggleLabel = mode === 'settings' ? 'Back to Editor' : (isWorkbench ? 'Switch to Editor' : 'Switch to Workbench');
            elements.btnToggleModeTop.classList.toggle('active', isWorkbench);
            elements.btnToggleModeTop.style.display = mode === 'editor' ? 'none' : 'flex';
            const label = elements.btnToggleModeTop.querySelector('.tab-label');
            if (label) {
                label.textContent = toggleLabel;
            } else {
                elements.btnToggleModeTop.textContent = toggleLabel;
            }
        }

        if (elements.btnOpenIssues) {
            elements.btnOpenIssues.style.display = mode === 'workbench' ? 'flex' : 'none';
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
        state.viewMode.previous = previousMode;
        state.viewMode.current = mode;

        updateModeControls(mode);
        if (elements.leftSidebar) {
            elements.leftSidebar.classList.toggle('workbench-hidden', mode === 'workbench');
        }

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
        } else if (mode === 'settings') {
            renderSettingsView();
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
    // SETTINGS VIEW
    // ============================================

    function showComingSoonModal(title, details) {
        const { modal, body, confirmBtn } = createModalShell(
            title || 'Coming soon',
            'Got it',
            'Close',
            { closeOnCancel: true, closeOnConfirm: true }
        );
        modal.classList.add('settings-coming-soon-modal');

        const text = document.createElement('div');
        text.className = 'modal-text';
        text.textContent = details || 'This setting is wired up visually for now. Functionality will ship soon.';
        body.appendChild(text);

        confirmBtn.focus();
    }

    function promptForPassword(title, hint) {
        return new Promise(resolve => {
            const { modal, body, confirmBtn, cancelBtn, close } = createModalShell(
                title || 'Enter password',
                'Confirm',
                'Cancel',
                { closeOnCancel: true, closeOnConfirm: false }
            );

            modal.classList.add('settings-coming-soon-modal');

            const text = document.createElement('div');
            text.className = 'modal-text';
            text.textContent = hint || 'Password required.';
            body.appendChild(text);

            const input = document.createElement('input');
            input.type = 'password';
            input.className = 'modal-input';
            input.placeholder = 'Password';
            body.appendChild(input);

            const error = document.createElement('div');
            error.className = 'modal-error-hint';
            error.style.display = 'none';
            body.appendChild(error);

            const finish = (value) => {
                resolve(value);
                close();
            };

            confirmBtn.addEventListener('click', () => {
                const value = input.value.trim();
                if (!value) {
                    error.textContent = 'Password is required.';
                    error.style.display = 'block';
                    return;
                }
                finish(value);
            });

            cancelBtn.addEventListener('click', () => finish(null));

            input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    confirmBtn.click();
                }
            });

            input.focus();
        });
    }

    function getProviderDefaults() {
        const raw = localStorage.getItem('control-room:provider-defaults');
        if (!raw) {
            return { provider: 'openai', model: '' };
        }
        try {
            const parsed = JSON.parse(raw);
            return {
                provider: parsed.provider || 'openai',
                model: parsed.model || ''
            };
        } catch (err) {
            return { provider: 'openai', model: '' };
        }
    }

    function saveProviderDefaults(data) {
        const payload = {
            provider: data.provider || 'openai',
            model: data.model || ''
        };
        localStorage.setItem('control-room:provider-defaults', JSON.stringify(payload));
        notificationStore.success('Provider defaults saved.', 'global');
    }

    function renderSettingsView() {
        const container = document.getElementById('settings-content');
        if (!container) return;

        container.innerHTML = `
            <div class="settings-layout">
                <!-- Settings Sidebar Navigation -->
                <nav class="settings-nav">
                    <div class="settings-nav-header">
                        <button class="settings-back-btn" id="settings-back-btn" type="button" title="Back to Editor">
                            <img src="assets/icons/heroicons_outline/arrow-left.svg" alt="">
                        </button>
                        <h2>
                            <img src="assets/icons/heroicons_outline/cog-6-tooth.svg" alt="">
                            Settings
                        </h2>
                    </div>
                    <div class="settings-nav-list">
                        <div class="settings-nav-item active" data-section="appearance">
                            <span class="nav-icon"><img src="assets/icons/heroicons_outline/swatch.svg" alt=""></span>
                            Appearance
                        </div>
                        <div class="settings-nav-item" data-section="editor">
                            <span class="nav-icon"><img src="assets/icons/heroicons_outline/pencil-square.svg" alt=""></span>
                            Editor
                            <span class="nav-badge">Soon</span>
                        </div>
                        <div class="settings-nav-item" data-section="providers">
                            <span class="nav-icon"><img src="assets/icons/heroicons_outline/server.svg" alt=""></span>
                            Providers
                        </div>
                        <div class="settings-nav-item" data-section="security">
                            <span class="nav-icon"><img src="assets/icons/heroicons_outline/key.svg" alt=""></span>
                            Keys & Security
                        </div>
                        <div class="settings-nav-item" data-section="backup">
                            <span class="nav-icon"><img src="assets/icons/heroicons_outline/cloud.svg" alt=""></span>
                            Backup
                            <span class="nav-badge">Soon</span>
                        </div>
                        <div class="settings-nav-item" data-section="shortcuts">
                            <span class="nav-icon"><img src="assets/icons/heroicons_outline/command-line.svg" alt=""></span>
                            Shortcuts
                        </div>
                    </div>
                    <div class="settings-nav-footer">
                        Control Room v0.1
                    </div>
                </nav>

                <!-- Settings Main Content -->
                <main class="settings-main">
                    <div class="settings-content">

                        <!-- Appearance Section -->
                        <section class="settings-section active" id="settings-appearance">
                            <div class="settings-section-header">
                                <h3>
                                    <img src="assets/icons/heroicons_outline/swatch.svg" alt="">
                                    Appearance
                                </h3>
                                <p>Customize the look and feel of Control Room.</p>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Theme</div>
                                <div class="settings-card">
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Color Theme</span>
                                            <span class="settings-label-desc">Choose a UI color scheme</span>
                                        </div>
                                        <select class="settings-control" data-coming-soon="Theme selection is not wired yet.">
                                            <option value="default">Default Dark</option>
                                            <option value="studio">Studio</option>
                                            <option value="midnight">Midnight</option>
                                            <option value="paper">Paper (Light)</option>
                                        </select>
                                    </div>
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Day / Night Mode</span>
                                            <span class="settings-label-desc">Toggle between light and dark modes</span>
                                        </div>
                                        <label class="toggle-switch">
                                            <input type="checkbox" data-coming-soon="Day/night mode is coming soon.">
                                            <span class="toggle-slider"></span>
                                        </label>
                                    </div>
                                </div>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Interface</div>
                                <div class="settings-card">
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">UI Scale</span>
                                            <span class="settings-label-desc">Adjust interface element sizes</span>
                                        </div>
                                        <select class="settings-control" data-coming-soon="UI scaling is coming soon.">
                                            <option value="small">Compact</option>
                                            <option value="medium" selected>Normal</option>
                                            <option value="large">Large</option>
                                        </select>
                                    </div>
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Sidebar Position</span>
                                            <span class="settings-label-desc">Place the main sidebar on left or right</span>
                                        </div>
                                        <select class="settings-control" data-coming-soon="Sidebar position is coming soon.">
                                            <option value="left" selected>Left</option>
                                            <option value="right">Right</option>
                                        </select>
                                    </div>
                                </div>
                            </div>
                        </section>

                        <!-- Editor Section -->
                        <section class="settings-section" id="settings-editor">
                            <div class="settings-section-header">
                                <h3>
                                    <img src="assets/icons/heroicons_outline/pencil-square.svg" alt="">
                                    Editor
                                </h3>
                                <p>Configure the text editor behavior and typography.</p>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Typography</div>
                                <div class="settings-card">
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Editor Font</span>
                                            <span class="settings-label-desc">Monospace font for code editing</span>
                                        </div>
                                        <input class="settings-control settings-control-wide" type="text" placeholder="JetBrains Mono, Consolas" data-coming-soon="Editor font is coming soon.">
                                    </div>
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Font Size</span>
                                            <span class="settings-label-desc">Text size in the editor</span>
                                        </div>
                                        <select class="settings-control" data-coming-soon="Font size is coming soon.">
                                            <option value="12">12px</option>
                                            <option value="13">13px</option>
                                            <option value="14" selected>14px</option>
                                            <option value="15">15px</option>
                                            <option value="16">16px</option>
                                        </select>
                                    </div>
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Line Height</span>
                                            <span class="settings-label-desc">Spacing between lines</span>
                                        </div>
                                        <select class="settings-control" data-coming-soon="Line height is coming soon.">
                                            <option value="1.2">Tight (1.2)</option>
                                            <option value="1.5" selected>Normal (1.5)</option>
                                            <option value="1.8">Relaxed (1.8)</option>
                                        </select>
                                    </div>
                                </div>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Behavior</div>
                                <div class="settings-card">
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Word Wrap</span>
                                            <span class="settings-label-desc">Wrap long lines automatically</span>
                                        </div>
                                        <label class="toggle-switch">
                                            <input type="checkbox" checked data-coming-soon="Word wrap setting is coming soon.">
                                            <span class="toggle-slider"></span>
                                        </label>
                                    </div>
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Auto Save</span>
                                            <span class="settings-label-desc">Save files automatically after changes</span>
                                        </div>
                                        <label class="toggle-switch">
                                            <input type="checkbox" data-coming-soon="Auto save is coming soon.">
                                            <span class="toggle-slider"></span>
                                        </label>
                                    </div>
                                </div>
                            </div>
                        </section>

                        <!-- Providers Section -->
                        <section class="settings-section" id="settings-providers">
                            <div class="settings-section-header">
                                <h3>
                                    <img src="assets/icons/heroicons_outline/server.svg" alt="">
                                    AI Providers
                                </h3>
                                <p>Configure default AI provider and model settings.</p>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Default Configuration</div>
                                <div class="settings-card">
                                    <div class="settings-row">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Default Provider</span>
                                            <span class="settings-label-desc">Used when no agent override exists</span>
                                        </div>
                                        <select class="settings-control" id="settings-default-provider">
                                            <option value="openai">OpenAI</option>
                                            <option value="anthropic">Anthropic</option>
                                            <option value="gemini">Gemini</option>
                                            <option value="grok">Grok</option>
                                            <option value="openrouter">OpenRouter</option>
                                            <option value="local">Local</option>
                                        </select>
                                    </div>
                                    <div class="settings-row">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Default Model</span>
                                            <span class="settings-label-desc">Fallback model for the selected provider</span>
                                        </div>
                                        <input class="settings-control settings-control-wide" type="text" id="settings-default-model" placeholder="e.g., gpt-4o-mini">
                                    </div>
                                </div>
                            </div>
                        </section>

                        <!-- Security Section -->
                        <section class="settings-section" id="settings-security">
                            <div class="settings-section-header">
                                <h3>
                                    <img src="assets/icons/heroicons_outline/key.svg" alt="">
                                    Keys & Security
                                </h3>
                                <p>Manage API keys and security settings for your providers.</p>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Vault Settings</div>
                                <div class="settings-card">
                                    <div class="settings-row">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Key Storage Mode</span>
                                            <span class="settings-label-desc">How API keys are stored on disk</span>
                                        </div>
                                        <select class="settings-control" id="settings-key-mode">
                                            <option value="encrypted">Encrypted Vault</option>
                                            <option value="plaintext">Plaintext</option>
                                        </select>
                                    </div>
                                    <div class="settings-row">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Vault Status</span>
                                            <span class="settings-label-desc" id="settings-vault-status">Checking...</span>
                                        </div>
                                        <div class="settings-inline">
                                            <button class="settings-button" type="button" id="settings-vault-unlock">Unlock</button>
                                            <button class="settings-button" type="button" id="settings-vault-lock">Lock</button>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Stored Keys</div>
                                <div id="settings-key-list" class="settings-key-list"></div>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Add New Key</div>
                                <div class="settings-card">
                                    <div class="settings-subsection">
                                        <div class="settings-add-key-form">
                                            <div class="settings-add-key-row">
                                                <div class="settings-add-key-field">
                                                    <label>Provider</label>
                                                    <select class="settings-control" id="settings-key-provider">
                                                        <option value="openai">OpenAI</option>
                                                        <option value="anthropic">Anthropic</option>
                                                        <option value="gemini">Gemini</option>
                                                        <option value="grok">Grok</option>
                                                        <option value="openrouter">OpenRouter</option>
                                                        <option value="nanogpt">NanoGPT</option>
                                                        <option value="togetherai">TogetherAI</option>
                                                    </select>
                                                </div>
                                                <div class="settings-add-key-field">
                                                    <label>Label</label>
                                                    <input class="settings-control" type="text" id="settings-key-label" placeholder="My API key">
                                                </div>
                                            </div>
                                            <div class="settings-add-key-row">
                                                <div class="settings-add-key-field" style="flex: 2;">
                                                    <label>API Key</label>
                                                    <input class="settings-control" type="password" id="settings-key-value" placeholder="Paste your API key here">
                                                </div>
                                                <div class="settings-add-key-field">
                                                    <label>Custom ID (Optional)</label>
                                                    <input class="settings-control" type="text" id="settings-key-id" placeholder="my-key-1">
                                                </div>
                                            </div>
                                            <div class="settings-add-key-row" style="justify-content: flex-end;">
                                                <button class="settings-button settings-button-primary" type="button" id="settings-key-save">Save Key</button>
                                            </div>
                                            <div class="settings-error" id="settings-key-error"></div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </section>

                        <!-- Backup Section -->
                        <section class="settings-section" id="settings-backup">
                            <div class="settings-section-header">
                                <h3>
                                    <img src="assets/icons/heroicons_outline/cloud.svg" alt="">
                                    Cloud Backup
                                </h3>
                                <p>Configure automatic backups to cloud storage providers.</p>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Backup Configuration</div>
                                <div class="settings-card">
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Backup Mode</span>
                                            <span class="settings-label-desc">Automatically sync changes to cloud</span>
                                        </div>
                                        <select class="settings-control" data-coming-soon="Cloud backup is coming soon.">
                                            <option value="off" selected>Off</option>
                                            <option value="auto">Automatic</option>
                                            <option value="manual">Manual Only</option>
                                        </select>
                                    </div>
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Backup Provider</span>
                                            <span class="settings-label-desc">Cloud storage destination</span>
                                        </div>
                                        <select class="settings-control" data-coming-soon="Backup provider is coming soon.">
                                            <option value="none">Select provider...</option>
                                            <option value="drive">Google Drive</option>
                                            <option value="dropbox">Dropbox</option>
                                            <option value="s3">S3 Compatible</option>
                                        </select>
                                    </div>
                                    <div class="settings-row coming-soon">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Backup Frequency</span>
                                            <span class="settings-label-desc">How often to create backups</span>
                                        </div>
                                        <select class="settings-control" data-coming-soon="Backup frequency is coming soon.">
                                            <option value="hourly">Every Hour</option>
                                            <option value="daily" selected>Daily</option>
                                            <option value="weekly">Weekly</option>
                                        </select>
                                    </div>
                                </div>
                            </div>
                        </section>

                        <!-- Shortcuts Section -->
                        <section class="settings-section" id="settings-shortcuts">
                            <div class="settings-section-header">
                                <h3>
                                    <img src="assets/icons/heroicons_outline/command-line.svg" alt="">
                                    Keyboard Shortcuts
                                </h3>
                                <p>View and customize keyboard shortcuts.</p>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Editor Shortcuts</div>
                                <div class="settings-card">
                                    <div class="settings-subsection">
                                        <div class="hotkey-list">
                                            <div class="hotkey-row">
                                                <span>Save file</span>
                                                <span class="hotkey-keys"><kbd>Ctrl</kbd> <span>+</span> <kbd>S</kbd></span>
                                            </div>
                                            <div class="hotkey-row">
                                                <span>Find in file</span>
                                                <span class="hotkey-keys"><kbd>Ctrl</kbd> <span>+</span> <kbd>F</kbd></span>
                                            </div>
                                            <div class="hotkey-row">
                                                <span>Find and replace</span>
                                                <span class="hotkey-keys"><kbd>Ctrl</kbd> <span>+</span> <kbd>H</kbd></span>
                                            </div>
                                            <div class="hotkey-row">
                                                <span>Go to line</span>
                                                <span class="hotkey-keys"><kbd>Ctrl</kbd> <span>+</span> <kbd>G</kbd></span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Navigation Shortcuts</div>
                                <div class="settings-card">
                                    <div class="settings-subsection">
                                        <div class="hotkey-list">
                                            <div class="hotkey-row">
                                                <span>Search in workspace</span>
                                                <span class="hotkey-keys"><kbd>Ctrl</kbd> <span>+</span> <kbd>Shift</kbd> <span>+</span> <kbd>F</kbd></span>
                                            </div>
                                            <div class="hotkey-row">
                                                <span>Quick open file</span>
                                                <span class="hotkey-keys"><kbd>Ctrl</kbd> <span>+</span> <kbd>P</kbd></span>
                                            </div>
                                            <div class="hotkey-row">
                                                <span>Toggle sidebar</span>
                                                <span class="hotkey-keys"><kbd>Ctrl</kbd> <span>+</span> <kbd>B</kbd></span>
                                            </div>
                                            <div class="hotkey-row">
                                                <span>Toggle workbench</span>
                                                <span class="hotkey-keys"><kbd>Ctrl</kbd> <span>+</span> <kbd>Shift</kbd> <span>+</span> <kbd>W</kbd></span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="settings-group">
                                <button class="settings-button" type="button" data-coming-soon="Custom shortcut editing is coming soon.">
                                    Customize Shortcuts
                                </button>
                            </div>
                        </section>

                    </div>
                </main>
            </div>
        `;

        // Wire up back button
        const backBtn = container.querySelector('#settings-back-btn');
        if (backBtn) {
            backBtn.addEventListener('click', () => {
                // Go back to editor (or workbench if that was the previous mode)
                const previousMode = state.viewMode.previous || 'editor';
                setViewMode(previousMode === 'settings' ? 'editor' : previousMode);
            });
        }

        // Wire up nav item clicks
        container.querySelectorAll('.settings-nav-item').forEach(item => {
            item.addEventListener('click', () => {
                const section = item.getAttribute('data-section');
                container.querySelectorAll('.settings-nav-item').forEach(i => i.classList.remove('active'));
                container.querySelectorAll('.settings-section').forEach(s => s.classList.remove('active'));
                item.classList.add('active');
                const targetSection = container.querySelector(`#settings-${section}`);
                if (targetSection) targetSection.classList.add('active');
            });
        });

        // Wire up coming-soon controls
        container.querySelectorAll('[data-coming-soon]').forEach(control => {
            const message = control.getAttribute('data-coming-soon') || '';
            const handler = () => showComingSoonModal('Coming soon', message);
            if (control.tagName === 'SELECT' || control.tagName === 'INPUT') {
                control.addEventListener('change', handler);
            } else {
                control.addEventListener('click', handler);
            }
        });

        initSettingsWiring();
    }

    async function initSettingsWiring() {
        const providerSelect = document.getElementById('settings-default-provider');
        const modelInput = document.getElementById('settings-default-model');
        const modeSelect = document.getElementById('settings-key-mode');
        const vaultStatus = document.getElementById('settings-vault-status');
        const vaultUnlock = document.getElementById('settings-vault-unlock');
        const vaultLock = document.getElementById('settings-vault-lock');
        const keyList = document.getElementById('settings-key-list');
        const keyProvider = document.getElementById('settings-key-provider');
        const keyLabel = document.getElementById('settings-key-label');
        const keyValue = document.getElementById('settings-key-value');
        const keyId = document.getElementById('settings-key-id');
        const keySave = document.getElementById('settings-key-save');
        const keyError = document.getElementById('settings-key-error');

        if (!providerSelect || !modelInput || !modeSelect || !vaultStatus || !vaultUnlock || !vaultLock || !keyList) {
            return;
        }

        let security = { keysSecurityMode: 'plaintext', vaultUnlocked: false };
        let keysMeta = { providers: {} };

        const updateProviderDefaultsUI = () => {
            const defaults = getProviderDefaults();
            providerSelect.value = defaults.provider || 'openai';
            modelInput.value = defaults.model || '';
        };

        const renderKeyList = () => {
            keyList.innerHTML = '';
            const providers = keysMeta.providers || {};
            const providerNames = Object.keys(providers).sort();
            if (providerNames.length === 0) {
                const empty = document.createElement('div');
                empty.className = 'settings-empty';
                empty.textContent = 'No keys stored yet.';
                keyList.appendChild(empty);
                return;
            }

            providerNames.forEach(provider => {
                const entries = providers[provider] || [];
                const section = document.createElement('div');
                section.className = 'settings-key-provider';
                const header = document.createElement('div');
                header.className = 'settings-key-provider-title';
                header.textContent = provider;
                section.appendChild(header);

                entries.forEach(entry => {
                    const row = document.createElement('div');
                    row.className = 'settings-key-row';
                    const meta = document.createElement('div');
                    meta.className = 'settings-key-meta';
                    const label = document.createElement('div');
                    label.className = 'settings-key-label';
                    label.textContent = entry.label || entry.id;
                    const info = document.createElement('div');
                    info.className = 'settings-key-info';
                    info.textContent = entry.id ? `ID: ${entry.id}` : 'No id';
                    meta.appendChild(label);
                    meta.appendChild(info);
                    const actions = document.createElement('div');
                    actions.className = 'settings-key-actions';
                    const del = document.createElement('button');
                    del.type = 'button';
                    del.className = 'settings-button settings-button-danger';
                    del.textContent = 'Delete';
                    del.addEventListener('click', async (e) => {
                        e.preventDefault();
                        let password = null;
                        if (security.keysSecurityMode === 'encrypted' && !security.vaultUnlocked) {
                            password = await promptForPassword('Unlock vault to delete', 'Enter vault password.');
                            if (!password) return;
                        }
                        try {
                            await settingsApi.deleteKey(provider, entry.id, password || undefined);
                            notificationStore.success(`Deleted key ${entry.label || entry.id}`, 'global');
                            await refreshSettingsData();
                        } catch (err) {
                            notificationStore.error(`Failed to delete key: ${err.message}`, 'global');
                        }
                    });
                    actions.appendChild(del);
                    row.appendChild(meta);
                    row.appendChild(actions);
                    section.appendChild(row);
                });

                keyList.appendChild(section);
            });
        };

        const updateVaultUI = () => {
            if (security.vaultUnlocked) {
                vaultStatus.textContent = 'Unlocked';
                vaultUnlock.disabled = true;
                vaultLock.disabled = false;
            } else {
                vaultStatus.textContent = security.keysSecurityMode === 'encrypted' ? 'Locked' : 'Not required';
                vaultUnlock.disabled = security.keysSecurityMode !== 'encrypted';
                vaultLock.disabled = security.keysSecurityMode !== 'encrypted';
            }
        };

        const refreshSettingsData = async () => {
            try {
                security = await settingsApi.getSecurity();
            } catch (err) {
                notificationStore.warning(`Failed to load security settings: ${err.message}`, 'global');
            }
            try {
                keysMeta = await settingsApi.listKeys();
            } catch (err) {
                notificationStore.warning(`Failed to load key metadata: ${err.message}`, 'global');
            }
            modeSelect.value = security.keysSecurityMode || 'plaintext';
            updateVaultUI();
            renderKeyList();
        };

        updateProviderDefaultsUI();
        providerSelect.addEventListener('change', () => {
            saveProviderDefaults({ provider: providerSelect.value, model: modelInput.value.trim() });
        });
        modelInput.addEventListener('change', () => {
            saveProviderDefaults({ provider: providerSelect.value, model: modelInput.value.trim() });
        });

        modeSelect.addEventListener('change', async () => {
            const nextMode = modeSelect.value;
            const password = await promptForPassword('Update key storage mode', 'Enter your vault password to confirm.');
            if (!password) {
                modeSelect.value = security.keysSecurityMode || 'plaintext';
                return;
            }
            try {
                await settingsApi.updateSecurity(nextMode, password);
                notificationStore.success('Security mode updated.', 'global');
                await refreshSettingsData();
            } catch (err) {
                notificationStore.error(`Failed to update security mode: ${err.message}`, 'global');
                modeSelect.value = security.keysSecurityMode || 'plaintext';
            }
        });

        vaultUnlock.addEventListener('click', async () => {
            const password = await promptForPassword('Unlock vault', 'Enter vault password.');
            if (!password) return;
            try {
                await settingsApi.unlockVault(password);
                notificationStore.success('Vault unlocked.', 'global');
                await refreshSettingsData();
            } catch (err) {
                notificationStore.error(`Failed to unlock vault: ${err.message}`, 'global');
            }
        });

        vaultLock.addEventListener('click', async () => {
            try {
                await settingsApi.lockVault();
                notificationStore.success('Vault locked.', 'global');
                await refreshSettingsData();
            } catch (err) {
                notificationStore.error(`Failed to lock vault: ${err.message}`, 'global');
            }
        });

        keySave.addEventListener('click', async () => {
            if (keyError) {
                keyError.textContent = '';
                keyError.style.display = 'none';
            }
            const provider = keyProvider.value;
            const label = keyLabel.value.trim();
            const key = keyValue.value.trim();
            const id = keyId.value.trim();
            if (!provider || !key) {
                if (keyError) {
                    keyError.textContent = 'Provider and key are required.';
                    keyError.style.display = 'block';
                }
                return;
            }
            let password = null;
            if (security.keysSecurityMode === 'encrypted' && !security.vaultUnlocked) {
                password = await promptForPassword('Unlock vault to add key', 'Enter vault password.');
                if (!password) return;
            }
            try {
                await settingsApi.addKey({
                    provider,
                    label: label || null,
                    key,
                    id: id || null,
                    password: password || undefined
                });
                notificationStore.success('Key added.', 'global');
                keyLabel.value = '';
                keyValue.value = '';
                keyId.value = '';
                await refreshSettingsData();
            } catch (err) {
                if (keyError) {
                    keyError.textContent = err.message || 'Failed to add key.';
                    keyError.style.display = 'block';
                }
            }
        });

        await refreshSettingsData();
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

        agents.forEach((agent, index) => {
            const item = document.createElement('div');
            item.className = 'agent-item';
            item.dataset.agentId = agent.id || '';
            item.draggable = true;

            if (index === 0) {
                item.classList.add('team-lead');
            }

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
            const statusInfo = getAgentStatusInfo(agent);
            status.className = `agent-status ${statusInfo.className}`;
            status.title = statusInfo.title;

            item.appendChild(icon);
            item.appendChild(info);
            item.appendChild(status);

            if (index === 0) {
                const badge = document.createElement('span');
                badge.className = 'agent-lead-badge';
                badge.textContent = 'Lead';
                item.appendChild(badge);
            }

            item.addEventListener('click', () => {
                container.querySelectorAll('.agent-item').forEach(el => el.classList.remove('active'));
                item.classList.add('active');
                log(`Selected agent: ${agent.name}`, 'info');
                showWorkbenchChatModal(agent);
            });

            item.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                showAgentContextMenu(e, agent);
            });

            item.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('text/plain', agent.id || '');
                e.dataTransfer.effectAllowed = 'move';
                item.classList.add('dragging');
            });

            item.addEventListener('dragend', () => {
                item.classList.remove('dragging');
            });

            container.appendChild(item);
        });

        container.querySelectorAll('.agent-item').forEach(item => {
            item.addEventListener('dragover', (e) => {
                e.preventDefault();
                const dragging = container.querySelector('.agent-item.dragging');
                if (!dragging || dragging === item) return;
                const rect = item.getBoundingClientRect();
                const shouldInsertAfter = e.clientY > rect.top + rect.height / 2;
                if (shouldInsertAfter) {
                    item.after(dragging);
                } else {
                    item.before(dragging);
                }
            });

            item.addEventListener('drop', async (e) => {
                e.preventDefault();
                const orderedIds = Array.from(container.querySelectorAll('.agent-item'))
                    .map(el => el.dataset.agentId)
                    .filter(Boolean);
                state.agents.list = orderedIds
                    .map(id => agents.find(agent => agent.id === id))
                    .filter(Boolean);
                try {
                    await agentApi.reorder(orderedIds);
                } catch (err) {
                    log(`Failed to save agent order: ${err.message}`, 'warning');
                }
            });
        });
    }

    function getAgentStatusInfo(agent) {
        if (agent.enabled === false) {
            return { className: 'offline', title: 'Offline' };
        }
        const status = state.agents.statusById[agent.id] || 'unknown';
        switch (status) {
            case 'ready':
                return { className: 'ready', title: 'Ready' };
            case 'unreachable':
                return { className: 'unreachable', title: 'Endpoint not reachable' };
            case 'unconfigured':
                return { className: 'unconfigured', title: 'No endpoint configured' };
            case 'incomplete':
                return { className: 'unreachable', title: 'Endpoint incomplete (missing model)' };
            case 'checking':
                return { className: 'checking', title: 'Checking endpoint...' };
            default:
                return { className: 'unknown', title: 'Status unknown' };
        }
    }

    function renderWorkbenchChatPane() {
        renderWorkbenchDashboard();
    }

    function getPlannerAgent() {
        const agents = state.agents.list || [];
        const primaryPlanner = agents.find(agent => agent.role === 'planner' && agent.isPrimaryForRole);
        if (primaryPlanner) return primaryPlanner;
        const planner = agents.find(agent => agent.role === 'planner');
        if (planner) return planner;
        return agents[0] || null;
    }

    function renderWorkbenchDashboard() {
        const container = document.getElementById('workbench-chat-content');
        if (!container) return;

        const leader = getPlannerAgent();
        const leaderName = leader?.name || 'Planner';
        const leaderAvatar = leader?.avatar || '';

        container.innerHTML = `
            <div class="workbench-dashboard">
                <div class="workbench-card workbench-card-hero">
                    <div class="workbench-briefing-header">
                        <div class="workbench-briefing-avatar" id="workbench-briefing-avatar"></div>
                        <div>
                            <div class="workbench-card-title">Planner Briefing</div>
                            <div class="workbench-card-subtitle">Resolved issues and momentum check-in.</div>
                        </div>
                        <button class="workbench-briefing-dismiss" id="workbench-dismiss-briefing" type="button">Dismiss</button>
                    </div>
                    <div class="workbench-briefing" id="workbench-briefing">
                        <div class="workbench-digest-loading">Loading digest...</div>
                    </div>
                    <div class="workbench-briefing-actions">
                        <button class="workbench-link-btn" id="workbench-open-issues" type="button">Open Issues</button>
                        <button class="workbench-link-btn" id="workbench-start-conference" type="button">Start Conference</button>
                    </div>
                    <div class="workbench-briefing-signature">${escapeHtml(leaderName)}</div>
                </div>
                <div class="workbench-card">
                    <div class="workbench-card-title">Issue Pulse</div>
                    <div class="workbench-card-subtitle">Open vs resolved trends.</div>
                    <div class="workbench-stats" id="workbench-issue-stats">
                        <div class="workbench-digest-loading">Loading stats...</div>
                    </div>
                </div>
                <div class="workbench-card workbench-card-compact">
                    <div class="workbench-card-title">Credits Leaderboard</div>
                    <div class="workbench-card-subtitle">Coming soon.</div>
                    <div class="workbench-placeholder">Top contributors will appear here.</div>
                    <div class="workbench-card-detail">No credits yet. Once enabled, this card will show weekly leaders.</div>
                </div>
                <div class="workbench-card workbench-card-compact">
                    <div class="workbench-card-title">Team Activity</div>
                    <div class="workbench-card-subtitle">Last 24 hours.</div>
                    <div class="workbench-placeholder">Telemetry and token usage are coming soon.</div>
                    <div class="workbench-card-detail">Token usage, active sessions, and throughput will appear here.</div>
                </div>
            </div>
        `;

        const briefingAvatar = document.getElementById('workbench-briefing-avatar');
        if (briefingAvatar) {
            const avatarData = leaderAvatar && leaderAvatar.trim() ? leaderAvatar.trim() : '';
            if (avatarData.startsWith('data:') || avatarData.startsWith('http')) {
                const img = document.createElement('img');
                img.src = avatarData;
                img.alt = leaderName;
                briefingAvatar.appendChild(img);
            } else if (avatarData) {
                briefingAvatar.textContent = avatarData;
            } else {
                briefingAvatar.textContent = leaderName.charAt(0).toUpperCase();
            }
        }

        const dismissBtn = document.getElementById('workbench-dismiss-briefing');
        if (dismissBtn) {
            dismissBtn.addEventListener('click', () => {
                const card = dismissBtn.closest('.workbench-card');
                if (card) card.remove();
            });
        }

        container.querySelectorAll('.workbench-card-compact').forEach(card => {
            card.classList.add('workbench-card-expandable');
            card.addEventListener('click', () => {
                card.classList.toggle('is-expanded');
            });
        });

        const openIssuesBtn = document.getElementById('workbench-open-issues');
        if (openIssuesBtn) {
            openIssuesBtn.addEventListener('click', () => {
                openIssueBoardPanel();
            });
        }

        const startConferenceBtn = document.getElementById('workbench-start-conference');
        if (startConferenceBtn) {
            startConferenceBtn.addEventListener('click', () => {
                showConferenceInviteModal();
            });
        }

        loadWorkbenchDashboardData();
    }

    async function loadWorkbenchDashboardData() {
        const digestContainer = document.getElementById('workbench-briefing');
        const statsContainer = document.getElementById('workbench-issue-stats');
        if (!digestContainer || !statsContainer) return;

        try {
            const issues = await issueApi.list();
            const total = issues.length;
            const openCount = issues.filter(issue => issue.status === 'open').length;
            const closed = issues.filter(issue => issue.status === 'closed');
            const resolvedCount = closed.length;
            const recentResolved = closed
                .sort((a, b) => (b.closedAt || b.updatedAt || 0) - (a.closedAt || a.updatedAt || 0))
                .slice(0, 5);

            statsContainer.innerHTML = `
                <div class="workbench-stat">
                    <span class="workbench-stat-label">Open</span>
                    <span class="workbench-stat-value">${openCount}</span>
                </div>
                <div class="workbench-stat">
                    <span class="workbench-stat-label">Resolved</span>
                    <span class="workbench-stat-value">${resolvedCount}</span>
                </div>
                <div class="workbench-stat">
                    <span class="workbench-stat-label">Total</span>
                    <span class="workbench-stat-value">${total}</span>
                </div>
            `;

            if (recentResolved.length === 0) {
                digestContainer.innerHTML = '<div class="workbench-placeholder">No issues resolved yet. Let’s get a win on the board.</div>';
                return;
            }

            const leader = getPlannerAgent();
            const leaderName = leader?.name || 'Planner';
            const creditsEarned = Math.min(resolvedCount, 12);
            const highlightAgent = leaderName;

            digestContainer.innerHTML = `
                <div class="workbench-briefing-text">
                    Hello, here’s the current state of the project: we finished ${resolvedCount} issue${resolvedCount !== 1 ? 's' : ''} recently, and our agents earned ${creditsEarned} credits.
                    ${highlightAgent ? `${escapeHtml(highlightAgent)} was exceptionally successful.` : ''}
                    Check the issues board for the newest items, or start a conference to regroup.
                </div>
                <div class="workbench-digest-list">
                    ${recentResolved.map(issue => `
                        <div class="workbench-digest-item">
                            <div class="workbench-digest-title">Issue #${issue.id}: ${escapeHtml(issue.title)}</div>
                            <div class="workbench-digest-meta">${formatTimestamp(issue.closedAt || issue.updatedAt)}</div>
                        </div>
                    `).join('')}
                </div>
            `;
        } catch (err) {
            digestContainer.innerHTML = `<div class="workbench-placeholder">Failed to load briefing: ${escapeHtml(err.message)}</div>`;
            statsContainer.innerHTML = `<div class="workbench-placeholder">Stats unavailable.</div>`;
        }
    }

    // ============================================
    // ISSUE BOARD
    // ============================================

    function createWorkbenchPanelShell(title) {
        const overlay = document.createElement('div');
        overlay.className = 'workbench-panel-overlay';

        const panel = document.createElement('div');
        panel.className = 'workbench-panel';

        const header = document.createElement('div');
        header.className = 'workbench-panel-header';

        const titleEl = document.createElement('div');
        titleEl.className = 'workbench-panel-title';
        titleEl.textContent = title || '';

        const actions = document.createElement('div');
        actions.className = 'workbench-panel-actions';

        header.appendChild(titleEl);
        header.appendChild(actions);

        const body = document.createElement('div');
        body.className = 'workbench-panel-body';

        panel.appendChild(header);
        panel.appendChild(body);
        overlay.appendChild(panel);
        document.body.appendChild(overlay);

        const close = () => {
            overlay.classList.remove('is-visible');
            setTimeout(() => {
                overlay.remove();
            }, 240);
        };

        requestAnimationFrame(() => {
            void overlay.offsetHeight;
            overlay.classList.add('is-visible');
        });

        return { overlay, panel, header, actions, body, close };
    }

    function openIssueBoardPanel() {
        if (document.getElementById('issue-board-overlay')) return;
        const { overlay, panel, actions, body, close } = createWorkbenchPanelShell('Issue Board');
        overlay.id = 'issue-board-overlay';
        panel.classList.add('issue-board-panel');

        const newIssueBtn = document.createElement('button');
        newIssueBtn.type = 'button';
        newIssueBtn.className = 'workbench-panel-btn';
        newIssueBtn.textContent = 'New Issue';
        newIssueBtn.addEventListener('click', () => {
            showIssueCreateModal();
        });

        const closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'workbench-panel-btn';
        closeBtn.textContent = 'Close';
        closeBtn.addEventListener('click', close);

        actions.appendChild(newIssueBtn);
        actions.appendChild(closeBtn);

        renderIssueBoard(body);
    }

    function showIssueCreateModal() {
        const { modal, body, confirmBtn, close } = createModalShell(
            'Create Issue',
            'Create',
            'Cancel',
            { closeOnCancel: true }
        );

        modal.classList.add('issue-create-modal');

        const error = document.createElement('div');
        error.className = 'modal-error-hint';
        body.appendChild(error);

        const buildRow = (labelText, inputEl) => {
            const row = document.createElement('div');
            row.className = 'modal-row';
            const label = document.createElement('label');
            label.className = 'modal-label';
            label.textContent = labelText;
            row.appendChild(label);
            row.appendChild(inputEl);
            return row;
        };

        const titleInput = document.createElement('input');
        titleInput.type = 'text';
        titleInput.className = 'modal-input';
        titleInput.placeholder = 'Short, descriptive title';
        body.appendChild(buildRow('Title', titleInput));

        const bodyInput = document.createElement('textarea');
        bodyInput.className = 'modal-textarea';
        bodyInput.rows = 4;
        bodyInput.placeholder = 'Describe the issue...';
        body.appendChild(buildRow('Description', bodyInput));

        const prioritySelect = document.createElement('select');
        prioritySelect.className = 'modal-select';
        [
            { value: 'urgent', label: 'Urgent' },
            { value: 'high', label: 'High' },
            { value: 'normal', label: 'Normal' },
            { value: 'low', label: 'Low' }
        ].forEach(({ value, label }) => {
            const option = document.createElement('option');
            option.value = value;
            option.textContent = label;
            prioritySelect.appendChild(option);
        });
        prioritySelect.value = 'normal';
        body.appendChild(buildRow('Priority', prioritySelect));

        const tagsInput = document.createElement('input');
        tagsInput.type = 'text';
        tagsInput.className = 'modal-input';
        tagsInput.placeholder = 'Comma-separated tags (optional)';
        body.appendChild(buildRow('Tags', tagsInput));

        const assigneeInput = document.createElement('input');
        assigneeInput.type = 'text';
        assigneeInput.className = 'modal-input';
        assigneeInput.placeholder = 'Optional assignee';
        body.appendChild(buildRow('Assignee', assigneeInput));

        const parseTags = (value) => {
            const rawTags = value.split(',').map(tag => tag.trim()).filter(Boolean);
            return Array.from(new Set(rawTags));
        };

        const submitIssue = async () => {
            const title = titleInput.value.trim();
            if (!title) {
                error.textContent = 'Title is required.';
                return;
            }

            confirmBtn.disabled = true;
            error.textContent = '';

            const bodyText = bodyInput.value.trim();
            const tags = parseTags(tagsInput.value || '');
            const assignee = assigneeInput.value.trim();

            const payload = {
                title,
                body: bodyText,
                priority: prioritySelect.value,
                tags
            };

            if (assignee) {
                payload.assignedTo = assignee;
            }

            try {
                const issue = await issueApi.create(payload);
                notificationStore.issueCreated(issue.id, issue.title, issue.openedBy || 'user', issue.assignedTo || '');
                await loadIssues();
                close();
                openIssueModal(issue.id);
            } catch (err) {
                error.textContent = err.message;
                confirmBtn.disabled = false;
            }
        };

        confirmBtn.addEventListener('click', submitIssue);

        [titleInput, tagsInput, assigneeInput].forEach(input => {
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    submitIssue();
                }
            });
        });

        bodyInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                e.preventDefault();
                submitIssue();
            }
        });

        titleInput.focus();
    }

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

    function renderIssueBoard(container) {
        const target = container || document.getElementById('workbench-chat-content');
        if (!target) return;

        target.innerHTML = `
            <div class="issue-board">
                <div class="issue-board-header">
                    <div class="issue-board-title">
                        <span class="issue-board-icon">📋</span>
                        <span>Issue Board</span>
                    </div>
                    <div class="issue-board-actions">
                        <button type="button" class="issue-board-btn issue-board-btn-primary" id="issue-board-new" title="Create issue">
                            New Issue
                        </button>
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

        const newBtn = document.getElementById('issue-board-new');
        if (newBtn) {
            newBtn.addEventListener('click', () => {
                showIssueCreateModal();
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

        const isClosed = issue.status === 'closed';
        const actionLabel = isClosed ? 'Reopen' : 'Close';
        const actionClass = isClosed ? 'issue-action-reopen' : 'issue-action-close';

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
            <div class="issue-card-actions">
                <button type="button" class="issue-quick-action ${actionClass}" title="${actionLabel} issue">
                    ${actionLabel}
                </button>
            </div>
            ${tagsHtml || moreTagsHtml ? `<div class="issue-card-tags">${tagsHtml}${moreTagsHtml}</div>` : ''}
        `;

        // Click to open issue modal
        card.addEventListener('click', () => {
            openIssueModal(issue.id);
        });

        const quickAction = card.querySelector('.issue-quick-action');
        if (quickAction) {
            quickAction.addEventListener('click', async (e) => {
                e.stopPropagation();
                quickAction.disabled = true;
                const targetStatus = isClosed ? 'open' : 'closed';
                try {
                    const updated = await issueApi.update(issue.id, { status: targetStatus });
                    if (targetStatus === 'closed') {
                        notificationStore.issueClosed(updated.id, updated.title);
                    } else {
                        notificationStore.success(`Issue #${updated.id} reopened: ${updated.title}`, 'workbench');
                    }
                    await loadIssues();
                } catch (err) {
                    notificationStore.error(`Failed to ${actionLabel.toLowerCase()} Issue #${issue.id}: ${err.message}`, 'workbench');
                } finally {
                    quickAction.disabled = false;
                }
            });
        }

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

    // Scene Segments API (uses window.api from api.js)
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
                showWorkbenchChatModal(agent);
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

        const providers = [
            'openai', 'anthropic', 'gemini', 'grok', 'openrouter', 'nanogpt', 'togetherai',
            'lmstudio', 'ollama', 'jan', 'koboldcpp', 'custom'
        ];

        const initialTemplate = templates[0];
        const formState = resumeState || {
            templateId: initialTemplate.id,
            name: '',
            role: initialTemplate.role,
            skills: [...initialTemplate.skills],
            goals: [...initialTemplate.goals],
            instructions: initialTemplate.instructions,
            provider: 'openai',
            model: '',
            keyRef: '',
            baseUrl: '',
            temperature: '',
            maxOutputTokens: '',
            timeoutMs: '',
            maxRetries: '',
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
            const info = document.createElement('div');
            info.className = 'modal-text';
            info.textContent = 'Configure the endpoint this agent will use.';
            body.appendChild(info);

            const summary = document.createElement('div');
            summary.className = 'modal-text';
            const providerLabel = formState.provider ? escapeHtml(formState.provider) : 'Not set';
            const modelLabel = formState.model ? escapeHtml(formState.model) : 'Not set';
            summary.innerHTML = `
                <div><strong>Provider:</strong> ${providerLabel}</div>
                <div><strong>Model:</strong> ${modelLabel}</div>
            `;
            body.appendChild(summary);

            const hint = document.createElement('div');
            hint.className = 'modal-hint';
            const wired = isEndpointWired({
                provider: formState.provider,
                model: formState.model,
                keyRef: formState.keyRef
            });
            hint.textContent = wired
                ? 'Endpoint configured. You can adjust settings anytime.'
                : 'Endpoint not wired yet. Configure provider, model, and API key if required.';
            body.appendChild(hint);

            const actions = document.createElement('div');
            actions.className = 'modal-buttons';
            const configureBtn = document.createElement('button');
            configureBtn.type = 'button';
            configureBtn.className = 'modal-btn modal-btn-primary';
            configureBtn.textContent = 'Configure Endpoint';
            actions.appendChild(configureBtn);
            body.appendChild(actions);

            configureBtn.addEventListener('click', () => {
                const resumeStep = stepIndex;
                close();
                const draftAgent = {
                    id: null,
                    name: formState.name.trim() || generateAgentName(formState.role),
                    role: formState.role.trim(),
                    endpoint: {
                        provider: formState.provider,
                        model: formState.model,
                        apiKeyRef: formState.keyRef || null,
                        baseUrl: formState.baseUrl || null,
                        temperature: formState.temperature ?? null,
                        maxOutputTokens: formState.maxOutputTokens ?? null,
                        timeoutMs: formState.timeoutMs ?? null,
                        maxRetries: formState.maxRetries ?? null
                    }
                };
                showAgentSettingsModal(draftAgent, {
                    allowDraft: true,
                    initialEndpoint: draftAgent.endpoint,
                    onSave: async (endpoint) => {
                        formState.provider = endpoint.provider || formState.provider;
                        formState.model = endpoint.model || '';
                        formState.keyRef = endpoint.apiKeyRef || endpoint.keyRef || '';
                        formState.baseUrl = endpoint.baseUrl || '';
                        formState.temperature = endpoint.temperature ?? '';
                        formState.maxOutputTokens = endpoint.maxOutputTokens ?? '';
                        formState.timeoutMs = endpoint.timeoutMs ?? '';
                        formState.maxRetries = endpoint.maxRetries ?? '';
                    },
                    onClose: () => {
                        showAddAgentWizard({ state: formState, stepIndex: resumeStep });
                    }
                });
            });

            setNextEnabled(wired);
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
                    model: formState.model,
                    apiKeyRef: formState.keyRef || null,
                    baseUrl: formState.baseUrl || null,
                    timeoutMs: formState.timeoutMs || null,
                    maxRetries: formState.maxRetries || null
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
                const endpointSnapshot = {
                    provider: formState.provider,
                    model: formState.model,
                    keyRef: formState.keyRef || ''
                };
                const wiredNow = isEndpointWired(endpointSnapshot);
                if (wiredNow) {
                    notificationStore.push(
                        'success',
                        'workbench',
                        `Created and connected ${created.name}.`,
                        `Model: ${formState.model}`,
                        'social',
                        false,
                        '',
                        null,
                        'agents'
                    );
                    await createAgentIntroIssue(created, endpointSnapshot, 'initial wiring');
                } else {
                    notificationStore.push(
                        'success',
                        'workbench',
                        `Created ${created.name}.`,
                        `Role: ${created.role || role}`,
                        'social',
                        false,
                        '',
                        null,
                        'agents'
                    );
                }
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

    // Modal functions are now in modals.js (window.modals)
    const showModal = window.modals.showModal;
    const createModalShell = window.modals.createModalShell;

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

    function showAgentSettingsModal(agent, options = {}) {
        const allowDraft = Boolean(options.allowDraft);
        const onSaveDraft = typeof options.onSave === 'function' ? options.onSave : null;
        const onCloseDraft = typeof options.onClose === 'function' ? options.onClose : null;
        const name = agent?.name || 'Agent';
        const agentId = agent?.id;
        const { overlay, modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            `Agent Settings: ${name}`,
            'Save',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: false }
        );
        const closeWithCallback = (result = {}) => {
            close();
            if (onCloseDraft) {
                onCloseDraft(result);
            }
        };

        modal.classList.add('agent-settings-modal');

        const providerOptions = [
            'openai', 'anthropic', 'gemini', 'grok', 'openrouter', 'nanogpt', 'togetherai',
            'lmstudio', 'ollama', 'jan', 'koboldcpp', 'custom'
        ];

        const defaultBaseUrls = {
            openai: 'https://api.openai.com',
            anthropic: 'https://api.anthropic.com',
            gemini: 'https://generativelanguage.googleapis.com',
            grok: 'https://api.x.ai/v1',
            openrouter: 'https://openrouter.ai',
            nanogpt: 'https://nano-gpt.com/api/v1',
            togetherai: 'https://api.together.xyz',
            lmstudio: 'http://localhost:1234',
            ollama: 'http://localhost:11434',
            jan: 'http://localhost:1234',
            koboldcpp: 'http://localhost:1234',
            custom: ''
        };

        const formState = {
            provider: 'anthropic',
            model: '',
            keyRef: '',
            baseUrl: '',
            temperature: '',
            maxOutputTokens: '',
            timeoutMs: '',
            maxRetries: '',
            nanoGptLegacy: false
        };

        let security = { keysSecurityMode: 'plaintext', vaultUnlocked: true };
        let keyMetadata = { providers: {} };
        let modelList = [];
        let isLoadingModels = false;
        let baseUrlTouched = false;
        let initialEndpointSnapshot = { provider: '', model: '', keyRef: '' };
        let initialWired = false;

        const errorHint = document.createElement('div');
        errorHint.className = 'modal-hint modal-error-hint';
        errorHint.style.display = 'none';
        const modelHint = document.createElement('div');
        modelHint.className = 'modal-hint';
        modelHint.style.display = 'none';
        const localActions = document.createElement('div');
        localActions.className = 'agent-settings-inline';
        localActions.style.display = 'none';
        const localHelp = document.createElement('button');
        localHelp.type = 'button';
        localHelp.className = 'modal-btn modal-btn-secondary';
        localHelp.textContent = 'Local provider not running?';
        const localRetry = document.createElement('button');
        localRetry.type = 'button';
        localRetry.className = 'modal-btn modal-btn-secondary';
        localRetry.textContent = 'Retry';
        localActions.appendChild(localHelp);
        localActions.appendChild(localRetry);

        const content = document.createElement('div');
        content.className = 'agent-settings-body';
        body.appendChild(content);
        body.appendChild(modelHint);
        body.appendChild(localActions);
        body.appendChild(errorHint);

        const vaultSection = document.createElement('div');
        vaultSection.className = 'agent-settings-vault';

        const vaultLabel = document.createElement('div');
        vaultLabel.className = 'modal-text';
        vaultLabel.textContent = 'Key vault locked. Enter password to unlock stored keys.';
        const vaultRow = document.createElement('div');
        vaultRow.className = 'agent-settings-inline';
        const vaultInput = document.createElement('input');
        vaultInput.type = 'password';
        vaultInput.className = 'modal-input';
        vaultInput.placeholder = 'Vault password';
        const vaultBtn = document.createElement('button');
        vaultBtn.type = 'button';
        vaultBtn.className = 'modal-btn modal-btn-secondary';
        vaultBtn.textContent = 'Unlock';
        vaultRow.appendChild(vaultInput);
        vaultRow.appendChild(vaultBtn);
        vaultSection.appendChild(vaultLabel);
        vaultSection.appendChild(vaultRow);

        const providerRow = document.createElement('div');
        providerRow.className = 'modal-row';
        const providerLabel = document.createElement('label');
        providerLabel.className = 'modal-label';
        providerLabel.textContent = 'Provider';
        const providerSelect = document.createElement('select');
        providerSelect.className = 'modal-select';
        providerOptions.forEach(provider => {
            const option = document.createElement('option');
            option.value = provider;
            option.textContent = provider;
            providerSelect.appendChild(option);
        });
        providerRow.appendChild(providerLabel);
        providerRow.appendChild(providerSelect);

        const keyRow = document.createElement('div');
        keyRow.className = 'modal-row';
        const keyLabel = document.createElement('label');
        keyLabel.className = 'modal-label';
        keyLabel.textContent = 'API Key';
        const keyInline = document.createElement('div');
        keyInline.className = 'agent-settings-inline';
        const keySelect = document.createElement('select');
        keySelect.className = 'modal-select';
        const keyAddBtn = document.createElement('button');
        keyAddBtn.type = 'button';
        keyAddBtn.className = 'modal-btn modal-btn-secondary';
        keyAddBtn.textContent = 'Add';
        keyInline.appendChild(keySelect);
        keyInline.appendChild(keyAddBtn);
        keyRow.appendChild(keyLabel);
        keyRow.appendChild(keyInline);

        const addKeySection = document.createElement('div');
        addKeySection.className = 'agent-settings-add-key';
        addKeySection.style.display = 'none';
        const addKeyLabel = document.createElement('label');
        addKeyLabel.className = 'modal-label';
        addKeyLabel.textContent = 'New Key';
        const addKeyNameRow = document.createElement('div');
        addKeyNameRow.className = 'agent-settings-inline';
        const addKeyNameInput = document.createElement('input');
        addKeyNameInput.type = 'text';
        addKeyNameInput.className = 'modal-input';
        addKeyNameInput.placeholder = 'Label (optional)';
        const addKeyValueInput = document.createElement('input');
        addKeyValueInput.type = 'password';
        addKeyValueInput.className = 'modal-input';
        addKeyValueInput.placeholder = 'Paste API key';
        addKeyNameRow.appendChild(addKeyNameInput);
        addKeyNameRow.appendChild(addKeyValueInput);

        const addKeyActions = document.createElement('div');
        addKeyActions.className = 'agent-settings-inline';
        const addKeySaveBtn = document.createElement('button');
        addKeySaveBtn.type = 'button';
        addKeySaveBtn.className = 'modal-btn modal-btn-primary';
        addKeySaveBtn.textContent = 'Save Key';
        const addKeyCancelBtn = document.createElement('button');
        addKeyCancelBtn.type = 'button';
        addKeyCancelBtn.className = 'modal-btn modal-btn-secondary';
        addKeyCancelBtn.textContent = 'Cancel';
        addKeyActions.appendChild(addKeyCancelBtn);
        addKeyActions.appendChild(addKeySaveBtn);

        addKeySection.appendChild(addKeyLabel);
        addKeySection.appendChild(addKeyNameRow);
        addKeySection.appendChild(addKeyActions);

        const modelRow = document.createElement('div');
        modelRow.className = 'modal-row';
        const modelLabel = document.createElement('label');
        modelLabel.className = 'modal-label';
        modelLabel.textContent = 'Model';
        const modelSearch = document.createElement('input');
        modelSearch.type = 'text';
        modelSearch.className = 'modal-input';
        modelSearch.placeholder = 'Search models...';
        const modelListRow = document.createElement('div');
        modelListRow.className = 'agent-settings-inline';
        const modelSelect = document.createElement('select');
        modelSelect.className = 'modal-select';
        const modelRefreshBtn = document.createElement('button');
        modelRefreshBtn.type = 'button';
        modelRefreshBtn.className = 'modal-btn modal-btn-secondary';
        modelRefreshBtn.textContent = 'Refresh';
        modelListRow.appendChild(modelSelect);
        modelListRow.appendChild(modelRefreshBtn);
        modelRow.appendChild(modelLabel);
        modelRow.appendChild(modelSearch);
        modelRow.appendChild(modelListRow);

        const baseRow = document.createElement('div');
        baseRow.className = 'modal-row';
        const baseLabel = document.createElement('label');
        baseLabel.className = 'modal-label';
        baseLabel.textContent = 'Base URL';
        const baseInput = document.createElement('input');
        baseInput.type = 'text';
        baseInput.className = 'modal-input';
        baseInput.placeholder = 'Leave blank to use provider default';
        baseRow.appendChild(baseLabel);
        baseRow.appendChild(baseInput);

        const nanoLegacyRow = document.createElement('label');
        nanoLegacyRow.className = 'modal-checkbox-row';
        const nanoLegacyToggle = document.createElement('input');
        nanoLegacyToggle.type = 'checkbox';
        const nanoLegacyText = document.createElement('span');
        nanoLegacyText.textContent = 'Use NanoGPT legacy API path (/api/v1legacy)';
        nanoLegacyRow.appendChild(nanoLegacyToggle);
        nanoLegacyRow.appendChild(nanoLegacyText);

        const tempRow = document.createElement('div');
        tempRow.className = 'modal-row';
        const tempLabel = document.createElement('label');
        tempLabel.className = 'modal-label';
        tempLabel.textContent = 'Temperature';
        const tempInput = document.createElement('input');
        tempInput.type = 'number';
        tempInput.step = '0.1';
        tempInput.min = '0';
        tempInput.max = '2';
        tempInput.className = 'modal-input';
        tempInput.placeholder = 'Leave blank for provider default';
        tempRow.appendChild(tempLabel);
        tempRow.appendChild(tempInput);

        const tokenRow = document.createElement('div');
        tokenRow.className = 'modal-row';
        const tokenLabel = document.createElement('label');
        tokenLabel.className = 'modal-label';
        tokenLabel.textContent = 'Max Output Tokens';
        const tokenInput = document.createElement('input');
        tokenInput.type = 'number';
        tokenInput.min = '1';
        tokenInput.className = 'modal-input';
        tokenInput.placeholder = 'Leave blank for provider default';
        tokenRow.appendChild(tokenLabel);
        tokenRow.appendChild(tokenInput);

        const timeoutRow = document.createElement('div');
        timeoutRow.className = 'modal-row';
        const timeoutLabel = document.createElement('label');
        timeoutLabel.className = 'modal-label';
        timeoutLabel.textContent = 'Timeout (ms)';
        const timeoutInput = document.createElement('input');
        timeoutInput.type = 'number';
        timeoutInput.min = '1000';
        timeoutInput.className = 'modal-input';
        timeoutInput.placeholder = 'Leave blank for default';
        timeoutRow.appendChild(timeoutLabel);
        timeoutRow.appendChild(timeoutInput);

        const retryRow = document.createElement('div');
        retryRow.className = 'modal-row';
        const retryLabel = document.createElement('label');
        retryLabel.className = 'modal-label';
        retryLabel.textContent = 'Max Retries';
        const retryInput = document.createElement('input');
        retryInput.type = 'number';
        retryInput.min = '0';
        retryInput.className = 'modal-input';
        retryInput.placeholder = 'Leave blank for default';
        retryRow.appendChild(retryLabel);
        retryRow.appendChild(retryInput);

        content.appendChild(providerRow);
        content.appendChild(keyRow);
        content.appendChild(addKeySection);
        content.appendChild(modelRow);

        const advancedSection = document.createElement('div');
        advancedSection.className = 'agent-settings-advanced';
        const advancedToggle = document.createElement('button');
        advancedToggle.type = 'button';
        advancedToggle.className = 'agent-settings-advanced-toggle';
        advancedToggle.textContent = 'Advanced settings';
        const advancedBody = document.createElement('div');
        advancedBody.className = 'agent-settings-advanced-body';
        advancedBody.style.display = 'none';
        advancedBody.appendChild(baseRow);
        advancedBody.appendChild(nanoLegacyRow);
        advancedBody.appendChild(tempRow);
        advancedBody.appendChild(tokenRow);
        advancedBody.appendChild(timeoutRow);
        advancedBody.appendChild(retryRow);
        advancedSection.appendChild(advancedToggle);
        advancedSection.appendChild(advancedBody);
        content.appendChild(advancedSection);

        const renderNanoLegacy = () => {
            const isNano = formState.provider === 'nanogpt';
            nanoLegacyRow.style.display = isNano ? 'flex' : 'none';
        };

        const updateLocalActions = () => {
            const isLocal = LOCAL_PROVIDERS.has(formState.provider);
            localActions.style.display = isLocal ? 'flex' : 'none';
        };

        const updateLocalHint = (message = '') => {
            const isLocal = LOCAL_PROVIDERS.has(formState.provider);
            if (!isLocal) {
                modelHint.style.display = 'none';
                return;
            }
            if (message) {
                modelHint.textContent = message;
                modelHint.style.display = 'block';
                return;
            }
            const base = (formState.baseUrl || defaultBaseUrls[formState.provider] || '').replace(/\/+$/, '');
            const path = formState.provider === 'ollama' ? '/api/tags' : '/v1/models';
            if (base) {
                modelHint.textContent = `Local provider will be queried at ${base}${path}.`;
                modelHint.style.display = 'block';
            } else {
                modelHint.textContent = 'Set a Base URL in Advanced to reach your local provider.';
                modelHint.style.display = 'block';
            }
        };

        const updateVaultSection = () => {
            if (security.keysSecurityMode === 'encrypted' && !security.vaultUnlocked) {
                if (!vaultSection.parentNode) {
                    content.insertBefore(vaultSection, content.firstChild);
                }
            } else if (vaultSection.parentNode) {
                vaultSection.remove();
            }
        };

        const updateKeySelect = () => {
            keySelect.innerHTML = '';
            const provider = formState.provider;
            const keys = (keyMetadata.providers && keyMetadata.providers[provider]) || [];
            const emptyOption = document.createElement('option');
            emptyOption.value = '';
            emptyOption.textContent = keys.length ? 'Select a key' : 'No saved keys';
            keySelect.appendChild(emptyOption);

            keys.forEach(entry => {
                const option = document.createElement('option');
                option.value = `${provider}:${entry.id}`;
                option.textContent = entry.label || entry.id;
                keySelect.appendChild(option);
            });
            const expectedPrefix = `${provider}:`;
            if (formState.keyRef && !formState.keyRef.startsWith(expectedPrefix)) {
                formState.keyRef = '';
            }
            keySelect.value = formState.keyRef || '';
        };

        const updateModelSelect = () => {
            modelSelect.innerHTML = '';
            if (isLoadingModels) {
                const option = document.createElement('option');
                option.value = '';
                option.textContent = 'Loading models...';
                modelSelect.appendChild(option);
                modelSelect.disabled = true;
                return;
            }
            if (!modelList.length) {
                const option = document.createElement('option');
                option.value = '';
                option.textContent = 'No models loaded';
                modelSelect.appendChild(option);
                modelSelect.disabled = true;
                return;
            }
            modelSelect.disabled = false;
            const blank = document.createElement('option');
            blank.value = '';
            blank.textContent = 'Select a model';
            modelSelect.appendChild(blank);
            const query = modelSearch.value.trim().toLowerCase();
            modelList.forEach(model => {
                const name = (model.name || model.id || '').toLowerCase();
                if (query && !name.includes(query) && !String(model.id || '').toLowerCase().includes(query)) {
                    return;
                }
                const option = document.createElement('option');
                option.value = model.id;
                option.textContent = model.name || model.id;
                if (model.recommended) {
                    option.textContent += ' ★';
                }
                modelSelect.appendChild(option);
            });
            modelSelect.value = modelList.find(item => item.id === formState.model) ? formState.model : '';
        };

        const updateBaseUrlPlaceholder = () => {
            const provider = formState.provider;
            baseInput.placeholder = defaultBaseUrls[provider] || 'Leave blank to use provider default';
        };

        const updateKeyVisibility = () => {
            const provider = formState.provider;
            if (LOCAL_PROVIDERS.has(provider)) {
                keyRow.style.display = 'none';
                addKeySection.style.display = 'none';
                formState.keyRef = '';
            } else {
                keyRow.style.display = 'flex';
            }
        };

        const computeNanoBase = (legacy) => legacy
            ? 'https://nano-gpt.com/api/v1legacy'
            : 'https://nano-gpt.com/api/v1';

        const applyNanoLegacy = (enabled) => {
            formState.nanoGptLegacy = enabled;
            nanoLegacyToggle.checked = enabled;
            const current = (formState.baseUrl || '').trim();
            if (!current || current.startsWith('https://nano-gpt.com/api/v1')) {
                const next = computeNanoBase(enabled);
                formState.baseUrl = next;
                baseInput.value = next;
                baseUrlTouched = true;
            }
        };

        const refreshModels = async () => {
            if (!formState.provider) return;
            isLoadingModels = true;
            updateModelSelect();
            errorHint.style.display = 'none';
            updateLocalHint();
            try {
                const baseUrl = baseUrlTouched ? formState.baseUrl : '';
                const models = await providerApi.listModels(formState.provider, baseUrl, formState.keyRef);
                modelList = Array.isArray(models) ? models : [];
                if (LOCAL_PROVIDERS.has(formState.provider)) {
                    updateLocalHint();
                } else {
                    modelHint.style.display = 'none';
                }
            } catch (err) {
                const message = err.message || 'Failed to fetch models.';
                const isLocal = LOCAL_PROVIDERS.has(formState.provider);
                if (String(message).toLowerCase().includes('vault')) {
                    security.vaultUnlocked = false;
                    updateVaultSection();
                }
                if (isLocal) {
                    updateLocalHint(message);
                } else {
                    errorHint.textContent = message;
                    errorHint.style.display = 'block';
                }
                modelList = [];
            } finally {
                isLoadingModels = false;
                updateModelSelect();
            }
        };

        const loadInitialData = async () => {
            try {
                security = await settingsApi.getSecurity();
            } catch (err) {
                log(`Failed to load security settings: ${err.message}`, 'warning');
            }

            try {
                const keysResponse = await settingsApi.listKeys();
                if (keysResponse && keysResponse.providers) {
                    keyMetadata = keysResponse;
                }
            } catch (err) {
                log(`Failed to load API keys: ${err.message}`, 'warning');
            }

            if (agentId) {
                try {
                    const endpoint = await agentEndpointsApi.get(agentId);
                    if (endpoint) {
                        formState.provider = endpoint.provider || formState.provider;
                        formState.model = endpoint.model || '';
                        formState.keyRef = endpoint.apiKeyRef || '';
                        formState.baseUrl = endpoint.baseUrl || '';
                        formState.temperature = endpoint.temperature ?? '';
                        formState.maxOutputTokens = endpoint.maxOutputTokens ?? '';
                        formState.timeoutMs = endpoint.timeoutMs ?? '';
                        formState.maxRetries = endpoint.maxRetries ?? '';
                    }
                } catch (_) {
                    if (agent?.endpoint) {
                        formState.provider = agent.endpoint.provider || formState.provider;
                        formState.model = agent.endpoint.model || '';
                        formState.baseUrl = agent.endpoint.baseUrl || '';
                        formState.temperature = agent.endpoint.temperature ?? '';
                        formState.maxOutputTokens = agent.endpoint.maxOutputTokens ?? '';
                    }
                }
            } else if (allowDraft && options.initialEndpoint) {
                const endpoint = options.initialEndpoint;
                formState.provider = endpoint.provider || formState.provider;
                formState.model = endpoint.model || '';
                formState.keyRef = endpoint.apiKeyRef || endpoint.keyRef || '';
                formState.baseUrl = endpoint.baseUrl || '';
                formState.temperature = endpoint.temperature ?? '';
                formState.maxOutputTokens = endpoint.maxOutputTokens ?? '';
                formState.timeoutMs = endpoint.timeoutMs ?? '';
                formState.maxRetries = endpoint.maxRetries ?? '';
            }

            if (formState.provider === 'nanogpt') {
                formState.nanoGptLegacy = (formState.baseUrl || '').includes('/api/v1legacy');
                if (!formState.baseUrl) {
                    formState.baseUrl = computeNanoBase(formState.nanoGptLegacy);
                }
            }
            if (formState.baseUrl) {
                baseUrlTouched = true;
            }

            initialEndpointSnapshot = {
                provider: formState.provider,
                model: formState.model,
                keyRef: formState.keyRef
            };
            initialWired = isEndpointWired(initialEndpointSnapshot);

            providerSelect.value = formState.provider;
            modelSearch.value = '';
            baseInput.value = formState.baseUrl;
            tempInput.value = formState.temperature;
            tokenInput.value = formState.maxOutputTokens;
            timeoutInput.value = formState.timeoutMs;
            retryInput.value = formState.maxRetries;
            nanoLegacyToggle.checked = formState.nanoGptLegacy;
            const advancedPref = localStorage.getItem('control-room:agent-settings-advanced');
            if (advancedPref === 'open') {
                advancedBody.style.display = 'flex';
                advancedToggle.textContent = 'Hide advanced';
            }

            updateVaultSection();
            updateKeySelect();
            updateKeyVisibility();
            updateBaseUrlPlaceholder();
            renderNanoLegacy();
            updateLocalActions();
            await refreshModels();
        };

        const normalizeBaseUrl = (value) => (value || '').trim().replace(/\/+$/, '').toLowerCase();
        const looksLikeProviderDefault = (value, provider) => {
            const candidate = normalizeBaseUrl(value);
            const fallback = normalizeBaseUrl(defaultBaseUrls[provider]);
            if (!candidate || !fallback) return false;
            if (candidate === fallback) return true;
            const variants = [
                `${fallback}/v1`,
                `${fallback}/api/v1`,
                `${fallback}/api/v1/models`,
                `${fallback}/api/v1legacy`
            ];
            return variants.includes(candidate);
        };

        providerSelect.addEventListener('change', () => {
            const previousProvider = formState.provider;
            formState.provider = providerSelect.value;
            if (formState.baseUrl && looksLikeProviderDefault(formState.baseUrl, previousProvider)) {
                formState.baseUrl = '';
                baseInput.value = '';
                baseUrlTouched = false;
            } else if (formState.baseUrl) {
                const isKnownDefault = Object.keys(defaultBaseUrls)
                    .some(provider => looksLikeProviderDefault(formState.baseUrl, provider));
                if (isKnownDefault) {
                    formState.baseUrl = '';
                    baseInput.value = '';
                    baseUrlTouched = false;
                }
            }
            updateKeyVisibility();
            if (formState.keyRef && !formState.keyRef.startsWith(`${formState.provider}:`)) {
                formState.keyRef = '';
            }
            if (formState.provider === 'nanogpt') {
                applyNanoLegacy(formState.nanoGptLegacy);
            }
            updateKeySelect();
            updateBaseUrlPlaceholder();
            renderNanoLegacy();
            updateLocalActions();
            updateLocalHint();
            refreshModels();
        });

        keySelect.addEventListener('change', () => {
            formState.keyRef = keySelect.value;
            refreshModels();
        });

        keyAddBtn.addEventListener('click', () => {
            addKeySection.style.display = 'flex';
            addKeySection.classList.add('visible');
            addKeyNameInput.value = '';
            addKeyValueInput.value = '';
        });

        addKeyCancelBtn.addEventListener('click', () => {
            addKeySection.style.display = 'none';
        });

        addKeySaveBtn.addEventListener('click', async () => {
            errorHint.style.display = 'none';
            try {
                if (security.keysSecurityMode === 'encrypted' && !security.vaultUnlocked) {
                    errorHint.textContent = 'Unlock the key vault before adding a key.';
                    errorHint.style.display = 'block';
                    return;
                }
                const payload = {
                    provider: formState.provider,
                    label: addKeyNameInput.value.trim(),
                    key: addKeyValueInput.value.trim()
                };
                const result = await settingsApi.addKey(payload);
                if (result && result.keyRef) {
                    formState.keyRef = result.keyRef;
                }
                const keysResponse = await settingsApi.listKeys();
                if (keysResponse && keysResponse.providers) {
                    keyMetadata = keysResponse;
                }
                updateKeySelect();
                addKeySection.style.display = 'none';
                await refreshModels();
            } catch (err) {
                errorHint.textContent = err.message || 'Failed to save key.';
                errorHint.style.display = 'block';
            }
        });

        modelSelect.addEventListener('change', () => {
            if (modelSelect.value) {
                formState.model = modelSelect.value;
            }
        });

        modelSearch.addEventListener('input', updateModelSelect);

        modelRefreshBtn.addEventListener('click', () => {
            refreshModels();
        });

        baseInput.addEventListener('input', () => {
            formState.baseUrl = baseInput.value.trim();
            baseUrlTouched = true;
            updateLocalHint();
        });

        nanoLegacyToggle.addEventListener('change', () => {
            applyNanoLegacy(nanoLegacyToggle.checked);
            refreshModels();
        });

        tempInput.addEventListener('input', () => {
            formState.temperature = tempInput.value;
        });

        tokenInput.addEventListener('input', () => {
            formState.maxOutputTokens = tokenInput.value;
        });

        timeoutInput.addEventListener('input', () => {
            formState.timeoutMs = timeoutInput.value;
        });

        retryInput.addEventListener('input', () => {
            formState.maxRetries = retryInput.value;
        });

        vaultBtn.addEventListener('click', async () => {
            if (!vaultInput.value) return;
            errorHint.style.display = 'none';
            try {
                await settingsApi.unlockVault(vaultInput.value);
                security.vaultUnlocked = true;
                updateVaultSection();
                await refreshModels();
            } catch (err) {
                errorHint.textContent = err.message || 'Failed to unlock vault.';
                errorHint.style.display = 'block';
            } finally {
                vaultInput.value = '';
            }
        });

        localRetry.addEventListener('click', () => {
            refreshModels();
        });

        localHelp.addEventListener('click', () => {
            const provider = formState.provider;
            let message = 'Make sure your local provider is running and reachable.\n\n';
            if (provider === 'lmstudio') {
                message += 'Open LM Studio, load a model, and keep it running.\n';
                message += 'Default URL: http://localhost:1234';
            } else if (provider === 'ollama') {
                message += 'Start the Ollama service and pull a model.\n';
                message += 'Default URL: http://localhost:11434';
            } else if (provider === 'jan') {
                message += 'Start Jan, load a model, and enable the API server.\n';
                message += 'Default URL: http://localhost:1234';
            } else if (provider === 'koboldcpp') {
                message += 'Start KoboldCPP with the API server enabled.\n';
                message += 'Default URL: http://localhost:1234';
            } else {
                message += 'Check that your local server is running and that the Base URL is correct.';
            }
            alert(message);
        });

        confirmBtn.addEventListener('click', async () => {
            errorHint.style.display = 'none';
            if (!formState.provider || !formState.model) {
                errorHint.textContent = 'Provider and model are required.';
                errorHint.style.display = 'block';
                return;
            }
            if (PROVIDERS_REQUIRE_KEY.has(formState.provider) && !formState.keyRef) {
                errorHint.textContent = 'API key is required for this provider.';
                errorHint.style.display = 'block';
                return;
            }

            const payload = {
                provider: formState.provider,
                model: formState.model,
                apiKeyRef: formState.keyRef || null,
                baseUrl: formState.baseUrl || null
            };

            const temperature = parseFloat(formState.temperature);
            if (!Number.isNaN(temperature)) {
                payload.temperature = temperature;
            }
            const maxTokens = parseInt(formState.maxOutputTokens, 10);
            if (!Number.isNaN(maxTokens)) {
                payload.maxOutputTokens = maxTokens;
            }
            const timeoutMs = parseInt(formState.timeoutMs, 10);
            if (!Number.isNaN(timeoutMs)) {
                payload.timeoutMs = timeoutMs;
            }
            const maxRetries = parseInt(formState.maxRetries, 10);
            if (!Number.isNaN(maxRetries)) {
                payload.maxRetries = maxRetries;
            }

            try {
                confirmBtn.disabled = true;
                confirmBtn.textContent = 'Saving...';
                if (!agentId && allowDraft && onSaveDraft) {
                    await onSaveDraft(payload);
                    closeWithCallback({ saved: true });
                    return;
                }
                const endpointSnapshot = {
                    provider: formState.provider,
                    model: formState.model,
                    keyRef: formState.keyRef
                };
                const nowWired = isEndpointWired(endpointSnapshot);
                const modelChanged = initialEndpointSnapshot.provider !== endpointSnapshot.provider
                    || initialEndpointSnapshot.model !== endpointSnapshot.model;
                const shouldSendIntro = nowWired && (!initialWired || modelChanged);
                await agentEndpointsApi.save(agentId, payload);
                await loadAgentStatuses();
                if (shouldSendIntro) {
                    const reason = !initialWired ? 'initial wiring' : 'model change';
                    const successMessage = !initialWired
                        ? `Connected ${name} to ${formState.provider}.`
                        : `Updated ${name} model.`;
                    notificationStore.push(
                        'success',
                        'workbench',
                        successMessage,
                        `Model: ${formState.model}`,
                        'social',
                        false,
                        '',
                        null,
                        'agents'
                    );
                    closeWithCallback({ saved: true });
                    void createAgentIntroIssue(agent || { name }, endpointSnapshot, reason);
                    return;
                } else {
                    notificationStore.success(`Saved endpoint settings for ${name}`, 'workbench');
                }
                closeWithCallback({ saved: true });
            } catch (err) {
                errorHint.textContent = err.message || 'Failed to save agent settings.';
                errorHint.style.display = 'block';
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Save';
            }
        });

        cancelBtn.addEventListener('click', () => closeWithCallback({ saved: false }));

        advancedToggle.addEventListener('click', () => {
            const isOpen = advancedBody.style.display !== 'none';
            const nextOpen = !isOpen;
            advancedBody.style.display = nextOpen ? 'flex' : 'none';
            advancedToggle.textContent = nextOpen ? 'Hide advanced' : 'Advanced settings';
            localStorage.setItem('control-room:agent-settings-advanced', nextOpen ? 'open' : 'closed');
        });

        if (!agentId && !allowDraft) {
            errorHint.textContent = 'Agent id missing.';
            errorHint.style.display = 'block';
        } else {
            loadInitialData();
        }
    }

    async function createAgentIntroIssue(agent, endpoint, reason) {
        if (!agent) return null;
        const name = agent.name || 'Agent';
        const role = agent.role || 'role';
        const provider = endpoint?.provider || 'provider';
        const model = endpoint?.model || 'model';
        const prompt = buildGreetingPrompt(agent);
        const reasonLine = reason ? `Trigger: ${reason}` : '';
        const body = [
            `Intro from ${name}.`,
            `${name} (${role}) is now wired to ${provider}${model ? ` / ${model}` : ''}.`,
            reasonLine
        ].filter(Boolean).join('\n');

        try {
            const issue = await issueApi.create({
                title: `Agent intro: ${name}`,
                body,
                openedBy: name,
                tags: ['agent-intro']
            });
            if (issue && issue.id) {
                notificationStore.issueCreated(issue.id, issue.title, issue.openedBy, issue.assignedTo);
                if (agent.id) {
                    try {
                        const reply = await api('/api/ai/chat', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ message: prompt, agentId: agent.id })
                        });
                        if (reply && reply.content) {
                            await issueApi.addComment(issue.id, {
                                author: name,
                                body: reply.content
                            });
                            notificationStore.issueCommentAdded(issue.id, name);
                            await refreshIssueModal(issue.id);
                        } else {
                            notificationStore.warning(`Greeting response was empty for ${name}`, 'workbench');
                        }
                    } catch (err) {
                        const errorMessage = `Greeting failed: ${err.message}`;
                        log(`Failed to fetch greeting for ${name}: ${err.message}`, 'warning');
                        notificationStore.warning(errorMessage, 'workbench');
                        try {
                            await issueApi.addComment(issue.id, {
                                author: 'system',
                                body: errorMessage
                            });
                            notificationStore.issueCommentAdded(issue.id, 'system');
                            await refreshIssueModal(issue.id);
                        } catch (commentErr) {
                            log(`Failed to record greeting error: ${commentErr.message}`, 'warning');
                        }
                    }
                }
            }
            return issue;
        } catch (err) {
            log(`Failed to create intro issue for ${name}: ${err.message}`, 'warning');
            return null;
        }
    }

    async function refreshIssueModal(issueId) {
        if (!state.issueModal.isOpen || state.issueModal.issueId !== issueId) return;
        state.issueModal.isLoading = true;
        state.issueModal.error = null;
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

        const inviteAllRow = document.createElement('label');
        inviteAllRow.className = 'conference-invite-actions';
        const inviteAllCheckbox = document.createElement('input');
        inviteAllCheckbox.type = 'checkbox';
        inviteAllCheckbox.className = 'conference-agent-checkbox';
        const inviteAllText = document.createElement('span');
        inviteAllText.textContent = 'Invite all agents';
        inviteAllRow.appendChild(inviteAllCheckbox);
        inviteAllRow.appendChild(inviteAllText);
        body.appendChild(inviteAllRow);

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
            inviteAllCheckbox.checked = invitedCount > 0 && invitedCount === selections.size;
            confirmBtn.disabled = invitedCount === 0;
        };

        inviteAllCheckbox.addEventListener('change', () => {
            const shouldInvite = inviteAllCheckbox.checked;
            selections.forEach(item => {
                item.invited.checked = shouldInvite;
                if (!shouldInvite && item.lead.checked) {
                    item.invited.checked = true;
                }
            });
            updateStartState();
        });

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
            showConferenceModeModal({
                agenda,
                invited,
                moderators: leaders
            });
        });
    }

    function showConferenceModeModal(config) {
        const invited = Array.isArray(config?.invited) ? config.invited.slice() : [];
        const moderators = Array.isArray(config?.moderators) ? config.moderators.slice() : [];
        const agenda = config?.agenda || '';
        const mutedIds = new Set();
        const chatLog = [];

        const { panel, actions, body, close } = createWorkbenchPanelShell('Conference');
        panel.classList.add('conference-mode-modal');

        const header = document.createElement('div');
        header.className = 'conference-header';

        const headerLeft = document.createElement('div');
        headerLeft.className = 'conference-title';
        headerLeft.textContent = agenda ? `Agenda: ${agenda}` : 'No agenda set';

        const headerActions = document.createElement('div');
        headerActions.className = 'conference-actions';

        const btnCreateIssue = document.createElement('button');
        btnCreateIssue.type = 'button';
        btnCreateIssue.className = 'conference-action-btn';
        btnCreateIssue.textContent = 'Create Issue from Chat';
        btnCreateIssue.addEventListener('click', () => {
            showComingSoonModal('Create Issue', 'Conference transcript -> issue creation is coming soon.');
        });

        const btnManage = document.createElement('button');
        btnManage.type = 'button';
        btnManage.className = 'conference-action-btn';
        btnManage.textContent = 'Manage Attendees';
        btnManage.addEventListener('click', () => {
            showConferenceManageModal(invited, renderAttendees);
        });

        headerActions.appendChild(btnCreateIssue);
        headerActions.appendChild(btnManage);

        header.appendChild(headerLeft);
        header.appendChild(headerActions);
        body.appendChild(header);

        const layout = document.createElement('div');
        layout.className = 'conference-layout';

        const attendeesPane = document.createElement('div');
        attendeesPane.className = 'conference-attendees';

        const attendeesTitle = document.createElement('div');
        attendeesTitle.className = 'conference-attendees-title';
        attendeesTitle.textContent = `Attendees (${invited.length})`;
        attendeesPane.appendChild(attendeesTitle);

        const attendeesList = document.createElement('div');
        attendeesList.className = 'conference-attendees-list';
        attendeesPane.appendChild(attendeesList);

        const chatPane = document.createElement('div');
        chatPane.className = 'conference-chat';

        const chatHistory = document.createElement('div');
        chatHistory.className = 'conference-chat-history';

        const chatHint = document.createElement('div');
        chatHint.className = 'conference-chat-hint';
        chatHint.textContent = 'Conference chat is not wired yet. Messages are local for now.';

        const chatInputRow = document.createElement('div');
        chatInputRow.className = 'conference-chat-input-row';
        const chatInput = document.createElement('textarea');
        chatInput.className = 'conference-chat-input';
        chatInput.rows = 3;
        chatInput.placeholder = 'Type a message to the room...';
        const chatSend = document.createElement('button');
        chatSend.type = 'button';
        chatSend.className = 'conference-chat-send';
        chatSend.textContent = 'Send';
        chatInputRow.appendChild(chatInput);
        chatInputRow.appendChild(chatSend);

        chatPane.appendChild(chatHistory);
        chatPane.appendChild(chatHint);
        chatPane.appendChild(chatInputRow);

        layout.appendChild(attendeesPane);
        layout.appendChild(chatPane);
        body.appendChild(layout);

        const renderAttendees = () => {
            attendeesTitle.textContent = `Attendees (${invited.length})`;
            attendeesList.innerHTML = '';
            if (invited.length === 0) {
                const empty = document.createElement('div');
                empty.className = 'conference-attendees-empty';
                empty.textContent = 'No attendees.';
                attendeesList.appendChild(empty);
                return;
            }
            invited.forEach(agent => {
                const row = document.createElement('div');
                row.className = 'conference-attendee';
                const avatar = document.createElement('div');
                avatar.className = 'conference-attendee-avatar';
                const avatarData = agent.avatar && agent.avatar.trim() ? agent.avatar.trim() : '';
                if (avatarData.startsWith('data:') || avatarData.startsWith('http')) {
                    const img = document.createElement('img');
                    img.src = avatarData;
                    img.alt = agent.name || 'Agent';
                    avatar.appendChild(img);
                } else if (avatarData) {
                    avatar.textContent = avatarData;
                } else {
                    avatar.textContent = agent.name ? agent.name.charAt(0).toUpperCase() : '?';
                }
                if (agent.color && !avatarData.startsWith('data:') && !avatarData.startsWith('http')) {
                    avatar.style.background = agent.color;
                }

                const info = document.createElement('div');
                info.className = 'conference-attendee-info';

                const name = document.createElement('div');
                name.className = 'conference-attendee-name';
                name.textContent = agent.name || 'Unnamed Agent';
                const meta = document.createElement('div');
                meta.className = 'conference-attendee-meta';
                const role = agent.role ? `Role: ${agent.role}` : 'Role: -';
                const isLead = moderators.some(moderator => moderator.id === agent.id);
                const leadTag = isLead ? 'Moderator' : 'Participant';
                const mutedTag = mutedIds.has(agent.id) ? 'Muted' : '';
                meta.textContent = [role, leadTag, mutedTag].filter(Boolean).join(' • ');
                if (isLead) {
                    row.classList.add('conference-attendee-moderator');
                }
                info.appendChild(name);
                info.appendChild(meta);
                row.appendChild(avatar);
                row.appendChild(info);
                attendeesList.appendChild(row);

                row.addEventListener('contextmenu', (e) => {
                    e.preventDefault();
                    showConferenceAttendeeMenu(e, agent, invited, moderators, mutedIds, renderAttendees);
                });
            });
        };

        const addChatMessage = (author, role, content) => {
            const entry = document.createElement('div');
            entry.className = `conference-chat-message ${role}`;
            const header = document.createElement('div');
            header.className = 'conference-chat-message-header';
            header.textContent = author;
            const body = document.createElement('div');
            body.className = 'conference-chat-message-body';
            body.textContent = content;
            entry.appendChild(header);
            entry.appendChild(body);
            chatHistory.appendChild(entry);
            chatHistory.scrollTop = chatHistory.scrollHeight;
        };

        const sendMessage = () => {
            const text = chatInput.value.trim();
            if (!text) return;
            chatInput.value = '';
            chatLog.push({ author: 'You', role: 'user', content: text });
            addChatMessage('user', text);
        };

        chatSend.addEventListener('click', sendMessage);
        chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });

        const exitBtn = document.createElement('button');
        exitBtn.type = 'button';
        exitBtn.className = 'workbench-panel-btn';
        exitBtn.textContent = 'Exit';
        exitBtn.addEventListener('click', close);

        const discardBtn = document.createElement('button');
        discardBtn.type = 'button';
        discardBtn.className = 'workbench-panel-btn workbench-panel-btn-danger';
        discardBtn.textContent = 'Discard Session';
        discardBtn.addEventListener('click', close);

        actions.appendChild(discardBtn);
        actions.appendChild(exitBtn);

        renderAttendees();
        setTimeout(() => chatInput.focus(), 0);
    }

    function showConferenceAttendeeMenu(event, agent, invited, moderators, mutedIds, onUpdate) {
        hideContextMenu();

        const menu = document.createElement('div');
        menu.className = 'context-menu';
        menu.style.top = `${event.clientY}px`;
        menu.style.left = `${event.clientX}px`;

        const isMuted = mutedIds.has(agent.id);
        const isModerator = moderators.some(item => item.id === agent.id);

        const actions = [
            {
                label: isMuted ? 'Unmute' : 'Mute',
                action: () => {
                    if (isMuted) mutedIds.delete(agent.id);
                    else mutedIds.add(agent.id);
                    onUpdate();
                }
            },
            {
                label: 'Kick',
                action: () => {
                    const index = invited.findIndex(item => item.id === agent.id);
                    if (index !== -1) invited.splice(index, 1);
                    const leadIndex = moderators.findIndex(item => item.id === agent.id);
                    if (leadIndex !== -1) moderators.splice(leadIndex, 1);
                    mutedIds.delete(agent.id);
                    onUpdate();
                }
            },
            {
                label: isModerator ? 'Demote from Moderator' : 'Promote to Moderator',
                action: () => {
                    if (isModerator) {
                        const idx = moderators.findIndex(item => item.id === agent.id);
                        if (idx !== -1) moderators.splice(idx, 1);
                    } else {
                        moderators.length = 0;
                        moderators.push(agent);
                    }
                    onUpdate();
                }
            }
        ];

        actions.forEach(item => {
            const row = document.createElement('div');
            row.className = 'context-menu-item';
            row.textContent = item.label;
            row.addEventListener('click', () => {
                hideContextMenu();
                item.action();
            });
            menu.appendChild(row);
        });

        document.body.appendChild(menu);
        contextMenu = menu;
    }

    function showConferenceManageModal(invited, onUpdate) {
        const agents = (state.agents.list || []).filter(agent => agent.enabled !== false);
        const { modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            'Manage Attendees',
            'Apply',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: false }
        );

        modal.classList.add('conference-manage-modal');

        const info = document.createElement('div');
        info.className = 'modal-text';
        info.textContent = 'Select agents to include in this conference.';
        body.appendChild(info);

        const list = document.createElement('div');
        list.className = 'conference-manage-list';
        body.appendChild(list);

        const invitedIds = new Set(invited.map(agent => agent.id));

        agents.forEach(agent => {
            const row = document.createElement('label');
            row.className = 'conference-manage-row';
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = invitedIds.has(agent.id);
            const name = document.createElement('span');
            name.textContent = agent.name || 'Unnamed Agent';
            const role = document.createElement('span');
            role.className = 'conference-manage-role';
            role.textContent = agent.role || 'role';
            row.appendChild(checkbox);
            row.appendChild(name);
            row.appendChild(role);
            list.appendChild(row);
        });

        confirmBtn.addEventListener('click', () => {
            invited.length = 0;
            const rows = Array.from(list.querySelectorAll('.conference-manage-row'));
            rows.forEach((row, index) => {
                const checkbox = row.querySelector('input');
                if (checkbox && checkbox.checked) {
                    invited.push(agents[index]);
                }
            });
            if (typeof onUpdate === 'function') {
                onUpdate();
            }
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

        confirmBtn.addEventListener('click', async () => {
            try {
                await agentApi.setStatus(agent.id, false);
                log(`Retired ${name}`, 'warning');
                await loadAgents();
            } catch (err) {
                log(`Failed to retire ${name}: ${err.message}`, 'error');
            } finally {
                close();
            }
        });
    }

    async function showRetiredAgentsModal() {
        const { modal, body, confirmBtn, close } = createModalShell(
            'Retired Agents',
            'Close',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: true }
        );

        modal.classList.add('retired-agent-modal');
        confirmBtn.style.display = 'none';

        const info = document.createElement('div');
        info.className = 'modal-text';
        info.textContent = 'Retired agents are hidden from the active roster.';
        body.appendChild(info);

        const list = document.createElement('div');
        list.className = 'retired-agent-list';
        body.appendChild(list);

        try {
            const agents = await agentApi.listAll();
            const retired = (agents || []).filter(agent => agent.enabled === false);
            if (retired.length === 0) {
                const empty = document.createElement('div');
                empty.className = 'agent-empty';
                empty.textContent = 'No retired agents.';
                list.appendChild(empty);
                return;
            }

            retired.forEach(agent => {
                const row = document.createElement('div');
                row.className = 'retired-agent-row';

                const name = document.createElement('div');
                name.className = 'retired-agent-name';
                name.textContent = agent.name || 'Unnamed Agent';

                const role = document.createElement('div');
                role.className = 'retired-agent-role';
                role.textContent = agent.role || 'role';

                const reactivate = document.createElement('button');
                reactivate.type = 'button';
                reactivate.className = 'modal-btn modal-btn-secondary';
                reactivate.textContent = 'Reactivate';
                reactivate.addEventListener('click', async () => {
                    reactivate.disabled = true;
                    try {
                        await agentApi.setStatus(agent.id, true);
                        await loadAgents();
                        row.remove();
                    } catch (err) {
                        log(`Failed to reactivate ${agent.name}: ${err.message}`, 'error');
                        reactivate.disabled = false;
                    }
                });

                row.appendChild(name);
                row.appendChild(role);
                row.appendChild(reactivate);
                list.appendChild(row);
            });
        } catch (err) {
            const error = document.createElement('div');
            error.className = 'modal-hint';
            error.textContent = `Failed to load retired agents: ${err.message}`;
            body.appendChild(error);
        }

        modal.addEventListener('click', (e) => {
            if (e.target === modal) close();
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
            state.agents.statusById = {};
            renderAgentSidebar();
            renderAgentSelector();
            await loadAgentStatuses();
            log(`Loaded ${state.agents.list.length} agent(s)`, 'info');
        } catch (err) {
            state.agents.list = [];
            state.agents.statusById = {};
            renderAgentSidebar();
            renderAgentSelector();
            log(`Failed to load agents: ${err.message}`, 'error');
        }
    }

    async function loadAgentStatuses() {
        const agents = state.agents.list || [];
        if (agents.length === 0) return;

        let endpoints = {};
        try {
            endpoints = await agentEndpointsApi.list();
        } catch (err) {
            log(`Failed to load agent endpoints: ${err.message}`, 'warning');
        }

        const statusById = { ...state.agents.statusById };
        const reachabilityCache = new Map();

        const resolveEndpoint = (agent) => endpoints?.[agent.id] || agent.endpoint || null;
        const endpointConfigured = (endpoint) => {
            if (!endpoint || !endpoint.provider) return false;
            if (PROVIDERS_REQUIRE_KEY.has(endpoint.provider) && !endpoint.apiKeyRef) return false;
            return true;
        };

        const checkReachability = async (endpoint) => {
            const key = [
                endpoint.provider,
                endpoint.baseUrl || '',
                endpoint.apiKeyRef || ''
            ].join('|');
            if (reachabilityCache.has(key)) {
                return reachabilityCache.get(key);
            }
            const promise = providerApi.listModels(endpoint.provider, endpoint.baseUrl, endpoint.apiKeyRef)
                .then(() => true)
                .catch(() => false);
            reachabilityCache.set(key, promise);
            return promise;
        };

        agents.forEach(agent => {
            const endpoint = resolveEndpoint(agent);
            if (!endpointConfigured(endpoint)) {
                statusById[agent.id] = 'unconfigured';
            } else if (!endpoint.model) {
                statusById[agent.id] = 'incomplete';
            } else {
                statusById[agent.id] = 'checking';
            }
        });
        state.agents.statusById = statusById;
        renderAgentSidebar();

        for (const agent of agents) {
            const endpoint = resolveEndpoint(agent);
            if (!endpointConfigured(endpoint) || !endpoint?.model) continue;
            const reachable = await checkReachability(endpoint);
            statusById[agent.id] = reachable ? 'ready' : 'unreachable';
        }

        state.agents.statusById = statusById;
        renderAgentSidebar();
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

    const workbenchChats = new Map();

    // Chat
    async function handleWitnessClick(memoryId, witness) {
        if (!memoryId || !witness) return;
        try {
            const ev = await memoryApi.getEvidence(memoryId, witness);
            const preview = ev && ev.text ? ev.text : JSON.stringify(ev);
            log(`Evidence ${witness}: ${preview}`, 'info');
            notificationStore.info(`Loaded evidence ${witness}`, 'editor');
        } catch (err) {
            log(`Failed to load evidence ${witness}: ${err.message}`, 'error');
            notificationStore.error(`Failed to load evidence ${witness}: ${err.message}`, 'editor');
        }
    }

    function addChatMessage(role, content, meta = {}) {
        const msg = document.createElement('div');
        msg.className = `chat-message ${role}`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'chat-message-content';
        contentDiv.textContent = content;
        msg.appendChild(contentDiv);

        const hasMeta = meta && (meta.repLevel || meta.escalated || (meta.witnesses && meta.witnesses.length));
        if (hasMeta) {
            const metaDiv = document.createElement('div');
            metaDiv.className = 'chat-message-meta';
            if (meta.repLevel) {
                const level = document.createElement('span');
                level.className = 'chat-badge';
                level.textContent = `R${meta.repLevel}${meta.escalated ? ' ↑' : ''}`;
                metaDiv.appendChild(level);
            } else if (meta.escalated) {
                const escalated = document.createElement('span');
                escalated.className = 'chat-badge';
                escalated.textContent = 'Escalated';
                metaDiv.appendChild(escalated);
            }

            if (Array.isArray(meta.witnesses)) {
                meta.witnesses.forEach((w) => {
                    const chip = document.createElement('button');
                    chip.type = 'button';
                    chip.className = 'witness-chip';
                    chip.textContent = `witness ${w}`;
                    chip.addEventListener('click', () => {
                        handleWitnessClick(meta.memoryId || state.chat.memoryId, w);
                    });
                    metaDiv.appendChild(chip);
                });
            }
            msg.appendChild(metaDiv);
        }

        elements.chatHistory.appendChild(msg);
        elements.chatHistory.scrollTop = elements.chatHistory.scrollHeight;
    }

    function getWorkbenchChatLog(agentId) {
        if (!workbenchChats.has(agentId)) {
            workbenchChats.set(agentId, []);
        }
        return workbenchChats.get(agentId);
    }

    function appendWorkbenchChatMessage(container, role, content) {
        const msg = document.createElement('div');
        msg.className = `chat-message ${role}`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'chat-message-content';
        contentDiv.textContent = content;

        msg.appendChild(contentDiv);
        container.appendChild(msg);
        container.scrollTop = container.scrollHeight;
    }

    function showWorkbenchChatModal(agent) {
        if (!agent || !agent.id) return;

        setSelectedAgentId(agent.id);

        const { modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            `Chat with ${agent.name || 'Agent'}`,
            'Close',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: true }
        );

        modal.classList.add('workbench-chat-modal');

        if (cancelBtn) {
            cancelBtn.style.display = 'none';
        }

        const meta = document.createElement('div');
        meta.className = 'modal-text';
        meta.textContent = agent.role ? `Role: ${agent.role}` : 'Direct chat';
        body.appendChild(meta);

        const chatBody = document.createElement('div');
        chatBody.className = 'workbench-chat-body';

        const history = document.createElement('div');
        history.className = 'workbench-chat-history';

        const inputRow = document.createElement('div');
        inputRow.className = 'workbench-chat-input-row';

        const input = document.createElement('textarea');
        input.className = 'modal-textarea workbench-chat-input';
        input.rows = 3;
        input.placeholder = `Message ${agent.name || 'agent'}...`;

        const sendBtn = document.createElement('button');
        sendBtn.type = 'button';
        sendBtn.className = 'modal-btn modal-btn-primary workbench-chat-send';
        sendBtn.textContent = 'Send';

        inputRow.appendChild(input);
        inputRow.appendChild(sendBtn);

        chatBody.appendChild(history);
        chatBody.appendChild(inputRow);
        body.appendChild(chatBody);

        const log = getWorkbenchChatLog(agent.id);
        log.forEach(entry => {
            appendWorkbenchChatMessage(history, entry.role, entry.content);
        });

        const sendMessage = async () => {
            const message = input.value.trim();
            if (!message) return;
            input.value = '';
            sendBtn.disabled = true;

            appendWorkbenchChatMessage(history, 'user', message);
            log.push({ role: 'user', content: message });

            try {
                const response = await api('/api/ai/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message, agentId: agent.id })
                });
                const reply = response.content || 'No response.';
                appendWorkbenchChatMessage(history, 'assistant', reply);
                log.push({ role: 'assistant', content: reply });
            } catch (err) {
                const errorMessage = 'Sorry, I encountered an error. Please try again.';
                appendWorkbenchChatMessage(history, 'assistant', errorMessage);
                log.push({ role: 'assistant', content: errorMessage });
            } finally {
                sendBtn.disabled = false;
            }
        };

        sendBtn.addEventListener('click', sendMessage);
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });

        setTimeout(() => input.focus(), 0);
    }

    function updateChatMemoryBadge(meta) {
        if (!elements.chatMemoryBadge) return;
        if (meta && meta.repLevel) {
            elements.chatMemoryBadge.textContent = `R${meta.repLevel}${meta.escalated ? ' ↑' : ''}`;
            elements.chatMemoryBadge.classList.remove('hidden');
        } else {
            elements.chatMemoryBadge.classList.add('hidden');
        }
        if (elements.chatEscalate) {
            elements.chatEscalate.disabled = !state.chat.lastPayload;
        }
    }

    async function sendChatMessage(reroll = false) {
        const draftMessage = elements.chatInput.value.trim();
        const previous = state.chat.lastPayload;
        const message = reroll && previous ? previous.message : draftMessage;
        if (!message) return;

        if (!reroll) {
            elements.chatInput.value = '';
            addChatMessage('user', message);
            state.chat.lastPayload = {
                message,
                agentId: state.agents.selectedId || null,
                memoryId: state.chat.memoryId
            };
        }

        elements.chatSend.disabled = true;

        log(`Chat: User message sent${reroll ? ' (reroll)' : ''}`, 'info');

        try {
            const payload = {
                message,
                memoryId: state.chat.memoryId || undefined,
                reroll
            };
            const agentId = (reroll && previous ? previous.agentId : state.agents.selectedId) || null;
            if (agentId) {
                payload.agentId = agentId;
            }
            if (reroll) {
                notificationStore.info('Requesting more evidence (reroll)...', 'editor');
            }
            const response = await api('/api/ai/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const meta = {
                memoryId: response.memoryId || payload.memoryId,
                repLevel: response.repLevel,
                escalated: !!response.escalated,
                witnesses: response.witnesses
            };
            state.chat.lastResponseMeta = meta;
            updateChatMemoryBadge(meta);

            const reply = response && response.content ? response.content : 'No response.';
            addChatMessage('assistant', reply, meta);
            log(`Chat: AI response received${meta.escalated ? ' (escalated)' : ''}`, 'success');
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
    function handleToggleModeClick() {
        if (state.viewMode.current === 'workbench') {
            setViewMode('editor');
        } else if (state.viewMode.current === 'editor') {
            setViewMode('workbench');
        } else {
            setViewMode('editor');
        }
    }

    function initSidebarButtons() {
        if (elements.btnToggleMode) {
            elements.btnToggleMode.addEventListener('click', handleToggleModeClick);
        }

        if (elements.btnToggleModeTop) {
            elements.btnToggleModeTop.addEventListener('click', handleToggleModeClick);
        }

        if (elements.btnOpenIssues) {
            elements.btnOpenIssues.addEventListener('click', () => {
                openIssueBoardPanel();
            });
        }

        if (elements.btnStartConference) {
            elements.btnStartConference.addEventListener('click', () => {
                showConferenceInviteModal();
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

        const btnRetiredAgents = document.getElementById('btn-retired-agents');
        if (btnRetiredAgents) {
            btnRetiredAgents.addEventListener('click', () => {
                showRetiredAgentsModal();
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

        if (elements.btnCreateIssue) {
            elements.btnCreateIssue.addEventListener('click', () => {
                showIssueCreateModal();
            });
        }

        // Chat
        elements.chatSend.addEventListener('click', sendChatMessage);
        elements.chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendChatMessage();
            }
        });

        if (elements.chatMemorySet) {
            elements.chatMemorySet.addEventListener('click', () => {
                const value = elements.chatMemoryId.value.trim();
                state.chat.memoryId = value || null;
                state.chat.lastPayload = null;
                updateChatMemoryBadge(null);
                if (value) {
                    log(`Bound chat to memory ${value}`, 'info');
                    notificationStore.success(`Chat bound to memory ${value}`, 'editor');
                } else {
                    log('Chat memory binding cleared', 'info');
                    notificationStore.info('Chat memory binding cleared', 'editor');
                }
            });
        }

        if (elements.chatEscalate) {
            elements.chatEscalate.addEventListener('click', () => {
                if (!state.chat.lastPayload) {
                    notificationStore.warning('Send a message first before requesting more evidence.', 'editor');
                    return;
                }
                sendChatMessage(true);
            });
            elements.chatEscalate.disabled = true;
        }

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

    function initMemoryModeratorControls() {
        const memIdInput = document.getElementById('moderator-memory-id');
        const versionInput = document.getElementById('moderator-version-id');
        const feedback = document.getElementById('moderator-feedback');
        const archiveInput = document.getElementById('decay-archive-days');
        const expireInput = document.getElementById('decay-expire-days');
        const pruneCheckbox = document.getElementById('decay-prune-r5');
        const decayStatus = document.getElementById('decay-status');
        const intervalInput = document.getElementById('decay-interval-min');
        const dryRunCheckbox = document.getElementById('decay-dry-run');
        const decayReport = document.getElementById('decay-report');
        const notifyCheckbox = document.getElementById('decay-notify-on-run');
        const setFeedback = (text, level = 'info') => {
            if (feedback) {
                feedback.textContent = text;
                feedback.dataset.level = level;
            }
        };

        const setDecayStatus = (text) => {
            if (decayStatus) {
                decayStatus.textContent = text;
            }
        };

        const showDecayReport = (res, dryRun) => {
            if (!decayReport) return;
            const archived = res.archived || [];
            const expired = res.expired || [];
            const prunable = res.prunable || [];
            decayReport.innerHTML = `
                <div><strong>Decay ${dryRun ? '(dry run)' : ''} results:</strong></div>
                <div>Archived: ${archived.length}, Expired: ${expired.length}, Prunable R5: ${prunable.length}, Locked skipped: ${res.lockedItems || 0}</div>
                <div>Pruned events: ${res.prunedEvents || 0}</div>
                ${dryRun && prunable.length ? `<div>Prunable IDs:</div><ul>${prunable.slice(0, 10).map(id => `<li>${id}</li>`).join('')}${prunable.length > 10 ? '<li>...</li>' : ''}</ul>` : ''}
            `;
        };

        const loadDecayStatus = async () => {
            try {
                const status = await memoryApi.getDecayStatus();
                const minutes = status.intervalMs ? Math.round(status.intervalMs / 60000) : null;
                const archived = status.archived ? status.archived.length : 0;
                const expired = status.expired ? status.expired.length : 0;
                const pruned = status.prunedEvents || 0;
                const locked = status.lockedItems || 0;
                const lastRun = status.lastRunAt ? new Date(status.lastRunAt).toLocaleString() : 'never';
                setDecayStatus(`Schedule: ${minutes || 'n/a'} min | Last run: ${lastRun} | Archived: ${archived} | Expired: ${expired} | Pruned: ${pruned} | Locked skipped: ${locked}`);
                if (intervalInput && minutes) intervalInput.value = minutes;
                if (archiveInput && status.archiveAfterMs) archiveInput.value = Math.round(status.archiveAfterMs / (24 * 60 * 60 * 1000));
                if (expireInput && status.expireAfterMs) expireInput.value = Math.round(status.expireAfterMs / (24 * 60 * 60 * 1000));
                if (pruneCheckbox) pruneCheckbox.checked = Boolean(status.pruneExpiredR5);
                if (notifyCheckbox && status.settings && typeof status.settings.notifyOnRun === 'boolean') {
                    notifyCheckbox.checked = status.settings.notifyOnRun;
                }
                if (dryRunCheckbox && status.settings && typeof status.settings.dryRun === 'boolean') {
                    dryRunCheckbox.checked = status.settings.dryRun;
                }
            } catch (err) {
                setDecayStatus(`Failed to load decay status: ${err.message}`);
            }
        };

        const btnPromote = document.getElementById('btn-promote-memory');
        if (btnPromote) {
            btnPromote.addEventListener('click', async () => {
                const memId = memIdInput.value.trim();
                const verId = versionInput.value.trim();
                if (!memId || !verId) {
                    setFeedback('Memory ID and Version ID are required to promote.', 'error');
                    notificationStore.warning('Memory ID and Version ID required to promote.', 'workbench');
                    return;
                }
                try {
                    const res = await memoryApi.setActive(memId, verId, { reason: 'manual-promote', lockMinutes: 90 });
                    setFeedback(`Active set to ${verId} (lock until ${res.lockUntil || 'now'})`, 'success');
                    notificationStore.success(`Promoted ${memId} -> ${verId}`, 'workbench');
                } catch (err) {
                    setFeedback(`Failed to promote: ${err.message}`, 'error');
                    notificationStore.error(`Failed to promote memory: ${err.message}`, 'workbench');
                }
            });
        }

        const btnPin = document.getElementById('btn-pin-memory');
        if (btnPin) {
            btnPin.addEventListener('click', async () => {
                const memId = memIdInput.value.trim();
                if (!memId) {
                    setFeedback('Memory ID required to pin.', 'error');
                    notificationStore.warning('Memory ID required to pin.', 'workbench');
                    return;
                }
                try {
                    await memoryApi.pin(memId, 3);
                    setFeedback(`Pinned ${memId} at R3`, 'success');
                    notificationStore.success(`Pinned ${memId} at R3`, 'workbench');
                } catch (err) {
                    setFeedback(`Failed to pin: ${err.message}`, 'error');
                    notificationStore.error(`Failed to pin memory: ${err.message}`, 'workbench');
                }
            });
        }

        const btnArchive = document.getElementById('btn-archive-memory');
        if (btnArchive) {
            btnArchive.addEventListener('click', async () => {
                const memId = memIdInput.value.trim();
                if (!memId) {
                    setFeedback('Memory ID required to archive.', 'error');
                    notificationStore.warning('Memory ID required to archive.', 'workbench');
                    return;
                }
                try {
                    await memoryApi.setState(memId, 'archived');
                    setFeedback(`Archived ${memId}`, 'success');
                    notificationStore.success(`Archived ${memId}`, 'workbench');
                } catch (err) {
                    setFeedback(`Failed to archive: ${err.message}`, 'error');
                    notificationStore.error(`Failed to archive memory: ${err.message}`, 'workbench');
                }
            });
        }

        const btnDecay = document.getElementById('btn-run-decay');
        if (btnDecay) {
            btnDecay.addEventListener('click', async () => {
                const archiveDays = archiveInput && archiveInput.value ? parseInt(archiveInput.value, 10) : 14;
                const expireDays = expireInput && expireInput.value ? parseInt(expireInput.value, 10) : 30;
                const pruneR5 = pruneCheckbox ? pruneCheckbox.checked : false;
                const dryRun = dryRunCheckbox ? dryRunCheckbox.checked : false;
                const notifyOnRun = notifyCheckbox ? notifyCheckbox.checked : true;
                try {
                    const res = await memoryApi.decay({
                        archiveAfterDays: archiveDays,
                        expireAfterDays: expireDays,
                        pruneExpiredR5: pruneR5,
                        dryRun,
                        notifyOnRun
                    });
                    const msg = `Decay ${dryRun ? '(dry run)' : ''} done. Archived: ${res.archived.length}, Expired: ${res.expired.length}, Pruned events: ${res.prunedEvents}, Locked skipped: ${res.lockedItems}`;
                    setFeedback(msg, dryRun ? 'info' : 'success');
                    notificationStore.success(msg, 'workbench');
                    showDecayReport(res, dryRun);
                    if (!dryRun) {
                        loadDecayStatus();
                    }
                } catch (err) {
                    setFeedback(`Decay failed: ${err.message}`, 'error');
                    notificationStore.error(`Decay failed: ${err.message}`, 'workbench');
                }
            });
        }

        const btnSaveConfig = document.getElementById('btn-save-decay-config');
        if (btnSaveConfig) {
            btnSaveConfig.addEventListener('click', async () => {
                const interval = intervalInput && intervalInput.value ? parseInt(intervalInput.value, 10) : 360;
                const archiveDays = archiveInput && archiveInput.value ? parseInt(archiveInput.value, 10) : 14;
                const expireDays = expireInput && expireInput.value ? parseInt(expireInput.value, 10) : 30;
                const pruneR5 = pruneCheckbox ? pruneCheckbox.checked : false;
                const notifyOnRun = notifyCheckbox ? notifyCheckbox.checked : true;
                const dryRunScheduled = dryRunCheckbox ? dryRunCheckbox.checked : false;
                try {
                    await memoryApi.saveDecayConfig({
                        intervalMinutes: interval,
                        archiveAfterDays: archiveDays,
                        expireAfterDays: expireDays,
                        pruneExpiredR5: pruneR5,
                        notifyOnRun,
                        dryRun: dryRunScheduled
                    });
                    const msg = `Decay config saved. Interval: ${interval}m, archive: ${archiveDays}d, expire: ${expireDays}d, prune R5: ${pruneR5}`;
                    setFeedback(msg, 'success');
                    notificationStore.success(msg, 'workbench');
                    loadDecayStatus();
                } catch (err) {
                    setFeedback(`Failed to save decay config: ${err.message}`, 'error');
                    notificationStore.error(`Failed to save decay config: ${err.message}`, 'workbench');
                }
            });
        }

        const btnDownloadReport = document.getElementById('btn-download-decay-report');
        if (btnDownloadReport) {
            btnDownloadReport.addEventListener('click', async () => {
                const archiveDays = archiveInput && archiveInput.value ? parseInt(archiveInput.value, 10) : 14;
                const expireDays = expireInput && expireInput.value ? parseInt(expireInput.value, 10) : 30;
                const pruneR5 = pruneCheckbox ? pruneCheckbox.checked : false;
                try {
                    const res = await memoryApi.downloadDecayReport({
                        archiveAfterDays: archiveDays,
                        expireAfterDays: expireDays,
                        pruneExpiredR5: pruneR5
                    });
                    const blob = new Blob([JSON.stringify(res, null, 2)], { type: 'application/json' });
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = `decay-report-${Date.now()}.json`;
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    URL.revokeObjectURL(url);
                    notificationStore.success('Dry run report downloaded.', 'workbench');
                } catch (err) {
                    notificationStore.error(`Failed to download report: ${err.message}`, 'workbench');
                }
            });
        }

        loadDecayStatus();
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
        initMemoryModeratorControls();
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
