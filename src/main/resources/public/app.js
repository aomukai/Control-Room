// Control Room Application
// State and API are loaded from state.js and api.js (window.state, window.api, etc.)
(function() {
    'use strict';

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

    // Modal helpers
    const showModal = window.modals.showModal;
    const createModalShell = window.modals.createModalShell;
    const showConfigurableModal = window.modals.showConfigurableModal;
    // escapeHtml is now in modals.js (window.modals.escapeHtml)
    const escapeHtml = window.modals.escapeHtml;

    // Confirmation dialog helper - returns a Promise<boolean>
    function showConfirmDialog(title, message, confirmLabel = 'Confirm', cancelLabel = 'Cancel') {
        return new Promise((resolve) => {
            showConfigurableModal({
                title,
                body: `<p class="modal-text">${escapeHtml(message)}</p>`,
                buttons: [
                    { label: cancelLabel, value: false, className: 'modal-btn modal-btn-secondary' },
                    { label: confirmLabel, value: true, className: 'modal-btn modal-btn-danger' }
                ],
                onResult: (value) => resolve(value)
            });
        });
    }
    const buildChatPrompt = window.buildChatPrompt;
    const extractStopHook = window.extractStopHook;
    const canonicalizeRole = window.canonicalizeRole;
    const isAssistantAgent = window.isAssistantAgent;
    const resolveAgentIdFromLabel = window.resolveAgentIdFromLabel;
    const setAgentActivityState = window.setAgentActivityState;
    const withAgentActivity = window.withAgentActivity;
    const renderAgentSidebar = window.renderAgentSidebar;
    const ensureChiefOfStaff = window.ensureChiefOfStaff;
    const updateAgentLockState = window.updateAgentLockState;
    const showAddAgentWizard = window.showAddAgentWizard;
    const showImportAgentDialog = window.showImportAgentDialog;
    const showRetiredAgentsModal = window.showRetiredAgentsModal;
    const showAssistedModeModal = window.showAssistedModeModal;
    const showConferenceInviteModal = window.showConferenceInviteModal;
    const initSplitters = window.initSplitters;
    const initMonaco = window.initMonaco;
    const loadFileTree = window.loadFileTree;
    const openFile = window.openFile;
    const openFileInNewTab = window.openFileInNewTab;
    const saveCurrentFile = window.saveCurrentFile;
    const saveAllFiles = window.saveAllFiles;
    const openWorkspaceSearch = window.openWorkspaceSearch;
    const performSearch = window.performSearch;
    const getExplorerVisible = window.getExplorerVisible;
    const setExplorerVisible = window.setExplorerVisible;
    const preparationApi = window.preparationApi;

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
            state.workspace.name = info.currentName || 'Project';
            state.workspace.path = info.currentPath || '';
            state.workspace.root = info.rootPath || '';
            state.workspace.available = Array.isArray(info.available) ? info.available : [];
            state.workspace.devMode = Boolean(info.devMode);
            const meta = info.metadata || {};
            state.workspace.displayName = meta.displayName || state.workspace.name || 'Project';
            state.workspace.description = meta.description || '';
            state.workspace.icon = meta.icon || '';
            state.workspace.accentColor = meta.accentColor || '';
            state.workspace.prepared = Boolean(meta.prepared);
            state.workspace.preparedMode = meta.preparedMode || '';
            state.workspace.preparedAt = meta.preparedAt || '';
            updateWorkspaceButton();
            const pendingPrep = (() => {
                try {
                    return JSON.parse(localStorage.getItem('cr-prep-pending') || 'null');
                } catch (_) {
                    return null;
                }
            })();
            if (!state.workspace.prepared) {
                if (pendingPrep && pendingPrep.name === state.workspace.name) {
                    await showProjectPreparationWizard();
                    return;
                }
            } else if (pendingPrep) {
                localStorage.removeItem('cr-prep-pending');
            }
            if (window.restoreEditorState) {
                await window.restoreEditorState();
            }
        } catch (err) {
            log(`Failed to load Project info: ${err.message}`, 'error');
        }
    }

    // ============================================
    // PATCH REVIEW MODAL
    // ============================================

    async function showPatchReviewModal(focusId, defaultView = 'diff') {
        let patchActivityAgentId = null;
        const { overlay, modal, body, close, confirmBtn } = createModalShell(
            'Patch Review',
            'Close',
            'Cancel',
            { closeOnCancel: true, onClose: () => {
                if (patchActivityAgentId) {
                    setAgentActivityState(patchActivityAgentId, 'idle');
                    patchActivityAgentId = null;
                }
            } }
        );
        modal.classList.add('patch-review-modal');
        confirmBtn.addEventListener('click', close);

        let currentPatchId = focusId || null;

        // Track current view mode for this modal instance
        let currentViewMode = defaultView; // 'editor' or 'diff'

        const headerRow = document.createElement('div');
        headerRow.className = 'modal-row space-between patch-diff-only';
        const headerActions = document.createElement('div');
        headerActions.className = 'patch-header-actions';
        const refreshBtn = document.createElement('button');
        refreshBtn.className = 'modal-btn modal-btn-secondary';
        refreshBtn.textContent = 'Refresh';
        headerActions.appendChild(refreshBtn);
        headerRow.appendChild(headerActions);
        body.appendChild(headerRow);

        const hint = document.createElement('div');
        hint.className = 'modal-hint';
        hint.textContent = 'Select a patch to view details.';
        body.appendChild(hint);

        const agentHeaderContainer = document.createElement('div');
        agentHeaderContainer.className = 'patch-agent-header-container';
        body.appendChild(agentHeaderContainer);

        const layout = document.createElement('div');
        layout.className = 'patch-layout';

        const listContainer = document.createElement('div');
        listContainer.className = 'patch-list patch-diff-only';
        layout.appendChild(listContainer);

        const detail = document.createElement('div');
        detail.className = 'patch-detail';
        detail.innerHTML = '<div class="patch-detail-empty">Select a patch to review</div>';
        layout.appendChild(detail);
        body.appendChild(layout);

        refreshBtn.addEventListener('click', () => loadPatches(currentPatchId));

        async function loadPatches(focusPatchId) {
            try {
                const res = await patchApi.list();
                const patches = res.patches || [];
                renderPatchList(patches);
                if (focusPatchId && patches.some(p => p.id === focusPatchId)) {
                    currentPatchId = focusPatchId;
                } else if (!patches.find(p => p.id === currentPatchId)) {
                    currentPatchId = patches.length ? patches[0].id : null;
                }
                if (currentPatchId) {
                    await openPatchDetail(currentPatchId);
                } else {
                    detail.innerHTML = '<div class="patch-detail-empty">No patches</div>';
                }
            } catch (err) {
                hint.textContent = `Failed to load patches: ${err.message}`;
            }
        }

        function renderPatchList(patches) {
            listContainer.innerHTML = '';
            if (!patches.length) {
                listContainer.innerHTML = '<div class="patch-empty">No patches</div>';
                return;
            }
            patches.forEach(patch => {
                const item = document.createElement('div');
                const isSelected = patch.id === currentPatchId;
                item.className = 'patch-item' + (isSelected ? ' is-selected' : '');
                const fileCount = Array.isArray(patch.files) ? patch.files.length : (patch.filePath ? 1 : 0);
                const fileLabel = Array.isArray(patch.files) && patch.files[0]
                    ? patch.files[0].filePath
                    : (patch.filePath || '');
                item.innerHTML = `
                    <div class="patch-item-title">${escapeHtml(patch.title || patch.id)}</div>
                    <div class="patch-item-meta">${escapeHtml(fileLabel)}${fileCount > 1 ? ` • ${fileCount} files` : ''}</div>
                    <div class="patch-item-status status-${patch.status || 'pending'}">${patch.status || 'pending'}</div>
                    <div class="patch-item-time">${formatRelativeTime(patch.createdAt)}</div>
                `;
                item.addEventListener('click', () => {
                    currentPatchId = patch.id;
                    renderPatchList(patches);
                    openPatchDetail(patch.id);
                });
                listContainer.appendChild(item);
            });
        }

        function renderAgentHeader(patch) {
            agentHeaderContainer.innerHTML = '';
            if (!patch || !patch.provenance) {
                if (patchActivityAgentId) {
                    setAgentActivityState(patchActivityAgentId, 'idle');
                    patchActivityAgentId = null;
                }
                return;
            }

            const provenance = patch.provenance;
            const agentName = provenance.agent || null;

            if (!agentName) {
                if (patchActivityAgentId) {
                    setAgentActivityState(patchActivityAgentId, 'idle');
                    patchActivityAgentId = null;
                }
                return;
            }

            // Find agent in state
            const agentId = resolveAgentIdFromLabel(agentName);
            const agent = (state.agents.list || []).find(a =>
                a.id === agentId || a.name === agentName
            );
            if (agentId) {
                if (patchActivityAgentId && patchActivityAgentId !== agentId) {
                    setAgentActivityState(patchActivityAgentId, 'idle');
                }
                patchActivityAgentId = agentId;
                setAgentActivityState(agentId, 'reading', `Reviewing patch ${patch.id || ''}`.trim());
            }

            const header = document.createElement('div');
            header.className = 'patch-agent-header';

            const avatarDiv = document.createElement('div');
            avatarDiv.className = 'patch-agent-avatar';

            // Render avatar
            if (agent) {
                if (agent.avatar && agent.avatar.startsWith('data:image')) {
                    const img = document.createElement('img');
                    img.src = agent.avatar;
                    img.alt = agent.name || agentName;
                    avatarDiv.appendChild(img);
                } else if (agent.emoji) {
                    const emoji = document.createElement('div');
                    emoji.className = 'patch-agent-avatar-emoji';
                    emoji.textContent = agent.emoji;
                    avatarDiv.appendChild(emoji);
                } else {
                    const initials = document.createElement('div');
                    initials.className = 'patch-agent-avatar-initials';
                    const name = agent.name || agentName;
                    initials.textContent = name.substring(0, 2).toUpperCase();
                    avatarDiv.appendChild(initials);
                }
            } else {
                // Fallback for unknown agent
                const initials = document.createElement('div');
                initials.className = 'patch-agent-avatar-initials';
                initials.textContent = agentName.substring(0, 2).toUpperCase();
                avatarDiv.appendChild(initials);
            }

            const infoDiv = document.createElement('div');
            infoDiv.className = 'patch-agent-info';

            const nameDiv = document.createElement('div');
            nameDiv.className = 'patch-agent-name';
            nameDiv.textContent = agent ? (agent.name || agentName) : agentName;

            const messageDiv = document.createElement('div');
            messageDiv.className = 'patch-agent-message';
            messageDiv.textContent = 'suggests the following changes...';

            infoDiv.appendChild(nameDiv);
            infoDiv.appendChild(messageDiv);

            header.appendChild(avatarDiv);
            header.appendChild(infoDiv);

            // Add action buttons to header (always visible, sticky)
            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'patch-agent-actions';
            actionsDiv.innerHTML = `
                <button class="modal-btn modal-btn-primary" id="btn-apply-patch">Apply</button>
                <button class="modal-btn modal-btn-secondary" id="btn-reject-patch">Reject</button>
                <button class="modal-btn modal-btn-ghost" id="btn-delete-patch">Delete</button>
                <button class="modal-btn modal-btn-ghost" id="btn-export-audit">Export Audit</button>
            `;
            header.appendChild(actionsDiv);

            agentHeaderContainer.appendChild(header);
        }

        function getPrimaryPatchFile(patch) {
            if (!patch) return '';
            if (Array.isArray(patch.files) && patch.files.length > 0 && patch.files[0]) {
                return patch.files[0].filePath || '';
            }
            return patch.filePath || '';
        }

        function buildPatchNotificationPayload(patch) {
            if (!patch) return {};
            const filePath = getPrimaryPatchFile(patch);
            const filePaths = Array.isArray(patch.files)
                ? patch.files.map(file => file && file.filePath).filter(Boolean)
                : [];
            const payload = {
                kind: 'review-patch',
                patchId: patch.id || null,
                filePath: filePath || null,
                filePaths,
                patchTitle: patch.title || null,
                provenance: patch.provenance || null
            };
            const projectName = state.workspace.displayName || state.workspace.name;
            if (projectName) {
                payload.projectName = projectName;
            }
            return payload;
        }

        async function createPatchFeedbackIssue(patch, action, editedFiles = null) {
            // Only create feedback if patch has agent provenance
            if (!patch.provenance || !patch.provenance.agent) {
                return;
            }

            const agentName = patch.provenance.agent;
            const patchTitle = patch.title || patch.id;
            const filePaths = Array.isArray(patch.files)
                ? patch.files.map(file => file && file.filePath).filter(Boolean)
                : [];

            let issueTitle;
            let issueBody;

            if (action === 'applied') {
                const hasEdits = editedFiles && editedFiles.length > 0;
                issueTitle = hasEdits
                    ? `Patch applied with edits: ${patchTitle}`
                    : `Patch applied: ${patchTitle}`;

                const fileSummary = filePaths.length > 0
                    ? filePaths.map(fp => `- ${fp}`).join('\n')
                    : 'Unknown file';

                issueBody = `User ${hasEdits ? 'applied your patch with manual edits' : 'applied your patch'}.\n\n**Patch**: ${patchTitle}\n**Files**:\n${fileSummary}`;

                if (hasEdits) {
                    issueBody += '\n\n**User edited the content** before applying. The final version may differ from your original suggestion.';
                }
            } else if (action === 'rejected') {
                issueTitle = `Patch rejected: ${patchTitle}`;

                const fileSummary = filePaths.length > 0
                    ? filePaths.map(fp => `- ${fp}`).join('\n')
                    : 'Unknown file';

                issueBody = `User rejected your patch.\n\n**Patch**: ${patchTitle}\n**Files**:\n${fileSummary}\n\nThe user decided not to apply these changes.`;
            }

            try {
                await issueApi.create({
                    title: issueTitle,
                    body: issueBody,
                    openedBy: 'User',
                    assignedTo: agentName,
                    tags: ['patch-feedback']
                });
            } catch (err) {
                console.warn('Failed to create patch feedback issue:', err);
                // Don't throw - feedback issue creation is nice-to-have, not critical
            }
        }

        function formatPatchApplyFailureDetails(errorMessage, fileErrorsList) {
            const parts = [];
            if (errorMessage) {
                parts.push(errorMessage);
            }
            const files = Array.isArray(fileErrorsList)
                ? fileErrorsList.map(entry => entry && entry.filePath).filter(Boolean)
                : [];
            if (files.length) {
                const preview = files.slice(0, 3).join(', ');
                const suffix = files.length > 3 ? `, +${files.length - 3} more` : '';
                parts.push(`Files: ${preview}${suffix}`);
            }
            return parts.join(' | ');
        }

        function pushPatchApplyFailureNotification(patch, errorMessage, fileErrorsList) {
            const details = formatPatchApplyFailureDetails(errorMessage, fileErrorsList);
            const payload = buildPatchNotificationPayload(patch);
            payload.fileErrors = Array.isArray(fileErrorsList) ? fileErrorsList : [];
            notificationStore.push(
                'error',
                'editor',
                patch && patch.id ? `Failed to apply ${patch.id}` : 'Patch apply failed',
                details,
                'attention',
                true,
                'Review Patch',
                payload,
                'patch'
            );
        }

        async function openPatchDetail(id) {
            detail.innerHTML = '<div class="patch-detail-empty">Loading patch…</div>';
            agentHeaderContainer.innerHTML = '';
            try {
                const full = await patchApi.get(id);
                renderAgentHeader(full);
                renderPatchDetail(full);
            } catch (err) {
                detail.innerHTML = `<div class="patch-detail-empty">Failed to load patch: ${escapeHtml(err.message)}</div>`;
            }
        }

        function renderPatchDetail(patch, fileErrors = new Map(), errorMessage = '') {
            detail.innerHTML = '';
            const header = document.createElement('div');
            header.className = 'patch-detail-header';

            const fileCount = Array.isArray(patch.files) ? patch.files.length : (patch.filePath ? 1 : 0);
            const primaryPath = Array.isArray(patch.files) && patch.files[0]
                ? patch.files[0].filePath
                : (patch.filePath || '');

            header.innerHTML = `
                <div>
                    <div class="patch-detail-title">${escapeHtml(patch.title || patch.id)}</div>
                    <div class="patch-detail-path">${escapeHtml(primaryPath)}${fileCount > 1 ? ` • ${fileCount} files` : ''}</div>
                    <div class="patch-detail-status status-${patch.status}">${patch.status}</div>
                </div>
            `;
            detail.appendChild(header);

            const actionError = document.createElement('div');
            actionError.className = 'patch-action-error hidden';
            detail.appendChild(actionError);

            const meta = document.createElement('div');
            meta.className = 'patch-meta patch-diff-only';
            meta.textContent = `${formatRelativeTime(patch.createdAt)} • ${fileCount || 0} file${fileCount === 1 ? '' : 's'}`;
            detail.appendChild(meta);

            const provenance = patch.provenance || {};
            const prov = document.createElement('div');
            prov.className = 'patch-provenance patch-diff-only';
            prov.innerHTML = `
                <div><span class="label">Author</span>${escapeHtml(provenance.author || 'Unknown')}</div>
                <div><span class="label">Source</span>${escapeHtml(provenance.source || 'manual')}</div>
                <div><span class="label">Agent</span>${escapeHtml(provenance.agent || '—')}</div>
                <div><span class="label">Model</span>${escapeHtml(provenance.model || '—')}</div>
            `;
            detail.appendChild(prov);

            if (patch.description) {
                const desc = document.createElement('div');
                desc.className = 'patch-detail-desc';
                desc.textContent = patch.description;
                detail.appendChild(desc);
            }

            const files = Array.isArray(patch.files) ? patch.files : [];
            files.forEach((change, idx) => {
                // Anchor used for nav + scroll syncing
                change._anchor = `patch-file-${idx}`;
            });

            const fileNavPills = [];
            let activeAnchor = files.length ? files[0]._anchor : null;

            const setActiveFile = (anchor) => {
                activeAnchor = anchor;
                fileNavPills.forEach(pill => {
                    pill.classList.toggle('is-active', pill.dataset.anchor === anchor);
                });
            };

            const scrollToAnchor = (anchor) => {
                if (!anchor) return;
                const target = detail.querySelector(`.patch-file[data-anchor="${anchor}"]`);
                if (target) {
                    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    setActiveFile(anchor);
                }
            };

            // File navigation pills + prev/next controls
            if (files.length > 1) {
                const fileNav = document.createElement('div');
                fileNav.className = 'patch-file-nav';

                const prevBtn = document.createElement('button');
                prevBtn.type = 'button';
                prevBtn.className = 'patch-file-nav-btn';
                prevBtn.textContent = 'Prev';

                const pillsWrap = document.createElement('div');
                pillsWrap.className = 'patch-file-pills';

                const nextBtn = document.createElement('button');
                nextBtn.type = 'button';
                nextBtn.className = 'patch-file-nav-btn';
                nextBtn.textContent = 'Next';

                const activateByDelta = (delta) => {
                    const anchors = files.map(f => f._anchor);
                    const currentIdx = anchors.indexOf(activeAnchor);
                    if (currentIdx === -1) return;
                    const nextIdx = Math.min(Math.max(currentIdx + delta, 0), anchors.length - 1);
                    scrollToAnchor(anchors[nextIdx]);
                };

                prevBtn.addEventListener('click', () => activateByDelta(-1));
                nextBtn.addEventListener('click', () => activateByDelta(1));

                files.forEach(change => {
                    const btn = document.createElement('button');
                    btn.type = 'button';
                    btn.className = 'patch-file-pill';
                    btn.textContent = change.filePath || 'Unknown file';
                    btn.dataset.anchor = change._anchor;
                    if (fileErrors.has(change.filePath)) {
                        btn.classList.add('has-error');
                    }
                    btn.addEventListener('click', () => scrollToAnchor(change._anchor));
                    pillsWrap.appendChild(btn);
                    fileNavPills.push(btn);
                });

                fileNav.appendChild(prevBtn);
                fileNav.appendChild(pillsWrap);
                fileNav.appendChild(nextBtn);
                detail.appendChild(fileNav);
            }

            const filesSection = document.createElement('div');
            filesSection.className = 'patch-files';
            const fileCards = [];

            if (!files.length) {
                filesSection.innerHTML = '<div class="patch-detail-empty">No files in this patch</div>';
            } else {
                files.forEach(change => {
                    const card = document.createElement('div');
                    card.className = 'patch-file';
                    card.dataset.file = change.filePath || '';
                    if (change._anchor) {
                        card.dataset.anchor = change._anchor;
                    }

                    const headerRow = document.createElement('div');
                    headerRow.className = 'patch-file-header';
                    const fileName = document.createElement('div');
                    fileName.className = 'patch-file-path';
                    fileName.textContent = change.filePath || 'Unknown file';
                    const fileMeta = document.createElement('div');
                    fileMeta.className = 'patch-file-meta';
                    const editCount = Array.isArray(change.edits) ? change.edits.length : 0;
                    fileMeta.textContent = `${editCount} edit${editCount === 1 ? '' : 's'}`;
                    headerRow.appendChild(fileName);
                    headerRow.appendChild(fileMeta);
                    card.appendChild(headerRow);

                    // View switcher container
                    const viewSwitcher = document.createElement('div');
                    viewSwitcher.className = 'patch-view-switcher';

                    const editorViewBtn = document.createElement('button');
                    editorViewBtn.className = 'patch-view-btn active';
                    editorViewBtn.textContent = 'Editor View';
                    editorViewBtn.dataset.view = 'editor';

                    const diffViewBtn = document.createElement('button');
                    diffViewBtn.className = 'patch-view-btn';
                    diffViewBtn.textContent = 'Diff View';
                    diffViewBtn.dataset.view = 'diff';

                    viewSwitcher.appendChild(editorViewBtn);
                    viewSwitcher.appendChild(diffViewBtn);
                    card.appendChild(viewSwitcher);

                    // Container for both views
                    const viewContainer = document.createElement('div');
                    viewContainer.className = 'patch-view-container';

                    const editorView = renderEditorView(change.filePath || '', change.diff || change.preview || '');
                    editorView.className = 'patch-editor-view active';

                    const diffBlock = renderDiffTable(change.filePath || '', change.diff || change.preview || '');
                    diffBlock.classList.add('patch-diff-view');

                    viewContainer.appendChild(editorView);
                    viewContainer.appendChild(diffBlock);
                    card.appendChild(viewContainer);

                    // View switcher logic
                    const switchView = (viewType) => {
                        currentViewMode = viewType;

                        if (viewType === 'editor') {
                            editorViewBtn.classList.add('active');
                            diffViewBtn.classList.remove('active');
                            editorView.classList.add('active');
                            diffBlock.classList.remove('active');

                            // Switch modal to editor layout
                            modal.classList.add('patch-editor-mode');
                            modal.classList.remove('patch-diff-mode');
                        } else {
                            diffViewBtn.classList.add('active');
                            editorViewBtn.classList.remove('active');
                            diffBlock.classList.add('active');
                            editorView.classList.remove('active');

                            // Switch modal to diff layout
                            modal.classList.remove('patch-editor-mode');
                            modal.classList.add('patch-diff-mode');
                        }
                    };

                    editorViewBtn.addEventListener('click', () => switchView('editor'));
                    diffViewBtn.addEventListener('click', () => switchView('diff'));

                    // Initialize with editor mode
                    switchView('editor');

                    const fileError = document.createElement('div');
                    fileError.className = 'patch-file-error hidden';
                    if (fileErrors.has(change.filePath)) {
                        fileError.textContent = fileErrors.get(change.filePath);
                        fileError.classList.remove('hidden');
                        card.classList.add('has-error');
                    }
                    card.appendChild(fileError);
                    filesSection.appendChild(card);
                    fileCards.push(card);
                });
            }
            detail.appendChild(filesSection);

            // Sync nav pills to scroll position
            if (fileNavPills.length && fileCards.length) {
                const observer = new IntersectionObserver((entries) => {
                    entries.forEach(entry => {
                        if (entry.isIntersecting && entry.target.dataset.anchor) {
                            setActiveFile(entry.target.dataset.anchor);
                        }
                    });
                }, { root: filesSection, threshold: 0.4 });

                fileCards.forEach(card => observer.observe(card));
                setActiveFile(activeAnchor);
            }

            if (Array.isArray(patch.auditLog) && patch.auditLog.length) {
                const auditWrap = document.createElement('div');
                auditWrap.className = 'patch-audit';
                const title = document.createElement('div');
                title.className = 'patch-audit-title';
                title.textContent = 'Audit trail';
                auditWrap.appendChild(title);

                const list = document.createElement('div');
                list.className = 'patch-audit-list';
                patch.auditLog
                    .slice()
                    .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
                    .forEach(entry => {
                        const row = document.createElement('div');
                        row.className = 'patch-audit-row';
                        const ts = entry.timestamp ? formatRelativeTime(entry.timestamp) : '';
                        row.innerHTML = `
                            <div class="patch-audit-meta">${escapeHtml(entry.actor || 'user')} • ${escapeHtml(entry.action || '')}</div>
                            <div class="patch-audit-msg">${escapeHtml(entry.message || '')}</div>
                            <div class="patch-audit-time">${ts}</div>
                        `;
                        list.appendChild(row);
                    });
                auditWrap.appendChild(list);
                detail.appendChild(auditWrap);
            }

            // Action buttons are now in the agent header
            const applyBtn = agentHeaderContainer.querySelector('#btn-apply-patch');
            const rejectBtn = agentHeaderContainer.querySelector('#btn-reject-patch');
            const deleteBtn = agentHeaderContainer.querySelector('#btn-delete-patch');
            const exportBtn = agentHeaderContainer.querySelector('#btn-export-audit');

            const disableActions = (disabled) => {
                if (applyBtn) applyBtn.disabled = disabled || patch.status !== 'pending';
                if (rejectBtn) rejectBtn.disabled = disabled || patch.status !== 'pending';
                if (deleteBtn) deleteBtn.disabled = disabled;
                if (exportBtn) exportBtn.disabled = disabled;
            };

            disableActions(false);

            applyBtn.addEventListener('click', async () => {
                disableActions(true);
                actionError.classList.add('hidden');
                try {
                    // Check if we're in editor mode and content was edited
                    if (currentViewMode === 'editor') {
                        // Gather edited content from all editable areas
                        const editableAreas = detail.querySelectorAll('.editor-editable-area');
                        const editedFiles = [];

                        editableAreas.forEach(area => {
                            const filePath = area.dataset.filePath;
                            const editedContent = area.innerText; // Get plain text content
                            // Validate both path and content exist and are non-empty
                            if (filePath && filePath.trim() && editedContent !== null && editedContent !== undefined) {
                                editedFiles.push({ path: filePath.trim(), content: editedContent });
                            }
                        });

                        if (editedFiles.length > 0) {
                            // Save edited content directly to files
                            for (const file of editedFiles) {
                                // Double-check path is valid before saving
                                if (!file.path || !file.path.trim()) {
                                    console.error('Invalid file path:', file);
                                    continue;
                                }
                                if (patchActivityAgentId) {
                                    await withAgentActivity(
                                        patchActivityAgentId,
                                        'executing',
                                        () => api('/api/file', {
                                            method: 'PUT',
                                            body: { path: file.path, content: file.content }
                                        }),
                                        `Applying patch ${patch.id || ''}`.trim()
                                    );
                                } else {
                                    await api('/api/file', {
                                        method: 'PUT',
                                        body: { path: file.path, content: file.content }
                                    });
                                }
                            }

                            // Mark patch as applied
                            if (patchActivityAgentId) {
                                await withAgentActivity(
                                    patchActivityAgentId,
                                    'executing',
                                    () => patchApi.apply(patch.id),
                                    `Applying patch ${patch.id || ''}`.trim()
                                );
                            } else {
                                await patchApi.apply(patch.id);
                            }

                            // Create feedback issue for the agent
                            await createPatchFeedbackIssue(patch, 'applied', editedFiles);

                            const payload = buildPatchNotificationPayload(patch);
                            notificationStore.push(
                                'success',
                                'editor',
                                `Applied and saved edits to ${patch.id}`,
                                '',
                                'info',
                                false,
                                'Review Patch',
                                payload,
                                'patch'
                            );
                            await loadPatches(patch.id);
                            return;
                        }
                    }

                    // Normal apply (diff mode or no edits in editor mode)
                    if (patchActivityAgentId) {
                        await withAgentActivity(
                            patchActivityAgentId,
                            'executing',
                            () => patchApi.apply(patch.id),
                            `Applying patch ${patch.id || ''}`.trim()
                        );
                    } else {
                        await patchApi.apply(patch.id);
                    }
                    const payload = buildPatchNotificationPayload(patch);
                    notificationStore.push(
                        'success',
                        'editor',
                        `Applied ${patch.id}`,
                        '',
                        'info',
                        false,
                        'Review Patch',
                        payload,
                        'patch'
                    );
                    await loadPatches(patch.id);
                } catch (err) {
                    const fileErrorsList = Array.isArray(err.data && err.data.fileErrors) ? err.data.fileErrors : [];
                    actionError.textContent = err.message || 'Failed to apply patch';
                    actionError.classList.remove('hidden');
                    const fileErrorMap = new Map(fileErrorsList.map(fe => [fe.filePath, fe.message]));
                    renderPatchDetail(patch, fileErrorMap, err.message);
                    pushPatchApplyFailureNotification(patch, err.message, fileErrorsList);
                } finally {
                    disableActions(false);
                }
            });

            rejectBtn.addEventListener('click', async () => {
                disableActions(true);
                actionError.classList.add('hidden');
                try {
                    if (patchActivityAgentId) {
                        await withAgentActivity(
                            patchActivityAgentId,
                            'executing',
                            () => patchApi.reject(patch.id),
                            `Rejecting patch ${patch.id || ''}`.trim()
                        );
                    } else {
                        await patchApi.reject(patch.id);
                    }

                    // Create feedback issue for the agent
                    await createPatchFeedbackIssue(patch, 'rejected');

                    const payload = buildPatchNotificationPayload(patch);
                    notificationStore.push(
                        'warning',
                        'editor',
                        `Rejected ${patch.id}`,
                        '',
                        'attention',
                        false,
                        'Review Patch',
                        payload,
                        'patch'
                    );
                    await loadPatches(patch.id);
                } catch (err) {
                    actionError.textContent = err.message || 'Failed to reject patch';
                    actionError.classList.remove('hidden');
                    disableActions(false);
                }
            });

            deleteBtn.addEventListener('click', async () => {
                const confirmed = window.confirm('Delete this patch? This cannot be undone.');
                if (!confirmed) return;
                disableActions(true);
                try {
                    if (patchActivityAgentId) {
                        await withAgentActivity(
                            patchActivityAgentId,
                            'executing',
                            () => patchApi.delete(patch.id),
                            `Deleting patch ${patch.id || ''}`.trim()
                        );
                    } else {
                        await patchApi.delete(patch.id);
                    }
                    notificationStore.info(`Deleted ${patch.id}`, 'editor');
                    currentPatchId = null;
                    await loadPatches();
                } catch (err) {
                    actionError.textContent = err.message || 'Failed to delete patch';
                    actionError.classList.remove('hidden');
                    disableActions(false);
                }
            });

            exportBtn.addEventListener('click', async () => {
                disableActions(true);
                try {
                    const audit = await patchApi.exportAudit(patch.id);
                    const blob = new Blob([JSON.stringify(audit, null, 2)], { type: 'application/json' });
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = `patch-audit-${patch.id}.json`;
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    URL.revokeObjectURL(url);
                    notificationStore.success(`Exported audit for ${patch.id}`, 'editor');
                } catch (err) {
                    notificationStore.error(`Failed to export audit: ${err.message}`, 'editor');
                } finally {
                    disableActions(false);
                }
            });

            if (errorMessage) {
                actionError.textContent = errorMessage;
                actionError.classList.remove('hidden');
            }
        }

        function renderEditorView(filePath, diffText) {
            const container = document.createElement('div');
            container.className = 'patch-editor-content';

            if (!diffText) {
                container.textContent = 'No changes to show';
                return container;
            }

            // Add file heading for context
            if (filePath) {
                const fileHeading = document.createElement('div');
                fileHeading.className = 'editor-file-heading';
                fileHeading.textContent = filePath;
                container.appendChild(fileHeading);
            }

            // Create editable content area
            const editableArea = document.createElement('div');
            editableArea.className = 'editor-editable-area';
            editableArea.contentEditable = 'true';
            editableArea.spellcheck = false;
            // Only set filePath if it's valid
            if (filePath && filePath.trim()) {
                editableArea.dataset.filePath = filePath.trim();
            }

            const rows = parseUnifiedDiff(diffText);
            if (!rows.length) {
                container.textContent = 'No changes to show';
                return container;
            }

            // Group consecutive changes for better readability
            const groups = [];
            let currentGroup = [];

            rows.forEach((row, idx) => {
                if (row.type === 'hunk') {
                    // Start new group on hunk marker
                    if (currentGroup.length) {
                        groups.push(currentGroup);
                        currentGroup = [];
                    }
                } else {
                    currentGroup.push(row);
                }
            });

            if (currentGroup.length) {
                groups.push(currentGroup);
            }

            // Render each group as a prose block
            groups.forEach((group, groupIdx) => {
                if (!group.length) return;

                const block = document.createElement('div');
                block.className = 'editor-block';

                group.forEach(row => {
                    if (row.type === 'context') {
                        // Regular context text
                        const contextSpan = document.createElement('span');
                        contextSpan.className = 'editor-context';
                        contextSpan.textContent = row.text;
                        block.appendChild(contextSpan);

                        // Add space or newline if text doesn't end with whitespace
                        if (row.text && !row.text.match(/\s$/)) {
                            block.appendChild(document.createTextNode('\n'));
                        } else {
                            block.appendChild(document.createTextNode('\n'));
                        }
                    } else if (row.type === 'remove') {
                        // Deleted text with strikethrough
                        const del = document.createElement('del');
                        del.className = 'editor-deletion';
                        del.textContent = row.text;
                        block.appendChild(del);

                        if (row.text && !row.text.match(/\s$/)) {
                            block.appendChild(document.createTextNode('\n'));
                        } else {
                            block.appendChild(document.createTextNode('\n'));
                        }
                    } else if (row.type === 'add') {
                        // Added text with underline/highlight
                        const ins = document.createElement('ins');
                        ins.className = 'editor-addition';
                        ins.textContent = row.text;
                        block.appendChild(ins);

                        if (row.text && !row.text.match(/\s$/)) {
                            block.appendChild(document.createTextNode('\n'));
                        } else {
                            block.appendChild(document.createTextNode('\n'));
                        }
                    }
                });

                editableArea.appendChild(block);
            });

            container.appendChild(editableArea);

            // Add hint about editability
            const hint = document.createElement('div');
            hint.className = 'editor-hint';
            hint.textContent = 'Click to edit this content directly. Your changes will be saved when you click "Apply Changes".';
            container.appendChild(hint);

            return container;
        }

        function renderDiffTable(filePath, diffText) {
            const container = document.createElement('div');
            container.className = 'patch-diff-table';
            if (!diffText) {
                container.textContent = 'No diff available';
                return container;
            }

            const header = document.createElement('div');
            header.className = 'patch-diff-header';
            header.innerHTML = `
                <div class="diff-col-label">Old</div>
                <div class="diff-col-label">New</div>
                <div class="diff-file-label">${escapeHtml(filePath || '')}</div>
            `;
            container.appendChild(header);

            const table = document.createElement('div');
            table.className = 'diff-rows';

            const rows = parseUnifiedDiff(diffText);
            if (!rows.length) {
                const empty = document.createElement('div');
                empty.className = 'patch-detail-empty';
                empty.textContent = 'No diff available';
                table.appendChild(empty);
            } else {
                rows.forEach(row => {
                    if (row.type === 'hunk') {
                        const hunk = document.createElement('div');
                        hunk.className = 'diff-sxs-row hunk';
                        hunk.textContent = row.text;
                        table.appendChild(hunk);
                        return;
                    }

                    const div = document.createElement('div');
                    div.className = `diff-sxs-row ${row.type}`;

                    const left = document.createElement('div');
                    left.className = 'diff-sxs-cell left';
                    const leftNum = document.createElement('div');
                    leftNum.className = 'line-num';
                    leftNum.textContent = row.leftLine !== null ? row.leftLine : '';
                    const leftText = document.createElement('div');
                    leftText.className = 'line-text';
                    leftText.textContent = row.type === 'add' ? '' : row.text;
                    left.appendChild(leftNum);
                    left.appendChild(leftText);

                    const right = document.createElement('div');
                    right.className = 'diff-sxs-cell right';
                    const rightNum = document.createElement('div');
                    rightNum.className = 'line-num';
                    rightNum.textContent = row.rightLine !== null ? row.rightLine : '';
                    const rightText = document.createElement('div');
                    rightText.className = 'line-text';
                    rightText.textContent = row.type === 'remove' ? '' : row.text;
                    right.appendChild(rightNum);
                    right.appendChild(rightText);

                    div.appendChild(left);
                    div.appendChild(right);
                    table.appendChild(div);
                });
            }

            container.appendChild(table);
            return container;
        }

        function parseUnifiedDiff(diffText) {
            const lines = diffText.split('\n');
            const rows = [];
            let leftLine = 0;
            let rightLine = 0;
            const hunkRe = /^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/;

            lines.forEach(raw => {
                const line = raw || '';
                const hunkMatch = line.match(hunkRe);
                if (hunkMatch) {
                    leftLine = parseInt(hunkMatch[1], 10) - 1;
                    rightLine = parseInt(hunkMatch[2], 10) - 1;
                    rows.push({ type: 'hunk', text: line, leftLine: null, rightLine: null });
                    return;
                }

                if (line.startsWith('+')) {
                    rightLine += 1;
                    rows.push({ type: 'add', text: line.substring(1), leftLine: null, rightLine });
                } else if (line.startsWith('-')) {
                    leftLine += 1;
                    rows.push({ type: 'remove', text: line.substring(1), leftLine, rightLine: null });
                } else if (line.startsWith(' ')) {
                    leftLine += 1;
                    rightLine += 1;
                    rows.push({ type: 'context', text: line.substring(1), leftLine, rightLine });
                } else {
                    rows.push({ type: 'context', text: line, leftLine: null, rightLine: null });
                }
            });

            return rows;
        }

        await loadPatches(currentPatchId);
    }

    function updateWorkspaceButton() {
        if (!elements.btnWorkspaceSwitch || !elements.workspaceName) return;
        const displayName = state.workspace.displayName || state.workspace.name || 'Project';
        const icon = state.workspace.icon ? `${state.workspace.icon} ` : '';
        elements.workspaceName.textContent = `${icon}${displayName}`;
        if (elements.workspaceDesc) {
            elements.workspaceDesc.textContent = state.workspace.description || '';
            elements.workspaceDesc.style.display = state.workspace.description ? 'inline' : 'none';
        }
        if (state.workspace.path) {
            elements.btnWorkspaceSwitch.title = `Switch Project (${state.workspace.path})`;
        }
    }

    async function showProjectPreparationWizard() {
        if (!createModalShell || !preparationApi) {
            log('Preparation wizard unavailable', 'warning');
            return;
        }

        const { overlay, modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            'Project Preparation',
            'Next',
            'Cancel',
            { closeOnCancel: false, closeOnConfirm: false }
        );
        modal.classList.add('preparation-modal');
        if (cancelBtn) {
            cancelBtn.remove();
        }

        overlay.addEventListener('click', (event) => {
            if (event.target === overlay) {
                event.stopImmediatePropagation();
            }
        }, true);

        const escapeGuard = (event) => {
            if (event.key === 'Escape') {
                event.stopImmediatePropagation();
            }
        };
        document.addEventListener('keydown', escapeGuard, true);

        const wizardState = {
            step: 0,
            mode: null,
            manuscripts: [],
            canon: [],
            premise: '',
            genre: '',
            protagonistName: '',
            protagonistRole: '',
            themes: ''
        };

        const setConfirm = (label, handler, disabled = false) => {
            confirmBtn.textContent = label;
            confirmBtn.disabled = disabled;
            confirmBtn.onclick = handler;
            confirmBtn.style.display = 'inline-flex';
        };

        const renderStep = () => {
            body.innerHTML = '';

            if (wizardState.step === 0) {
                confirmBtn.style.display = 'none';
                const intro = document.createElement('div');
                intro.className = 'modal-text';
                intro.textContent = 'This is a one-way project preparation step. After it runs, Control Room uses canonical Story + Compendium state.';
                body.appendChild(intro);

                const choices = document.createElement('div');
                choices.className = 'preparation-choices';

                const importBtn = document.createElement('button');
                importBtn.type = 'button';
                importBtn.className = 'preparation-choice';
                importBtn.textContent = 'I have manuscript/canon files to import';
                importBtn.addEventListener('click', () => {
                    wizardState.mode = 'ingest';
                    wizardState.step = 1;
                    renderStep();
                });

                const freshBtn = document.createElement('button');
                freshBtn.type = 'button';
                freshBtn.className = 'preparation-choice primary';
                freshBtn.textContent = 'Start fresh (no files)';
                freshBtn.addEventListener('click', () => {
                    wizardState.mode = 'empty';
                    wizardState.step = 1;
                    renderStep();
                });

                choices.appendChild(importBtn);
                choices.appendChild(freshBtn);
                body.appendChild(choices);

                const hint = document.createElement('div');
                hint.className = 'modal-hint';
                hint.textContent = 'Prepared projects are virtual-only: editor + explorer read from canonical state.';
                body.appendChild(hint);
                return;
            }

            if (wizardState.mode === 'ingest') {
                const headline = document.createElement('div');
                headline.className = 'modal-text';
                headline.textContent = 'Drop manuscript and canon files. Control Room will ingest them once and store evidence receipts.';
                body.appendChild(headline);

                const addFiles = (target, files) => {
                    const existing = new Set(target.map(file => `${file.name}-${file.size}-${file.lastModified}`));
                    files.forEach(file => {
                        const key = `${file.name}-${file.size}-${file.lastModified}`;
                        if (!existing.has(key)) {
                            target.push(file);
                            existing.add(key);
                        }
                    });
                };

                const filterAccepted = (files) => {
                    return files.filter(file => /\.(md|txt)$/i.test(file.name || ''));
                };

                const collectDroppedFiles = async (dataTransfer) => {
                    const files = [];
                    const items = Array.from(dataTransfer.items || []);
                    if (items.length && items[0].webkitGetAsEntry) {
                        const traverse = async (entry) => {
                            if (!entry) return;
                            if (entry.isFile) {
                                await new Promise(resolve => {
                                    entry.file(file => {
                                        files.push(file);
                                        resolve();
                                    });
                                });
                            } else if (entry.isDirectory) {
                                const reader = entry.createReader();
                                const readEntries = () => new Promise(resolve => {
                                    reader.readEntries(resolve);
                                });
                                let batch = await readEntries();
                                while (batch.length) {
                                    for (const child of batch) {
                                        await traverse(child);
                                    }
                                    batch = await readEntries();
                                }
                            }
                        };
                        for (const item of items) {
                            const entry = item.webkitGetAsEntry();
                            if (entry) {
                                await traverse(entry);
                            }
                        }
                        return files;
                    }
                    return Array.from(dataTransfer.files || []);
                };

                const buildDropZone = (labelText, onFiles) => {
                    const wrapper = document.createElement('div');
                    wrapper.className = 'preparation-row';
                    const label = document.createElement('label');
                    label.className = 'modal-label';
                    label.textContent = labelText;
                    wrapper.appendChild(label);

                    const dropZone = document.createElement('button');
                    dropZone.type = 'button';
                    dropZone.className = 'prep-dropzone';
                    dropZone.innerHTML = '<strong>Drop your files/folders here</strong><span>or click to browse</span>';

                    const input = document.createElement('input');
                    input.type = 'file';
                    input.multiple = true;
                    input.accept = '.md,.txt';
                    input.className = 'prep-dropzone-input';
                    input.setAttribute('webkitdirectory', '');
                    input.setAttribute('directory', '');
                    input.setAttribute('mozdirectory', '');
                    dropZone.appendChild(input);

                    const handleFiles = (files) => {
                        const accepted = filterAccepted(files);
                        if (!accepted.length) {
                            notificationStore.warning('Only .md and .txt files are supported for ingest.', 'global');
                            return;
                        }
                        onFiles(accepted);
                        renderStep();
                    };

                    input.addEventListener('change', () => {
                        handleFiles(Array.from(input.files || []));
                        input.value = '';
                    });

                    dropZone.addEventListener('dragover', (event) => {
                        event.preventDefault();
                        dropZone.classList.add('is-dragging');
                    });
                    dropZone.addEventListener('dragleave', () => {
                        dropZone.classList.remove('is-dragging');
                    });
                    dropZone.addEventListener('drop', async (event) => {
                        event.preventDefault();
                        dropZone.classList.remove('is-dragging');
                        const files = await collectDroppedFiles(event.dataTransfer);
                        handleFiles(files);
                    });
                    dropZone.addEventListener('click', () => {
                        input.click();
                    });

                    wrapper.appendChild(dropZone);
                    return wrapper;
                };

                body.appendChild(buildDropZone('Manuscript files (.md/.txt)', (files) => addFiles(wizardState.manuscripts, files)));
                body.appendChild(buildDropZone('Canon/worldbuilding files (.md/.txt)', (files) => addFiles(wizardState.canon, files)));

                const summary = document.createElement('div');
                summary.className = 'modal-hint';
                summary.textContent = `Selected: ${wizardState.manuscripts.length} manuscript, ${wizardState.canon.length} canon files.`;
                body.appendChild(summary);

                setConfirm('Prepare Project', async () => {
                    confirmBtn.disabled = true;
                    try {
                        const formData = new FormData();
                        wizardState.manuscripts.forEach(file => formData.append('manuscripts', file));
                        wizardState.canon.forEach(file => formData.append('canon', file));
                    const result = await preparationApi.prepareIngest(formData);
                    notificationStore.success('Project prepared successfully. Reloading...', 'global');
                    localStorage.removeItem('cr-prep-pending');
                    close();
                    document.removeEventListener('keydown', escapeGuard, true);
                    setTimeout(() => window.location.reload(), 200);
                    } catch (err) {
                        confirmBtn.disabled = false;
                        notificationStore.error(`Preparation failed: ${err.message}`, 'global');
                    }
                }, wizardState.manuscripts.length + wizardState.canon.length === 0);
                return;
            }

            const headline = document.createElement('div');
            headline.className = 'modal-text';
            headline.textContent = 'Give the project a minimal seed. Control Room will scaffold Story + Compendium for you.';
            body.appendChild(headline);

            const premiseRow = document.createElement('div');
            premiseRow.className = 'preparation-row';
            premiseRow.innerHTML = '<label class="modal-label">Premise</label>';
            const premiseInput = document.createElement('textarea');
            premiseInput.className = 'modal-textarea';
            premiseInput.rows = 3;
            premiseInput.value = wizardState.premise;
            premiseInput.addEventListener('input', () => {
                wizardState.premise = premiseInput.value;
            });
            premiseRow.appendChild(premiseInput);
            body.appendChild(premiseRow);

            const genreRow = document.createElement('div');
            genreRow.className = 'preparation-row';
            genreRow.innerHTML = '<label class="modal-label">Genre/Tone</label>';
            const genreInput = document.createElement('input');
            genreInput.className = 'modal-input';
            genreInput.type = 'text';
            genreInput.placeholder = 'e.g., noir mystery, cozy fantasy';
            genreInput.value = wizardState.genre;
            genreInput.addEventListener('input', () => {
                wizardState.genre = genreInput.value;
            });
            genreRow.appendChild(genreInput);
            body.appendChild(genreRow);

            const protagonistRow = document.createElement('div');
            protagonistRow.className = 'preparation-row';
            protagonistRow.innerHTML = '<label class="modal-label">Protagonist</label>';
            const protagonistName = document.createElement('input');
            protagonistName.className = 'modal-input';
            protagonistName.type = 'text';
            protagonistName.placeholder = 'Name';
            protagonistName.value = wizardState.protagonistName;
            protagonistName.addEventListener('input', () => {
                wizardState.protagonistName = protagonistName.value;
            });
            const protagonistRole = document.createElement('input');
            protagonistRole.className = 'modal-input';
            protagonistRole.type = 'text';
            protagonistRole.placeholder = 'Role (optional)';
            protagonistRole.value = wizardState.protagonistRole;
            protagonistRole.addEventListener('input', () => {
                wizardState.protagonistRole = protagonistRole.value;
            });
            protagonistRow.appendChild(protagonistName);
            protagonistRow.appendChild(protagonistRole);
            body.appendChild(protagonistRow);

            const themesRow = document.createElement('div');
            themesRow.className = 'preparation-row';
            themesRow.innerHTML = '<label class="modal-label">Themes (optional)</label>';
            const themesInput = document.createElement('input');
            themesInput.className = 'modal-input';
            themesInput.type = 'text';
            themesInput.placeholder = 'e.g., memory, betrayal, wonder';
            themesInput.value = wizardState.themes;
            themesInput.addEventListener('input', () => {
                wizardState.themes = themesInput.value;
            });
            themesRow.appendChild(themesInput);
            body.appendChild(themesRow);

            setConfirm('Prepare Project', async () => {
                if (!wizardState.protagonistName.trim()) {
                    notificationStore.warning('Add at least one protagonist name.', 'global');
                    return;
                }
                confirmBtn.disabled = true;
                try {
                    const payload = {
                        premise: wizardState.premise,
                        genre: wizardState.genre,
                        protagonistName: wizardState.protagonistName,
                        protagonistRole: wizardState.protagonistRole,
                        themes: wizardState.themes
                    };
                    await preparationApi.prepareEmpty(payload);
                    notificationStore.success('Project prepared successfully. Reloading...', 'global');
                    localStorage.removeItem('cr-prep-pending');
                    close();
                    document.removeEventListener('keydown', escapeGuard, true);
                    setTimeout(() => window.location.reload(), 200);
                } catch (err) {
                    confirmBtn.disabled = false;
                    notificationStore.error(`Preparation failed: ${err.message}`, 'global');
                }
            }, !wizardState.protagonistName.trim());
        };

        renderStep();
    }

    async function showWorkspaceSwitcher() {
        // Refresh workspace info to ensure we have current project list
        // (handles externally deleted projects)
        try {
            await loadWorkspaceInfo();
        } catch (err) {
            log(`Failed to refresh workspace info: ${err.message}`, 'warn');
        }

        const { overlay, modal, body, confirmBtn, close } = createModalShell(
            'Switch Project',
            'Switch Now',
            'Cancel',
            { closeOnCancel: true }
        );

        modal.classList.add('workspace-switch-modal');

        const info = document.createElement('div');
        info.className = 'modal-text';
        const rootLabel = state.workspace.root ? `under ${state.workspace.root}` : 'under the project root';
        info.textContent = `Pick an existing project or type a new name ${rootLabel}. Switching applies instantly.`;
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
            option.textContent = 'No Projects found';
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
        const deleteBtn = document.createElement('button');
        deleteBtn.type = 'button';
        deleteBtn.className = 'modal-btn modal-btn-danger modal-btn-small';
        deleteBtn.textContent = 'Delete';
        deleteBtn.title = 'Delete selected project';
        deleteBtn.disabled = available.length === 0;

        const updateDeleteBtn = () => {
            const selectedName = select.value;
            const isCurrent = selectedName === state.workspace.name;
            deleteBtn.disabled = !selectedName || isCurrent;
            deleteBtn.title = isCurrent ? 'Cannot delete the currently active project' : 'Delete selected project';
        };
        select.addEventListener('change', updateDeleteBtn);
        updateDeleteBtn();

        deleteBtn.addEventListener('click', async () => {
            const nameToDelete = select.value;
            if (!nameToDelete) return;

            if (nameToDelete === state.workspace.name) {
                error.textContent = 'Cannot delete the currently active project.';
                return;
            }

            const confirmed = await showConfirmDialog(
                'Delete Project',
                `Are you sure you want to permanently delete "${nameToDelete}"? This will remove all files and data in this project folder. This action cannot be undone.`,
                'Delete',
                'Cancel'
            );

            if (!confirmed) return;

            deleteBtn.disabled = true;
            error.textContent = '';
            try {
                await workspaceApi.deleteProject(nameToDelete);
                // Remove from dropdown
                const optionToRemove = select.querySelector(`option[value="${CSS.escape(nameToDelete)}"]`);
                if (optionToRemove) optionToRemove.remove();
                // Update state
                state.workspace.available = state.workspace.available.filter(n => n !== nameToDelete);
                // Update recents in localStorage
                const recents = JSON.parse(localStorage.getItem('recent-projects') || '[]').filter(n => n !== nameToDelete);
                localStorage.setItem('recent-projects', JSON.stringify(recents));
                // Update UI
                if (select.options.length === 0) {
                    const emptyOpt = document.createElement('option');
                    emptyOpt.value = '';
                    emptyOpt.textContent = 'No Projects found';
                    select.appendChild(emptyOpt);
                    select.disabled = true;
                }
                notificationStore.success(`Project "${nameToDelete}" deleted.`, 'global');
                updateDeleteBtn();
            } catch (err) {
                error.textContent = err.message || 'Failed to delete project.';
            } finally {
                deleteBtn.disabled = false;
                updateDeleteBtn();
            }
        });

        rowSelect.appendChild(selectLabel);
        rowSelect.appendChild(select);
        rowSelect.appendChild(deleteBtn);
        body.appendChild(rowSelect);

        const rowInput = document.createElement('div');
        rowInput.className = 'modal-row';
        const inputLabel = document.createElement('label');
        inputLabel.className = 'modal-label';
        inputLabel.textContent = 'New name';
        const input = document.createElement('input');
        input.className = 'modal-input';
        input.type = 'text';
        input.placeholder = 'e.g., whateverwonderfulworkspaceNametheuserwillcomeupwith';
        rowInput.appendChild(inputLabel);
        rowInput.appendChild(input);
        body.appendChild(rowInput);

        const storedRecents = JSON.parse(localStorage.getItem('recent-projects') || '[]').filter(Boolean);
        const availableSet = new Set(available);
        const recents = storedRecents.filter(name => availableSet.has(name));
        if (recents.length !== storedRecents.length) {
            localStorage.setItem('recent-projects', JSON.stringify(recents));
        }
        if (recents.length) {
            const recentRow = document.createElement('div');
            recentRow.className = 'recent-projects';
            const recentLabel = document.createElement('label');
            recentLabel.className = 'modal-label';
            recentLabel.textContent = 'Recent';
            const recentChips = document.createElement('div');
            recentChips.className = 'recent-chip-row';
            recents.slice(0, 6).forEach(name => {
                const chip = document.createElement('button');
                chip.type = 'button';
                chip.className = 'recent-chip';
                chip.textContent = name;
                chip.addEventListener('click', () => {
                    input.value = name;
                    select.value = name;
                });
                recentChips.appendChild(chip);
            });
            recentRow.appendChild(recentLabel);
            recentRow.appendChild(recentChips);
            body.appendChild(recentRow);
        }

        const selectName = () => input.value.trim() || select.value;

        confirmBtn.addEventListener('click', async () => {
            const name = selectName();
            if (!name) {
                error.textContent = 'Choose or enter a Project name.';
                return;
            }
            confirmBtn.disabled = true;
            error.textContent = '';
            try {
                const available = state.workspace.available || [];
                const isNewProject = !available.includes(name);
                if (isNewProject) {
                    localStorage.setItem('cr-prep-pending', JSON.stringify({ name }));
                } else {
                    localStorage.removeItem('cr-prep-pending');
                }
                const result = await workspaceApi.select(name);
                const targetPath = result.targetPath || name;
                const recent = JSON.parse(localStorage.getItem('recent-projects') || '[]').filter(Boolean);
                const updatedRecent = [name, ...recent.filter(n => n !== name)].slice(0, 6);
                localStorage.setItem('recent-projects', JSON.stringify(updatedRecent));
                notificationStore.success(`Project switched to ${name}. Reloading...`, 'global');
                log(`Project selection applied: ${targetPath}`, 'info');
                setTimeout(() => window.location.reload(), 200);
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

        // Metadata section
        const metaTitle = document.createElement('div');
        metaTitle.className = 'modal-subtitle';
        metaTitle.textContent = 'Project Metadata';
        body.appendChild(metaTitle);

        const metaDisplayRow = document.createElement('div');
        metaDisplayRow.className = 'modal-row';
        const metaDisplayLabel = document.createElement('label');
        metaDisplayLabel.className = 'modal-label';
        metaDisplayLabel.textContent = 'Display name';
        const metaDisplayInput = document.createElement('input');
        metaDisplayInput.className = 'modal-input';
        metaDisplayInput.type = 'text';
        metaDisplayInput.placeholder = 'Friendly Project name';
        metaDisplayInput.value = state.workspace.displayName || state.workspace.name || '';
        metaDisplayRow.appendChild(metaDisplayLabel);
        metaDisplayRow.appendChild(metaDisplayInput);
        body.appendChild(metaDisplayRow);

        const metaDescRow = document.createElement('div');
        metaDescRow.className = 'modal-row';
        const metaDescLabel = document.createElement('label');
        metaDescLabel.className = 'modal-label';
        metaDescLabel.textContent = 'Description';
        const metaDescInput = document.createElement('textarea');
        metaDescInput.className = 'modal-textarea';
        metaDescInput.rows = 2;
        metaDescInput.placeholder = 'Short description (e.g., team, sprint, repo)';
        metaDescInput.value = state.workspace.description || '';
        metaDescRow.appendChild(metaDescLabel);
        metaDescRow.appendChild(metaDescInput);
        body.appendChild(metaDescRow);

        const metaIconRow = document.createElement('div');
        metaIconRow.className = 'modal-row';
        const metaIconLabel = document.createElement('label');
        metaIconLabel.className = 'modal-label';
        metaIconLabel.textContent = 'Icon (emoji)';
        const metaIconInput = document.createElement('input');
        metaIconInput.className = 'modal-input';
        metaIconInput.type = 'text';
        metaIconInput.placeholder = '🛰️';
        metaIconInput.value = state.workspace.icon || '';
        metaIconRow.appendChild(metaIconLabel);
        metaIconRow.appendChild(metaIconInput);
        body.appendChild(metaIconRow);

        const metaHint = document.createElement('div');
        metaHint.className = 'modal-hint';
        metaHint.textContent = 'Metadata saves instantly for this Project.';
        body.appendChild(metaHint);

        const saveMetaBtn = document.createElement('button');
        saveMetaBtn.className = 'modal-btn modal-btn-secondary';
        saveMetaBtn.type = 'button';
        saveMetaBtn.textContent = 'Save Metadata';
        body.appendChild(saveMetaBtn);

        saveMetaBtn.addEventListener('click', async () => {
            saveMetaBtn.disabled = true;
            metaHint.textContent = '';
            try {
                const payload = {
                    displayName: metaDisplayInput.value.trim(),
                    description: metaDescInput.value.trim(),
                    icon: metaIconInput.value.trim()
                };
                const result = await workspaceApi.saveMetadata(payload);
                const meta = result.metadata || payload;
                state.workspace.displayName = meta.displayName || state.workspace.name;
                state.workspace.description = meta.description || '';
                state.workspace.icon = meta.icon || '';
                updateWorkspaceButton();
                notificationStore.success('Project metadata saved.', 'global');
                metaHint.textContent = 'Saved.';
            } catch (err) {
                metaHint.textContent = err.message;
                notificationStore.error(`Failed to save metadata: ${err.message}`, 'global');
            } finally {
                saveMetaBtn.disabled = false;
            }
        });
    }

    // Notification Store (frontend)
    // Notification store is now in notifications.js (window.createNotificationStore)
    const notificationStore = window.createNotificationStore();
    window.notificationStore = notificationStore;

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

    async function dismissNotification(notification) {
        if (!notification || !notification.id) return;
        try {
            await notificationsApi.update(notification.id, { dismissed: true, read: true });
        } catch (err) {
            log(`Failed to dismiss notification: ${err.message}`, 'warning');
        }
        notificationStore.dismiss(notification.id);
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

            if (notification.persistent) {
                const dismissBtn = document.createElement('button');
                dismissBtn.type = 'button';
                dismissBtn.className = 'toast-dismiss';
                dismissBtn.textContent = 'Dismiss';
                dismissBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    dismissNotification(notification);
                });
                toast.appendChild(dismissBtn);
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

    function getNotificationPatchPayload(notification) {
        const payload = notification && notification.actionPayload;
        if (!payload || typeof payload !== 'object') return null;
        const kind = payload.kind || payload.type;
        const hasPatchContext = payload.patchId || payload.filePath
            || (Array.isArray(payload.filePaths) && payload.filePaths.length > 0);
        if (!hasPatchContext && kind !== 'review-patch' && kind !== 'open-patch') {
            return null;
        }
        return payload;
    }

    function buildPatchBreadcrumbs(payload) {
        if (!payload) return null;
        const crumbs = [];
        const projectName = payload.projectName || state.workspace.displayName || state.workspace.name;
        const filePath = payload.filePath || (Array.isArray(payload.filePaths) ? payload.filePaths[0] : null);
        if (projectName) crumbs.push({ label: projectName, action: null });
        if (payload.patchId) crumbs.push({ label: `Patch ${payload.patchId}`, action: 'patch' });
        if (filePath) crumbs.push({ label: filePath, action: 'file' });
        return crumbs.length ? crumbs : null;
    }

    function renderNotificationBreadcrumbs(payload) {
        const crumbs = buildPatchBreadcrumbs(payload);
        if (!crumbs) return null;
        const wrap = document.createElement('div');
        wrap.className = 'notification-breadcrumbs';
        const filePath = payload.filePath || (Array.isArray(payload.filePaths) ? payload.filePaths[0] : null);
        crumbs.forEach((crumb, index) => {
            if (index > 0) {
                const sep = document.createElement('span');
                sep.className = 'notification-breadcrumb-sep';
                sep.textContent = '>';
                wrap.appendChild(sep);
            }
            if (crumb.action) {
                const button = document.createElement('button');
                button.type = 'button';
                button.className = 'notification-breadcrumb';
                button.textContent = crumb.label;
                button.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (crumb.action === 'patch' && payload.patchId) {
                        closeNotificationCenter();
                        showPatchReviewModal(payload.patchId);
                        return;
                    }
                    if (crumb.action === 'file' && filePath) {
                        closeNotificationCenter();
                        openFile(filePath);
                    }
                });
                wrap.appendChild(button);
            } else {
                const span = document.createElement('span');
                span.className = 'notification-breadcrumb is-static';
                span.textContent = crumb.label;
                wrap.appendChild(span);
            }
        });
        return wrap;
    }

    function getNotificationFileErrors(payload) {
        if (!payload || !Array.isArray(payload.fileErrors)) return [];
        return payload.fileErrors.filter(entry => entry && (entry.filePath || entry.message));
    }

    function renderNotificationFileErrors(payload) {
        const errors = getNotificationFileErrors(payload);
        if (!errors.length) return null;
        const wrap = document.createElement('div');
        wrap.className = 'notification-file-errors';
        const title = document.createElement('div');
        title.className = 'notification-file-errors-title';
        title.textContent = 'Apply failure details:';
        wrap.appendChild(title);
        const list = document.createElement('ul');
        list.className = 'notification-file-errors-list';
        const maxItems = 6;
        errors.slice(0, maxItems).forEach(entry => {
            const item = document.createElement('li');
            const label = entry.filePath || 'File';
            const message = entry.message || 'Failed to apply';
            item.textContent = `${label}: ${message}`;
            list.appendChild(item);
        });
        if (errors.length > maxItems) {
            const item = document.createElement('li');
            item.textContent = `...and ${errors.length - maxItems} more`;
            list.appendChild(item);
        }
        wrap.appendChild(list);
        return wrap;
    }

    function buildProvenanceLabel(provenance) {
        if (!provenance) return '';
        const parts = [];
        if (provenance.agent) parts.push(`Agent: ${provenance.agent}`);
        if (provenance.model) parts.push(`Model: ${provenance.model}`);
        if (provenance.source) parts.push(`Source: ${provenance.source}`);
        if (provenance.author) parts.push(`Author: ${provenance.author}`);
        return parts.join(' • ');
    }

    function renderNotificationProvenance(payload) {
        if (!payload || !payload.provenance) return null;
        const label = buildProvenanceLabel(payload.provenance);
        if (!label) return null;
        const wrap = document.createElement('div');
        wrap.className = 'notification-provenance';
        wrap.textContent = label;
        return wrap;
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

            const patchPayload = getNotificationPatchPayload(notification);
            if (patchPayload) {
                const breadcrumbs = renderNotificationBreadcrumbs(patchPayload);
                if (breadcrumbs) {
                    item.appendChild(breadcrumbs);
                }
                const errorDetails = renderNotificationFileErrors(patchPayload);
                if (errorDetails) {
                    item.appendChild(errorDetails);
                }
                const provenance = renderNotificationProvenance(patchPayload);
                if (provenance) {
                    item.appendChild(provenance);
                }
            }

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
        const actionKind = payload.kind || payload.type || payload.action;

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
                closeNotificationCenter();
                showPatchReviewModal(payload.patchId, 'editor'); // Default to editor view from notifications
                break;
            case 'openVersioning':
            case 'open-versioning':
                closeNotificationCenter();
                if (window.versioning && window.versioning.openVersioningPanel) {
                    window.versioning.openVersioningPanel(payload.snapshotId || null);
                } else {
                    log('Versioning panel not available', 'warning');
                }
                break;
            default:
                openNotificationCenter(notification.id);
        }
    }


    // ============================================
    // VIEW MODE SWITCHING (Workbench / Editor / Settings)
    // ============================================

    function updateModeControls(mode) {
        const isEditor = mode === 'editor';
        const isWorkbench = mode === 'workbench';
        const isSettings = mode === 'settings';

        // Sidebar toggle mode button - always visible, label changes
        if (elements.btnToggleMode) {
            const toggleLabel = isSettings ? 'Back to Editor' : (isWorkbench ? 'Switch to Editor' : 'Switch to Workbench');
            elements.btnToggleMode.classList.toggle('is-active', isWorkbench);
            elements.btnToggleMode.title = toggleLabel;
            elements.btnToggleMode.setAttribute('aria-label', toggleLabel);
            const btnText = elements.btnToggleMode.querySelector('.btn-text');
            if (btnText) {
                btnText.textContent = toggleLabel;
            }
        }

        // Editor-only sidebar buttons
        const btnToggleExplorer = document.getElementById('btn-toggle-explorer');
        const btnOpenWorkspace = document.getElementById('btn-open-workspace');
        const btnSidebarSearch = document.getElementById('btn-sidebar-search');
        const btnCommit = document.getElementById('btn-commit');
        const btnOpenTerminal = document.getElementById('btn-open-terminal');

        if (btnToggleExplorer) btnToggleExplorer.style.display = isEditor ? 'flex' : 'none';
        if (btnOpenWorkspace) btnOpenWorkspace.style.display = isEditor ? 'flex' : 'none';
        if (btnSidebarSearch) btnSidebarSearch.style.display = isEditor ? 'flex' : 'none';
        if (btnCommit) btnCommit.style.display = isEditor ? 'flex' : 'none';
        if (btnOpenTerminal) btnOpenTerminal.style.display = isEditor ? 'flex' : 'none';

        // Workbench-only sidebar buttons
        const btnSidebarIssues = document.getElementById('btn-sidebar-issues');
        const btnSidebarAssistedMode = document.getElementById('btn-sidebar-assisted-mode');
        const btnSidebarWidgets = document.getElementById('btn-sidebar-widgets');
        const btnSidebarPatchReview = document.getElementById('btn-sidebar-patch-review');

        if (btnSidebarIssues) btnSidebarIssues.style.display = isWorkbench ? 'flex' : 'none';
        if (btnSidebarAssistedMode) btnSidebarAssistedMode.style.display = isWorkbench ? 'flex' : 'none';
        if (btnSidebarWidgets) btnSidebarWidgets.style.display = isWorkbench ? 'flex' : 'none';
        if (btnSidebarPatchReview) btnSidebarPatchReview.style.display = isWorkbench ? 'flex' : 'none';

        // Explorer panel - only show in editor mode, respect explorer toggle state
        if (elements.explorerPanel) {
            if (isEditor) {
                elements.explorerPanel.style.display = 'flex';
                setExplorerVisible(getExplorerVisible());
            } else {
                elements.explorerPanel.style.display = 'none';
            }
        }

        // Top bar buttons - no longer need to hide Issues, Widgets, Patch Review (they're sidebar-only now)

        // Settings button
        if (elements.btnOpenSettings) {
            elements.btnOpenSettings.classList.toggle('is-active', isSettings);
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
        // Sidebar now stays visible in all modes

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

        const closeChatModal = () => {
            closeWithActivity();
            close();
        };

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
                        <div class="settings-nav-item" data-section="prompts">
                            <span class="nav-icon"><img src="assets/icons/heroicons_outline/document-plus.svg" alt=""></span>
                            Prompt Tools
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
                        <div class="settings-nav-item" data-section="tts">
                            <span class="nav-icon"><img src="assets/icons/heroicons_outline/speaker-wave.svg" alt=""></span>
                            Text-to-Speech
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

                        <!-- Prompt Tools Section -->
                        <section class="settings-section" id="settings-prompts">
                            <div class="settings-section-header">
                                <h3>
                                    <img src="assets/icons/heroicons_outline/document-plus.svg" alt="">
                                    Prompt Tools
                                </h3>
                                <p>Define prompt tools that are injected into every agent call.</p>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Tool Editor</div>
                                <div class="settings-card">
                                    <div class="prompt-tools-grid">
                                        <div class="prompt-tools-field">
                                            <label for="prompt-tool-select" class="prompt-tools-label" title="Select an existing prompt tool to edit.">
                                                <img src="assets/icons/heroicons_outline/rectangle-stack.svg" alt="">
                                                <span>Tool</span>
                                            </label>
                                            <select id="prompt-tool-select" class="settings-control settings-control-wide"></select>
                                        </div>
                                        <div class="prompt-tools-field prompt-tools-actions">
                                            <label class="prompt-tools-label" title="Create or remove a prompt tool.">
                                                <img src="assets/icons/heroicons_outline/arrow-path.svg" alt="">
                                                <span>Actions</span>
                                            </label>
                                            <div class="settings-actions">
                                                <button id="prompt-tool-new" type="button">New</button>
                                                <button id="prompt-tool-delete" type="button">Delete</button>
                                            </div>
                                        </div>
                                        <div class="prompt-tools-field">
                                            <label for="prompt-tool-name" class="prompt-tools-label" title="Human-friendly name shown in the tool catalog.">
                                                <img src="assets/icons/heroicons_outline/document-plus.svg" alt="">
                                                <span>Prompt name</span>
                                            </label>
                                            <input id="prompt-tool-name" type="text" class="settings-control settings-control-wide" placeholder="Beat Architect">
                                        </div>
                                        <div class="prompt-tools-field">
                                            <label for="prompt-tool-archetype" class="prompt-tools-label" title="Optional guidance for smaller models. Not a hard restriction.">
                                                <img src="assets/icons/heroicons_outline/queue-list.svg" alt="">
                                                <span>Archetype</span>
                                            </label>
                                            <select id="prompt-tool-archetype" class="settings-control settings-control-wide">
                                                <option value="">Any</option>
                                                <option value="assistant">Assistant</option>
                                                <option value="planner">Planner</option>
                                                <option value="writer">Writer</option>
                                                <option value="editor">Editor</option>
                                                <option value="critic">Critic</option>
                                                <option value="continuity">Continuity</option>
                                            </select>
                                        </div>
                                        <div class="prompt-tools-field">
                                            <label for="prompt-tool-scope" class="prompt-tools-label" title="Suggested scope for using the tool.">
                                                <img src="assets/icons/heroicons_outline/arrows-right-left.svg" alt="">
                                                <span>Scope</span>
                                            </label>
                                            <select id="prompt-tool-scope" class="settings-control settings-control-wide">
                                                <option value="">Any</option>
                                                <option value="selection">Selection</option>
                                                <option value="file">File</option>
                                                <option value="project">Project</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="prompt-tools-field">
                                        <label for="prompt-tool-usage" class="prompt-tools-label" title="When should the model reach for this tool?">
                                            <img src="assets/icons/heroicons_outline/magnifying-glass.svg" alt="">
                                            <span>Usage notes</span>
                                        </label>
                                        <textarea id="prompt-tool-usage" rows="2" class="settings-control settings-control-wide" placeholder="When should this tool be used?"></textarea>
                                    </div>
                                    <div class="prompt-tools-field">
                                        <label for="prompt-tool-goals" class="prompt-tools-label" title="What is this prompt trying to accomplish?">
                                            <img src="assets/icons/heroicons_outline/archive-box-arrow-down.svg" alt="">
                                            <span>Goals & scope</span>
                                        </label>
                                        <textarea id="prompt-tool-goals" rows="2" class="settings-control settings-control-wide" placeholder="What is this prompt for?"></textarea>
                                    </div>
                                    <div class="prompt-tools-field">
                                        <label for="prompt-tool-guardrails" class="prompt-tools-label" title="Limits, must-not-do rules, or safety constraints.">
                                            <img src="assets/icons/heroicons_outline/key.svg" alt="">
                                            <span>Guardrails</span>
                                        </label>
                                        <textarea id="prompt-tool-guardrails" rows="2" class="settings-control settings-control-wide" placeholder="Limitations or must-not-do rules."></textarea>
                                    </div>
                                    <div class="prompt-tools-field">
                                        <label for="prompt-tool-prompt" class="prompt-tools-label" title="Full prompt text injected into the tool catalog.">
                                            <img src="assets/icons/heroicons_outline/pencil-square.svg" alt="">
                                            <span>Prompt</span>
                                        </label>
                                        <textarea id="prompt-tool-prompt" rows="8" class="settings-control settings-control-wide" placeholder="Full prompt text..."></textarea>
                                    </div>
                                    <div class="settings-actions">
                                        <button id="prompt-tool-save" type="button" class="primary">Save</button>
                                        <button id="prompt-tool-cancel" type="button">Cancel</button>
                                    </div>
                                    <div id="prompt-tool-status" class="settings-feedback" aria-live="polite"></div>
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
                                                <span>Search in Project</span>
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

                        <!-- TTS Section -->
                        <section class="settings-section" id="settings-tts">
                            <div class="settings-section-header">
                                <h3>
                                    <img src="assets/icons/heroicons_outline/speaker-wave.svg" alt="">
                                    Text-to-Speech
                                </h3>
                                <p>Configure voice synthesis for reading text aloud.</p>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Voice Settings</div>
                                <div class="settings-card">
                                    <div class="settings-row">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Default Voice</span>
                                            <span class="settings-label-desc">Voice used for text-to-speech</span>
                                        </div>
                                        <select class="settings-control settings-control-wide" id="settings-tts-voice">
                                            <option value="">Loading voices...</option>
                                        </select>
                                    </div>
                                    <div class="settings-row">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Speed</span>
                                            <span class="settings-label-desc">Playback speed (0.5x to 2.0x)</span>
                                        </div>
                                        <div class="settings-inline">
                                            <input type="range" class="settings-slider" id="settings-tts-speed" min="0.5" max="2.0" step="0.1" value="1.0">
                                            <span class="settings-slider-value" id="settings-tts-speed-value">1.0x</span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Preview</div>
                                <div class="settings-card">
                                    <div class="settings-row">
                                        <div class="settings-label">
                                            <span class="settings-label-text">Test Text</span>
                                            <span class="settings-label-desc">Text to read aloud</span>
                                        </div>
                                        <input type="text" class="settings-control settings-control-wide" id="settings-tts-preview-text" value="The quick brown fox jumps over the lazy dog.">
                                    </div>
                                    <div class="settings-row">
                                        <div class="settings-inline">
                                            <button class="settings-button settings-button-primary" type="button" id="settings-tts-test">
                                                Test Voice
                                            </button>
                                            <button class="settings-button" type="button" id="settings-tts-stop" disabled>
                                                Stop
                                            </button>
                                            <span class="settings-status" id="settings-tts-status"></span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="settings-group">
                                <div class="settings-group-title">Add More Voices</div>
                                <div class="settings-card">
                                    <div class="settings-subsection">
                                        <p class="settings-hint">
                                            Download Piper voice models (.onnx + .onnx.json) and drop them in the voices folder.
                                        </p>
                                        <div class="settings-row">
                                            <div class="settings-inline">
                                                <button class="settings-button" type="button" id="settings-tts-browse-voices">
                                                    Browse Voices
                                                </button>
                                                <button class="settings-button" type="button" id="settings-tts-open-folder">
                                                    Open Voices Folder
                                                </button>
                                            </div>
                                        </div>
                                        <p class="settings-hint settings-hint-muted">
                                            Voices are loaded on app startup. Restart Control Room after adding new voices.
                                        </p>
                                    </div>
                                </div>
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
        initPromptToolsControls();
        initTtsSettingsWiring();
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

    async function initTtsSettingsWiring() {
        const voiceSelect = document.getElementById('settings-tts-voice');
        const speedSlider = document.getElementById('settings-tts-speed');
        const speedValue = document.getElementById('settings-tts-speed-value');
        const previewText = document.getElementById('settings-tts-preview-text');
        const testBtn = document.getElementById('settings-tts-test');
        const stopBtn = document.getElementById('settings-tts-stop');
        const statusEl = document.getElementById('settings-tts-status');
        const browseVoicesBtn = document.getElementById('settings-tts-browse-voices');
        const openFolderBtn = document.getElementById('settings-tts-open-folder');

        if (!voiceSelect || !speedSlider) return;

        let currentAudio = null;
        let currentUtterance = null; // For system voice
        let systemVoicesCache = [];

        // Get flag emoji for language code
        const getLangFlag = (langCode) => {
            if (!langCode) return '';
            const flags = {
                'en-US': '\u{1F1FA}\u{1F1F8}', 'en-GB': '\u{1F1EC}\u{1F1E7}', 'en-AU': '\u{1F1E6}\u{1F1FA}',
                'de-DE': '\u{1F1E9}\u{1F1EA}', 'de-AT': '\u{1F1E6}\u{1F1F9}', 'de-CH': '\u{1F1E8}\u{1F1ED}',
                'fr-FR': '\u{1F1EB}\u{1F1F7}', 'fr-CA': '\u{1F1E8}\u{1F1E6}',
                'es-ES': '\u{1F1EA}\u{1F1F8}', 'es-MX': '\u{1F1F2}\u{1F1FD}',
                'it-IT': '\u{1F1EE}\u{1F1F9}', 'ja-JP': '\u{1F1EF}\u{1F1F5}',
                'zh-CN': '\u{1F1E8}\u{1F1F3}', 'zh-TW': '\u{1F1F9}\u{1F1FC}',
                'ko-KR': '\u{1F1F0}\u{1F1F7}', 'ru-RU': '\u{1F1F7}\u{1F1FA}',
                'pt-BR': '\u{1F1E7}\u{1F1F7}', 'pt-PT': '\u{1F1F5}\u{1F1F9}',
                'nl-NL': '\u{1F1F3}\u{1F1F1}', 'pl-PL': '\u{1F1F5}\u{1F1F1}',
                'sv-SE': '\u{1F1F8}\u{1F1EA}', 'da-DK': '\u{1F1E9}\u{1F1F0}',
                'nb-NO': '\u{1F1F3}\u{1F1F4}', 'fi-FI': '\u{1F1EB}\u{1F1EE}',
            };
            // Try exact match first, then language prefix
            const normalized = langCode.replace('_', '-');
            if (flags[normalized]) return flags[normalized];
            const lang = normalized.split('-')[0];
            for (const [key, flag] of Object.entries(flags)) {
                if (key.startsWith(lang + '-')) return flag;
            }
            return '';
        };

        // Get system voices (Web Speech API)
        const getSystemVoices = () => {
            return new Promise(resolve => {
                const voices = speechSynthesis.getVoices();
                if (voices.length > 0) {
                    resolve(voices);
                } else {
                    // Chrome loads voices async
                    speechSynthesis.onvoiceschanged = () => {
                        resolve(speechSynthesis.getVoices());
                    };
                    // Timeout fallback
                    setTimeout(() => resolve(speechSynthesis.getVoices()), 500);
                }
            });
        };

        // Load voices and settings
        const loadTtsData = async () => {
            try {
                const [voicesData, settingsData] = await Promise.all([
                    fetch('/api/tts/voices').then(r => r.json()),
                    fetch('/api/tts/settings').then(r => r.json())
                ]);

                // Get system voices
                systemVoicesCache = await getSystemVoices();

                // Populate voice dropdown with optgroups
                voiceSelect.innerHTML = '';

                // Piper voices group
                const piperVoices = voicesData.voices || [];
                if (piperVoices.length > 0) {
                    const piperGroup = document.createElement('optgroup');
                    piperGroup.label = 'Piper (Local Neural TTS)';
                    piperVoices.forEach(voice => {
                        const opt = document.createElement('option');
                        opt.value = 'piper:' + voice.id;
                        // Extract lang from voice id (e.g., "en_US-amy-medium" -> "en-US")
                        const langMatch = voice.id.match(/^([a-z]{2})_([A-Z]{2})/);
                        const langCode = langMatch ? `${langMatch[1]}-${langMatch[2]}` : '';
                        const flag = getLangFlag(langCode);
                        opt.textContent = flag ? `${flag} ${voice.name}` : voice.name;
                        opt.dataset.type = 'piper';
                        if (settingsData.voice === 'piper:' + voice.id || settingsData.voice === voice.id) {
                            opt.selected = true;
                        }
                        piperGroup.appendChild(opt);
                    });
                    voiceSelect.appendChild(piperGroup);
                }

                // System voices group
                if (systemVoicesCache.length > 0) {
                    const systemGroup = document.createElement('optgroup');
                    systemGroup.label = 'System Voices';

                    // Sort by language, then name
                    const sortedVoices = [...systemVoicesCache].sort((a, b) => {
                        const langA = a.lang || '';
                        const langB = b.lang || '';
                        if (langA !== langB) return langA.localeCompare(langB);
                        return a.name.localeCompare(b.name);
                    });

                    sortedVoices.forEach(voice => {
                        const opt = document.createElement('option');
                        opt.value = 'system:' + voice.name;
                        // Format: "Name (Language)" with flag emoji if possible
                        const langFlag = getLangFlag(voice.lang);
                        opt.textContent = `${langFlag} ${voice.name}`;
                        opt.dataset.type = 'system';
                        opt.dataset.voiceName = voice.name;
                        if (settingsData.voice === 'system:' + voice.name) {
                            opt.selected = true;
                        }
                        systemGroup.appendChild(opt);
                    });
                    voiceSelect.appendChild(systemGroup);
                }

                // Fallback if no voices at all
                if (piperVoices.length === 0 && systemVoicesCache.length === 0) {
                    const opt = document.createElement('option');
                    opt.value = '';
                    opt.textContent = 'No voices available';
                    voiceSelect.appendChild(opt);
                }

                // Set speed
                speedSlider.value = settingsData.speed || 1.0;
                speedValue.textContent = speedSlider.value + 'x';
            } catch (err) {
                console.error('Failed to load TTS settings:', err);
                voiceSelect.innerHTML = '<option value="">Error loading voices</option>';
            }
        };

        // Save settings
        const saveSettings = async () => {
            try {
                await fetch('/api/tts/settings', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        voice: voiceSelect.value,
                        speed: parseFloat(speedSlider.value)
                    })
                });
            } catch (err) {
                console.error('Failed to save TTS settings:', err);
            }
        };

        // Update speed display and save
        speedSlider.addEventListener('input', () => {
            speedValue.textContent = speedSlider.value + 'x';
        });
        speedSlider.addEventListener('change', saveSettings);
        voiceSelect.addEventListener('change', saveSettings);

        // Stop any current playback (Piper audio or system speech)
        const stopPlayback = () => {
            if (currentAudio) {
                currentAudio.pause();
                currentAudio = null;
            }
            if (currentUtterance) {
                speechSynthesis.cancel();
                currentUtterance = null;
            }
            testBtn.disabled = false;
            stopBtn.disabled = true;
            statusEl.textContent = '';
        };

        // Play with system voice (Web Speech API)
        const playSystemVoice = (text, voiceName, speed) => {
            const utterance = new SpeechSynthesisUtterance(text);
            const voice = systemVoicesCache.find(v => v.name === voiceName);
            if (voice) {
                utterance.voice = voice;
            }
            utterance.rate = speed; // 0.1 to 10, default 1

            utterance.onend = () => {
                currentUtterance = null;
                testBtn.disabled = false;
                stopBtn.disabled = true;
                statusEl.textContent = '';
            };

            utterance.onerror = (e) => {
                console.error('System TTS error:', e);
                currentUtterance = null;
                testBtn.disabled = false;
                stopBtn.disabled = true;
                statusEl.textContent = 'Speech error';
            };

            currentUtterance = utterance;
            statusEl.textContent = 'Speaking...';
            speechSynthesis.speak(utterance);
        };

        // Play with Piper (server-side)
        const playPiperVoice = async (text, voiceId, speed) => {
            statusEl.textContent = 'Generating audio...';

            const response = await fetch('/api/tts/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    text: text,
                    voice: voiceId,
                    speed: speed
                })
            });

            if (!response.ok) {
                const errData = await response.json();
                throw new Error(errData.error || 'TTS request failed');
            }

            const audioBlob = await response.blob();
            const audioUrl = URL.createObjectURL(audioBlob);
            currentAudio = new Audio(audioUrl);

            currentAudio.addEventListener('ended', () => {
                testBtn.disabled = false;
                stopBtn.disabled = true;
                statusEl.textContent = '';
                URL.revokeObjectURL(audioUrl);
                currentAudio = null;
            });

            currentAudio.addEventListener('error', (e) => {
                testBtn.disabled = false;
                stopBtn.disabled = true;
                statusEl.textContent = 'Playback error';
                URL.revokeObjectURL(audioUrl);
                currentAudio = null;
            });

            statusEl.textContent = 'Playing...';
            await currentAudio.play();
        };

        // Test voice
        testBtn.addEventListener('click', async () => {
            const text = previewText.value.trim();
            if (!text) {
                statusEl.textContent = 'Enter some text to preview';
                return;
            }

            const voiceValue = voiceSelect.value;
            if (!voiceValue) {
                statusEl.textContent = 'No voice selected';
                return;
            }

            // Stop any current playback
            stopPlayback();

            testBtn.disabled = true;
            stopBtn.disabled = false;

            try {
                const speed = parseFloat(speedSlider.value);

                if (voiceValue.startsWith('system:')) {
                    // System voice (Web Speech API)
                    const voiceName = voiceValue.substring(7);
                    playSystemVoice(text, voiceName, speed);
                } else {
                    // Piper voice (strip 'piper:' prefix if present)
                    const voiceId = voiceValue.startsWith('piper:') ? voiceValue.substring(6) : voiceValue;
                    await playPiperVoice(text, voiceId, speed);
                }
            } catch (err) {
                console.error('TTS test failed:', err);
                statusEl.textContent = err.message || 'TTS failed';
                testBtn.disabled = false;
                stopBtn.disabled = true;
            }
        });

        // Stop playback
        stopBtn.addEventListener('click', stopPlayback);

        // Browse voices (open Piper samples page)
        browseVoicesBtn.addEventListener('click', () => {
            window.open('https://rhasspy.github.io/piper-samples/', '_blank');
        });

        // Open voices folder
        openFolderBtn.addEventListener('click', async () => {
            try {
                const response = await fetch('/api/tts/open-folder', { method: 'POST' });
                const data = await response.json();
                if (!response.ok) {
                    throw new Error(data.error || 'Failed to open folder');
                }
            } catch (err) {
                console.error('Failed to open voices folder:', err);
                notificationStore.error('Failed to open voices folder: ' + err.message, 'global');
            }
        });

        // Initial load
        await loadTtsData();
    }

    // ============================================
    // WORKBENCH VIEW RENDERING
    // ============================================

    function renderWorkbenchView() {
        renderAgentSidebar();
        renderWorkbenchChatPane();
        renderWorkbenchNewsfeed();
    }

    function renderWorkbenchChatPane() {
        renderWidgetDashboard();
    }

    function getPlannerAgent() {
        const agents = state.agents.list || [];
        const primaryPlanner = agents.find(agent => canonicalizeRole(agent.role) === 'planner' && agent.isPrimaryForRole);
        if (primaryPlanner) return primaryPlanner;
        const planner = agents.find(agent => canonicalizeRole(agent.role) === 'planner');
        if (planner) return planner;
        return agents[0] || null;
    }

    function getChiefOfStaffAgent() {
        const agents = state.agents.list || [];
        const chief = agents.find(agent => isAssistantAgent(agent));
        if (chief) return chief;
        return agents[0] || null;
    }

    function renderWorkbenchDashboard() {
        const container = document.getElementById('workbench-chat-content');
        if (!container) return;

        const leader = getChiefOfStaffAgent();
        const leaderName = leader?.name || 'Chief of Staff';
        const leaderAvatar = leader?.avatar || '';
        const agentsLocked = state.agents.locked;

        container.innerHTML = `
            <div class="workbench-dashboard">
                <div class="workbench-card workbench-card-hero">
                    <div class="workbench-briefing-header">
                        <div class="workbench-briefing-avatar" id="workbench-briefing-avatar"></div>
                        <div>
                            <div class="workbench-card-title">Today's Briefing</div>
                            <div class="workbench-card-subtitle">Resolved issues and momentum check-in.</div>
                        </div>
                        <button class="workbench-briefing-dismiss" id="workbench-dismiss-briefing" type="button">Dismiss</button>
                    </div>
                    <div class="workbench-briefing" id="workbench-briefing">
                        <div class="workbench-digest-loading">Loading digest...</div>
                    </div>
                    <div class="workbench-briefing-actions">
                        <button class="workbench-link-btn" id="workbench-open-issues" type="button">Open Issues</button>
                        <button class="workbench-link-btn" id="workbench-start-conference" type="button" ${agentsLocked ? 'disabled' : ''}>Start Conference</button>
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
            if (agentsLocked) {
                startConferenceBtn.title = 'Agents are locked until a Chief of Staff exists.';
            }
            startConferenceBtn.addEventListener('click', () => {
                ensureChiefOfStaff('Start conference', () => showConferenceInviteModal());
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
        window.renderIssueBoardContent();

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
            window.renderIssueBoardContent();
        } catch (err) {
            state.issueBoard.isLoading = false;
            state.issueBoard.error = err.message;
            window.renderIssueBoardContent();
        }
    }

    // ============================================
    // WORKBENCH NEWSFEED (Notification-backed)
    // ============================================

    function getStoredAgentId() {
        return localStorage.getItem('selected-agent-id');
    }

    function pickDefaultAgentId(agents) {
        if (!agents || agents.length === 0) return null;

        const stored = getStoredAgentId();
        if (stored && agents.some(agent => agent.id === stored)) {
            return stored;
        }

        const primaryWriter = agents.find(agent => agent.isPrimaryForRole && canonicalizeRole(agent.role) === 'writer');
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
            updateAgentLockState();
            renderAgentSidebar();
            renderAgentSelector();
            await loadAgentStatuses();
            log(`Loaded ${state.agents.list.length} agent(s)`, 'info');
        } catch (err) {
            state.agents.list = [];
            state.agents.statusById = {};
            updateAgentLockState();
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

        const resetAssistedIfModelChanged = async (agent, endpoint) => {
            if (!agent || !agent.assisted || !agent.assistedModel || !endpoint?.model) {
                return;
            }
            if (agent.assistedModel === endpoint.model) {
                return;
            }
            agent.assisted = false;
            agent.assistedReason = null;
            agent.assistedSince = null;
            agent.assistedModel = null;
            try {
                await agentApi.update(agent.id, {
                    assisted: false,
                    assistedReason: null,
                    assistedSince: null,
                    assistedModel: null
                });
            } catch (err) {
                log(`Failed to reset assisted mode for ${agent.name}: ${err.message}`, 'warning');
            }
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
            void resetAssistedIfModelChanged(agent, endpoint);
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

        const hasMeta = meta && (meta.repLevel || meta.escalated || meta.stopHook || (meta.witnesses && meta.witnesses.length));
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
            if (meta.stopHook) {
                const stop = document.createElement('span');
                stop.className = 'chat-badge chat-badge-stop';
                stop.textContent = `Stop: ${meta.stopHook}`;
                metaDiv.appendChild(stop);
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

    function updateChatMemoryBadge(meta) {
        if (!elements.chatMemoryBadge) return;
        if (meta && meta.repLevel) {
            elements.chatMemoryBadge.textContent = `R${meta.repLevel}${meta.escalated ? ' ↑' : ''}`;
            elements.chatMemoryBadge.classList.remove('hidden');
        } else {
            elements.chatMemoryBadge.classList.add('hidden');
        }
        if (elements.chatEscalate) {
            const stopHookActive = state.chat.lastStopHook && state.chat.lastStopHook.hook;
            elements.chatEscalate.disabled = !state.chat.lastPayload || stopHookActive;
        }
    }

    async function sendChatMessage(reroll = false) {
        const draftMessage = elements.chatInput.value.trim();
        const previous = state.chat.lastPayload;
        const message = reroll && previous ? previous.message : draftMessage;
        if (!message) return;
        if (reroll && state.chat.lastStopHook) {
            notificationStore.warning(`Stop hook active: ${state.chat.lastStopHook.hook}`, 'editor');
            return;
        }

        if (!reroll) {
            elements.chatInput.value = '';
            addChatMessage('user', message);
            state.chat.lastPayload = {
                message,
                agentId: state.agents.selectedId || null,
                memoryId: state.chat.memoryId
            };
            state.chat.lastStopHook = null;
            updateChatMemoryBadge(state.chat.lastResponseMeta);
        }

        elements.chatSend.disabled = true;

        log(`Chat: User message sent${reroll ? ' (reroll)' : ''}`, 'info');

        const agentId = (reroll && previous ? previous.agentId : state.agents.selectedId) || null;
        const agent = agentId ? (state.agents.list || []).find(item => item.id === agentId) : null;
        const runChat = async () => {
            const requestMessage = agent && buildChatPrompt ? buildChatPrompt(message, agent) : message;
            const payload = {
                message: requestMessage,
                memoryId: state.chat.memoryId || undefined,
                reroll
            };
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

            const rawContent = response && response.content ? response.content : '';
            const parsed = extractStopHook ? extractStopHook(rawContent) : { content: rawContent, stopHook: null, stopHookDetail: '' };
            const meta = {
                memoryId: response.memoryId || payload.memoryId,
                repLevel: response.repLevel,
                escalated: !!response.escalated,
                witnesses: response.witnesses,
                stopHook: parsed.stopHook
            };
            state.chat.lastStopHook = parsed.stopHook
                ? { hook: parsed.stopHook, detail: parsed.stopHookDetail }
                : null;
            state.chat.lastResponseMeta = meta;
            updateChatMemoryBadge(meta);

            const reply = parsed.content ? parsed.content : 'No response.';
            addChatMessage('assistant', reply, meta);
            log(`Chat: AI response received${meta.escalated ? ' (escalated)' : ''}`, 'success');
            if (parsed.stopHook) {
                log(`Chat stop hook: ${parsed.stopHook}`, 'warning');
                notificationStore.warning(`Stop hook: ${parsed.stopHook}`, 'editor');
            }
        };

        try {
            if (agentId) {
                await withAgentTurn(agentId, 'processing', runChat, 'Responding to chat');
            } else {
                await runChat();
            }
        } catch (err) {
            addChatMessage('assistant', 'Sorry, I encountered an error. Please try again.');
            log(`Chat error: ${err.message}`, 'error');
        } finally {
            elements.chatSend.disabled = false;
        }
    }

    // ============================================
    // AI FOUNDATION (READ-ONLY)
    // ============================================

    const AI_FOUNDATION_TOOLS = {
        summarize: {
            label: 'Summarize',
            schema: '{"tool":"summarize","summary":"...","bullets":["..."],"keywords":["..."]}',
            rules: [
                'summary: 2-4 sentences, plain text.',
                'bullets: 3-6 short bullets, no markdown.',
                'keywords: 4-8 short phrases.',
                'No external facts; use only the input.'
            ]
        },
        explain: {
            label: 'Explain',
            schema: '{"tool":"explain","explanation":"...","key_points":["..."],"style_notes":["..."]}',
            rules: [
                'explanation: 2-4 sentences describing what this document is and what it means (not a summary).',
                'Include the document type if you can infer it (e.g., character profile, scene, outline, notes).',
                'Mention any explicit labels or roles stated in the text (e.g., "POV character").',
                'key_points: 3-6 short bullets capturing the document purpose, audience, or intent.',
                'style_notes: 0-3 short bullets about the text form/structure only (optional).',
                'No external facts; use only the input.'
            ]
        },
        suggest: {
            label: 'Suggest',
            schema: '{"tool":"suggest","suggestions":[{"title":"...","detail":"...","impact":"low|medium|high"}],"notes":"..."}',
            rules: [
                'First, infer what kind of text this is, then suggest improvements appropriate to that type.',
                'suggestions: 3-5 items.',
                'detail: 1-2 sentences each, plain text. Be specific to the content.',
                'impact: low | medium | high.',
                'notes: optional single sentence about overall fit/coverage.'
            ]
        }
    };
    const AI_FOUNDATION_MAX_CHARS = null;

    function normalizeToolArray(values) {
        if (!Array.isArray(values)) return [];
        return values.map(item => String(item || '').trim()).filter(Boolean);
    }

    function extractJsonObject(text) {
        if (!text) return null;
        const cleaned = String(text).replace(/<think>[\s\S]*?<\/think>/gi, '');
        const markerMatch = cleaned.match(/BEGIN_JSON\s*([\s\S]*?)\s*END_JSON/i);
        if (markerMatch && markerMatch[1]) {
            try {
                return JSON.parse(markerMatch[1].trim());
            } catch (_) {
                // Fall back to loose extraction.
            }
        }
        const match = cleaned.match(/\{[\s\S]*\}/);
        if (!match) return null;
        try {
            const parsed = JSON.parse(match[0].trim());
            if (typeof parsed === 'string' && parsed.trim().startsWith('{')) {
                return JSON.parse(parsed);
            }
            return parsed;
        } catch (_) {
            return null;
        }
    }

    function normalizeToolPayload(toolId, payload) {
        const safe = (payload && typeof payload === 'object') ? payload : {};
        if (toolId === 'summarize') {
            return {
                tool: 'summarize',
                summary: String(safe.summary || '').trim(),
                bullets: normalizeToolArray(safe.bullets),
                keywords: normalizeToolArray(safe.keywords)
            };
        }
        if (toolId === 'explain') {
            return {
                tool: 'explain',
                explanation: String(safe.explanation || '').trim(),
                key_points: normalizeToolArray(safe.key_points || safe.keyPoints),
                style_notes: normalizeToolArray(safe.style_notes || safe.styleNotes)
            };
        }
        return {
            tool: 'suggest',
            suggestions: Array.isArray(safe.suggestions)
                ? safe.suggestions
                    .map(item => ({
                        title: String(item?.title || '').trim(),
                        detail: String(item?.detail || '').trim(),
                        impact: String(item?.impact || '').trim().toLowerCase()
                    }))
                    .map(item => ({
                        ...item,
                        impact: ['low', 'medium', 'high'].includes(item.impact) ? item.impact : 'medium'
                    }))
                    .filter(item => item.title || item.detail)
                : [],
            notes: String(safe.notes || '').trim()
        };
    }

    function trimToLimit(text, maxChars) {
        if (!text) return { text: '', truncated: false };
        if (!maxChars) return { text, truncated: false };
        if (text.length <= maxChars) return { text, truncated: false };
        return { text: text.slice(0, maxChars).trimEnd(), truncated: true };
    }

    function resolveFoundationScope() {
        if (!elements.aiFoundationScope) return 'auto';
        const value = elements.aiFoundationScope.value || 'auto';
        if (value === 'selection' || value === 'file' || value === 'auto') return value;
        return 'auto';
    }

    function getEditorInputForScope(scope) {
        if (!state.editor) {
            return { error: 'Open a file first to use AI tools.' };
        }
        const model = state.editor.getModel();
        if (!model) {
            return { error: 'No editor model is available.' };
        }

        const selection = state.editor.getSelection();
        const selectionText = selection && !selection.isEmpty()
            ? model.getValueInRange(selection)
            : '';

        const useSelection = scope === 'selection' || (scope === 'auto' && selectionText.trim());
        if (useSelection) {
            if (!selectionText.trim()) {
                return { error: 'Select some text first, or switch scope to Whole file.' };
            }
            const trimmed = trimToLimit(selectionText.trim(), AI_FOUNDATION_MAX_CHARS);
            return {
                text: trimmed.text,
                truncated: trimmed.truncated,
                scopeLabel: 'Selection',
                charCount: trimmed.text.length
            };
        }

        const fileText = model.getValue();
        if (!fileText || !fileText.trim()) {
            return { error: 'The current file is empty.' };
        }
        const trimmed = trimToLimit(fileText.trim(), AI_FOUNDATION_MAX_CHARS);
        const fileLabel = state.activeFile ? `File: ${state.activeFile}` : 'File';
        return {
            text: trimmed.text,
            truncated: trimmed.truncated,
            scopeLabel: fileLabel,
            charCount: trimmed.text.length
        };
    }

    function buildFoundationPrompt(toolId, input, attempt) {
        const tool = AI_FOUNDATION_TOOLS[toolId];
        const base = [
            'Return ONLY valid JSON wrapped between BEGIN_JSON and END_JSON.',
            'No markdown, no preface, no code fences.',
            'Use double quotes for all keys and string values.',
            'Do not include STOP_HOOK or any extra text.',
            `Schema: ${tool.schema}`,
            ...tool.rules
        ];
        if (attempt > 1) {
            base.unshift('Retry: respond with JSON only. If uncertain, return empty strings/arrays but keep schema.');
        }
        base.push('', 'Text:', input.text, '', 'BEGIN_JSON', 'END_JSON');
        return base.join('\n');
    }

    function setFoundationButtonsEnabled(enabled) {
        if (elements.btnAiSummarize) elements.btnAiSummarize.disabled = !enabled;
        if (elements.btnAiExplain) elements.btnAiExplain.disabled = !enabled;
        if (elements.btnAiSuggest) elements.btnAiSuggest.disabled = !enabled;
        if (elements.aiFoundationScope) elements.aiFoundationScope.disabled = !enabled;
    }

    async function copyToClipboard(text) {
        if (!text) return;
        if (navigator.clipboard && navigator.clipboard.writeText) {
            await navigator.clipboard.writeText(text);
            return;
        }
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        document.execCommand('copy');
        textarea.remove();
    }

    function renderFoundationOutput(toolId, data) {
        const output = document.createElement('div');
        output.className = 'ai-foundation-output';

        if (toolId === 'summarize') {
            const heading = document.createElement('h3');
            heading.textContent = 'Summary';
            output.appendChild(heading);

            const summary = document.createElement('div');
            summary.textContent = data.summary || 'No summary returned.';
            output.appendChild(summary);

            if (data.bullets.length) {
                const bulletsHeading = document.createElement('h3');
                bulletsHeading.textContent = 'Bullets';
                output.appendChild(bulletsHeading);
                const bullets = document.createElement('div');
                bullets.className = 'ai-foundation-list';
                data.bullets.forEach(item => {
                    const row = document.createElement('div');
                    row.textContent = `- ${item}`;
                    bullets.appendChild(row);
                });
                output.appendChild(bullets);
            }

            if (data.keywords.length) {
                const keywordsHeading = document.createElement('h3');
                keywordsHeading.textContent = 'Keywords';
                output.appendChild(keywordsHeading);
                const keywords = document.createElement('div');
                keywords.textContent = `Keywords: ${data.keywords.join(', ')}`;
                output.appendChild(keywords);
            }
            return output;
        }

        if (toolId === 'explain') {
            const heading = document.createElement('h3');
            heading.textContent = 'Explanation';
            output.appendChild(heading);

            const explanation = document.createElement('div');
            explanation.textContent = data.explanation || 'No explanation returned.';
            output.appendChild(explanation);

            if (data.key_points.length) {
                const pointsHeading = document.createElement('h3');
                pointsHeading.textContent = 'Key points';
                output.appendChild(pointsHeading);
                const points = document.createElement('div');
                points.className = 'ai-foundation-list';
                data.key_points.forEach(item => {
                    const row = document.createElement('div');
                    row.textContent = `- ${item}`;
                    points.appendChild(row);
                });
                output.appendChild(points);
            }

            if (data.style_notes.length) {
                const notesHeading = document.createElement('h3');
                notesHeading.textContent = 'Style notes';
                output.appendChild(notesHeading);
                const notes = document.createElement('div');
                notes.className = 'ai-foundation-list';
                data.style_notes.forEach(item => {
                    const row = document.createElement('div');
                    row.textContent = `- ${item}`;
                    notes.appendChild(row);
                });
                output.appendChild(notes);
            }
            return output;
        }

        const suggestions = Array.isArray(data.suggestions) ? data.suggestions : [];
        if (suggestions.length) {
            const listHeading = document.createElement('h3');
            listHeading.textContent = 'Suggestions';
            output.appendChild(listHeading);
            const list = document.createElement('div');
            list.className = 'ai-foundation-list';
            suggestions.forEach(item => {
                const row = document.createElement('div');
                const title = item.title ? `${item.title} (${item.impact || 'medium'})` : `Suggestion (${item.impact || 'medium'})`;
                row.textContent = `${title}: ${item.detail || ''}`.trim();
                list.appendChild(row);
            });
            output.appendChild(list);
        } else {
            const empty = document.createElement('div');
            empty.textContent = 'No suggestions returned.';
            output.appendChild(empty);
        }

        if (data.notes) {
            const notesHeading = document.createElement('h3');
            notesHeading.textContent = 'Notes';
            output.appendChild(notesHeading);
            const notes = document.createElement('div');
            notes.textContent = `Notes: ${data.notes}`;
            output.appendChild(notes);
        }

        return output;
    }

    function formatFoundationTextForCopy(toolId, data) {
        if (!data || typeof data !== 'object') return '';
        const lines = [];
        if (toolId === 'summarize') {
            if (data.summary) {
                lines.push(data.summary);
            }
            if (data.bullets && data.bullets.length) {
                lines.push('');
                lines.push('Bullets:');
                data.bullets.forEach(item => lines.push(`- ${item}`));
            }
            if (data.keywords && data.keywords.length) {
                lines.push('');
                lines.push(`Keywords: ${data.keywords.join(', ')}`);
            }
            return lines.join('\n').trim();
        }

        if (toolId === 'explain') {
            if (data.explanation) {
                lines.push(data.explanation);
            }
            if (data.key_points && data.key_points.length) {
                lines.push('');
                lines.push('Key points:');
                data.key_points.forEach(item => lines.push(`- ${item}`));
            }
            if (data.style_notes && data.style_notes.length) {
                lines.push('');
                lines.push('Style notes:');
                data.style_notes.forEach(item => lines.push(`- ${item}`));
            }
            return lines.join('\n').trim();
        }

        const suggestions = Array.isArray(data.suggestions) ? data.suggestions : [];
        if (suggestions.length) {
            lines.push('Suggestions:');
            suggestions.forEach(item => {
                const impact = item.impact ? ` (${item.impact})` : '';
                const title = item.title ? `${item.title}${impact}` : `Suggestion${impact}`;
                lines.push(`- ${title}: ${item.detail || ''}`.trim());
            });
        }
        if (data.notes) {
            lines.push('');
            lines.push(`Notes: ${data.notes}`);
        }
        return lines.join('\n').trim();
    }

    function showFoundationResultModal(toolId, data, meta) {
        const tool = AI_FOUNDATION_TOOLS[toolId];
        const title = tool ? `${tool.label} Result` : 'AI Tool Result';
        const { modal, body, confirmBtn } = createModalShell(
            title,
            'Copy Result',
            'Close',
            { closeOnCancel: true, closeOnConfirm: false }
        );

        modal.classList.add('ai-foundation-modal');
        confirmBtn.addEventListener('click', async () => {
            try {
                await copyToClipboard(formatFoundationTextForCopy(toolId, data));
                notificationStore.success('Copied result to clipboard.', 'editor');
            } catch (err) {
                notificationStore.error(`Copy failed: ${err.message}`, 'editor');
            }
        });
        const container = document.createElement('div');
        container.className = 'ai-foundation-result';

        const metaLine = document.createElement('div');
        metaLine.className = 'ai-foundation-meta';
        metaLine.textContent = `${meta.scopeLabel} - ${meta.charCount} chars`;
        container.appendChild(metaLine);

        const output = renderFoundationOutput(toolId, data);
        container.appendChild(output);

        body.appendChild(container);
    }

    function showFoundationErrorModal(toolId, meta, rawText, errorMessage) {
        const tool = AI_FOUNDATION_TOOLS[toolId];
        const title = tool ? `${tool.label} Error` : 'AI Tool Error';
        const { modal, body, confirmBtn } = createModalShell(
            title,
            'Copy Raw',
            'Close',
            { closeOnCancel: true, closeOnConfirm: false }
        );
        modal.classList.add('ai-foundation-modal');

        confirmBtn.addEventListener('click', async () => {
            try {
                await copyToClipboard(rawText || '');
                notificationStore.success('Copied raw response to clipboard.', 'editor');
            } catch (err) {
                notificationStore.error(`Copy failed: ${err.message}`, 'editor');
            }
        });

        const container = document.createElement('div');
        container.className = 'ai-foundation-result';

        const metaLine = document.createElement('div');
        metaLine.className = 'ai-foundation-meta';
        metaLine.textContent = `${meta.scopeLabel} - ${meta.charCount} chars`;
        container.appendChild(metaLine);

        const errorLine = document.createElement('div');
        errorLine.textContent = errorMessage || 'Model did not return valid JSON.';
        container.appendChild(errorLine);

        body.appendChild(container);
    }

    async function runFoundationTool(toolId) {
        const tool = AI_FOUNDATION_TOOLS[toolId];
        if (!tool) return;

        const agentId = state.agents.selectedId;
        if (!agentId) {
            notificationStore.warning('Select an agent first.', 'editor');
            return;
        }
        const agent = (state.agents.list || []).find(item => item.id === agentId);
        const status = state.agents.statusById ? state.agents.statusById[agentId] : null;
        if (status === 'unconfigured' || status === 'incomplete') {
            notificationStore.warning('Selected agent is not configured.', 'editor');
            return;
        }
        if (status === 'unreachable') {
            notificationStore.error('Selected agent endpoint is unreachable.', 'editor');
            return;
        }
        if (agent && Array.isArray(agent.tools) && agent.tools.length) {
            const toolConfig = agent.tools.find(t => t.id === toolId);
            if (toolConfig && toolConfig.enabled === false) {
                notificationStore.warning(`${tool.label} is disabled for this agent.`, 'editor');
                return;
            }
        }

        const scope = resolveFoundationScope();
        const input = getEditorInputForScope(scope);
        if (input.error) {
            notificationStore.warning(input.error, 'editor');
            return;
        }

        const payloadBase = { agentId, memoryId: undefined, skipToolCatalog: true };
        let parsed = null;
        let lastError = null;
        let lastResponseText = '';

        setFoundationButtonsEnabled(false);
        log(`AI Tool: ${tool.label} started`, 'info');

        const execute = async (prompt, label) => {
            const payload = { ...payloadBase, message: prompt };
            const runChat = () => api('/api/ai/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (typeof withAgentTurn === 'function') {
                return withAgentTurn(agentId, 'processing', runChat, label);
            }
            return runChat();
        };

        try {
            for (let attempt = 1; attempt <= 2; attempt += 1) {
                const prompt = buildFoundationPrompt(toolId, input, attempt);
                const response = await execute(prompt, `${tool.label} (attempt ${attempt})`);
                const responseText = response && response.content ? response.content : '';
                lastResponseText = responseText;
                parsed = extractJsonObject(responseText);
                if (parsed) {
                    break;
                }
            }
            if (!parsed) {
                lastError = 'Model did not return valid JSON.';
                throw new Error(lastError);
            }
            const normalized = normalizeToolPayload(toolId, parsed);
            showFoundationResultModal(toolId, normalized, input);
            notificationStore.success(`${tool.label} completed.`, 'editor');
            log(`AI Tool: ${tool.label} completed`, 'success');
        } catch (err) {
            const message = err.message || lastError || 'Unknown error';
            notificationStore.error(`${tool.label} failed: ${message}`, 'editor');
            log(`AI Tool: ${tool.label} failed (${message})`, 'error');
            if (lastResponseText) {
                showFoundationErrorModal(toolId, input, lastResponseText, message);
            }
        } finally {
            setFoundationButtonsEnabled(true);
        }
    }

    function initAIFoundation() {
        if (elements.btnAiSummarize) {
            elements.btnAiSummarize.addEventListener('click', () => runFoundationTool('summarize'));
        }
        if (elements.btnAiExplain) {
            elements.btnAiExplain.addEventListener('click', () => runFoundationTool('explain'));
        }
        if (elements.btnAiSuggest) {
            elements.btnAiSuggest.addEventListener('click', () => runFoundationTool('suggest'));
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

        if (elements.btnDevTools) {
            elements.btnDevTools.addEventListener('click', () => showDevToolsModal());
        }

        // Sidebar workbench buttons
        const btnSidebarIssues = document.getElementById('btn-sidebar-issues');
        if (btnSidebarIssues) {
            btnSidebarIssues.addEventListener('click', () => {
                openIssueBoardPanel();
            });
        }

        const btnSidebarAssistedMode = document.getElementById('btn-sidebar-assisted-mode');
        if (btnSidebarAssistedMode) {
            btnSidebarAssistedMode.addEventListener('click', () => {
                showAssistedModeModal();
            });
        }

        const btnSidebarWidgets = document.getElementById('btn-sidebar-widgets');
        if (btnSidebarWidgets) {
            btnSidebarWidgets.addEventListener('click', () => {
                showWidgetPicker();
            });
        }

        const btnSidebarPatchReview = document.getElementById('btn-sidebar-patch-review');
        if (btnSidebarPatchReview) {
            btnSidebarPatchReview.addEventListener('click', () => showPatchReviewModal());
        }

        if (elements.btnStartConference) {
            elements.btnStartConference.addEventListener('click', () => {
                ensureChiefOfStaff('Start conference', () => showConferenceInviteModal());
            });
        }

        if (elements.btnToggleExplorer) {
            elements.btnToggleExplorer.addEventListener('click', () => {
                const isVisible = elements.explorerPanel && !elements.explorerPanel.classList.contains('is-hidden');

                // If versioning panel is active and explorer is visible, switch to file tree
                if (isVisible && window.versioning && window.versioning.isActive()) {
                    window.versioning.showFileTreePanel();
                    // Update button states
                    const commitBtn = document.getElementById('btn-commit');
                    if (commitBtn) commitBtn.classList.remove('active');
                    elements.btnToggleExplorer.classList.add('is-active');
                    return;
                }

                setExplorerVisible(!isVisible);
            });
        }

        if (elements.btnWorkspaceSwitch) {
            elements.btnWorkspaceSwitch.addEventListener('click', () => {
                showWorkspaceSwitcher();
            });
        }

        document.getElementById('btn-open-workspace').addEventListener('click', async () => {
            log('Opening Project folder...', 'info');
            try {
                const result = await api('/api/workspace/open', { method: 'POST' });
                if (result.ok) {
                    log('Project folder opened', 'success');
                } else {
                    log(`Failed to open Project folder: ${result.error}`, 'error');
                }
            } catch (err) {
                log(`Failed to open Project folder: ${err.message}`, 'error');
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
            log('Opening terminal at Project...', 'info');
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

        if (elements.btnViewHistory) {
            elements.btnViewHistory.addEventListener('click', () => {
                if (!state.activeFile) {
                    log('No active file to view history', 'warning');
                    return;
                }
                if (window.showFileHistory) {
                    window.showFileHistory(state.activeFile);
                }
            });
        }

        // Find button - triggers Monaco find widget
        elements.btnFind.addEventListener('click', () => {
            if (state.editor && state.activeFile) {
                state.editor.trigger('keyboard', 'actions.find');
                log('Find in file (Ctrl+F)', 'info');
            } else {
                log('No file open to search', 'warning');
            }
        });

        // Search button - opens Project search
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

            // E2: Find in Project (Ctrl+Shift+F / Cmd+Shift+F)
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
        const excludeTopicsInput = document.getElementById('decay-exclude-topics');
        const excludeAgentsInput = document.getElementById('decay-exclude-agents');
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
            const filtered = res.filteredItems || 0;
            decayReport.innerHTML = `
                <div><strong>Decay ${dryRun ? '(dry run)' : ''} results:</strong></div>
                <div>Archived: ${archived.length}, Expired: ${expired.length}, Prunable R5: ${prunable.length}, Locked skipped: ${res.lockedItems || 0}, Filtered: ${filtered}</div>
                <div>Pruned events: ${res.prunedEvents || 0}</div>
                ${dryRun && prunable.length ? `<div>Prunable IDs:</div><ul>${prunable.slice(0, 10).map(id => `<li>${id}</li>`).join('')}${prunable.length > 10 ? '<li>...</li>' : ''}</ul>` : ''}
            `;
        };

        const parseListInput = (input) => {
            if (!input || !input.value) return [];
            return input.value
                .split(/[,\\n]/)
                .map(s => s.trim())
                .filter(Boolean);
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
                const notifySetting = typeof status.notifyOnRun === 'boolean'
                    ? status.notifyOnRun
                    : (status.settings && typeof status.settings.notifyOnRun === 'boolean' ? status.settings.notifyOnRun : undefined);
                if (notifyCheckbox && typeof notifySetting === 'boolean') {
                    notifyCheckbox.checked = notifySetting;
                }
                const dryRunSetting = typeof status.dryRun === 'boolean'
                    ? status.dryRun
                    : (status.settings && typeof status.settings.dryRun === 'boolean' ? status.settings.dryRun : undefined);
                if (dryRunCheckbox && typeof dryRunSetting === 'boolean') {
                    dryRunCheckbox.checked = dryRunSetting;
                }
                if (excludeTopicsInput && Array.isArray(status.excludeTopicKeys)) {
                    excludeTopicsInput.value = status.excludeTopicKeys.join(', ');
                }
                if (excludeAgentsInput && Array.isArray(status.excludeAgentIds)) {
                    excludeAgentsInput.value = status.excludeAgentIds.join(', ');
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
                const excludeTopicKeys = parseListInput(excludeTopicsInput);
                const excludeAgentIds = parseListInput(excludeAgentsInput);
                try {
                    const res = await memoryApi.decay({
                        archiveAfterDays: archiveDays,
                        expireAfterDays: expireDays,
                        pruneExpiredR5: pruneR5,
                        dryRun,
                        notifyOnRun,
                        excludeTopicKeys,
                        excludeAgentIds
                    });
                    const msg = `Decay ${dryRun ? '(dry run)' : ''} done. Archived: ${res.archived.length}, Expired: ${res.expired.length}, Pruned events: ${res.prunedEvents}, Locked skipped: ${res.lockedItems}, Filtered: ${res.filteredItems || 0}`;
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
                const excludeTopicKeys = parseListInput(excludeTopicsInput);
                const excludeAgentIds = parseListInput(excludeAgentsInput);
                try {
                    await memoryApi.saveDecayConfig({
                        intervalMinutes: interval,
                        archiveAfterDays: archiveDays,
                        expireAfterDays: expireDays,
                        pruneExpiredR5: pruneR5,
                        notifyOnRun,
                        dryRun: dryRunScheduled,
                        excludeTopicKeys,
                        excludeAgentIds
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
                const excludeTopicKeys = parseListInput(excludeTopicsInput);
                const excludeAgentIds = parseListInput(excludeAgentsInput);
                try {
                    const res = await memoryApi.downloadDecayReport({
                        archiveAfterDays: archiveDays,
                        expireAfterDays: expireDays,
                        pruneExpiredR5: pruneR5,
                        excludeTopicKeys,
                        excludeAgentIds
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

    function showDevToolsModal() {
        const { modal, body, confirmBtn, cancelBtn } = createModalShell(
            'Dev Tools',
            'Close',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: true }
        );

        modal.classList.add('dev-tools-modal');
        if (cancelBtn) {
            cancelBtn.remove();
        }
        if (confirmBtn) {
            confirmBtn.classList.remove('modal-btn-primary');
            confirmBtn.classList.add('modal-btn-secondary');
        }

        const intro = document.createElement('div');
        intro.className = 'modal-text';
        intro.textContent = 'Quick utilities and test hooks for development.';
        body.appendChild(intro);

        const status = document.createElement('div');
        status.className = 'dev-tools-status';
        status.textContent = 'Status: idle';
        body.appendChild(status);

        const setStatus = (text) => {
            status.textContent = text;
        };

        const createRow = (title, description, buttonLabel, handler) => {
            const row = document.createElement('div');
            row.className = 'dev-tools-row';

            const textWrap = document.createElement('div');
            const titleEl = document.createElement('div');
            titleEl.className = 'dev-tools-item-title';
            titleEl.textContent = title;
            const descEl = document.createElement('div');
            descEl.className = 'dev-tools-item-desc';
            descEl.textContent = description;
            textWrap.appendChild(titleEl);
            textWrap.appendChild(descEl);

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'modal-btn modal-btn-secondary';
            btn.textContent = buttonLabel;
            btn.addEventListener('click', () => handler(btn));

            row.appendChild(textWrap);
            row.appendChild(btn);
            return row;
        };

        const createToggleSwitch = (checked) => {
            const wrapper = document.createElement('label');
            wrapper.className = 'toggle-switch';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.checked = Boolean(checked);
            const slider = document.createElement('span');
            slider.className = 'toggle-slider';
            wrapper.appendChild(input);
            wrapper.appendChild(slider);
            return { wrapper, input };
        };

        const backendSection = document.createElement('div');
        backendSection.className = 'dev-tools-section';
        const backendTitle = document.createElement('div');
        backendTitle.className = 'dev-tools-section-title';
        backendTitle.textContent = 'Backend';
        backendSection.appendChild(backendTitle);

        backendSection.appendChild(createRow(
            'Backend smoke test',
            'Run a multi-endpoint smoke test (workspace, settings, agents, issues, credits, memory, versioning, patches, dashboard).',
            'Run',
            async (btn) => {
                btn.disabled = true;
                setStatus('Status: running backend smoke test...');
                try {
                    const checks = [
                        { label: 'workspace', run: () => workspaceApi.info() },
                        { label: 'settings.security', run: () => settingsApi.getSecurity() },
                        { label: 'settings.keys', run: () => settingsApi.listKeys() },
                        { label: 'agents', run: () => agentApi.list() },
                        { label: 'agent.endpoints', run: () => agentEndpointsApi.list() },
                        { label: 'notifications', run: () => notificationsApi.list() },
                        { label: 'issues', run: () => issueApi.list() },
                        { label: 'credits', run: () => creditApi.listEvents() },
                        { label: 'memory.decay', run: () => memoryApi.getDecayStatus() },
                        { label: 'dashboard.layout', run: () => fetch('/api/dashboard/layout').then(res => {
                            if (!res.ok) throw new Error(`HTTP ${res.status}`);
                            return res.json();
                        }) },
                        { label: 'versioning.snapshots', run: () => versioningApi.snapshots() },
                        { label: 'patches', run: () => patchApi.list() }
                    ];

                    const results = await Promise.allSettled(checks.map(check => check.run()));
                    const failures = [];
                    results.forEach((result, index) => {
                        if (result.status === 'rejected') {
                            const reason = result.reason && result.reason.message
                                ? result.reason.message
                                : String(result.reason || 'Unknown error');
                            failures.push(`${checks[index].label}: ${reason}`);
                        }
                    });

                    const time = new Date().toLocaleTimeString();
                    if (failures.length === 0) {
                        setStatus(`Status: backend smoke test OK (${time})`);
                        notificationStore.success('Backend smoke test succeeded.', 'editor');
                    } else {
                        const summary = `Failures: ${failures.length}/${checks.length}`;
                        setStatus(`Status: backend smoke test FAILED (${summary})`);
                        notificationStore.error(`Backend smoke test failed. ${summary}`, 'editor');
                        failures.slice(0, 6).forEach((entry) => log(`Smoke test failure: ${entry}`, 'error'));
                    }
                } catch (err) {
                    setStatus(`Status: backend smoke test failed (${err.message})`);
                    notificationStore.error(`Backend smoke test failed: ${err.message}`, 'editor');
                } finally {
                    btn.disabled = false;
                }
            }
        ));

        backendSection.appendChild(createRow(
            'Frontend init check',
            'Verify Monaco/Split globals and editor instance wiring.',
            'Run',
            async (btn) => {
                btn.disabled = true;
                setStatus('Status: running frontend init check...');
                try {
                    const checks = [
                        { label: 'Split', ok: typeof window.Split === 'function' },
                        { label: 'require', ok: typeof window.require === 'function' },
                        { label: 'monaco', ok: Boolean(window.monaco && window.monaco.editor) },
                        { label: 'monaco.editor', ok: Boolean(window.monaco && typeof window.monaco.editor.create === 'function') },
                        { label: 'editor.instance', ok: Boolean(state && state.editor && typeof state.editor.getModel === 'function') },
                        { label: 'editor.dom', ok: Boolean(elements && elements.monacoEditor) }
                    ];

                    const failures = checks.filter(check => !check.ok).map(check => check.label);
                    const time = new Date().toLocaleTimeString();
                    if (failures.length === 0) {
                        setStatus(`Status: frontend init check OK (${time})`);
                        notificationStore.success('Frontend init check succeeded.', 'editor');
                    } else {
                        setStatus(`Status: frontend init check FAILED (${failures.length}/${checks.length})`);
                        notificationStore.error(`Frontend init check failed: ${failures.join(', ')}`, 'editor');
                        failures.forEach(label => log(`Frontend init check failed: ${label}`, 'error'));
                    }
                } catch (err) {
                    setStatus(`Status: frontend init check failed (${err.message})`);
                    notificationStore.error(`Frontend init check failed: ${err.message}`, 'editor');
                } finally {
                    btn.disabled = false;
                }
            }
        ));

        const memorySection = document.createElement('div');
        memorySection.className = 'dev-tools-section';
        const memoryTitle = document.createElement('div');
        memoryTitle.className = 'dev-tools-section-title';
        memoryTitle.textContent = 'Memory';
        memorySection.appendChild(memoryTitle);

        const memoryIntro = document.createElement('div');
        memoryIntro.className = 'dev-tools-item-desc';
        memoryIntro.textContent = 'Create multi-level memory from a seed text using the Chief of Staff.';
        memorySection.appendChild(memoryIntro);

        const memoryRow = document.createElement('div');
        memoryRow.className = 'dev-tools-row dev-tools-row-stack';

        const memoryForm = document.createElement('div');
        memoryForm.className = 'dev-tools-controls';

        const seedAgentLabel = document.createElement('label');
        seedAgentLabel.className = 'modal-label';
        seedAgentLabel.textContent = 'Agent';
        const seedAgentSelect = document.createElement('select');
        seedAgentSelect.className = 'modal-select';

        const seedModelLabel = document.createElement('div');
        seedModelLabel.className = 'dev-tools-item-desc';
        seedModelLabel.textContent = 'Model: unset';

        const seedTagsLabel = document.createElement('label');
        seedTagsLabel.className = 'modal-label';
        seedTagsLabel.textContent = 'Tags (comma or newline)';
        const seedTagsInput = document.createElement('textarea');
        seedTagsInput.className = 'modal-input';
        seedTagsInput.rows = 2;
        seedTagsInput.placeholder = 'canon, chapter-6';

        const seedTextLabel = document.createElement('label');
        seedTextLabel.className = 'modal-label';
        seedTextLabel.textContent = 'Seed text';
        const seedTextInput = document.createElement('textarea');
        seedTextInput.className = 'modal-input';
        seedTextInput.rows = 6;
        seedTextInput.placeholder = 'Paste a short paragraph or two...';

        const seedActions = document.createElement('div');
        seedActions.className = 'dev-tools-controls';
        const seedCreateBtn = document.createElement('button');
        seedCreateBtn.type = 'button';
        seedCreateBtn.className = 'modal-btn modal-btn-primary';
        seedCreateBtn.textContent = 'Create 5-Level Memory';
        seedActions.appendChild(seedCreateBtn);
        const seedDownloadBtn = document.createElement('button');
        seedDownloadBtn.type = 'button';
        seedDownloadBtn.className = 'modal-btn modal-btn-secondary';
        seedDownloadBtn.textContent = 'Save memtest.json';
        seedActions.appendChild(seedDownloadBtn);

        const seedFeedback = document.createElement('div');
        seedFeedback.className = 'dev-tools-status';
        seedFeedback.textContent = '';
        const seedResult = document.createElement('div');
        seedResult.className = 'dev-tools-item-desc';
        seedResult.textContent = '';

        memoryForm.appendChild(seedAgentLabel);
        memoryForm.appendChild(seedAgentSelect);
        memoryForm.appendChild(seedModelLabel);
        memoryForm.appendChild(seedTagsLabel);
        memoryForm.appendChild(seedTagsInput);
        memoryForm.appendChild(seedTextLabel);
        memoryForm.appendChild(seedTextInput);
        memoryForm.appendChild(seedActions);
        memoryForm.appendChild(seedFeedback);
        memoryForm.appendChild(seedResult);
        memoryRow.appendChild(memoryForm);
        memorySection.appendChild(memoryRow);

        const parseListInput = (input) => {
            if (!input || !input.value) return [];
            return input.value
                .split(/[,\\n]/)
                .map(s => s.trim())
                .filter(Boolean);
        };

        const parseJsonFromText = (text) => {
            if (!text) return null;
            const cleaned = text.replace(/<think>[\s\S]*?<\/think>/gi, '');
            const match = cleaned.match(/\{[\s\S]*\}/);
            if (!match) return null;
            const raw = match[0].trim();
            try {
                const parsed = JSON.parse(raw);
                if (typeof parsed === 'string' && parsed.trim().startsWith('{')) {
                    return JSON.parse(parsed);
                }
                return parsed;
            } catch (_) {
                return null;
            }
        };

        const summarizeFallback = (text, sentenceCount = 2) => {
            if (!text) return '';
            const sentences = text
                .replace(/\\s+/g, ' ')
                .split(/(?<=[.!?])\\s+/)
                .filter(Boolean);
            return sentences.slice(0, sentenceCount).join(' ');
        };

        const normalizeLevels = (levels) => {
            if (!levels || typeof levels !== 'object') {
                return null;
            }
            const normalized = {};
            ['L4', 'L3', 'L2', 'L1'].forEach((key) => {
                const value = levels[key] || levels[key.toLowerCase()] || levels[key.toUpperCase()];
                if (typeof value === 'string' && value.trim()) {
                    normalized[key] = value.trim();
                }
            });
            return Object.keys(normalized).length ? normalized : null;
        };

        const stripEmbeddedLevels = (text) => {
            if (!text) return '';
            const marker = /\bL[1-4]\s*:/i;
            const match = marker.exec(text);
            if (!match) return text.trim();
            return text.slice(0, match.index).trim();
        };

        const clampSummary = (text, maxSentences) => {
            if (!text) return '';
            const sentences = text
                .replace(/\s+/g, ' ')
                .split(/(?<=[.!?])\s+/)
                .filter(Boolean);
            return sentences.slice(0, maxSentences).join(' ');
        };

        const makeSemanticTrace = (text, seedText) => {
            const base = text || summarizeFallback(seedText, 1);
            let cleaned = clampSummary(base, 1)
                .replace(/\[[^\]]+\]/g, '') // strip citation brackets
                .replace(/"[^"]*"/g, '') // drop quoted phrases
                .replace(/\([^)]*\)/g, '') // drop parentheticals
                .replace(/\d+([.,]\d+)?/g, '') // drop numbers/dates
                .replace(/\s+/g, ' ')
                .trim();
            if (!cleaned) {
                cleaned = summarizeFallback(seedText, 1);
            }
            const words = cleaned.split(' ').filter(Boolean);
            let trace = words.slice(0, 16).join(' ');
            trace = trace.replace(/\b(is|is a|is an|is the|a|an|the|of|to|in|for|with)$/i, '').trim();
            if (!trace) {
                trace = summarizeFallback(seedText, 1);
            }
            if (!/[.!?]$/.test(trace)) {
                trace = `${trace}.`;
            }
            return trace;
        };

        const enforceSummaryLevels = (levels, seedText) => {
            const base = levels || {};
            const fallback = {
                L4: summarizeFallback(seedText, 4),
                L3: summarizeFallback(seedText, 3),
                L2: summarizeFallback(seedText, 2),
                L1: summarizeFallback(seedText, 1)
            };
            const cleaned = {
                L4: stripEmbeddedLevels(base.L4 || ''),
                L3: stripEmbeddedLevels(base.L3 || ''),
                L2: stripEmbeddedLevels(base.L2 || ''),
                L1: stripEmbeddedLevels(base.L1 || '')
            };
            const clamped = {
                L4: clampSummary(cleaned.L4, 4),
                L3: clampSummary(cleaned.L3, 3),
                L2: clampSummary(cleaned.L2, 2),
                L1: makeSemanticTrace(cleaned.L1, seedText)
            };
            ['L4', 'L3', 'L2', 'L1'].forEach((key) => {
                if (!clamped[key]) {
                    clamped[key] = fallback[key];
                }
            });
            return clamped;
        };

        let lastSeedLevels = null;

        const downloadSeedJson = () => {
            if (!lastSeedLevels) {
                setSeedFeedback('No memory JSON available yet.', 'warning');
                return;
            }
            const payload = JSON.stringify(lastSeedLevels, null, 2);
            const blob = new Blob([payload], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'memtest.json';
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(url);
            setSeedFeedback('Saved memtest.json.', 'success');
        };

        const setSeedFeedback = (text, level = 'info') => {
            seedFeedback.textContent = text || '';
            seedFeedback.dataset.level = level;
        };

        const setSeedResult = (text, level = 'info') => {
            seedResult.textContent = text || '';
            seedResult.dataset.level = level;
        };

        const loadSeedAgents = async () => {
            if (!state.agents.list || state.agents.list.length === 0) {
                await window.loadAgents();
            }
            let endpoints = {};
            try {
                endpoints = await agentEndpointsApi.list();
            } catch (err) {
                endpoints = {};
            }
            seedAgentSelect.innerHTML = '';
            const agents = state.agents.list || [];
            const isAssistant = window.isAssistantAgent || (() => false);
            const sorted = agents.slice().sort((a, b) => {
                const aIsLead = isAssistant(a) ? 1 : 0;
                const bIsLead = isAssistant(b) ? 1 : 0;
                if (aIsLead !== bIsLead) return bIsLead - aIsLead;
                return (a.name || '').localeCompare(b.name || '');
            });
            sorted.forEach(agent => {
                const option = document.createElement('option');
                option.value = agent.id;
                const label = agent.name ? `${agent.name} (${agent.role || 'role'})` : agent.id;
                option.textContent = label;
                seedAgentSelect.appendChild(option);
            });
            const updateModelLabel = () => {
                const selectedId = seedAgentSelect.value;
                const agent = agents.find(item => item.id === selectedId);
                const endpoint = endpoints?.[selectedId] || agent?.endpoint || null;
                if (endpoint && endpoint.provider && endpoint.model) {
                    seedModelLabel.textContent = `Model: ${endpoint.provider} / ${endpoint.model}`;
                } else {
                    seedModelLabel.textContent = 'Model: unset';
                }
            };
            seedAgentSelect.addEventListener('change', updateModelLabel);
            updateModelLabel();
        };

        const createMemoryFromSeed = async () => {
            const seedText = seedTextInput.value.trim();
            if (!seedText) {
                setSeedFeedback('Seed text is required.', 'error');
                notificationStore.warning('Seed text is required.', 'workbench');
                return;
            }
            const agentId = seedAgentSelect.value;
            if (!agentId) {
                setSeedFeedback('Select an agent first.', 'error');
                notificationStore.warning('Select an agent first.', 'workbench');
                return;
            }

            setSeedFeedback('Generating memory levels...', 'info');
            setSeedResult('');
            seedCreateBtn.disabled = true;

            const tags = parseListInput(seedTagsInput);
            let levels = null;
            lastSeedLevels = null;

            try {
                const runSeedChat = async (payload, messageLabel) => {
                    if (agentId && typeof withAgentTurn === 'function') {
                        return withAgentTurn(agentId, 'processing', () => api('/api/ai/chat', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(payload)
                        }), messageLabel);
                    }
                    return api('/api/ai/chat', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(payload)
                    });
                };
                const prompt = [
                    'Return ONLY valid JSON. No markdown, no preface, no explanation.',
                    'Output schema: {"L4":"...","L3":"...","L2":"...","L1":"..."}',
                    'Each value must be a short string. Do not use bullets or lists.',
                    'L4: most detailed summary; keep full fidelity with key facts, names, numbers.',
                    'L3: concise summary; keep all important info, shorter than L4.',
                    'L2: aggressive summary, MAX 2 sentences.',
                    'L1: single sentence semantic trace (what it was about, no details).',
                    'Do not include quotes inside values unless escaped.',
                    '',
                    'Text:',
                    seedText
                ].join('\\n');

                const response = await runSeedChat({ message: prompt, agentId }, 'Generating memory summary');
                levels = normalizeLevels(parseJsonFromText(response && response.content ? response.content : ''));

                if (!levels) {
                    const retryPrompt = [
                        'Return ONLY JSON, nothing else.',
                        '{"L4":"...","L3":"...","L2":"...","L1":"..."}',
                        'L4 most detailed; L3 concise but complete; L2 max 2 sentences; L1 single sentence semantic trace.',
                        'Text:',
                        seedText
                    ].join('\\n');
                    const retry = await runSeedChat({ message: retryPrompt, agentId }, 'Retrying memory summary');
                    levels = normalizeLevels(parseJsonFromText(retry && retry.content ? retry.content : ''));
                }
            } catch (err) {
                levels = null;
            }

            const enforced = enforceSummaryLevels(levels, seedText);
            levels = enforced;

            try {
                lastSeedLevels = { L4: levels.L4, L3: levels.L3, L2: levels.L2, L1: levels.L1 };
                const memory = await memoryApi.create({
                    agentId,
                    topicKey: null,
                    defaultLevel: 3,
                    pinnedMinLevel: null,
                    tags
                });

                const versions = [
                    { level: 5, content: seedText, kind: 'source' },
                    { level: 4, content: levels.L4 || levels.l4 || '', kind: 'summarize' },
                    { level: 3, content: levels.L3 || levels.l3 || '', kind: 'summarize' },
                    { level: 2, content: levels.L2 || levels.l2 || '', kind: 'compress' },
                    { level: 1, content: levels.L1 || levels.l1 || '', kind: 'trace' }
                ].filter(entry => entry.content);

                const created = [];
                for (const entry of versions) {
                    const version = await memoryApi.addVersion(memory.id, {
                        repLevel: entry.level,
                        content: entry.content,
                        derivationKind: entry.kind
                    });
                    created.push(version.id || `${entry.level}`);
                }

                const detail = `Memory ${memory.id} created. Versions: ${created.join(', ')}`;
                setSeedFeedback('Memory created successfully.', 'success');
                setSeedResult(detail, 'success');
                notificationStore.success(detail, 'workbench');
                seedTextInput.value = '';
                downloadSeedJson();
            } catch (err) {
                setSeedFeedback(`Failed to create memory: ${err.message}`, 'error');
                notificationStore.error(`Failed to create memory: ${err.message}`, 'workbench');
            } finally {
                seedCreateBtn.disabled = false;
            }
        };

        seedCreateBtn.addEventListener('click', createMemoryFromSeed);
        seedDownloadBtn.addEventListener('click', downloadSeedJson);
        void loadSeedAgents();

        const localSection = document.createElement('div');
        localSection.className = 'dev-tools-section';
        const localTitle = document.createElement('div');
        localTitle.className = 'dev-tools-section-title';
        localTitle.textContent = 'Workbench';
        localSection.appendChild(localTitle);

        localSection.appendChild(createRow(
            'Reload agents',
            'Fetch the agent list from disk and refresh the roster.',
            'Reload',
            async (btn) => {
                btn.disabled = true;
                setStatus('Status: reloading agents...');
                try {
                    await loadAgents();
                    setStatus('Status: agents reloaded');
                    notificationStore.success('Agents reloaded.', 'workbench');
                } catch (err) {
                    setStatus(`Status: reload failed (${err.message})`);
                    notificationStore.error(`Failed to reload agents: ${err.message}`, 'workbench');
                } finally {
                    btn.disabled = false;
                }
            }
        ));

        localSection.appendChild(createRow(
            'Clear console',
            'Remove all console log entries.',
            'Clear',
            (btn) => {
                btn.disabled = true;
                clearLogs();
                renderConsole();
                setStatus('Status: console cleared');
                btn.disabled = false;
            }
        ));

        localSection.appendChild(createRow(
            'Seed credits leaderboard',
            'Create random credit events for all agents.',
            'Seed',
            async (btn) => {
                btn.disabled = true;
                setStatus('Status: seeding credits...');
                const agents = state.agents.list || [];

                if (agents.length === 0) {
                    setStatus('Status: no agents to seed');
                    notificationStore.warning('No agents found to seed credits.', 'workbench');
                    btn.disabled = false;
                    return;
                }

                // Generate random credits for each agent (1-3 events per agent)
                let created = 0;
                for (const agent of agents) {
                    const eventCount = Math.floor(Math.random() * 3) + 1; // 1-3 events
                    for (let i = 0; i < eventCount; i++) {
                        const amount = Math.floor(Math.random() * 25) + 1; // 1-25 credits per event
                        try {
                            await creditApi.createEvent({
                                agentId: agent.id,
                                amount,
                                reason: 'moderator-commendation',
                                verifiedBy: 'system',
                                timestamp: Date.now()
                            });
                            created += 1;
                        } catch (err) {
                            log(`Failed to seed credit for ${agent.name}: ${err.message}`, 'warning');
                        }
                    }
                }

                if (created > 0) {
                    renderWidgetDashboard();
                    setStatus(`Status: seeded ${created} credit events for ${agents.length} agents`);
                    notificationStore.success(`Seeded ${created} credits across ${agents.length} agents.`, 'workbench');
                } else {
                    setStatus('Status: failed to seed credits');
                }
                btn.disabled = false;
            }
        ));

        localSection.appendChild(createRow(
            'Seed credit comment',
            'Post a comment with a credit action on an issue.',
            'Post',
            async (btn) => {
                btn.disabled = true;
                setStatus('Status: posting credit comment...');

                const issueIdRaw = prompt('Issue ID to credit (e.g., 14):');
                const issueIdValue = issueIdRaw ? parseInt(String(issueIdRaw).replace(/[^0-9]/g, ''), 10) : NaN;
                if (!issueIdRaw || Number.isNaN(issueIdValue)) {
                    setStatus('Status: credit comment canceled');
                    notificationStore.warning('Invalid issue ID format. Use a number like 14.', 'workbench');
                    btn.disabled = false;
                    return;
                }

                const reason = prompt('Credit reason (e.g., evidence-verified):', 'evidence-verified');
                if (!reason) {
                    setStatus('Status: credit comment canceled');
                    btn.disabled = false;
                    return;
                }

                const author = prompt('Author (agent name or id):', 'system') || 'system';
                const body = `Credit hook: ${reason}`;
                let details = 'Dev Tools credit hook';
                const reasonKey = reason.trim().toLowerCase();
                if (reasonKey.startsWith('evidence-')) {
                    const outcome = reasonKey === 'evidence-outcome-upgrade' ? 'resolved-issue' : 'no-action-yet';
                    details = `trigger: manual; outcome: ${outcome}; note: Dev Tools credit hook`;
                }

                try {
                    await issueApi.addComment(issueIdValue, {
                        author,
                        body,
                        action: {
                            type: reason,
                            details
                        }
                    });
                    await refreshIssueModal(issueIdValue);
                    setStatus(`Status: posted credit comment to Issue #${issueIdValue}`);
                    notificationStore.success('Credit comment posted.', 'workbench');
                } catch (err) {
                    setStatus(`Status: credit comment failed (${err.message})`);
                    notificationStore.error(`Failed to post credit comment: ${err.message}`, 'workbench');
                } finally {
                    btn.disabled = false;
                }
            }
        ));

        const assistedSection = document.createElement('div');
        assistedSection.className = 'dev-tools-section';
        const assistedTitle = document.createElement('div');
        assistedTitle.className = 'dev-tools-section-title';
        assistedTitle.textContent = 'Assisted Mode';
        assistedSection.appendChild(assistedTitle);

        const assistedIntro = document.createElement('div');
        assistedIntro.className = 'modal-text';
        assistedIntro.textContent = 'Toggle per-agent assisted mode and set a reason. Badges update in the roster.';
        assistedSection.appendChild(assistedIntro);

        const assistedList = document.createElement('div');
        assistedList.className = 'dev-tools-list';
        assistedList.textContent = 'Loading agents...';
        assistedSection.appendChild(assistedList);

        const plannerSection = document.createElement('div');
        plannerSection.className = 'dev-tools-section';
        const plannerTitle = document.createElement('div');
        plannerTitle.className = 'dev-tools-section-title';
        plannerTitle.textContent = 'Planner Status Tags';
        plannerSection.appendChild(plannerTitle);

        const plannerIntro = document.createElement('div');
        plannerIntro.className = 'modal-text';
        plannerIntro.textContent = 'Apply a single roadmap status tag (Idea → Plan → Draft → Polished) to an issue.';
        plannerSection.appendChild(plannerIntro);

        const plannerRow = document.createElement('div');
        plannerRow.className = 'dev-tools-row dev-tools-row-stack';

        const plannerFields = document.createElement('div');
        plannerFields.className = 'dev-tools-fields';

        const issueInput = document.createElement('input');
        issueInput.type = 'number';
        issueInput.min = '1';
        issueInput.placeholder = 'Issue ID';
        issueInput.className = 'modal-input dev-tools-input';

        const statusSelect = document.createElement('select');
        statusSelect.className = 'modal-select dev-tools-select';
        [
            { value: '', label: 'Select status tag' },
            { value: 'Idea', label: 'Idea' },
            { value: 'Plan', label: 'Plan' },
            { value: 'Draft', label: 'Draft' },
            { value: 'Polished', label: 'Polished' },
            { value: 'none', label: 'Clear status tag' }
        ].forEach(({ value, label }) => {
            const option = document.createElement('option');
            option.value = value;
            option.textContent = label;
            statusSelect.appendChild(option);
        });

        plannerFields.appendChild(issueInput);
        plannerFields.appendChild(statusSelect);
        plannerRow.appendChild(plannerFields);

        const plannerBtn = document.createElement('button');
        plannerBtn.type = 'button';
        plannerBtn.className = 'modal-btn modal-btn-secondary';
        plannerBtn.textContent = 'Apply';

        plannerBtn.addEventListener('click', async () => {
            const issueId = parseInt(issueInput.value, 10);
            if (!issueId) {
                setStatus('Status: enter a valid issue ID');
                return;
            }
            const selected = statusSelect.value;
            if (!selected) {
                setStatus('Status: select a status tag');
                return;
            }

            const roadmapTags = new Set(['idea', 'plan', 'draft', 'polished']);
            const isRoadmapTag = (tag) => roadmapTags.has(String(tag || '').toLowerCase());

            plannerBtn.disabled = true;
            setStatus('Status: updating issue tags...');
            try {
                const issue = await issueApi.get(issueId);
                const currentTags = Array.isArray(issue.tags) ? issue.tags : [];
                const filtered = currentTags.filter(tag => !isRoadmapTag(tag));
                const nextTags = selected === 'none' ? filtered : [...filtered, selected];
                await issueApi.update(issueId, { tags: nextTags });
                await loadIssues();
                setStatus(`Status: updated Issue #${issueId}`);
                notificationStore.success(`Updated Issue #${issueId} status tag.`, 'workbench');
            } catch (err) {
                setStatus(`Status: tag update failed (${err.message})`);
                notificationStore.error(`Failed to update tags: ${err.message}`, 'workbench');
            } finally {
                plannerBtn.disabled = false;
            }
        });

        plannerRow.appendChild(plannerBtn);
        plannerSection.appendChild(plannerRow);

        const renderAssistedList = async () => {
            const agents = state.agents.list || [];
            if (agents.length === 0) {
                assistedList.textContent = 'No agents available.';
                return;
            }

            let endpoints = {};
            try {
                endpoints = await agentEndpointsApi.list();
            } catch (err) {
                log(`Failed to load agent endpoints: ${err.message}`, 'warning');
            }

            assistedList.innerHTML = '';

            agents.forEach(agent => {
                const isAssistant = isAssistantAgent(agent);
                const endpoint = endpoints?.[agent.id] || agent.endpoint || null;
                const modelLabel = endpoint?.model ? `Model: ${endpoint.model}` : 'Model: unset';

                const row = document.createElement('div');
                row.className = 'dev-tools-row dev-tools-row-stack';

                const textWrap = document.createElement('div');
                const titleEl = document.createElement('div');
                titleEl.className = 'dev-tools-item-title';
                titleEl.textContent = agent.name || agent.id || 'Agent';
                const descEl = document.createElement('div');
                descEl.className = 'dev-tools-item-desc';
                descEl.textContent = `${agent.role || 'role'} · ${modelLabel}`;
                textWrap.appendChild(titleEl);
                textWrap.appendChild(descEl);

                const controls = document.createElement('div');
                controls.className = 'dev-tools-controls';

                const { wrapper, input } = createToggleSwitch(agent.assisted);
                if (isAssistant) {
                    input.disabled = true;
                    wrapper.title = 'Assistant cannot be assisted.';
                }

                const reasonSelect = document.createElement('select');
                reasonSelect.className = 'modal-select dev-tools-select';
                [
                    { value: 'manual', label: 'Manual' },
                    { value: 'scope-exceeded', label: 'Scope exceeded' },
                    { value: 'uncertainty', label: 'Uncertainty' },
                    { value: 'no-progress', label: 'No progress' },
                    { value: 'hysteria', label: 'Hysteria' }
                ].forEach(({ value, label }) => {
                    const option = document.createElement('option');
                    option.value = value;
                    option.textContent = label;
                    reasonSelect.appendChild(option);
                });
                reasonSelect.value = agent.assistedReason || 'manual';
                reasonSelect.disabled = !agent.assisted || isAssistant;

                const queueInput = document.createElement('input');
                queueInput.type = 'number';
                queueInput.min = '0';
                queueInput.className = 'modal-input dev-tools-input';
                queueInput.placeholder = 'Queue';
                queueInput.value = agent.assistedQueueSize ?? '';
                queueInput.disabled = !agent.assisted || isAssistant;

                const dosageInput = document.createElement('input');
                dosageInput.type = 'number';
                dosageInput.min = '1';
                dosageInput.className = 'modal-input dev-tools-input';
                dosageInput.placeholder = 'Dosage';
                dosageInput.value = agent.assistedTaskDosage ?? '';
                dosageInput.disabled = !agent.assisted || isAssistant;

                const noteInput = document.createElement('input');
                noteInput.type = 'text';
                noteInput.className = 'modal-input dev-tools-input';
                noteInput.placeholder = 'Assist note';
                noteInput.value = agent.assistedNotes || '';
                noteInput.disabled = !agent.assisted || isAssistant;

                const applyAssistedState = async () => {
                    if (isAssistant) {
                        notificationStore.warning('Assistant cannot be set to assisted mode.', 'workbench');
                        input.checked = false;
                        return;
                    }
                    const assisted = input.checked;
                    const reason = reasonSelect.value;
                    reasonSelect.disabled = !assisted;
                    input.disabled = true;
                    reasonSelect.disabled = true;
                    queueInput.disabled = true;
                    dosageInput.disabled = true;
                    noteInput.disabled = true;

                    const queueValue = queueInput.value.trim();
                    const dosageValue = dosageInput.value.trim();

                    const payload = {
                        assisted,
                        assistedReason: assisted ? reason : null,
                        assistedSince: assisted ? Date.now() : null,
                        assistedModel: assisted ? (endpoint?.model || null) : null,
                        assistedQueueSize: assisted && queueValue !== '' ? parseInt(queueValue, 10) : null,
                        assistedTaskDosage: assisted && dosageValue !== '' ? parseInt(dosageValue, 10) : null,
                        assistedNotes: assisted ? noteInput.value.trim() : null
                    };

                    try {
                        await agentApi.update(agent.id, payload);
                        agent.assisted = assisted;
                        agent.assistedReason = payload.assistedReason;
                        agent.assistedSince = payload.assistedSince;
                        agent.assistedModel = payload.assistedModel;
                        agent.assistedQueueSize = payload.assistedQueueSize;
                        agent.assistedTaskDosage = payload.assistedTaskDosage;
                        agent.assistedNotes = payload.assistedNotes;
                        setStatus(`Status: updated assisted mode for ${agent.name}`);
                        renderAgentSidebar();
                    } catch (err) {
                        setStatus(`Status: assisted update failed (${err.message})`);
                        notificationStore.error(`Failed to update assisted mode: ${err.message}`, 'workbench');
                        input.checked = Boolean(agent.assisted);
                    } finally {
                        input.disabled = false;
                        reasonSelect.disabled = !input.checked || isAssistant;
                        queueInput.disabled = !input.checked || isAssistant;
                        dosageInput.disabled = !input.checked || isAssistant;
                        noteInput.disabled = !input.checked || isAssistant;
                    }
                };

                input.addEventListener('change', applyAssistedState);
                reasonSelect.addEventListener('change', () => {
                    if (input.checked) {
                        applyAssistedState();
                    }
                });
                [queueInput, dosageInput, noteInput].forEach((field) => {
                    field.addEventListener('change', () => {
                        if (input.checked) {
                            applyAssistedState();
                        }
                    });
                });

                controls.appendChild(wrapper);
                controls.appendChild(reasonSelect);
                controls.appendChild(queueInput);
                controls.appendChild(dosageInput);
                controls.appendChild(noteInput);
                row.appendChild(textWrap);
                row.appendChild(controls);
                assistedList.appendChild(row);
            });
        };

        body.appendChild(backendSection);
        body.appendChild(memorySection);
        body.appendChild(localSection);
        body.appendChild(assistedSection);
        body.appendChild(plannerSection);
        const isDevMode = state.workspace && state.workspace.devMode;
        if (isDevMode) {
            const patchSection = document.createElement('div');
            patchSection.className = 'dev-tools-section';
            const patchTitle = document.createElement('div');
            patchTitle.className = 'dev-tools-section-title';
            patchTitle.textContent = 'PATCH REVIEW';
            patchSection.appendChild(patchTitle);

            patchSection.appendChild(createRow(
                'Create test patch',
                'Creates a simulated patch for end-to-end patch review testing.',
                'Run',
                async (btn) => {
                    btn.disabled = true;
                    setStatus('Status: creating test patch...');
                    try {
                        const created = await patchApi.simulate('README.md');
                        setStatus(`Status: created test patch ${created.id}`);
                        notificationStore.success(`Created test patch: ${created.id}`, 'editor');
                    } catch (err) {
                        setStatus(`Status: test patch failed (${err.message})`);
                        notificationStore.error(`Test patch failed: ${err.message}`, 'editor');
                    } finally {
                        btn.disabled = false;
                    }
                }
            ));

            patchSection.appendChild(createRow(
                'Clean applied/rejected',
                'Remove patches that have already been applied or rejected.',
                'Run',
                async (btn) => {
                    btn.disabled = true;
                    setStatus('Status: cleaning applied/rejected patches...');
                    try {
                        await patchApi.cleanup();
                        setStatus('Status: cleaned applied/rejected patches');
                        notificationStore.success('Removed applied/rejected patches.', 'editor');
                    } catch (err) {
                        setStatus(`Status: cleanup failed (${err.message})`);
                        notificationStore.error(`Cleanup failed: ${err.message}`, 'editor');
                    } finally {
                        btn.disabled = false;
                    }
                }
            ));

            body.appendChild(patchSection);
        }

        renderAssistedList();
    }

    function initPromptToolsControls() {
        const select = document.getElementById('prompt-tool-select');
        if (!select || !window.promptToolsApi) return;

        const fields = {
            name: document.getElementById('prompt-tool-name'),
            archetype: document.getElementById('prompt-tool-archetype'),
            scope: document.getElementById('prompt-tool-scope'),
            usage: document.getElementById('prompt-tool-usage'),
            goals: document.getElementById('prompt-tool-goals'),
            guardrails: document.getElementById('prompt-tool-guardrails'),
            prompt: document.getElementById('prompt-tool-prompt')
        };
        const statusEl = document.getElementById('prompt-tool-status');
        const newBtn = document.getElementById('prompt-tool-new');
        const deleteBtn = document.getElementById('prompt-tool-delete');
        const saveBtn = document.getElementById('prompt-tool-save');
        const cancelBtn = document.getElementById('prompt-tool-cancel');

        let promptList = [];
        let currentId = null;

        const setStatus = (text, level = 'info') => {
            if (!statusEl) return;
            statusEl.textContent = text || '';
            statusEl.dataset.level = level;
        };

        const clearForm = () => {
            if (fields.name) fields.name.value = '';
            if (fields.archetype) fields.archetype.value = '';
            if (fields.scope) fields.scope.value = '';
            if (fields.usage) fields.usage.value = '';
            if (fields.goals) fields.goals.value = '';
            if (fields.guardrails) fields.guardrails.value = '';
            if (fields.prompt) fields.prompt.value = '';
        };

        const loadForm = (prompt) => {
            if (!prompt) {
                clearForm();
                return;
            }
            if (fields.name) fields.name.value = prompt.name || '';
            if (fields.archetype) fields.archetype.value = prompt.archetype || '';
            if (fields.scope) fields.scope.value = prompt.scope || '';
            if (fields.usage) fields.usage.value = prompt.usageNotes || '';
            if (fields.goals) fields.goals.value = prompt.goals || '';
            if (fields.guardrails) fields.guardrails.value = prompt.guardrails || '';
            if (fields.prompt) fields.prompt.value = prompt.prompt || '';
        };

        const buildPayload = () => ({
            id: currentId,
            name: fields.name ? fields.name.value.trim() : '',
            archetype: fields.archetype ? fields.archetype.value : '',
            scope: fields.scope ? fields.scope.value : '',
            usageNotes: fields.usage ? fields.usage.value.trim() : '',
            goals: fields.goals ? fields.goals.value.trim() : '',
            guardrails: fields.guardrails ? fields.guardrails.value.trim() : '',
            prompt: fields.prompt ? fields.prompt.value.trim() : ''
        });

        const renderSelect = () => {
            select.innerHTML = '';
            const placeholder = document.createElement('option');
            placeholder.value = '';
            placeholder.textContent = promptList.length ? 'Select a prompt' : 'No prompts yet';
            select.appendChild(placeholder);
            promptList.forEach(prompt => {
                const option = document.createElement('option');
                option.value = prompt.id;
                option.textContent = prompt.name || prompt.id;
                select.appendChild(option);
            });
        };

        const loadPrompts = async (selectId) => {
            try {
                promptList = await promptToolsApi.list();
            } catch (err) {
                promptList = [];
                setStatus(`Failed to load prompts: ${err.message}`, 'error');
            }
            renderSelect();
            if (selectId) {
                select.value = selectId;
            }
            const active = promptList.find(item => item.id === select.value);
            currentId = active ? active.id : null;
            loadForm(active);
        };

        select.addEventListener('change', () => {
            const selected = promptList.find(item => item.id === select.value);
            currentId = selected ? selected.id : null;
            loadForm(selected);
            setStatus('');
        });

        if (newBtn) {
            newBtn.addEventListener('click', () => {
                currentId = null;
                select.value = '';
                clearForm();
                setStatus('New prompt ready.', 'info');
            });
        }

        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => {
                const current = promptList.find(item => item.id === currentId);
                loadForm(current);
                setStatus('Changes reverted.', 'info');
            });
        }

        if (deleteBtn) {
            deleteBtn.addEventListener('click', async () => {
                if (!currentId) {
                    setStatus('Select a prompt to delete.', 'warning');
                    return;
                }
                if (!confirm('Delete this prompt?')) {
                    return;
                }
                try {
                    await promptToolsApi.delete(currentId);
                    setStatus('Prompt deleted.', 'success');
                    await loadPrompts();
                } catch (err) {
                    setStatus(`Delete failed: ${err.message}`, 'error');
                }
            });
        }

        if (saveBtn) {
            saveBtn.addEventListener('click', async () => {
                const payload = buildPayload();
                if (!payload.name) {
                    setStatus('Prompt name is required.', 'warning');
                    return;
                }
                if (!payload.prompt) {
                    setStatus('Prompt text is required.', 'warning');
                    return;
                }
                try {
                    const saved = currentId
                        ? await promptToolsApi.update(currentId, payload)
                        : await promptToolsApi.create(payload);
                    currentId = saved.id;
                    setStatus('Prompt saved.', 'success');
                    await loadPrompts(saved.id);
                } catch (err) {
                    setStatus(`Save failed: ${err.message}`, 'error');
                }
            });
        }

        void loadPrompts();
    }

    window.dispatchNotificationAction = dispatchNotificationAction;
    window.isWorkbenchView = isWorkbenchView;
    window.showIssueCreateModal = showIssueCreateModal;
    window.loadIssues = loadIssues;
    window.log = log;
    window.loadAgents = loadAgents;
    window.loadWorkspaceInfo = loadWorkspaceInfo;
    window.initConsoleTabs = initConsoleTabs;
    window.initAIActions = initAIActions;
    window.initAIFoundation = initAIFoundation;
    window.initSidebarButtons = initSidebarButtons;
    window.initEventListeners = initEventListeners;
    window.initMemoryModeratorControls = initMemoryModeratorControls;
    window.initPromptToolsControls = initPromptToolsControls;
    window.initNotifications = initNotifications;
    window.setViewMode = setViewMode;
    window.addChatMessage = addChatMessage;
    window.isEndpointWired = isEndpointWired;
    window.PROVIDERS_REQUIRE_KEY = PROVIDERS_REQUIRE_KEY;
    window.LOCAL_PROVIDERS = LOCAL_PROVIDERS;
    window.loadAgentStatuses = loadAgentStatuses;
    window.setSelectedAgentId = setSelectedAgentId;

})();
