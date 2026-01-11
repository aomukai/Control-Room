// App utilities (refactor split)
(function() {
    'use strict';

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

    function formatTimestamp(timestamp) {
        const date = new Date(timestamp);
        const pad = (value) => String(value).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
            + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
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

    function formatStatus(status) {
        switch (status) {
            case 'open': return 'Open';
            case 'closed': return 'Closed';
            case 'waiting-on-user': return 'Waiting';
            default: return status;
        }
    }

    window.normalizeWorkspacePath = normalizeWorkspacePath;
    window.formatTimestamp = formatTimestamp;
    window.formatRelativeTime = formatRelativeTime;
    window.getStatusClass = getStatusClass;
    window.getPriorityClass = getPriorityClass;
    window.formatStatus = formatStatus;
})();
