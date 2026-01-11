// Agents module (refactor split)
(function() {
    'use strict';

    const createModalShell = window.modals ? window.modals.createModalShell : null;

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
        state.agents.locked = !hasExactlyOneAssistant();
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
            empty.className = 'agent-empty';
            empty.textContent = 'No agents available';
            container.appendChild(empty);
            return;
        }

        let reorderInFlight = false;
        const persistAgentOrder = async () => {
            if (reorderInFlight) {
                return;
            }
            const orderedIds = Array.from(container.querySelectorAll('.agent-item'))
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
                await loadAgents();
            } catch (err) {
                log(`Failed to save agent order: ${err.message}`, 'warning');
            } finally {
                reorderInFlight = false;
            }
        };

        agents.forEach(agent => {
            const item = document.createElement('div');
            item.className = 'agent-item';
            item.dataset.agentId = agent.id || '';
            item.draggable = true;

            if (isAssistantAgent(agent)) {
                item.classList.add('team-lead');
            }
            if (state.agents.locked) {
                const assistantCount = countAssistantAgents();
                item.classList.add('is-disabled');
                item.title = assistantCount > 1
                    ? 'Agents are locked until extra Chiefs of Staff are disabled.'
                    : 'Agents are locked until a Chief of Staff exists.';
            }

            const icon = document.createElement('span');
            icon.className = 'agent-icon';

            const avatarData = agent.avatar && agent.avatar.trim() ? agent.avatar.trim() : '';

            if (avatarData.startsWith('data:') || avatarData.startsWith('http')) {
                // Image avatar
                const img = document.createElement('img');
                img.src = avatarData;
                img.alt = agent.name || 'Agent';
                img.className = 'agent-icon-img';
                icon.appendChild(img);
                icon.classList.add('has-image');
            } else if (avatarData) {
                // Emoji or text avatar
                icon.textContent = avatarData;
            } else {
                // Fallback to first letter
                icon.textContent = agent.name ? agent.name.charAt(0).toUpperCase() : '?';
            }

            if (agent.color && !avatarData.startsWith('data:') && !avatarData.startsWith('http')) {
                icon.style.background = agent.color;
            }

            const info = document.createElement('div');
            info.className = 'agent-info';

            const name = document.createElement('div');
            name.className = 'agent-name';
            const fullName = agent.name || 'Unnamed Agent';
            name.textContent = fullName;
            name.title = fullName; // Tooltip for truncated names

            const role = document.createElement('div');
            role.className = 'agent-role';
            role.textContent = agent.role || 'role';

            info.appendChild(name);
            info.appendChild(role);

            item.appendChild(icon);
            item.appendChild(info);

            const statusCluster = document.createElement('div');
            statusCluster.className = 'agent-status-cluster';

            const status = document.createElement('div');
            const statusInfo = getAgentStatusInfo(agent);
            status.className = `agent-status ${statusInfo.className}`;
            status.title = statusInfo.title;
            statusCluster.appendChild(status);

            const icons = [];
            const activityState = getAgentActivityState(agent);
            const activityMessage = getAgentActivityMessage(agent);
            if (activityState === 'reading') {
                icons.push(createAgentStatusIcon(
                    'assets/icons/lucide/reading.svg',
                    activityMessage || 'Reviewing context and references.'
                ));
            } else if (activityState === 'processing') {
                icons.push(createAgentStatusIcon(
                    'assets/icons/lucide/processing.svg',
                    activityMessage || 'Thinking through the best next steps.',
                    'agent-status-icon-processing'
                ));
            } else if (activityState === 'executing') {
                icons.push(createAgentStatusIcon(
                    'assets/icons/lucide/executing.svg',
                    activityMessage || 'Producing output / taking action now.'
                ));
            }

            const supervisionState = getAgentSupervisionState(agent);
            if (supervisionState === 'watched') {
                icons.push(createAgentStatusIcon(
                    'assets/icons/lucide/watched.svg',
                    'This agent is being monitored due to recent uncertainty.'
                ));
            } else if (supervisionState === 'assisted') {
                const note = agent.assistedNotes ? ` ${agent.assistedNotes}` : '';
                const dosage = agent.assistedTaskDosage ? ` Dosage: ${agent.assistedTaskDosage}.` : '';
                const queue = agent.assistedQueueSize !== null && agent.assistedQueueSize !== undefined
                    ? ` Queue: ${agent.assistedQueueSize}.`
                    : '';
                icons.push(createAgentStatusIcon(
                    'assets/icons/lucide/assisted.svg',
                    `Another agent is quietly assisting to ensure accuracy.${dosage}${queue}${note}`
                ));
            }

            if (agent.isPrimaryForRole) {
                icons.push(createAgentStatusIcon(
                    'assets/icons/lucide/primary.svg',
                    'Primary agent for this role.'
                ));
            }

            if (icons.length > 0) {
                const iconRow = document.createElement('div');
                iconRow.className = 'agent-status-icons';
                icons.slice(0, 3).forEach(icon => iconRow.appendChild(icon));
                statusCluster.appendChild(iconRow);
            }

            if (isAssistantAgent(agent)) {
                const badge = document.createElement('span');
                badge.className = 'agent-lead-badge';
                badge.textContent = 'Lead';
                statusCluster.appendChild(badge);
            }

            item.appendChild(statusCluster);

            item.addEventListener('click', () => {
                if (!ensureChiefOfStaff('Agent chat')) {
                    return;
                }
                container.querySelectorAll('.agent-item').forEach(el => el.classList.remove('active'));
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

        container.querySelectorAll('.agent-item').forEach(item => {
            item.addEventListener('dragover', (e) => {
                e.preventDefault();
                const dragging = container.querySelector('.agent-item.dragging');
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
                await loadAgents();
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
    window.renderAgentSidebar = renderAgentSidebar;
    window.ensureChiefOfStaff = ensureChiefOfStaff;
    window.updateAgentLockState = updateAgentLockState;
})();
