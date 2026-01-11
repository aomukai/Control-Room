// Workbench module (refactor split)
(function() {
    'use strict';

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

    window.createWorkbenchPanelShell = createWorkbenchPanelShell;
    window.openIssueBoardPanel = openIssueBoardPanel;
    window.renderWorkbenchNewsfeed = renderWorkbenchNewsfeed;
    window.handleNewsfeedItemClick = handleNewsfeedItemClick;
    window.initWorkbenchNewsfeedSubscription = initWorkbenchNewsfeedSubscription;
})();
