// History viewer module (versioning snapshots)
(function() {
    'use strict';

    const api = window.api;
    const state = window.state;
    const createModalShell = window.modals ? window.modals.createModalShell : null;
    const escapeHtml = window.modals ? window.modals.escapeHtml : (text) => String(text || '');
    const normalizeWorkspacePath = window.normalizeWorkspacePath;

    function log(message, level) {
        if (window.log) {
            window.log(message, level);
        }
    }

    function getLanguageForFile(path) {
        const ext = path.split('.').pop().toLowerCase();
        const languages = {
            'js': 'javascript',
            'ts': 'typescript',
            'json': 'json',
            'html': 'html',
            'css': 'css',
            'md': 'markdown',
            'java': 'java',
            'py': 'python',
            'rb': 'ruby',
            'go': 'go',
            'rs': 'rust',
            'xml': 'xml',
            'yaml': 'yaml',
            'yml': 'yaml',
            'sql': 'sql',
            'txt': 'plaintext'
        };
        return languages[ext] || 'plaintext';
    }

    function formatTimeAgo(date) {
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffHours < 24) return `${diffHours}h ago`;
        if (diffDays < 7) return `${diffDays}d ago`;
        return date.toLocaleDateString();
    }

    async function getWorkingContent(path) {
        if (state && state.openFiles && state.openFiles.has(path)) {
            const file = state.openFiles.get(path);
            if (file && typeof file.content === 'string') {
                return file.content;
            }
        }
        return api(`/api/file?path=${encodeURIComponent(path)}`);
    }

    async function fetchFileHistory(path) {
        if (window.versioningApi && window.versioningApi.fileHistory) {
            return window.versioningApi.fileHistory(path);
        }
        return api(`/api/versioning/file-history?path=${encodeURIComponent(path)}`);
    }

    async function fetchSnapshotFile(path, snapshotId) {
        if (window.versioningApi && window.versioningApi.snapshotFile) {
            return window.versioningApi.snapshotFile(snapshotId, path);
        }
        return api(`/api/versioning/snapshot/${encodeURIComponent(snapshotId)}/file?path=${encodeURIComponent(path)}`);
    }

    async function showHistoryPicker(path) {
        if (!createModalShell) {
            throw new Error('Modal system unavailable.');
        }

        const { body, confirmBtn, cancelBtn, close } = createModalShell('File History', 'Close', 'Cancel', {
            closeOnConfirm: true
        });
        if (cancelBtn) {
            cancelBtn.remove();
        }

        const intro = document.createElement('div');
        intro.className = 'modal-text';
        intro.textContent = `History for ${path}`;
        body.appendChild(intro);

        const list = document.createElement('div');
        list.className = 'history-list';
        list.textContent = 'Loading history...';
        body.appendChild(list);

        try {
            const data = await fetchFileHistory(path);
            const history = Array.isArray(data.history) ? data.history : [];

            if (history.length === 0) {
                list.textContent = 'No history yet.';
                return;
            }

            list.innerHTML = '';
            history.forEach(entry => {
                const item = document.createElement('button');
                item.type = 'button';
                item.className = 'history-list-item';
                const date = new Date(entry.publishedAt);
                const timeAgo = formatTimeAgo(date);
                const status = entry.status ? entry.status.toUpperCase() : '';
                item.innerHTML = `
                    <div class="history-list-title">${escapeHtml(entry.snapshotName || entry.snapshotId)}</div>
                    <div class="history-list-meta">${escapeHtml(status)} • ${timeAgo}</div>
                `;
                item.addEventListener('click', () => {
                    showHistoryViewer(path, entry.snapshotId, entry.snapshotName);
                    close();
                });
                list.appendChild(item);
            });
        } catch (err) {
            list.textContent = 'Failed to load history.';
            log(`Failed to load file history: ${err.message}`, 'error');
        }

        if (confirmBtn) {
            confirmBtn.focus();
        }
    }

    async function showHistoryViewer(path, snapshotId, snapshotName) {
        if (!createModalShell) {
            throw new Error('Modal system unavailable.');
        }

        let currentContent;
        let snapshot;
        try {
            [currentContent, snapshot] = await Promise.all([
                getWorkingContent(path),
                fetchSnapshotFile(path, snapshotId)
            ]);
        } catch (err) {
            log(`Failed to load snapshot content: ${err.message}`, 'error');
            if (window.showToast) {
                window.showToast(`Failed to open history: ${err.message}`, 'error');
            }
            return;
        }

        let currentModel = null;
        let snapshotModel = null;
        let currentEditor = null;
        let snapshotEditor = null;

        function disposeEditors() {
            if (currentEditor) currentEditor.dispose();
            if (snapshotEditor) snapshotEditor.dispose();
            if (currentModel) currentModel.dispose();
            if (snapshotModel) snapshotModel.dispose();
        }

        const { modal, body, confirmBtn, cancelBtn } = createModalShell(
            `History: ${path}`,
            'Close',
            'Cancel',
            { closeOnConfirm: true, onClose: disposeEditors }
        );
        if (cancelBtn) {
            cancelBtn.remove();
        }
        modal.classList.add('history-modal');

        const header = document.createElement('div');
        header.className = 'history-header';
        header.innerHTML = `
            <div class="history-title">Snapshot: ${escapeHtml(snapshotName || snapshotId)}</div>
            <div class="history-subtitle">Current file vs history</div>
        `;
        body.appendChild(header);

        const split = document.createElement('div');
        split.className = 'history-split';

        const currentPanel = document.createElement('div');
        currentPanel.className = 'history-panel';
        currentPanel.innerHTML = '<div class="history-panel-title">Current</div>';
        const currentEditorHost = document.createElement('div');
        currentEditorHost.className = 'history-editor';
        currentPanel.appendChild(currentEditorHost);

        const snapshotPanel = document.createElement('div');
        snapshotPanel.className = 'history-panel';
        snapshotPanel.innerHTML = '<div class="history-panel-title">Snapshot</div>';
        const snapshotEditorHost = document.createElement('div');
        snapshotEditorHost.className = 'history-editor';
        snapshotPanel.appendChild(snapshotEditorHost);

        split.appendChild(currentPanel);
        split.appendChild(snapshotPanel);
        body.appendChild(split);

        try {
            if (!window.monaco || !window.monaco.editor) {
                const currentFallback = document.createElement('textarea');
                currentFallback.className = 'history-fallback';
                currentFallback.value = currentContent || '';
                currentEditorHost.appendChild(currentFallback);

                const snapshotFallback = document.createElement('textarea');
                snapshotFallback.className = 'history-fallback';
                snapshotFallback.value = snapshot.content || '';
                snapshotFallback.readOnly = true;
                snapshotEditorHost.appendChild(snapshotFallback);
                return;
            }

            const language = getLanguageForFile(path);
            currentModel = monaco.editor.createModel(currentContent || '', language);
            snapshotModel = monaco.editor.createModel(snapshot.content || '', language);

            currentEditor = monaco.editor.create(currentEditorHost, {
                model: currentModel,
                theme: 'vs-dark',
                readOnly: false,
                wordWrap: 'on',
                minimap: { enabled: false },
                automaticLayout: true,
                scrollBeyondLastLine: false
            });

            snapshotEditor = monaco.editor.create(snapshotEditorHost, {
                model: snapshotModel,
                theme: 'vs-dark',
                readOnly: true,
                wordWrap: 'on',
                minimap: { enabled: false },
                automaticLayout: true,
                scrollBeyondLastLine: false
            });
        } catch (err) {
            log(`Failed to render history viewer: ${err.message}`, 'error');
        }

        if (confirmBtn) {
            confirmBtn.addEventListener('click', disposeEditors);
        }

        const overlay = modal.closest('.modal-overlay');
        if (overlay) {
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) {
                    disposeEditors();
                }
            });
        }
    }

    function showFileHistory(path) {
        const normalized = normalizeWorkspacePath ? normalizeWorkspacePath(path) : path;
        if (!normalized) {
            log('No file selected for history', 'warning');
            return;
        }
        showHistoryPicker(normalized);
    }

    window.showFileHistory = showFileHistory;
})();
