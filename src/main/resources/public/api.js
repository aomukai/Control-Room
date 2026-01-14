// Control Room API Layer
// Centralized API functions for all backend communication
(function() {
    'use strict';

    const canonicalizeRole = window.canonicalizeRole;

    // Base API helper function
    async function api(endpoint, options = {}) {
        try {
            const response = await fetch(endpoint, options);
            if (!response.ok) {
                let error = null;
                try {
                    error = await response.json();
                } catch (_) {
                    // ignore
                }
                const err = new Error((error && error.error) || response.statusText || 'Request failed');
                err.data = error;
                err.status = response.status;
                throw err;
            }
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                return response.json();
            }
            return response.text();
        } catch (err) {
            console.error(`API Error: ${err.message}`);
            throw err;
        }
    }

    // Issue API
    const issueApi = {
        async list(filters = {}) {
            const params = new URLSearchParams();
            if (filters.tag) params.set('tag', filters.tag);
            if (filters.assignedTo) params.set('assignedTo', filters.assignedTo);
            if (filters.status) params.set('status', filters.status);
            if (filters.priority) params.set('priority', filters.priority);
            const query = params.toString();
            const url = '/api/issues' + (query ? '?' + query : '');
            const response = await fetch(url);
            if (!response.ok) throw new Error('Failed to fetch issues');
            return response.json();
        },

        async get(id) {
            const response = await fetch(`/api/issues/${id}`);
            if (!response.ok) {
                if (response.status === 404) throw new Error(`Issue #${id} not found`);
                throw new Error('Failed to fetch issue');
            }
            return response.json();
        },

        async create(data) {
            const response = await fetch('/api/issues', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            if (!response.ok) {
                const err = await response.json();
                throw new Error(err.error || 'Failed to create issue');
            }
            return response.json();
        },

        async update(id, data) {
            const response = await fetch(`/api/issues/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            if (!response.ok) {
                const err = await response.json();
                throw new Error(err.error || 'Failed to update issue');
            }
            return response.json();
        },

        async delete(id) {
            const response = await fetch(`/api/issues/${id}`, { method: 'DELETE' });
            if (!response.ok) {
                if (response.status === 404) throw new Error(`Issue #${id} not found`);
                throw new Error('Failed to delete issue');
            }
            return response.json();
        },

        async addComment(issueId, data) {
            const response = await fetch(`/api/issues/${issueId}/comments`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            if (!response.ok) {
                const err = await response.json();
                throw new Error(err.error || 'Failed to add comment');
            }
            return response.json();
        }
    };

    // Credit API
    const creditApi = {
        async listProfiles() {
            return api('/api/credits/profiles');
        },
        async getProfile(agentId) {
            return api(`/api/credits/profiles/${agentId}`);
        },
        async listEvents(filters = {}) {
            const params = new URLSearchParams();
            if (filters.agentId) params.set('agentId', filters.agentId);
            const query = params.toString();
            const url = '/api/credits' + (query ? '?' + query : '');
            return api(url);
        },
        async createEvent(data) {
            return api('/api/credits', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
        }
    };

    // Agent API
    const agentApi = {
        async list() {
            return api('/api/agents', { cache: 'no-store' });
        },
        async listAll() {
            return api('/api/agents/all', { cache: 'no-store' });
        },
        async create(data) {
            return api('/api/agents', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
        },
        async get(id) {
            return api(`/api/agents/${encodeURIComponent(id)}`);
        },
        async update(id, data) {
            return api(`/api/agents/${encodeURIComponent(id)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
        },
        async setStatus(id, enabled) {
            return api(`/api/agents/${encodeURIComponent(id)}/status`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled })
            });
        },
        async reorder(order) {
            return api('/api/agents/order', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ order })
            });
        },
        async import(data) {
            return api('/api/agents/import', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
        }
    };

    // Agent Endpoints API
    const agentEndpointsApi = {
        async list() {
            return api('/api/agent-endpoints');
        },
        async get(id) {
            return api(`/api/agent-endpoints/${encodeURIComponent(id)}`);
        },
        async save(id, data) {
            return api(`/api/agent-endpoints/${encodeURIComponent(id)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
        }
    };

    // Settings API
    const settingsApi = {
        async getSecurity() {
            return api('/api/settings/security');
        },
        async updateSecurity(mode, password) {
            return api('/api/settings/security', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ keysSecurityMode: mode, password })
            });
        },
        async unlockVault(password) {
            return api('/api/settings/security/unlock', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password })
            });
        },
        async lockVault() {
            return api('/api/settings/security/lock', { method: 'POST' });
        },
        async listKeys() {
            return api('/api/settings/keys');
        },
        async addKey(payload) {
            return api('/api/settings/keys', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
        },
        async deleteKey(provider, id, password) {
            return api(`/api/settings/keys/${encodeURIComponent(provider)}/${encodeURIComponent(id)}`, {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(password ? { password } : {})
            });
        }
    };

    // Provider API
    const providerApi = {
        async listModels(provider, baseUrl, keyRef) {
            const params = new URLSearchParams();
            params.set('provider', provider);
            if (baseUrl) params.set('baseUrl', baseUrl);
            if (keyRef) params.set('keyRef', keyRef);
            return api(`/api/providers/models?${params.toString()}`);
        }
    };

    // Role Settings API
    const roleSettingsApi = {
        async list() {
            return api('/api/agents/role-settings');
        },
        async get(role) {
            const roleKey = canonicalizeRole ? canonicalizeRole(role) : role;
            return api(`/api/agents/role-settings/${encodeURIComponent(roleKey || '')}`);
        },
        async save(role, settings) {
            const roleKey = canonicalizeRole ? canonicalizeRole(role) : role;
            return api(`/api/agents/role-settings/${encodeURIComponent(roleKey || '')}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(settings)
            });
        }
    };

    // Workspace API
    const workspaceApi = {
        async info() {
            return api('/api/workspace/info');
        },
        async select(name) {
            return api('/api/workspace/select', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name })
            });
        },
        async metadata() {
            return api('/api/workspace/metadata');
        },
        async saveMetadata(metadata) {
            return api('/api/workspace/metadata', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(metadata || {})
            });
        }
    };

    // Patch Review API
    const patchApi = {
        async list() {
            return api('/api/patches');
        },
        async get(id) {
            return api(`/api/patches/${encodeURIComponent(id)}`);
        },
        async exportAudit(id) {
            return api(`/api/patches/${encodeURIComponent(id)}/audit`);
        },
        async exportAllAudits() {
            return api('/api/patches/audit/export');
        },
        async apply(id) {
            return api(`/api/patches/${encodeURIComponent(id)}/apply`, { method: 'POST' });
        },
        async reject(id) {
            return api(`/api/patches/${encodeURIComponent(id)}/reject`, { method: 'POST' });
        },
        async delete(id) {
            return api(`/api/patches/${encodeURIComponent(id)}`, { method: 'DELETE' });
        },
        async cleanup(statuses) {
            return api('/api/patches/cleanup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(statuses ? { statuses } : {})
            });
        },
        async simulate(filePath) {
            return api('/api/patches/simulate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ filePath })
            });
        }
    };

    // Versioning API
    const versioningApi = {
        async status() {
            return api('/api/versioning/status');
        },
        async changes() {
            return api('/api/versioning/changes');
        },
        async snapshots() {
            return api('/api/versioning/snapshots');
        },
        async publish(name) {
            return api('/api/versioning/publish', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name })
            });
        },
        async discard(payload) {
            return api('/api/versioning/discard', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload || {})
            });
        },
        async restore(payload) {
            return api('/api/versioning/restore', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload || {})
            });
        },
        async snapshot(id) {
            return api(`/api/versioning/snapshot/${encodeURIComponent(id)}`);
        },
        async snapshotFile(id, path) {
            return api(`/api/versioning/snapshot/${encodeURIComponent(id)}/file?path=${encodeURIComponent(path)}`);
        },
        async fileHistory(path) {
            return api(`/api/versioning/file-history?path=${encodeURIComponent(path)}`);
        },
        async deleteSnapshot(id) {
            return api(`/api/versioning/snapshot/${encodeURIComponent(id)}`, { method: 'DELETE' });
        },
        async cleanup(keepCount) {
            return api('/api/versioning/cleanup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ keepCount })
            });
        }
    };

    // File API
    const fileApi = {
        async getTree() {
            return api('/api/tree');
        },
        async getFile(path) {
            return api(`/api/file?path=${encodeURIComponent(path)}`);
        },
        async saveFile(path, content) {
            return api(`/api/file?path=${encodeURIComponent(path)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'text/plain' },
                body: content
            });
        },
        async createFile(path, type = 'file') {
            return api('/api/file', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ path, type })
            });
        },
        async rename(oldPath, newPath) {
            return api('/api/rename', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ oldPath, newPath })
            });
        },
        async reveal(path) {
            return api('/api/file/reveal', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ path })
            });
        },
        async openFolder(path) {
            return api('/api/file/open-folder', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ path })
            });
        },
        async search(query) {
            return api(`/api/search?q=${encodeURIComponent(query)}`);
        }
    };

    // Segments API
    const segmentsApi = {
        async get(path) {
            return api(`/api/segments?path=${encodeURIComponent(path)}`);
        }
    };

    // Chat API
    const chatApi = {
        async send(agentId, message, context = {}) {
            return api('/api/ai/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ agentId, message, ...context })
            });
        }
    };

    // Memory (Librarian) API
    const memoryApi = {
        async create(payload) {
            return api('/api/memory', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
        },
        async addVersion(id, payload) {
            return api(`/api/memory/${encodeURIComponent(id)}/versions`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
        },
        async addEvent(id, payload) {
            return api(`/api/memory/${encodeURIComponent(id)}/events`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
        },
        async get(id, level = 'auto', opts = {}) {
            const params = new URLSearchParams();
            params.set('level', level || 'auto');
            if (opts.includeArchived) params.set('includeArchived', 'true');
            if (opts.includeExpired) params.set('includeExpired', 'true');
            return api(`/api/memory/${encodeURIComponent(id)}?${params.toString()}`);
        },
        async getVersions(id) {
            return api(`/api/memory/${encodeURIComponent(id)}/versions`);
        },
        async getEvidence(id, witness) {
            const params = new URLSearchParams({ witness });
            return api(`/api/memory/${encodeURIComponent(id)}/evidence?${params.toString()}`);
        },
        async setActive(id, versionId, opts = {}) {
            const params = new URLSearchParams();
            if (opts.lockMinutes) params.set('lockMinutes', opts.lockMinutes);
            if (opts.reason) params.set('reason', opts.reason);
            return api(`/api/memory/${encodeURIComponent(id)}/active/${encodeURIComponent(versionId)}?${params.toString()}`, {
                method: 'PUT'
            });
        },
        async pin(id, pinnedMinLevel) {
            return api(`/api/memory/${encodeURIComponent(id)}/pin`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pinnedMinLevel })
            });
        },
        async setState(id, state) {
            return api(`/api/memory/${encodeURIComponent(id)}/state`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ state })
            });
        },
        async decay(options = {}) {
            return api('/api/memory/decay', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(options)
            });
        },
        async getDecayStatus() {
            return api('/api/memory/decay/status');
        },
        async saveDecayConfig(options = {}) {
            return api('/api/memory/decay/config', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(options)
            });
        },
        async downloadDecayReport(options = {}) {
            return api('/api/memory/decay', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ...options, dryRun: true, collectReport: true })
            });
        }
    };

    // Notifications API
    const notificationsApi = {
        async list() {
            const response = await fetch('/api/notifications');
            if (!response.ok) throw new Error('Failed to fetch notifications');
            return response.json();
        },
        async update(id, payload) {
            return api(`/api/notifications/${encodeURIComponent(id)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload || {})
            });
        }
    };

    // Workspace actions API
    const workspaceActionsApi = {
        async open() {
            return api('/api/workspace/open', { method: 'POST' });
        },
        async terminal() {
            return api('/api/workspace/terminal', { method: 'POST' });
        }
    };

    // Expose all APIs on window for app.js to use
    window.api = api;
    window.issueApi = issueApi;
    window.creditApi = creditApi;
    window.agentApi = agentApi;
    window.agentEndpointsApi = agentEndpointsApi;
    window.settingsApi = settingsApi;
    window.providerApi = providerApi;
    window.roleSettingsApi = roleSettingsApi;
    window.workspaceApi = workspaceApi;
    window.patchApi = patchApi;
    window.fileApi = fileApi;
    window.segmentsApi = segmentsApi;
    window.chatApi = chatApi;
    window.memoryApi = memoryApi;
    window.notificationsApi = notificationsApi;
    window.workspaceActionsApi = workspaceActionsApi;
    window.versioningApi = versioningApi;

})();
