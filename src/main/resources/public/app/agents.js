// Agents module (refactor split)
(function() {
    'use strict';

    const createModalShell = window.modals ? window.modals.createModalShell : null;

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
})();
