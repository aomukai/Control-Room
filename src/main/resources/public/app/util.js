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
        'conflict',
        'uncertainty',
        'scope-exceeded',
        'approval-required',
        'error',
        'budget-limit'
    ];
    // Some STOP_HOOK types are emitted by the server/tool gate (not by the model). We should parse and surface them,
    // but we should not include them in the model-facing STOP_HOOK_TYPES list above.
    const STOP_HOOK_PARSE_TYPES = STOP_HOOK_TYPES.concat([
        'tool_call_rejected'
    ]);

    function stripThinkingTags(content) {
        if (!content) return content;
        let cleaned = String(content);
        cleaned = cleaned.replace(/<thinking>[\s\S]*?<\/thinking>/gi, '');
        cleaned = cleaned.replace(/<think>[\s\S]*?<\/think>/gi, '');
        cleaned = cleaned.replace(/\[thinking\][\s\S]*?\[\/thinking\]/gi, '');
        cleaned = cleaned.replace(/\[think\][\s\S]*?\[\/think\]/gi, '');
        cleaned = cleaned.replace(/\[thought\][\s\S]*?\[\/thought\]/gi, '');
        cleaned = stripOrphanClosingThink(cleaned, '</thinking>');
        cleaned = stripOrphanClosingThink(cleaned, '</think>');
        cleaned = stripOrphanClosingThink(cleaned, '[/thinking]');
        cleaned = stripOrphanClosingThink(cleaned, '[/think]');
        cleaned = stripOrphanClosingThink(cleaned, '[/thought]');
        return cleaned.trim();
    }

    function stripOrphanClosingThink(content, closingTag) {
        if (!content || !closingTag) return content || '';
        const lower = content.toLowerCase();
        const tag = closingTag.toLowerCase();
        const idx = lower.indexOf(tag);
        if (idx === -1) return content;
        return content.slice(idx + closingTag.length).trim();
    }

    function buildStopHookPreamble(agent) {
        if (!agent) return '';
        const lines = [
            'System: Stop hooks are mandatory ONLY when you cannot proceed.',
            'If you cannot proceed, respond with "STOP_HOOK: <type>" as the first line, then a short reason.',
            `Stop hook types: ${STOP_HOOK_TYPES.join(', ')}.`,
            'If you can answer the user, answer normally and do NOT emit STOP_HOOK.',
            'Do not mention STOP_HOOKs or internal control flow in normal answers.'
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
        const text = stripThinkingTags(content || '');
        const lines = String(text || '').split('\n');
        const first = lines[0] ? lines[0].trim() : '';
        // Allow underscores since the server may emit types like "tool_call_rejected".
        const match = first.match(/^\[?STOP[ _-]?HOOK\s*:\s*([a-z_-]+)\]?\s*(.*)$/i);
        if (!match) {
            return { content: text, stopHook: null, stopHookDetail: '' };
        }

        const hook = match[1].toLowerCase();
        if (!STOP_HOOK_PARSE_TYPES.includes(hook)) {
            return { content: text, stopHook: null, stopHookDetail: '' };
        }

        const inlineDetail = match[2] ? match[2].trim() : '';
        const remaining = lines.slice(1).join('\n').trim();
        const cleaned = remaining || inlineDetail;
        return { content: cleaned, stopHook: hook, stopHookDetail: inlineDetail };
    }

    function renderSimpleMarkdown(text, options = {}) {
        const fallbackText = options.emptyFallback || '';
        const raw = String(text || '');
        if (!raw.trim()) {
            return fallbackText ? `<div class="markdown-empty">${escapeHtml ? escapeHtml(fallbackText) : fallbackText}</div>` : '';
        }

        const parts = raw.split('```');
        const rendered = parts.map((part, index) => {
            const isCode = index % 2 === 1;
            if (isCode) {
                const code = escapeHtml ? escapeHtml(part.trim()) : part.trim();
                return `<pre><code>${code}</code></pre>`;
            }

            let html = escapeHtml ? escapeHtml(part) : part;

            html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
            html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
            html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');
            html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
            html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
            html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');

            const lines = html.split('\n');
            let output = '';
            let inUl = false;
            let inOl = false;

            const closeLists = () => {
                if (inUl) {
                    output += '</ul>';
                    inUl = false;
                }
                if (inOl) {
                    output += '</ol>';
                    inOl = false;
                }
            };

            lines.forEach((line) => {
                if (/^\s*[-*+]\s+/.test(line)) {
                    if (!inUl) {
                        closeLists();
                        output += '<ul>';
                        inUl = true;
                    }
                    const item = line.replace(/^\s*[-*+]\s+/, '');
                    output += `<li>${item}</li>`;
                    return;
                }

                if (/^\s*\d+\.\s+/.test(line)) {
                    if (!inOl) {
                        closeLists();
                        output += '<ol>';
                        inOl = true;
                    }
                    const item = line.replace(/^\s*\d+\.\s+/, '');
                    output += `<li>${item}</li>`;
                    return;
                }

                closeLists();
                if (!line.trim()) {
                    output += '<br>';
                    return;
                }
                if (/^\s*<h[1-3]>/.test(line)) {
                    output += line;
                    return;
                }
                output += `${line}<br>`;
            });

            closeLists();
            output = output.replace(/(<br>\s*)+$/g, '');
            return output;
        }).join('');

        return rendered;
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
    window.stripThinkingTags = stripThinkingTags;
    window.buildChatPrompt = buildChatPrompt;
    window.extractStopHook = extractStopHook;
    window.renderSimpleMarkdown = renderSimpleMarkdown;
})();
