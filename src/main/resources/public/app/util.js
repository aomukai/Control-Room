// App utilities (refactor split)
(function() {
    'use strict';

    const escapeHtml = window.modals ? window.modals.escapeHtml : null;

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

    const STOP_HOOK_TYPES = [
        'question',
        'conflict',
        'uncertainty',
        'scope-exceeded',
        'approval-required',
        'error',
        'budget-limit'
    ];

    function buildStopHookPreamble(agent) {
        if (!agent) return '';
        const lines = [
            'System: Stop hooks are mandatory.',
            'If you hit a stop condition, respond with "STOP_HOOK: <type>" as the first line, then a short reason.',
            `Stop hook types: ${STOP_HOOK_TYPES.join(', ')}.`
        ];

        const canonicalizeRole = window.canonicalizeRole;
        const roleKey = canonicalizeRole ? canonicalizeRole(agent.role) : null;
        if (roleKey === 'assistant') {
            lines.push('Assistant constraint: Do not author creative canon unless explicitly tasked.');
            lines.push('If a request would create or change canon without explicit confirmation, respond with "STOP_HOOK: approval-required" and ask for confirmation.');
        }

        const agentNotes = agent.instructions || (agent.personality && agent.personality.baseInstructions) || '';
        const defaultCharters = window.DEFAULT_ROLE_CHARTERS || {};
        const roleNotes = !agentNotes && roleKey ? defaultCharters[roleKey] : '';
        if (agentNotes || roleNotes) {
            lines.push(`Role notes: ${agentNotes || roleNotes}`);
        }

        return lines.join('\n');
    }

    function buildChatPrompt(message, agent) {
        const preamble = buildStopHookPreamble(agent);
        if (!preamble) return message;
        return `${preamble}\n\nUser message:\n${message}`;
    }

    function extractStopHook(content) {
        const text = String(content || '');
        const lines = text.split('\n');
        const first = lines[0] ? lines[0].trim() : '';
        const match = first.match(/^\[?STOP[ _-]?HOOK\s*:\s*([a-z-]+)\]?\s*(.*)$/i);
        if (!match) {
            return { content: text, stopHook: null, stopHookDetail: '' };
        }

        const hook = match[1].toLowerCase();
        if (!STOP_HOOK_TYPES.includes(hook)) {
            return { content: text, stopHook: null, stopHookDetail: '' };
        }

        const inlineDetail = match[2] ? match[2].trim() : '';
        const remaining = lines.slice(1).join('\n').trim();
        const cleaned = remaining || inlineDetail;
        return { content: cleaned, stopHook: hook, stopHookDetail: inlineDetail };
    }

    if (escapeHtml) {
        window.escapeHtml = escapeHtml;
    }
    window.normalizeWorkspacePath = normalizeWorkspacePath;
    window.formatTimestamp = formatTimestamp;
    window.formatRelativeTime = formatRelativeTime;
    window.getStatusClass = getStatusClass;
    window.getPriorityClass = getPriorityClass;
    window.formatStatus = formatStatus;
    window.buildChatPrompt = buildChatPrompt;
    window.extractStopHook = extractStopHook;
})();
