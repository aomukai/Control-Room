// Workbench module (refactor split)
(function() {
    'use strict';

    let issueActivityAgentId = null;
    const renderSimpleMarkdown = window.renderSimpleMarkdown;

    // Status options for issues
    const ISSUE_STATUS_OPTIONS = [
        { value: 'open', label: 'Open' },
        { value: 'waiting', label: 'Waiting' },
        { value: 'closed', label: 'Closed' }
    ];

    // Helper to resolve agent from label (accessed lazily to ensure window.resolveAgentIdFromLabel is available)
    function getAgentFromLabel(agentLabel) {
        if (!agentLabel) return null;
        const resolver = window.resolveAgentIdFromLabel;
        const agentId = typeof resolver === 'function' ? resolver(agentLabel) : null;
        const agents = (state && state.agents && state.agents.list) || [];
        return agentId ? agents.find(a => a.id === agentId) : null;
    }

    // Render agent avatar HTML (reusable for author/assignee/comments)
    function renderAgentAvatarHtml(agentLabel, size = 'small') {
        const avatarClass = size === 'small' ? 'issue-agent-avatar' : 'comment-avatar';
        const emojiClass = size === 'small' ? 'issue-agent-avatar-emoji' : 'comment-avatar-emoji';
        const initialsClass = size === 'small' ? 'issue-agent-avatar-initials' : 'comment-avatar-initials';

        if (!agentLabel) {
            return `<div class="${avatarClass}"><span class="${initialsClass}">?</span></div>`;
        }

        const agent = getAgentFromLabel(agentLabel);

        if (agent) {
            if (agent.avatar && agent.avatar.startsWith('data:image')) {
                return `<div class="${avatarClass}"><img src="${agent.avatar}" alt="${escapeHtml(agent.name || agentLabel)}"></div>`;
            } else if (agent.emoji) {
                return `<div class="${avatarClass}"><span class="${emojiClass}">${agent.emoji}</span></div>`;
            }
        }

        // Fallback to initials
        const name = agent ? (agent.name || agentLabel) : agentLabel;
        const initials = String(name).substring(0, 2).toUpperCase();
        return `<div class="${avatarClass}"><span class="${initialsClass}">${escapeHtml(initials)}</span></div>`;
    }

    // Render agent chip (avatar + name) for header
    function renderAgentChipHtml(agentLabel) {
        if (!agentLabel) return '';

        const agent = getAgentFromLabel(agentLabel);
        const displayName = agent ? (agent.name || agentLabel) : agentLabel;

        return `
            <div class="issue-agent-chip">
                ${renderAgentAvatarHtml(agentLabel, 'small')}
                <span class="issue-agent-name">${escapeHtml(displayName)}</span>
            </div>
        `;
    }

    function getAgentDisplayName(agentId) {
        if (!agentId) return 'Unknown';
        const agents = (state && state.agents && state.agents.list) || [];
        const agent = agents.find(a => a.id === agentId);
        return agent ? (agent.name || agent.id) : agentId;
    }

    function buildAgentOptions(selectedId) {
        const agents = (state && state.agents && state.agents.list) || [];
        const enabledAgents = agents.filter(a => a && a.enabled !== false);
        return enabledAgents.map(agent => {
            const selected = agent.id === selectedId ? ' selected' : '';
            return `<option value="${escapeHtml(agent.id)}"${selected}>${escapeHtml(agent.name || agent.id)}</option>`;
        }).join('');
    }

    function resolveDefaultMemoryAgent(issue) {
        const agents = (state && state.agents && state.agents.list) || [];
        if (!agents.length) return null;
        if (state && state.agents && state.agents.selectedId) {
            const selected = agents.find(a => a.id === state.agents.selectedId);
            if (selected) return selected.id;
        }
        const resolver = window.resolveAgentIdFromLabel;
        const assignedId = issue && issue.assignedTo && typeof resolver === 'function'
            ? resolver(issue.assignedTo)
            : null;
        if (assignedId) return assignedId;
        const openedId = issue && issue.openedBy && typeof resolver === 'function'
            ? resolver(issue.openedBy)
            : null;
        if (openedId) return openedId;
        const fallback = agents.find(a => a && a.enabled !== false);
        return fallback ? fallback.id : agents[0].id;
    }

    function renderInterestDots(level) {
        const safeLevel = Math.max(1, Math.min(5, Number(level) || 1));
        let dots = '';
        for (let i = 1; i <= 5; i++) {
            dots += `<span class="issue-memory-dot${i <= safeLevel ? ' is-active' : ''}"></span>`;
        }
        return dots;
    }

    function resolveIssueBody(issue, memoryLevel) {
        if (!issue) return '';
        if (memoryLevel <= 1) {
            return issue.semanticTrace || issue.resolutionSummary || issue.compressedSummary || issue.body || '';
        }
        if (memoryLevel === 2) {
            return issue.resolutionSummary || issue.compressedSummary || issue.body || '';
        }
        if (memoryLevel === 3) {
            return issue.compressedSummary || issue.body || '';
        }
        return issue.body || '';
    }

    // Build assignee dropdown options
    function buildAssigneeOptions(currentAssignee) {
        const agents = (state && state.agents && state.agents.list) || [];
        const enabledAgents = agents.filter(a => a.enabled !== false);

        let html = '<option value="">Unassigned</option>';
        enabledAgents.forEach(agent => {
            const selected = (currentAssignee && (agent.id === currentAssignee || agent.name === currentAssignee)) ? ' selected' : '';
            html += `<option value="${escapeHtml(agent.id)}"${selected}>${escapeHtml(agent.name || agent.id)}</option>`;
        });
        return html;
    }

    // Build status dropdown options
    function buildStatusOptions(currentStatus) {
        return ISSUE_STATUS_OPTIONS.map(opt => {
            const selected = opt.value === currentStatus ? ' selected' : '';
            return `<option value="${opt.value}"${selected}>${opt.label}</option>`;
        }).join('');
    }

    const ROADMAP_STATUS_MAP = {
        idea: 'Idea',
        plan: 'Plan',
        draft: 'Draft',
        polished: 'Polished'
    };
    const ROADMAP_STATUS_ORDER = ['Idea', 'Plan', 'Draft', 'Polished'];
    const EPISTEMIC_STATUS_OPTIONS = [
        { value: '', label: 'Unset' },
        { value: 'tentative', label: 'Tentative' },
        { value: 'proposed', label: 'Proposed' },
        { value: 'agreed', label: 'Agreed' },
        { value: 'verified', label: 'Verified' }
    ];

    function canonicalizeRoadmapTag(tag) {
        if (!tag) {
            return null;
        }
        const key = String(tag).trim().toLowerCase();
        return ROADMAP_STATUS_MAP[key] || null;
    }

    function extractRoadmapStatus(tags) {
        if (!Array.isArray(tags)) {
            return { status: null, otherTags: [] };
        }
        let status = null;
        const otherTags = [];
        tags.forEach((tag) => {
            const canonical = canonicalizeRoadmapTag(tag);
            if (canonical) {
                status = canonical;
                return;
            }
            if (tag && !otherTags.includes(tag)) {
                otherTags.push(tag);
            }
        });
        return { status, otherTags };
    }

    function buildRoadmapStatusOptions(current) {
        const options = [
            { value: '', label: 'Select status' },
            ...ROADMAP_STATUS_ORDER.map((value) => ({ value, label: value })),
            { value: 'none', label: 'Clear status' }
        ];
        return options.map(({ value, label }) => {
            const selected = value === (current || '') ? ' selected' : '';
            return `<option value="${value}"${selected}>${label}</option>`;
        }).join('');
    }

    function buildEpistemicOptions(current) {
        const currentValue = (current || '').toLowerCase();
        return EPISTEMIC_STATUS_OPTIONS.map(({ value, label }) => {
            const selected = value === currentValue ? ' selected' : '';
            return `<option value="${value}"${selected}>${label}</option>`;
        }).join('');
    }

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

        const refreshBtn = document.createElement('button');
        refreshBtn.type = 'button';
        refreshBtn.className = 'workbench-panel-btn';
        refreshBtn.textContent = 'Refresh';
        refreshBtn.addEventListener('click', () => {
            loadIssues();
        });

        const closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'workbench-panel-btn';
        closeBtn.textContent = 'Close';
        closeBtn.addEventListener('click', close);

        actions.appendChild(newIssueBtn);
        actions.appendChild(refreshBtn);
        actions.appendChild(closeBtn);

        renderIssueBoard(body, { compactHeader: true });
    }

    function renderWorkbenchNewsfeed() {
        const container = document.getElementById('newsfeed-list');
        if (!container) return;

        // Get current project name for filtering
        const currentProject = window.state?.workspace?.name || '';

        // Get all notifications
        const allNotifications = notificationStore.getAll();

        // Filter to workbench-related and issue-related notifications, scoped to current project
        const filtered = allNotifications.filter(notification => {
            // Filter by project if notification has a projectId
            if (notification.projectId && currentProject && notification.projectId !== currentProject) {
                return false;
            }
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
            item.dataset.notificationId = notification.id;
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
                    <button type="button" class="newsfeed-dismiss" title="Dismiss notification">&times;</button>
                </div>
                <div class="newsfeed-message">${escapeHtml(notification.message)}</div>
                ${actionHtml}
            `;

            // Dismiss button handler
            const dismissBtn = item.querySelector('.newsfeed-dismiss');
            if (dismissBtn) {
                dismissBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    dismissNewsfeedNotification(notification.id);
                });
            }

            // Click anywhere on item to open related issue or handle action
            item.addEventListener('click', () => {
                handleNewsfeedItemClick(notification);
            });

            container.appendChild(item);
        });
    }

    async function dismissNewsfeedNotification(notificationId) {
        if (!notificationId) return;
        try {
            // Delete from server
            await fetch(`/api/notifications/${notificationId}`, { method: 'DELETE' });
            // Remove from client-side store (it will trigger a re-render via subscription)
            if (notificationStore.delete) {
                notificationStore.delete(notificationId);
            } else {
                // Fallback: just re-render
                renderWorkbenchNewsfeed();
            }
        } catch (err) {
            console.warn('[Newsfeed] Failed to dismiss notification:', err.message);
        }
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

    function renderIssueBoard(container, options = {}) {
        const target = container || document.getElementById('workbench-chat-content');
        if (!target) return;
        const compactHeader = Boolean(options.compactHeader);

        target.innerHTML = `
            <div class="issue-board${compactHeader ? ' issue-board-compact' : ''}">
                <div class="issue-board-header">
                    <div class="issue-board-title">
                        <span class="issue-board-icon">
                            <img src="assets/icons/heroicons_outline/rectangle-stack.svg" alt="" class="issue-board-icon-img">
                        </span>
                        <span>Issue Board</span>
                    </div>
                    <div class="issue-board-actions">
                        <button type="button" class="issue-board-btn issue-board-btn-primary" id="issue-board-new" title="Create issue">
                            New Issue
                        </button>
                        <button type="button" class="issue-board-btn" id="issue-board-refresh" title="Refresh">
                            <span class="issue-board-refresh-icon">
                                <img src="assets/icons/heroicons_outline/arrow-path.svg" alt="" class="issue-board-icon-img">
                            </span>
                            <span>Refresh</span>
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
                    <div class="issue-filter-group">
                        <label class="issue-filter-label">Memory</label>
                        <select id="issue-filter-interest" class="issue-filter-select">
                            <option value="active">Active</option>
                            <option value="all">All</option>
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
        const interestSelect = document.getElementById('issue-filter-interest');
        if (statusSelect) statusSelect.value = state.issueBoard.filters.status;
        if (prioritySelect) prioritySelect.value = state.issueBoard.filters.priority;
        if (interestSelect) interestSelect.value = state.issueBoard.filters.interest;

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

        const interestSelect = document.getElementById('issue-filter-interest');
        if (interestSelect) {
            interestSelect.addEventListener('change', (e) => {
                state.issueBoard.filters.interest = e.target.value;
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
                    <span class="issue-error-icon">!</span>
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
                    <span class="issue-empty-icon">--</span>
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
        const { status: roadmapStatus, otherTags } = extractRoadmapStatus(issue.tags);

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
        const tagsHtml = otherTags.length > 0
            ? otherTags.slice(0, 2).map(t => `<span class="issue-card-tag">${escapeHtml(t)}</span>`).join('')
            : '';
        const moreTagsHtml = otherTags.length > 2
            ? `<span class="issue-card-tag-more">+${otherTags.length - 2}</span>`
            : '';

        card.innerHTML = `
            <div class="issue-card-header">
                <div class="issue-card-header-left">
                    <span class="issue-card-id">#${issue.id}</span>
                    ${roadmapStatus ? `<span class="issue-card-roadmap">${escapeHtml(roadmapStatus)}</span>` : ''}
                </div>
                <span class="issue-card-status ${statusClass}">${formatStatus(issue.status)}</span>
            </div>
            <div class="issue-card-title">${escapeHtml(issue.title)}</div>
            <div class="issue-card-meta">
                <span class="issue-card-priority ${priorityClass}" title="${issue.priority} priority">
                    ${priorityIcon}
                </span>
                ${issue.assignedTo ? `<span class="issue-card-assignee" title="Assigned to ${issue.assignedTo}">Assignee: ${escapeHtml(issue.assignedTo)}</span>` : ''}
                ${commentCount > 0 ? `<span class="issue-card-comments" title="${commentCount} comment${commentCount !== 1 ? 's' : ''}">Comments: ${commentCount}</span>` : ''}
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
            case 'urgent': return 'Urgent';
            case 'high': return 'High';
            case 'normal': return 'Normal';
            case 'low': return 'Low';
            default: return 'Normal';
        }
    }

    function formatIssueBody(text, emptyFallback = '') {
        if (typeof renderSimpleMarkdown === 'function') {
            return renderSimpleMarkdown(text, { emptyFallback });
        }
        const fallback = text || emptyFallback || '';
        return escapeHtml ? escapeHtml(fallback) : fallback;
    }

    function extractPatchIds(comments) {
        const ids = new Set();
        if (!Array.isArray(comments)) {
            return [];
        }
        comments.forEach(comment => {
            const action = comment && comment.action ? comment.action : null;
            if (!action || action.type !== 'patch-proposed') {
                return;
            }
            const details = action.details || '';
            const match = String(details).match(/patchId:\s*([\w-]+)/i);
            if (match && match[1]) {
                ids.add(match[1]);
            }
        });
        return Array.from(ids);
    }

    async function openIssueModal(issueId) {
        if (!issueId) return;

        state.issueModal.isOpen = true;
        state.issueModal.issueId = issueId;
        state.issueModal.isLoading = true;
        state.issueModal.error = null;
        state.issueModal.issue = null;
        state.issueModal.memory = {
            agentId: null,
            record: null,
            isLoading: false,
            error: null
        };

        renderIssueModal();

        try {
            const issue = await issueApi.get(issueId);
            state.issueModal.issue = issue;
            state.issueModal.isLoading = false;
            if (issueActivityAgentId) {
                setAgentActivityState(issueActivityAgentId, 'idle');
                issueActivityAgentId = null;
            }
            const resolver = window.resolveAgentIdFromLabel;
            const assignedId = typeof resolver === 'function' ? resolver(issue.assignedTo) : null;
            const openedId = typeof resolver === 'function' ? resolver(issue.openedBy) : null;
            issueActivityAgentId = assignedId || openedId;
            if (issueActivityAgentId) {
                setAgentActivityState(issueActivityAgentId, 'reading', `Reviewing Issue #${issue.id}`);
            }
            const memoryAgent = resolveDefaultMemoryAgent(issue);
            if (memoryAgent) {
                state.issueModal.memory.agentId = memoryAgent;
                await loadIssueMemory(issue.id, memoryAgent);
            }
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
        state.issueModal.memory = {
            agentId: null,
            record: null,
            isLoading: false,
            error: null
        };

        if (issueActivityAgentId) {
            setAgentActivityState(issueActivityAgentId, 'idle');
            issueActivityAgentId = null;
        }

        const overlay = document.getElementById('issue-modal-overlay');
        if (overlay) {
            overlay.remove();
        }

        // Return focus to notification bell if it exists
        if (elements.notificationBell) {
            elements.notificationBell.focus();
        }
    }

    async function loadIssueMemory(issueId, agentId, { recordAccess = true } = {}) {
        if (!issueId || !agentId || !window.issueMemoryApi) return;
        state.issueModal.memory.isLoading = true;
        state.issueModal.memory.error = null;
        renderIssueModal();
        try {
            const record = recordAccess
                ? await issueMemoryApi.access(agentId, issueId)
                : await issueMemoryApi.get(agentId, issueId);
            state.issueModal.memory.record = record;
            state.issueModal.memory.isLoading = false;
            renderIssueModal();
        } catch (err) {
            state.issueModal.memory.error = err.message;
            state.issueModal.memory.isLoading = false;
            renderIssueModal();
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
            const { status: roadmapStatus, otherTags } = extractRoadmapStatus(issue.tags);
            const roadmapOptions = buildRoadmapStatusOptions(roadmapStatus);
            const statusOptions = buildStatusOptions(issue.status);
            const epistemicOptions = buildEpistemicOptions(issue.epistemicStatus);
            const assigneeOptions = buildAssigneeOptions(issue.assignedTo);

            // Build tags HTML
            const tagsHtml = otherTags.length > 0
                ? otherTags.map(tag => `<span class="issue-tag">${escapeHtml(tag)}</span>`).join('')
                : '<span class="issue-no-tags">No tags</span>';

            const patchIds = extractPatchIds(issue.comments || []);
            const memoryLevel = Math.max(1, Math.min(5, Number(issue.memoryLevel) || 3));
            let memoryBannerHtml = '';
            if (memoryLevel === 3) {
                memoryBannerHtml = '<div class="issue-memory-banner">Memory Level 3: Summary view (thread compressed).</div>';
            } else if (memoryLevel === 2) {
                memoryBannerHtml = '<div class="issue-memory-banner">Memory Level 2: Resolution summary only.</div>';
            } else if (memoryLevel <= 1) {
                memoryBannerHtml = '<div class="issue-memory-banner">Memory Level 1: Semantic trace only.</div>';
            }

            // Build comments HTML with avatars
            let commentsHtml = '';
            let commentList = Array.isArray(issue.comments) ? issue.comments : [];
            if (memoryLevel === 3 && commentList.length > 1) {
                const first = commentList[0];
                const last = commentList[commentList.length - 1];
                commentList = first === last ? [first] : [first, last];
            } else if (memoryLevel <= 2) {
                commentList = [];
            }

            if (commentList.length > 0) {
                commentsHtml = commentList.map(comment => {
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
                            ${renderAgentAvatarHtml(comment.author, 'medium')}
                            <div class="comment-content">
                                <div class="comment-header">
                                    <span class="comment-author">${escapeHtml(comment.author || 'Unknown')}</span>
                                    <span class="comment-timestamp">${formatRelativeTime(comment.timestamp)}</span>
                                    ${actionBadge}
                                </div>
                                <div class="comment-body issue-markdown">${formatIssueBody(comment.body || '')}</div>
                            </div>
                        </div>
                    `;
                }).join('');
            } else {
                commentsHtml = memoryLevel <= 2
                    ? '<div class="issue-no-comments">Thread compressed at this memory level.</div>'
                    : '<div class="issue-no-comments">No comments yet</div>';
            }

            const patchesHtml = patchIds.length
                ? `
                    <div class="issue-patches">
                        ${patchIds.map(patchId => `
                            <button type="button" class="issue-action-btn issue-action-primary" data-patch-id="${patchId}">
                                Review Patch ${escapeHtml(patchId)}
                            </button>
                        `).join('')}
                    </div>
                `
                : '';

            const memoryLevelsHtml = `
                <details class="issue-memory-levels">
                    <summary>Memory Levels (all)</summary>
                    <div class="issue-memory-level-entry">
                        <div class="issue-memory-level-entry-title">Level 1 - Semantic Trace</div>
                        <div class="issue-memory-level-entry-body issue-markdown">${formatIssueBody(issue.semanticTrace || 'Not generated')}</div>
                    </div>
                    <div class="issue-memory-level-entry">
                        <div class="issue-memory-level-entry-title">Level 2 - Resolution Summary</div>
                        <div class="issue-memory-level-entry-body issue-markdown">${formatIssueBody(issue.resolutionSummary || 'Not generated')}</div>
                    </div>
                    <div class="issue-memory-level-entry">
                        <div class="issue-memory-level-entry-title">Level 3 - Compressed Summary</div>
                        <div class="issue-memory-level-entry-body issue-markdown">${formatIssueBody(issue.compressedSummary || 'Not generated')}</div>
                    </div>
                    <div class="issue-memory-level-entry">
                        <div class="issue-memory-level-entry-title">Level 4/5 - Full Thread</div>
                        <div class="issue-memory-level-entry-body issue-markdown">${formatIssueBody(issue.body || 'Not available')}</div>
                    </div>
                </details>
            `;

            const memoryState = state.issueModal.memory || {};
            const memoryAgentId = memoryState.agentId || resolveDefaultMemoryAgent(issue);
            const memoryRecord = memoryState.record;
            const memoryAgentOptions = memoryAgentId ? buildAgentOptions(memoryAgentId) : '';
            let memoryBodyHtml = '';
            if (!window.issueMemoryApi) {
                memoryBodyHtml = '<div class="issue-memory-empty">Issue memory service unavailable.</div>';
            } else if (memoryState.isLoading) {
                memoryBodyHtml = '<div class="issue-memory-loading">Loading memory state...</div>';
            } else if (memoryState.error) {
                memoryBodyHtml = `<div class="issue-memory-error">Failed to load memory: ${escapeHtml(memoryState.error)}</div>`;
            } else if (memoryRecord) {
                const interestLevel = memoryRecord.interestLevel || 1;
                const accessCount = memoryRecord.accessCount || 0;
                const lastAccessed = memoryRecord.lastAccessedAt ? formatTimestamp(memoryRecord.lastAccessedAt) : '—';
                const lastRefreshed = memoryRecord.lastRefreshedAt ? formatTimestamp(memoryRecord.lastRefreshedAt) : '—';
                const appliedLabel = memoryRecord.appliedInWork ? 'Applied in work' : 'Not applied yet';
                memoryBodyHtml = `
                    <div class="issue-memory-level">
                        <span class="issue-memory-label">Interest</span>
                        <div class="issue-memory-dots">${renderInterestDots(interestLevel)}</div>
                        <span class="issue-memory-level-text">Level ${interestLevel}</span>
                    </div>
                    <div class="issue-memory-stats">
                        <div class="issue-memory-stat">
                            <span class="issue-memory-label">Accessed</span>
                            <span class="issue-memory-value">${escapeHtml(lastAccessed)}</span>
                        </div>
                        <div class="issue-memory-stat">
                            <span class="issue-memory-label">Refreshed</span>
                            <span class="issue-memory-value">${escapeHtml(lastRefreshed)}</span>
                        </div>
                        <div class="issue-memory-stat">
                            <span class="issue-memory-label">Access Count</span>
                            <span class="issue-memory-value">${accessCount}</span>
                        </div>
                        <div class="issue-memory-stat">
                            <span class="issue-memory-label">Status</span>
                            <span class="issue-memory-value">${escapeHtml(appliedLabel)}</span>
                        </div>
                    </div>
                `;
            } else {
                memoryBodyHtml = '<div class="issue-memory-empty">No memory data yet.</div>';
            }

            const memoryControlsDisabled = !memoryAgentId || !window.issueMemoryApi;
            const memoryPanelHtml = `
                <div class="issue-memory-panel">
                    <div class="issue-memory-header">
                        <div>
                            <div class="issue-memory-title">Memory</div>
                            <div class="issue-memory-subtitle">Per-agent interest & usage</div>
                        </div>
                        ${memoryAgentId ? `
                            <div class="issue-memory-agent">
                                <span class="issue-memory-label">Agent</span>
                                <select class="modal-select issue-memory-agent-select">${memoryAgentOptions}</select>
                            </div>
                        ` : ''}
                    </div>
                    <div class="issue-memory-body">
                        ${memoryBodyHtml}
                    </div>
                    <div class="issue-memory-actions">
                        <button type="button" class="issue-action-btn issue-memory-applied-btn"${memoryControlsDisabled ? ' disabled' : ''}>Mark Applied</button>
                        <button type="button" class="issue-action-btn issue-memory-irrelevant-btn"${memoryControlsDisabled ? ' disabled' : ''}>Mark Irrelevant</button>
                        <button type="button" class="issue-action-btn issue-memory-refresh-btn"${memoryControlsDisabled ? ' disabled' : ''}>Refresh</button>
                    </div>
                </div>
            `;

            // Status action button
            const isOpen = issue.status === 'open';
            const isClosed = issue.status === 'closed';
            const statusActionBtn = isClosed
                ? `<button type="button" class="issue-action-btn issue-action-success issue-reopen-btn">Reopen Issue</button>`
                : `<button type="button" class="issue-action-btn issue-action-danger issue-close-btn">${isOpen ? 'Close Issue' : 'Close Issue'}</button>`;

            modal.innerHTML = `
                <div class="issue-modal-header">
                    <div class="issue-modal-title-row">
                        <h2 id="issue-modal-title" class="issue-modal-title">#${issue.id}: ${escapeHtml(issue.title)}</h2>
                        <span class="issue-status-pill ${getStatusClass(issue.status)}">${escapeHtml(statusLabel)}</span>
                    </div>
                    <div class="issue-modal-meta-row">
                        <span class="issue-meta-item">
                            <span class="issue-meta-label">Opened by</span>
                            ${renderAgentChipHtml(issue.openedBy || 'Unknown')}
                        </span>
                        <span class="issue-meta-item">
                            <span class="issue-meta-label">Assigned to</span>
                            ${issue.assignedTo ? renderAgentChipHtml(issue.assignedTo) : '<span class="issue-meta-value" style="color: var(--text-secondary)">Unassigned</span>'}
                        </span>
                    </div>
                    <button type="button" class="issue-modal-close" aria-label="Close">&times;</button>
                </div>

                <div class="issue-modal-body">
                    <div class="issue-meta-section">
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Status</span>
                            <select class="modal-select issue-status-select">${statusOptions}</select>
                        </div>
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Assignee</span>
                            <select class="modal-select issue-assignee-select">${assigneeOptions}</select>
                        </div>
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Roadmap</span>
                            <select class="modal-select issue-roadmap-select">${roadmapOptions}</select>
                        </div>
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Epistemic</span>
                            <select class="modal-select issue-epistemic-select">${epistemicOptions}</select>
                        </div>
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Priority</span>
                            <span class="issue-priority-pill ${getPriorityClass(issue.priority)}">${escapeHtml(issue.priority)}</span>
                        </div>
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Tags</span>
                            <div class="issue-tags-container">${tagsHtml}</div>
                        </div>
                        <div class="issue-meta-group">
                            <span class="issue-meta-label">Created</span>
                            <span class="issue-meta-value">${formatTimestamp(issue.createdAt)}</span>
                        </div>
                    </div>

                    ${memoryPanelHtml}
                    ${memoryLevelsHtml}

                    <div class="issue-body-section">
                        <h3 class="issue-section-title">Description</h3>
                        ${memoryBannerHtml}
                        <div class="issue-body-content issue-markdown">${formatIssueBody(resolveIssueBody(issue, memoryLevel), 'No description provided.')}</div>
                    </div>

                    ${patchIds.length ? `
                        <div class="issue-patches-section">
                            <h3 class="issue-section-title">Patches</h3>
                            ${patchesHtml}
                        </div>
                    ` : ''}

                    <div class="issue-comments-section">
                        <h3 class="issue-section-title">Discussion (${issue.comments ? issue.comments.length : 0})</h3>
                        <div class="issue-comments-list">${commentsHtml}</div>
                    </div>
                </div>

                <div class="issue-modal-footer">
                    <div class="issue-comment-form">
                        <div class="issue-comment-input-wrapper">
                            <textarea class="issue-comment-input" placeholder="Add a comment..." rows="2"></textarea>
                        </div>
                        <button type="button" class="issue-action-btn issue-action-primary issue-submit-comment-btn">Comment</button>
                    </div>
                    <div class="issue-footer-actions">
                        <div class="issue-footer-left">
                            ${statusActionBtn}
                        </div>
                        <div class="issue-footer-right">
                            ${memoryLevel <= 2 ? '<button type="button" class="issue-action-btn issue-action-secondary issue-revive-btn">Revive Thread</button>' : ''}
                            <button type="button" class="issue-modal-btn-close">Close</button>
                        </div>
                    </div>
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

        // Patch review buttons
        const patchButtons = modal.querySelectorAll('[data-patch-id]');
        patchButtons.forEach(btn => {
            btn.addEventListener('click', () => {
                const patchId = btn.getAttribute('data-patch-id');
                if (patchId) {
                    if (typeof window.showPatchReviewModal === 'function') {
                        window.showPatchReviewModal(patchId);
                    } else if (typeof showPatchReviewModal === 'function') {
                        showPatchReviewModal(patchId);
                    }
                }
            });
        });

        // Status dropdown change
        const statusSelect = modal.querySelector('.issue-status-select');
        if (statusSelect) {
            statusSelect.addEventListener('change', async () => {
                const issue = state.issueModal.issue;
                if (!issue) return;
                const newStatus = statusSelect.value;
                if (newStatus === issue.status) return;

                statusSelect.disabled = true;
                try {
                    const updated = await issueApi.update(issue.id, { status: newStatus });
                    state.issueModal.issue = updated;
                    notificationStore.success(`Issue #${updated.id} status changed to ${newStatus}.`, 'workbench');
                    renderIssueModal();
                    if (state.viewMode && state.viewMode.current === 'workbench') {
                        await loadIssues();
                    }
                } catch (err) {
                    notificationStore.error(`Failed to update status: ${err.message}`, 'workbench');
                    statusSelect.value = issue.status;
                } finally {
                    statusSelect.disabled = false;
                }
            });
        }

        // Assignee dropdown change
        const assigneeSelect = modal.querySelector('.issue-assignee-select');
        if (assigneeSelect) {
            assigneeSelect.addEventListener('change', async () => {
                const issue = state.issueModal.issue;
                if (!issue) return;
                const newAssignee = assigneeSelect.value || null;

                assigneeSelect.disabled = true;
                try {
                    const updated = await issueApi.update(issue.id, { assignedTo: newAssignee });
                    state.issueModal.issue = updated;
                    const assigneeName = newAssignee || 'Unassigned';
                    notificationStore.success(`Issue #${updated.id} assigned to ${assigneeName}.`, 'workbench');
                    renderIssueModal();
                    if (state.viewMode && state.viewMode.current === 'workbench') {
                        await loadIssues();
                    }
                } catch (err) {
                    notificationStore.error(`Failed to update assignee: ${err.message}`, 'workbench');
                } finally {
                    assigneeSelect.disabled = false;
                }
            });
        }

        // Roadmap dropdown change (inline, no apply button)
        const roadmapSelect = modal.querySelector('.issue-roadmap-select');
        if (roadmapSelect) {
            roadmapSelect.addEventListener('change', async () => {
                const issue = state.issueModal.issue;
                if (!issue) return;
                const value = roadmapSelect.value;
                const nextStatus = value === 'none' ? null : (value || null);
                const { otherTags } = extractRoadmapStatus(issue.tags || []);
                const nextTags = [...otherTags];
                if (nextStatus) {
                    nextTags.push(nextStatus);
                }

                roadmapSelect.disabled = true;
                try {
                    const updated = await issueApi.update(issue.id, { tags: nextTags });
                    state.issueModal.issue = updated;
                    notificationStore.success(`Issue #${updated.id} roadmap updated.`, 'workbench');
                    renderIssueModal();
                    if (state.viewMode && state.viewMode.current === 'workbench') {
                        await loadIssues();
                    }
                } catch (err) {
                    notificationStore.error(`Failed to update roadmap: ${err.message}`, 'workbench');
                } finally {
                    roadmapSelect.disabled = false;
                }
            });
        }

        const epistemicSelect = modal.querySelector('.issue-epistemic-select');
        if (epistemicSelect) {
            epistemicSelect.addEventListener('change', async () => {
                const issue = state.issueModal.issue;
                if (!issue) return;
                const value = epistemicSelect.value || null;
                epistemicSelect.disabled = true;
                try {
                    const payload = value ? { epistemicStatus: value } : { epistemicStatus: null };
                    const updated = await issueApi.update(issue.id, payload);
                    state.issueModal.issue = updated;
                    notificationStore.success(`Issue #${updated.id} epistemic status updated.`, 'workbench');
                    renderIssueModal();
                    if (state.viewMode && state.viewMode.current === 'workbench') {
                        await loadIssues();
                    }
                } catch (err) {
                    notificationStore.error(`Failed to update epistemic status: ${err.message}`, 'workbench');
                } finally {
                    epistemicSelect.disabled = false;
                }
            });
        }

        // Memory agent selector
        const memoryAgentSelect = modal.querySelector('.issue-memory-agent-select');
        if (memoryAgentSelect) {
            memoryAgentSelect.addEventListener('change', async () => {
                const issue = state.issueModal.issue;
                if (!issue) return;
                const nextAgent = memoryAgentSelect.value || null;
                if (!nextAgent) return;
                state.issueModal.memory.agentId = nextAgent;
                await loadIssueMemory(issue.id, nextAgent);
            });
        }

        const memoryAppliedBtn = modal.querySelector('.issue-memory-applied-btn');
        if (memoryAppliedBtn) {
            memoryAppliedBtn.addEventListener('click', async () => {
                const issue = state.issueModal.issue;
                const agentId = state.issueModal.memory.agentId;
                if (!issue || !agentId || !window.issueMemoryApi) return;
                memoryAppliedBtn.disabled = true;
                try {
                    const record = await issueMemoryApi.applied(agentId, issue.id);
                    state.issueModal.memory.record = record;
                    renderIssueModal();
                } catch (err) {
                    notificationStore.error(`Failed to mark applied: ${err.message}`, 'workbench');
                } finally {
                    memoryAppliedBtn.disabled = false;
                }
            });
        }

        const memoryIrrelevantBtn = modal.querySelector('.issue-memory-irrelevant-btn');
        if (memoryIrrelevantBtn) {
            memoryIrrelevantBtn.addEventListener('click', async () => {
                const issue = state.issueModal.issue;
                const agentId = state.issueModal.memory.agentId;
                if (!issue || !agentId || !window.issueMemoryApi) return;
                const showModal = window.modals && window.modals.showModal;
                const applyIrrelevant = async (note) => {
                    memoryIrrelevantBtn.disabled = true;
                    try {
                        const record = await issueMemoryApi.irrelevant(agentId, issue.id, note || '');
                        state.issueModal.memory.record = record;
                        renderIssueModal();
                    } catch (err) {
                        notificationStore.error(`Failed to mark irrelevant: ${err.message}`, 'workbench');
                    } finally {
                        memoryIrrelevantBtn.disabled = false;
                    }
                };
                if (typeof showModal === 'function') {
                    showModal('Mark memory irrelevant', 'Optional note', (note) => {
                        applyIrrelevant(note);
                    }, 'Adds a private note for this agent.');
                } else {
                    applyIrrelevant('');
                }
            });
        }

        const memoryRefreshBtn = modal.querySelector('.issue-memory-refresh-btn');
        if (memoryRefreshBtn) {
            memoryRefreshBtn.addEventListener('click', async () => {
                const issue = state.issueModal.issue;
                const agentId = state.issueModal.memory.agentId;
                if (!issue || !agentId) return;
                memoryRefreshBtn.disabled = true;
                await loadIssueMemory(issue.id, agentId, { recordAccess: false });
                memoryRefreshBtn.disabled = false;
            });
        }

        // Comment submission
        const commentInput = modal.querySelector('.issue-comment-input');
        const submitCommentBtn = modal.querySelector('.issue-submit-comment-btn');
        if (submitCommentBtn && commentInput) {
            const submitComment = async () => {
                const issue = state.issueModal.issue;
                if (!issue) return;
                const body = commentInput.value.trim();
                if (!body) {
                    notificationStore.warning('Please enter a comment.', 'workbench');
                    return;
                }

                submitCommentBtn.disabled = true;
                commentInput.disabled = true;
                try {
                    await issueApi.addComment(issue.id, { body, author: 'User' });
                    commentInput.value = '';
                    notificationStore.success('Comment added.', 'workbench');
                    // Refresh the issue to show the new comment
                    const updated = await issueApi.get(issue.id);
                    state.issueModal.issue = updated;
                    renderIssueModal();
                } catch (err) {
                    notificationStore.error(`Failed to add comment: ${err.message}`, 'workbench');
                } finally {
                    submitCommentBtn.disabled = false;
                    commentInput.disabled = false;
                }
            };

            submitCommentBtn.addEventListener('click', submitComment);

            // Ctrl+Enter to submit
            commentInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                    e.preventDefault();
                    submitComment();
                }
            });
        }

        // Close issue button
        const closeIssueBtn = modal.querySelector('.issue-close-btn');
        if (closeIssueBtn) {
            closeIssueBtn.addEventListener('click', async () => {
                const issue = state.issueModal.issue;
                if (!issue) return;

                closeIssueBtn.disabled = true;
                try {
                    const updated = await issueApi.update(issue.id, { status: 'closed' });
                    state.issueModal.issue = updated;
                    notificationStore.success(`Issue #${updated.id} closed.`, 'workbench');
                    renderIssueModal();
                    if (state.viewMode && state.viewMode.current === 'workbench') {
                        await loadIssues();
                    }
                } catch (err) {
                    notificationStore.error(`Failed to close issue: ${err.message}`, 'workbench');
                } finally {
                    closeIssueBtn.disabled = false;
                }
            });
        }

        // Reopen issue button
        const reopenIssueBtn = modal.querySelector('.issue-reopen-btn');
        if (reopenIssueBtn) {
            reopenIssueBtn.addEventListener('click', async () => {
                const issue = state.issueModal.issue;
                if (!issue) return;

                reopenIssueBtn.disabled = true;
                try {
                    const updated = await issueApi.update(issue.id, { status: 'open' });
                    state.issueModal.issue = updated;
                    notificationStore.success(`Issue #${updated.id} reopened.`, 'workbench');
                    renderIssueModal();
                    if (state.viewMode && state.viewMode.current === 'workbench') {
                        await loadIssues();
                    }
                } catch (err) {
                    notificationStore.error(`Failed to reopen issue: ${err.message}`, 'workbench');
                } finally {
                    reopenIssueBtn.disabled = false;
                }
            });
        }

        const reviveBtn = modal.querySelector('.issue-revive-btn');
        if (reviveBtn) {
            reviveBtn.addEventListener('click', async () => {
                const issue = state.issueModal.issue;
                if (!issue || !window.issueApi || !issueApi.revive) return;
                reviveBtn.disabled = true;
                try {
                    const revived = await issueApi.revive(issue.id);
                    state.issueModal.issue = revived;
                    notificationStore.success(`Issue #${revived.id} revived to Level 3.`, 'workbench');
                    renderIssueModal();
                    if (state.viewMode && state.viewMode.current === 'workbench') {
                        await loadIssues();
                    }
                } catch (err) {
                    notificationStore.error(`Failed to revive issue: ${err.message}`, 'workbench');
                } finally {
                    reviveBtn.disabled = false;
                }
            });
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

        // Focus the comment input for quick interaction
        if (commentInput) {
            commentInput.focus();
        } else if (closeBtn) {
            closeBtn.focus();
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

    // Subscribe to notification changes to update Newsfeed when in Workbench view
    function initWorkbenchNewsfeedSubscription() {
        notificationStore.subscribe(() => {
            if (isWorkbenchView()) {
                renderWorkbenchNewsfeed();
            }
        });
    }

    window.createWorkbenchPanelShell = createWorkbenchPanelShell;
    window.openIssueBoardPanel = openIssueBoardPanel;
    window.renderWorkbenchNewsfeed = renderWorkbenchNewsfeed;
    window.handleNewsfeedItemClick = handleNewsfeedItemClick;
    window.dismissNewsfeedNotification = dismissNewsfeedNotification;
    window.initWorkbenchNewsfeedSubscription = initWorkbenchNewsfeedSubscription;
    window.renderIssueBoard = renderIssueBoard;
    window.initIssueBoardListeners = initIssueBoardListeners;
    window.renderIssueBoardContent = renderIssueBoardContent;
    window.createIssueCard = createIssueCard;
    window.getPriorityIcon = getPriorityIcon;
    window.openIssueModal = openIssueModal;
    window.closeIssueModal = closeIssueModal;
    window.renderIssueModal = renderIssueModal;
    window.refreshIssueModal = refreshIssueModal;
    window.extractRoadmapStatus = extractRoadmapStatus;
})();
