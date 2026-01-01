// Notification Store
// Client-side notification management with pub/sub pattern
(function() {
    'use strict';

    /**
     * Creates a notification store with pub/sub support.
     * @returns {Object} Notification store API
     */
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

    // Expose factory function
    window.createNotificationStore = createNotificationStore;
})();
