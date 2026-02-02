// Agents module (refactor split)
(function() {
    'use strict';

    const createModalShell = window.modals ? window.modals.createModalShell : null;
    const escapeHtml = window.modals ? window.modals.escapeHtml : null;
    const canonicalizeRole = window.canonicalizeRole;
    const buildChatPrompt = window.buildChatPrompt;
    const extractStopHook = window.extractStopHook;
    const ROLE_ENDPOINT_PRESETS = window.ROLE_ENDPOINT_PRESETS || {};

    function isAssistantAgent(agent) {
        if (!agent || agent.enabled === false) {
            return false;
        }
        const roleKey = canonicalizeRole(agent.role);
        if (roleKey === 'assistant' || roleKey === 'chief of staff') {
            return true;
        }
        return agent.canBeTeamLead === true;
    }

    function normalizeRoleForPreset(role) {
        const roleKey = canonicalizeRole ? canonicalizeRole(role) : null;
        if (!roleKey) {
            return null;
        }
        if (roleKey === 'chief of staff') {
            return 'assistant';
        }
        return roleKey;
    }

    function getRoleEndpointPreset(role) {
        const roleKey = normalizeRoleForPreset(role);
        if (!roleKey) {
            return ROLE_ENDPOINT_PRESETS.default || null;
        }
        return ROLE_ENDPOINT_PRESETS[roleKey] || ROLE_ENDPOINT_PRESETS.default || null;
    }

    function hasSamplingValues(target) {
        if (!target) {
            return false;
        }
        const fields = ['temperature', 'topP', 'topK', 'minP', 'repeatPenalty'];
        return fields.some(field => {
            const value = target[field];
            return value !== null && value !== undefined && value !== '';
        });
    }

    function applyRoleEndpointPreset(target, role, options = {}) {
        if (!target || target.useProviderDefaults) {
            return false;
        }
        const preset = getRoleEndpointPreset(role);
        if (!preset) {
            return false;
        }
        const force = options.force === true;
        if (!force && hasSamplingValues(target)) {
            return false;
        }
        target.temperature = preset.temperature ?? target.temperature ?? '';
        target.topP = preset.topP ?? target.topP ?? '';
        target.topK = preset.topK ?? target.topK ?? '';
        target.minP = preset.minP ?? target.minP ?? '';
        target.repeatPenalty = preset.repeatPenalty ?? target.repeatPenalty ?? '';
        return true;
    }

    async function applyRoleEndpointPresetForAgent(agent, role) {
        if (!agent || !agent.id) {
            return;
        }
        let endpoint = null;
        try {
            endpoint = await agentEndpointsApi.get(agent.id);
        } catch (_) {
            endpoint = null;
        }
        if (!endpoint && agent.endpoint) {
            endpoint = { ...agent.endpoint };
        }
        if (!endpoint || endpoint.useProviderDefaults) {
            return;
        }
        const applied = applyRoleEndpointPreset(endpoint, role, { force: true });
        if (!applied) {
            return;
        }
        await agentEndpointsApi.save(agent.id, endpoint);
        await window.loadAgentStatuses();
    }

    function resolveAgentIdFromLabel(label) {
        if (!label) {
            return null;
        }
        const target = String(label).trim().toLowerCase();
        if (!target) {
            return null;
        }
        const agents = state.agents.list || [];
        const byId = agents.find(agent => (agent.id || '').toLowerCase() === target);
        if (byId) {
            return byId.id;
        }
        const byName = agents.find(agent => (agent.name || '').trim().toLowerCase() === target);
        return byName ? byName.id : null;
    }

    function getAgentActivityState(agent) {
        const fallback = agent && agent.activityState ? String(agent.activityState).toLowerCase() : 'idle';
        const override = agent && agent.id ? state.agents.activityById[agent.id] : null;
        const activity = override || fallback;
        const normalized = activity ? String(activity).toLowerCase() : 'idle';
        if (normalized === 'reading' || normalized === 'processing' || normalized === 'executing') {
            return normalized;
        }
        return 'idle';
    }

    function getAgentActivityMessage(agent) {
        if (!agent || !agent.id) {
            return '';
        }
        return state.agents.activityMessageById[agent.id] || '';
    }

    function setAgentActivityState(agentId, activity, message) {
        if (!agentId) {
            return;
        }
        if (!activity || activity === 'idle') {
            delete state.agents.activityById[agentId];
            delete state.agents.activityMessageById[agentId];
        } else {
            state.agents.activityById[agentId] = activity;
            if (message) {
                state.agents.activityMessageById[agentId] = message;
            } else {
                delete state.agents.activityMessageById[agentId];
            }
        }
        renderAgentSidebar();
    }

    function withAgentActivity(agentId, activity, work, message) {
        setAgentActivityState(agentId, activity, message);
        const finalize = () => setAgentActivityState(agentId, 'idle');
        try {
            const result = typeof work === 'function' ? work() : null;
            if (result && typeof result.finally === 'function') {
                return result.finally(finalize);
            }
            finalize();
            return result;
        } catch (err) {
            finalize();
            throw err;
        }
    }

    const agentTurnQueue = [];
    let agentTurnActive = false;
    const agentTurnQueuedCount = new Map();

    function updateQueuedCount(agentId, delta) {
        if (!agentId) {
            return;
        }
        const current = agentTurnQueuedCount.get(agentId) || 0;
        const next = current + delta;
        if (next <= 0) {
            agentTurnQueuedCount.delete(agentId);
        } else {
            agentTurnQueuedCount.set(agentId, next);
        }
        renderAgentSidebar();
    }

    function getQueuedCount(agentId) {
        return agentId ? (agentTurnQueuedCount.get(agentId) || 0) : 0;
    }

    function runNextAgentTurn() {
        if (agentTurnActive) {
            return;
        }
        const next = agentTurnQueue.shift();
        if (!next) {
            return;
        }
        updateQueuedCount(next.agentId, -1);
        agentTurnActive = true;
        Promise.resolve()
            .then(next.work)
            .then(next.resolve, next.reject)
            .finally(() => {
                agentTurnActive = false;
                runNextAgentTurn();
            });
    }

    function enqueueAgentTurn(work, agentId) {
        return new Promise((resolve, reject) => {
            if (agentTurnActive || agentTurnQueue.length > 0) {
                updateQueuedCount(agentId, 1);
            }
            agentTurnQueue.push({ work, resolve, reject, agentId });
            runNextAgentTurn();
        });
    }

    function withAgentTurn(agentId, activity, work, message) {
        if (!agentId) {
            return typeof work === 'function' ? work() : null;
        }
        return enqueueAgentTurn(() => withAgentActivity(agentId, activity, work, message), agentId);
    }

    function getAgentSupervisionState(agent) {
        const state = agent && agent.supervisionState ? String(agent.supervisionState).toLowerCase() : 'none';
        if (state === 'assisted' || state === 'watched') {
            return state;
        }
        if (agent && agent.assisted) {
            return 'assisted';
        }
        return 'none';
    }

    function countAssistantAgents() {
        return (state.agents.list || []).filter(agent => isAssistantAgent(agent)).length;
    }

    function hasExactlyOneAssistant() {
        return countAssistantAgents() === 1;
    }

    function updateAgentLockState() {
        const prepLocked = window.canAddAgents ? !window.canAddAgents() : false;
        state.agents.prepLocked = prepLocked;
        state.agents.locked = prepLocked || !hasExactlyOneAssistant();
    }

    function showPreparationRequiredModal(actionLabel) {
        const { body, confirmBtn } = createModalShell(
            'Project Preparation Required',
            'Prepare Project',
            'Close',
            { closeOnCancel: true, closeOnConfirm: true }
        );
        const text = document.createElement('div');
        text.className = 'modal-text';
        text.textContent = `${actionLabel || 'This action'} is locked until project preparation is completed.`;
        body.appendChild(text);
        confirmBtn.addEventListener('click', () => {
            if (window.showProjectPreparationWizard) {
                window.showProjectPreparationWizard();
            }
        });
    }

    function showChiefRequiredModal(actionLabel) {
        const { body, confirmBtn, cancelBtn } = createModalShell(
            'Chief of Staff Required',
            'Create Chief of Staff',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: true }
        );

        const text = document.createElement('div');
        text.className = 'modal-text';
        text.textContent = `Agents are disabled without a Chief of Staff. ${actionLabel || 'This action'} can't run until one exists.`;
        body.appendChild(text);

        const hint = document.createElement('div');
        hint.className = 'modal-hint';
        hint.textContent = 'Create a Chief of Staff now, or cancel to keep agents locked.';
        body.appendChild(hint);

        confirmBtn.addEventListener('click', () => {
            showAddAgentWizard();
        });

        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => {});
        }
    }

    function showMultipleChiefsModal(actionLabel) {
        const { body, confirmBtn, cancelBtn } = createModalShell(
            'Multiple Chiefs of Staff',
            'Close',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: true }
        );

        if (cancelBtn) {
            cancelBtn.remove();
        }
        if (confirmBtn) {
            confirmBtn.classList.remove('modal-btn-primary');
            confirmBtn.classList.add('modal-btn-secondary');
        }

        const text = document.createElement('div');
        text.className = 'modal-text';
        text.textContent = `Team Mode requires exactly one Chief of Staff. ${actionLabel || 'This action'} can't run until extra Chiefs are disabled.`;
        body.appendChild(text);

        const hint = document.createElement('div');
        hint.className = 'modal-hint';
        hint.textContent = 'Disable or retire extra Chiefs of Staff to unlock agents.';
        body.appendChild(hint);
    }

    function ensureChiefOfStaff(actionLabel, onProceed) {
        const count = countAssistantAgents();
        if (count === 1) {
            if (typeof onProceed === 'function') {
                onProceed();
            }
            return true;
        }
        if (count === 0) {
            showChiefRequiredModal(actionLabel);
        } else {
            showMultipleChiefsModal(actionLabel);
        }
        return false;
    }

    function renderAgentSidebar() {
        const container = document.getElementById('agent-list');
        if (!container) return;

        container.innerHTML = '';
        const agents = state.agents.list || [];

        if (agents.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'agent-empty-state';

            const icon = document.createElement('div');
            icon.className = 'agent-empty-icon';
            icon.textContent = '+';

            const title = document.createElement('div');
            title.className = 'agent-empty-title';
            title.textContent = 'Add your first agent';

            const text = document.createElement('div');
            text.className = 'agent-empty-text';
            text.textContent = 'Create an archetype to begin delegating work.';

            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'btn-primary';
            button.textContent = '+ Add Agent';
            button.addEventListener('click', () => showAddAgentWizard());

            empty.appendChild(icon);
            empty.appendChild(title);
            empty.appendChild(text);
            empty.appendChild(button);
            container.appendChild(empty);
            return;
        }

        let reorderInFlight = false;
        const persistAgentOrder = async () => {
            if (reorderInFlight) {
                return;
            }
            const orderedIds = Array.from(container.querySelectorAll('.agent-card'))
                .map(el => el.dataset.agentId)
                .filter(Boolean);
            if (orderedIds.length === 0) {
                return;
            }
            const currentOrder = (state.agents.list || []).map(agent => agent.id).filter(Boolean);
            const isSameOrder = orderedIds.length === currentOrder.length
                && orderedIds.every((id, index) => id === currentOrder[index]);
            if (isSameOrder) {
                return;
            }
            state.agents.list = orderedIds
                .map(id => agents.find(agent => agent.id === id))
                .filter(Boolean);
            try {
                reorderInFlight = true;
                await agentApi.reorder(orderedIds);
                await window.loadAgents();
            } catch (err) {
                log(`Failed to save agent order: ${err.message}`, 'warning');
            } finally {
                reorderInFlight = false;
            }
        };

        agents.forEach(agent => {
            const item = document.createElement('div');
            item.className = 'agent-card';
            item.dataset.agentId = agent.id || '';
            item.draggable = true;

            const isChiefOfStaff = isAssistantAgent(agent);
            if (isChiefOfStaff) {
                item.classList.add('chief-of-staff');
            }
            if (state.agents.locked) {
                const assistantCount = countAssistantAgents();
                item.classList.add('is-disabled');
                if (state.agents.prepLocked) {
                    item.title = 'Agents are locked until project preparation is completed.';
                } else {
                    item.title = assistantCount > 1
                        ? 'Agents are locked until extra Chiefs of Staff are disabled.'
                        : 'Agents are locked until a Chief of Staff exists.';
                }
            }

            // === Main content area (avatar + info + status LED) ===
            const mainContent = document.createElement('div');
            mainContent.className = 'agent-card-main';

            // Large circular avatar
            const avatar = document.createElement('div');
            avatar.className = 'agent-card-avatar';
            const avatarData = agent.avatar && agent.avatar.trim() ? agent.avatar.trim() : '';

            if (avatarData.startsWith('data:') || avatarData.startsWith('http')) {
                const img = document.createElement('img');
                img.src = avatarData;
                img.alt = agent.name || 'Agent';
                avatar.appendChild(img);
                avatar.classList.add('has-image');
            } else if (avatarData) {
                avatar.textContent = avatarData;
                avatar.classList.add('has-emoji');
            } else {
                avatar.textContent = agent.name ? agent.name.charAt(0).toUpperCase() : '?';
                avatar.classList.add('has-initial');
            }

            if (agent.color && !avatarData.startsWith('data:') && !avatarData.startsWith('http')) {
                avatar.style.background = agent.color;
            }

            // Info section (name + role)
            const info = document.createElement('div');
            info.className = 'agent-card-info';

            const nameRow = document.createElement('div');
            nameRow.className = 'agent-card-name-row';

            const name = document.createElement('div');
            name.className = 'agent-card-name';
            const fullName = agent.name || 'Unnamed Agent';
            name.textContent = fullName;
            name.title = fullName;
            nameRow.appendChild(name);

            // Chief of Staff star badge (inline with name)
            if (isChiefOfStaff) {
                const star = document.createElement('span');
                star.className = 'agent-card-star';
                star.textContent = '★';
                star.title = 'Chief of Staff';
                nameRow.appendChild(star);
            }

            const role = document.createElement('div');
            role.className = 'agent-card-role';
            role.textContent = agent.role || 'role';

            info.appendChild(nameRow);
            info.appendChild(role);

            // Status LED (top right)
            const statusInfo = getAgentStatusInfo(agent);
            const statusLed = document.createElement('div');
            statusLed.className = `agent-card-led ${statusInfo.className}`;
            statusLed.title = statusInfo.title;

            mainContent.appendChild(avatar);
            mainContent.appendChild(info);
            mainContent.appendChild(statusLed);

            item.appendChild(mainContent);

            // === Status effects bar (bottom) ===
            const statusBar = document.createElement('div');
            statusBar.className = 'agent-card-status-bar';

            const effects = [];
            const activityState = getAgentActivityState(agent);
            const activityMessage = getAgentActivityMessage(agent);

            // Activity states
            if (activityState === 'reading') {
                effects.push({ label: 'reading', icon: '📖', title: activityMessage || 'Reviewing context and references.' });
            } else if (activityState === 'processing') {
                effects.push({ label: 'thinking', icon: '💭', title: activityMessage || 'Thinking through the best next steps.', anim: 'pulse' });
            } else if (activityState === 'executing') {
                effects.push({ label: 'working', icon: '⚡', title: activityMessage || 'Producing output / taking action now.' });
            }

            // Queued turns
            const queuedCount = getQueuedCount(agent.id);
            if (queuedCount > 0) {
                const queuedLabel = queuedCount > 1 ? `${queuedCount} queued` : 'queued';
                effects.push({ label: queuedLabel, icon: '📋', title: `Queued for turn (${queuedCount} ${queuedCount > 1 ? 'turns' : 'turn'}).` });
            }

            // Supervision states
            const supervisionState = getAgentSupervisionState(agent);
            if (supervisionState === 'watched') {
                effects.push({ label: 'watched', icon: '👁️', title: 'This agent is being monitored due to recent uncertainty.' });
            } else if (supervisionState === 'assisted') {
                const note = agent.assistedNotes ? ` ${agent.assistedNotes}` : '';
                const dosage = agent.assistedTaskDosage ? ` Dosage: ${agent.assistedTaskDosage}.` : '';
                const queue = agent.assistedQueueSize !== null && agent.assistedQueueSize !== undefined
                    ? ` Queue: ${agent.assistedQueueSize}.`
                    : '';
                effects.push({ label: 'assisted', icon: '🤝', title: `Another agent is quietly assisting to ensure accuracy.${dosage}${queue}${note}` });
            }

            // Primary for role
            if (agent.isPrimaryForRole) {
                effects.push({ label: 'primary', icon: '🎯', title: 'Primary agent for this role.' });
            }

            // Show idle if no active effects
            if (effects.length === 0) {
                effects.push({ label: 'idle', icon: '💤', title: 'Available and ready for work.' });
            }

            // Render effect pills (max 3)
            effects.slice(0, 3).forEach(effect => {
                const pill = document.createElement('span');
                pill.className = 'agent-status-pill';
                if (effect.anim) {
                    pill.classList.add(`anim-${effect.anim}`);
                }
                pill.title = effect.title;
                pill.innerHTML = `<span class="pill-icon">${effect.icon}</span><span class="pill-label">${effect.label}</span>`;
                statusBar.appendChild(pill);
            });

            item.appendChild(statusBar);

            item.addEventListener('click', () => {
                if (!ensureChiefOfStaff('Agent chat')) {
                    return;
                }
                container.querySelectorAll('.agent-card').forEach(el => el.classList.remove('active'));
                item.classList.add('active');
                log(`Selected agent: ${agent.name}`, 'info');
                showWorkbenchChatModal(agent);
            });

            item.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                if (!ensureChiefOfStaff('Agent actions')) {
                    return;
                }
                showAgentContextMenu(e, agent);
            });

            item.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('text/plain', agent.id || '');
                e.dataTransfer.effectAllowed = 'move';
                item.classList.add('dragging');
            });

            item.addEventListener('dragend', async () => {
                item.classList.remove('dragging');
                await persistAgentOrder();
            });

            container.appendChild(item);
        });

        container.addEventListener('dragover', (e) => {
            e.preventDefault();
        });

        container.addEventListener('drop', async (e) => {
            e.preventDefault();
            await persistAgentOrder();
        });

        container.querySelectorAll('.agent-card').forEach(item => {
            item.addEventListener('dragover', (e) => {
                e.preventDefault();
                const dragging = container.querySelector('.agent-card.dragging');
                if (!dragging || dragging === item) return;
                const rect = item.getBoundingClientRect();
                const shouldInsertAfter = e.clientY > rect.top + rect.height / 2;
                if (shouldInsertAfter) {
                    item.after(dragging);
                } else {
                    item.before(dragging);
                }
            });

            item.addEventListener('drop', async (e) => {
                e.preventDefault();
                await persistAgentOrder();
            });
        });
    }

    function getAgentStatusInfo(agent) {
        if (agent.enabled === false) {
            return { className: 'offline', title: 'Offline' };
        }
        const status = state.agents.statusById[agent.id] || 'unknown';
        switch (status) {
            case 'ready':
                return { className: 'ready', title: 'Ready' };
            case 'unreachable':
                return { className: 'unreachable', title: 'Endpoint not reachable' };
            case 'unconfigured':
                return { className: 'unconfigured', title: 'No endpoint configured' };
            case 'incomplete':
                return { className: 'unreachable', title: 'Endpoint incomplete (missing model)' };
            case 'checking':
                return { className: 'checking', title: 'Checking endpoint...' };
            default:
                return { className: 'unknown', title: 'Status unknown' };
        }
    }

    function createAgentStatusIcon(src, title, className) {
        const wrapper = document.createElement('span');
        wrapper.className = `agent-status-icon${className ? ` ${className}` : ''}`;
        wrapper.title = title || '';

        const img = document.createElement('img');
        img.src = src;
        img.alt = '';
        img.setAttribute('aria-hidden', 'true');
        wrapper.appendChild(img);

        return wrapper;
    }

    function handleAgentMenuAction(action, agent) {
        const agentName = agent && agent.name ? agent.name : 'Agent';
        if (!ensureChiefOfStaff('Agent actions')) {
            return;
        }
        switch (action) {
            case 'invite-conference':
                showConferenceInviteModal(agent);
                break;
            case 'invite-chat':
                showWorkbenchChatModal(agent);
                break;
            case 'open-profile':
                showAgentProfileModal(agent);
                break;
            case 'open-role-settings':
                showRoleSettingsModal(agent);
                break;
            case 'open-agent-settings':
                showAgentSettingsModal(agent, { applyRolePresetWhenEmpty: true });
                break;
            case 'change-role':
                showChangeRoleModal(agent);
                break;
            case 'export':
                exportAgent(agent);
                break;
            case 'duplicate':
                duplicateAgent(agent);
                break;
            case 'retire':
                showConfirmRetireModal(agent);
                break;
            default:
                log(`Unknown action for ${agentName}`, 'warning');
                break;
        }
    }

    function exportAgent(agent) {
        // Create a clean export object (remove internal IDs that shouldn't transfer)
        const exportData = {
            name: agent.name,
            role: agent.role,
            avatar: agent.avatar,
            color: agent.color,
            personality: agent.personality,
            personalitySliders: agent.personalitySliders,
            signatureLine: agent.signatureLine,
            skills: agent.skills,
            goals: agent.goals,
            memoryProfile: agent.memoryProfile,
            exportedAt: new Date().toISOString(),
            exportVersion: 1
        };

        const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `agent-${agent.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        log(`Exported agent: ${agent.name}`, 'success');
        notificationStore.success(`Exported ${agent.name}`, 'workbench');
    }

    async function duplicateAgent(agent) {
        const duplicateData = {
            ...agent,
            id: null, // Will be generated by backend
            name: `${agent.name} (Copy)`,
            clonedFrom: agent.id
        };

        try {
            const imported = await agentApi.import(duplicateData);
            log(`Duplicated agent: ${imported.name}`, 'success');
            notificationStore.success(`Created ${imported.name}`, 'workbench');
            await window.loadAgents();
        } catch (err) {
            log(`Failed to duplicate agent: ${err.message}`, 'error');
            notificationStore.error(`Failed to duplicate: ${err.message}`, 'workbench');
        }
    }

    function showImportAgentDialog() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json';
        input.style.display = 'none';

        input.addEventListener('change', async (e) => {
            const file = e.target.files?.[0];
            if (!file) return;

            try {
                const text = await file.text();
                const agentData = JSON.parse(text);

                // Validate basic structure
                if (!agentData.name || !agentData.role) {
                    throw new Error('Invalid agent file: missing name or role');
                }

                const imported = await agentApi.import(agentData);
                log(`Imported agent: ${imported.name}`, 'success');
                notificationStore.success(`Imported ${imported.name}`, 'workbench');
                await window.loadAgents();
            } catch (err) {
                log(`Failed to import agent: ${err.message}`, 'error');
                notificationStore.error(`Import failed: ${err.message}`, 'workbench');
            }

            input.remove();
        });

        document.body.appendChild(input);
        input.click();
    }

    function showAddAgentWizard(resume = {}) {
        if (window.canAddAgents && !window.canAddAgents()) {
            showPreparationRequiredModal('Adding agents');
            return;
        }
        const { state: resumeState, stepIndex: resumeStepIndex } = resume;
        const { modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            'Add Agent',
            'Next',
            'Cancel',
            { closeOnCancel: false }
        );

        modal.classList.add('agent-create-modal');

        const templates = [
            {
                id: 'assistant',
                label: 'Assistant',
                role: 'assistant',
                description: 'Chief of Staff who coordinates the rest of the team.',
                skills: ['coordination', 'pacing', 'system health'],
                goals: ['maintain team cadence', 'enforce guardrails'],
                instructions: 'Coordinate the team and manage pacing. Avoid authoring creative canon unless asked.'
            },
            {
                id: 'planner',
                label: 'Planner',
                role: 'planner',
                description: 'Story structure, beats, and long-range arc.',
                skills: ['structure', 'beats', 'timeline'],
                goals: ['maintain story shape', 'catch structural issues'],
                instructions: 'Focus on structure, beats, and timeline continuity.'
            },
            {
                id: 'writer',
                label: 'Writer',
                role: 'writer',
                description: 'Prose, dialogue, and scene flow.',
                skills: ['prose', 'voice', 'scene flow'],
                goals: ['write vivid scenes', 'maintain tone'],
                instructions: 'Focus on prose, voice, and scene flow.'
            },
            {
                id: 'editor',
                label: 'Editor',
                role: 'editor',
                description: 'Clarity, grammar, and pacing polish.',
                skills: ['clarity', 'grammar', 'pacing'],
                goals: ['polish prose', 'remove friction'],
                instructions: 'Focus on clarity, grammar, and pacing.'
            },
            {
                id: 'critic',
                label: 'Critic',
                role: 'critic',
                description: 'Feedback, themes, and logic stress tests.',
                skills: ['feedback', 'themes', 'logic'],
                goals: ['identify weak spots', 'stress-test ideas'],
                instructions: 'Focus on feedback, themes, and logic.'
            },
            {
                id: 'continuity',
                label: 'Continuity',
                role: 'continuity',
                description: 'Canon, worldbuilding, and consistency checks.',
                skills: ['lore', 'canon', 'consistency'],
                goals: ['protect canon', 'catch conflicts'],
                instructions: 'Focus on lore consistency and canon.'
            },
            {
                id: 'custom',
                label: 'Custom',
                role: '',
                description: 'Start from a blank template.',
                skills: [],
                goals: [],
                instructions: ''
            }
        ];

        const hasAssistant = (state.agents.list || []).some(agent => isAssistantAgent(agent));
        const availableTemplates = templates;

        const providers = [
            'openai', 'anthropic', 'gemini', 'grok', 'openrouter', 'nanogpt', 'togetherai',
            'lmstudio', 'ollama', 'jan', 'koboldcpp', 'custom'
        ];

        const initialTemplate = availableTemplates[0];
        const formState = resumeState ? { ...resumeState } : {
            templateId: initialTemplate.id,
            name: '',
            role: initialTemplate.role,
            skills: [...initialTemplate.skills],
            goals: [...initialTemplate.goals],
            instructions: initialTemplate.instructions,
            provider: 'openai',
            model: '',
            keyRef: '',
            baseUrl: '',
            temperature: '',
            topP: '',
            topK: '',
            minP: '',
            repeatPenalty: '',
            maxOutputTokens: '',
            useProviderDefaults: false,
            configurePersonality: false,
            personalityConfigured: false,
            personalityInstructions: '',
            personalitySliders: {},
            signatureLine: '',
            avatar: ''
        };

        const assistantTemplate = templates.find(template => template.id === 'assistant');
        if (!hasAssistant && assistantTemplate) {
            formState.templateId = assistantTemplate.id;
            formState.role = assistantTemplate.role;
            formState.skills = [...assistantTemplate.skills];
            formState.goals = [...assistantTemplate.goals];
            formState.instructions = assistantTemplate.instructions;
        } else if (formState.templateId === 'assistant' && !assistantTemplate) {
            formState.templateId = availableTemplates[0].id;
            formState.role = availableTemplates[0].role;
            formState.skills = [...availableTemplates[0].skills];
            formState.goals = [...availableTemplates[0].goals];
            formState.instructions = availableTemplates[0].instructions;
        }

        let stepIndex = Number.isInteger(resumeStepIndex) ? resumeStepIndex : 0;
        const lastStep = 4;
        let lastRoleKey = canonicalizeRole(formState.role);
        applyRoleEndpointPreset(formState, formState.role, { force: true });

        const updateButtons = () => {
            cancelBtn.textContent = stepIndex === 0 ? 'Cancel' : 'Back';
            confirmBtn.textContent = stepIndex === lastStep ? 'Create Agent' : 'Next';
        };

        const setNextEnabled = (enabled) => {
            confirmBtn.disabled = !enabled;
        };

        const getDefaultAgentName = (role) => {
            const roleKey = canonicalizeRole(role);
            if (roleKey === 'assistant') {
                return 'Chief of Staff';
            }
            return (role || 'agent').trim() || 'agent';
        };

        const generateAgentName = (role) => {
            const base = getDefaultAgentName(role);
            const existing = (state.agents.list || []).map(item => (item.name || '').toLowerCase());
            let candidate = base;
            let counter = 2;
            while (existing.includes(candidate.toLowerCase())) {
                candidate = `${base}${counter++}`;
            }
            return candidate;
        };

        const renderPurposeStep = () => {
            const row = document.createElement('div');
            row.className = 'modal-row';

            const label = document.createElement('label');
            label.className = 'modal-label';
            label.textContent = 'Purpose';
            label.title = 'Pick a starting template. You can change name and role next.';

            const select = document.createElement('select');
            select.className = 'modal-select';
            select.title = 'Starting template for role, skills, and instructions.';
            availableTemplates.forEach(template => {
                const option = document.createElement('option');
                option.value = template.id;
                option.textContent = template.label;
                select.appendChild(option);
            });
            select.value = formState.templateId;
            select.disabled = !hasAssistant;

            const description = document.createElement('div');
            description.className = 'modal-text';
            const activeTemplate = availableTemplates.find(template => template.id === formState.templateId);
            description.textContent = activeTemplate?.description || '';

            select.addEventListener('change', () => {
                const chosen = availableTemplates.find(template => template.id === select.value);
                if (!chosen) return;
                formState.templateId = chosen.id;
                formState.role = chosen.role;
                formState.skills = [...chosen.skills];
                formState.goals = [...chosen.goals];
                formState.instructions = chosen.instructions;
                description.textContent = chosen.description;
                applyRoleEndpointPreset(formState, formState.role, { force: true });
                lastRoleKey = canonicalizeRole(formState.role);
            });

            row.appendChild(label);
            row.appendChild(select);
            row.appendChild(description);
            body.appendChild(row);

            setNextEnabled(true);
        };

        const renderIdentityStep = () => {
            const hint = document.createElement('div');
            hint.className = 'modal-text';
            hint.textContent = 'Name is optional. Leave it blank to use the role name (or role2, role3...).';
            body.appendChild(hint);

            const nameRow = document.createElement('div');
            nameRow.className = 'modal-row';
            const nameLabel = document.createElement('label');
            nameLabel.className = 'modal-label';
            nameLabel.textContent = 'Name';
            nameLabel.title = 'Display name for the agent.';
            const nameInput = document.createElement('input');
            nameInput.className = 'modal-input';
            nameInput.type = 'text';
            nameInput.placeholder = 'e.g., Beta Reader A';
            nameInput.title = 'Optional. Defaults to the role name if empty.';
            nameInput.value = formState.name;
            nameRow.appendChild(nameLabel);
            nameRow.appendChild(nameInput);
            body.appendChild(nameRow);

            const roleRow = document.createElement('div');
            roleRow.className = 'modal-row';
            const roleLabel = document.createElement('label');
            roleLabel.className = 'modal-label';
            roleLabel.textContent = 'Role';
            roleLabel.title = 'Functional role used for filters and routing.';
            const roleInput = document.createElement('input');
            roleInput.className = 'modal-input';
            roleInput.type = 'text';
            roleInput.placeholder = 'e.g., writer, critic, sensitivity reader';
            roleInput.title = 'Required. Short, lowercase roles work best.';
            roleInput.value = formState.role;
            roleRow.appendChild(roleLabel);
            roleRow.appendChild(roleInput);
            body.appendChild(roleRow);

            const roleError = document.createElement('div');
            roleError.className = 'modal-error-hint';
            roleError.textContent = 'Role is required.';
            roleError.style.display = 'none';
            body.appendChild(roleError);

            const lockRole = !hasAssistant;
            if (lockRole && assistantTemplate) {
                roleInput.value = assistantTemplate.role;
                roleInput.disabled = true;
                roleInput.title = 'Role is locked until a Chief of Staff exists.';
                const lockedHint = document.createElement('div');
                lockedHint.className = 'modal-hint';
                lockedHint.textContent = 'Chief of Staff is required before other roles can be created.';
                body.appendChild(lockedHint);
            }

            const updateIdentityState = () => {
                formState.name = nameInput.value;
                formState.role = lockRole && assistantTemplate ? assistantTemplate.role : roleInput.value.trim();
                const hasRole = Boolean(formState.role);
                roleError.style.display = hasRole ? 'none' : 'block';
                setNextEnabled(hasRole);
                const nextRoleKey = canonicalizeRole(formState.role);
                if (nextRoleKey && nextRoleKey !== lastRoleKey) {
                    applyRoleEndpointPreset(formState, formState.role, { force: true });
                    lastRoleKey = nextRoleKey;
                }
            };

            nameInput.addEventListener('input', updateIdentityState);
            roleInput.addEventListener('input', updateIdentityState);

            updateIdentityState();
        };

        const renderEndpointStep = () => {
            const info = document.createElement('div');
            info.className = 'modal-text';
            info.textContent = 'Configure the endpoint this agent will use.';
            info.title = 'Provider and model determine how the agent responds.';
            body.appendChild(info);

            const summary = document.createElement('div');
            summary.className = 'modal-text';
            const providerLabel = formState.provider ? escapeHtml(formState.provider) : 'Not set';
            const modelLabel = formState.model ? escapeHtml(formState.model) : 'Not set';
            summary.innerHTML = `
                <div><strong>Provider:</strong> ${providerLabel}</div>
                <div><strong>Model:</strong> ${modelLabel}</div>
            `;
            body.appendChild(summary);

            const hint = document.createElement('div');
            const wired = window.isEndpointWired({
                provider: formState.provider,
                model: formState.model,
                keyRef: formState.keyRef
            });
            hint.className = wired ? 'modal-hint' : 'modal-error-hint';
            hint.textContent = wired
                ? 'Endpoint configured. You can adjust settings anytime.'
                : 'Endpoint not wired yet. Configure provider, model, and API key if required.';
            body.appendChild(hint);

            const actions = document.createElement('div');
            actions.className = 'modal-buttons';
            const configureBtn = document.createElement('button');
            configureBtn.type = 'button';
            configureBtn.className = 'modal-btn modal-btn-primary';
            configureBtn.textContent = 'Configure Endpoint';
            configureBtn.title = 'Open provider/model settings for this agent.';
            actions.appendChild(configureBtn);
            body.appendChild(actions);

            confirmBtn.textContent = wired ? 'Next' : 'Skip';
            setNextEnabled(true);

            configureBtn.addEventListener('click', () => {
                const resumeStep = stepIndex;
                close();
                const draftAgent = {
                    id: null,
                    name: formState.name.trim() || generateAgentName(formState.role),
                    role: formState.role.trim(),
                    endpoint: {
                        provider: formState.provider,
                        model: formState.model,
                        apiKeyRef: formState.keyRef || null,
                        baseUrl: formState.baseUrl || null,
                        temperature: formState.temperature ?? null,
                        topP: formState.topP ?? null,
                        topK: formState.topK ?? null,
                        minP: formState.minP ?? null,
                        repeatPenalty: formState.repeatPenalty ?? null,
                        maxOutputTokens: formState.maxOutputTokens ?? null,
                        useProviderDefaults: formState.useProviderDefaults ?? false
                    }
                };
                showAgentSettingsModal(draftAgent, {
                    allowDraft: true,
                    initialEndpoint: draftAgent.endpoint,
                    applyRolePresetWhenEmpty: true,
                    onSave: async (endpoint) => {
                        formState.provider = endpoint.provider || formState.provider;
                        formState.model = endpoint.model || '';
                        formState.keyRef = endpoint.apiKeyRef || endpoint.keyRef || '';
                        formState.baseUrl = endpoint.baseUrl || '';
                        formState.temperature = endpoint.temperature ?? '';
                        formState.topP = endpoint.topP ?? '';
                        formState.topK = endpoint.topK ?? '';
                        formState.minP = endpoint.minP ?? '';
                        formState.repeatPenalty = endpoint.repeatPenalty ?? '';
                        formState.maxOutputTokens = endpoint.maxOutputTokens ?? '';
                        formState.useProviderDefaults = endpoint.useProviderDefaults ?? false;
                    },
                    onClose: () => {
                        showAddAgentWizard({ state: formState, stepIndex: resumeStep });
                    }
                });
            });

            setNextEnabled(true);
        };

        const renderPersonalityStep = () => {
            const info = document.createElement('div');
            info.className = 'modal-text';
            info.textContent = 'Would you like to configure this agent\'s personality now? You can also do it later.';
            body.appendChild(info);

            const yesInput = document.createElement('input');
            yesInput.type = 'radio';
            yesInput.name = 'personality-choice';
            yesInput.value = 'yes';
            yesInput.checked = formState.configurePersonality;
            const noInput = document.createElement('input');
            noInput.type = 'radio';
            noInput.name = 'personality-choice';
            noInput.value = 'no';
            noInput.checked = !formState.configurePersonality;
            const yesRow = document.createElement('label');
            yesRow.className = 'modal-choice-row';
            yesRow.title = 'Open the Agent Profile to tweak sliders and instructions.';
            const yesText = document.createElement('span');
            yesText.textContent = 'Yes, configure personality now';
            yesRow.appendChild(yesInput);
            yesRow.appendChild(yesText);

            const noRow = document.createElement('label');
            noRow.className = 'modal-choice-row';
            noRow.title = 'Skip for now and use defaults.';
            const noText = document.createElement('span');
            noText.textContent = 'Skip for now';
            noRow.appendChild(noInput);
            noRow.appendChild(noText);

            body.appendChild(yesRow);
            body.appendChild(noRow);

            const updateChoice = () => {
                formState.configurePersonality = yesInput.checked;
                setNextEnabled(true);
            };

            yesInput.addEventListener('change', updateChoice);
            noInput.addEventListener('change', updateChoice);

            updateChoice();
        };

        const renderConfirmStep = () => {
            const name = formState.name.trim() || generateAgentName(formState.role);
            const personalityStatus = formState.personalityConfigured ? 'Configured' : 'Default';
            const summary = document.createElement('div');
            summary.className = 'modal-text';
            summary.innerHTML = `
                <div><strong>Name:</strong> ${escapeHtml(name)}</div>
                <div><strong>Role:</strong> ${escapeHtml(formState.role)}</div>
                <div><strong>Provider:</strong> ${escapeHtml(formState.provider)}</div>
                <div><strong>Model:</strong> ${escapeHtml(formState.model)}</div>
                <div><strong>Personality:</strong> ${escapeHtml(personalityStatus)}</div>
            `;
            body.appendChild(summary);

            const note = document.createElement('div');
            note.className = 'modal-text';
            note.textContent = 'You can customize this agent later in Agent Settings.';
            body.appendChild(note);

            setNextEnabled(true);
        };

        const openPersonalityConfigurator = () => {
            const role = formState.role.trim() || 'agent';
            const name = formState.name.trim() || generateAgentName(role);
            const baseInstructions = formState.personalityInstructions ||
                formState.instructions ||
                `Focus on your role: ${role}.`;

            const draftAgent = {
                id: 'draft',
                name,
                role,
                avatar: formState.avatar || '',
                personality: {
                    tone: 'neutral',
                    verbosity: 'normal',
                    voiceTags: [role],
                    baseInstructions
                },
                personalitySliders: formState.personalitySliders || {},
                signatureLine: formState.signatureLine || ''
            };

            let profileSaved = false;
            close();

            showAgentProfileModal(draftAgent, {
                onSave: async (updatedAgent) => {
                    profileSaved = true;
                    formState.name = updatedAgent.name || formState.name;
                    formState.role = updatedAgent.role || formState.role;
                    formState.avatar = updatedAgent.avatar || formState.avatar;
                    formState.personalityInstructions = updatedAgent.personality?.baseInstructions || '';
                    formState.personalitySliders = updatedAgent.personalitySliders || {};
                    formState.signatureLine = updatedAgent.signatureLine || '';
                    formState.personalityConfigured = true;
                },
                onClose: () => {
                    const resumeStep = profileSaved ? Math.min(stepIndex + 1, lastStep) : stepIndex;
                    showAddAgentWizard({ state: formState, stepIndex: resumeStep });
                }
            });
        };

        const renderStep = () => {
            body.innerHTML = '';
            updateButtons();

            if (stepIndex === 0) {
                renderPurposeStep();
            } else if (stepIndex === 1) {
                renderIdentityStep();
            } else if (stepIndex === 2) {
                renderEndpointStep();
            } else if (stepIndex === 3) {
                renderPersonalityStep();
            } else {
                renderConfirmStep();
            }
        };

        cancelBtn.addEventListener('click', () => {
            if (stepIndex === 0) {
                close();
                return;
            }
            stepIndex = Math.max(0, stepIndex - 1);
            renderStep();
        });

        confirmBtn.addEventListener('click', async () => {
            if (stepIndex < lastStep) {
                if (stepIndex === 3 && formState.configurePersonality && !formState.personalityConfigured) {
                    openPersonalityConfigurator();
                    return;
                }
                stepIndex += 1;
                renderStep();
                return;
            }

            const role = formState.role.trim();
            const roleKey = canonicalizeRole(role);
            if (!roleKey) {
                setNextEnabled(false);
                return;
            }

            const name = formState.name.trim() || generateAgentName(role);
            const payload = {
                name,
                role: roleKey,
                avatar: formState.avatar || '',
                skills: formState.skills,
                goals: formState.goals,
                endpoint: {
                    provider: formState.provider,
                    model: formState.model,
                    apiKeyRef: formState.keyRef || null,
                    baseUrl: formState.baseUrl || null,
                    useProviderDefaults: formState.useProviderDefaults || false
                },
                personality: {
                    tone: 'neutral',
                    verbosity: 'normal',
                    voiceTags: [roleKey],
                    baseInstructions: formState.personalityInstructions ||
                        formState.instructions ||
                        `Focus on your role: ${roleKey}.`
                }
            };

            if (roleKey === 'assistant') {
                payload.canBeTeamLead = true;
            }

            if (formState.personalityConfigured && formState.personalitySliders) {
                payload.personalitySliders = formState.personalitySliders;
            }
            if (formState.signatureLine) {
                payload.signatureLine = formState.signatureLine;
            }

            const temperature = parseFloat(formState.temperature);
            if (!Number.isNaN(temperature)) {
                payload.endpoint.temperature = temperature;
            }
            const topP = parseFloat(formState.topP);
            if (!Number.isNaN(topP)) {
                payload.endpoint.topP = topP;
            }
            const topK = parseInt(formState.topK, 10);
            if (!Number.isNaN(topK)) {
                payload.endpoint.topK = topK;
            }
            const minP = parseFloat(formState.minP);
            if (!Number.isNaN(minP)) {
                payload.endpoint.minP = minP;
            }
            const repeatPenalty = parseFloat(formState.repeatPenalty);
            if (!Number.isNaN(repeatPenalty)) {
                payload.endpoint.repeatPenalty = repeatPenalty;
            }
            const maxTokens = parseInt(formState.maxOutputTokens, 10);
            if (!Number.isNaN(maxTokens)) {
                payload.endpoint.maxOutputTokens = maxTokens;
            }

            try {
                confirmBtn.disabled = true;
                confirmBtn.textContent = 'Creating...';
                const created = await agentApi.create(payload);
                log(`Created agent: ${created.name}`, 'success');
                const endpointSnapshot = {
                    provider: formState.provider,
                    model: formState.model,
                    keyRef: formState.keyRef || ''
                };
                const wiredNow = window.isEndpointWired(endpointSnapshot);
                if (wiredNow) {
                    notificationStore.push(
                        'success',
                        'workbench',
                        `Created and connected ${created.name}.`,
                        `Model: ${formState.model}`,
                        'social',
                        false,
                        '',
                        null,
                        'agents'
                    );
                } else {
                    notificationStore.push(
                        'success',
                        'workbench',
                        `Created ${created.name}.`,
                        `Role: ${created.role || role}`,
                        'social',
                        false,
                        '',
                        null,
                        'agents'
                    );
                }
                close();
                showPostCreateProfilePrompt(created);
                window.loadAgents().catch(err => log(`Failed to refresh agents: ${err.message}`, 'warning'));
                if (wiredNow) {
                    createAgentIntroIssue(created, endpointSnapshot, 'initial wiring')
                        .catch(err => log(`Failed to create intro issue: ${err.message}`, 'warning'));
                }
            } catch (err) {
                log(`Failed to create agent: ${err.message}`, 'error');
                notificationStore.error(`Failed to create agent: ${err.message}`, 'workbench');
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Create Agent';
            }
        });

        renderStep();
    }

    function showPostCreateProfilePrompt(agent) {
        if (!agent) return;
        const agentName = agent.name || 'Agent';
        const { body, confirmBtn, cancelBtn, close } = createModalShell(
            'Agent Created',
            'Open Profile',
            'Skip',
            { closeOnCancel: true, closeOnConfirm: true }
        );

        const text = document.createElement('div');
        text.className = 'modal-text';
        text.textContent = `Would you like to open ${agentName}'s profile now? You can skip and edit later.`;
        body.appendChild(text);

        confirmBtn.addEventListener('click', () => {
            close();
            showAgentProfileModal(agent);
        });

        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => close());
        }
    }

    // Context Menu
    let contextMenu = null;

    function positionContextMenu(menu, x, y) {
        if (!menu) return;
        const padding = 8;
        const rect = menu.getBoundingClientRect();
        let left = x;
        let top = y;
        if (left + rect.width + padding > window.innerWidth) {
            left = Math.max(padding, window.innerWidth - rect.width - padding);
        }
        if (top + rect.height + padding > window.innerHeight) {
            top = Math.max(padding, window.innerHeight - rect.height - padding);
        }
        menu.style.left = `${left}px`;
        menu.style.top = `${top}px`;
        requestAnimationFrame(() => {
            menu.classList.add('is-open');
        });
    }

      function showContextMenu(e, node) {
          hideContextMenu();

          contextMenu = document.createElement('div');
          contextMenu.className = 'context-menu';

          const actions = [];
          const canRename = window.canRenamePath ? window.canRenamePath(node.path, node.type) : true;
          const multiSelectEnabled = window.isPrepMultiSelectEnabled ? window.isPrepMultiSelectEnabled() : false;

          if (multiSelectEnabled && node.type === 'file' && window.setTreeSelection && window.getTreeSelection) {
              const selection = window.getTreeSelection();
              if (!selection.includes(node.path)) {
                  window.setTreeSelection([node.path]);
              }
          }
          const selection = window.getTreeSelection ? window.getTreeSelection() : [];
          const hasMultiSelection = multiSelectEnabled && selection.length > 1;
          if (hasMultiSelection && window.promptMoveMultiple) {
              actions.push({
                  label: `Move ${selection.length} Selected...`,
                  action: () => window.promptMoveMultiple(selection)
              });
              if (window.clearTreeSelection) {
                  actions.push({ label: 'Clear Selection', action: () => window.clearTreeSelection() });
              }
              actions.push({ divider: true });
          }

          // Only show "Open in New Tab" for files, not folders
          if (node.type === 'file') {
              actions.push({ label: 'Explore', action: () => window.explorePath(node.path, node.type) });
              actions.push({ label: 'Open in New Tab', action: () => window.openFileInNewTab(node.path) });
            actions.push({ label: 'View History', action: () => {
                if (window.showFileHistory) {
                    window.showFileHistory(node.path);
                }
            } });
        }

        // For folders: add "New File Here" and "New Folder Here"
        if (node.type === 'folder') {
            actions.push({ label: 'Explore', action: () => window.explorePath(node.path, node.type) });
            actions.push({ label: 'New File Here...', action: () => window.promptNewFile('file', node.path) });
            actions.push({ label: 'New Folder Here...', action: () => window.promptNewFile('folder', node.path) });
            actions.push({ divider: true });
        }

          if (canRename) {
              actions.push({ label: 'Rename', action: () => window.promptRename(node.path, node.type) });
          }
          actions.push({ label: 'Move...', action: () => window.promptMove(node.path, node.type) });
          actions.push({ divider: true });
          actions.push({ label: 'Delete', action: () => window.promptDelete(node.path, node.type) });

        actions.forEach(item => {
            if (item.divider) {
                const div = document.createElement('div');
                div.className = 'context-menu-divider';
                contextMenu.appendChild(div);
            } else {
                const menuItem = document.createElement('div');
                menuItem.className = 'context-menu-item';
                menuItem.textContent = item.label;
                menuItem.addEventListener('click', () => {
                    hideContextMenu();
                    item.action();
                });
                contextMenu.appendChild(menuItem);
            }
        });

        document.body.appendChild(contextMenu);
        positionContextMenu(contextMenu, e.clientX, e.clientY);
    }

    function showAgentContextMenu(e, agent) {
        hideContextMenu();

        contextMenu = document.createElement('div');
        contextMenu.className = 'context-menu';

        const actions = [
            { label: 'Invite to Conference', action: () => handleAgentMenuAction('invite-conference', agent) },
            { label: 'Invite to Chat', action: () => handleAgentMenuAction('invite-chat', agent) },
            { divider: true },
            { label: 'Open Agent Profile', action: () => handleAgentMenuAction('open-profile', agent) },
            { label: 'Open Role Settings', action: () => handleAgentMenuAction('open-role-settings', agent) },
            { label: 'Open Agent Settings', action: () => handleAgentMenuAction('open-agent-settings', agent) },
            { label: 'Change Role...', action: () => handleAgentMenuAction('change-role', agent) },
            { divider: true },
            { label: 'Export Agent...', action: () => handleAgentMenuAction('export', agent) },
            { label: 'Duplicate Agent', action: () => handleAgentMenuAction('duplicate', agent) },
            { divider: true },
            { label: 'Retire Agent', action: () => handleAgentMenuAction('retire', agent) }
        ];

        actions.forEach(item => {
            if (item.divider) {
                const div = document.createElement('div');
                div.className = 'context-menu-divider';
                contextMenu.appendChild(div);
            } else {
                const menuItem = document.createElement('div');
                menuItem.className = 'context-menu-item';
                menuItem.textContent = item.label;
                menuItem.addEventListener('click', () => {
                    hideContextMenu();
                    item.action();
                });
                contextMenu.appendChild(menuItem);
            }
        });

        document.body.appendChild(contextMenu);
        positionContextMenu(contextMenu, e.clientX, e.clientY);
    }

    function hideContextMenu() {
        if (contextMenu) {
            contextMenu.remove();
            contextMenu = null;
        }
    }

    document.addEventListener('click', hideContextMenu);

    // Modal functions are now in modals.js (window.modals)
    const showModal = window.modals.showModal;

    function showAgentProfileModal(agent, options = {}) {
        const agentName = agent?.name || 'Agent';
        const agentRole = agent?.role || '';
        const agentAvatar = agent?.avatar || '';
        const personality = agent?.personality || {};
        const baseInstructions = personality.baseInstructions || '';
        const originalRoleKey = canonicalizeRole(agentRole);

        // Personality sliders config: [id, leftLabel, rightLabel, defaultValue, tooltip]
        const sliderConfig = [
            ['humor', 'Serious', 'Playful', 50, 'How much levity vs gravitas in communication?'],
            ['strictness', 'Lenient', 'Strict', 50, 'How rigidly should rules and guidelines be enforced?'],
            ['diplomacy', 'Blunt', 'Diplomatic', 50, 'How direct or softened should feedback be?'],
            ['verbosity', 'Terse', 'Elaborate', 50, 'How much detail and explanation to include?'],
            ['confidence', 'Tentative', 'Assertive', 50, 'How strongly should opinions be stated?'],
            ['warmth', 'Formal', 'Warm', 50, 'Professional distance vs friendly and approachable?'],
            ['focus', 'Big Picture', 'Detail-Oriented', 50, 'Strategic overview vs granular analysis?'],
            ['pace', 'Methodical', 'Quick', 50, 'Thorough and careful vs rapid iteration?']
        ];

        // Personality presets for quick setup
        const presets = [
            {
                id: 'zen-strategist',
                emoji: '🧘',
                name: 'Zen Strategist',
                description: 'Calm, macro-thinking planner',
                sliders: { humor: 30, strictness: 40, diplomacy: 80, verbosity: 60, confidence: 50, warmth: 30, focus: 10, pace: 20 },
                signature: 'With patience and clarity, we proceed.',
                instructions: 'Speaks slowly, prioritizes clarity, prefers structured long-term plans and risk-mitigation. Makes suggestions gently but firmly.'
            },
            {
                id: 'playful-brainstormer',
                emoji: '🎉',
                name: 'Playful Brainstormer',
                description: 'Creative chaos gremlin',
                sliders: { humor: 80, strictness: 10, diplomacy: 60, verbosity: 80, confidence: 65, warmth: 85, focus: 25, pace: 90 },
                signature: 'Got another wild idea!',
                instructions: 'Generates surprising ideas, riffs, word associations. Not worried about feasibility first — great for ideation and unblocking.'
            },
            {
                id: 'academic-editor',
                emoji: '🧑‍🏫',
                name: 'Academic Editor',
                description: 'Pedantic but useful',
                sliders: { humor: 10, strictness: 70, diplomacy: 60, verbosity: 40, confidence: 50, warmth: 10, focus: 95, pace: 5 },
                signature: 'Please allow me to correct and refine this.',
                instructions: 'Very focused on grammar, citations, style consistency. Will critique sentence structure and tone. Less creative, more precise.'
            },
            {
                id: 'ruthless-critic',
                emoji: '🔥',
                name: 'Ruthless Critic',
                description: 'Brutally honest feedback',
                sliders: { humor: 5, strictness: 85, diplomacy: 5, verbosity: 15, confidence: 90, warmth: 20, focus: 85, pace: 30 },
                signature: 'You can do better — let\'s fix it.',
                instructions: 'No sugarcoating. Finds flaws, plot holes, lazy writing. Hard feedback mode. Best for revisions, not feelings.'
            },
            {
                id: 'compassionate-coach',
                emoji: '🧚',
                name: 'Compassionate Coach',
                description: 'Encouraging and supportive',
                sliders: { humor: 55, strictness: 25, diplomacy: 85, verbosity: 70, confidence: 50, warmth: 95, focus: 40, pace: 55 },
                signature: 'You\'re doing amazing — let\'s keep going!',
                instructions: 'Boosts morale, suggests improvements kindly, reminds you of progress, celebrates wins. Great when stuck.'
            },
            {
                id: 'lore-archivist',
                emoji: '🧠',
                name: 'Lore Archivist',
                description: 'Worldbuilding memory keeper',
                sliders: { humor: 35, strictness: 50, diplomacy: 75, verbosity: 85, confidence: 50, warmth: 50, focus: 92, pace: 15 },
                signature: 'I preserve what must not be forgotten.',
                instructions: 'Tracks world facts, canon, character sheets, timeline consistency. Offers cross-links. Great for sci-fi/fantasy projects.'
            },
            {
                id: 'productive-taskmaster',
                emoji: '🚀',
                name: 'Productive Taskmaster',
                description: 'Output over perfection',
                sliders: { humor: 25, strictness: 80, diplomacy: 35, verbosity: 30, confidence: 90, warmth: 40, focus: 50, pace: 90 },
                signature: 'Next step. No excuses.',
                instructions: 'Pushes progress. Breaks tasks into steps. Keeps momentum. "Good enough, ship it."'
            },
            {
                id: 'poetic-weaver',
                emoji: '🎨',
                name: 'Poetic Prose Weaver',
                description: 'Style and voice specialist',
                sliders: { humor: 65, strictness: 30, diplomacy: 80, verbosity: 95, confidence: 50, warmth: 80, focus: 40, pace: 60 },
                signature: 'Let the words dance.',
                instructions: 'Sensory language, metaphors, lyrical cadence. Perfect for scene flavor and voice experimentation.'
            },
            {
                id: 'plot-snake',
                emoji: '🐍',
                name: 'Plot Snake',
                description: 'Conflict-driven storytelling',
                sliders: { humor: 45, strictness: 50, diplomacy: 30, verbosity: 60, confidence: 85, warmth: 40, focus: 20, pace: 60 },
                signature: 'Stories move when things break.',
                instructions: 'Injects tension, twists, betrayal, dilemmas. Asks "What goes wrong?" relentlessly.'
            },
            {
                id: 'continuity-sentinel',
                emoji: '👁',
                name: 'Continuity Sentinel',
                description: 'Canon enforcer',
                sliders: { humor: 15, strictness: 75, diplomacy: 50, verbosity: 35, confidence: 70, warmth: 30, focus: 95, pace: 10 },
                signature: 'Canon must remain intact.',
                instructions: 'Tracks consistency, checks previous info, flags contradictions. Perfect for late-stage novel polishing.'
            }
        ];

        // Get existing slider values from agent or use defaults
        const sliderValues = agent?.personalitySliders || {};

        const { overlay, modal, body, confirmBtn, close } = createModalShell(
            'Agent Profile',
            'Save',
            'Cancel',
            { closeOnCancel: true, onClose: options.onClose }
        );

        modal.classList.add('agent-profile-modal');

        // === HEADER: Avatar + Identity Fields ===
        const header = document.createElement('div');
        header.className = 'agent-profile-header';

        // Avatar drop zone
        const avatarDrop = document.createElement('div');
        avatarDrop.className = 'agent-avatar-drop';
        avatarDrop.title = 'Click to upload or drag an image';

        let currentAvatarData = agentAvatar; // Will store base64 or emoji

        const updateAvatarDisplay = () => {
            avatarDrop.innerHTML = '';
            if (currentAvatarData) {
                if (currentAvatarData.startsWith('data:') || currentAvatarData.startsWith('http')) {
                    const img = document.createElement('img');
                    img.src = currentAvatarData;
                    img.alt = 'Agent avatar';
                    avatarDrop.appendChild(img);
                } else {
                    // Treat as emoji
                    const emoji = document.createElement('div');
                    emoji.className = 'agent-avatar-emoji';
                    emoji.textContent = currentAvatarData;
                    avatarDrop.appendChild(emoji);
                }
            } else {
                const placeholder = document.createElement('div');
                placeholder.className = 'agent-avatar-placeholder';
                placeholder.innerHTML = `
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                        <path d="M12 16a4 4 0 100-8 4 4 0 000 8z"/>
                        <path d="M3 16l3-3 4 4 6-6 5 5"/>
                        <rect x="3" y="3" width="18" height="18" rx="2"/>
                    </svg>
                    <span>Drop image</span>
                `;
                avatarDrop.appendChild(placeholder);
            }
        };

        updateAvatarDisplay();

        // Helper to resize image to max 256x256 for avatars
        const resizeImage = (file, maxSize = 256) => {
            return new Promise((resolve) => {
                const reader = new FileReader();
                reader.onload = (e) => {
                    const img = new Image();
                    img.onload = () => {
                        // Calculate new dimensions
                        let width = img.width;
                        let height = img.height;
                        if (width > height && width > maxSize) {
                            height = (height * maxSize) / width;
                            width = maxSize;
                        } else if (height > maxSize) {
                            width = (width * maxSize) / height;
                            height = maxSize;
                        }

                        // Draw to canvas and export
                        const canvas = document.createElement('canvas');
                        canvas.width = width;
                        canvas.height = height;
                        const ctx = canvas.getContext('2d');
                        ctx.drawImage(img, 0, 0, width, height);
                        resolve(canvas.toDataURL('image/jpeg', 0.85));
                    };
                    img.src = e.target.result;
                };
                reader.readAsDataURL(file);
            });
        };

        // Avatar file input (hidden)
        const avatarInput = document.createElement('input');
        avatarInput.type = 'file';
        avatarInput.accept = 'image/*';
        avatarInput.style.display = 'none';

        avatarInput.addEventListener('change', async (e) => {
            const file = e.target.files?.[0];
            if (file) {
                currentAvatarData = await resizeImage(file);
                updateAvatarDisplay();
            }
        });

        avatarDrop.addEventListener('click', () => avatarInput.click());

        // Drag and drop
        avatarDrop.addEventListener('dragover', (e) => {
            e.preventDefault();
            avatarDrop.classList.add('drag-over');
        });

        avatarDrop.addEventListener('dragleave', () => {
            avatarDrop.classList.remove('drag-over');
        });

        avatarDrop.addEventListener('drop', async (e) => {
            e.preventDefault();
            avatarDrop.classList.remove('drag-over');
            const file = e.dataTransfer.files?.[0];
            if (file && file.type.startsWith('image/')) {
                currentAvatarData = await resizeImage(file);
                updateAvatarDisplay();
            }
        });

        // Identity fields container
        const identityFields = document.createElement('div');
        identityFields.className = 'agent-identity-fields';

        // Name field
        const nameGroup = document.createElement('div');
        nameGroup.className = 'agent-field-group';
        const nameLabel = document.createElement('label');
        nameLabel.className = 'agent-field-label';
        nameLabel.textContent = 'Name';
        const nameInput = document.createElement('input');
        nameInput.type = 'text';
        nameInput.className = 'agent-field-input';
        nameInput.value = agentName;
        nameInput.placeholder = 'e.g., Serene, The Critic, Chaos Gremlin';
        nameGroup.appendChild(nameLabel);
        nameGroup.appendChild(nameInput);

        // Role field with suggestions
        const roleGroup = document.createElement('div');
        roleGroup.className = 'agent-field-group';
        const roleLabel = document.createElement('label');
        roleLabel.className = 'agent-field-label';
        roleLabel.textContent = 'Role';
        const roleWrapper = document.createElement('div');
        roleWrapper.className = 'agent-role-wrapper';
        const roleInput = document.createElement('input');
        roleInput.type = 'text';
        roleInput.className = 'agent-field-input';
        roleInput.value = agentRole;
        roleInput.placeholder = 'e.g., writer, critic, sensitivity reader';

        // Role suggestions dropdown
        const roleSuggestions = document.createElement('div');
        roleSuggestions.className = 'agent-role-suggestions';

        const existingRoles = new Map();
        (state.agents.list || []).forEach(agentItem => {
            const roleKey = canonicalizeRole(agentItem.role);
            if (roleKey && !existingRoles.has(roleKey)) {
                existingRoles.set(roleKey, agentItem.role);
            }
        });
        const suggestedRoles = [
            'planner', 'writer', 'editor', 'critic', 'continuity',
            'beta reader', 'sensitivity reader', 'lore keeper', 'devil\'s advocate'
        ];
        const suggestedRoleMap = new Map();
        suggestedRoles.forEach(role => {
            const roleKey = canonicalizeRole(role);
            if (roleKey && !existingRoles.has(roleKey) && !suggestedRoleMap.has(roleKey)) {
                suggestedRoleMap.set(roleKey, role);
            }
        });
        const allRoles = [...existingRoles.values(), ...suggestedRoleMap.values()];
        const roleKeys = new Set(allRoles.map(role => canonicalizeRole(role)).filter(Boolean));

        const updateRoleSuggestions = (filter = '') => {
            roleSuggestions.innerHTML = '';
            const filterKey = canonicalizeRole(filter || '');
            const filtered = allRoles.filter(r => {
                if (!filterKey) {
                    return true;
                }
                const roleKey = canonicalizeRole(r);
                return roleKey ? roleKey.includes(filterKey) : false;
            });
            filtered.forEach(role => {
                const item = document.createElement('div');
                item.className = 'agent-role-suggestion';
                item.textContent = role;
                item.addEventListener('click', () => {
                    roleInput.value = role;
                    roleSuggestions.classList.remove('visible');
                });
                roleSuggestions.appendChild(item);
            });
            // Add "create new" option if typing something new
            const trimmedFilter = filter.trim();
            if (trimmedFilter && !roleKeys.has(filterKey)) {
                const createNew = document.createElement('div');
                createNew.className = 'agent-role-suggestion create-new';
                createNew.textContent = `Create "${trimmedFilter}"`;
                createNew.addEventListener('click', () => {
                    roleSuggestions.classList.remove('visible');
                });
                roleSuggestions.appendChild(createNew);
            }
        };

        roleInput.addEventListener('focus', () => {
            updateRoleSuggestions(roleInput.value);
            roleSuggestions.classList.add('visible');
        });

        roleInput.addEventListener('input', () => {
            updateRoleSuggestions(roleInput.value);
        });

        roleInput.addEventListener('blur', () => {
            // Delay to allow click on suggestion
            setTimeout(() => roleSuggestions.classList.remove('visible'), 150);
        });

        roleWrapper.appendChild(roleInput);
        roleWrapper.appendChild(roleSuggestions);
        roleGroup.appendChild(roleLabel);
        roleGroup.appendChild(roleWrapper);

        identityFields.appendChild(nameGroup);
        identityFields.appendChild(roleGroup);

        header.appendChild(avatarDrop);
        header.appendChild(avatarInput);
        header.appendChild(identityFields);

        body.appendChild(header);

        // === PRESETS SECTION (Collapsible) ===
        const presetsSection = document.createElement('div');
        presetsSection.className = 'agent-profile-section agent-presets-section';

        const presetsHeader = document.createElement('div');
        presetsHeader.className = 'agent-section-title agent-presets-toggle';
        presetsHeader.innerHTML = `<span>Quick Presets</span><span class="presets-arrow">▼</span>`;
        presetsHeader.style.cursor = 'pointer';

        const presetsGrid = document.createElement('div');
        presetsGrid.className = 'agent-presets-grid collapsed';

        // Toggle presets visibility
        presetsHeader.addEventListener('click', () => {
            const isCollapsed = presetsGrid.classList.toggle('collapsed');
            presetsHeader.querySelector('.presets-arrow').textContent = isCollapsed ? '▼' : '▲';
        });

        // Will be populated after sliderInputs are created
        let applyPreset = null;

        presets.forEach(preset => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'agent-preset-btn';
            btn.title = preset.description;
            btn.innerHTML = `<span class="preset-emoji">${preset.emoji}</span><span class="preset-name">${preset.name}</span>`;
            btn.addEventListener('click', () => {
                if (applyPreset) applyPreset(preset);
            });
            presetsGrid.appendChild(btn);
        });

        presetsSection.appendChild(presetsHeader);
        presetsSection.appendChild(presetsGrid);
        body.appendChild(presetsSection);

        // === PERSONALITY SLIDERS ===
        const personalitySection = document.createElement('div');
        personalitySection.className = 'agent-profile-section';
        const personalityTitle = document.createElement('div');
        personalityTitle.className = 'agent-section-title';
        personalityTitle.textContent = 'Personality';

        const slidersContainer = document.createElement('div');
        slidersContainer.className = 'agent-sliders';

        const sliderInputs = {};

        sliderConfig.forEach(([id, leftLabel, rightLabel, defaultVal, tooltip]) => {
            const row = document.createElement('div');
            row.className = 'agent-slider-row';
            row.title = tooltip;

            const left = document.createElement('span');
            left.className = 'agent-slider-label';
            left.textContent = leftLabel;

            const slider = document.createElement('input');
            slider.type = 'range';
            slider.className = 'agent-slider-input';
            slider.min = '0';
            slider.max = '100';
            slider.value = sliderValues[id] ?? defaultVal;
            slider.id = `slider-${id}`;

            const right = document.createElement('span');
            right.className = 'agent-slider-label agent-slider-label-right';
            right.textContent = rightLabel;

            sliderInputs[id] = slider;

            row.appendChild(left);
            row.appendChild(slider);
            row.appendChild(right);
            slidersContainer.appendChild(row);
        });

        personalitySection.appendChild(personalityTitle);
        personalitySection.appendChild(slidersContainer);
        body.appendChild(personalitySection);

        // === INSTRUCTIONS ===
        const instructionsSection = document.createElement('div');
        instructionsSection.className = 'agent-profile-section';
        const instructionsTitle = document.createElement('div');
        instructionsTitle.className = 'agent-section-title';
        instructionsTitle.textContent = 'Instructions';

        const instructionsTextarea = document.createElement('textarea');
        instructionsTextarea.className = 'agent-instructions-textarea';
        instructionsTextarea.value = baseInstructions;
        instructionsTextarea.placeholder = 'Custom instructions for this agent...\n\ne.g., "Focus on structural issues and pacing. Point out plot holes without being harsh. Always suggest alternatives when criticizing."';

        const signatureRow = document.createElement('div');
        signatureRow.className = 'agent-signature-row';
        const signaturePrefix = document.createElement('span');
        signaturePrefix.className = 'agent-signature-prefix';
        signaturePrefix.textContent = 'Signature line:';
        const signatureInput = document.createElement('input');
        signatureInput.type = 'text';
        signatureInput.className = 'agent-signature-input';
        signatureInput.value = agent?.signatureLine || '';
        signatureInput.placeholder = 'Carthago delenda est.';

        signatureRow.appendChild(signaturePrefix);
        signatureRow.appendChild(signatureInput);

        instructionsSection.appendChild(instructionsTitle);
        instructionsSection.appendChild(instructionsTextarea);
        instructionsSection.appendChild(signatureRow);
        body.appendChild(instructionsSection);

        // === APPLY PRESET FUNCTION ===
        applyPreset = (preset) => {
            // Update sliders
            Object.entries(preset.sliders).forEach(([id, value]) => {
                if (sliderInputs[id]) {
                    sliderInputs[id].value = value;
                }
            });

            // Update instructions
            if (preset.instructions) {
                instructionsTextarea.value = preset.instructions;
            }

            // Update signature
            if (preset.signature) {
                signatureInput.value = preset.signature;
            }

            log(`Applied preset: ${preset.name}`, 'info');
        };

        // === SAVE HANDLER ===
        confirmBtn.addEventListener('click', async () => {
            const roleValue = roleInput.value.trim() || agentRole;
            const roleKey = canonicalizeRole(roleValue);
            const updatedAgent = {
                name: nameInput.value.trim() || agentName,
                role: roleKey || roleValue,
                avatar: currentAvatarData,
                personality: {
                    ...personality,
                    baseInstructions: instructionsTextarea.value
                },
                personalitySliders: {},
                signatureLine: signatureInput.value.trim()
            };

            // Collect slider values
            sliderConfig.forEach(([id]) => {
                updatedAgent.personalitySliders[id] = parseInt(sliderInputs[id].value, 10);
            });

            try {
                confirmBtn.disabled = true;
                confirmBtn.textContent = 'Saving...';

                if (typeof options.onSave === 'function') {
                    await options.onSave(updatedAgent);
                    close();
                } else {
                    const saved = await agentApi.update(agent.id, updatedAgent);
                    log(`Profile saved for ${saved.name}`, 'success');
                    notificationStore.success(`Saved profile for ${saved.name}`, 'workbench');

                    // Refresh agent list to show updated data
                    await window.loadAgents();
                    if (agent?.id) {
                        const nextRoleKey = canonicalizeRole(roleValue);
                        if (nextRoleKey && nextRoleKey !== originalRoleKey) {
                            await applyRoleEndpointPresetForAgent(agent, roleValue);
                        }
                    }

                    close();
                }
            } catch (err) {
                log(`Failed to save profile: ${err.message}`, 'error');
                if (options.onSave) {
                    notificationStore.error(`Failed to save profile: ${err.message}`, 'workbench');
                } else {
                    notificationStore.error(`Failed to save: ${err.message}`, 'workbench');
                }
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Save';
            }
        });
    }

    async function showRoleSettingsModal(agent) {
        const role = agent?.role || 'role';
        const roleKey = canonicalizeRole(role);

        // Fetch existing settings or use defaults
        let existingSettings = null;
        try {
            existingSettings = await roleSettingsApi.get(roleKey);
        } catch (err) {
            log(`Failed to fetch role settings: ${err.message}`, 'warning');
        }

        // Initialize local state from existing settings or defaults
        const defaultTemplate = ROLE_TEMPLATES.balanced;
        const localState = {
            template: existingSettings?.template || 'balanced',
            freedomLevel: existingSettings?.freedomLevel || defaultTemplate.freedomLevel,
            notifyOn: {
                start: existingSettings?.notifyUserOn?.includes('start') ?? defaultTemplate.notifyOn.start,
                question: existingSettings?.notifyUserOn?.includes('question') ?? defaultTemplate.notifyOn.question,
                conflict: existingSettings?.notifyUserOn?.includes('conflict') ?? defaultTemplate.notifyOn.conflict,
                completion: existingSettings?.notifyUserOn?.includes('completion') ?? defaultTemplate.notifyOn.completion,
                error: existingSettings?.notifyUserOn?.includes('error') ?? defaultTemplate.notifyOn.error
            },
            maxActionsPerSession: existingSettings?.maxActionsPerSession ?? defaultTemplate.maxActionsPerSession,
            roleCharter: existingSettings?.roleCharter || DEFAULT_ROLE_CHARTERS[roleKey] || DEFAULT_ROLE_CHARTERS.default,
            collaborationGuidance: existingSettings?.collaborationGuidance || defaultTemplate.collaborationGuidance,
            toolAndSafetyNotes: existingSettings?.toolAndSafetyNotes || defaultTemplate.toolAndSafetyNotes
        };

        // Clone for dirty detection
        const originalState = JSON.stringify(localState);

        const { overlay, modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            `Role Settings: ${role}`,
            'Save',
            'Cancel',
            { closeOnCancel: false, closeOnConfirm: false }
        );

        modal.classList.add('role-settings-modal');

        // =============== TEMPLATE SELECTOR ===============
        const templateSection = document.createElement('div');
        templateSection.className = 'modal-section';

        const templateLabel = document.createElement('label');
        templateLabel.className = 'modal-label';
        templateLabel.textContent = 'Behavior Template';
        templateSection.appendChild(templateLabel);

        const templateGrid = document.createElement('div');
        templateGrid.className = 'role-template-grid';

        const templateButtons = {};
        ['autonomous', 'balanced', 'verbose', 'custom'].forEach(templateKey => {
            const tmpl = ROLE_TEMPLATES[templateKey];
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'role-template-btn';
            btn.dataset.template = templateKey;
            btn.innerHTML = `
                <span class="template-name">${tmpl.label}</span>
                <span class="template-desc">${tmpl.description || ''}</span>
            `;
            if (localState.template === templateKey) {
                btn.classList.add('selected');
            }
            btn.addEventListener('click', () => applyTemplate(templateKey));
            templateGrid.appendChild(btn);
            templateButtons[templateKey] = btn;
        });
        templateSection.appendChild(templateGrid);
        body.appendChild(templateSection);

        // =============== FREEDOM LEVEL ===============
        const freedomSection = document.createElement('div');
        freedomSection.className = 'modal-section';

        const freedomLabel = document.createElement('label');
        freedomLabel.className = 'modal-label';
        freedomLabel.textContent = 'Freedom Level';
        freedomSection.appendChild(freedomLabel);

        const freedomSelect = document.createElement('select');
        freedomSelect.className = 'modal-select';
        freedomSelect.innerHTML = `
            <option value="supervised">Supervised - Requires approval for most actions</option>
            <option value="semi-autonomous">Semi-Autonomous - Independent within guidelines</option>
            <option value="autonomous">Autonomous - Full independence, minimal check-ins</option>
        `;
        freedomSelect.value = localState.freedomLevel;
        freedomSelect.addEventListener('change', () => {
            localState.freedomLevel = freedomSelect.value;
            markCustom();
        });
        freedomSection.appendChild(freedomSelect);
        body.appendChild(freedomSection);

        // =============== NOTIFICATIONS ===============
        const notifySection = document.createElement('div');
        notifySection.className = 'modal-section';

        const notifyLabel = document.createElement('label');
        notifyLabel.className = 'modal-label';
        notifyLabel.textContent = 'Notify User On';
        notifySection.appendChild(notifyLabel);

        const notifyGrid = document.createElement('div');
        notifyGrid.className = 'notify-checkbox-grid';

        const notifyCheckboxes = {};
        const notifyOptions = [
            { key: 'start', label: 'Task Start' },
            { key: 'question', label: 'Questions' },
            { key: 'conflict', label: 'Conflicts' },
            { key: 'completion', label: 'Completion' },
            { key: 'error', label: 'Errors' }
        ];

        notifyOptions.forEach(opt => {
            const wrapper = document.createElement('label');
            wrapper.className = 'modal-checkbox-row';

            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = localState.notifyOn[opt.key];
            checkbox.addEventListener('change', () => {
                localState.notifyOn[opt.key] = checkbox.checked;
                markCustom();
            });

            const text = document.createElement('span');
            text.textContent = opt.label;

            wrapper.appendChild(checkbox);
            wrapper.appendChild(text);
            notifyGrid.appendChild(wrapper);
            notifyCheckboxes[opt.key] = checkbox;
        });
        notifySection.appendChild(notifyGrid);
        body.appendChild(notifySection);

        // =============== MAX ACTIONS ===============
        const actionsSection = document.createElement('div');
        actionsSection.className = 'modal-section';

        const actionsLabel = document.createElement('label');
        actionsLabel.className = 'modal-label';
        actionsLabel.textContent = 'Max Actions Per Session';
        actionsSection.appendChild(actionsLabel);

        const actionsRow = document.createElement('div');
        actionsRow.className = 'actions-input-row';

        const actionsInput = document.createElement('input');
        actionsInput.type = 'number';
        actionsInput.className = 'modal-input actions-input';
        actionsInput.min = '1';
        actionsInput.max = '100';
        actionsInput.value = localState.maxActionsPerSession ?? '';
        actionsInput.placeholder = 'Unlimited';
        actionsInput.addEventListener('input', () => {
            const val = actionsInput.value.trim();
            localState.maxActionsPerSession = val ? parseInt(val, 10) : null;
            markCustom();
        });

        const unlimitedBtn = document.createElement('button');
        unlimitedBtn.type = 'button';
        unlimitedBtn.className = 'modal-btn modal-btn-secondary';
        unlimitedBtn.textContent = 'Unlimited';
        unlimitedBtn.addEventListener('click', () => {
            actionsInput.value = '';
            localState.maxActionsPerSession = null;
            markCustom();
        });

        actionsRow.appendChild(actionsInput);
        actionsRow.appendChild(unlimitedBtn);
        actionsSection.appendChild(actionsRow);
        body.appendChild(actionsSection);

        // =============== ROLE CHARTER ===============
        const charterSection = document.createElement('div');
        charterSection.className = 'modal-section';

        const charterLabel = document.createElement('label');
        charterLabel.className = 'modal-label';
        charterLabel.textContent = 'Role Charter (Job Description)';
        charterSection.appendChild(charterLabel);

        const charterTextarea = document.createElement('textarea');
        charterTextarea.className = 'modal-textarea';
        charterTextarea.rows = 3;
        charterTextarea.placeholder = 'Describe this role\'s purpose and responsibilities...';
        charterTextarea.value = localState.roleCharter;
        charterTextarea.addEventListener('input', () => {
            localState.roleCharter = charterTextarea.value;
            markCustom();
        });
        charterSection.appendChild(charterTextarea);
        body.appendChild(charterSection);

        // =============== COLLABORATION GUIDANCE ===============
        const collabSection = document.createElement('div');
        collabSection.className = 'modal-section';

        const collabLabel = document.createElement('label');
        collabLabel.className = 'modal-label';
        collabLabel.textContent = 'Collaboration Guidance';
        collabSection.appendChild(collabLabel);

        const collabTextarea = document.createElement('textarea');
        collabTextarea.className = 'modal-textarea';
        collabTextarea.rows = 3;
        collabTextarea.placeholder = 'How should this role collaborate with others and escalate issues...';
        collabTextarea.value = localState.collaborationGuidance;
        collabTextarea.addEventListener('input', () => {
            localState.collaborationGuidance = collabTextarea.value;
            markCustom();
        });
        collabSection.appendChild(collabTextarea);
        body.appendChild(collabSection);

        // =============== TOOL & SAFETY NOTES ===============
        const safetySection = document.createElement('div');
        safetySection.className = 'modal-section';

        const safetyLabel = document.createElement('label');
        safetyLabel.className = 'modal-label';
        safetyLabel.textContent = 'Tool & Safety Notes';
        safetySection.appendChild(safetyLabel);

        const safetyTextarea = document.createElement('textarea');
        safetyTextarea.className = 'modal-textarea';
        safetyTextarea.rows = 2;
        safetyTextarea.placeholder = 'Tool preferences and safety constraints...';
        safetyTextarea.value = localState.toolAndSafetyNotes;
        safetyTextarea.addEventListener('input', () => {
            localState.toolAndSafetyNotes = safetyTextarea.value;
            markCustom();
        });
        safetySection.appendChild(safetyTextarea);
        body.appendChild(safetySection);

        // =============== ERROR HINT ===============
        const errorHint = document.createElement('div');
        errorHint.className = 'modal-hint modal-error-hint';
        errorHint.style.display = 'none';
        body.appendChild(errorHint);

        // =============== HELPER FUNCTIONS ===============
        function markCustom() {
            if (localState.template !== 'custom') {
                localState.template = 'custom';
                updateTemplateSelection();
            }
        }

        function updateTemplateSelection() {
            Object.entries(templateButtons).forEach(([key, btn]) => {
                btn.classList.toggle('selected', key === localState.template);
            });
        }

        function applyTemplate(templateKey) {
            localState.template = templateKey;
            updateTemplateSelection();

            if (templateKey === 'custom') return;

            const tmpl = ROLE_TEMPLATES[templateKey];
            if (!tmpl) return;

            // Apply template values to form
            localState.freedomLevel = tmpl.freedomLevel;
            freedomSelect.value = tmpl.freedomLevel;

            if (tmpl.notifyOn) {
                Object.entries(tmpl.notifyOn).forEach(([key, val]) => {
                    localState.notifyOn[key] = val;
                    if (notifyCheckboxes[key]) {
                        notifyCheckboxes[key].checked = val;
                    }
                });
            }

            localState.maxActionsPerSession = tmpl.maxActionsPerSession;
            actionsInput.value = tmpl.maxActionsPerSession ?? '';

            if (tmpl.collaborationGuidance) {
                localState.collaborationGuidance = tmpl.collaborationGuidance;
                collabTextarea.value = tmpl.collaborationGuidance;
            }

            if (tmpl.toolAndSafetyNotes) {
                localState.toolAndSafetyNotes = tmpl.toolAndSafetyNotes;
                safetyTextarea.value = tmpl.toolAndSafetyNotes;
            }
        }

        function isDirty() {
            return JSON.stringify(localState) !== originalState;
        }

        // =============== CANCEL HANDLER ===============
        cancelBtn.addEventListener('click', () => {
            if (isDirty()) {
                if (confirm('Discard unsaved changes?')) {
                    close();
                }
            } else {
                close();
            }
        });

        // =============== SAVE HANDLER ===============
        confirmBtn.addEventListener('click', async () => {
            confirmBtn.disabled = true;
            confirmBtn.textContent = 'Saving...';
            errorHint.style.display = 'none';

            // Validate maxActionsPerSession
            if (localState.maxActionsPerSession !== null &&
                (isNaN(localState.maxActionsPerSession) || localState.maxActionsPerSession < 1)) {
                errorHint.textContent = 'Max actions must be a positive number or unlimited.';
                errorHint.style.display = 'block';
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Save';
                return;
            }

            // Build notifyUserOn array from checkboxes
            const notifyUserOn = Object.entries(localState.notifyOn)
                .filter(([_, checked]) => checked)
                .map(([key]) => key);

            const payload = {
                role: roleKey,
                template: localState.template,
                freedomLevel: localState.freedomLevel,
                notifyUserOn,
                maxActionsPerSession: localState.maxActionsPerSession,
                requireApprovalFor: [],
                roleCharter: localState.roleCharter,
                collaborationGuidance: localState.collaborationGuidance,
                toolAndSafetyNotes: localState.toolAndSafetyNotes
            };

            try {
                await roleSettingsApi.save(roleKey, payload);
                notificationStore.success(`Role settings saved for "${role}"`, 'workbench');
                close();
            } catch (err) {
                errorHint.textContent = err.message || 'Failed to save role settings.';
                errorHint.style.display = 'block';
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Save';
            }
        });
    }

    function showAgentSettingsModal(agent, options = {}) {
        const allowDraft = Boolean(options.allowDraft);
        const onSaveDraft = typeof options.onSave === 'function' ? options.onSave : null;
        const onCloseDraft = typeof options.onClose === 'function' ? options.onClose : null;
        const applyPresetWhenEmpty = options.applyRolePresetWhenEmpty !== false;
        const name = agent?.name || 'Agent';
        const agentId = agent?.id;
        const { overlay, modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            `Agent Settings: ${name}`,
            'Save',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: false }
        );
        const closeWithCallback = (result = {}) => {
            close();
            if (onCloseDraft) {
                onCloseDraft(result);
            }
        };

        modal.classList.add('agent-settings-modal');

        const providerOptions = [
            'openai', 'anthropic', 'gemini', 'grok', 'openrouter', 'nanogpt', 'togetherai',
            'lmstudio', 'ollama', 'jan', 'koboldcpp', 'custom'
        ];

        const defaultBaseUrls = {
            openai: 'https://api.openai.com',
            anthropic: 'https://api.anthropic.com',
            gemini: 'https://generativelanguage.googleapis.com',
            grok: 'https://api.x.ai/v1',
            openrouter: 'https://openrouter.ai',
            nanogpt: 'https://nano-gpt.com/api/v1',
            togetherai: 'https://api.together.xyz',
            lmstudio: 'http://localhost:1234',
            ollama: 'http://localhost:11434',
            jan: 'http://localhost:1234',
            koboldcpp: 'http://localhost:1234',
            custom: ''
        };

        const formState = {
            provider: 'anthropic',
            model: '',
            keyRef: '',
            baseUrl: '',
            temperature: '',
            topP: '',
            topK: '',
            minP: '',
            repeatPenalty: '',
            maxOutputTokens: '',
            useProviderDefaults: false,
            nanoGptLegacy: false
        };

        let security = { keysSecurityMode: 'plaintext', vaultUnlocked: true };
        let keyMetadata = { providers: {} };
        let modelList = [];
        let isLoadingModels = false;
        let baseUrlTouched = false;
        let initialEndpointSnapshot = { provider: '', model: '', keyRef: '' };
        let initialWired = false;

        const errorHint = document.createElement('div');
        errorHint.className = 'modal-hint modal-error-hint';
        errorHint.style.display = 'none';
        const modelHint = document.createElement('div');
        modelHint.className = 'modal-hint';
        modelHint.style.display = 'none';
        const localActions = document.createElement('div');
        localActions.className = 'agent-settings-inline';
        localActions.style.display = 'none';
        const localHelp = document.createElement('button');
        localHelp.type = 'button';
        localHelp.className = 'modal-btn modal-btn-secondary';
        localHelp.textContent = 'Local provider not running?';
        const localRetry = document.createElement('button');
        localRetry.type = 'button';
        localRetry.className = 'modal-btn modal-btn-secondary';
        localRetry.textContent = 'Retry';
        localActions.appendChild(localHelp);
        localActions.appendChild(localRetry);

        const content = document.createElement('div');
        content.className = 'agent-settings-body';
        body.appendChild(content);
        body.appendChild(modelHint);
        body.appendChild(localActions);
        body.appendChild(errorHint);

        const vaultSection = document.createElement('div');
        vaultSection.className = 'agent-settings-vault';

        const vaultLabel = document.createElement('div');
        vaultLabel.className = 'modal-text';
        vaultLabel.textContent = 'Key vault locked. Enter password to unlock stored keys.';
        const vaultRow = document.createElement('div');
        vaultRow.className = 'agent-settings-inline';
        const vaultInput = document.createElement('input');
        vaultInput.type = 'password';
        vaultInput.className = 'modal-input';
        vaultInput.placeholder = 'Vault password';
        const vaultBtn = document.createElement('button');
        vaultBtn.type = 'button';
        vaultBtn.className = 'modal-btn modal-btn-secondary';
        vaultBtn.textContent = 'Unlock';
        vaultRow.appendChild(vaultInput);
        vaultRow.appendChild(vaultBtn);
        vaultSection.appendChild(vaultLabel);
        vaultSection.appendChild(vaultRow);

        const providerRow = document.createElement('div');
        providerRow.className = 'modal-row';
        const providerLabel = document.createElement('label');
        providerLabel.className = 'modal-label';
        providerLabel.textContent = 'Provider';
        const providerSelect = document.createElement('select');
        providerSelect.className = 'modal-select';
        providerOptions.forEach(provider => {
            const option = document.createElement('option');
            option.value = provider;
            option.textContent = provider;
            providerSelect.appendChild(option);
        });
        providerRow.appendChild(providerLabel);
        providerRow.appendChild(providerSelect);

        const keyRow = document.createElement('div');
        keyRow.className = 'modal-row';
        const keyLabel = document.createElement('label');
        keyLabel.className = 'modal-label';
        keyLabel.textContent = 'API Key';
        const keyInline = document.createElement('div');
        keyInline.className = 'agent-settings-inline';
        const keySelect = document.createElement('select');
        keySelect.className = 'modal-select';
        const keyAddBtn = document.createElement('button');
        keyAddBtn.type = 'button';
        keyAddBtn.className = 'modal-btn modal-btn-secondary';
        keyAddBtn.textContent = 'Add';
        keyInline.appendChild(keySelect);
        keyInline.appendChild(keyAddBtn);
        keyRow.appendChild(keyLabel);
        keyRow.appendChild(keyInline);

        const addKeySection = document.createElement('div');
        addKeySection.className = 'agent-settings-add-key';
        addKeySection.style.display = 'none';
        const addKeyLabel = document.createElement('label');
        addKeyLabel.className = 'modal-label';
        addKeyLabel.textContent = 'New Key';
        const addKeyNameRow = document.createElement('div');
        addKeyNameRow.className = 'agent-settings-inline';
        const addKeyNameInput = document.createElement('input');
        addKeyNameInput.type = 'text';
        addKeyNameInput.className = 'modal-input';
        addKeyNameInput.placeholder = 'Label (optional)';
        const addKeyValueInput = document.createElement('input');
        addKeyValueInput.type = 'password';
        addKeyValueInput.className = 'modal-input';
        addKeyValueInput.placeholder = 'Paste API key';
        addKeyNameRow.appendChild(addKeyNameInput);
        addKeyNameRow.appendChild(addKeyValueInput);

        const addKeyActions = document.createElement('div');
        addKeyActions.className = 'agent-settings-inline';
        const addKeySaveBtn = document.createElement('button');
        addKeySaveBtn.type = 'button';
        addKeySaveBtn.className = 'modal-btn modal-btn-primary';
        addKeySaveBtn.textContent = 'Save Key';
        const addKeyCancelBtn = document.createElement('button');
        addKeyCancelBtn.type = 'button';
        addKeyCancelBtn.className = 'modal-btn modal-btn-secondary';
        addKeyCancelBtn.textContent = 'Cancel';
        addKeyActions.appendChild(addKeyCancelBtn);
        addKeyActions.appendChild(addKeySaveBtn);

        addKeySection.appendChild(addKeyLabel);
        addKeySection.appendChild(addKeyNameRow);
        addKeySection.appendChild(addKeyActions);

        const modelRow = document.createElement('div');
        modelRow.className = 'modal-row';
        const modelLabel = document.createElement('label');
        modelLabel.className = 'modal-label';
        modelLabel.textContent = 'Model';
        const modelSearch = document.createElement('input');
        modelSearch.type = 'text';
        modelSearch.className = 'modal-input';
        modelSearch.placeholder = 'Search models...';
        const modelListRow = document.createElement('div');
        modelListRow.className = 'agent-settings-inline';
        const modelSelect = document.createElement('select');
        modelSelect.className = 'modal-select';
        const modelRefreshBtn = document.createElement('button');
        modelRefreshBtn.type = 'button';
        modelRefreshBtn.className = 'modal-btn modal-btn-secondary';
        modelRefreshBtn.textContent = 'Refresh';
        modelListRow.appendChild(modelSelect);
        modelListRow.appendChild(modelRefreshBtn);
        modelRow.appendChild(modelLabel);
        modelRow.appendChild(modelSearch);
        modelRow.appendChild(modelListRow);

        const baseRow = document.createElement('div');
        baseRow.className = 'modal-row';
        const baseLabel = document.createElement('label');
        baseLabel.className = 'modal-label';
        baseLabel.textContent = 'Base URL';
        const baseInput = document.createElement('input');
        baseInput.type = 'text';
        baseInput.className = 'modal-input';
        baseInput.placeholder = 'Leave blank to use provider default';
        baseRow.appendChild(baseLabel);
        baseRow.appendChild(baseInput);

        const nanoLegacyRow = document.createElement('label');
        nanoLegacyRow.className = 'modal-checkbox-row';
        const nanoLegacyToggle = document.createElement('input');
        nanoLegacyToggle.type = 'checkbox';
        const nanoLegacyText = document.createElement('span');
        nanoLegacyText.textContent = 'Use NanoGPT legacy API path (/api/v1legacy)';
        nanoLegacyRow.appendChild(nanoLegacyToggle);
        nanoLegacyRow.appendChild(nanoLegacyText);

        const tempRow = document.createElement('div');
        tempRow.className = 'modal-row';
        const tempLabel = document.createElement('label');
        tempLabel.className = 'modal-label';
        tempLabel.textContent = 'Temperature';
        const tempInput = document.createElement('input');
        tempInput.type = 'number';
        tempInput.step = '0.1';
        tempInput.min = '0';
        tempInput.max = '2';
        tempInput.className = 'modal-input';
        tempInput.placeholder = 'Role preset';
        tempRow.appendChild(tempLabel);
        tempRow.appendChild(tempInput);

        const topPRow = document.createElement('div');
        topPRow.className = 'modal-row';
        const topPLabel = document.createElement('label');
        topPLabel.className = 'modal-label';
        topPLabel.textContent = 'Top P';
        const topPInput = document.createElement('input');
        topPInput.type = 'number';
        topPInput.step = '0.01';
        topPInput.min = '0';
        topPInput.max = '1';
        topPInput.className = 'modal-input';
        topPInput.placeholder = 'Role preset';
        topPRow.appendChild(topPLabel);
        topPRow.appendChild(topPInput);

        const topKRow = document.createElement('div');
        topKRow.className = 'modal-row';
        const topKLabel = document.createElement('label');
        topKLabel.className = 'modal-label';
        topKLabel.textContent = 'Top K';
        const topKInput = document.createElement('input');
        topKInput.type = 'number';
        topKInput.min = '0';
        topKInput.className = 'modal-input';
        topKInput.placeholder = 'Role preset';
        topKRow.appendChild(topKLabel);
        topKRow.appendChild(topKInput);

        const minPRow = document.createElement('div');
        minPRow.className = 'modal-row';
        const minPLabel = document.createElement('label');
        minPLabel.className = 'modal-label';
        minPLabel.textContent = 'Min P';
        const minPInput = document.createElement('input');
        minPInput.type = 'number';
        minPInput.step = '0.01';
        minPInput.min = '0';
        minPInput.max = '1';
        minPInput.className = 'modal-input';
        minPInput.placeholder = 'Role preset';
        minPRow.appendChild(minPLabel);
        minPRow.appendChild(minPInput);

        const repeatRow = document.createElement('div');
        repeatRow.className = 'modal-row';
        const repeatLabel = document.createElement('label');
        repeatLabel.className = 'modal-label';
        repeatLabel.textContent = 'Repeat Penalty';
        const repeatInput = document.createElement('input');
        repeatInput.type = 'number';
        repeatInput.step = '0.01';
        repeatInput.min = '0.5';
        repeatInput.max = '2';
        repeatInput.className = 'modal-input';
        repeatInput.placeholder = 'Role preset';
        repeatRow.appendChild(repeatLabel);
        repeatRow.appendChild(repeatInput);

        const tokenRow = document.createElement('div');
        tokenRow.className = 'modal-row';
        const tokenLabel = document.createElement('label');
        tokenLabel.className = 'modal-label';
        tokenLabel.textContent = 'Max Output Tokens';
        const tokenInput = document.createElement('input');
        tokenInput.type = 'number';
        tokenInput.min = '1';
        tokenInput.className = 'modal-input';
        tokenInput.placeholder = 'Role preset';
        tokenRow.appendChild(tokenLabel);
        tokenRow.appendChild(tokenInput);

        const defaultsRow = document.createElement('label');
        defaultsRow.className = 'modal-checkbox-row';
        const defaultsToggle = document.createElement('input');
        defaultsToggle.type = 'checkbox';
        const defaultsText = document.createElement('span');
        defaultsText.textContent = 'Use provider defaults (ignore role presets)';
        defaultsRow.appendChild(defaultsToggle);
        defaultsRow.appendChild(defaultsText);

        content.appendChild(providerRow);
        content.appendChild(keyRow);
        content.appendChild(addKeySection);
        content.appendChild(modelRow);

        const advancedSection = document.createElement('div');
        advancedSection.className = 'agent-settings-advanced';
        const advancedToggle = document.createElement('button');
        advancedToggle.type = 'button';
        advancedToggle.className = 'agent-settings-advanced-toggle';
        advancedToggle.textContent = 'Advanced settings';
        const advancedBody = document.createElement('div');
        advancedBody.className = 'agent-settings-advanced-body';
        advancedBody.style.display = 'none';
        advancedBody.appendChild(baseRow);
        advancedBody.appendChild(nanoLegacyRow);
        advancedBody.appendChild(defaultsRow);
        advancedBody.appendChild(tempRow);
        advancedBody.appendChild(topPRow);
        advancedBody.appendChild(topKRow);
        advancedBody.appendChild(minPRow);
        advancedBody.appendChild(repeatRow);
        advancedBody.appendChild(tokenRow);
        advancedSection.appendChild(advancedToggle);
        advancedSection.appendChild(advancedBody);
        content.appendChild(advancedSection);

        const renderNanoLegacy = () => {
            const isNano = formState.provider === 'nanogpt';
            nanoLegacyRow.style.display = isNano ? 'flex' : 'none';
        };

        const updateLocalActions = () => {
            const isLocal = window.LOCAL_PROVIDERS.has(formState.provider);
            localActions.style.display = isLocal ? 'flex' : 'none';
        };

        const updateSamplingState = () => {
            const disabled = formState.useProviderDefaults === true;
            [tempInput, topPInput, topKInput, minPInput, repeatInput, tokenInput].forEach(input => {
                if (input) {
                    input.disabled = disabled;
                }
            });
        };

        const updateLocalHint = (message = '') => {
            const isLocal = window.LOCAL_PROVIDERS.has(formState.provider);
            if (!isLocal) {
                modelHint.style.display = 'none';
                return;
            }
            if (message) {
                modelHint.textContent = message;
                modelHint.style.display = 'block';
                return;
            }
            const base = (formState.baseUrl || defaultBaseUrls[formState.provider] || '').replace(/\/+$/, '');
            const path = formState.provider === 'ollama' ? '/api/tags' : '/v1/models';
            if (base) {
                modelHint.textContent = `Local provider will be queried at ${base}${path}.`;
                modelHint.style.display = 'block';
            } else {
                modelHint.textContent = 'Set a Base URL in Advanced to reach your local provider.';
                modelHint.style.display = 'block';
            }
        };

        const updateVaultSection = () => {
            if (security.keysSecurityMode === 'encrypted' && !security.vaultUnlocked) {
                if (!vaultSection.parentNode) {
                    content.insertBefore(vaultSection, content.firstChild);
                }
            } else if (vaultSection.parentNode) {
                vaultSection.remove();
            }
        };

        const updateKeySelect = () => {
            keySelect.innerHTML = '';
            const provider = formState.provider;
            const keys = (keyMetadata.providers && keyMetadata.providers[provider]) || [];
            const emptyOption = document.createElement('option');
            emptyOption.value = '';
            emptyOption.textContent = keys.length ? 'Select a key' : 'No saved keys';
            keySelect.appendChild(emptyOption);

            keys.forEach(entry => {
                const option = document.createElement('option');
                option.value = `${provider}:${entry.id}`;
                option.textContent = entry.label || entry.id;
                keySelect.appendChild(option);
            });
            const expectedPrefix = `${provider}:`;
            if (formState.keyRef && !formState.keyRef.startsWith(expectedPrefix)) {
                formState.keyRef = '';
            }
            keySelect.value = formState.keyRef || '';
        };

        const updateModelSelect = () => {
            modelSelect.innerHTML = '';
            if (isLoadingModels) {
                const option = document.createElement('option');
                option.value = '';
                option.textContent = 'Loading models...';
                modelSelect.appendChild(option);
                modelSelect.disabled = true;
                return;
            }
            if (!modelList.length) {
                const option = document.createElement('option');
                option.value = '';
                option.textContent = 'No models loaded';
                modelSelect.appendChild(option);
                modelSelect.disabled = true;
                return;
            }
            modelSelect.disabled = false;
            const blank = document.createElement('option');
            blank.value = '';
            blank.textContent = 'Select a model';
            modelSelect.appendChild(blank);
            const query = modelSearch.value.trim().toLowerCase();
            modelList.forEach(model => {
                const name = (model.name || model.id || '').toLowerCase();
                if (query && !name.includes(query) && !String(model.id || '').toLowerCase().includes(query)) {
                    return;
                }
                const option = document.createElement('option');
                option.value = model.id;
                option.textContent = model.name || model.id;
                if (model.recommended) {
                    option.textContent += ' ★';
                }
                modelSelect.appendChild(option);
            });
            modelSelect.value = modelList.find(item => item.id === formState.model) ? formState.model : '';
        };

        const updateBaseUrlPlaceholder = () => {
            const provider = formState.provider;
            baseInput.placeholder = defaultBaseUrls[provider] || 'Leave blank to use provider default';
        };

        const updateKeyVisibility = () => {
            const provider = formState.provider;
            if (window.LOCAL_PROVIDERS.has(provider)) {
                keyRow.style.display = 'none';
                addKeySection.style.display = 'none';
                formState.keyRef = '';
            } else {
                keyRow.style.display = 'flex';
            }
        };

        const computeNanoBase = (legacy) => legacy
            ? 'https://nano-gpt.com/api/v1legacy'
            : 'https://nano-gpt.com/api/v1';

        const applyNanoLegacy = (enabled) => {
            formState.nanoGptLegacy = enabled;
            nanoLegacyToggle.checked = enabled;
            const current = (formState.baseUrl || '').trim();
            if (!current || current.startsWith('https://nano-gpt.com/api/v1')) {
                const next = computeNanoBase(enabled);
                formState.baseUrl = next;
                baseInput.value = next;
                baseUrlTouched = true;
            }
        };

        const refreshModels = async () => {
            if (!formState.provider) return;
            isLoadingModels = true;
            updateModelSelect();
            errorHint.style.display = 'none';
            updateLocalHint();
            try {
                const baseUrl = baseUrlTouched ? formState.baseUrl : '';
                const models = await providerApi.listModels(formState.provider, baseUrl, formState.keyRef);
                modelList = Array.isArray(models) ? models : [];
                if (window.LOCAL_PROVIDERS.has(formState.provider)) {
                    updateLocalHint();
                } else {
                    modelHint.style.display = 'none';
                }
            } catch (err) {
                const message = err.message || 'Failed to fetch models.';
                const isLocal = window.LOCAL_PROVIDERS.has(formState.provider);
                if (String(message).toLowerCase().includes('vault')) {
                    security.vaultUnlocked = false;
                    updateVaultSection();
                }
                if (isLocal) {
                    updateLocalHint(message);
                } else {
                    errorHint.textContent = message;
                    errorHint.style.display = 'block';
                }
                modelList = [];
            } finally {
                isLoadingModels = false;
                updateModelSelect();
            }
        };

        const loadInitialData = async () => {
            try {
                security = await settingsApi.getSecurity();
            } catch (err) {
                log(`Failed to load security settings: ${err.message}`, 'warning');
            }

            try {
                const keysResponse = await settingsApi.listKeys();
                if (keysResponse && keysResponse.providers) {
                    keyMetadata = keysResponse;
                }
            } catch (err) {
                log(`Failed to load API keys: ${err.message}`, 'warning');
            }

            if (agentId) {
                try {
                    const endpoint = await agentEndpointsApi.get(agentId);
                    if (endpoint) {
                        formState.provider = endpoint.provider || formState.provider;
                        formState.model = endpoint.model || '';
                        formState.keyRef = endpoint.apiKeyRef || '';
                        formState.baseUrl = endpoint.baseUrl || '';
                        formState.temperature = endpoint.temperature ?? '';
                        formState.topP = endpoint.topP ?? '';
                        formState.topK = endpoint.topK ?? '';
                        formState.minP = endpoint.minP ?? '';
                        formState.repeatPenalty = endpoint.repeatPenalty ?? '';
                        formState.maxOutputTokens = endpoint.maxOutputTokens ?? '';
                        formState.useProviderDefaults = endpoint.useProviderDefaults ?? false;
                    }
                } catch (_) {
                    if (agent?.endpoint) {
                        formState.provider = agent.endpoint.provider || formState.provider;
                        formState.model = agent.endpoint.model || '';
                        formState.baseUrl = agent.endpoint.baseUrl || '';
                        formState.temperature = agent.endpoint.temperature ?? '';
                        formState.topP = agent.endpoint.topP ?? '';
                        formState.topK = agent.endpoint.topK ?? '';
                        formState.minP = agent.endpoint.minP ?? '';
                        formState.repeatPenalty = agent.endpoint.repeatPenalty ?? '';
                        formState.maxOutputTokens = agent.endpoint.maxOutputTokens ?? '';
                        formState.useProviderDefaults = agent.endpoint.useProviderDefaults ?? false;
                    }
                }
            } else if (allowDraft && options.initialEndpoint) {
                const endpoint = options.initialEndpoint;
                formState.provider = endpoint.provider || formState.provider;
                formState.model = endpoint.model || '';
                formState.keyRef = endpoint.apiKeyRef || endpoint.keyRef || '';
                formState.baseUrl = endpoint.baseUrl || '';
                formState.temperature = endpoint.temperature ?? '';
                formState.topP = endpoint.topP ?? '';
                formState.topK = endpoint.topK ?? '';
                formState.minP = endpoint.minP ?? '';
                formState.repeatPenalty = endpoint.repeatPenalty ?? '';
                formState.maxOutputTokens = endpoint.maxOutputTokens ?? '';
                formState.useProviderDefaults = endpoint.useProviderDefaults ?? false;
            }

            if (formState.provider === 'nanogpt') {
                formState.nanoGptLegacy = (formState.baseUrl || '').includes('/api/v1legacy');
                if (!formState.baseUrl) {
                    formState.baseUrl = computeNanoBase(formState.nanoGptLegacy);
                }
            }
            if (formState.baseUrl) {
                baseUrlTouched = true;
            }

            if (applyPresetWhenEmpty) {
                applyRoleEndpointPreset(formState, agent?.role, { force: false });
            }

            initialEndpointSnapshot = {
                provider: formState.provider,
                model: formState.model,
                keyRef: formState.keyRef
            };
            initialWired = window.isEndpointWired(initialEndpointSnapshot);

            providerSelect.value = formState.provider;
            modelSearch.value = '';
            baseInput.value = formState.baseUrl;
            tempInput.value = formState.temperature;
            topPInput.value = formState.topP;
            topKInput.value = formState.topK;
            minPInput.value = formState.minP;
            repeatInput.value = formState.repeatPenalty;
            tokenInput.value = formState.maxOutputTokens;
            defaultsToggle.checked = formState.useProviderDefaults;
            nanoLegacyToggle.checked = formState.nanoGptLegacy;
            const advancedPref = localStorage.getItem('control-room:agent-settings-advanced');
            if (advancedPref === 'open') {
                advancedBody.style.display = 'flex';
                advancedToggle.textContent = 'Hide advanced';
            }

            updateSamplingState();
            updateVaultSection();
            updateKeySelect();
            updateKeyVisibility();
            updateBaseUrlPlaceholder();
            renderNanoLegacy();
            updateLocalActions();
            await refreshModels();
        };

        const normalizeBaseUrl = (value) => (value || '').trim().replace(/\/+$/, '').toLowerCase();
        const looksLikeProviderDefault = (value, provider) => {
            const candidate = normalizeBaseUrl(value);
            const fallback = normalizeBaseUrl(defaultBaseUrls[provider]);
            if (!candidate || !fallback) return false;
            if (candidate === fallback) return true;
            const variants = [
                `${fallback}/v1`,
                `${fallback}/api/v1`,
                `${fallback}/api/v1/models`,
                `${fallback}/api/v1legacy`
            ];
            return variants.includes(candidate);
        };

        providerSelect.addEventListener('change', () => {
            const previousProvider = formState.provider;
            formState.provider = providerSelect.value;
            if (formState.baseUrl && looksLikeProviderDefault(formState.baseUrl, previousProvider)) {
                formState.baseUrl = '';
                baseInput.value = '';
                baseUrlTouched = false;
            } else if (formState.baseUrl) {
                const isKnownDefault = Object.keys(defaultBaseUrls)
                    .some(provider => looksLikeProviderDefault(formState.baseUrl, provider));
                if (isKnownDefault) {
                    formState.baseUrl = '';
                    baseInput.value = '';
                    baseUrlTouched = false;
                }
            }
            updateKeyVisibility();
            if (formState.keyRef && !formState.keyRef.startsWith(`${formState.provider}:`)) {
                formState.keyRef = '';
            }
            if (formState.provider === 'nanogpt') {
                applyNanoLegacy(formState.nanoGptLegacy);
            }
            updateKeySelect();
            updateBaseUrlPlaceholder();
            renderNanoLegacy();
            updateLocalActions();
            updateLocalHint();
            refreshModels();
        });

        keySelect.addEventListener('change', () => {
            formState.keyRef = keySelect.value;
            refreshModels();
        });

        keyAddBtn.addEventListener('click', () => {
            addKeySection.style.display = 'flex';
            addKeySection.classList.add('visible');
            addKeyNameInput.value = '';
            addKeyValueInput.value = '';
        });

        addKeyCancelBtn.addEventListener('click', () => {
            addKeySection.style.display = 'none';
        });

        addKeySaveBtn.addEventListener('click', async () => {
            errorHint.style.display = 'none';
            try {
                if (security.keysSecurityMode === 'encrypted' && !security.vaultUnlocked) {
                    errorHint.textContent = 'Unlock the key vault before adding a key.';
                    errorHint.style.display = 'block';
                    return;
                }
                const payload = {
                    provider: formState.provider,
                    label: addKeyNameInput.value.trim(),
                    key: addKeyValueInput.value.trim()
                };
                const result = await settingsApi.addKey(payload);
                if (result && result.keyRef) {
                    formState.keyRef = result.keyRef;
                }
                const keysResponse = await settingsApi.listKeys();
                if (keysResponse && keysResponse.providers) {
                    keyMetadata = keysResponse;
                }
                updateKeySelect();
                addKeySection.style.display = 'none';
                await refreshModels();
            } catch (err) {
                errorHint.textContent = err.message || 'Failed to save key.';
                errorHint.style.display = 'block';
            }
        });

        modelSelect.addEventListener('change', () => {
            if (modelSelect.value) {
                formState.model = modelSelect.value;
            }
        });

        modelSearch.addEventListener('input', updateModelSelect);

        modelRefreshBtn.addEventListener('click', () => {
            refreshModels();
        });

        baseInput.addEventListener('input', () => {
            formState.baseUrl = baseInput.value.trim();
            baseUrlTouched = true;
            updateLocalHint();
        });

        nanoLegacyToggle.addEventListener('change', () => {
            applyNanoLegacy(nanoLegacyToggle.checked);
            refreshModels();
        });

        tempInput.addEventListener('input', () => {
            formState.temperature = tempInput.value;
        });

        topPInput.addEventListener('input', () => {
            formState.topP = topPInput.value;
        });

        topKInput.addEventListener('input', () => {
            formState.topK = topKInput.value;
        });

        minPInput.addEventListener('input', () => {
            formState.minP = minPInput.value;
        });

        repeatInput.addEventListener('input', () => {
            formState.repeatPenalty = repeatInput.value;
        });

        tokenInput.addEventListener('input', () => {
            formState.maxOutputTokens = tokenInput.value;
        });

        defaultsToggle.addEventListener('change', () => {
            formState.useProviderDefaults = defaultsToggle.checked;
            if (formState.useProviderDefaults) {
                updateSamplingState();
                return;
            }
            const applied = applyRoleEndpointPreset(formState, agent?.role, { force: false });
            if (applied) {
                tempInput.value = formState.temperature;
                topPInput.value = formState.topP;
                topKInput.value = formState.topK;
                minPInput.value = formState.minP;
                repeatInput.value = formState.repeatPenalty;
            }
            updateSamplingState();
        });

        vaultBtn.addEventListener('click', async () => {
            if (!vaultInput.value) return;
            errorHint.style.display = 'none';
            try {
                await settingsApi.unlockVault(vaultInput.value);
                security.vaultUnlocked = true;
                updateVaultSection();
                await refreshModels();
            } catch (err) {
                errorHint.textContent = err.message || 'Failed to unlock vault.';
                errorHint.style.display = 'block';
            } finally {
                vaultInput.value = '';
            }
        });

        localRetry.addEventListener('click', () => {
            refreshModels();
        });

        localHelp.addEventListener('click', () => {
            const provider = formState.provider;
            let message = 'Make sure your local provider is running and reachable.\n\n';
            if (provider === 'lmstudio') {
                message += 'Open LM Studio, load a model, and keep it running.\n';
                message += 'Default URL: http://localhost:1234';
            } else if (provider === 'ollama') {
                message += 'Start the Ollama service and pull a model.\n';
                message += 'Default URL: http://localhost:11434';
            } else if (provider === 'jan') {
                message += 'Start Jan, load a model, and enable the API server.\n';
                message += 'Default URL: http://localhost:1234';
            } else if (provider === 'koboldcpp') {
                message += 'Start KoboldCPP with the API server enabled.\n';
                message += 'Default URL: http://localhost:1234';
            } else {
                message += 'Check that your local server is running and that the Base URL is correct.';
            }
            alert(message);
        });

        confirmBtn.addEventListener('click', async () => {
            errorHint.style.display = 'none';
            if (!formState.provider || !formState.model) {
                errorHint.textContent = 'Provider and model are required.';
                errorHint.style.display = 'block';
                return;
            }
            if (window.PROVIDERS_REQUIRE_KEY.has(formState.provider) && !formState.keyRef) {
                errorHint.textContent = 'API key is required for this provider.';
                errorHint.style.display = 'block';
                return;
            }

            const payload = {
                provider: formState.provider,
                model: formState.model,
                apiKeyRef: formState.keyRef || null,
                baseUrl: formState.baseUrl || null,
                useProviderDefaults: formState.useProviderDefaults || false
            };

            const temperature = parseFloat(formState.temperature);
            if (!Number.isNaN(temperature)) {
                payload.temperature = temperature;
            }
            const topP = parseFloat(formState.topP);
            if (!Number.isNaN(topP)) {
                payload.topP = topP;
            }
            const topK = parseInt(formState.topK, 10);
            if (!Number.isNaN(topK)) {
                payload.topK = topK;
            }
            const minP = parseFloat(formState.minP);
            if (!Number.isNaN(minP)) {
                payload.minP = minP;
            }
            const repeatPenalty = parseFloat(formState.repeatPenalty);
            if (!Number.isNaN(repeatPenalty)) {
                payload.repeatPenalty = repeatPenalty;
            }
            const maxTokens = parseInt(formState.maxOutputTokens, 10);
            if (!Number.isNaN(maxTokens)) {
                payload.maxOutputTokens = maxTokens;
            }

            try {
                confirmBtn.disabled = true;
                confirmBtn.textContent = 'Saving...';
                if (!agentId && allowDraft && onSaveDraft) {
                    await onSaveDraft(payload);
                    closeWithCallback({ saved: true });
                    return;
                }
                const endpointSnapshot = {
                    provider: formState.provider,
                    model: formState.model,
                    keyRef: formState.keyRef
                };
                const nowWired = window.isEndpointWired(endpointSnapshot);
                const modelChanged = initialEndpointSnapshot.provider !== endpointSnapshot.provider
                    || initialEndpointSnapshot.model !== endpointSnapshot.model;
                const shouldSendIntro = nowWired && (!initialWired || modelChanged);
                await agentEndpointsApi.save(agentId, payload);
                await window.loadAgentStatuses();
                if (shouldSendIntro) {
                    const reason = !initialWired ? 'initial wiring' : 'model change';
                    const successMessage = !initialWired
                        ? `Connected ${name} to ${formState.provider}.`
                        : `Updated ${name} model.`;
                    notificationStore.push(
                        'success',
                        'workbench',
                        successMessage,
                        `Model: ${formState.model}`,
                        'social',
                        false,
                        '',
                        null,
                        'agents'
                    );
                    closeWithCallback({ saved: true });
                    void createAgentIntroIssue(agent || { name }, endpointSnapshot, reason);
                    return;
                } else {
                    notificationStore.success(`Saved endpoint settings for ${name}`, 'workbench');
                }
                closeWithCallback({ saved: true });
            } catch (err) {
                errorHint.textContent = err.message || 'Failed to save agent settings.';
                errorHint.style.display = 'block';
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Save';
            }
        });

        cancelBtn.addEventListener('click', () => closeWithCallback({ saved: false }));

        advancedToggle.addEventListener('click', () => {
            const isOpen = advancedBody.style.display !== 'none';
            const nextOpen = !isOpen;
            advancedBody.style.display = nextOpen ? 'flex' : 'none';
            advancedToggle.textContent = nextOpen ? 'Hide advanced' : 'Advanced settings';
            localStorage.setItem('control-room:agent-settings-advanced', nextOpen ? 'open' : 'closed');
        });

        if (!agentId && !allowDraft) {
            errorHint.textContent = 'Agent id missing.';
            errorHint.style.display = 'block';
        } else {
            loadInitialData();
        }
    }

    async function createAgentIntroIssue(agent, endpoint, reason) {
        if (!agent) return null;
        const name = agent.name || 'Agent';
        const role = agent.role || 'role';
        const provider = endpoint?.provider || 'provider';
        const model = endpoint?.model || 'model';
        const prompt = buildGreetingPrompt(agent);
        const reasonLine = reason ? `Trigger: ${reason}` : '';
        const body = [
            `Intro from ${name}.`,
            `${name} (${role}) is now wired to ${provider}${model ? ` / ${model}` : ''}.`,
            reasonLine
        ].filter(Boolean).join('\n');

        try {
            const issue = await issueApi.create({
                title: `Agent intro: ${name}`,
                body,
                openedBy: name,
                tags: ['agent-intro']
            });
            if (issue && issue.id) {
                notificationStore.issueCreated(issue.id, issue.title, issue.openedBy, issue.assignedTo);
                if (agent.id) {
                    try {
                        const reply = await withAgentTurn(agent.id, 'processing', () => api('/api/ai/chat', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ message: prompt, agentId: agent.id })
                        }), `Generating greeting for ${name}`);
                        if (reply && reply.content) {
                            await issueApi.addComment(issue.id, {
                                author: name,
                                body: reply.content
                            });
                            notificationStore.issueCommentAdded(issue.id, name);
                            await refreshIssueModal(issue.id);
                        } else {
                            notificationStore.warning(`Greeting response was empty for ${name}`, 'workbench');
                        }
                    } catch (err) {
                        const errorMessage = `Greeting failed: ${err.message}`;
                        log(`Failed to fetch greeting for ${name}: ${err.message}`, 'warning');
                        notificationStore.warning(errorMessage, 'workbench');
                        try {
                            await issueApi.addComment(issue.id, {
                                author: 'system',
                                body: errorMessage
                            });
                            notificationStore.issueCommentAdded(issue.id, 'system');
                            await refreshIssueModal(issue.id);
                        } catch (commentErr) {
                            log(`Failed to record greeting error: ${commentErr.message}`, 'warning');
                        }
                    }
                }
            }
            return issue;
        } catch (err) {
            log(`Failed to create intro issue for ${name}: ${err.message}`, 'warning');
            return null;
        }
    }


    function buildGreetingPrompt(agent) {
        const name = agent?.name || 'Agent';
        const role = agent?.role || 'role';
        const instructions = agent?.personality?.baseInstructions || '';
        const sliders = agent?.personalitySliders || {};
        const signatureLine = agent?.signatureLine || '';

        const lines = [
            `You are ${name}, role: ${role}.`,
            '',
            'Please introduce yourself in 2-4 sentences that reflect your personality and role.',
            'Respond with only the introduction text. Do not include analysis, reasoning, or bullet lists.'
        ];

        if (instructions) {
            lines.unshift(`Personality instructions: ${instructions}`);
        }

        const sliderEntries = Object.entries(sliders);
        if (sliderEntries.length > 0) {
            const sliderText = sliderEntries
                .map(([key, value]) => `${key}=${value}`)
                .join(', ');
            lines.unshift(`Personality sliders: ${sliderText}`);
        }
        if (signatureLine) {
            lines.unshift(`Signature line: ${signatureLine}`);
        }

        return lines.join('\n');
    }

    async function showGreetingScanModal(agentId) {
        const { modal, body } = createModalShell(
            'Greeting Scan',
            'Close',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: true }
        );

        modal.classList.add('agent-greeting-modal');

        const status = document.createElement('div');
        status.className = 'modal-text';
        status.textContent = 'Greeting scan started. Waiting for agent response.';
        body.appendChild(status);

        let agent = null;
        try {
            agent = await agentApi.get(agentId);
        } catch (err) {
            const error = document.createElement('div');
            error.className = 'modal-hint';
            error.textContent = `Failed to load agent data: ${err.message}`;
            body.appendChild(error);
            return;
        }

        // Store the prompt for future wiring without rendering it yet.
        modal.dataset.greetingPrompt = buildGreetingPrompt(agent);

        const responseTitle = document.createElement('div');
        responseTitle.className = 'modal-label';
        responseTitle.textContent = 'Response';
        body.appendChild(responseTitle);

        const responseBox = document.createElement('div');
        responseBox.className = 'greeting-response';
        responseBox.textContent = 'Awaiting agent response...';
        body.appendChild(responseBox);
    }

    function showConferenceInviteModal(seedAgent) {
        const agents = (state.agents.list || []).filter(agent => agent.enabled !== false);
        const seedId = seedAgent?.id || '';
        const { modal, body, confirmBtn, close } = createModalShell(
            'Invite to Conference',
            'Start Conference',
            'Cancel',
            { closeOnCancel: true }
        );

        modal.classList.add('conference-invite-modal');

        const info = document.createElement('div');
        info.className = 'modal-text';
        info.textContent = 'Pick invitees and select a moderator for the conference.';
        body.appendChild(info);

        const agendaRow = document.createElement('div');
        agendaRow.className = 'modal-row';
        const agendaLabel = document.createElement('label');
        agendaLabel.className = 'modal-label';
        agendaLabel.textContent = 'Agenda';
        const agendaInput = document.createElement('textarea');
        agendaInput.className = 'conference-agenda-input';
        agendaInput.placeholder = 'Topics, goals, or questions...';
        agendaInput.rows = 3;
        agendaRow.appendChild(agendaLabel);
        agendaRow.appendChild(agendaInput);
        body.appendChild(agendaRow);

        const inviteAllRow = document.createElement('label');
        inviteAllRow.className = 'conference-invite-actions';
        const inviteAllCheckbox = document.createElement('input');
        inviteAllCheckbox.type = 'checkbox';
        inviteAllCheckbox.className = 'conference-agent-checkbox';
        const inviteAllText = document.createElement('span');
        inviteAllText.textContent = 'Invite all agents';
        inviteAllRow.appendChild(inviteAllCheckbox);
        inviteAllRow.appendChild(inviteAllText);
        body.appendChild(inviteAllRow);

        if (agents.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'conference-agent-empty';
            empty.textContent = 'No agents available to invite.';
            body.appendChild(empty);
            confirmBtn.disabled = true;
            return;
        }

        const list = document.createElement('div');
        list.className = 'conference-agent-list';

        const header = document.createElement('div');
        header.className = 'conference-agent-row conference-agent-header';
        const headerName = document.createElement('div');
        headerName.className = 'conference-agent-name';
        headerName.textContent = 'Agent';
        const headerRole = document.createElement('div');
        headerRole.className = 'conference-agent-role';
        headerRole.textContent = 'Role';
        const headerInvite = document.createElement('div');
        headerInvite.className = 'conference-agent-flag';
        headerInvite.textContent = 'Invited';
        const headerLead = document.createElement('div');
        headerLead.className = 'conference-agent-flag';
        headerLead.textContent = 'Moderator';
        header.appendChild(headerName);
        header.appendChild(headerRole);
        header.appendChild(headerInvite);
        header.appendChild(headerLead);
        list.appendChild(header);

        const selections = new Map();

        agents.forEach(agent => {
            const row = document.createElement('div');
            row.className = 'conference-agent-row';

            const name = document.createElement('div');
            name.className = 'conference-agent-name';
            name.textContent = agent.name || 'Unnamed Agent';
            name.title = agent.name || 'Unnamed Agent';

            const role = document.createElement('div');
            role.className = 'conference-agent-role';
            role.textContent = agent.role || 'role';

            const invitedCell = document.createElement('label');
            invitedCell.className = 'conference-agent-toggle';
            const invitedCheckbox = document.createElement('input');
            invitedCheckbox.type = 'checkbox';
            invitedCheckbox.className = 'conference-agent-checkbox';
            invitedCheckbox.checked = Boolean(agent.id && agent.id === seedId);
            invitedCell.appendChild(invitedCheckbox);

            const leadCell = document.createElement('label');
            leadCell.className = 'conference-agent-toggle';
            const leadCheckbox = document.createElement('input');
            leadCheckbox.type = 'checkbox';
            leadCheckbox.className = 'conference-agent-checkbox';
            leadCell.appendChild(leadCheckbox);

            invitedCheckbox.addEventListener('change', () => {
                // Moderator must be invited, so re-check if user tries to uninvite.
                if (!invitedCheckbox.checked && leadCheckbox.checked) {
                    invitedCheckbox.checked = true;
                }
                updateStartState();
            });

            leadCheckbox.addEventListener('change', () => {
                if (leadCheckbox.checked) {
                    // Single moderator: uncheck all other lead boxes.
                    selections.forEach((item) => {
                        if (item.agent.id !== agent.id) {
                            item.lead.checked = false;
                        }
                    });
                    invitedCheckbox.checked = true;
                }
                updateStartState();
            });

            selections.set(agent.id, { invited: invitedCheckbox, lead: leadCheckbox, agent });

            row.appendChild(name);
            row.appendChild(role);
            row.appendChild(invitedCell);
            row.appendChild(leadCell);
            list.appendChild(row);
        });

        body.appendChild(list);

        const updateStartState = () => {
            const invitedCount = Array.from(selections.values()).filter(item => item.invited.checked).length;
            inviteAllCheckbox.checked = invitedCount > 0 && invitedCount === selections.size;
            confirmBtn.disabled = invitedCount === 0;
        };

        inviteAllCheckbox.addEventListener('change', () => {
            const shouldInvite = inviteAllCheckbox.checked;
            selections.forEach(item => {
                item.invited.checked = shouldInvite;
                if (!shouldInvite && item.lead.checked) {
                    item.invited.checked = true;
                }
            });
            updateStartState();
        });

        updateStartState();

        confirmBtn.addEventListener('click', () => {
            const invited = [];
            const leaders = [];
            selections.forEach((item) => {
                if (item.invited.checked) invited.push(item.agent);
                if (item.lead.checked) leaders.push(item.agent);
            });

            const agenda = agendaInput.value.trim();
            const invitedNames = invited.map(agent => agent.name).join(', ') || 'none';
            const leaderNames = leaders.map(agent => agent.name).join(', ') || 'none';

            log(`Conference started. Invited: ${invitedNames}. Moderators: ${leaderNames}.`, 'info');
            if (agenda) {
                log(`Conference agenda: ${agenda}`, 'info');
            }
            notificationStore.success(`Conference started with ${invited.length} agent(s).`, 'workbench');
            close();
            showConferenceModeModal({
                agenda,
                invited,
                moderators: leaders
            });
        });
    }

    function showConferenceModeModal(config) {
        const invited = Array.isArray(config?.invited) ? config.invited.slice() : [];
        const moderators = Array.isArray(config?.moderators) ? config.moderators.slice() : [];
        const agenda = config?.agenda || '';
        const mutedIds = new Set();
        const chatLog = [];
        const conferenceContext = {
            isLoading: false,
            text: ''
        };

        const { panel, actions, body, close } = createWorkbenchPanelShell('Conference');
        panel.classList.add('conference-mode-modal');

        const closeConference = () => {
            invited.forEach(agent => setAgentActivityState(agent.id, 'idle'));
            close();
        };

        invited.forEach(agent => setAgentActivityState(agent.id, 'reading', 'In conference'));

        const header = document.createElement('div');
        header.className = 'conference-header';

        const headerLeft = document.createElement('div');
        headerLeft.className = 'conference-title';
        headerLeft.textContent = agenda ? `Agenda: ${agenda}` : 'No agenda set';

        const headerActions = document.createElement('div');
        headerActions.className = 'conference-actions';

        const btnCreateIssue = document.createElement('button');
        btnCreateIssue.type = 'button';
        btnCreateIssue.className = 'conference-action-btn';
        btnCreateIssue.textContent = 'Create Issue from Chat';
        btnCreateIssue.addEventListener('click', () => {
            showComingSoonModal('Create Issue', 'Conference transcript -> issue creation is coming soon.');
        });

        const btnManage = document.createElement('button');
        btnManage.type = 'button';
        btnManage.className = 'conference-action-btn';
        btnManage.textContent = 'Manage Attendees';
        btnManage.addEventListener('click', () => {
            showConferenceManageModal(invited, renderAttendees);
        });

        const btnClose = document.createElement('button');
        btnClose.type = 'button';
        btnClose.className = 'conference-action-btn';
        btnClose.textContent = 'Close';
        btnClose.addEventListener('click', closeConference);

        headerActions.appendChild(btnCreateIssue);
        headerActions.appendChild(btnManage);
        headerActions.appendChild(btnClose);

        header.appendChild(headerLeft);
        header.appendChild(headerActions);
        body.appendChild(header);

        const layout = document.createElement('div');
        layout.className = 'conference-layout';

        const attendeesPane = document.createElement('div');
        attendeesPane.className = 'conference-attendees';

        const attendeesTitle = document.createElement('div');
        attendeesTitle.className = 'conference-attendees-title';
        attendeesTitle.textContent = `Attendees (${invited.length})`;
        attendeesPane.appendChild(attendeesTitle);

        const attendeesList = document.createElement('div');
        attendeesList.className = 'conference-attendees-list';
        attendeesPane.appendChild(attendeesList);

        const chatPane = document.createElement('div');
        chatPane.className = 'conference-chat';

        const chatHistory = document.createElement('div');
        chatHistory.className = 'conference-chat-history';

        const chatHint = document.createElement('div');
        chatHint.className = 'conference-chat-hint';
        chatHint.textContent = 'Messages are routed to invited agents (muted agents are skipped).';

        const chatInputRow = document.createElement('div');
        chatInputRow.className = 'conference-chat-input-row';
        const chatInput = document.createElement('textarea');
        chatInput.className = 'conference-chat-input';
        chatInput.rows = 3;
        chatInput.placeholder = 'Type a message to the room...';
        const chatSend = document.createElement('button');
        chatSend.type = 'button';
        chatSend.className = 'conference-chat-send';
        chatSend.textContent = 'Send';
        chatInputRow.appendChild(chatInput);
        chatInputRow.appendChild(chatSend);

        chatPane.appendChild(chatHistory);
        chatPane.appendChild(chatHint);
        chatPane.appendChild(chatInputRow);

        layout.appendChild(attendeesPane);
        layout.appendChild(chatPane);
        body.appendChild(layout);

        const renderAttendees = () => {
            attendeesTitle.textContent = `Attendees (${invited.length})`;
            attendeesList.innerHTML = '';
            if (invited.length === 0) {
                const empty = document.createElement('div');
                empty.className = 'conference-attendees-empty';
                empty.textContent = 'No attendees.';
                attendeesList.appendChild(empty);
                return;
            }
            invited.forEach(agent => {
                const row = document.createElement('div');
                row.className = 'conference-attendee';
                const avatar = document.createElement('div');
                avatar.className = 'conference-attendee-avatar';
                const avatarData = agent.avatar && agent.avatar.trim() ? agent.avatar.trim() : '';
                if (avatarData.startsWith('data:') || avatarData.startsWith('http')) {
                    const img = document.createElement('img');
                    img.src = avatarData;
                    img.alt = agent.name || 'Agent';
                    avatar.appendChild(img);
                } else if (avatarData) {
                    avatar.textContent = avatarData;
                } else {
                    avatar.textContent = agent.name ? agent.name.charAt(0).toUpperCase() : '?';
                }
                if (agent.color && !avatarData.startsWith('data:') && !avatarData.startsWith('http')) {
                    avatar.style.background = agent.color;
                }

                const info = document.createElement('div');
                info.className = 'conference-attendee-info';

                const name = document.createElement('div');
                name.className = 'conference-attendee-name';
                name.textContent = agent.name || 'Unnamed Agent';
                const meta = document.createElement('div');
                meta.className = 'conference-attendee-meta';
                const role = agent.role ? `Role: ${agent.role}` : 'Role: -';
                const isLead = moderators.some(moderator => moderator.id === agent.id);
                const leadTag = isLead ? 'Moderator' : 'Participant';
                const mutedTag = mutedIds.has(agent.id) ? 'Muted' : '';
                meta.textContent = [role, leadTag, mutedTag].filter(Boolean).join(' • ');
                if (isLead) {
                    row.classList.add('conference-attendee-moderator');
                }
                info.appendChild(name);
                info.appendChild(meta);
                row.appendChild(avatar);
                row.appendChild(info);
                attendeesList.appendChild(row);

                row.addEventListener('contextmenu', (e) => {
                    e.preventDefault();
                    showConferenceAttendeeMenu(e, agent, invited, moderators, mutedIds, renderAttendees);
                });
            });
        };

        const addChatMessage = (author, role, content) => {
            const entry = document.createElement('div');
            entry.className = `conference-chat-message ${role}`;
            const header = document.createElement('div');
            header.className = 'conference-chat-message-header';
            header.textContent = author || (role === 'user' ? 'You' : 'Agent');
            const body = document.createElement('div');
            body.className = 'conference-chat-message-body';
            body.textContent = content;
            entry.appendChild(header);
            entry.appendChild(body);
            chatHistory.appendChild(entry);
            chatHistory.scrollTop = chatHistory.scrollHeight;
        };

        const formatConferenceRoster = () => {
            const names = invited.map(agent => agent.name || 'Agent');
            return names.length > 0 ? names.join(', ') : 'none';
        };

        const formatModerators = () => {
            const names = moderators.map(agent => agent.name || 'Agent');
            return names.length > 0 ? names.join(', ') : 'none';
        };

        const formatChatLogForPrompt = (limit = 12) => {
            const slice = chatLog.slice(-limit);
            if (slice.length === 0) return 'No prior messages.';
            return slice.map(entry => `${entry.author}: ${entry.content}`).join('\n');
        };

        const buildConferencePrompt = (agent, message, { retry = false } = {}) => {
            const agentName = agent?.name || 'Agent';
            const agentRole = agent?.role || 'role';
            const roleFrame = buildRoleFrame(agentRole);
            const evidenceRules = buildEvidenceRules();
            const contextPrelude = conferenceContext.text ? `Grounding prelude:\n${conferenceContext.text}` : 'Grounding prelude: (no context loaded)';
            const lines = [
                'You are in a creative project review for a fiction project (not a business standup).',
                `Conference agenda: ${agenda || 'none'}`,
                `Attendees: ${formatConferenceRoster()}`,
                `Moderators: ${formatModerators()}`,
                `You are ${agentName} (${agentRole}). Respond as this agent.`,
                'Primary task: find ONE issue in your role domain using evidence from the VFS. Secondary observations are optional and must be clearly labeled as secondary.',
                roleFrame,
                evidenceRules,
                contextPrelude,
                'Conversation so far:',
                formatChatLogForPrompt(),
                'Latest message:',
                message,
                retry ? 'Your last response was rejected for missing or invalid evidence. Fix it now.' : 'Keep your reply concise and actionable.'
            ];
            const payload = lines.join('\n');
            return buildChatPrompt ? buildChatPrompt(payload, agent) : payload;
        };

        const buildRoleFrame = (role) => {
            const normalized = (role || '').toLowerCase();
            if (normalized.includes('planner')) {
                return 'Role framing: Focus on outline structure, scene order, stakes, and story arc continuity. Avoid project management jargon unless asked. Evidence must reference outline or scene ordering.';
            }
            if (normalized.includes('writer')) {
                return 'Role framing: Focus on prose quality, pacing, voice, and concrete scene execution. Avoid project management jargon unless asked. Evidence must reference a scene file quote.';
            }
            if (normalized.includes('editor')) {
                return 'Role framing: Focus on line-level clarity, consistency, and tightening the draft. Avoid project management jargon unless asked. Evidence must reference a scene file quote or line/section.';
            }
            if (normalized.includes('critic')) {
                return 'Role framing: Focus on critique of narrative choices and reader experience. Avoid meeting-meta feedback. Evidence must reference a scene file quote.';
            }
            if (normalized.includes('continuity')) {
                return 'Role framing: Focus on canon consistency, timeline coherence, and named entities accuracy. Evidence must reference compendium/canon or scene quotes.';
            }
            if (normalized.includes('assistant') || normalized.includes('chief')) {
                return 'Role framing: Synthesize grounded findings across agents and surface next steps tied to the text. Evidence must reference outline or scene quotes.';
            }
            return 'Role framing: Stay grounded in the fiction project and avoid business standup tropes.';
        };

        const buildEvidenceRules = () => {
            return [
                'Evidence line requirement (mandatory): include exactly one line starting with "Evidence:".',
                'Evidence must reference story content (outline/scenes/canon/compendium) or explicitly request access to those.',
                'Valid formats:',
                '✅ Evidence: I checked [file/memory tier] and found [specific thing].',
                '✅ Evidence: I checked [location] and found no issues with [specific aspect].',
                '✅ Evidence: I need to check [X] but don’t have access — requesting [Y].',
                '❌ Missing evidence line, issue-tracker-only evidence, or generic claims without checks will be rejected.'
            ].join('\n');
        };

        const validateEvidenceLine = (content) => {
            if (!content) return { ok: false, reason: 'empty' };
            const lines = String(content).split('\n').map(line => line.trim()).filter(Boolean);
            const evidenceLine = lines.find(line => line.toLowerCase().startsWith('evidence:'));
            if (!evidenceLine) return { ok: false, reason: 'missing' };
            const normalized = evidenceLine.toLowerCase();
            const checkedFound = normalized.includes('i checked') && normalized.includes('and found');
            const checkedFoundNo = normalized.includes('i checked') && (normalized.includes('found no') || normalized.includes('found nothing'));
            const needCheck = normalized.includes('i need to check') && (normalized.includes('requesting') || normalized.includes('request'));
            const storyKeywords = [
                'outline',
                'scene',
                'story/',
                'compendium',
                'canon',
                'scn-outline',
                'scn outline',
                'manuscript'
            ];
            const mentionsStorySource = storyKeywords.some(keyword => normalized.includes(keyword));
            const mentionsIssueOnly = normalized.includes('issue') && !mentionsStorySource;
            if ((checkedFound || checkedFoundNo) && mentionsIssueOnly) {
                return { ok: false, reason: 'issue-only' };
            }
            const hasQuote = /["“”'‘’][^"“”'‘’]{6,}["“”'‘’]/.test(evidenceLine);
            const hasLocation = normalized.includes('line') || normalized.includes('section') || normalized.includes('paragraph');
            const hasScope = normalized.includes('scene') || normalized.includes('.md') || normalized.includes('file') || normalized.includes('files');
            const hasContentClaims = lines.some(line => {
                const lower = line.toLowerCase();
                return lower.startsWith('problem:') || lower.startsWith('suggestion:') || lower.includes('problem') || lower.includes('suggestion');
            });

            if (checkedFoundNo && mentionsStorySource) {
                if (!hasScope) return { ok: false, reason: 'missing-scope' };
                return { ok: true };
            }
            if (checkedFound && mentionsStorySource) {
                if (hasQuote) return { ok: true };
                if (hasLocation && !hasContentClaims) return { ok: true };
                return { ok: false, reason: hasContentClaims ? 'missing-quote' : 'missing-location' };
            }
            if (needCheck) {
                if (!hasScope) return { ok: false, reason: 'missing-scope' };
                return { ok: true };
            }
            return { ok: false, reason: 'format' };
        };

        const loadConferenceContext = async () => {
            if (conferenceContext.isLoading) return;
            conferenceContext.isLoading = true;
            const parts = [];
            try {
                if (window.workspaceApi) {
                    const meta = await workspaceApi.metadata();
                    if (meta && meta.displayName) {
                        parts.push(`Project: ${meta.displayName}`);
                    }
                }
            } catch (_) {
                // ignore
            }
            try {
                if (window.outlineApi) {
                    const outline = await outlineApi.get();
                    const scenes = outline && Array.isArray(outline.scenes) ? outline.scenes : [];
                    if (scenes.length) {
                        const sceneLines = scenes.slice(0, 5).map(scene => {
                            const number = scene.number != null ? `#${scene.number}` : '';
                            const title = scene.title || scene.name || 'Untitled';
                            return `${number} ${title}`.trim();
                        });
                        parts.push(`Outline scenes (sample): ${sceneLines.join(' | ')}`);
                    } else {
                        parts.push('Outline scenes: none listed.');
                    }
                }
            } catch (_) {
                // ignore
            }
            try {
                if (window.fileApi) {
                    const tree = await fileApi.getTree();
                    const storyFiles = collectStoryFiles(tree, 6);
                    const outlineAvailable = storyFiles.some(item => item.toLowerCase().includes('scn-outline'));
                    if (storyFiles.length) {
                        parts.push(`Story/Canon files (sample): ${storyFiles.join(' | ')}`);
                        parts.push(`Outline available in VFS: ${outlineAvailable ? 'Yes (Story/SCN-outline.md)' : 'No'}`);
                    } else {
                        parts.push('Story/Canon files: none detected.');
                    }
                }
            } catch (_) {
                // ignore
            }
            try {
                if (window.issueApi) {
                    const issues = await issueApi.list({ status: 'open' });
                    if (Array.isArray(issues) && issues.length) {
                        const summaries = issues.slice(0, 5).map(item => `#${item.id} ${item.title || 'Untitled'}`);
                        parts.push(`Open issues (sample): ${summaries.join(' | ')}`);
                    } else {
                        parts.push('Open issues: none.');
                    }
                }
            } catch (_) {
                // ignore
            }
            conferenceContext.text = parts.join('\n');
            conferenceContext.isLoading = false;
        };

        const collectStoryFiles = (node, limit = 6) => {
            const results = [];
            const walk = (item) => {
                if (!item || results.length >= limit) return;
                if (item.type === 'file' && item.path) {
                    const lower = item.path.toLowerCase();
                    if (lower.startsWith('story/') || lower.startsWith('compendium/') || lower.includes('scn-outline')) {
                        results.push(item.path);
                    }
                }
                if (Array.isArray(item.children)) {
                    item.children.forEach(child => walk(child));
                }
            };
            walk(node);
            return results;
        };

        const getActiveParticipants = () => {
            const invitedList = invited.filter(agent => agent && agent.id && !mutedIds.has(agent.id));
            const moderatorIds = new Set(moderators.map(agent => agent.id));
            const ordered = [];
            invitedList.forEach(agent => {
                if (moderatorIds.has(agent.id)) {
                    ordered.push(agent);
                }
            });
            invitedList.forEach(agent => {
                if (!moderatorIds.has(agent.id)) {
                    ordered.push(agent);
                }
            });
            return ordered;
        };

        const sendMessage = async () => {
            const text = chatInput.value.trim();
            if (!text) return;
            chatInput.value = '';
            chatInput.disabled = true;
            chatSend.disabled = true;
            chatLog.push({ author: 'You', role: 'user', content: text });
            addChatMessage('You', 'user', text);

            const participants = getActiveParticipants();
            if (participants.length === 0) {
                notificationStore.warning('No invited agents available to respond.', 'workbench');
                chatInput.disabled = false;
                chatSend.disabled = false;
                return;
            }

            for (const agent of participants) {
                try {
                    const prompt = buildConferencePrompt(agent, text);
                    const response = await withAgentTurn(agent.id, 'processing', () => api('/api/ai/chat', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ message: prompt, agentId: agent.id })
                    }), `Conference response from ${agent.name || 'agent'}`);
                    let parsed = extractStopHook ? extractStopHook(response.content) : { content: response.content, stopHook: null };
                    let reply = parsed.content || 'No response.';
                    let evidenceCheck = validateEvidenceLine(reply);
                    if (!evidenceCheck.ok) {
                        const retryPrompt = buildConferencePrompt(agent, text, { retry: true });
                        const retryResponse = await withAgentTurn(agent.id, 'processing', () => api('/api/ai/chat', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ message: retryPrompt, agentId: agent.id })
                        }), `Conference retry for ${agent.name || 'agent'}`);
                        parsed = extractStopHook ? extractStopHook(retryResponse.content) : { content: retryResponse.content, stopHook: null };
                        reply = parsed.content || 'No response.';
                        evidenceCheck = validateEvidenceLine(reply);
                    }
                    if (!evidenceCheck.ok) {
                        let rejection = 'Ungrounded response rejected. Evidence line missing or invalid.';
                        if (evidenceCheck.reason === 'issue-only') {
                            rejection = 'Ungrounded response rejected. Issue-tracker evidence alone is not sufficient; cite story content or request access.';
                        } else if (evidenceCheck.reason === 'missing-quote') {
                            rejection = 'Ungrounded response rejected. Content claims require a direct quote from the cited file.';
                        } else if (evidenceCheck.reason === 'missing-location') {
                            rejection = 'Ungrounded response rejected. Structural claims must cite a line/section reference.';
                        } else if (evidenceCheck.reason === 'missing-scope') {
                            rejection = 'Ungrounded response rejected. Absence or access claims must specify scope (files/scenes scanned).';
                        }
                        addChatMessage(agent.name || 'Agent', 'assistant', rejection);
                        chatLog.push({ author: agent.name || 'Agent', role: 'assistant', content: rejection, stopHook: 'grounding-required' });
                        notificationStore.warning(`Conference response rejected: grounding required for ${agent.name || 'agent'}.`, 'workbench');
                    } else {
                        addChatMessage(agent.name || 'Agent', 'assistant', reply);
                        chatLog.push({ author: agent.name || 'Agent', role: 'assistant', content: reply, stopHook: parsed.stopHook });
                        if (parsed.stopHook) {
                            notificationStore.warning(`Stop hook: ${parsed.stopHook}`, 'workbench');
                        }
                    }
                } catch (err) {
                    const errorMessage = err && err.message ? err.message : 'Agent failed to respond.';
                    addChatMessage(agent.name || 'Agent', 'assistant', `Error: ${errorMessage}`);
                    chatLog.push({ author: agent.name || 'Agent', role: 'assistant', content: `Error: ${errorMessage}` });
                    notificationStore.warning(`Conference response failed for ${agent.name || 'agent'}.`, 'workbench');
                }
            }

            chatInput.disabled = false;
            chatSend.disabled = false;
        };

        void loadConferenceContext();

        chatSend.addEventListener('click', sendMessage);
        chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });

        const exitBtn = document.createElement('button');
        exitBtn.type = 'button';
        exitBtn.className = 'workbench-panel-btn';
        exitBtn.textContent = 'Exit';
        exitBtn.addEventListener('click', close);

        const discardBtn = document.createElement('button');
        discardBtn.type = 'button';
        discardBtn.className = 'workbench-panel-btn workbench-panel-btn-danger';
        discardBtn.textContent = 'Discard Session';
        discardBtn.addEventListener('click', close);

        actions.appendChild(discardBtn);
        actions.appendChild(exitBtn);

        renderAttendees();
        setTimeout(() => chatInput.focus(), 0);
    }

    function showConferenceAttendeeMenu(event, agent, invited, moderators, mutedIds, onUpdate) {
        hideContextMenu();

        const menu = document.createElement('div');
        menu.className = 'context-menu';

        const isMuted = mutedIds.has(agent.id);
        const isModerator = moderators.some(item => item.id === agent.id);

        const actions = [
            {
                label: isMuted ? 'Unmute' : 'Mute',
                action: () => {
                    if (isMuted) mutedIds.delete(agent.id);
                    else mutedIds.add(agent.id);
                    onUpdate();
                }
            },
            {
                label: 'Kick',
                action: () => {
                    const index = invited.findIndex(item => item.id === agent.id);
                    if (index !== -1) invited.splice(index, 1);
                    const leadIndex = moderators.findIndex(item => item.id === agent.id);
                    if (leadIndex !== -1) moderators.splice(leadIndex, 1);
                    mutedIds.delete(agent.id);
                    onUpdate();
                }
            },
            {
                label: isModerator ? 'Demote from Moderator' : 'Promote to Moderator',
                action: () => {
                    if (isModerator) {
                        const idx = moderators.findIndex(item => item.id === agent.id);
                        if (idx !== -1) moderators.splice(idx, 1);
                    } else {
                        moderators.length = 0;
                        moderators.push(agent);
                    }
                    onUpdate();
                }
            }
        ];

        actions.forEach(item => {
            const row = document.createElement('div');
            row.className = 'context-menu-item';
            row.textContent = item.label;
            row.addEventListener('click', () => {
                hideContextMenu();
                item.action();
            });
            menu.appendChild(row);
        });

        document.body.appendChild(menu);
        positionContextMenu(menu, event.clientX, event.clientY);
        contextMenu = menu;
    }

    function showConferenceManageModal(invited, onUpdate) {
        const agents = (state.agents.list || []).filter(agent => agent.enabled !== false);
        const { modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            'Manage Attendees',
            'Apply',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: false }
        );

        modal.classList.add('conference-manage-modal');

        const info = document.createElement('div');
        info.className = 'modal-text';
        info.textContent = 'Select agents to include in this conference.';
        body.appendChild(info);

        const list = document.createElement('div');
        list.className = 'conference-manage-list';
        body.appendChild(list);

        const invitedIds = new Set(invited.map(agent => agent.id));

        agents.forEach(agent => {
            const row = document.createElement('label');
            row.className = 'conference-manage-row';
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = invitedIds.has(agent.id);
            const name = document.createElement('span');
            name.textContent = agent.name || 'Unnamed Agent';
            const role = document.createElement('span');
            role.className = 'conference-manage-role';
            role.textContent = agent.role || 'role';
            row.appendChild(checkbox);
            row.appendChild(name);
            row.appendChild(role);
            list.appendChild(row);
        });

        confirmBtn.addEventListener('click', () => {
            invited.length = 0;
            const rows = Array.from(list.querySelectorAll('.conference-manage-row'));
            rows.forEach((row, index) => {
                const checkbox = row.querySelector('input');
                if (checkbox && checkbox.checked) {
                    invited.push(agents[index]);
                }
            });
            if (typeof onUpdate === 'function') {
                onUpdate();
            }
            close();
        });
    }

    function showChangeRoleModal(agent) {
        const name = agent?.name || 'Agent';
        const { body, confirmBtn, close } = createModalShell(
            `Change Role: ${name}`,
            'Apply',
            'Close',
            { closeOnCancel: true }
        );

        const roleMap = new Map();
        (state.agents.list || []).forEach(item => {
            const roleKey = canonicalizeRole(item.role);
            if (roleKey && !roleMap.has(roleKey)) {
                roleMap.set(roleKey, item.role);
            }
        });
        const roles = Array.from(roleMap.values());

        const row = document.createElement('div');
        row.className = 'modal-row';

        const label = document.createElement('label');
        label.className = 'modal-label';
        label.textContent = 'Current roles';

        const select = document.createElement('select');
        select.className = 'modal-select';
        if (roles.length === 0) {
            const option = document.createElement('option');
            option.value = '';
            option.textContent = 'No roles';
            select.appendChild(option);
            select.disabled = true;
        } else {
            roles.forEach(role => {
                const option = document.createElement('option');
                option.value = role;
                option.textContent = role;
                select.appendChild(option);
            });
        }

        const newRoleLabel = document.createElement('label');
        newRoleLabel.className = 'modal-label';
        newRoleLabel.textContent = 'Or create a new role';

        const input = document.createElement('input');
        input.className = 'modal-input';
        input.type = 'text';
        input.placeholder = 'New role name';

        row.appendChild(label);
        row.appendChild(select);
        row.appendChild(newRoleLabel);
        row.appendChild(input);
        body.appendChild(row);

        const updateApplyState = () => {
            const chosen = input.value.trim() || select.value;
            confirmBtn.disabled = !chosen;
        };

        if (agent?.role) {
            const roleKey = canonicalizeRole(agent.role);
            if (roleKey && roleMap.has(roleKey)) {
                select.value = roleMap.get(roleKey);
            }
        }

        updateApplyState();

        input.addEventListener('input', updateApplyState);
        select.addEventListener('change', updateApplyState);

        confirmBtn.addEventListener('click', () => {
            const chosen = input.value.trim() || select.value;
            if (chosen) {
                log(`Role for ${name} set to ${chosen}`, 'info');
            }
            close();
        });
    }

    function showConfirmRetireModal(agent) {
        const name = agent?.name || 'Agent';
        const { body, confirmBtn, close } = createModalShell(`Retire ${name}?`, 'Retire', 'Cancel');

        const info = document.createElement('div');
        info.className = 'modal-text';
        info.textContent = 'This will disable the agent. You can re-enable later.';
        body.appendChild(info);

        confirmBtn.addEventListener('click', async () => {
            close();
            try {
                await agentApi.setStatus(agent.id, false);
                log(`Retired ${name}`, 'warning');
                await window.loadAgents();
            } catch (err) {
                log(`Failed to retire ${name}: ${err.message}`, 'error');
            }
        });
    }

    async function showRetiredAgentsModal() {
        const { modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            'Retired Agents',
            'Close',
            'Cancel',
            { closeOnCancel: true, closeOnConfirm: true }
        );

        modal.classList.add('retired-agent-modal');
        confirmBtn.style.display = 'none';
        if (cancelBtn) {
            cancelBtn.textContent = 'Close';
        }

        const info = document.createElement('div');
        info.className = 'modal-text';
        info.textContent = 'Retired agents are hidden from the active roster.';
        body.appendChild(info);

        const list = document.createElement('div');
        list.className = 'retired-agent-list';
        body.appendChild(list);

        const renderRetiredList = async () => {
            list.innerHTML = '';
            try {
                const agents = await agentApi.listAll();
                const retired = (agents || []).filter(agent => agent.enabled === false);
                if (retired.length === 0) {
                    const empty = document.createElement('div');
                    empty.className = 'retired-agent-empty';
                    empty.innerHTML = '<div class="retired-agent-empty-icon">☀️</div><div class="retired-agent-empty-text">No retired agents</div><div class="retired-agent-empty-hint">Everyone\'s hard at work!</div>';
                    list.appendChild(empty);
                    return;
                }

                retired.forEach(agent => {
                    const card = document.createElement('div');
                    card.className = 'retired-agent-card';

                    // Avatar (reuse same logic as agent cards)
                    const avatar = document.createElement('div');
                    avatar.className = 'retired-agent-avatar';
                    const avatarData = agent.avatar && agent.avatar.trim() ? agent.avatar.trim() : '';

                    if (avatarData.startsWith('data:') || avatarData.startsWith('http')) {
                        const img = document.createElement('img');
                        img.src = avatarData;
                        img.alt = agent.name || 'Agent';
                        avatar.appendChild(img);
                        avatar.classList.add('has-image');
                    } else if (avatarData) {
                        avatar.textContent = avatarData;
                        avatar.classList.add('has-emoji');
                    } else {
                        avatar.textContent = agent.name ? agent.name.charAt(0).toUpperCase() : '?';
                        avatar.classList.add('has-initial');
                    }

                    // Info section
                    const info = document.createElement('div');
                    info.className = 'retired-agent-info';

                    const name = document.createElement('div');
                    name.className = 'retired-agent-name';
                    name.textContent = agent.name || 'Unnamed Agent';

                    const role = document.createElement('div');
                    role.className = 'retired-agent-role';
                    role.textContent = agent.role || 'role';

                    info.appendChild(name);
                    info.appendChild(role);

                    // Reactivate button
                    const reactivate = document.createElement('button');
                    reactivate.type = 'button';
                    reactivate.className = 'retired-agent-btn';
                    reactivate.textContent = 'Reactivate';
                    reactivate.addEventListener('click', async () => {
                        reactivate.disabled = true;
                        card.classList.add('reactivating');
                        try {
                            await agentApi.setStatus(agent.id, true);
                            await window.loadAgents();
                            card.remove();
                            if (!list.querySelector('.retired-agent-card')) {
                                const empty = document.createElement('div');
                                empty.className = 'retired-agent-empty';
                                empty.innerHTML = '<div class="retired-agent-empty-icon">☀️</div><div class="retired-agent-empty-text">No retired agents</div><div class="retired-agent-empty-hint">Everyone\'s hard at work!</div>';
                                list.appendChild(empty);
                            }
                        } catch (err) {
                            log(`Failed to reactivate ${agent.name}: ${err.message}`, 'error');
                            card.classList.remove('reactivating');
                            reactivate.disabled = false;
                        }
                    });

                    card.appendChild(avatar);
                    card.appendChild(info);
                    card.appendChild(reactivate);
                    list.appendChild(card);
                });
            } catch (err) {
                const error = document.createElement('div');
                error.className = 'modal-hint';
                error.textContent = `Failed to load retired agents: ${err.message}`;
                list.appendChild(error);
            }
        };

        await renderRetiredList();

        modal.addEventListener('click', (e) => {
            if (e.target === modal) close();
        });
    }


    const workbenchChats = new Map();

    function getWorkbenchChatLog(agentId) {
        if (!workbenchChats.has(agentId)) {
            workbenchChats.set(agentId, []);
        }
        return workbenchChats.get(agentId);
    }

    function appendWorkbenchChatMessage(container, role, content, agentName, meta = {}) {
        const msg = document.createElement('div');
        msg.className = `workbench-chat-message ${role}`;

        const label = document.createElement('div');
        label.className = 'workbench-chat-message-label';
        label.textContent = role === 'user' ? 'You' : (agentName || 'Assistant');

        const contentDiv = document.createElement('div');
        contentDiv.className = 'workbench-chat-message-content';
        contentDiv.textContent = content;

        msg.appendChild(label);
        msg.appendChild(contentDiv);
        if (meta && meta.stopHook) {
            const metaDiv = document.createElement('div');
            metaDiv.className = 'workbench-chat-message-meta';
            const stop = document.createElement('span');
            stop.className = 'chat-badge chat-badge-stop';
            stop.textContent = `Stop: ${meta.stopHook}`;
            metaDiv.appendChild(stop);
            msg.appendChild(metaDiv);
        }
        container.appendChild(msg);
        container.scrollTop = container.scrollHeight;
    }

    function showWorkbenchChatModal(agent) {
        if (!agent || !agent.id) return;
        if (!ensureChiefOfStaff('Agent chat')) {
            return;
        }

        window.setSelectedAgentId(agent.id);
        setAgentActivityState(agent.id, 'reading', `Reading chat for ${agent.name || 'agent'}`);
        const closeWithActivity = () => setAgentActivityState(agent.id, 'idle');

        const { modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            `${agent.name || 'Agent'}`,
            'Exit and Create Issue',
            null,
            { closeOnCancel: false, closeOnConfirm: false, onClose: closeWithActivity }
        );
        const closeChatModal = close;

        modal.classList.add('workbench-chat-modal');

        // Make modal sticky - prevent closing on overlay click
        const overlay = modal.closest('.modal-overlay');
        if (overlay) {
            overlay.style.pointerEvents = 'none';
            modal.style.pointerEvents = 'auto';
        }

        if (cancelBtn) {
            cancelBtn.style.display = 'none';
        }

        // Agent meta card with avatar
        const agentMeta = document.createElement('div');
        agentMeta.className = 'workbench-chat-agent-meta';

        const avatar = document.createElement('div');
        avatar.className = 'workbench-chat-agent-avatar';

        // Use agent's actual avatar if available
        const avatarData = agent.avatar && agent.avatar.trim() ? agent.avatar.trim() : '';
        if (avatarData.startsWith('data:') || avatarData.startsWith('http')) {
            const img = document.createElement('img');
            img.src = avatarData;
            img.alt = agent.name || 'Agent';
            img.className = 'workbench-chat-agent-avatar-img';
            avatar.appendChild(img);
        } else if (avatarData) {
            avatar.textContent = avatarData;
        } else {
            avatar.textContent = (agent.name || 'A').charAt(0).toUpperCase();
        }

        const agentInfo = document.createElement('div');
        agentInfo.className = 'workbench-chat-agent-info';

        const agentName = document.createElement('div');
        agentName.className = 'workbench-chat-agent-name';
        agentName.textContent = agent.name || 'Agent';

        const agentRole = document.createElement('div');
        agentRole.className = 'workbench-chat-agent-role';
        agentRole.textContent = agent.role ? `${agent.role} • Quick chat for task assignment` : 'Direct chat for task assignment';

        agentInfo.appendChild(agentName);
        agentInfo.appendChild(agentRole);
        agentMeta.appendChild(avatar);
        agentMeta.appendChild(agentInfo);
        body.appendChild(agentMeta);

        // Chat body
        const chatBody = document.createElement('div');
        chatBody.className = 'workbench-chat-body';

        const history = document.createElement('div');
        history.className = 'workbench-chat-history';

        const inputRow = document.createElement('div');
        inputRow.className = 'workbench-chat-input-row';

        const input = document.createElement('textarea');
        input.className = 'workbench-chat-input';
        input.rows = 3;
        input.placeholder = `Describe the task for ${agent.name || 'this agent'}...`;

        const sendBtn = document.createElement('button');
        sendBtn.type = 'button';
        sendBtn.className = 'workbench-chat-send';
        sendBtn.textContent = 'Send';

        inputRow.appendChild(input);
        inputRow.appendChild(sendBtn);

        chatBody.appendChild(history);
        chatBody.appendChild(inputRow);
        body.appendChild(chatBody);

        // Load chat history
        const log = getWorkbenchChatLog(agent.id);
        log.forEach(entry => {
            appendWorkbenchChatMessage(history, entry.role, entry.content, agent.name, {
                stopHook: entry.stopHook
            });
        });

        // Send message handler
        const sendMessage = async () => {
            const message = input.value.trim();
            if (!message) return;
            input.value = '';
            sendBtn.disabled = true;

            appendWorkbenchChatMessage(history, 'user', message, agent.name);
            log.push({ role: 'user', content: message });

            try {
                const requestMessage = buildChatPrompt ? buildChatPrompt(message, agent) : message;
                const response = await withAgentTurn(agent.id, 'processing', () => api('/api/ai/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: requestMessage, agentId: agent.id })
                }), `Responding to ${agent.name || 'agent'} chat`);
                const parsed = extractStopHook ? extractStopHook(response.content) : { content: response.content, stopHook: null, stopHookDetail: '' };
                const reply = parsed.content || 'No response.';
                appendWorkbenchChatMessage(history, 'assistant', reply, agent.name, { stopHook: parsed.stopHook });
                log.push({ role: 'assistant', content: reply, stopHook: parsed.stopHook, stopHookDetail: parsed.stopHookDetail });
                if (parsed.stopHook) {
                    notificationStore.warning(`Stop hook: ${parsed.stopHook}`, 'workbench');
                }
            } catch (err) {
                const errorMessage = 'Sorry, I encountered an error. Please try again.';
                appendWorkbenchChatMessage(history, 'assistant', errorMessage, agent.name);
                log.push({ role: 'assistant', content: errorMessage });
            } finally {
                sendBtn.disabled = false;
            }
        };

        // Create issue from chat log and close
        const exitAndCreateIssue = async () => {
            if (log.length === 0) {
                notificationStore.warning('No chat history to create issue from.', 'workbench');
                return;
            }

            // Build issue description from chat log
            let description = '## Chat Log\n\n';
            log.forEach(entry => {
                const speaker = entry.role === 'user' ? 'User' : agent.name || 'Agent';
                const stopLabel = entry.stopHook ? ` (stop hook: ${entry.stopHook})` : '';
                description += `**${speaker}${stopLabel}:** ${entry.content}\n\n`;
            });

            // Find team lead
            const teamLead = (state.agents.list || []).find(agent => isAssistantAgent(agent));

            // Assign to both agent and team lead
            const assignees = [agent.id];
            if (teamLead && teamLead.id !== agent.id) {
                assignees.push(teamLead.id);
            }

            // Extract title from first user message or use default
            const firstUserMessage = log.find(entry => entry.role === 'user');
            const title = firstUserMessage ?
                (firstUserMessage.content.length > 60 ?
                    firstUserMessage.content.substring(0, 57) + '...' :
                    firstUserMessage.content) :
                'Task from chat';

            try {
                const response = await api('/api/issues', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        title,
                        body: description,
                        assignee: agent.id,
                        priority: 'normal',
                        status: 'open',
                        tags: ['from-chat', agent.role || 'general']
                    })
                });

                const assigneeName = teamLead && teamLead.id !== agent.id ?
                    `${agent.name} and ${teamLead.name}` :
                    agent.name || 'Agent';

                notificationStore.success(`Issue created and assigned to ${assigneeName}!`, 'workbench', {
                    action: 'openIssue',
                    issueId: response.id
                });

                closeChatModal();
            } catch (err) {
                notificationStore.error('Failed to create issue. Please try again.', 'workbench');
                console.error('Issue creation error:', err);
            }
        };

        // Event listeners
        sendBtn.addEventListener('click', sendMessage);
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });

        // Wire up the "Exit and Create Issue" button
        confirmBtn.addEventListener('click', exitAndCreateIssue);

        setTimeout(() => input.focus(), 0);
    }


    function showAssistedModeModal() {
        if (!createModalShell) {
            throw new Error('createModalShell is not available');
        }
        const { modal, body, confirmBtn, cancelBtn } = createModalShell(
            'Assisted Mode',
            'Close',
            'Cancel',
            { closeOnConfirm: true }
        );
        modal.classList.add('dev-tools-modal', 'assisted-mode-modal');
        if (cancelBtn) {
            cancelBtn.remove();
        }

        const intro = document.createElement('div');
        intro.className = 'modal-text';
        intro.textContent = 'Toggle per-agent assisted mode, set a reason, and track queue/dosage.';
        body.appendChild(intro);

        const assistedList = document.createElement('div');
        assistedList.className = 'dev-tools-list';
        assistedList.textContent = 'Loading agents...';
        body.appendChild(assistedList);

        const status = document.createElement('div');
        status.className = 'dev-tools-status';
        status.textContent = 'Status: ready';
        body.appendChild(status);

        const setStatus = (text) => {
            status.textContent = text;
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

        const reasonOptions = [
            { value: 'manual', label: 'Manual' },
            { value: 'scope-exceeded', label: 'Scope exceeded' },
            { value: 'uncertainty', label: 'Uncertainty' },
            { value: 'no-progress', label: 'No progress' },
            { value: 'hysteria', label: 'Hysteria' }
        ];

        const renderAssistedList = async () => {
            if (!state.agents.list || state.agents.list.length === 0) {
                await window.loadAgents();
            }
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
                descEl.textContent = `${agent.role || 'role'} - ${modelLabel}`;
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
                reasonOptions.forEach(({ value, label }) => {
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
                        setStatus(`Status: updated assisted mode for ${agent.name || agent.id}`);
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

        renderAssistedList();
        confirmBtn.focus();
    }

    window.showAssistedModeModal = showAssistedModeModal;
    window.isAssistantAgent = isAssistantAgent;
    window.resolveAgentIdFromLabel = resolveAgentIdFromLabel;
    window.setAgentActivityState = setAgentActivityState;
    window.withAgentActivity = withAgentActivity;
    window.withAgentTurn = withAgentTurn;
    window.renderAgentSidebar = renderAgentSidebar;
    window.ensureChiefOfStaff = ensureChiefOfStaff;
    window.updateAgentLockState = updateAgentLockState;
    window.showAddAgentWizard = showAddAgentWizard;
    window.showWorkbenchChatModal = showWorkbenchChatModal;
    window.showContextMenu = showContextMenu;
    window.showAgentContextMenu = showAgentContextMenu;
    window.showAgentProfileModal = showAgentProfileModal;
    window.showRoleSettingsModal = showRoleSettingsModal;
    window.showAgentSettingsModal = showAgentSettingsModal;
    window.showChangeRoleModal = showChangeRoleModal;
    window.showConfirmRetireModal = showConfirmRetireModal;
    window.showRetiredAgentsModal = showRetiredAgentsModal;
    window.showImportAgentDialog = showImportAgentDialog;
    window.showConferenceInviteModal = showConferenceInviteModal;
    window.exportAgent = exportAgent;
    window.duplicateAgent = duplicateAgent;
})();
