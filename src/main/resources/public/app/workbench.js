// Workbench module (refactor split)
(function() {
    'use strict';

    let issueActivityAgentId = null;
    const renderSimpleMarkdown = window.renderSimpleMarkdown;
    const ROADMAP_STATUS_MAP = {
        idea: 'Idea',
        plan: 'Plan',
        draft: 'Draft',
        polished: 'Polished'
    };
    const ROADMAP_STATUS_ORDER = ['Idea', 'Plan', 'Draft', 'Polished'];

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
            if (issueActivityAgentId) {
                setAgentActivityState(issueActivityAgentId, 'idle');
                issueActivityAgentId = null;
            }
            const assignedId = resolveAgentIdFromLabel(issue.assignedTo);
            const openedId = resolveAgentIdFromLabel(issue.openedBy);
            issueActivityAgentId = assignedId || openedId;
            if (issueActivityAgentId) {
                setAgentActivityState(issueActivityAgentId, 'reading', `Reviewing Issue #${issue.id}`);
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
            const roadmapLabel = roadmapStatus || 'None';
            const roadmapOptions = buildRoadmapStatusOptions(roadmapStatus);

            // Build tags HTML
            const tagsHtml = otherTags.length > 0
                ? otherTags.map(tag => `<span class="issue-tag">${escapeHtml(tag)}</span>`).join('')
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
                            <div class="comment-body issue-markdown">${formatIssueBody(comment.body || '')}</div>
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
                              <span class="issue-meta-label">Roadmap:</span>
                              <span class="issue-roadmap-pill ${roadmapStatus ? 'is-set' : 'is-empty'}">${escapeHtml(roadmapLabel)}</span>
                          </div>
                          <div class="issue-meta-group issue-roadmap-controls">
                              <span class="issue-meta-label">Update:</span>
                              <div class="issue-roadmap-actions">
                                  <select class="modal-select issue-roadmap-select">
                                      ${roadmapOptions}
                                  </select>
                                  <button type="button" class="modal-btn modal-btn-secondary issue-roadmap-apply">Apply</button>
                              </div>
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
                        <div class="issue-body-content issue-markdown">${formatIssueBody(issue.body || '', 'No description provided.')}</div>
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

        const roadmapApplyBtn = modal.querySelector('.issue-roadmap-apply');
        if (roadmapApplyBtn) {
            roadmapApplyBtn.addEventListener('click', async () => {
                const issue = state.issueModal.issue;
                if (!issue) {
                    return;
                }
                const select = modal.querySelector('.issue-roadmap-select');
                if (!select) {
                    return;
                }
                const value = select.value;
                if (!value) {
                    notificationStore.warning('Select a roadmap status tag first.', 'workbench');
                    return;
                }
                const nextStatus = value === 'none' ? null : value;
                const { otherTags } = extractRoadmapStatus(issue.tags || []);
                const nextTags = [...otherTags];
                if (nextStatus) {
                    nextTags.push(nextStatus);
                }
                roadmapApplyBtn.disabled = true;
                try {
                    const updated = await issueApi.update(issue.id, { tags: nextTags });
                    state.issueModal.issue = updated;
                    notificationStore.success(`Updated Issue #${updated.id} roadmap status.`, 'workbench');
                    renderIssueModal();
                    if (state.viewMode && state.viewMode.current === 'workbench') {
                        await loadIssues();
                    }
                } catch (err) {
                    notificationStore.error(`Failed to update roadmap status: ${err.message}`, 'workbench');
                } finally {
                    roadmapApplyBtn.disabled = false;
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

        // Focus the close button for accessibility
        if (closeBtn) {
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


