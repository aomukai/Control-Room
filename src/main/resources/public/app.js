// Control Room Application
(function() {
    'use strict';

    // State
    const state = {
        editor: null,
        openFiles: new Map(), // path -> { content, originalContent, model }
        activeFile: null,
        fileTree: null
    };

    // DOM Elements
    const elements = {
        fileTree: document.getElementById('file-tree'),
        tabsContainer: document.getElementById('tabs-container'),
        editorPlaceholder: document.getElementById('editor-placeholder'),
        monacoEditor: document.getElementById('monaco-editor'),
        consoleOutput: document.getElementById('console-output'),
        chatHistory: document.getElementById('chat-history'),
        chatInput: document.getElementById('chat-input'),
        chatSend: document.getElementById('chat-send'),
        searchInput: document.getElementById('search-input'),
        searchBtn: document.getElementById('search-btn'),
        searchResults: document.getElementById('search-results'),
        diffPreview: document.getElementById('diff-preview'),
        diffContent: document.getElementById('diff-content'),
        closeDiff: document.getElementById('close-diff'),
        btnRevealFile: document.getElementById('btn-reveal-file'),
        btnOpenFolder: document.getElementById('btn-open-folder')
    };

    // Initialize Split.js
    function initSplitters() {
        // Load saved sizes or use defaults
        const mainSizes = JSON.parse(localStorage.getItem('split-main') || '[75, 25]');
        const editorSizes = JSON.parse(localStorage.getItem('split-editor') || '[70, 30]');

        // Main horizontal split: Center Area | Chat Panel
        Split(['#center-area', '#chat-panel'], {
            sizes: mainSizes,
            minSize: [300, 200],
            gutterSize: 4,
            direction: 'horizontal',
            onDragEnd: (sizes) => {
                localStorage.setItem('split-main', JSON.stringify(sizes));
                if (state.editor) state.editor.layout();
            }
        });

        // Vertical split: Editor | Console
        Split(['#editor-area', '#bottom-console'], {
            sizes: editorSizes,
            minSize: [100, 80],
            gutterSize: 4,
            direction: 'vertical',
            onDragEnd: (sizes) => {
                localStorage.setItem('split-editor', JSON.stringify(sizes));
                if (state.editor) state.editor.layout();
            }
        });
    }

    // Initialize Monaco Editor
    function initMonaco() {
        require(['vs/editor/editor.main'], function() {
            state.editor = monaco.editor.create(elements.monacoEditor, {
                theme: 'vs-dark',
                automaticLayout: true,
                fontSize: 14,
                lineNumbers: 'on',
                minimap: { enabled: true },
                scrollBeyondLastLine: false,
                wordWrap: 'on',
                tabSize: 4,
                insertSpaces: true
            });

            // Save on Ctrl+S
            state.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
                saveCurrentFile();
            });

            // Track changes for dirty state
            state.editor.onDidChangeModelContent(() => {
                if (state.activeFile) {
                    const file = state.openFiles.get(state.activeFile);
                    if (file) {
                        file.content = state.editor.getValue();
                        updateTabDirtyState(state.activeFile);
                    }
                }
            });

            log('Monaco editor initialized', 'success');
        });
    }

    // Console logging
    function log(message, type = 'info') {
        const line = document.createElement('div');
        line.className = `console-line ${type}`;

        const time = new Date().toLocaleTimeString();
        line.innerHTML = `<span class="console-time">[${time}]</span>${escapeHtml(message)}`;

        elements.consoleOutput.appendChild(line);
        elements.consoleOutput.scrollTop = elements.consoleOutput.scrollHeight;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // API Functions
    async function api(endpoint, options = {}) {
        try {
            const response = await fetch(endpoint, options);
            if (!response.ok) {
                const error = await response.json().catch(() => ({ error: response.statusText }));
                throw new Error(error.error || 'Request failed');
            }
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                return response.json();
            }
            return response.text();
        } catch (err) {
            log(`API Error: ${err.message}`, 'error');
            throw err;
        }
    }

    // File Tree
    async function loadFileTree() {
        try {
            const tree = await api('/api/tree');
            state.fileTree = tree;
            renderFileTree(tree);
            log('File tree loaded', 'info');
        } catch (err) {
            log(`Failed to load file tree: ${err.message}`, 'error');
        }
    }

    function renderFileTree(node, container = elements.fileTree, depth = 0) {
        if (depth === 0) {
            container.innerHTML = '';
        }

        if (node.type === 'folder' && node.children) {
            if (depth > 0) {
                const folderItem = createTreeItem(node, depth);
                container.appendChild(folderItem.element);
                container = folderItem.childContainer;
            }

            node.children.forEach(child => {
                renderFileTree(child, container, depth + 1);
            });
        } else if (node.type === 'file') {
            const fileItem = createTreeItem(node, depth);
            container.appendChild(fileItem.element);
        }
    }

    function createTreeItem(node, depth) {
        const item = document.createElement('div');
        item.className = `tree-item ${node.type === 'folder' ? 'tree-folder' : 'tree-file'}`;
        item.style.setProperty('--indent', `${depth * 16}px`);

        const icon = document.createElement('span');
        icon.className = 'tree-icon';
        icon.textContent = node.type === 'folder' ? '📁' : getFileIcon(node.name);

        const name = document.createElement('span');
        name.className = 'tree-name';
        name.textContent = node.name;

        item.appendChild(icon);
        item.appendChild(name);

        let childContainer = null;

        if (node.type === 'folder') {
            childContainer = document.createElement('div');
            childContainer.className = 'tree-children';

            item.addEventListener('click', (e) => {
                e.stopPropagation();
                const isExpanded = childContainer.classList.toggle('expanded');
                icon.textContent = isExpanded ? '📂' : '📁';
            });

            // Auto-expand root level folders
            if (depth === 1) {
                childContainer.classList.add('expanded');
                icon.textContent = '📂';
            }
        } else {
            item.addEventListener('click', (e) => {
                e.stopPropagation();
                openFile(node.path);
            });

            item.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                showContextMenu(e, node);
            });
        }

        item.dataset.path = node.path;

        const wrapper = document.createElement('div');
        wrapper.appendChild(item);
        if (childContainer) {
            wrapper.appendChild(childContainer);
        }

        return { element: wrapper, childContainer };
    }

    function getFileIcon(filename) {
        const ext = filename.split('.').pop().toLowerCase();
        const icons = {
            'md': '📝',
            'txt': '📄',
            'js': '📜',
            'json': '📋',
            'html': '🌐',
            'css': '🎨',
            'java': '☕',
            'py': '🐍',
            'rb': '💎',
            'go': '🔵',
            'rs': '🦀'
        };
        return icons[ext] || '📄';
    }

    // File Operations
    async function openFile(path) {
        try {
            if (!state.openFiles.has(path)) {
                log(`Opening file: ${path}`, 'info');
                const content = await api(`/api/file?path=${encodeURIComponent(path)}`);

                const model = monaco.editor.createModel(content, getLanguageForFile(path));
                state.openFiles.set(path, {
                    content,
                    originalContent: content,
                    model
                });

                createTab(path);
            }

            setActiveFile(path);
        } catch (err) {
            log(`Failed to open file: ${err.message}`, 'error');
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
            'sql': 'sql'
        };
        return languages[ext] || 'plaintext';
    }

    function setActiveFile(path) {
        state.activeFile = path;
        const file = state.openFiles.get(path);

        if (file && state.editor) {
            state.editor.setModel(file.model);
            elements.editorPlaceholder.classList.add('hidden');
            elements.monacoEditor.classList.add('active');
        }

        // Update tab active state
        document.querySelectorAll('.tab').forEach(tab => {
            tab.classList.toggle('active', tab.dataset.path === path);
        });

        // Update tree selection
        document.querySelectorAll('.tree-item').forEach(item => {
            item.classList.toggle('selected', item.dataset.path === path);
        });

        // Update Reveal File and Open Folder button states
        elements.btnRevealFile.disabled = !path;
        elements.btnOpenFolder.disabled = !path;
    }

    function createTab(path) {
        const tab = document.createElement('div');
        tab.className = 'tab';
        tab.dataset.path = path;

        const name = document.createElement('span');
        name.className = 'tab-name';
        name.textContent = path.split('/').pop();

        const close = document.createElement('button');
        close.className = 'tab-close';
        close.textContent = '×';
        close.addEventListener('click', (e) => {
            e.stopPropagation();
            closeFile(path);
        });

        tab.appendChild(name);
        tab.appendChild(close);

        tab.addEventListener('click', () => setActiveFile(path));

        elements.tabsContainer.appendChild(tab);
    }

    function updateTabDirtyState(path) {
        const file = state.openFiles.get(path);
        const tab = document.querySelector(`.tab[data-path="${path}"]`);

        if (file && tab) {
            const isDirty = file.content !== file.originalContent;
            tab.classList.toggle('dirty', isDirty);
        }
    }

    async function closeFile(path) {
        const file = state.openFiles.get(path);

        if (file && file.content !== file.originalContent) {
            const confirmed = confirm(`${path} has unsaved changes. Close anyway?`);
            if (!confirmed) return;
        }

        // Remove tab
        const tab = document.querySelector(`.tab[data-path="${path}"]`);
        if (tab) tab.remove();

        // Dispose model
        if (file && file.model) {
            file.model.dispose();
        }

        state.openFiles.delete(path);

        // Switch to another open file or show placeholder
        if (state.activeFile === path) {
            const remaining = Array.from(state.openFiles.keys());
            if (remaining.length > 0) {
                setActiveFile(remaining[remaining.length - 1]);
            } else {
                state.activeFile = null;
                elements.editorPlaceholder.classList.remove('hidden');
                elements.monacoEditor.classList.remove('active');
                elements.btnRevealFile.disabled = true;
                elements.btnOpenFolder.disabled = true;
            }
        }

        log(`Closed file: ${path}`, 'info');
    }

    async function saveCurrentFile() {
        if (!state.activeFile) return;

        const file = state.openFiles.get(state.activeFile);
        if (!file) return;

        try {
            await api(`/api/file?path=${encodeURIComponent(state.activeFile)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'text/plain' },
                body: file.content
            });

            file.originalContent = file.content;
            updateTabDirtyState(state.activeFile);
            log(`Saved: ${state.activeFile}`, 'success');
        } catch (err) {
            log(`Failed to save: ${err.message}`, 'error');
        }
    }

    async function saveAllFiles() {
        for (const [path, file] of state.openFiles) {
            if (file.content !== file.originalContent) {
                try {
                    await api(`/api/file?path=${encodeURIComponent(path)}`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'text/plain' },
                        body: file.content
                    });
                    file.originalContent = file.content;
                    updateTabDirtyState(path);
                    log(`Saved: ${path}`, 'success');
                } catch (err) {
                    log(`Failed to save ${path}: ${err.message}`, 'error');
                }
            }
        }
    }

    // Context Menu
    let contextMenu = null;

    function showContextMenu(e, node) {
        hideContextMenu();

        contextMenu = document.createElement('div');
        contextMenu.className = 'context-menu';
        contextMenu.style.left = `${e.clientX}px`;
        contextMenu.style.top = `${e.clientY}px`;

        const actions = [
            { label: 'Open', action: () => openFile(node.path) },
            { label: 'Rename', action: () => promptRename(node.path) },
            { divider: true },
            { label: 'Delete', action: () => promptDelete(node.path) }
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
    }

    function hideContextMenu() {
        if (contextMenu) {
            contextMenu.remove();
            contextMenu = null;
        }
    }

    document.addEventListener('click', hideContextMenu);

    // Modals
    function showModal(title, placeholder, callback) {
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';

        const modal = document.createElement('div');
        modal.className = 'modal';

        modal.innerHTML = `
            <div class="modal-title">${escapeHtml(title)}</div>
            <input type="text" class="modal-input" placeholder="${escapeHtml(placeholder)}">
            <div class="modal-buttons">
                <button class="modal-btn modal-btn-secondary" data-action="cancel">Cancel</button>
                <button class="modal-btn modal-btn-primary" data-action="confirm">OK</button>
            </div>
        `;

        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        const input = modal.querySelector('.modal-input');
        input.focus();

        const close = () => overlay.remove();

        modal.querySelector('[data-action="cancel"]').addEventListener('click', close);
        modal.querySelector('[data-action="confirm"]').addEventListener('click', () => {
            callback(input.value);
            close();
        });

        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                callback(input.value);
                close();
            } else if (e.key === 'Escape') {
                close();
            }
        });

        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) close();
        });
    }

    async function promptRename(oldPath) {
        showModal('Rename', oldPath, async (newPath) => {
            if (!newPath || newPath === oldPath) return;

            try {
                await api('/api/rename', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ from: oldPath, to: newPath })
                });
                log(`Renamed: ${oldPath} -> ${newPath}`, 'success');
                await loadFileTree();
            } catch (err) {
                log(`Rename failed: ${err.message}`, 'error');
            }
        });
    }

    async function promptDelete(path) {
        if (!confirm(`Delete "${path}"?`)) return;

        try {
            await api(`/api/file?path=${encodeURIComponent(path)}`, {
                method: 'DELETE'
            });
            log(`Deleted: ${path}`, 'success');

            // Close if open
            if (state.openFiles.has(path)) {
                closeFile(path);
            }

            await loadFileTree();
        } catch (err) {
            log(`Delete failed: ${err.message}`, 'error');
        }
    }

    async function promptNewFile(type = 'file') {
        const placeholder = type === 'folder' ? 'folder/name' : 'path/to/file.txt';
        showModal(`New ${type}`, placeholder, async (path) => {
            if (!path) return;

            try {
                await api('/api/file', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path, type, initialContent: '' })
                });
                log(`Created: ${path}`, 'success');
                await loadFileTree();

                if (type === 'file') {
                    openFile(path);
                }
            } catch (err) {
                log(`Create failed: ${err.message}`, 'error');
            }
        });
    }

    // Search
    async function performSearch(query) {
        if (!query.trim()) {
            elements.searchResults.innerHTML = '<div class="search-no-results">Enter a search term</div>';
            return;
        }

        try {
            const results = await api(`/api/search?q=${encodeURIComponent(query)}`);

            if (results.length === 0) {
                elements.searchResults.innerHTML = '<div class="search-no-results">No results found</div>';
                return;
            }

            elements.searchResults.innerHTML = '';
            results.slice(0, 100).forEach(result => {
                const item = document.createElement('div');
                item.className = 'search-result';
                item.innerHTML = `
                    <span class="search-result-file">${escapeHtml(result.file)}</span>
                    <span class="search-result-line">:${result.line}</span>
                    <div class="search-result-preview">${escapeHtml(result.preview)}</div>
                `;
                item.addEventListener('click', () => {
                    openFile(result.file).then(() => {
                        // Jump to line
                        if (state.editor) {
                            state.editor.revealLineInCenter(result.line);
                            state.editor.setPosition({ lineNumber: result.line, column: 1 });
                            state.editor.focus();
                        }
                    });
                });
                elements.searchResults.appendChild(item);
            });

            log(`Search found ${results.length} results for "${query}"`, 'info');
        } catch (err) {
            log(`Search failed: ${err.message}`, 'error');
        }
    }

    // Chat
    function addChatMessage(role, content) {
        const msg = document.createElement('div');
        msg.className = `chat-message ${role}`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'chat-message-content';
        contentDiv.textContent = content;

        msg.appendChild(contentDiv);
        elements.chatHistory.appendChild(msg);
        elements.chatHistory.scrollTop = elements.chatHistory.scrollHeight;
    }

    async function sendChatMessage() {
        const message = elements.chatInput.value.trim();
        if (!message) return;

        elements.chatInput.value = '';
        elements.chatSend.disabled = true;

        addChatMessage('user', message);
        log(`Chat: User message sent`, 'info');

        try {
            const response = await api('/api/ai/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message })
            });

            addChatMessage('assistant', response.content);
            log(`Chat: AI response received`, 'success');
        } catch (err) {
            addChatMessage('assistant', 'Sorry, I encountered an error. Please try again.');
            log(`Chat error: ${err.message}`, 'error');
        } finally {
            elements.chatSend.disabled = false;
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
    function initSidebarButtons() {
        document.getElementById('btn-commit').addEventListener('click', () => {
            log('Saving all files...', 'info');
            saveAllFiles();
        });

        document.getElementById('btn-scenes').addEventListener('click', () => {
            // Filter tree to scenes folder
            log('Showing scenes folder', 'info');
            openFolder('scenes');
        });

        document.getElementById('btn-chars').addEventListener('click', () => {
            log('Showing characters folder', 'info');
            openFolder('chars');
        });

        document.getElementById('btn-etc').addEventListener('click', () => {
            log('Showing notes folder', 'info');
            openFolder('notes');
        });

        document.getElementById('btn-open-workspace').addEventListener('click', async () => {
            log('Opening workspace folder...', 'info');
            try {
                const result = await api('/api/workspace/open', { method: 'POST' });
                if (result.ok) {
                    log('Workspace folder opened', 'success');
                } else {
                    log(`Failed to open workspace folder: ${result.error}`, 'error');
                }
            } catch (err) {
                log(`Failed to open workspace folder: ${err.message}`, 'error');
            }
        });

        document.getElementById('btn-open-terminal').addEventListener('click', async () => {
            log('Opening terminal at workspace...', 'info');
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
                    log('File revealed in explorer', 'success');
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

        document.getElementById('btn-new-file').addEventListener('click', () => promptNewFile('file'));
        document.getElementById('btn-new-folder').addEventListener('click', () => promptNewFile('folder'));
        document.getElementById('btn-refresh-tree').addEventListener('click', () => {
            log('Refreshing file tree...', 'info');
            loadFileTree();
        });
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

        // Chat
        elements.chatSend.addEventListener('click', sendChatMessage);
        elements.chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendChatMessage();
            }
        });

        // Global keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey && e.key === 's') {
                e.preventDefault();
                saveCurrentFile();
            }
        });

        // Window resize
        window.addEventListener('resize', () => {
            if (state.editor) {
                state.editor.layout();
            }
        });
    }

    // Initialize
    function init() {
        log('Control Room starting...', 'info');

        initSplitters();
        initMonaco();
        initConsoleTabs();
        initAIActions();
        initSidebarButtons();
        initEventListeners();
        loadFileTree();

        // Welcome message in chat
        addChatMessage('assistant', 'Hello! I\'m your AI writing assistant. How can I help you with your creative writing project today?');

        log('Control Room ready!', 'success');
    }

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
