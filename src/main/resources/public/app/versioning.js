// Versioning module - Writer-friendly version control
(function() {
    'use strict';

    const state = window.state;
    const api = window.api;
    const versioningApi = window.versioningApi;
    const showConfigurableModal = window.modals ? window.modals.showConfigurableModal : null;
    const normalizeWorkspacePath = window.normalizeWorkspacePath;

    // DOM elements
    let fileTreeArea = null;
    let versioningArea = null;
    let snapshotNameInput = null;
    let publishBtn = null;
    let changesList = null;
    let changesSummary = null;
    let snapshotsList = null;
    let discardAllBtn = null;
    let refreshBtn = null;
    let cleanupBtn = null;

    // State
    let currentChanges = [];
    let currentSnapshots = [];
    let isVersioningPanelActive = false;

    function log(message, level) {
        if (window.log) {
            window.log(message, level);
        }
    }

    // Initialize versioning module
    function init() {
        // Cache DOM elements
        fileTreeArea = document.getElementById('file-tree-area');
        versioningArea = document.getElementById('versioning-area');
        snapshotNameInput = document.getElementById('snapshot-name-input');
        publishBtn = document.getElementById('btn-publish-snapshot');
        changesList = document.getElementById('changes-list');
        changesSummary = document.getElementById('changes-summary');
        snapshotsList = document.getElementById('snapshots-list');
        discardAllBtn = document.getElementById('btn-discard-all');
        refreshBtn = document.getElementById('btn-refresh-changes');
        cleanupBtn = document.getElementById('btn-cleanup-history');

        // Wire up sidebar button
        const commitBtn = document.getElementById('btn-commit');
        if (commitBtn) {
            commitBtn.addEventListener('click', toggleVersioningPanel);
        }

        // Wire up panel buttons
        if (publishBtn) {
            publishBtn.addEventListener('click', publishSnapshot);
        }
        if (discardAllBtn) {
            discardAllBtn.addEventListener('click', discardAllChanges);
        }
        if (refreshBtn) {
            refreshBtn.addEventListener('click', refreshChanges);
        }
        if (cleanupBtn) {
            cleanupBtn.addEventListener('click', showCleanupModal);
        }

        log('Versioning module initialized', 'info');
    }

    // Toggle between file tree and versioning panel
    function toggleVersioningPanel() {
        isVersioningPanelActive = !isVersioningPanelActive;

        if (isVersioningPanelActive) {
            showVersioningPanel();
        } else {
            showFileTreePanel();
        }

        updateSidebarButtons();
    }

    function updateSidebarButtons() {
        const commitBtn = document.getElementById('btn-commit');
        const explorerBtn = document.getElementById('btn-toggle-explorer');
        if (commitBtn) {
            commitBtn.classList.toggle('active', isVersioningPanelActive);
        }
        if (explorerBtn) {
            // Explorer button should show inactive when versioning is active
            explorerBtn.classList.toggle('is-active', !isVersioningPanelActive);
        }
    }

    function showVersioningPanel() {
        // Ensure explorer panel is visible first
        const explorerPanel = document.getElementById('explorer-panel');
        if (explorerPanel && explorerPanel.classList.contains('is-hidden')) {
            if (window.setExplorerVisible) {
                window.setExplorerVisible(true);
            }
        }

        if (fileTreeArea) fileTreeArea.style.display = 'none';
        if (versioningArea) versioningArea.style.display = 'flex';

        // Load data when panel opens
        refreshChanges();
        loadSnapshots();
    }

    function showFileTreePanel(updateState = true) {
        if (updateState) {
            isVersioningPanelActive = false;
        }
        if (fileTreeArea) fileTreeArea.style.display = 'flex';
        if (versioningArea) versioningArea.style.display = 'none';
    }

    // Called when explorer visibility changes (from explorer toggle button)
    function syncWithExplorerState() {
        // When explorer panel is shown/hidden, sync the internal panel state
        if (isVersioningPanelActive) {
            if (versioningArea) versioningArea.style.display = 'flex';
            if (fileTreeArea) fileTreeArea.style.display = 'none';
        } else {
            if (fileTreeArea) fileTreeArea.style.display = 'flex';
            if (versioningArea) versioningArea.style.display = 'none';
        }
    }

    // Fetch and display changes
    async function refreshChanges() {
        try {
            const data = versioningApi ? await versioningApi.changes() : await api('/api/versioning/changes');
            currentChanges = data.changes || [];

            renderChanges(currentChanges, data);
            updatePublishButton();
        } catch (err) {
            log('Failed to refresh changes: ' + err.message, 'error');
            renderChanges([], { fileCount: 0, addedWords: 0, removedWords: 0 });
        }
    }

    function renderChanges(changes, summary) {
        if (!changesList) return;

        if (changes.length === 0) {
            changesList.innerHTML = '<div class="no-changes">No changes detected</div>';
            if (changesSummary) changesSummary.textContent = '';
            if (discardAllBtn) discardAllBtn.style.display = 'none';
            updateUnpublishedIndicators([]);
            return;
        }

        // Update summary
        if (changesSummary) {
            const parts = [];
            const fileCount = summary.fileCount || 0;
            const folderCount = summary.folderCount || 0;
            parts.push(`${fileCount} file${fileCount !== 1 ? 's' : ''}`);
            if (folderCount > 0) {
                parts.push(`${folderCount} folder${folderCount !== 1 ? 's' : ''}`);
            }
            if (summary.addedWords > 0 || summary.removedWords > 0) {
                const wordParts = [];
                if (summary.addedWords > 0) wordParts.push(`+${summary.addedWords}`);
                if (summary.removedWords > 0) wordParts.push(`-${summary.removedWords}`);
                parts.push(wordParts.join('/') + ' words');
            }
            changesSummary.textContent = `${parts.join(', ')} since last publish`;
        }

        // Render changes list
        changesList.innerHTML = changes.map(change => {
            const statusIcon = getStatusIcon(change.status);
            const statusClass = `status-${change.status}`;
            const isFolder = change.isFolder;
            const path = change.path || '';
            const fileName = path.split('/').pop();
            const dirPath = path.substring(0, path.lastIndexOf('/')) || '';
            const previousPath = change.previousPath || '';
            const previousLabel = previousPath ? `<span class="change-previous">from ${escapeHtml(previousPath)}</span>` : '';
            const actionButton = isFolder ? '' : `
                    <button class="change-action" title="Discard changes" data-action="discard">
                        <img src="assets/icons/heroicons_outline/arrow-left.svg" alt="Discard">
                    </button>
                `;

            return `
                <div class="change-item ${statusClass}${isFolder ? ' is-folder' : ''}" data-path="${escapeAttr(path)}" data-status="${escapeAttr(change.status || '')}">
                    <span class="change-status">${statusIcon}</span>
                    <span class="change-file" title="${escapeAttr(path)}">
                        <span class="change-filename">${escapeHtml(fileName)}</span>
                        ${dirPath ? `<span class="change-dir">${escapeHtml(dirPath)}</span>` : ''}
                        ${previousLabel}
                    </span>
                    ${actionButton}
                </div>
            `;
        }).join('');

        // Wire up discard buttons
        changesList.querySelectorAll('.change-action[data-action="discard"]').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const item = btn.closest('.change-item');
                const path = item.dataset.path;
                discardFileChanges(path);
            });
        });

        // Wire up click to open file
        changesList.querySelectorAll('.change-item').forEach(item => {
            item.addEventListener('click', () => {
                const path = item.dataset.path;
                const status = item.dataset.status;
                if (item.classList.contains('is-folder')) {
                    return;
                }
                if (status === 'deleted') {
                    return;
                }
                if (window.openFileInNewTab) {
                    window.openFileInNewTab(path);
                }
            });
        });

        if (discardAllBtn) discardAllBtn.style.display = 'block';
        updateUnpublishedIndicators(changes);
    }

    function updateUnpublishedIndicators(changes) {
        const normalize = normalizeWorkspacePath || ((path) => path);
        const paths = new Set(
            (changes || [])
                .map(change => normalize(change.path || ''))
                .filter(Boolean)
        );

        document.querySelectorAll('.tab').forEach(tab => {
            const path = normalize(tab.dataset.path || '');
            tab.classList.toggle('unpublished', path && paths.has(path));
        });

        document.querySelectorAll('.tree-item').forEach(item => {
            const path = normalize(item.dataset.path || '');
            item.classList.toggle('unpublished', path && paths.has(path));
        });
    }

    function getStatusIcon(status) {
        switch (status) {
            case 'added': return '<span class="status-badge added">A</span>';
            case 'modified': return '<span class="status-badge modified">M</span>';
            case 'deleted': return '<span class="status-badge deleted">D</span>';
            case 'renamed': return '<span class="status-badge renamed">R</span>';
            default: return '<span class="status-badge">?</span>';
        }
    }

    // Load and display snapshots
    async function loadSnapshots() {
        try {
            const data = versioningApi ? await versioningApi.snapshots() : await api('/api/versioning/snapshots');
            currentSnapshots = data.snapshots || [];

            renderSnapshots(currentSnapshots);
        } catch (err) {
            log('Failed to load snapshots: ' + err.message, 'error');
            renderSnapshots([]);
        }
    }

    function renderSnapshots(snapshots) {
        if (!snapshotsList) return;

        if (snapshots.length === 0) {
            snapshotsList.innerHTML = '<div class="no-snapshots">No snapshots yet</div>';
            return;
        }

        snapshotsList.innerHTML = snapshots.map(snapshot => {
            const date = new Date(snapshot.publishedAt);
            const timeAgo = formatTimeAgo(date);
            const filesCount = snapshot.files ? snapshot.files.length : 0;
            const fileNames = snapshot.files ? snapshot.files.map(file => file.path).filter(Boolean) : [];
            const tooltip = fileNames.length > 0 ? `Changed files:\n${fileNames.join('\n')}` : '';

            return `
                <div class="snapshot-item" data-id="${escapeAttr(snapshot.id)}" title="${escapeAttr(tooltip)}">
                    <div class="snapshot-header">
                        <span class="snapshot-name">${escapeHtml(snapshot.name)}</span>
                        <span class="snapshot-time" title="${date.toLocaleString()}">${timeAgo}</span>
                    </div>
                    <div class="snapshot-meta">
                        ${filesCount} file${filesCount !== 1 ? 's' : ''}
                        ${snapshot.addedWords > 0 ? `, +${snapshot.addedWords}` : ''}
                        ${snapshot.removedWords > 0 ? ` -${snapshot.removedWords}` : ''} words
                    </div>
                </div>
            `;
        }).join('');

        // Wire up click to expand/show details
        snapshotsList.querySelectorAll('.snapshot-item').forEach(item => {
            item.addEventListener('click', () => {
                const id = item.dataset.id;
                showSnapshotDetails(id);
            });
        });
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

    // Update publish button state
    function updatePublishButton() {
        if (publishBtn) {
            const hasChanges = currentChanges.length > 0;
            publishBtn.disabled = !hasChanges;
            publishBtn.title = hasChanges ? 'Publish changes to history' : 'No changes to save.';
        }
    }

    // Publish snapshot
    async function publishSnapshot() {
        if (currentChanges.length === 0) return;

        const name = snapshotNameInput ? snapshotNameInput.value.trim() : '';

        try {
            publishBtn.disabled = true;
            publishBtn.textContent = 'Publishing...';

            const result = versioningApi
                ? await versioningApi.publish(name)
                : await api('/api/versioning/publish', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name })
                });
            log(`Published snapshot: ${result.snapshot.name}`, 'success');

            // Clear input and refresh
            if (snapshotNameInput) snapshotNameInput.value = '';
            await refreshChanges();
            await loadSnapshots();

            // Show success toast
            if (window.showToast) {
                window.showToast(`Published: ${result.snapshot.name}`, 'success');
            }

            // Create Chief of Staff notification
            await createChiefOfStaffNotification(result.snapshot);
        } catch (err) {
            log('Failed to publish snapshot: ' + err.message, 'error');
            if (window.showToast) {
                window.showToast('Failed to publish: ' + err.message, 'error');
            }
        } finally {
            publishBtn.disabled = currentChanges.length === 0;
            publishBtn.innerHTML = '<img src="assets/icons/heroicons_outline/arrow-up-on-square.svg" alt="" class="btn-icon-img"> Publish to History';
        }
    }

    // Discard changes for a single file
    async function discardFileChanges(path) {
        if (!showConfigurableModal) {
            // Fallback to confirm
            if (!confirm(`Discard changes to ${path}?`)) return;
        } else {
            const confirmed = await new Promise(resolve => {
                showConfigurableModal({
                    title: 'Discard Changes',
                    body: `<p>Discard changes to <strong>${escapeHtml(path)}</strong> and restore the last published version?</p><p class="modal-warning">This cannot be undone.</p>`,
                    buttons: [
                        { label: 'Cancel', value: false },
                        { label: 'Discard', value: true, className: 'danger' }
                    ],
                    onResult: resolve
                });
            });
            if (!confirmed) return;
        }

        try {
            if (versioningApi) {
                await versioningApi.discard({ paths: [path] });
            } else {
                await api('/api/versioning/discard', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ paths: [path] })
                });
            }

            log(`Discarded changes to ${path}`, 'info');
            await refreshChanges();

            // Reload file if it's open
            if (window.reloadOpenFile) {
                window.reloadOpenFile(path);
            }
        } catch (err) {
            log('Failed to discard changes: ' + err.message, 'error');
        }
    }

    // Discard all changes
    async function discardAllChanges() {
        if (currentChanges.length === 0) return;

        const confirmed = await new Promise(resolve => {
            if (!showConfigurableModal) {
                resolve(confirm('Discard ALL changes and restore the last published state?'));
                return;
            }
            showConfigurableModal({
                title: 'Discard All Changes',
                body: `<p>Discard <strong>${currentChanges.length} changed file${currentChanges.length !== 1 ? 's' : ''}</strong> and restore the last published state?</p><p class="modal-warning">This cannot be undone.</p>`,
                buttons: [
                    { label: 'Cancel', value: false },
                    { label: 'Discard All', value: true, className: 'danger' }
                ],
                onResult: resolve
            });
        });

        if (!confirmed) return;

        try {
            const result = versioningApi
                ? await versioningApi.discard({ all: true })
                : await api('/api/versioning/discard', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ all: true })
                });
            log(`Discarded ${result.restoredCount} files`, 'info');
            await refreshChanges();

            // Reload all open files
            if (window.reloadAllOpenFiles) {
                window.reloadAllOpenFiles();
            }
        } catch (err) {
            log('Failed to discard all changes: ' + err.message, 'error');
        }
    }

    // Show snapshot details
    async function showSnapshotDetails(snapshotId) {
        const snapshot = currentSnapshots.find(s => s.id === snapshotId);
        if (!snapshot) return;

        const date = new Date(snapshot.publishedAt);
        const filesList = snapshot.files.map(f => {
            const icon = getStatusIcon(f.status);
            return `<div class="snapshot-file">${icon} ${escapeHtml(f.path)}</div>`;
        }).join('');

        if (showConfigurableModal) {
            showConfigurableModal({
                title: snapshot.name,
                body: `
                    <div class="snapshot-details">
                        <p class="snapshot-date">${date.toLocaleString()}</p>
                        <p class="snapshot-stats">${snapshot.files.length} files, +${snapshot.addedWords || 0}/-${snapshot.removedWords || 0} words</p>
                        <div class="snapshot-files-list">${filesList}</div>
                    </div>
                `,
                buttons: [
                    { label: 'Close', value: 'close' },
                    { label: 'Delete Snapshot', value: 'delete', className: 'danger' }
                ],
                onResult: async (action) => {
                    if (action === 'delete') {
                        await deleteSnapshot(snapshotId);
                    }
                }
            });
        }
    }

    // Delete a snapshot
    async function deleteSnapshot(snapshotId) {
        try {
            if (versioningApi) {
                await versioningApi.deleteSnapshot(snapshotId);
            } else {
                await api(`/api/versioning/snapshot/${snapshotId}`, { method: 'DELETE' });
            }

            log('Snapshot deleted', 'info');
            await loadSnapshots();
        } catch (err) {
            log('Failed to delete snapshot: ' + err.message, 'error');
        }
    }

    // Show cleanup modal
    function showCleanupModal() {
        if (!showConfigurableModal) {
            const keepCount = prompt('Keep last N snapshots:', '10');
            if (keepCount) cleanupSnapshots(parseInt(keepCount, 10));
            return;
        }

        showConfigurableModal({
            title: 'Cleanup History',
            body: `
                <p>Remove old snapshots to save space. This cannot be undone.</p>
                <div class="form-group">
                    <label for="cleanup-keep-count">Keep last</label>
                    <input type="number" id="cleanup-keep-count" class="modal-input" value="10" min="1" max="100">
                    <span>snapshots</span>
                </div>
            `,
            buttons: [
                { label: 'Cancel', value: null },
                { label: 'Cleanup', value: 'cleanup', className: 'danger' }
            ],
            onResult: async (action) => {
                if (action === 'cleanup') {
                    const input = document.getElementById('cleanup-keep-count');
                    const keepCount = input ? parseInt(input.value, 10) : 10;
                    await cleanupSnapshots(keepCount);
                }
            }
        });
    }

    // Cleanup old snapshots
    async function cleanupSnapshots(keepCount) {
        try {
            const result = versioningApi
                ? await versioningApi.cleanup(keepCount)
                : await api('/api/versioning/cleanup', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ keepCount })
                });
            log(`Cleaned up ${result.removed} snapshots, ${result.remaining} remaining`, 'info');
            await loadSnapshots();

            if (window.showToast) {
                window.showToast(`Removed ${result.removed} old snapshots`, 'info');
            }
        } catch (err) {
            log('Failed to cleanup: ' + err.message, 'error');
        }
    }

    // Create notification for Chief of Staff on publish
    async function createChiefOfStaffNotification(snapshot) {
        try {
            const filesCount = snapshot.files ? snapshot.files.length : 0;
            const wordsDelta = [];
            if (snapshot.addedWords > 0) wordsDelta.push(`+${snapshot.addedWords}`);
            if (snapshot.removedWords > 0) wordsDelta.push(`-${snapshot.removedWords}`);
            const wordsStr = wordsDelta.length > 0 ? ` (${wordsDelta.join('/')} words)` : '';

            await fetch('/api/notifications', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    level: 'info',
                    scope: 'project',
                    category: 'system',
                    message: `Snapshot published: ${snapshot.name}`,
                    details: `${filesCount} file${filesCount !== 1 ? 's' : ''} saved to history${wordsStr}`,
                    persistent: true,
                    source: 'versioning',
                    actionLabel: 'View History',
                    actionPayload: { kind: 'openVersioning', snapshotId: snapshot.id }
                })
            });

            // Refresh notification badge if available
            if (window.notifications && window.notifications.loadFromServer) {
                window.notifications.loadFromServer();
            }
        } catch (err) {
            // Non-critical, just log
            log('Failed to create notification: ' + err.message, 'warn');
        }
    }

    // Helper: escape HTML
    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>"']/g, c => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[c]));
    }

    // Helper: escape attribute
    function escapeAttr(str) {
        return escapeHtml(str);
    }

    // Export public API
    window.versioning = {
        init,
        toggleVersioningPanel,
        refreshChanges,
        loadSnapshots,
        syncWithExplorerState,
        showFileTreePanel,
        applyUnpublishedIndicators: () => updateUnpublishedIndicators(currentChanges),
        isActive: () => isVersioningPanelActive,
        openVersioningPanel: async (snapshotId) => {
            isVersioningPanelActive = true;
            showVersioningPanel();
            updateSidebarButtons();
            if (snapshotId) {
                await loadSnapshots();
                showSnapshotDetails(snapshotId);
            }
        }
    };

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
