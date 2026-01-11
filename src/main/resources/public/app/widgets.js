// Widgets module (refactor split)
(function() {
    'use strict';

    const createModalShell = window.modals ? window.modals.createModalShell : null;
    const escapeHtml = window.escapeHtml;
    const formatTimestamp = window.formatTimestamp;
    const issueApi = window.issueApi;
    const openIssueBoardPanel = window.openIssueBoardPanel;
    const ensureChiefOfStaff = window.ensureChiefOfStaff;
    const showConferenceInviteModal = window.showConferenceInviteModal;
    const isAssistantAgent = window.isAssistantAgent;
    const state = window.state;

    function getChiefOfStaffAgent() {
        const agents = (state && state.agents && state.agents.list) ? state.agents.list : [];
        if (typeof isAssistantAgent === 'function') {
            const chief = agents.find(agent => isAssistantAgent(agent));
            if (chief) return chief;
        }
        return agents[0] || null;
    }

    // ============================================
    // WIDGET SYSTEM
    // ============================================
    
    // Widget Registry - manages available widget types
    class WidgetRegistry {
        constructor() {
            this.widgets = new Map();
        }
    
        register(manifest) {
            this.widgets.set(manifest.id, manifest);
        }
    
        get(widgetId) {
            return this.widgets.get(widgetId);
        }
    
        list() {
            return Array.from(this.widgets.values());
        }
    
        createInstance(widgetId, settings = {}) {
            const manifest = this.get(widgetId);
            if (!manifest) throw new Error(`Widget not found: ${widgetId}`);
    
            const defaultSettings = this.getDefaultSettings(manifest);
    
            return {
                instanceId: this.generateId(),
                widgetId,
                position: 0,
                size: manifest.size.default,
                settings: { ...defaultSettings, ...settings }
            };
        }
    
        getDefaultSettings(manifest) {
            if (!manifest.settings) return {};
            const defaults = {};
            for (const [key, config] of Object.entries(manifest.settings)) {
                defaults[key] = config.default;
            }
            return defaults;
        }
    
        generateId() {
            return 'widget-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
        }
    }
    
    // Widget Base Class - all widgets extend this
    class Widget {
        constructor(instance, manifest) {
            this.instance = instance;
            this.manifest = manifest;
            this.container = null;
            this.mounted = false;
        }
    
        async mount(container) {
            this.container = container;
            await this.render();
            this.attachEventListeners();
            this.mounted = true;
        }
    
        async unmount() {
            this.detachEventListeners();
            this.container = null;
            this.mounted = false;
        }
    
        async render() {
            // Default implementation - subclasses override
            if (!this.container) return;
            this.container.innerHTML = `
                <div class="widget-content">
                    <p>Widget: ${this.manifest.name}</p>
                </div>
            `;
        }
    
        async update(newSettings) {
            this.instance.settings = { ...this.instance.settings, ...newSettings };
            await this.render();
            this.saveToDashboard();
        }
    
        attachEventListeners() {}
        detachEventListeners() {}
    
        saveToDashboard() {
            if (window.dashboardState) {
                window.dashboardState.saveWidget(this.instance);
            }
        }
    }
    
    // Quick Notes Widget - simple text editor with auto-save
    class QuickNotesWidget extends Widget {
        constructor(instance, manifest) {
            super(instance, manifest);
            this.saveTimeout = null;
        }
    
        async render() {
            if (!this.container) return;
    
            const content = this.instance.settings.content || '';
            const showPreview = this.instance.settings.showPreview || false;
            const lastModified = this.instance.settings.lastModified;
    
            this.container.innerHTML = `
                <div class="widget-quick-notes">
                    <div class="widget-quick-notes-toolbar">
                        <button class="widget-quick-notes-toggle" type="button" title="Toggle Preview">
                            ${showPreview ? '📝 Edit' : '👁️ Preview'}
                        </button>
                        <span class="widget-quick-notes-meta">
                            ${content.length} chars
                            ${lastModified ? '· ' + this.formatTimeAgo(lastModified) : ''}
                        </span>
                    </div>
                    ${showPreview ?
                        `<div class="widget-quick-notes-preview">${this.renderMarkdown(content)}</div>` :
                        `<textarea class="widget-quick-notes-textarea" placeholder="Jot down your thoughts...">${escapeHtml(content)}</textarea>`
                    }
                </div>
            `;
        }
    
        attachEventListeners() {
            if (!this.container) return;
    
            const toggleBtn = this.container.querySelector('.widget-quick-notes-toggle');
            if (toggleBtn) {
                toggleBtn.addEventListener('click', () => {
                    this.instance.settings.showPreview = !this.instance.settings.showPreview;
                    this.render();
                    this.attachEventListeners();
                });
            }
    
            const textarea = this.container.querySelector('.widget-quick-notes-textarea');
            if (textarea) {
                textarea.addEventListener('input', (e) => {
                    this.handleContentChange(e.target.value);
                });
            }
        }
    
        handleContentChange(content) {
            this.instance.settings.content = content;
            this.instance.settings.lastModified = Date.now();
    
            // Debounced save
            clearTimeout(this.saveTimeout);
            this.saveTimeout = setTimeout(() => {
                this.saveToDashboard();
                // Update the meta display
                const meta = this.container.querySelector('.widget-quick-notes-meta');
                if (meta) {
                    meta.innerHTML = `${content.length} chars · just now`;
                }
            }, 500);
        }
    
        renderMarkdown(text) {
            if (!text) return '<p class="widget-quick-notes-empty">Nothing here yet...</p>';
    
            // Simple markdown rendering
            let html = escapeHtml(text);
    
            // Headers
            html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
            html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
            html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');
    
            // Bold and italic
            html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
            html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
    
            // Line breaks
            html = html.replace(/\n/g, '<br>');
    
            return html;
        }
    
        formatTimeAgo(timestamp) {
            const seconds = Math.floor((Date.now() - timestamp) / 1000);
            if (seconds < 60) return 'just now';
            if (seconds < 3600) return Math.floor(seconds / 60) + 'm ago';
            if (seconds < 86400) return Math.floor(seconds / 3600) + 'h ago';
            return Math.floor(seconds / 86400) + 'd ago';
        }
    }
    
    // Today's Briefing Widget - Team lead's daily summary
    class PlannerBriefingWidget extends Widget {
        async render() {
            if (!this.container) return;
    
            const leader = getChiefOfStaffAgent();
            const leaderName = leader?.name || 'Chief of Staff';
            const leaderAvatar = leader?.avatar || '';
    
            this.container.innerHTML = `
                <div class="widget-planner-briefing">
                    <div class="workbench-briefing-header">
                        <div class="workbench-briefing-avatar" id="briefing-avatar-${this.instance.instanceId}"></div>
                        <div>
                            <div class="workbench-card-title">Today's Briefing</div>
                            <div class="workbench-card-subtitle">Resolved issues and momentum check-in.</div>
                        </div>
                    </div>
                    <div class="workbench-briefing" id="briefing-content-${this.instance.instanceId}">
                        <div class="workbench-digest-loading">Loading digest...</div>
                    </div>
                    <div class="workbench-briefing-actions">
                        <button class="workbench-link-btn briefing-open-issues" type="button">Open Issues</button>
                        <button class="workbench-link-btn briefing-start-conference" type="button">Start Conference</button>
                    </div>
                    <div class="workbench-briefing-signature">${escapeHtml(leaderName)}</div>
                </div>
            `;
    
            // Render avatar
            const avatarEl = this.container.querySelector(`#briefing-avatar-${this.instance.instanceId}`);
            if (avatarEl) {
                const avatarData = leaderAvatar && leaderAvatar.trim() ? leaderAvatar.trim() : '';
                if (avatarData.startsWith('data:') || avatarData.startsWith('http')) {
                    const img = document.createElement('img');
                    img.src = avatarData;
                    img.alt = leaderName;
                    avatarEl.appendChild(img);
                } else if (avatarData) {
                    avatarEl.textContent = avatarData;
                } else {
                    avatarEl.textContent = leaderName.charAt(0).toUpperCase();
                }
            }
    
            // Load data
            this.loadData();
        }
    
        async loadData() {
            const digestContainer = this.container.querySelector(`#briefing-content-${this.instance.instanceId}`);
            if (!digestContainer) return;
    
            try {
                const issues = await issueApi.list();
                const closed = issues.filter(issue => issue.status === 'closed');
                const resolvedCount = closed.length;
                const recentResolved = closed
                    .sort((a, b) => (b.closedAt || b.updatedAt || 0) - (a.closedAt || a.updatedAt || 0))
                    .slice(0, 5);
    
                if (recentResolved.length === 0) {
                    digestContainer.innerHTML = '<div class="workbench-placeholder">No issues resolved yet. Let\'s get a win on the board.</div>';
                    return;
                }
    
                const leader = getChiefOfStaffAgent();
                const leaderName = leader?.name || 'Chief of Staff';
                const creditsEarned = Math.min(resolvedCount, 12);
    
                digestContainer.innerHTML = `
                    <div class="workbench-briefing-text">
                        Hello, here's the current state of the project: we finished ${resolvedCount} issue${resolvedCount !== 1 ? 's' : ''} recently, and our agents earned ${creditsEarned} credits.
                        ${leaderName ? `${escapeHtml(leaderName)} was exceptionally successful.` : ''}
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
            }
        }
    
        attachEventListeners() {
            if (!this.container) return;
    
            const openIssuesBtn = this.container.querySelector('.briefing-open-issues');
            if (openIssuesBtn) {
                openIssuesBtn.addEventListener('click', () => openIssueBoardPanel());
            }
    
            const startConfBtn = this.container.querySelector('.briefing-start-conference');
            if (startConfBtn) {
                if (state.agents.locked) {
                    startConfBtn.disabled = true;
                    startConfBtn.title = 'Agents are locked until a Chief of Staff exists.';
                }
                startConfBtn.addEventListener('click', () => {
                    ensureChiefOfStaff('Start conference', () => showConferenceInviteModal());
                });
            }
        }
    }
    
    // Issue Pulse Widget - Open vs resolved trends
    class IssuePulseWidget extends Widget {
        async render() {
            if (!this.container) return;
    
            this.container.innerHTML = `
                <div class="widget-issue-pulse">
                    <div class="workbench-card-subtitle">Open vs resolved trends.</div>
                    <div class="workbench-stats" id="issue-stats-${this.instance.instanceId}">
                        <div class="workbench-digest-loading">Loading stats...</div>
                    </div>
                </div>
            `;
    
            this.loadData();
        }
    
        async loadData() {
            const statsContainer = this.container.querySelector(`#issue-stats-${this.instance.instanceId}`);
            if (!statsContainer) return;
    
            try {
                const issues = await issueApi.list();
                const total = issues.length;
                const openCount = issues.filter(issue => issue.status === 'open').length;
                const resolvedCount = issues.filter(issue => issue.status === 'closed').length;
    
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
            } catch (err) {
                statsContainer.innerHTML = `<div class="workbench-placeholder">Stats unavailable.</div>`;
            }
        }
    }
    
    // Credits Leaderboard Widget - Top contributors
    class CreditsLeaderboardWidget extends Widget {
        async render() {
            if (!this.container) return;
    
            this.container.innerHTML = `
                <div class="widget-credits-leaderboard">
                    <div class="widget-credits-header">
                        <div class="workbench-card-subtitle">Top contributors by credits earned.</div>
                        <button type="button" class="credits-refresh-btn" id="credits-refresh-${this.instance.instanceId}" title="Refresh leaderboard">
                            <img src="assets/icons/heroicons_outline/arrow-path.svg" class="credits-refresh-icon" alt="Refresh">
                        </button>
                    </div>
                    <div class="credits-leaderboard-container" id="credits-leaderboard-${this.instance.instanceId}">
                        <div class="credits-leaderboard-loading">Loading leaderboard...</div>
                    </div>
                </div>
            `;
    
            const refreshBtn = this.container.querySelector(`#credits-refresh-${this.instance.instanceId}`);
            if (refreshBtn) {
                refreshBtn.addEventListener('click', () => {
                    this.loadData(true);
                });
            }
    
            this.loadData();
        }
    
        getRankIcon(index) {
            const icons = {
                0: `<img src="assets/icons/lucide/trophy.svg" class="leaderboard-rank-icon gold" alt="1st place">`,
                1: `<img src="assets/icons/lucide/medal.svg" class="leaderboard-rank-icon silver" alt="2nd place">`,
                2: `<img src="assets/icons/lucide/award.svg" class="leaderboard-rank-icon bronze" alt="3rd place">`
            };
            return icons[index] || `<span class="leaderboard-rank-number">${index + 1}</span>`;
        }
    
        getTierBadge(tier) {
            if (!tier || tier.toLowerCase() === 'none') return '';
            const tierLower = tier.toLowerCase();
            const tierColors = {
                platinum: '#E5E4E2',
                gold: '#FFD700',
                silver: '#C0C0C0',
                bronze: '#CD7F32'
            };
            const color = tierColors[tierLower] || 'var(--text-secondary)';
            return `<span class="leaderboard-tier-badge" style="color: ${color};">${tier.toUpperCase()}</span>`;
        }
    
        getAgentAvatar(agent) {
            if (!agent) {
                return `<div class="leaderboard-avatar leaderboard-avatar-fallback">?</div>`;
            }
    
            if (agent.avatar && agent.avatar.startsWith('data:image')) {
                return `<img src="${agent.avatar}" class="leaderboard-avatar" alt="${escapeHtml(agent.name || 'Agent')}">`;
            } else if (agent.emoji) {
                return `<div class="leaderboard-avatar leaderboard-avatar-emoji">${agent.emoji}</div>`;
            } else {
                const initial = (agent.name || 'A').charAt(0).toUpperCase();
                return `<div class="leaderboard-avatar leaderboard-avatar-initial">${initial}</div>`;
            }
        }
    
        async loadData(force = false) {
            const container = this.container.querySelector(`#credits-leaderboard-${this.instance.instanceId}`);
            if (!container) return;
            if (force) {
                container.innerHTML = '<div class="credits-leaderboard-loading">Loading leaderboard...</div>';
            }
    
            const formatCredits = (value) => {
                if (Number.isInteger(value)) {
                    return value;
                }
                return Number(value).toFixed(1);
            };
            const formatDelta = (value) => {
                const num = Number(value || 0);
                const abs = Math.abs(num);
                const amount = Number.isInteger(abs) ? abs : abs.toFixed(1);
                return num >= 0 ? `+${amount}` : `-${amount}`;
            };
    
            try {
                const profiles = await creditApi.listProfiles();
                const agents = state.agents.list || [];
                const agentById = new Map(agents.map(agent => [agent.id, agent]));
    
                if (!profiles || profiles.length === 0) {
                    container.innerHTML = '<div class="workbench-placeholder">No credits yet.</div>';
                    return;
                }
    
                const maxCredits = profiles[0]?.currentCredits || 1;
    
                container.innerHTML = `
                    <div class="credits-leaderboard-list">
                        ${profiles.map((profile, index) => {
                            const agent = agentById.get(profile.agentId);
                            const name = agent?.name || profile.agentId || 'Unknown';
                            const credits = formatCredits(profile.currentCredits || 0);
                            const delta = formatDelta(profile.recentDelta || 0);
                            const tier = profile.reliabilityTier;
                            const deltaClass = (profile.recentDelta || 0) >= 0 ? 'positive' : 'negative';
                            const barWidth = Math.max(5, (profile.currentCredits / maxCredits) * 100);
    
                            return `
                                <div class="leaderboard-item rank-${index + 1}">
                                    <div class="leaderboard-rank">
                                        ${this.getRankIcon(index)}
                                    </div>
                                    ${this.getAgentAvatar(agent)}
                                    <div class="leaderboard-info">
                                        <div class="leaderboard-name-row">
                                            <span class="leaderboard-name">${escapeHtml(name)}</span>
                                            ${tier ? this.getTierBadge(tier) : ''}
                                        </div>
                                        <div class="leaderboard-credits-row">
                                            <span class="leaderboard-credits">${credits} credits</span>
                                            <span class="leaderboard-delta ${deltaClass}">${delta}</span>
                                        </div>
                                        <div class="leaderboard-bar-track">
                                            <div class="leaderboard-bar-fill" style="width: ${barWidth}%"></div>
                                        </div>
                                    </div>
                                </div>
                            `;
                        }).join('')}
                    </div>
                `;
            } catch (err) {
                container.innerHTML = `<div class="workbench-placeholder">Leaderboard unavailable.</div>`;
            }
        }
    }
    
    // Team Activity Widget - Last 24 hours (stub for now)
    class TeamActivityWidget extends Widget {
        async render() {
            if (!this.container) return;
    
            this.container.innerHTML = `
                <div class="widget-team-activity">
                    <div class="workbench-card-subtitle">Last 24 hours.</div>
                    <div class="workbench-placeholder">Telemetry and token usage are coming soon.</div>
                    <div class="workbench-card-detail">Token usage, active sessions, and throughput will appear here.</div>
                </div>
            `;
        }
    }
    
    // Dashboard State Manager
    class DashboardState {
        constructor() {
            this.workspaceId = 'default'; // Will be updated from workspace info
            this.widgets = [];
            this.mountedWidgets = new Map(); // instanceId -> Widget instance
        }
    
        async load() {
            console.log('[Dashboard] Loading layout from server...');
            try {
                const response = await fetch('/api/dashboard/layout');
                const result = await response.json();
                if (result.layout && result.layout.widgets) {
                    this.widgets = result.layout.widgets || [];
                    this.workspaceId = result.layout.workspaceId || 'default';
                    console.log('[Dashboard] Loaded widgets:', this.widgets.length);
                } else {
                    console.log('[Dashboard] No saved layout found');
                    this.widgets = [];
                }
            } catch (err) {
                console.error('[Dashboard] Failed to load layout:', err);
                this.widgets = [];
            }
        }
    
        async save() {
            const data = {
                workspaceId: this.workspaceId,
                version: 1,
                columns: 4,
                widgets: this.widgets
            };
            console.log('[Dashboard] Saving layout to server...', {widgetCount: this.widgets.length});
            try {
                await fetch('/api/dashboard/layout', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ layout: data })
                });
                console.log('[Dashboard] Layout saved successfully');
            } catch (err) {
                console.error('[Dashboard] Failed to save layout:', err);
            }
        }
    
        saveWidget(instance) {
            const index = this.widgets.findIndex(w => w.instanceId === instance.instanceId);
            if (index >= 0) {
                this.widgets[index] = instance;
            }
            this.save();
        }
    
        addWidget(instance) {
            instance.position = this.widgets.length;
            this.widgets.push(instance);
            this.save();
        }
    
        removeWidget(instanceId) {
            this.widgets = this.widgets.filter(w => w.instanceId !== instanceId);
            // Reindex positions
            this.widgets.forEach((w, i) => w.position = i);
            this.save();
        }
    }
    
    // Global instances
    const widgetRegistry = new WidgetRegistry();
    const dashboardState = new DashboardState();
    window.dashboardState = dashboardState; // Make available to widgets
    
    // Register built-in widgets
    function registerBuiltInWidgets() {
        // Quick Notes
        widgetRegistry.register({
            id: 'widget-quick-notes',
            name: 'Quick Notes',
            description: 'Jot down quick thoughts',
            icon: '📝',
            author: 'Control Room',
            version: '1.0.0',
            size: {
                default: 'small',
                allowedSizes: ['small', 'medium', 'large']
            },
            configurable: true,
            settings: {
                showPreview: {
                    type: 'checkbox',
                    label: 'Show Markdown Preview',
                    default: false
                },
                content: {
                    type: 'text',
                    label: 'Content',
                    default: ''
                },
                lastModified: {
                    type: 'number',
                    label: 'Last Modified',
                    default: 0
                }
            }
        });
    
        // Today's Briefing
        widgetRegistry.register({
            id: 'widget-planner-briefing',
            name: 'Today\'s Briefing',
            description: 'Daily summary from your team lead',
            icon: '📋',
            author: 'Control Room',
            version: '1.0.0',
            size: {
                default: 'large',
                allowedSizes: ['medium', 'large']
            },
            configurable: false,
            settings: {}
        });
    
        // Issue Pulse
        widgetRegistry.register({
            id: 'widget-issue-pulse',
            name: 'Issue Pulse',
            description: 'Open vs resolved issue trends',
            icon: '📊',
            author: 'Control Room',
            version: '1.0.0',
            size: {
                default: 'small',
                allowedSizes: ['small', 'medium']
            },
            configurable: false,
            settings: {}
        });
    
        // Credits Leaderboard
        widgetRegistry.register({
            id: 'widget-credits-leaderboard',
            name: 'Credits Leaderboard',
            description: 'Top contributors by credits earned',
            icon: '🏆',
            author: 'Control Room',
            version: '1.0.0',
            size: {
                default: 'small',
                allowedSizes: ['small', 'medium']
            },
            configurable: false,
            settings: {}
        });
    
        // Team Activity
        widgetRegistry.register({
            id: 'widget-team-activity',
            name: 'Team Activity',
            description: 'Recent agent activity and telemetry',
            icon: '👥',
            author: 'Control Room',
            version: '1.0.0',
            size: {
                default: 'small',
                allowedSizes: ['small', 'medium']
            },
            configurable: false,
            settings: {}
        });
    }
    
    // Render widget-based dashboard
    async function renderWidgetDashboard() {
        const container = document.getElementById('workbench-chat-content');
        if (!container) return;
    
        // Load saved layout (async)
        await dashboardState.load();
    
        container.innerHTML = `
            <div class="workbench-dashboard" id="widget-dashboard-grid"></div>
        `;
    
        const grid = document.getElementById('widget-dashboard-grid');
        if (!grid) return;
    
        // If no widgets, show empty state
        if (dashboardState.widgets.length === 0) {
            grid.innerHTML = `
                <div class="widget-empty-state">
                    <div class="widget-empty-icon">✨</div>
                    <h3>Your creative workspace awaits!</h3>
                    <p>Add widgets to customize your writer's command center.</p>
                    <button class="btn-primary" id="btn-add-first-widget" type="button">+ Add Your First Widget</button>
                </div>
            `;
    
            const addFirstBtn = document.getElementById('btn-add-first-widget');
            if (addFirstBtn) {
                addFirstBtn.addEventListener('click', () => {
                    showWidgetPicker();
                });
            }
        } else {
            // Render all widgets
            console.log('[Dashboard] Rendering widgets:', dashboardState.widgets);
            dashboardState.widgets
                .sort((a, b) => a.position - b.position)
                .forEach(instance => {
                    console.log('[Dashboard] Rendering widget:', instance.widgetId, instance.instanceId);
                    renderWidgetCard(grid, instance);
                });
        }
    }
    
    // Show hint pointing to Widgets button after widget is added
    function showWidgetHint() {
        if (!elements.btnWidgets) return;
    
        // Wait for widget animation to complete
        setTimeout(() => {
            // Add pulsing class
            elements.btnWidgets.classList.add('widget-hint-pulse');
    
            // Remove pulse after 3 pulses (4.5 seconds)
            setTimeout(() => {
                elements.btnWidgets.classList.remove('widget-hint-pulse');
            }, 4500);
        }, 400);
    }
    
    function renderWidgetCard(grid, instance) {
        const manifest = widgetRegistry.get(instance.widgetId);
        if (!manifest) {
            console.error('[Dashboard] Widget manifest not found for:', instance.widgetId);
            return;
        }
        console.log('[Dashboard] Rendering card for:', instance.widgetId);
    
        // Create widget wrapper
        const card = document.createElement('div');
        card.className = `workbench-card widget-card widget-size-${instance.size}`;
        card.dataset.instanceId = instance.instanceId;
        card.draggable = true;
    
        // Apply custom size if set
        if (instance.settings._customWidth) {
            card.style.width = instance.settings._customWidth + 'px';
        }
        if (instance.settings._customHeight) {
            card.style.height = instance.settings._customHeight + 'px';
        }
    
        // Widget header
        const header = document.createElement('div');
        header.className = 'widget-header';
        header.innerHTML = `
            <div class="widget-title">
                <span class="widget-icon">${manifest.icon}</span>
                <span class="widget-name">${manifest.name}</span>
            </div>
            <div class="widget-controls">
                <button class="widget-control-btn widget-remove-btn" type="button" title="Remove Widget">×</button>
            </div>
        `;
    
        // Widget content container
        const content = document.createElement('div');
        content.className = 'widget-body';
    
        // Resize handle
        const resizeHandle = document.createElement('div');
        resizeHandle.className = 'widget-resize-handle';
        resizeHandle.innerHTML = '⋰';
    
        card.appendChild(header);
        card.appendChild(content);
        card.appendChild(resizeHandle);
        grid.appendChild(card);
    
        // Mount animation
        requestAnimationFrame(() => {
            card.classList.add('widget-mounting');
            setTimeout(() => {
                card.classList.remove('widget-mounting');
                card.classList.add('widget-mounted');
            }, 300);
        });
    
        // Create and mount widget
        let widget;
        switch (instance.widgetId) {
            case 'widget-quick-notes':
                widget = new QuickNotesWidget(instance, manifest);
                break;
            case 'widget-planner-briefing':
                widget = new PlannerBriefingWidget(instance, manifest);
                break;
            case 'widget-issue-pulse':
                widget = new IssuePulseWidget(instance, manifest);
                break;
            case 'widget-credits-leaderboard':
                widget = new CreditsLeaderboardWidget(instance, manifest);
                break;
            case 'widget-team-activity':
                widget = new TeamActivityWidget(instance, manifest);
                break;
            default:
                widget = new Widget(instance, manifest);
        }
    
        widget.mount(content);
        dashboardState.mountedWidgets.set(instance.instanceId, widget);
    
        // Wire remove button
        const removeBtn = header.querySelector('.widget-remove-btn');
        if (removeBtn) {
            removeBtn.addEventListener('click', () => {
                if (confirm(`Remove ${manifest.name} widget?`)) {
                    removeWidgetCard(instance.instanceId);
                }
            });
        }
    
        // Wire resize handle
        initWidgetResize(card, resizeHandle, instance);
    
        // Wire drag-and-drop
        initWidgetDragDrop(card, instance);
    }
    
    function initWidgetResize(card, handle, instance) {
        let isResizing = false;
        let startX = 0;
        let startY = 0;
        let startWidth = 0;
        let startHeight = 0;
    
        handle.addEventListener('mousedown', (e) => {
            e.preventDefault();
            e.stopPropagation();
    
            isResizing = true;
            startX = e.clientX;
            startY = e.clientY;
    
            const rect = card.getBoundingClientRect();
            startWidth = rect.width;
            startHeight = rect.height;
    
            card.classList.add('widget-resizing');
            document.body.style.cursor = 'nwse-resize';
            document.body.style.userSelect = 'none';
        });
    
        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;
    
            const deltaX = e.clientX - startX;
            const deltaY = e.clientY - startY;
    
            const newWidth = Math.max(200, startWidth + deltaX);
            const newHeight = Math.max(150, startHeight + deltaY);
    
            card.style.width = newWidth + 'px';
            card.style.height = newHeight + 'px';
    
            // Store custom size
            instance.settings._customWidth = newWidth;
            instance.settings._customHeight = newHeight;
        });
    
        document.addEventListener('mouseup', () => {
            if (!isResizing) return;
    
            isResizing = false;
            card.classList.remove('widget-resizing');
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
    
            // Save the new size
            dashboardState.saveWidget(instance);
        });
    }
    
    // Shared drag state for all widgets
    let currentDraggedCard = null;
    let currentDraggedInstance = null;
    
    function initWidgetDragDrop(card, instance) {
        card.addEventListener('dragstart', (e) => {
            // Prevent drag from starting on interactive elements
            const target = e.target;
            if (target.tagName === 'TEXTAREA' ||
                target.tagName === 'INPUT' ||
                target.tagName === 'BUTTON' ||
                target.closest('.widget-body')) {
                e.preventDefault();
                return;
            }
    
            currentDraggedCard = card;
            currentDraggedInstance = instance;
            card.classList.add('widget-dragging');
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/html', card.innerHTML);
        });
    
        card.addEventListener('dragend', (e) => {
            card.classList.remove('widget-dragging');
            // Remove all drop zone indicators
            document.querySelectorAll('.widget-card').forEach(el => {
                el.classList.remove('widget-drop-target');
            });
            currentDraggedCard = null;
            currentDraggedInstance = null;
        });
    
        card.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
    
            if (currentDraggedCard && currentDraggedCard !== card) {
                card.classList.add('widget-drop-target');
            }
        });
    
        card.addEventListener('dragleave', (e) => {
            card.classList.remove('widget-drop-target');
        });
    
        card.addEventListener('drop', (e) => {
            e.preventDefault();
            e.stopPropagation();
    
            card.classList.remove('widget-drop-target');
    
            if (!currentDraggedCard || !currentDraggedInstance || currentDraggedCard === card) {
                return;
            }
    
            // Get current positions
            const draggedPos = currentDraggedInstance.position;
            const targetPos = instance.position;
    
            console.log('[Dashboard] Swapping positions:', draggedPos, '<->', targetPos);
    
            // Swap positions in the data
            currentDraggedInstance.position = targetPos;
            instance.position = draggedPos;
    
            // Update all widgets
            dashboardState.saveWidget(currentDraggedInstance);
            dashboardState.saveWidget(instance);
    
            // Re-render the dashboard to show new order
            const grid = document.getElementById('widget-dashboard-grid');
            if (grid) {
                grid.innerHTML = '';
                dashboardState.widgets
                    .sort((a, b) => a.position - b.position)
                    .forEach(inst => {
                        renderWidgetCard(grid, inst);
                    });
            }
        });
    }
    
    function removeWidgetCard(instanceId) {
        const card = document.querySelector(`[data-instance-id="${instanceId}"]`);
        if (!card) return;
    
        // Unmount widget
        const widget = dashboardState.mountedWidgets.get(instanceId);
        if (widget) {
            widget.unmount();
            dashboardState.mountedWidgets.delete(instanceId);
        }
    
        // Unmount animation
        card.classList.add('widget-unmounting');
        setTimeout(() => {
            card.remove();
            dashboardState.removeWidget(instanceId);
    
            // Check if dashboard is now empty
            const grid = document.getElementById('widget-dashboard-grid');
            if (grid && dashboardState.widgets.length === 0) {
                renderWidgetDashboard();
            }
        }, 300);
    }
    
    function showWidgetPicker() {
        if (!createModalShell) {
            throw new Error('createModalShell is not available');
        }
        const modalShell = createModalShell('Add Widget', '', 'Close');
    
        // Make modal wider for grid layout
        if (modalShell.modal) {
            modalShell.modal.classList.add('widget-picker-modal');
        }
        if (modalShell.confirmBtn) {
            modalShell.confirmBtn.style.display = 'none';
        }
        if (modalShell.cancelBtn) {
            modalShell.cancelBtn.textContent = 'Close';
        }
    
        const availableWidgets = widgetRegistry.list();
    
        modalShell.body.innerHTML = `
            <div class="widget-picker">
                <div class="widget-picker-grid">
                    ${availableWidgets.map(manifest => `
                        <div class="widget-picker-card" data-widget-id="${manifest.id}">
                            <div class="widget-picker-icon">${manifest.icon}</div>
                            <div class="widget-picker-name">${manifest.name}</div>
                            <div class="widget-picker-description">${manifest.description}</div>
                            <button class="btn-primary widget-picker-add-btn" type="button">Add</button>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    
        // Wire add buttons
        modalShell.body.querySelectorAll('.widget-picker-add-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const card = e.target.closest('.widget-picker-card');
                const widgetId = card.dataset.widgetId;
                addWidgetToGrid(widgetId);
                modalShell.close();
            });
        });
    
        // Show modal
        setTimeout(() => {
            modalShell.overlay.classList.add('is-visible');
        }, 10);
    }
    
    function addWidgetToGrid(widgetId) {
        const instance = widgetRegistry.createInstance(widgetId);
        dashboardState.addWidget(instance);
    
        const grid = document.getElementById('widget-dashboard-grid');
        if (!grid) {
            // If we're in empty state, re-render the whole dashboard
            renderWidgetDashboard();
            showWidgetHint();
            return;
        }
    
        // Clear empty state if present
        const emptyState = grid.querySelector('.widget-empty-state');
        if (emptyState) {
            emptyState.remove();
        }
    
        renderWidgetCard(grid, instance);
        showWidgetHint();
    }
    

    window.widgetRegistry = widgetRegistry;
    window.registerBuiltInWidgets = registerBuiltInWidgets;
    window.renderWidgetDashboard = renderWidgetDashboard;
    window.showWidgetPicker = showWidgetPicker;
})();
