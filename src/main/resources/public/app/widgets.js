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
    // Note: notificationStore must be accessed dynamically via window.notificationStore
    // because it's not yet initialized when this module loads

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
            const defaultSize = this.getDefaultDimensions(manifest.size.default);

            return {
                instanceId: this.generateId(),
                widgetId,
                x: 0,  // Will be set by findNonOverlappingPosition
                y: 0,
                width: defaultSize.width,
                height: defaultSize.height,
                settings: { ...defaultSettings, ...settings }
            };
        }

        getDefaultDimensions(sizeClass) {
            const sizes = {
                small: { width: 280, height: 220 },
                medium: { width: 380, height: 300 },
                large: { width: 520, height: 380 }
            };
            return sizes[sizeClass] || sizes.small;
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
            this.isFocused = false; // Cinema mode flag
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

        // Render enhanced focused version (cinema mode)
        // Subclasses can override for custom focused views
        async renderFocused(container) {
            // Store original state for restoration
            this._originalContainer = this.container;
            this.container = container;
            this.isFocused = true;
            await this.render();
            this.attachEventListeners();
            // Note: container and isFocused remain set for modal lifetime
            // Call exitFocusMode() when modal closes
        }

        // Called when focus modal closes to restore widget state
        exitFocusMode() {
            if (this._originalContainer) {
                this.container = this._originalContainer;
                this._originalContainer = null;
            }
            this.isFocused = false;
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

            // Enhanced focused view: side-by-side editor and preview
            if (this.isFocused) {
                this.container.innerHTML = `
                    <div class="widget-quick-notes widget-quick-notes-focused">
                        <div class="widget-quick-notes-toolbar">
                            <span class="widget-quick-notes-meta">
                                ${content.length} chars
                                ${lastModified ? '· ' + this.formatTimeAgo(lastModified) : ''}
                            </span>
                        </div>
                        <div class="widget-quick-notes-split">
                            <div class="widget-quick-notes-split-pane">
                                <div class="widget-quick-notes-pane-label">Edit</div>
                                <textarea class="widget-quick-notes-textarea" placeholder="Jot down your thoughts...">${escapeHtml(content)}</textarea>
                            </div>
                            <div class="widget-quick-notes-split-pane">
                                <div class="widget-quick-notes-pane-label">Preview</div>
                                <div class="widget-quick-notes-preview">${this.renderMarkdown(content)}</div>
                            </div>
                        </div>
                    </div>
                `;
                return;
            }

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
                    // Live preview update in focused mode
                    if (this.isFocused) {
                        const preview = this.container.querySelector('.widget-quick-notes-preview');
                        if (preview) {
                            preview.innerHTML = this.renderMarkdown(e.target.value);
                        }
                    }
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

    // Warmth Widget - Color temperature control
    class WarmthWidget extends Widget {
        constructor(instance, manifest) {
            super(instance, manifest);
            this.PRESETS = {
                cool: { value: -60, label: 'Cool', icon: '❄️' },
                neutral: { value: 0, label: 'Neutral', icon: '⚖️' },
                warm: { value: 40, label: 'Warm', icon: '☀️' },
                sunset: { value: 70, label: 'Sunset', icon: '🌅' }
            };
        }

        getStorageKey() {
            const workspaceId = window.state?.workspace?.id || 'default';
            return `warmth-${workspaceId}`;
        }

        loadValue() {
            const saved = localStorage.getItem(this.getStorageKey());
            return saved !== null ? parseInt(saved, 10) : 0;
        }

        saveValue(value) {
            localStorage.setItem(this.getStorageKey(), value.toString());
        }

        getActivePreset(value) {
            for (const [key, preset] of Object.entries(this.PRESETS)) {
                if (Math.abs(preset.value - value) <= 5) {
                    return key;
                }
            }
            return null;
        }

        async render() {
            if (!this.container) return;

            const currentValue = this.loadValue();

            this.container.innerHTML = `
                <div class="widget-warmth">
                    <div class="widget-warmth-header">
                        <span class="widget-warmth-icon">🎨</span>
                        <span class="widget-warmth-title">Color Warmth</span>
                    </div>
                    <div class="widget-warmth-slider-row">
                        <span class="widget-warmth-label-left">❄️</span>
                        <input type="range"
                               class="widget-warmth-slider"
                               min="-100"
                               max="100"
                               value="${currentValue}"
                               aria-label="Color warmth">
                        <span class="widget-warmth-label-right">🔥</span>
                    </div>
                    <div class="widget-warmth-value">${currentValue > 0 ? '+' : ''}${currentValue}</div>
                    <div class="widget-warmth-presets">
                        ${Object.entries(this.PRESETS).map(([key, preset]) => `
                            <button class="widget-warmth-preset ${this.getActivePreset(currentValue) === key ? 'active' : ''}"
                                    data-preset="${key}"
                                    title="${preset.label}">
                                ${preset.icon}
                            </button>
                        `).join('')}
                    </div>
                    <div class="widget-warmth-hint">Use [ ] keys to adjust</div>
                </div>
            `;
        }

        attachEventListeners() {
            if (!this.container) return;

            const slider = this.container.querySelector('.widget-warmth-slider');
            if (slider) {
                slider.addEventListener('input', (e) => {
                    const value = parseInt(e.target.value, 10);
                    if (window.applyWarmth) window.applyWarmth(value);
                    this.updateDisplay(value);
                });

                slider.addEventListener('change', (e) => {
                    const value = parseInt(e.target.value, 10);
                    this.saveValue(value);
                });
            }

            // Preset buttons
            this.container.querySelectorAll('.widget-warmth-preset').forEach(btn => {
                btn.addEventListener('click', () => {
                    const presetKey = btn.dataset.preset;
                    const preset = this.PRESETS[presetKey];
                    if (preset && slider) {
                        slider.value = preset.value;
                        if (window.applyWarmth) window.applyWarmth(preset.value);
                        this.updateDisplay(preset.value);
                        this.saveValue(preset.value);
                    }
                });
            });
        }

        updateDisplay(value) {
            const valueEl = this.container.querySelector('.widget-warmth-value');
            if (valueEl) {
                valueEl.textContent = `${value > 0 ? '+' : ''}${value}`;
            }

            // Update preset buttons
            const activePreset = this.getActivePreset(value);
            this.container.querySelectorAll('.widget-warmth-preset').forEach(btn => {
                btn.classList.toggle('active', btn.dataset.preset === activePreset);
            });
        }
    }

    // Ambient Sound Widget - Background soundscapes
    class AmbientSoundWidget extends Widget {
        constructor(instance, manifest) {
            super(instance, manifest);
            this.audioElements = new Map(); // filename -> Audio element
            this.activeSounds = new Set();  // Currently playing sounds
            this.volume = instance.settings.volume ?? 0.5;
            // Default icons for common sound types
            this.SOUND_ICONS = {
                'rain': '🌧️',
                'thunder': '⛈️',
                'storm': '⛈️',
                'typhoon': '🌀',
                'fire': '🔥',
                'fireplace': '🔥',
                'forest': '🌲',
                'garden': '🌿',
                'ocean': '🌊',
                'waves': '🌊',
                'cafe': '☕',
                'coffee': '☕',
                'wind': '💨',
                'chime': '🎐',
                'birds': '🐦',
                'summer': '☀️',
                'night': '🌙',
                'crickets': '🦗',
                'river': '🏞️',
                'stream': '🏞️',
                'default': '🎵'
            };
        }

        getIconForSound(displayName) {
            const nameLower = displayName.toLowerCase();
            for (const [key, icon] of Object.entries(this.SOUND_ICONS)) {
                if (nameLower.includes(key)) {
                    return icon;
                }
            }
            return this.SOUND_ICONS.default;
        }

        async render() {
            if (!this.container) return;

            // Show loading state
            this.container.innerHTML = `
                <div class="widget-ambient-sound">
                    <div class="widget-ambient-loading">Loading sounds...</div>
                </div>
            `;

            // Fetch available sounds from API
            try {
                const response = await fetch('/api/audio');
                const data = await response.json();
                const files = data.files || [];

                if (files.length === 0) {
                    this.container.innerHTML = `
                        <div class="widget-ambient-sound">
                            <div class="widget-ambient-empty">
                                <div class="widget-ambient-empty-icon">🎵</div>
                                <div class="widget-ambient-empty-text">No sounds found</div>
                                <div class="widget-ambient-empty-hint">Add .mp3 files to public/audio/</div>
                            </div>
                        </div>
                    `;
                    return;
                }

                // Restore active sounds from settings
                const savedActive = this.instance.settings.activeSounds || [];
                this.activeSounds = new Set(savedActive);

                this.container.innerHTML = `
                    <div class="widget-ambient-sound">
                        <div class="widget-ambient-grid">
                            ${files.map(file => `
                                <button class="widget-ambient-btn ${this.activeSounds.has(file.filename) ? 'active' : ''}"
                                        data-filename="${escapeHtml(file.filename)}"
                                        data-path="${escapeHtml(file.path)}"
                                        title="${escapeHtml(file.displayName)}">
                                    <span class="widget-ambient-btn-icon">${this.getIconForSound(file.displayName)}</span>
                                    <span class="widget-ambient-btn-label">${escapeHtml(file.displayName)}</span>
                                </button>
                            `).join('')}
                        </div>
                        <div class="widget-ambient-controls">
                            <div class="widget-ambient-volume-row">
                                <span class="widget-ambient-volume-icon">🔈</span>
                                <input type="range"
                                       class="widget-ambient-volume"
                                       min="0"
                                       max="100"
                                       value="${Math.round(this.volume * 100)}"
                                       aria-label="Volume">
                                <span class="widget-ambient-volume-icon">🔊</span>
                            </div>
                            <div class="widget-ambient-status">
                                ${this.activeSounds.size > 0
                                    ? `Playing ${this.activeSounds.size} sound${this.activeSounds.size > 1 ? 's' : ''}`
                                    : 'Click a sound to play'}
                            </div>
                        </div>
                    </div>
                `;

                // Start playing any previously active sounds
                for (const filename of this.activeSounds) {
                    const btn = this.container.querySelector(`[data-filename="${filename}"]`);
                    if (btn) {
                        this.playSound(filename, btn.dataset.path);
                    }
                }
            } catch (err) {
                console.error('[AmbientSound] Failed to load sounds:', err);
                this.container.innerHTML = `
                    <div class="widget-ambient-sound">
                        <div class="widget-ambient-empty">
                            <div class="widget-ambient-empty-icon">⚠️</div>
                            <div class="widget-ambient-empty-text">Failed to load sounds</div>
                        </div>
                    </div>
                `;
            }
        }

        attachEventListeners() {
            if (!this.container) return;

            // Sound buttons
            this.container.querySelectorAll('.widget-ambient-btn').forEach(btn => {
                btn.addEventListener('click', () => {
                    const filename = btn.dataset.filename;
                    const path = btn.dataset.path;

                    if (this.activeSounds.has(filename)) {
                        this.stopSound(filename);
                        btn.classList.remove('active');
                    } else {
                        this.playSound(filename, path);
                        btn.classList.add('active');
                    }

                    this.updateStatus();
                    this.saveState();
                });
            });

            // Volume slider
            const volumeSlider = this.container.querySelector('.widget-ambient-volume');
            if (volumeSlider) {
                volumeSlider.addEventListener('input', (e) => {
                    this.volume = parseInt(e.target.value, 10) / 100;
                    this.updateAllVolumes();
                });

                volumeSlider.addEventListener('change', () => {
                    this.saveState();
                });
            }
        }

        playSound(filename, path) {
            // Create audio element if it doesn't exist
            if (!this.audioElements.has(filename)) {
                const audio = new Audio(path);
                audio.loop = true;
                audio.volume = this.volume;
                this.audioElements.set(filename, audio);
            }

            const audio = this.audioElements.get(filename);
            audio.volume = this.volume;
            audio.play().catch(err => {
                console.error('[AmbientSound] Failed to play:', filename, err);
            });
            this.activeSounds.add(filename);
        }

        stopSound(filename) {
            const audio = this.audioElements.get(filename);
            if (audio) {
                audio.pause();
                audio.currentTime = 0;
            }
            this.activeSounds.delete(filename);
        }

        updateAllVolumes() {
            for (const audio of this.audioElements.values()) {
                audio.volume = this.volume;
            }
        }

        updateStatus() {
            const statusEl = this.container.querySelector('.widget-ambient-status');
            if (statusEl) {
                statusEl.textContent = this.activeSounds.size > 0
                    ? `Playing ${this.activeSounds.size} sound${this.activeSounds.size > 1 ? 's' : ''}`
                    : 'Click a sound to play';
            }
        }

        saveState() {
            this.instance.settings.activeSounds = Array.from(this.activeSounds);
            this.instance.settings.volume = this.volume;
            this.saveToDashboard();
        }

        async unmount() {
            // Stop all sounds when widget is removed
            for (const audio of this.audioElements.values()) {
                audio.pause();
                audio.currentTime = 0;
            }
            this.audioElements.clear();
            this.activeSounds.clear();
            await super.unmount();
        }
    }

    // Pomodoro Timer Widget - Focus timer with work/break cycles
    class PomodoroWidget extends Widget {
        constructor(instance, manifest) {
            super(instance, manifest);
            this.workMinutes = instance.settings.workMinutes ?? 25;
            this.breakMinutes = instance.settings.breakMinutes ?? 5;
            this.longBreakMinutes = instance.settings.longBreakMinutes ?? 15;
            this.sessionsUntilLongBreak = instance.settings.sessionsUntilLongBreak ?? 4;

            this.timeRemaining = this.workMinutes * 60; // seconds
            this.isRunning = false;
            this.isBreak = false;
            this.completedSessions = instance.settings.completedSessions ?? 0;
            this.intervalId = null;
        }

        async render() {
            if (!this.container) return;

            const minutes = Math.floor(this.timeRemaining / 60);
            const seconds = this.timeRemaining % 60;
            const timeStr = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
            const modeLabel = this.isBreak ? 'Break' : 'Focus';
            const modeClass = this.isBreak ? 'break' : 'focus';

            this.container.innerHTML = `
                <div class="widget-pomodoro ${modeClass}">
                    <div class="widget-pomodoro-display">
                        <div class="widget-pomodoro-mode">${modeLabel}</div>
                        <div class="widget-pomodoro-time">${timeStr}</div>
                        <div class="widget-pomodoro-sessions">
                            ${this.renderSessionDots()}
                        </div>
                    </div>
                    <div class="widget-pomodoro-controls">
                        <button class="widget-pomodoro-btn widget-pomodoro-start" title="${this.isRunning ? 'Pause' : 'Start'}">
                            ${this.isRunning ? '⏸' : '▶'}
                        </button>
                        <button class="widget-pomodoro-btn widget-pomodoro-reset" title="Reset">
                            ↺
                        </button>
                        <button class="widget-pomodoro-btn widget-pomodoro-skip" title="Skip to ${this.isBreak ? 'Focus' : 'Break'}">
                            ⏭
                        </button>
                    </div>
                </div>
            `;
        }

        renderSessionDots() {
            const dots = [];
            for (let i = 0; i < this.sessionsUntilLongBreak; i++) {
                const filled = i < (this.completedSessions % this.sessionsUntilLongBreak);
                dots.push(`<span class="widget-pomodoro-dot ${filled ? 'filled' : ''}"></span>`);
            }
            return dots.join('');
        }

        attachEventListeners() {
            if (!this.container) return;

            const startBtn = this.container.querySelector('.widget-pomodoro-start');
            const resetBtn = this.container.querySelector('.widget-pomodoro-reset');
            const skipBtn = this.container.querySelector('.widget-pomodoro-skip');

            if (startBtn) {
                startBtn.addEventListener('click', () => this.toggleTimer());
            }
            if (resetBtn) {
                resetBtn.addEventListener('click', () => this.resetTimer());
            }
            if (skipBtn) {
                skipBtn.addEventListener('click', () => this.skipPhase());
            }
        }

        toggleTimer() {
            if (this.isRunning) {
                this.pauseTimer();
            } else {
                this.startTimer();
            }
        }

        startTimer() {
            if (this.isRunning) return;
            this.isRunning = true;
            this.intervalId = setInterval(() => this.tick(), 1000);
            this.render();
            this.attachEventListeners();
        }

        pauseTimer() {
            this.isRunning = false;
            if (this.intervalId) {
                clearInterval(this.intervalId);
                this.intervalId = null;
            }
            this.render();
            this.attachEventListeners();
        }

        resetTimer() {
            this.pauseTimer();
            this.timeRemaining = (this.isBreak ? this.getBreakDuration() : this.workMinutes) * 60;
            this.render();
            this.attachEventListeners();
        }

        skipPhase() {
            this.pauseTimer();
            if (!this.isBreak) {
                // Skipping focus -> go to break (counts as completed session)
                this.completedSessions++;
                this.saveState();
            }
            this.switchPhase();
        }

        tick() {
            this.timeRemaining--;

            if (this.timeRemaining <= 0) {
                this.completePhase();
            } else {
                // Update display without full re-render for performance
                const timeEl = this.container.querySelector('.widget-pomodoro-time');
                if (timeEl) {
                    const minutes = Math.floor(this.timeRemaining / 60);
                    const seconds = this.timeRemaining % 60;
                    timeEl.textContent = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
                }
            }
        }

        completePhase() {
            this.pauseTimer();

            if (!this.isBreak) {
                // Completed a focus session
                this.completedSessions++;
                this.saveState();
                this.notifyUser('Focus session complete! Time for a break.');
            } else {
                this.notifyUser('Break over! Ready to focus?');
            }

            this.switchPhase();
        }

        switchPhase() {
            this.isBreak = !this.isBreak;
            this.timeRemaining = (this.isBreak ? this.getBreakDuration() : this.workMinutes) * 60;
            this.render();
            this.attachEventListeners();
        }

        getBreakDuration() {
            // Long break every N sessions
            if (this.completedSessions > 0 && this.completedSessions % this.sessionsUntilLongBreak === 0) {
                return this.longBreakMinutes;
            }
            return this.breakMinutes;
        }

        notifyUser(message) {
            // Try browser notification
            if ('Notification' in window && Notification.permission === 'granted') {
                new Notification('Pomodoro Timer', { body: message, icon: '🍅' });
            } else if ('Notification' in window && Notification.permission !== 'denied') {
                Notification.requestPermission();
            }
            // Also play a subtle sound if available
            try {
                const audio = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1pbJB3aWVseHB3cX6EeW55eHRygId/enZ7f3x+hYF7dnZ6eH2HhHx2dHZ3fYmFfHZ2d3d7hoJ6dHV5e3+HhHx2dnl7gIeDfHZ2eXt/h4N7dXZ5e3+Hg3t1dnl7f4eDe3V2eXt/h4N7dXZ5e3+Hg3t1dnl7f4eDe3V2eXt/h4N7dXZ5e3+Hg3t1dnl7');
                audio.volume = 0.3;
                audio.play().catch(() => {});
            } catch (e) {}
        }

        saveState() {
            this.instance.settings.completedSessions = this.completedSessions;
            this.saveToDashboard();
        }

        async unmount() {
            if (this.intervalId) {
                clearInterval(this.intervalId);
                this.intervalId = null;
            }
            await super.unmount();
        }
    }

    // Scene Editor Widget - Recent files with focus editing modal
    class SceneEditorWidget extends Widget {
        constructor(instance, manifest) {
            super(instance, manifest);
            this.recentFiles = [];
            this.ttsAudio = null;
            this.ttsPlaying = false;
        }

        async render() {
            if (!this.container) return;

            this.container.innerHTML = `
                <div class="widget-scene-editor">
                    <div class="scene-editor-loading">Loading recent files...</div>
                </div>
            `;

            await this.loadRecentFiles();
        }

        async loadRecentFiles() {
            try {
                const versioningApi = window.versioningApi;
                if (!versioningApi) {
                    this.renderError('Versioning not available');
                    return;
                }

                const data = await versioningApi.snapshots();
                const snapshots = data.snapshots || [];

                // Extract unique files from snapshots, sorted by most recent edit
                const fileMap = new Map();
                for (const snapshot of snapshots) {
                    if (!snapshot.files) continue;
                    for (const file of snapshot.files) {
                        // Only include scene-like files (txt, md) from story directories
                        const path = file.path || '';
                        if (!this.isSceneFile(path)) continue;

                        const existing = fileMap.get(path);
                        const timestamp = new Date(snapshot.publishedAt).getTime();
                        if (!existing || timestamp > existing.timestamp) {
                            fileMap.set(path, {
                                path: path,
                                timestamp: timestamp,
                                snapshotId: snapshot.id,
                                snapshotName: snapshot.name
                            });
                        }
                    }
                }

                // Sort by timestamp (newest first) and take top N
                const maxFiles = this.instance.settings.maxFiles || 8;
                this.recentFiles = Array.from(fileMap.values())
                    .sort((a, b) => b.timestamp - a.timestamp)
                    .slice(0, maxFiles);

                this.renderFileList();
            } catch (err) {
                console.error('[SceneEditor] Failed to load recent files:', err);
                this.renderError('Failed to load recent files');
            }
        }

        isSceneFile(path) {
            // Include markdown and text files, typically scene content
            const lowerPath = path.toLowerCase();
            if (!lowerPath.endsWith('.md') && !lowerPath.endsWith('.txt')) return false;
            // Exclude system files
            if (lowerPath.includes('.control-room/')) return false;
            if (lowerPath.startsWith('compendium/')) return false;
            return true;
        }

        renderFileList() {
            if (!this.container) return;

            if (this.recentFiles.length === 0) {
                this.container.innerHTML = `
                    <div class="widget-scene-editor">
                        <div class="scene-editor-empty">
                            <div class="scene-editor-empty-icon">📝</div>
                            <div class="scene-editor-empty-text">No recent scenes</div>
                            <div class="scene-editor-empty-hint">Edit and publish scenes to see them here</div>
                        </div>
                    </div>
                `;
                return;
            }

            const fileListHtml = this.recentFiles.map((file, index) => {
                const fileName = file.path.split('/').pop();
                const dirPath = file.path.substring(0, file.path.lastIndexOf('/')) || '';
                const timeAgo = this.formatTimeAgo(file.timestamp);

                return `
                    <div class="scene-editor-file" data-index="${index}" data-path="${escapeHtml(file.path)}">
                        <div class="scene-editor-file-icon">📄</div>
                        <div class="scene-editor-file-info">
                            <div class="scene-editor-file-name">${escapeHtml(fileName)}</div>
                            <div class="scene-editor-file-meta">
                                ${dirPath ? `<span class="scene-editor-file-dir">${escapeHtml(dirPath)}</span>` : ''}
                                <span class="scene-editor-file-time">${timeAgo}</span>
                            </div>
                        </div>
                        <div class="scene-editor-file-arrow">→</div>
                    </div>
                `;
            }).join('');

            this.container.innerHTML = `
                <div class="widget-scene-editor">
                    <div class="scene-editor-file-list">
                        ${fileListHtml}
                    </div>
                </div>
            `;
        }

        renderError(message) {
            if (!this.container) return;
            this.container.innerHTML = `
                <div class="widget-scene-editor">
                    <div class="scene-editor-error">${escapeHtml(message)}</div>
                </div>
            `;
        }

        formatTimeAgo(timestamp) {
            const seconds = Math.floor((Date.now() - timestamp) / 1000);
            if (seconds < 60) return 'just now';
            if (seconds < 3600) return Math.floor(seconds / 60) + 'm ago';
            if (seconds < 86400) return Math.floor(seconds / 3600) + 'h ago';
            if (seconds < 604800) return Math.floor(seconds / 86400) + 'd ago';
            return new Date(timestamp).toLocaleDateString();
        }

        attachEventListeners() {
            if (!this.container) return;

            // Click on file to open in focus editor
            this.container.querySelectorAll('.scene-editor-file').forEach(el => {
                el.addEventListener('click', () => {
                    const path = el.dataset.path;
                    if (path) {
                        this.openFocusEditor(path);
                    }
                });
            });
        }

        async openFocusEditor(filePath) {
            const fileApi = window.fileApi;
            const issueApi = window.issueApi;
            if (!fileApi) {
                console.error('[SceneEditor] File API not available');
                return;
            }

            let originalContent = '';
            let currentContent = '';

            try {
                // Load file content (API returns plain text, not JSON)
                originalContent = await fileApi.getFile(filePath);
                if (typeof originalContent !== 'string') {
                    originalContent = '';
                }
                currentContent = originalContent;
            } catch (err) {
                console.error('[SceneEditor] Failed to load file:', err);
                window.notificationStore?.error('Failed to load file: ' + err.message, 'workbench');
                return;
            }

            const fileName = filePath.split('/').pop();
            const self = this;

            // Create focus editor modal
            const modalContent = document.createElement('div');
            modalContent.className = 'focus-editor-container';
            modalContent.innerHTML = `
                <div class="focus-editor-header">
                    <div class="focus-editor-title">
                        <span class="focus-editor-icon">📝</span>
                        <span class="focus-editor-filename">${escapeHtml(fileName)}</span>
                        <span class="focus-editor-path">${escapeHtml(filePath)}</span>
                    </div>
                    <div class="focus-editor-status">
                        <span class="focus-editor-dirty-indicator" style="display: none;">●</span>
                        <span class="focus-editor-status-text">No changes</span>
                    </div>
                </div>
                <div class="focus-editor-toolbar">
                    <div class="focus-editor-tts-controls">
                        <button class="focus-editor-btn focus-editor-tts-btn" type="button" title="Play TTS">
                            <span class="focus-editor-tts-icon">▶</span>
                            <span class="focus-editor-tts-label">Play</span>
                        </button>
                    </div>
                    <div class="focus-editor-word-count">0 words</div>
                </div>
                <div class="focus-editor-content">
                    <textarea class="focus-editor-textarea" placeholder="Start writing...">${escapeHtml(originalContent)}</textarea>
                </div>
                <div class="focus-editor-footer">
                    <div class="focus-editor-hint">Changes are not saved until you click Save</div>
                    <div class="focus-editor-actions">
                        <button class="focus-editor-btn focus-editor-cancel-btn" type="button">Cancel</button>
                        <button class="focus-editor-btn focus-editor-save-btn btn-primary" type="button" disabled>Save</button>
                    </div>
                </div>
            `;

            // Create modal backdrop
            const backdrop = document.createElement('div');
            backdrop.className = 'focus-editor-backdrop';
            backdrop.appendChild(modalContent);
            document.body.appendChild(backdrop);

            // Get references to elements
            const textarea = modalContent.querySelector('.focus-editor-textarea');
            const saveBtn = modalContent.querySelector('.focus-editor-save-btn');
            const cancelBtn = modalContent.querySelector('.focus-editor-cancel-btn');
            const ttsBtn = modalContent.querySelector('.focus-editor-tts-btn');
            const dirtyIndicator = modalContent.querySelector('.focus-editor-dirty-indicator');
            const statusText = modalContent.querySelector('.focus-editor-status-text');
            const wordCount = modalContent.querySelector('.focus-editor-word-count');

            // Update word count
            const updateWordCount = () => {
                const text = textarea.value.trim();
                const words = text ? text.split(/\s+/).length : 0;
                wordCount.textContent = `${words} word${words !== 1 ? 's' : ''}`;
            };
            updateWordCount();

            // Track dirty state
            const updateDirtyState = () => {
                const isDirty = textarea.value !== originalContent;
                saveBtn.disabled = !isDirty;
                dirtyIndicator.style.display = isDirty ? 'inline' : 'none';
                statusText.textContent = isDirty ? 'Unsaved changes' : 'No changes';
                updateWordCount();
            };

            textarea.addEventListener('input', () => {
                currentContent = textarea.value;
                updateDirtyState();
            });

            // TTS functionality
            ttsBtn.addEventListener('click', async () => {
                if (self.ttsPlaying) {
                    self.stopTts();
                    ttsBtn.querySelector('.focus-editor-tts-icon').textContent = '▶';
                    ttsBtn.querySelector('.focus-editor-tts-label').textContent = 'Play';
                    ttsBtn.classList.remove('playing');
                } else {
                    const textToRead = textarea.selectionStart !== textarea.selectionEnd
                        ? textarea.value.substring(textarea.selectionStart, textarea.selectionEnd)
                        : textarea.value;

                    if (!textToRead.trim()) {
                        window.notificationStore?.info('No text to read', 'workbench');
                        return;
                    }

                    ttsBtn.querySelector('.focus-editor-tts-icon').textContent = '⏹';
                    ttsBtn.querySelector('.focus-editor-tts-label').textContent = 'Stop';
                    ttsBtn.classList.add('playing');
                    await self.playTts(textToRead, () => {
                        ttsBtn.querySelector('.focus-editor-tts-icon').textContent = '▶';
                        ttsBtn.querySelector('.focus-editor-tts-label').textContent = 'Play';
                        ttsBtn.classList.remove('playing');
                    });
                }
            });

            // Cancel button
            cancelBtn.addEventListener('click', () => {
                const isDirty = textarea.value !== originalContent;
                if (isDirty) {
                    if (!confirm('Discard unsaved changes?')) return;
                }
                self.stopTts();
                backdrop.remove();
            });

            // Save button
            saveBtn.addEventListener('click', async () => {
                const newContent = textarea.value;
                if (newContent === originalContent) return;

                try {
                    saveBtn.disabled = true;
                    saveBtn.textContent = 'Saving...';

                    // Save the file
                    await fileApi.saveFile(filePath, newContent);

                    // Create diff for issue
                    const diff = self.createDiff(originalContent, newContent, filePath);

                    // Create issue for Chief of Staff
                    const chiefOfStaff = getChiefOfStaffAgent();
                    const issueData = {
                        title: `Manual edit: ${fileName}`,
                        body: `User manually edited ${filePath} via Focus Editor.\n\n**Changes:**\n\`\`\`diff\n${diff}\n\`\`\``,
                        priority: 'low',
                        tags: ['manual-edit', 'focus-editor'],
                        assignedTo: chiefOfStaff?.id || null
                    };

                    if (issueApi) {
                        const issue = await issueApi.create(issueData);
                        // Create persistent notification in newsfeed
                        if (issue && issue.id && window.notificationStore?.issueCreated) {
                            window.notificationStore.issueCreated(issue.id, issue.title, 'user', chiefOfStaff?.name || chiefOfStaff?.id);
                        }
                    }

                    // Update state
                    originalContent = newContent;
                    updateDirtyState();
                    saveBtn.textContent = 'Save';

                    // Close modal after successful save
                    self.stopTts();
                    backdrop.remove();
                } catch (err) {
                    console.error('[SceneEditor] Failed to save:', err);
                    window.notificationStore?.error('Failed to save: ' + err.message, 'workbench');
                    saveBtn.disabled = false;
                    saveBtn.textContent = 'Save';
                }
            });

            // Prevent closing via backdrop click (sticky modal)
            backdrop.addEventListener('click', (e) => {
                if (e.target === backdrop) {
                    // Do nothing - modal is sticky
                    // Could add a subtle shake animation here
                    modalContent.classList.add('focus-editor-shake');
                    setTimeout(() => modalContent.classList.remove('focus-editor-shake'), 300);
                }
            });

            // Prevent Escape from closing without confirmation
            const escapeHandler = (e) => {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    const isDirty = textarea.value !== originalContent;
                    if (isDirty) {
                        if (!confirm('Discard unsaved changes?')) return;
                    }
                    self.stopTts();
                    backdrop.remove();
                    document.removeEventListener('keydown', escapeHandler);
                }
            };
            document.addEventListener('keydown', escapeHandler);

            // Focus textarea
            textarea.focus();
        }

        async playTts(text, onEnd) {
            try {
                // Use Piper TTS via API
                const response = await fetch('/api/tts/test', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ text: text.substring(0, 5000) }) // Limit length
                });

                if (!response.ok) {
                    // Try system speech synthesis as fallback
                    if ('speechSynthesis' in window) {
                        const utterance = new SpeechSynthesisUtterance(text);
                        utterance.onend = () => {
                            this.ttsPlaying = false;
                            if (onEnd) onEnd();
                        };
                        utterance.onerror = () => {
                            this.ttsPlaying = false;
                            if (onEnd) onEnd();
                        };
                        this.ttsPlaying = true;
                        this.currentUtterance = utterance;
                        speechSynthesis.speak(utterance);
                        return;
                    }
                    throw new Error('TTS not available');
                }

                const audioBlob = await response.blob();
                const audioUrl = URL.createObjectURL(audioBlob);
                this.ttsAudio = new Audio(audioUrl);
                this.ttsAudio.onended = () => {
                    this.ttsPlaying = false;
                    URL.revokeObjectURL(audioUrl);
                    if (onEnd) onEnd();
                };
                this.ttsAudio.onerror = () => {
                    this.ttsPlaying = false;
                    URL.revokeObjectURL(audioUrl);
                    if (onEnd) onEnd();
                };
                this.ttsPlaying = true;
                await this.ttsAudio.play();
            } catch (err) {
                console.error('[SceneEditor] TTS error:', err);
                this.ttsPlaying = false;
                if (onEnd) onEnd();
                window.notificationStore?.error('TTS failed: ' + err.message, 'workbench');
            }
        }

        stopTts() {
            if (this.ttsAudio) {
                this.ttsAudio.pause();
                this.ttsAudio = null;
            }
            if (this.currentUtterance && 'speechSynthesis' in window) {
                speechSynthesis.cancel();
                this.currentUtterance = null;
            }
            this.ttsPlaying = false;
        }

        createDiff(oldText, newText, filePath) {
            // Simple line-by-line diff
            const oldLines = oldText.split('\n');
            const newLines = newText.split('\n');
            const diff = [];

            diff.push(`--- a/${filePath}`);
            diff.push(`+++ b/${filePath}`);

            // Simple diff: show removed and added lines
            const maxLen = Math.max(oldLines.length, newLines.length);
            let inChange = false;
            let changeStart = 0;
            let removedLines = [];
            let addedLines = [];

            for (let i = 0; i < maxLen; i++) {
                const oldLine = oldLines[i];
                const newLine = newLines[i];

                if (oldLine !== newLine) {
                    if (!inChange) {
                        inChange = true;
                        changeStart = i;
                    }
                    if (oldLine !== undefined) removedLines.push(oldLine);
                    if (newLine !== undefined) addedLines.push(newLine);
                } else if (inChange) {
                    // End of change block
                    diff.push(`@@ -${changeStart + 1},${removedLines.length} +${changeStart + 1},${addedLines.length} @@`);
                    removedLines.forEach(l => diff.push('-' + l));
                    addedLines.forEach(l => diff.push('+' + l));
                    inChange = false;
                    removedLines = [];
                    addedLines = [];
                }
            }

            // Handle trailing changes
            if (inChange) {
                diff.push(`@@ -${changeStart + 1},${removedLines.length} +${changeStart + 1},${addedLines.length} @@`);
                removedLines.forEach(l => diff.push('-' + l));
                addedLines.forEach(l => diff.push('+' + l));
            }

            return diff.join('\n');
        }

        async unmount() {
            this.stopTts();
            await super.unmount();
        }
    }

    // Writing Streak Widget - Calendar heatmap of writing activity
    class WritingStreakWidget extends Widget {
        async render() {
            if (!this.container) return;

            // Show loading state
            this.container.innerHTML = `
                <div class="widget-writing-streak">
                    <div class="writing-streak-loading">Loading activity...</div>
                </div>
            `;

            await this.loadData();
        }

        async loadData() {
            try {
                const versioningApi = window.versioningApi;
                if (!versioningApi) {
                    this.renderError('Versioning API not available');
                    return;
                }

                const data = await versioningApi.snapshots();
                const snapshots = data.snapshots || [];

                if (snapshots.length === 0) {
                    this.renderEmpty();
                    return;
                }

                const timeRange = this.instance.settings.timeRange || '90days';
                const metric = this.instance.settings.metric || 'words';
                const dateMap = this.aggregateByDate(snapshots, metric);
                const streaks = this.calculateStreaks(dateMap);
                const totalWords = this.calculateTotalWords(snapshots);

                this.renderHeatmap(dateMap, streaks, totalWords, timeRange, metric);
            } catch (err) {
                console.error('[WritingStreak] Failed to load data:', err);
                this.renderError('Failed to load activity data');
            }
        }

        aggregateByDate(snapshots, metric) {
            const dateMap = new Map();

            for (const snapshot of snapshots) {
                if (!snapshot.publishedAt) continue;

                const date = new Date(snapshot.publishedAt);
                const dateStr = this.formatDateKey(date);

                const existing = dateMap.get(dateStr) || { words: 0, snapshots: 0 };
                existing.words += (snapshot.addedWords || 0);
                existing.snapshots += 1;
                dateMap.set(dateStr, existing);
            }

            return dateMap;
        }

        formatDateKey(date) {
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            return `${year}-${month}-${day}`;
        }

        calculateStreaks(dateMap) {
            const today = new Date();
            today.setHours(0, 0, 0, 0);

            let currentStreak = 0;
            let bestStreak = 0;
            let tempStreak = 0;

            // Sort dates descending
            const sortedDates = Array.from(dateMap.keys()).sort().reverse();

            // Calculate current streak (must include today or yesterday)
            const todayStr = this.formatDateKey(today);
            const yesterday = new Date(today);
            yesterday.setDate(yesterday.getDate() - 1);
            const yesterdayStr = this.formatDateKey(yesterday);

            let checkDate = new Date(today);
            // Allow starting from yesterday if today has no activity yet
            if (!dateMap.has(todayStr) && dateMap.has(yesterdayStr)) {
                checkDate = yesterday;
            }

            while (true) {
                const dateStr = this.formatDateKey(checkDate);
                if (dateMap.has(dateStr)) {
                    currentStreak++;
                    checkDate.setDate(checkDate.getDate() - 1);
                } else {
                    break;
                }
            }

            // Calculate best streak
            for (let i = 0; i < sortedDates.length; i++) {
                const currentDate = new Date(sortedDates[i]);
                const prevDate = i > 0 ? new Date(sortedDates[i - 1]) : null;

                if (prevDate) {
                    const diffDays = Math.round((prevDate - currentDate) / (1000 * 60 * 60 * 24));
                    if (diffDays === 1) {
                        tempStreak++;
                    } else {
                        bestStreak = Math.max(bestStreak, tempStreak);
                        tempStreak = 1;
                    }
                } else {
                    tempStreak = 1;
                }
            }
            bestStreak = Math.max(bestStreak, tempStreak);

            return { current: currentStreak, best: bestStreak };
        }

        calculateTotalWords(snapshots) {
            return snapshots.reduce((sum, s) => sum + (s.addedWords || 0), 0);
        }

        getColorLevel(value, metric) {
            if (value === 0) return 0;

            if (metric === 'snapshots') {
                if (value >= 5) return 4;
                if (value >= 3) return 3;
                if (value >= 2) return 2;
                return 1;
            }

            // Words
            if (value >= 1000) return 4;
            if (value >= 500) return 3;
            if (value >= 100) return 2;
            return 1;
        }

        getMonthsInRange(timeRange) {
            const months = [];
            const today = new Date();
            let numMonths;

            switch (timeRange) {
                case '30days': numMonths = 2; break;
                case 'year': numMonths = 12; break;
                default: numMonths = 3; // 90days
            }

            for (let i = 0; i < numMonths; i++) {
                const date = new Date(today.getFullYear(), today.getMonth() - i, 1);
                months.push({
                    year: date.getFullYear(),
                    month: date.getMonth(),
                    label: date.toLocaleDateString('en-US', { month: 'short' }),
                    daysInMonth: new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
                });
            }

            return months.reverse(); // Oldest first
        }

        renderHeatmap(dateMap, streaks, totalWords, timeRange, metric) {
            const months = this.getMonthsInRange(timeRange);

            let gridHtml = '<div class="writing-streak-grid">';

            for (const monthInfo of months) {
                gridHtml += `<div class="writing-streak-month-row">`;
                gridHtml += `<div class="writing-streak-month-label">${monthInfo.label}</div>`;
                gridHtml += `<div class="writing-streak-days">`;

                for (let day = 1; day <= 31; day++) {
                    if (day > monthInfo.daysInMonth) {
                        gridHtml += `<div class="writing-streak-cell writing-streak-cell-empty"></div>`;
                        continue;
                    }

                    const dateStr = `${monthInfo.year}-${String(monthInfo.month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
                    const data = dateMap.get(dateStr) || { words: 0, snapshots: 0 };
                    const value = metric === 'snapshots' ? data.snapshots : data.words;
                    const level = this.getColorLevel(value, metric);

                    const tooltipText = value > 0
                        ? `${dateStr}: ${data.words.toLocaleString()} words, ${data.snapshots} snapshot${data.snapshots !== 1 ? 's' : ''}`
                        : `${dateStr}: No activity`;

                    gridHtml += `<div class="writing-streak-cell writing-streak-level-${level}" title="${escapeHtml(tooltipText)}" data-date="${dateStr}"></div>`;
                }

                gridHtml += `</div></div>`;
            }

            gridHtml += '</div>';

            // Format total words
            const wordsDisplay = totalWords >= 1000
                ? (totalWords / 1000).toFixed(1) + 'k'
                : totalWords.toString();

            this.container.innerHTML = `
                <div class="widget-writing-streak">
                    ${gridHtml}
                    <div class="writing-streak-stats">
                        <span class="writing-streak-stat">
                            <span class="writing-streak-stat-icon">🔥</span>
                            <span class="writing-streak-stat-value">${streaks.current}</span> day${streaks.current !== 1 ? 's' : ''}
                        </span>
                        <span class="writing-streak-stat">
                            Best: <span class="writing-streak-stat-value">${streaks.best}</span>
                        </span>
                        <span class="writing-streak-stat">
                            <span class="writing-streak-stat-value">${wordsDisplay}</span> words
                        </span>
                    </div>
                </div>
            `;
        }

        renderEmpty() {
            this.container.innerHTML = `
                <div class="widget-writing-streak">
                    <div class="writing-streak-empty">
                        <div class="writing-streak-empty-icon">✨</div>
                        <div class="writing-streak-empty-text">Start your streak today!</div>
                        <div class="writing-streak-empty-hint">Publish snapshots to track your progress</div>
                    </div>
                </div>
            `;
        }

        renderError(message) {
            this.container.innerHTML = `
                <div class="widget-writing-streak">
                    <div class="writing-streak-error">${escapeHtml(message)}</div>
                </div>
            `;
        }
    }

    // Dashboard State Manager
    class DashboardState {
        constructor() {
            this.workspaceId = 'default';
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
                    // Migrate old format if needed
                    this.migrateIfNeeded();
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

        // Migrate from old grid-based format to new freeform format
        migrateIfNeeded() {
            let needsSave = false;
            const WIDGET_GAP = 20;
            const PADDING = 20;

            this.widgets.forEach((widget, index) => {
                // Check if this is old format (has 'position' field instead of x/y)
                if (widget.position !== undefined && widget.x === undefined) {
                    console.log('[Dashboard] Migrating widget to freeform format:', widget.instanceId);
                    needsSave = true;

                    // Get dimensions from old format
                    const manifest = widgetRegistry.get(widget.widgetId);
                    const sizeClass = widget.size || (manifest?.size?.default) || 'small';
                    const defaultDims = widgetRegistry.getDefaultDimensions(sizeClass);

                    // Use custom size if set, otherwise default
                    widget.width = widget.settings?._customWidth || defaultDims.width;
                    widget.height = widget.settings?._customHeight || defaultDims.height;

                    // Clean up old settings
                    if (widget.settings) {
                        delete widget.settings._customWidth;
                        delete widget.settings._customHeight;
                    }

                    // Calculate position based on old position index
                    // Layout in rows, 3 widgets per row
                    const widgetsPerRow = 3;
                    const row = Math.floor(index / widgetsPerRow);
                    const col = index % widgetsPerRow;
                    const avgWidth = 300;

                    widget.x = PADDING + col * (avgWidth + WIDGET_GAP);
                    widget.y = PADDING + row * (250 + WIDGET_GAP);

                    // Remove old fields
                    delete widget.position;
                    delete widget.size;
                }
            });

            if (needsSave) {
                console.log('[Dashboard] Migration complete, saving...');
                this.save();
            }
        }

        async save() {
            const data = {
                workspaceId: this.workspaceId,
                version: 2, // New version for freeform format
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
            // Find a non-overlapping position for the new widget
            const position = this.findNonOverlappingPosition(instance.width, instance.height);
            instance.x = position.x;
            instance.y = position.y;
            this.widgets.push(instance);
            this.save();
        }

        removeWidget(instanceId) {
            this.widgets = this.widgets.filter(w => w.instanceId !== instanceId);
            this.save();
        }

        // Find first non-overlapping position for a new widget
        findNonOverlappingPosition(width, height) {
            const PADDING = 20;
            const GAP = 20;
            const grid = document.getElementById('widget-dashboard-grid');
            const containerWidth = grid ? grid.clientWidth - PADDING * 2 : 800;

            // Try positions in a grid pattern
            for (let y = PADDING; y < 2000; y += height + GAP) {
                for (let x = PADDING; x < containerWidth - width; x += 50) {
                    const testRect = { x, y, width, height };
                    let overlaps = false;

                    for (const widget of this.widgets) {
                        if (checkCollision(testRect, widget)) {
                            overlaps = true;
                            break;
                        }
                    }

                    if (!overlaps) {
                        return { x, y };
                    }
                }
            }

            // Fallback: place below all existing widgets
            let maxY = PADDING;
            for (const widget of this.widgets) {
                maxY = Math.max(maxY, widget.y + widget.height + GAP);
            }
            return { x: PADDING, y: maxY };
        }
    }

    // ============================================
    // COLLISION DETECTION
    // ============================================

    function checkCollision(rectA, rectB) {
        return !(
            rectA.x + rectA.width <= rectB.x ||
            rectA.x >= rectB.x + rectB.width ||
            rectA.y + rectA.height <= rectB.y ||
            rectA.y >= rectB.y + rectB.height
        );
    }

    function getContainerBounds() {
        const grid = document.getElementById('widget-dashboard-grid');
        if (!grid) return { width: 800, height: 600 };
        return {
            width: grid.clientWidth,
            height: Math.max(grid.clientHeight, grid.scrollHeight, 2000) // Allow scrolling
        };
    }

    // Calculate the minimum push distance to resolve a collision
    function calculatePushVector(dragged, target) {
        // Calculate overlap on each axis
        const overlapLeft = (dragged.x + dragged.width) - target.x;
        const overlapRight = (target.x + target.width) - dragged.x;
        const overlapTop = (dragged.y + dragged.height) - target.y;
        const overlapBottom = (target.y + target.height) - dragged.y;

        // Find the minimum push direction
        const minX = overlapLeft < overlapRight ? -overlapLeft : overlapRight;
        const minY = overlapTop < overlapBottom ? -overlapTop : overlapBottom;

        // Push in the direction with smallest overlap
        if (Math.abs(minX) < Math.abs(minY)) {
            return { x: minX, y: 0 };
        } else {
            return { x: 0, y: minY };
        }
    }

    // Try to push a widget and return the new positions if successful
    function tryPushWidgets(draggedInstance, allWidgets, depth = 0) {
        if (depth > 5) return null; // Prevent infinite recursion

        const PADDING = 20;
        const bounds = getContainerBounds();
        const pushResults = new Map(); // instanceId -> new position

        // Find all widgets that collide with the dragged widget
        const collidingWidgets = allWidgets.filter(w =>
            w.instanceId !== draggedInstance.instanceId &&
            checkCollision(draggedInstance, w)
        );

        if (collidingWidgets.length === 0) {
            return pushResults; // No collisions, success
        }

        for (const target of collidingWidgets) {
            const pushVector = calculatePushVector(draggedInstance, target);
            const newX = target.x + pushVector.x;
            const newY = target.y + pushVector.y;

            // Check bounds
            if (newX < PADDING || newY < PADDING ||
                newX + target.width > bounds.width - PADDING) {
                return null; // Can't push, would go out of bounds
            }

            // Create a test rect for the pushed widget
            const pushedRect = {
                ...target,
                x: newX,
                y: newY,
                instanceId: target.instanceId
            };

            // Check if this push causes new collisions (excluding the dragged widget)
            const wouldCollide = allWidgets.some(w =>
                w.instanceId !== draggedInstance.instanceId &&
                w.instanceId !== target.instanceId &&
                checkCollision(pushedRect, w)
            );

            if (wouldCollide) {
                // Try recursive push
                const recursiveResult = tryPushWidgets(
                    pushedRect,
                    allWidgets.filter(w => w.instanceId !== target.instanceId),
                    depth + 1
                );
                if (recursiveResult === null) {
                    return null; // Can't push cascade
                }
                // Merge recursive results
                for (const [id, pos] of recursiveResult) {
                    pushResults.set(id, pos);
                }
            }

            pushResults.set(target.instanceId, { x: newX, y: newY });
        }

        return pushResults;
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

        // Warmth
        widgetRegistry.register({
            id: 'widget-warmth',
            name: 'Warmth',
            description: 'Adjust color temperature',
            icon: '🎨',
            author: 'Control Room',
            version: '1.0.0',
            size: {
                default: 'small',
                allowedSizes: ['small']
            },
            configurable: false,
            settings: {}
        });

        // Ambient Sound
        widgetRegistry.register({
            id: 'widget-ambient-sound',
            name: 'Ambient Sound',
            description: 'Background soundscapes for focus',
            icon: '🎵',
            author: 'Control Room',
            version: '1.0.0',
            size: {
                default: 'small',
                allowedSizes: ['small', 'medium']
            },
            configurable: false,
            settings: {
                volume: {
                    type: 'number',
                    label: 'Volume',
                    default: 0.5
                },
                activeSounds: {
                    type: 'array',
                    label: 'Active Sounds',
                    default: []
                }
            }
        });

        // Pomodoro Timer
        widgetRegistry.register({
            id: 'widget-pomodoro',
            name: 'Pomodoro Timer',
            description: 'Focus timer with work/break cycles',
            icon: '🍅',
            author: 'Control Room',
            version: '1.0.0',
            size: {
                default: 'small',
                allowedSizes: ['small']
            },
            configurable: false,
            settings: {
                workMinutes: {
                    type: 'number',
                    label: 'Work Duration (min)',
                    default: 25
                },
                breakMinutes: {
                    type: 'number',
                    label: 'Break Duration (min)',
                    default: 5
                },
                longBreakMinutes: {
                    type: 'number',
                    label: 'Long Break (min)',
                    default: 15
                },
                sessionsUntilLongBreak: {
                    type: 'number',
                    label: 'Sessions Until Long Break',
                    default: 4
                },
                completedSessions: {
                    type: 'number',
                    label: 'Completed Sessions',
                    default: 0
                }
            }
        });

        // Writing Streak
        widgetRegistry.register({
            id: 'widget-writing-streak',
            name: 'Writing Streak',
            description: 'Calendar heatmap of writing activity',
            icon: '🔥',
            author: 'Control Room',
            version: '1.0.0',
            size: {
                default: 'medium',
                allowedSizes: ['medium', 'large']
            },
            configurable: true,
            settings: {
                timeRange: {
                    type: 'select',
                    label: 'Time Range',
                    default: '90days',
                    options: ['30days', '90days', 'year']
                },
                metric: {
                    type: 'select',
                    label: 'Metric',
                    default: 'words',
                    options: ['words', 'snapshots']
                }
            }
        });

        // Scene Editor
        widgetRegistry.register({
            id: 'widget-scene-editor',
            name: 'Scene Editor',
            description: 'Recent scenes with focus editing mode',
            icon: '✍️',
            author: 'Control Room',
            version: '1.0.0',
            size: {
                default: 'medium',
                allowedSizes: ['small', 'medium', 'large']
            },
            configurable: true,
            settings: {
                maxFiles: {
                    type: 'number',
                    label: 'Max Files',
                    default: 8
                }
            }
        });
    }

    // Render widget-based dashboard
    async function renderWidgetDashboard() {
        const container = document.getElementById('workbench-chat-content');
        if (!container) return;

        if (state && state.workspace && state.workspace.prepStage === 'none') {
            container.innerHTML = `
                <div class="widget-empty-state">
                    <div class="widget-empty-icon">✨</div>
                    <h3>Prepare the project to unlock widgets</h3>
                    <p>Run the Project Preparation Wizard to activate your workbench.</p>
                    <button class="btn-primary" id="btn-prepare-project-workbench" type="button">Prepare Project</button>
                </div>
            `;
            const prepareBtn = document.getElementById('btn-prepare-project-workbench');
            if (prepareBtn) {
                prepareBtn.addEventListener('click', () => {
                    if (window.showProjectPreparationWizard) {
                        window.showProjectPreparationWizard();
                    }
                });
            }
            return;
        }
    
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
            // Render all widgets (freeform - no sorting needed)
            console.log('[Dashboard] Rendering widgets:', dashboardState.widgets);
            dashboardState.widgets.forEach(instance => {
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
        console.log('[Dashboard] Rendering card for:', instance.widgetId, 'at', instance.x, instance.y);

        // Create widget wrapper
        const card = document.createElement('div');
        card.className = 'workbench-card widget-card';
        card.dataset.instanceId = instance.instanceId;

        // Apply absolute positioning
        card.style.left = instance.x + 'px';
        card.style.top = instance.y + 'px';
        card.style.width = instance.width + 'px';
        card.style.height = instance.height + 'px';
    
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
            case 'widget-warmth':
                widget = new WarmthWidget(instance, manifest);
                break;
            case 'widget-ambient-sound':
                widget = new AmbientSoundWidget(instance, manifest);
                break;
            case 'widget-pomodoro':
                widget = new PomodoroWidget(instance, manifest);
                break;
            case 'widget-writing-streak':
                widget = new WritingStreakWidget(instance, manifest);
                break;
            case 'widget-scene-editor':
                widget = new SceneEditorWidget(instance, manifest);
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

        // Wire click-to-focus (cinema mode)
        // Only trigger on click, not on drag or interactive elements
        let clickStartTime = 0;
        let clickStartPos = { x: 0, y: 0 };

        content.addEventListener('mousedown', (e) => {
            // Skip if clicking on interactive elements
            if (e.target.closest('button, a, input, textarea, select, [contenteditable]')) {
                return;
            }
            clickStartTime = Date.now();
            clickStartPos = { x: e.clientX, y: e.clientY };
        });

        content.addEventListener('click', (e) => {
            // Skip if clicking on interactive elements
            if (e.target.closest('button, a, input, textarea, select, [contenteditable]')) {
                return;
            }
            // Only trigger if it was a quick click (not a drag attempt)
            const clickDuration = Date.now() - clickStartTime;
            const clickDistance = Math.sqrt(
                Math.pow(e.clientX - clickStartPos.x, 2) +
                Math.pow(e.clientY - clickStartPos.y, 2)
            );
            // Quick click: less than 300ms and moved less than 5px
            if (clickDuration < 300 && clickDistance < 5) {
                showWidgetFocusModal(instance.instanceId);
            }
        });
    }
    
    function initWidgetResize(card, handle, instance) {
        let resizeState = null;

        handle.addEventListener('mousedown', (e) => {
            e.preventDefault();
            e.stopPropagation();

            resizeState = {
                startX: e.clientX,
                startY: e.clientY,
                startWidth: instance.width,
                startHeight: instance.height
            };

            card.classList.add('widget-resizing');
            document.body.style.cursor = 'nwse-resize';
            document.body.style.userSelect = 'none';

            document.addEventListener('mousemove', handleResizeMove);
            document.addEventListener('mouseup', handleResizeEnd);
        });

        function handleResizeMove(e) {
            if (!resizeState) return;

            const deltaX = e.clientX - resizeState.startX;
            const deltaY = e.clientY - resizeState.startY;

            const newWidth = Math.max(200, resizeState.startWidth + deltaX);
            const newHeight = Math.max(150, resizeState.startHeight + deltaY);

            // Create test rect to check collisions
            const testRect = {
                ...instance,
                width: newWidth,
                height: newHeight
            };

            // Try to push widgets
            const pushResults = tryPushWidgets(testRect, dashboardState.widgets);

            // Clear previous push indicators
            document.querySelectorAll('.widget-card').forEach(el => {
                if (el !== card) {
                    el.classList.remove('widget-being-pushed');
                }
            });

            if (pushResults === null) {
                // Can't resize - don't update
                return;
            }

            // Update visual size
            card.style.width = newWidth + 'px';
            card.style.height = newHeight + 'px';

            // Apply pushes visually
            for (const [id, pos] of pushResults) {
                const pushedCard = document.querySelector(`[data-instance-id="${id}"]`);
                if (pushedCard) {
                    pushedCard.classList.add('widget-being-pushed');
                    pushedCard.style.left = pos.x + 'px';
                    pushedCard.style.top = pos.y + 'px';
                }
            }

            // Store pending changes
            resizeState.newWidth = newWidth;
            resizeState.newHeight = newHeight;
            resizeState.pendingPushes = pushResults;
        }

        function handleResizeEnd() {
            if (!resizeState) return;

            document.removeEventListener('mousemove', handleResizeMove);
            document.removeEventListener('mouseup', handleResizeEnd);

            card.classList.remove('widget-resizing');
            document.body.style.cursor = '';
            document.body.style.userSelect = '';

            // Clear push indicators
            document.querySelectorAll('.widget-card').forEach(el => {
                el.classList.remove('widget-being-pushed');
            });

            // Apply changes if valid
            if (resizeState.newWidth !== undefined) {
                instance.width = resizeState.newWidth;
                instance.height = resizeState.newHeight;

                // Apply pushes
                if (resizeState.pendingPushes && resizeState.pendingPushes.size > 0) {
                    for (const [id, pos] of resizeState.pendingPushes) {
                        const pushedWidget = dashboardState.widgets.find(w => w.instanceId === id);
                        if (pushedWidget) {
                            pushedWidget.x = pos.x;
                            pushedWidget.y = pos.y;
                        }
                    }
                }

                dashboardState.save();
            } else {
                // Revert size
                card.style.width = instance.width + 'px';
                card.style.height = instance.height + 'px';

                // Revert pushed widgets
                dashboardState.widgets.forEach(w => {
                    if (w.instanceId !== instance.instanceId) {
                        const widgetCard = document.querySelector(`[data-instance-id="${w.instanceId}"]`);
                        if (widgetCard) {
                            widgetCard.style.left = w.x + 'px';
                            widgetCard.style.top = w.y + 'px';
                        }
                    }
                });
            }

            resizeState = null;
        }
    }
    
    // ============================================
    // FREEFORM DRAG-AND-DROP SYSTEM
    // ============================================

    let dragState = null; // { card, instance, startX, startY, offsetX, offsetY }

    function initWidgetDragDrop(card, instance) {
        const header = card.querySelector('.widget-header');
        if (!header) return;

        header.addEventListener('mousedown', (e) => {
            // Prevent drag from starting on buttons
            if (e.target.tagName === 'BUTTON' || e.target.closest('button')) {
                return;
            }

            e.preventDefault();
            startDrag(card, instance, e);
        });
    }

    function startDrag(card, instance, e) {
        const rect = card.getBoundingClientRect();
        const grid = document.getElementById('widget-dashboard-grid');
        const gridRect = grid.getBoundingClientRect();

        dragState = {
            card,
            instance,
            startX: instance.x,
            startY: instance.y,
            offsetX: e.clientX - rect.left,
            offsetY: e.clientY - rect.top,
            gridOffsetX: gridRect.left,
            gridOffsetY: gridRect.top,
            scrollLeft: grid.scrollLeft,
            scrollTop: grid.scrollTop
        };

        card.classList.add('widget-dragging');
        document.body.style.cursor = 'grabbing';
        document.body.style.userSelect = 'none';

        // Add document-level listeners
        document.addEventListener('mousemove', handleDragMove);
        document.addEventListener('mouseup', handleDragEnd);
    }

    function handleDragMove(e) {
        if (!dragState) return;

        const { card, instance, offsetX, offsetY, gridOffsetX, gridOffsetY, scrollLeft, scrollTop } = dragState;
        const grid = document.getElementById('widget-dashboard-grid');
        const PADDING = 20;

        // Calculate new position relative to grid
        let newX = e.clientX - gridOffsetX - offsetX + grid.scrollLeft;
        let newY = e.clientY - gridOffsetY - offsetY + grid.scrollTop;

        // Clamp to bounds (left and top only - allow extending right/bottom)
        newX = Math.max(PADDING, newX);
        newY = Math.max(PADDING, newY);

        // Update visual position immediately
        card.style.left = newX + 'px';
        card.style.top = newY + 'px';

        // Create a test rect for collision detection
        const testRect = {
            ...instance,
            x: newX,
            y: newY
        };

        // Try to push widgets
        const pushResults = tryPushWidgets(testRect, dashboardState.widgets);

        // Clear previous push indicators
        document.querySelectorAll('.widget-card').forEach(el => {
            el.classList.remove('widget-being-pushed', 'widget-push-blocked');
        });

        if (pushResults === null) {
            // Push blocked - show visual feedback
            card.classList.add('widget-push-blocked');
        } else {
            card.classList.remove('widget-push-blocked');

            // Apply pushes visually (but don't save yet)
            for (const [id, pos] of pushResults) {
                const pushedCard = document.querySelector(`[data-instance-id="${id}"]`);
                if (pushedCard) {
                    pushedCard.classList.add('widget-being-pushed');
                    pushedCard.style.left = pos.x + 'px';
                    pushedCard.style.top = pos.y + 'px';
                }
            }

            // Store push results for when drag ends
            dragState.pendingPushes = pushResults;
            dragState.newX = newX;
            dragState.newY = newY;
        }
    }

    function handleDragEnd(e) {
        if (!dragState) return;

        const { card, instance, pendingPushes, newX, newY, startX, startY } = dragState;

        // Remove document listeners
        document.removeEventListener('mousemove', handleDragMove);
        document.removeEventListener('mouseup', handleDragEnd);

        // Clear visual states
        card.classList.remove('widget-dragging', 'widget-push-blocked');
        document.body.style.cursor = '';
        document.body.style.userSelect = '';

        document.querySelectorAll('.widget-card').forEach(el => {
            el.classList.remove('widget-being-pushed');
        });

        // Check if we have a valid drop position
        if (pendingPushes !== undefined && newX !== undefined) {
            // Update dragged widget position
            instance.x = newX;
            instance.y = newY;

            // Apply all pushes to the data model
            if (pendingPushes && pendingPushes.size > 0) {
                for (const [id, pos] of pendingPushes) {
                    const pushedWidget = dashboardState.widgets.find(w => w.instanceId === id);
                    if (pushedWidget) {
                        pushedWidget.x = pos.x;
                        pushedWidget.y = pos.y;
                    }
                }
            }

            // Save all changes
            dashboardState.save();
            console.log('[Dashboard] Drag complete, saved new positions');
        } else {
            // Revert to original position
            card.style.left = startX + 'px';
            card.style.top = startY + 'px';

            // Revert any pushed widgets
            dashboardState.widgets.forEach(w => {
                const widgetCard = document.querySelector(`[data-instance-id="${w.instanceId}"]`);
                if (widgetCard && w.instanceId !== instance.instanceId) {
                    widgetCard.style.left = w.x + 'px';
                    widgetCard.style.top = w.y + 'px';
                }
            });
        }

        dragState = null;
    }

    // ============================================
    // WIDGET FOCUS MODE (CINEMA MODE)
    // ============================================

    function showWidgetFocusModal(instanceId) {
        const widget = dashboardState.mountedWidgets.get(instanceId);
        const instance = dashboardState.widgets.find(w => w.instanceId === instanceId);
        if (!widget || !instance) return;

        const manifest = widgetRegistry.get(instance.widgetId);
        if (!manifest) return;

        // Check if widget supports focus mode (default: true)
        if (manifest.focusable === false) return;

        // Create focus modal overlay
        const overlay = document.createElement('div');
        overlay.className = 'widget-focus-overlay';

        const focusModal = document.createElement('div');
        focusModal.className = 'widget-focus-modal';

        // Focus modal header
        const header = document.createElement('div');
        header.className = 'widget-focus-header';
        header.innerHTML = `
            <div class="widget-focus-title">
                <span class="widget-icon">${manifest.icon}</span>
                <span class="widget-name">${manifest.name}</span>
            </div>
            <button class="widget-focus-close-btn" type="button" title="Close (Esc)">×</button>
        `;

        // Focus modal body
        const body = document.createElement('div');
        body.className = 'widget-focus-body';

        focusModal.appendChild(header);
        focusModal.appendChild(body);
        overlay.appendChild(focusModal);
        document.body.appendChild(overlay);

        // Render widget in focused mode
        widget.renderFocused(body);

        // Animate in
        requestAnimationFrame(() => {
            overlay.classList.add('widget-focus-visible');
        });

        // Close function
        const closeFocusModal = () => {
            overlay.classList.remove('widget-focus-visible');
            overlay.classList.add('widget-focus-closing');
            // Restore widget state and re-render original widget with updated data
            widget.exitFocusMode();
            widget.render();
            widget.attachEventListeners();
            setTimeout(() => {
                overlay.remove();
            }, 300);
            document.removeEventListener('keydown', handleEscKey);
        };

        // ESC key handler
        const handleEscKey = (e) => {
            if (e.key === 'Escape') {
                closeFocusModal();
            }
        };
        document.addEventListener('keydown', handleEscKey);

        // Close button
        const closeBtn = header.querySelector('.widget-focus-close-btn');
        closeBtn.addEventListener('click', closeFocusModal);

        // Click outside to close
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) {
                closeFocusModal();
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

        // Scroll the new widget into view
        setTimeout(() => {
            const card = document.querySelector(`[data-instance-id="${instance.instanceId}"]`);
            if (card) {
                card.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }, 350); // Wait for mount animation

        showWidgetHint();
    }
    

    window.widgetRegistry = widgetRegistry;
    window.registerBuiltInWidgets = registerBuiltInWidgets;
    window.renderWidgetDashboard = renderWidgetDashboard;
    window.showWidgetPicker = showWidgetPicker;

    // ============================================
    // WARMTH UTILITIES (used by WarmthWidget)
    // ============================================

    function hslToHex(h, s, l) {
        s /= 100;
        l /= 100;
        const a = s * Math.min(l, 1 - l);
        const f = n => {
            const k = (n + h / 30) % 12;
            const color = l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1);
            return Math.round(255 * color).toString(16).padStart(2, '0');
        };
        return `#${f(0)}${f(8)}${f(4)}`;
    }

    function hslToRgb(h, s, l) {
        s /= 100;
        l /= 100;
        const a = s * Math.min(l, 1 - l);
        const f = n => {
            const k = (n + h / 30) % 12;
            return Math.round(255 * (l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1)));
        };
        return { r: f(0), g: f(8), b: f(4) };
    }

    function applyWarmth(value) {
        const BASE_HUE = 207;
        const hueShift = value * 0.5;
        const hue = BASE_HUE + hueShift;

        // Calculate warmth color for card backgrounds and border accents
        const warmthColor = hslToHex(hue, 70, 45);
        const warmthColorDark = hslToHex(hue, 70, 35);

        const rgb = hslToRgb(hue, 70, 45);

        // Card background tints - more visible opacity for actual background tinting
        const cardTint = `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.08)`;
        const cardTintHover = `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.14)`;
        const cardTintStrong = `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.18)`;

        // Border/edge accent colors - for card borders on hover/active states
        const warmthGlow = `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.25)`;
        const warmthGlowStrong = `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.4)`;

        const root = document.documentElement;
        // Card tinting
        root.style.setProperty('--card-tint', cardTint);
        root.style.setProperty('--card-tint-hover', cardTintHover);
        root.style.setProperty('--card-tint-strong', cardTintStrong);
        // Border/edge accents for warmth-responsive elements
        root.style.setProperty('--warmth-border', warmthColor);
        root.style.setProperty('--warmth-border-dark', warmthColorDark);
        root.style.setProperty('--warmth-glow', warmthGlow);
        root.style.setProperty('--warmth-glow-strong', warmthGlowStrong);
    }

    // Apply saved warmth on load
    function initWarmth() {
        const workspaceId = window.state?.workspace?.id || 'default';
        const saved = localStorage.getItem(`warmth-${workspaceId}`);
        const value = saved !== null ? parseInt(saved, 10) : 0;
        applyWarmth(value);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initWarmth);
    } else {
        initWarmth();
    }

    window.applyWarmth = applyWarmth;
})();
