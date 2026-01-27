// Editor module (refactor split)
(function() {
    'use strict';

    const showModal = window.modals ? window.modals.showModal : null;
    const escapeHtml = window.escapeHtml;
    const normalizeWorkspacePath = window.normalizeWorkspacePath;
    function getNotificationStore() {
        return window.notificationStore || null;
    }
    const state = window.state;
    const elements = window.elements;
    const api = window.api;
    const editorStateStorageKey = 'control-room:editor-state';
    const explorerScopeStorageKey = 'control-room:explorer-scope';
    let expandedFolders = new Set();
    let hasSavedExplorerState = false;
    let isRestoringEditorState = false;
    let editorStateRestored = false;
    let explorerScope = 'all';
    let fullFileTree = null;
    const OUTLINE_VIRTUAL_PATH = 'Story/SCN-outline.md';
    const treeSelection = new Set();
    let lastTreeSelectionPath = null;

    function isOutlinePath(path) {
        const normalized = normalizeWorkspacePath(path || '');
        return normalized.toLowerCase() === OUTLINE_VIRTUAL_PATH.toLowerCase()
            || normalized.toLowerCase().endsWith('/scn-outline.md');
    }

    function isPrepMultiSelectEnabled() {
        return state.workspace && state.workspace.prepStage === 'draft';
    }

    function normalizeTreePath(path) {
        return normalizeWorkspacePath(path || '');
    }

    function stripPrefixForDisplay(filename, prefix) {
        if (!prefix || !filename) return filename;
        const lower = filename.toLowerCase();
        const prefixLower = `${prefix.toLowerCase()}-`;
        if (lower.startsWith(prefixLower)) {
            return filename.substring(prefix.length + 1);
        }
        return filename;
    }

    function inferCompendiumPrefixFromPath(path) {
        const normalized = normalizeWorkspacePath(path || '');
        const parts = normalized.split('/');
        if (parts.length < 2 || parts[0] !== 'Compendium') {
            return null;
        }
        const bucket = parts[1];
        switch (bucket) {
            case 'Characters': return 'CHAR';
            case 'Locations': return 'LOC';
            case 'Lore': return 'CONCEPT';
            case 'Factions': return 'FACTION';
            case 'Technology': return 'TECH';
            case 'Culture': return 'CULTURE';
            case 'Events': return 'EVENT';
            case 'Themes': return 'THEME';
            case 'Glossary': return 'GLOSSARY';
            default: return 'MISC';
        }
    }

    function inferStoryPrefixFromPath(path, filename) {
        const normalized = normalizeWorkspacePath(path || '');
        if (normalized.startsWith('Story/Scenes/')) {
            return 'SCN';
        }
        if (normalized.startsWith('Story/') && String(filename || '').toUpperCase().startsWith('SCN-')) {
            return 'SCN';
        }
        return null;
    }

    function getExplorerDisplayName(node) {
        if (!node || node.type !== 'file') {
            return node && node.name ? node.name : '';
        }
        const path = normalizeWorkspacePath(node.path || '');
        const filename = node.name || '';
        const prefix = inferCompendiumPrefixFromPath(path) || inferStoryPrefixFromPath(path, filename);
        return stripPrefixForDisplay(filename, prefix);
    }

    function getVisibleFilePaths() {
        if (!elements.fileTree) return [];
        return Array.from(elements.fileTree.querySelectorAll('.tree-item.tree-file:not(.tree-item-virtual)'))
            .map(item => normalizeTreePath(item.dataset.path || ''))
            .filter(Boolean);
    }

    function applyTreeSelection() {
        if (!elements.fileTree) return;
        const items = elements.fileTree.querySelectorAll('.tree-item.tree-file');
        items.forEach(item => {
            const path = normalizeTreePath(item.dataset.path || '');
            const selected = treeSelection.has(path);
            item.classList.toggle('selected-multi', selected);
        });
    }

    function clearTreeSelection() {
        if (!treeSelection.size) return;
        treeSelection.clear();
        lastTreeSelectionPath = null;
        applyTreeSelection();
    }

    function setTreeSelection(paths = []) {
        treeSelection.clear();
        const normalized = paths
            .map(path => normalizeTreePath(path))
            .filter(Boolean);
        normalized.forEach(path => treeSelection.add(path));
        lastTreeSelectionPath = normalized.length ? normalized[normalized.length - 1] : null;
        applyTreeSelection();
    }

    function getTreeSelection() {
        if (!isPrepMultiSelectEnabled()) return [];
        return Array.from(treeSelection);
    }

    function handleTreeSelectionClick(event, node) {
        if (!isPrepMultiSelectEnabled()) return false;
        if (!node || node.type !== 'file' || node.virtual) return false;

        const path = normalizeTreePath(node.path);
        if (!path) return false;

        const isToggle = event.ctrlKey || event.metaKey;
        const isRange = event.shiftKey;

        if (!isToggle && !isRange) {
            if (!treeSelection.has(path) || treeSelection.size > 1) {
                treeSelection.clear();
                treeSelection.add(path);
                lastTreeSelectionPath = path;
                applyTreeSelection();
            }
            return false;
        }

        event.preventDefault();

        if (isRange) {
            const visible = getVisibleFilePaths();
            const anchor = lastTreeSelectionPath || path;
            const startIndex = visible.indexOf(anchor);
            const endIndex = visible.indexOf(path);
            if (startIndex !== -1 && endIndex !== -1) {
                const [start, end] = startIndex < endIndex ? [startIndex, endIndex] : [endIndex, startIndex];
                treeSelection.clear();
                for (let i = start; i <= end; i += 1) {
                    treeSelection.add(visible[i]);
                }
            } else {
                treeSelection.clear();
                treeSelection.add(path);
            }
            lastTreeSelectionPath = path;
            applyTreeSelection();
            return true;
        }

        if (isToggle) {
            if (treeSelection.has(path)) {
                treeSelection.delete(path);
            } else {
                treeSelection.add(path);
            }
            lastTreeSelectionPath = path;
            applyTreeSelection();
            return true;
        }

        return false;
    }

    function getEditorStateKey() {
        const root = state.workspace && state.workspace.root ? String(state.workspace.root) : '';
        const name = state.workspace && state.workspace.name ? String(state.workspace.name) : '';
        if (root && name) {
            return `${editorStateStorageKey}:${root}:${name}`;
        }
        if (root) {
            return `${editorStateStorageKey}:${root}`;
        }
        if (name) {
            return `${editorStateStorageKey}:${name}`;
        }
        return editorStateStorageKey;
    }

    function loadEditorState() {
        const key = getEditorStateKey();
        const raw = localStorage.getItem(key);
        if (!raw) {
            return null;
        }
        try {
            const parsed = JSON.parse(raw);
            if (!parsed || typeof parsed !== 'object') {
                return null;
            }
            return parsed;
        } catch (err) {
            return null;
        }
    }

    function persistEditorState() {
        if (isRestoringEditorState) {
            return;
        }
        const openTabs = [];
        for (const [, tabData] of state.openTabs) {
            if (tabData && tabData.path) {
                openTabs.push(tabData.path);
            }
        }
        const payload = {
            version: 1,
            openTabs,
            activePath: state.activeFile || '',
            expandedFolders: Array.from(expandedFolders)
        };
        try {
            localStorage.setItem(getEditorStateKey(), JSON.stringify(payload));
        } catch (err) {
            // ignore storage failures (quota or privacy mode)
        }
    }

    function normalizeExplorerScope(scope) {
        switch (String(scope || '').toLowerCase()) {
            case 'story':
                return 'story';
            case 'compendium':
                return 'compendium';
            default:
                return 'all';
        }
    }

    function getStoredExplorerScope() {
        try {
            const raw = localStorage.getItem(explorerScopeStorageKey);
            return normalizeExplorerScope(raw);
        } catch (err) {
            return 'all';
        }
    }

    function getExplorerScope() {
        return explorerScope;
    }

    function isScopeAvailable(tree, scope) {
        if (!tree || scope === 'all') {
            return true;
        }
        const targetName = scope === 'story' ? 'Story' : 'Compendium';
        return Array.isArray(tree.children) && tree.children.some(child =>
            child && String(child.name || '').toLowerCase() === targetName.toLowerCase()
        );
    }

    function getScopedTree(tree, scope) {
        if (!tree || scope === 'all' || !Array.isArray(tree.children)) {
            return tree;
        }
        const targetName = scope === 'story' ? 'Story' : 'Compendium';
        const match = tree.children.find(child =>
            child && String(child.name || '').toLowerCase() === targetName.toLowerCase()
        );
        return match || tree;
    }

    function updateSearchPlaceholder() {
        if (!elements.searchInput) {
            return;
        }
        if (explorerScope === 'story') {
            elements.searchInput.placeholder = 'Search in Story...';
        } else if (explorerScope === 'compendium') {
            elements.searchInput.placeholder = 'Search in Compendium...';
        } else {
            elements.searchInput.placeholder = 'Search in Project...';
        }
    }

    function updateExplorerScopeUI() {
        const explorerVisible = elements.explorerPanel
            ? !elements.explorerPanel.classList.contains('is-hidden')
            : false;
        if (elements.fileTreeTitle) {
            if (explorerScope === 'story') {
                elements.fileTreeTitle.textContent = 'Story';
            } else if (explorerScope === 'compendium') {
                elements.fileTreeTitle.textContent = 'Compendium';
            } else {
                elements.fileTreeTitle.textContent = 'Files';
            }
        }
        if (elements.btnExplorerStory) {
            elements.btnExplorerStory.classList.toggle('is-active', explorerVisible && explorerScope === 'story');
        }
        if (elements.btnExplorerCompendium) {
            elements.btnExplorerCompendium.classList.toggle('is-active', explorerVisible && explorerScope === 'compendium');
        }
        updateSearchPlaceholder();
    }

    function renderExplorerTree() {
        if (!fullFileTree) {
            return;
        }
        const scopedTree = getScopedTree(fullFileTree, explorerScope);
        state.fileTree = scopedTree;
        renderFileTree(scopedTree);
        if (!isPrepMultiSelectEnabled()) {
            clearTreeSelection();
        } else {
            applyTreeSelection();
        }
    }

    function canRenamePath(path, nodeType) {
        const normalized = normalizeWorkspacePath(path);
        const isFolder = nodeType === 'folder';
        if (isFolder) {
            if (normalized.startsWith('Compendium/')) {
                return false;
            }
            if (state.workspace && state.workspace.prepStage && state.workspace.prepStage !== 'none') {
                return false;
            }
        }
        return true;
    }

    function setExplorerScope(scope, options = {}) {
        let normalized = normalizeExplorerScope(scope);
        const { persist = true, refresh = true } = options;
        if (fullFileTree && !isScopeAvailable(fullFileTree, normalized)) {
            normalized = 'all';
        }
        explorerScope = normalized;
        if (persist) {
            try {
                localStorage.setItem(explorerScopeStorageKey, explorerScope);
            } catch (err) {
                // ignore storage failures
            }
        }
        updateExplorerScopeUI();
        if (refresh) {
            renderExplorerTree();
        }
    }

    function applyExpandedStateToTree() {
        if (!elements.fileTree) return;
        const items = elements.fileTree.querySelectorAll('.tree-item.tree-folder');
        items.forEach(item => {
            const folderPath = normalizeWorkspacePath(item.dataset.path || '');
            if (!folderPath) return;
            const children = item.nextElementSibling;
            if (!children || !children.classList.contains('tree-children')) return;
            if (expandedFolders.has(folderPath)) {
                children.classList.add('expanded');
                const icon = item.querySelector('.tree-icon');
                if (icon) icon.textContent = '📂';
            } else {
                children.classList.remove('expanded');
                const icon = item.querySelector('.tree-icon');
                if (icon) icon.textContent = '📁';
            }
        });
    }

    async function restoreEditorState() {
        if (editorStateRestored) {
            return;
        }
        editorStateRestored = true;
        if (state.workspace && state.workspace.prepStage === 'none') {
            return;
        }
        const saved = loadEditorState();
        if (!saved) {
            return;
        }

        if (Array.isArray(saved.expandedFolders)) {
            expandedFolders = new Set(
                saved.expandedFolders
                    .map(path => normalizeWorkspacePath(path))
                    .filter(Boolean)
            );
            hasSavedExplorerState = expandedFolders.size > 0;
            applyExpandedStateToTree();
        }

        if (Array.isArray(saved.openTabs) && saved.openTabs.length > 0) {
            isRestoringEditorState = true;
            for (const path of saved.openTabs) {
                if (path) {
                    await openFileInNewTab(path);
                }
            }
            if (saved.activePath) {
                const activePath = normalizeWorkspacePath(saved.activePath);
                const tabId = findTabIdByPath(activePath);
                if (tabId) {
                    setActiveTab(tabId);
                }
            }
            isRestoringEditorState = false;
        }
    }

    function log(message, level) {
        if (window.log) {
            window.log(message, level);
        }
    }

    // Generate unique tab ID
    function generateTabId() {
        return `tab-${++state.tabCounter}`;
    }
    
    // Normalize a Project path to canonical form:
    // - Forward slashes only
    // - No leading slash
    // - No duplicate slashes
    // Count how many tabs reference a given path
    function countTabsForPath(path) {
        path = normalizeWorkspacePath(path);
        let count = 0;
        for (const [, tabData] of state.openTabs) {
            if (tabData.path === path) count++;
        }
        return count;
    }
    
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
    
            // Track changes for dirty state (updates shared file state)
            state.editor.onDidChangeModelContent(() => {
                if (state.activeFile) {
                    const file = state.openFiles.get(state.activeFile);
                    if (file) {
                        file.content = state.editor.getValue();
                        updateDirtyStateForPath(state.activeFile);
                    }
                }
            });
    
            log('Monaco editor initialized', 'success');
        });
    }
    

    // Scene Segments API (uses window.api from api.js)
    async function apiGetSegments(path, forceRefresh = false) {
        path = normalizeWorkspacePath(path);
    
        // Return cached if available and not forcing refresh
        if (!forceRefresh && state.segments.has(path)) {
            return state.segments.get(path);
        }
    
        try {
            const segments = await api(`/api/segments?path=${encodeURIComponent(path)}`);
            state.segments.set(path, segments);
            log(`Loaded ${segments.length} segment(s) for: ${path}`, 'info');
            return segments;
        } catch (err) {
            log(`Failed to load segments for ${path}: ${err.message}`, 'error');
            throw err;
        }
    }
    
    // File Tree
    async function loadFileTree() {
        try {
            const tree = await api('/api/tree');
            fullFileTree = tree;
            const storedScope = getStoredExplorerScope();
            const desiredScope = isScopeAvailable(tree, storedScope)
                ? storedScope
                : (isScopeAvailable(tree, 'story') ? 'story' : 'all');
            setExplorerScope(desiredScope, { persist: true, refresh: false });
            renderExplorerTree();
            if (window.versioning && window.versioning.applyUnpublishedIndicators) {
                window.versioning.applyUnpublishedIndicators();
            }
            log('File tree loaded', 'info');
        } catch (err) {
            log(`Failed to load file tree: ${err.message}`, 'error');
        }
    }
    
    function renderFileTree(node, container = elements.fileTree, depth = 0) {
        if (depth === 0) {
            container.innerHTML = '';
        }

        if (shouldHideFromExplorer(node)) {
            return;
        }
    
        if (node.type === 'folder' && node.children) {
            if (depth > 0) {
                const folderItem = createTreeItem(node, depth);
                container.appendChild(folderItem.element);
                container = folderItem.childContainer;
            }

            if (isStoryRoot(node)) {
                const outlineNode = {
                    name: 'SCN-outline.md',
                    path: OUTLINE_VIRTUAL_PATH,
                    type: 'file',
                    virtual: true
                };
                const outlineItem = createTreeItem(outlineNode, depth + 1);
                container.appendChild(outlineItem.element);
            }

            node.children.forEach(child => {
                renderFileTree(child, container, depth + 1);
            });
        } else if (node.type === 'file') {
            const fileItem = createTreeItem(node, depth);
            container.appendChild(fileItem.element);
        }
    }

    function shouldHideFromExplorer(node) {
        if (!node || node.type !== 'folder') {
            return false;
        }
        const name = String(node.name || '');
        return name.startsWith('.') && name !== '.' && name !== '..';
    }

    function isStoryRoot(node) {
        if (!node || node.type !== 'folder') {
            return false;
        }
        const path = normalizeWorkspacePath(node.path || '');
        return path === 'Story';
    }
    
    function createTreeItem(node, depth) {
        const item = document.createElement('div');
        item.className = `tree-item ${node.type === 'folder' ? 'tree-folder' : 'tree-file'}`;
        if (node.virtual) {
            item.classList.add('tree-item-virtual');
        }
        item.style.setProperty('--indent', `${depth * 16}px`);
    
        const icon = document.createElement('span');
        icon.className = 'tree-icon';
        icon.textContent = node.type === 'folder' ? '📁' : getFileIcon(node.name);
    
        const name = document.createElement('span');
        name.className = 'tree-name';
        name.textContent = getExplorerDisplayName(node);
    
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
                const folderPath = normalizeWorkspacePath(node.path || '');
                if (folderPath) {
                    if (isExpanded) {
                        expandedFolders.add(folderPath);
                    } else {
                        expandedFolders.delete(folderPath);
                    }
                    persistEditorState();
                }
            });
    
            // Context menu for folders (rename/delete)
            item.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                if (window.showContextMenu) {
                    window.showContextMenu(e, node);
                }
            });
    
            const folderPath = normalizeWorkspacePath(node.path || '');
            const shouldExpand = folderPath && expandedFolders.has(folderPath);
            if (shouldExpand) {
                childContainer.classList.add('expanded');
                icon.textContent = '📂';
            } else if (!hasSavedExplorerState && depth === 1) {
                childContainer.classList.add('expanded');
                icon.textContent = '📂';
                if (folderPath) {
                    expandedFolders.add(folderPath);
                }
            }
        } else {
            item.addEventListener('click', (e) => {
                e.stopPropagation();
                if (handleTreeSelectionClick(e, node)) {
                    return;
                }
                openFile(node.path);
            });
    
            if (!node.virtual) {
                item.addEventListener('contextmenu', (e) => {
                    e.preventDefault();
                    if (window.showContextMenu) {
                        window.showContextMenu(e, node);
                    }
                });
            }
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
    // Opens file - reuses existing tab if one exists for this path
    async function openFile(path) {
        path = normalizeWorkspacePath(path);
        try {
            if (isOutlinePath(path)) {
                const existingTabId = findTabIdByPath(path);
                if (existingTabId) {
                    closeTab(existingTabId, true);
                }
                showOutlineEditorModal();
                return;
            }
            // Check if there's already a tab for this path
            const existingTabId = findTabIdByPath(path);
            if (existingTabId) {
                // Reuse existing tab
                setActiveTab(existingTabId);
                return;
            }
    
            // No existing tab - need to open the file
            await ensureFileLoaded(path);
    
            // Create a new tab for this file
            const tabId = generateTabId();
            state.openTabs.set(tabId, { path });
    
            createTab(tabId, path);
            setActiveTab(tabId);
        } catch (err) {
            log(`Failed to open file: ${err.message}`, 'error');
        }
    }
    
    // Open file in a NEW tab, even if already open elsewhere
    // Multiple tabs share the same underlying file state
    async function openFileInNewTab(path) {
        path = normalizeWorkspacePath(path);
        try {
            if (isOutlinePath(path)) {
                const existingTabId = findTabIdByPath(path);
                if (existingTabId) {
                    closeTab(existingTabId, true);
                }
                showOutlineEditorModal();
                return;
            }
            log(`Opening file in new tab: ${path}`, 'info');
    
            // Ensure file is loaded (reuses existing if already loaded)
            await ensureFileLoaded(path);
    
            // Always create a new tab
            const tabId = generateTabId();
            state.openTabs.set(tabId, { path });
    
            createTab(tabId, path);
            setActiveTab(tabId);
        } catch (err) {
            log(`Failed to open file: ${err.message}`, 'error');
        }
    }

    function showOutlineEditorModal() {
        const createModalShell = window.modals ? window.modals.createModalShell : null;
        const outlineApi = window.outlineApi;
        const issueApi = window.issueApi;
        const store = getNotificationStore();
        if (!createModalShell || !outlineApi || !issueApi) {
            if (store) {
                store.error('Outline editor is unavailable.', 'editor');
            }
            return;
        }

        const { modal, body, confirmBtn, cancelBtn, close } = createModalShell(
            'Story Outline',
            'Accept',
            'Cancel',
            { closeOnCancel: false, closeOnConfirm: false, closeOnOverlay: false, closeOnEscape: false }
        );
        modal.classList.add('outline-modal');
        confirmBtn.disabled = true;

        // Header with intro text and scene count
        const header = document.createElement('div');
        header.className = 'outline-header';

        const intro = document.createElement('div');
        intro.className = 'outline-intro';
        intro.textContent = 'Reorder scenes and edit scene briefs. Changes apply only when accepted.';

        const sceneCount = document.createElement('div');
        sceneCount.className = 'outline-scene-count';
        sceneCount.textContent = 'Loading...';

        header.appendChild(intro);
        header.appendChild(sceneCount);
        body.appendChild(header);

        const list = document.createElement('div');
        list.className = 'outline-list';
        body.appendChild(list);

        const status = document.createElement('div');
        status.className = 'outline-status';
        status.textContent = 'Loading outline...';
        body.appendChild(status);

        let original = null;
        let draft = null;

        const cloneOutline = (outline) => JSON.parse(JSON.stringify(outline || {}));

        const setDirty = (dirty) => {
            confirmBtn.disabled = false;
            confirmBtn.textContent = dirty ? 'Accept' : 'Close';
        };

        const stripMarkdown = (value) => {
            if (!value) return '';
            let text = String(value);
            text = text.replace(/```[\s\S]*?```/g, '');
            text = text.replace(/`([^`]+)`/g, '$1');
            text = text.replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1');
            text = text.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');
            text = text.replace(/^\s{0,3}#{1,6}\s+/gm, '');
            text = text.replace(/^\s{0,3}>\s?/gm, '');
            text = text.replace(/^\s*[-*+]\s+/gm, '');
            text = text.replace(/^\s*\d+\.\s+/gm, '');
            text = text.replace(/\*\*([^*]+)\*\*/g, '$1');
            text = text.replace(/\*([^*]+)\*/g, '$1');
            text = text.replace(/__([^_]+)__/g, '$1');
            text = text.replace(/_([^_]+)_/g, '$1');
            text = text.replace(/~~([^~]+)~~/g, '$1');
            text = text.replace(/[ \t]+\n/g, '\n');
            text = text.replace(/\n{3,}/g, '\n\n');
            return text.trim();
        };

        const clampText = (value, max) => {
            if (!value) return '';
            if (value.length <= max) return value;
            const clipped = value.slice(0, Math.max(0, max - 3)).trimEnd();
            return clipped + '...';
        };

        const firstSentence = (value) => {
            const text = stripMarkdown(value);
            if (!text) return '';
            const match = text.match(/(.+?[.!?])(\s|$)/);
            if (match) return match[1].trim();
            const newline = text.indexOf('\n');
            if (newline > 0) return text.slice(0, newline).trim();
            return text;
        };

        // Extract POV from title (e.g., "Scene Title — Seryn POV" -> { title: "Scene Title", pov: "Seryn" })
        const extractPov = (title) => {
            if (!title) return { title: '', pov: null };
            // Match patterns like "— Name POV", "- Name POV", "– Name POV" (supports Unicode names)
            const povMatch = title.match(/\s*[—–-]\s*(.+?)\s+POV\s*$/i);
            if (povMatch) {
                return {
                    title: title.slice(0, povMatch.index).trim(),
                    pov: povMatch[1].trim()
                };
            }
            return { title: title.trim(), pov: null };
        };

        const getDisplayTitle = (entry) => {
            const rawTitle = entry && entry.title ? String(entry.title) : '';
            const baseTitle = rawTitle.trim() ? stripMarkdown(rawTitle) : firstSentence(entry?.summary || '');
            const fullTitle = baseTitle || 'Untitled Scene';
            const { title: cleanTitle, pov } = extractPov(fullTitle);
            return {
                fullTitle,
                displayTitle: clampText(cleanTitle || 'Untitled Scene', 100),
                pov
            };
        };

        const getDisplaySummary = (entry) => {
            const summary = stripMarkdown(entry?.summary || '');
            return summary || '';
        };

        const summarizeForIssue = (value) => {
            const summary = stripMarkdown(value || '');
            if (!summary) return '';
            return clampText(summary, 160);
        };

        const updateSceneCount = () => {
            const count = draft?.scenes?.length || 0;
            sceneCount.textContent = count === 1 ? '1 scene' : `${count} scenes`;
        };

        // Change detection helpers
        const getOriginalIndex = (sceneId) => {
            if (!original?.scenes) return -1;
            return original.scenes.findIndex(s => s.sceneId === sceneId);
        };

        const isSceneMoved = (sceneId, currentIndex) => {
            const origIndex = getOriginalIndex(sceneId);
            return origIndex !== -1 && origIndex !== currentIndex;
        };

        const isSceneEdited = (entry) => {
            if (!original?.scenes) return false;
            const origScene = original.scenes.find(s => s.sceneId === entry.sceneId);
            if (!origScene) return false;
            const origSummary = stripMarkdown(origScene.summary || '');
            const currSummary = stripMarkdown(entry.summary || '');
            return origSummary !== currSummary;
        };

        const renderList = () => {
            list.innerHTML = '';
            updateSceneCount();

            if (!draft || !Array.isArray(draft.scenes) || draft.scenes.length === 0) {
                const empty = document.createElement('div');
                empty.className = 'outline-empty';
                empty.innerHTML = `
                    <div class="outline-empty-icon">📖</div>
                    <div class="outline-empty-text">No scenes in outline yet.</div>
                `;
                list.appendChild(empty);
                return;
            }

            draft.scenes.forEach((entry, index) => {
                const { displayTitle, fullTitle, pov } = getDisplayTitle(entry);
                const summaryText = getDisplaySummary(entry);
                const hasSummary = summaryText.length > 0;
                const isLongSummary = summaryText.length > 200;

                // Card container
                const card = document.createElement('div');
                card.className = 'outline-card';

                // Scene number badge
                const numberBadge = document.createElement('div');
                numberBadge.className = 'outline-scene-number';

                const numberText = document.createElement('span');
                numberText.className = 'outline-number-text';
                numberText.textContent = String(index + 1);
                numberBadge.appendChild(numberText);

                // Change indicators
                const moved = isSceneMoved(entry.sceneId, index);
                const edited = isSceneEdited(entry);

                if (moved || edited) {
                    const indicators = document.createElement('div');
                    indicators.className = 'outline-change-indicators';

                    if (moved) {
                        const movedIcon = document.createElement('span');
                        movedIcon.className = 'outline-indicator moved';
                        movedIcon.innerHTML = '↕';
                        movedIcon.title = 'Reordered';
                        indicators.appendChild(movedIcon);
                    }

                    if (edited) {
                        const editedIcon = document.createElement('span');
                        editedIcon.className = 'outline-indicator edited';
                        editedIcon.innerHTML = '✎';
                        editedIcon.title = 'Edited';
                        indicators.appendChild(editedIcon);
                    }

                    numberBadge.appendChild(indicators);
                    card.classList.add('has-changes');
                }

                card.appendChild(numberBadge);

                // Card content area
                const content = document.createElement('div');
                content.className = 'outline-card-content';

                // Header row
                const headerRow = document.createElement('div');
                headerRow.className = 'outline-card-header';

                // Title area (title + POV badge)
                const titleArea = document.createElement('div');
                titleArea.className = 'outline-card-title-area';

                const titleEl = document.createElement('div');
                titleEl.className = 'outline-card-title';
                titleEl.textContent = displayTitle;
                titleEl.title = fullTitle;
                titleArea.appendChild(titleEl);

                if (pov) {
                    const povBadge = document.createElement('div');
                    povBadge.className = 'outline-pov-badge';
                    povBadge.textContent = `${pov} POV`;
                    titleArea.appendChild(povBadge);
                }

                headerRow.appendChild(titleArea);

                // Move controls
                const controls = document.createElement('div');
                controls.className = 'outline-card-controls';

                const upBtn = document.createElement('button');
                upBtn.type = 'button';
                upBtn.className = 'outline-move-btn';
                upBtn.innerHTML = '&#9650;'; // ▲
                upBtn.title = 'Move up';
                upBtn.disabled = index === 0;
                upBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (index === 0) return;
                    const moved = draft.scenes.splice(index, 1)[0];
                    draft.scenes.splice(index - 1, 0, moved);
                    setDirty(true);
                    renderList();
                });

                const downBtn = document.createElement('button');
                downBtn.type = 'button';
                downBtn.className = 'outline-move-btn';
                downBtn.innerHTML = '&#9660;'; // ▼
                downBtn.title = 'Move down';
                downBtn.disabled = index === draft.scenes.length - 1;
                downBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (index >= draft.scenes.length - 1) return;
                    const moved = draft.scenes.splice(index, 1)[0];
                    draft.scenes.splice(index + 1, 0, moved);
                    setDirty(true);
                    renderList();
                });

                controls.appendChild(upBtn);
                controls.appendChild(downBtn);
                headerRow.appendChild(controls);
                content.appendChild(headerRow);

                // Summary area
                const summary = document.createElement('div');
                summary.className = 'outline-summary';

                const renderSummaryView = () => {
                    summary.innerHTML = '';
                    summary.classList.remove('editing', 'expanded');

                    if (!hasSummary) {
                        const placeholder = document.createElement('span');
                        placeholder.className = 'outline-summary-placeholder';
                        placeholder.textContent = 'Click to add scene brief...';
                        summary.appendChild(placeholder);
                    } else {
                        const textSpan = document.createElement('span');
                        textSpan.className = 'outline-summary-text';
                        textSpan.textContent = summaryText;
                        summary.appendChild(textSpan);

                        if (isLongSummary) {
                            const toggle = document.createElement('span');
                            toggle.className = 'outline-expand-toggle';
                            toggle.textContent = 'show more';
                            toggle.addEventListener('click', (e) => {
                                e.stopPropagation();
                                if (summary.classList.contains('expanded')) {
                                    summary.classList.remove('expanded');
                                    toggle.textContent = 'show more';
                                } else {
                                    summary.classList.add('expanded');
                                    toggle.textContent = 'show less';
                                }
                            });
                            summary.appendChild(toggle);
                        }
                    }
                };

                const enterEditMode = () => {
                    summary.innerHTML = '';
                    summary.classList.add('editing');
                    summary.classList.remove('expanded');

                    const textarea = document.createElement('textarea');
                    textarea.className = 'outline-summary-input';
                    textarea.value = stripMarkdown(entry.summary || '');
                    textarea.placeholder = 'Write a brief description of this scene...';

                    const actions = document.createElement('div');
                    actions.className = 'outline-edit-actions';

                    const hint = document.createElement('div');
                    hint.className = 'outline-keyboard-hint';
                    hint.innerHTML = '<kbd>Ctrl</kbd>+<kbd>Enter</kbd> to save';

                    const cancelBtn = document.createElement('button');
                    cancelBtn.type = 'button';
                    cancelBtn.className = 'outline-edit-btn';
                    cancelBtn.textContent = 'Cancel';

                    const saveBtn = document.createElement('button');
                    saveBtn.type = 'button';
                    saveBtn.className = 'outline-edit-btn primary';
                    saveBtn.textContent = 'Save';

                    const finishEdit = (save) => {
                        if (save) {
                            entry.summary = textarea.value.trim();
                            setDirty(true);
                        }
                        renderList();
                    };

                    cancelBtn.addEventListener('click', (e) => {
                        e.stopPropagation();
                        finishEdit(false);
                    });

                    saveBtn.addEventListener('click', (e) => {
                        e.stopPropagation();
                        finishEdit(true);
                    });

                    textarea.addEventListener('keydown', (e) => {
                        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                            e.preventDefault();
                            finishEdit(true);
                        }
                        if (e.key === 'Escape') {
                            e.preventDefault();
                            finishEdit(false);
                        }
                    });

                    actions.appendChild(hint);
                    actions.appendChild(cancelBtn);
                    actions.appendChild(saveBtn);

                    summary.appendChild(textarea);
                    summary.appendChild(actions);
                    setTimeout(() => textarea.focus(), 0);
                };

                summary.addEventListener('click', (e) => {
                    if (summary.classList.contains('editing')) return;
                    if (e.target.classList.contains('outline-expand-toggle')) return;
                    enterEditMode();
                });

                renderSummaryView();
                content.appendChild(summary);
                card.appendChild(content);
                list.appendChild(card);
            });
        };

        const buildDiffPayload = () => {
            const before = (original?.scenes || []).map(scene => scene.sceneId).filter(Boolean);
            const after = (draft?.scenes || []).map(scene => scene.sceneId).filter(Boolean);
            const summaryChanges = [];
            const beforeMap = new Map();
            (original?.scenes || []).forEach(scene => {
                if (scene && scene.sceneId) {
                    beforeMap.set(scene.sceneId, scene.summary || '');
                }
            });
            (draft?.scenes || []).forEach(scene => {
                if (!scene || !scene.sceneId) return;
                const prev = beforeMap.get(scene.sceneId) || '';
                const next = scene.summary || '';
                if (prev !== next) {
                    summaryChanges.push({
                        sceneId: scene.sceneId,
                        before: summarizeForIssue(prev),
                        after: summarizeForIssue(next),
                        beforeLength: prev.length,
                        afterLength: next.length
                    });
                }
            });
            return { before, after, summaryChanges };
        };

        const buildIssueBody = (diff) => {
            const lines = [];
            lines.push('Target: outline');
            lines.push('Action: outline.update');
            lines.push('');
            lines.push(`Order before: ${diff.before.join(', ') || '(none)'}`);
            lines.push(`Order after: ${diff.after.join(', ') || '(none)'}`);
            if (diff.summaryChanges.length) {
                lines.push('');
                lines.push('Summary changes:');
                diff.summaryChanges.forEach(change => {
                    const entry = (draft?.scenes || []).find(scene => scene.sceneId === change.sceneId);
                    const sceneTitle = entry && entry.title ? stripMarkdown(entry.title) : '';
                    const title = sceneTitle ? ` (${sceneTitle})` : '';
                    lines.push(`- ${change.sceneId}${title}: "${change.before}" -> "${change.after}"`);
                });
            }
            lines.push('');
            lines.push('Payload:');
            lines.push(JSON.stringify({
                target: 'outline',
                action: 'outline.update',
                payload: diff
            }));
            return lines.join('\n');
        };

        const loadOutline = async () => {
            try {
                const response = await outlineApi.get();
                original = response.outline || {};
                draft = cloneOutline(original);
                const scenes = Array.isArray(original.scenes) ? original.scenes : [];
                status.textContent = scenes.length ? '' : 'No scenes found for outline.';
                setDirty(false);
                renderList();
            } catch (err) {
                status.textContent = `Failed to load outline: ${err.message}`;
            }
        };

        confirmBtn.addEventListener('click', async () => {
            if (!draft) {
                return;
            }
            const diff = buildDiffPayload();
            if (!diff.summaryChanges.length && diff.before.join() === diff.after.join()) {
                status.textContent = 'No changes to save.';
                close();
                return;
            }
            confirmBtn.disabled = true;
            try {
                status.textContent = 'Saving outline...';
                log(`Outline save started (scenes=${draft?.scenes?.length || 0})`, 'info');
                await outlineApi.save(draft);
                const canCreateIssues = state && state.workspace && state.workspace.agentsUnlocked;
                if (canCreateIssues) {
                    await issueApi.create({
                        title: 'Outline updated',
                        body: buildIssueBody(diff),
                        openedBy: 'user',
                        tags: ['outline', 'outline-update']
                    });
                    if (store) {
                        store.success('Outline saved and issue created.', 'editor');
                    }
                } else if (store) {
                    store.success('Outline saved.', 'editor');
                }
                status.textContent = 'Outline saved.';
                log('Outline save completed', 'success');
                close();
            } catch (err) {
                log(`Outline save failed: ${err.message}`, 'error');
                if (store) {
                    store.error(`Failed to save outline: ${err.message}`, 'editor');
                }
                status.textContent = `Save failed: ${err.message}`;
                confirmBtn.disabled = false;
            }
        });

        cancelBtn.addEventListener('click', () => {
            close();
        });

        loadOutline();
    }

    
    // Ensure a file is loaded into openFiles (loads from backend if needed)
    async function ensureFileLoaded(path) {
        path = normalizeWorkspacePath(path);
        if (state.openFiles.has(path)) {
            // Already loaded - nothing to do
            return;
        }

        log(`Loading file: ${path}`, 'info');
        const content = await api(`/api/file?path=${encodeURIComponent(path)}`);
        const model = monaco.editor.createModel(content, getLanguageForFile(path));

        state.openFiles.set(path, {
            model,
            content,
            originalContent: content
        });
    }

    // Force reload a file from disk (used after discarding changes)
    async function reloadOpenFile(path) {
        path = normalizeWorkspacePath(path);
        const fileData = state.openFiles.get(path);
        if (!fileData) return; // Not open, nothing to reload

        try {
            log(`Reloading file: ${path}`, 'info');
            const content = await api(`/api/file?path=${encodeURIComponent(path)}`);

            // Update model content
            fileData.model.setValue(content);
            fileData.content = content;
            fileData.originalContent = content;

            // Update dirty state
            updateTabDirtyState(path);
        } catch (err) {
            log(`Failed to reload file ${path}: ${err.message}`, 'error');
        }
    }

    // Reload all open files from disk
    async function reloadAllOpenFiles() {
        const paths = Array.from(state.openFiles.keys());
        for (const path of paths) {
            await reloadOpenFile(path);
        }
    }
    
    // Find first tab ID for a given path
    function findTabIdByPath(path) {
        path = normalizeWorkspacePath(path);
        for (const [tabId, tabData] of state.openTabs) {
            if (tabData.path === path) {
                return tabId;
            }
        }
        return null;
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
    
    function setActiveTab(tabId) {
        const tabData = state.openTabs.get(tabId);
        if (!tabData) return;
    
        const file = state.openFiles.get(tabData.path);
        if (!file) return;
    
        state.activeTabId = tabId;
        state.activeFile = tabData.path;
    
        if (state.editor) {
            state.editor.setModel(file.model);
            elements.editorPlaceholder.classList.add('hidden');
            elements.monacoEditor.classList.add('active');
        }
    
        // Update tab active state
        document.querySelectorAll('.tab').forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tabId === tabId);
        });
    
        // Update tree selection
        document.querySelectorAll('.tree-item').forEach(item => {
            item.classList.toggle('selected', item.dataset.path === tabData.path);
        });
    
        persistEditorState();
    }
    
    function createTab(tabId, path) {
        const tab = document.createElement('div');
        tab.className = 'tab';
        tab.dataset.tabId = tabId;
        tab.dataset.path = path;
    
        const name = document.createElement('span');
        name.className = 'tab-name';
        name.textContent = path.split('/').pop();
    
        const close = document.createElement('button');
        close.className = 'tab-close';
        close.textContent = '×';
        close.addEventListener('click', (e) => {
            e.stopPropagation();
            closeTab(tabId);
        });
    
        tab.appendChild(name);
        tab.appendChild(close);
    
        tab.addEventListener('click', () => setActiveTab(tabId));

        elements.tabsContainer.appendChild(tab);
        if (window.versioning && window.versioning.applyUnpublishedIndicators) {
            window.versioning.applyUnpublishedIndicators();
        }
    }
    
    // Update dirty state for ALL tabs showing a given path
    function updateDirtyStateForPath(path) {
        path = normalizeWorkspacePath(path);
        const file = state.openFiles.get(path);
        if (!file) return;
    
        const isDirty = file.content !== file.originalContent;
    
        // Update all tabs that reference this path
        for (const [tabId, tabData] of state.openTabs) {
            if (tabData.path === path) {
                const tabEl = document.querySelector(`.tab[data-tab-id="${tabId}"]`);
                if (tabEl) {
                    tabEl.classList.toggle('dirty', isDirty);
                }
            }
        }
    }
    
    // Close a specific tab by ID
    function closeTab(tabId, force = false) {
        const tabData = state.openTabs.get(tabId);
        if (!tabData) return;
    
        const path = tabData.path;
        const file = state.openFiles.get(path);
    
        // Check for unsaved changes (unless forced)
        if (!force && file && file.content !== file.originalContent) {
            const confirmed = confirm(`${path} has unsaved changes. Close anyway?`);
            if (!confirmed) return;
            const store = getNotificationStore();
            if (store) {
                store.editorDiscardWarning(path);
            }
        }
    
        // Remove tab element
        const tab = document.querySelector(`.tab[data-tab-id="${tabId}"]`);
        if (tab) tab.remove();
    
        // Remove tab from openTabs
        state.openTabs.delete(tabId);
    
        // If this was the last tab for this path, dispose the model and remove from openFiles
        if (countTabsForPath(path) === 0 && file) {
            if (file.model) {
                file.model.dispose();
            }
            state.openFiles.delete(path);
        }
    
        // Switch to another tab or show placeholder
        if (state.activeTabId === tabId) {
            const remaining = Array.from(state.openTabs.keys());
            if (remaining.length > 0) {
                setActiveTab(remaining[remaining.length - 1]);
            } else {
                state.activeFile = null;
                state.activeTabId = null;
                elements.editorPlaceholder.classList.remove('hidden');
                elements.monacoEditor.classList.remove('active');
            }
        }
    
        log(`Closed tab: ${path}`, 'info');
        persistEditorState();
    }
    
    // Close all tabs matching a path (for file deletion)
    // For folders, closes all tabs whose path starts with folderPath/
    // Also cleans up openFiles entries for deleted paths
    function closeTabsForPath(path, isFolder = false) {
        path = normalizeWorkspacePath(path);
        const tabsToClose = [];
        const filesToCleanup = [];
    
        // Find matching tabs
        for (const [tabId, tabData] of state.openTabs) {
            if (isFolder) {
                const folderPrefix = path + '/';
                if (tabData.path === path || tabData.path.startsWith(folderPrefix)) {
                    tabsToClose.push(tabId);
                }
            } else {
                if (tabData.path === path) {
                    tabsToClose.push(tabId);
                }
            }
        }
    
        // Find matching openFiles entries to clean up
        for (const [filePath] of state.openFiles) {
            if (isFolder) {
                const folderPrefix = path + '/';
                if (filePath === path || filePath.startsWith(folderPrefix)) {
                    filesToCleanup.push(filePath);
                }
            } else {
                if (filePath === path) {
                    filesToCleanup.push(filePath);
                }
            }
        }
    
        // Close all matching tabs (force close - no unsaved prompt for deleted files)
        tabsToClose.forEach(tabId => closeTab(tabId, true));
    
        // Clean up any remaining openFiles entries (in case tabs were already closed)
        filesToCleanup.forEach(filePath => {
            const file = state.openFiles.get(filePath);
            if (file) {
                if (file.model) {
                    file.model.dispose();
                }
                state.openFiles.delete(filePath);
            }
        });
    
        if (tabsToClose.length > 0) {
            log(`Closed ${tabsToClose.length} tab(s) for deleted path: ${path}`, 'info');
        }
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
            updateDirtyStateForPath(state.activeFile);
            log(`Saved: ${state.activeFile}`, 'success');
            const store = getNotificationStore();
            if (store) {
                store.editorSaveSuccess(state.activeFile);
            }
            if (window.versioning && window.versioning.refreshChanges) {
                window.versioning.refreshChanges();
            }
        } catch (err) {
            log(`Failed to save: ${err.message}`, 'error');
            const store = getNotificationStore();
            if (store) {
                store.editorSaveFailure(state.activeFile, err.message);
            }
        }
    }
    
    // Save all dirty files (iterates over openFiles, not tabs)
    async function saveAllFiles() {
        let savedCount = 0;
        let failedCount = 0;
        for (const [path, file] of state.openFiles) {
            if (file.content !== file.originalContent) {
                try {
                    await api(`/api/file?path=${encodeURIComponent(path)}`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'text/plain' },
                        body: file.content
                    });
                    file.originalContent = file.content;
                    updateDirtyStateForPath(path);
                    log(`Saved: ${path}`, 'success');
                    savedCount += 1;
                    const store = getNotificationStore();
                    if (store) {
                        store.editorSaveSuccess(path);
                    }
                } catch (err) {
                    log(`Failed to save ${path}: ${err.message}`, 'error');
                    failedCount += 1;
                    const store = getNotificationStore();
                    if (store) {
                        store.editorSaveFailure(path, err.message);
                    }
                }
            }
        }
        if (savedCount || failedCount) {
            if (failedCount) {
                log(`Save all completed: ${savedCount} saved, ${failedCount} failed`, 'warning');
            } else {
                log(`Save all completed: ${savedCount} saved`, 'success');
            }
        } else {
            log('Save all completed: no changes', 'info');
        }
        if (window.versioning && window.versioning.refreshChanges) {
            window.versioning.refreshChanges();
        }
    }
    
    async function explorePath(path, nodeType) {
        path = normalizeWorkspacePath(path);
        if (!path) return;
    
        const isFolder = nodeType === 'folder';
        const label = isFolder ? 'folder' : 'file';
        log(`Exploring ${label}: ${path}`, 'info');
    
        try {
            const endpoint = isFolder ? '/api/file/open-folder' : '/api/file/reveal';
            const result = await api(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ path })
            });
            if (result.ok) {
                if (result.fallback === 'open-folder') {
                    log(`Reveal failed; opened containing folder instead for ${label}`, 'warning');
                } else {
                    log(`Opened ${label} in explorer`, 'success');
                }
            } else {
                log(`Failed to explore ${label}: ${result.error}`, 'error');
            }
        } catch (err) {
            log(`Failed to explore ${label}: ${err.message}`, 'error');
        }
    }
    
    async function promptRename(oldPath, nodeType = 'file') {
        oldPath = normalizeWorkspacePath(oldPath);
        if (!canRenamePath(oldPath, nodeType)) {
            log('Rename is not allowed for this item.', 'warning');
            const store = getNotificationStore();
            if (store) {
                store.warning('Rename is not allowed for this item.', 'editor');
            }
            return;
        }
        const lastSlash = oldPath.lastIndexOf('/');
        const parentFolder = lastSlash !== -1 ? oldPath.substring(0, lastSlash) : '';
        const oldName = lastSlash !== -1 ? oldPath.substring(lastSlash + 1) : oldPath;
        const isFile = nodeType === 'file';

        const buildRenamedFile = (inputName) => {
            const raw = inputName.trim();
            if (!raw) return null;
            if (raw.includes('/') || raw.includes('\\')) return null;

            const compPrefix = inferCompendiumPrefixFromPath(parentFolder);
            const storyPrefix = inferStoryPrefixFromPath(parentFolder, oldName);
            const expectedPrefix = compPrefix || storyPrefix;
            const defaultExt = oldName.includes('.') ? oldName.substring(oldName.lastIndexOf('.')) : '';

            let name = raw;
            if (expectedPrefix) {
                name = stripPrefixForDisplay(name, expectedPrefix);
            }

            if (!name.includes('.') && defaultExt) {
                name = `${name}${defaultExt}`;
            }

            const ext = name.includes('.') ? name.substring(name.lastIndexOf('.')) : '';
            const base = ext ? name.substring(0, name.lastIndexOf('.')) : name;
            const normalizedExt = compPrefix || storyPrefix ? '.md' : ext || defaultExt || '';
            const finalBase = expectedPrefix ? `${expectedPrefix}-${base}` : base;
            return `${finalBase}${normalizedExt}`;
        };

        const displayName = isFile
            ? stripPrefixForDisplay(oldName, inferCompendiumPrefixFromPath(parentFolder) || inferStoryPrefixFromPath(parentFolder, oldName))
            : oldName;
        const title = nodeType === 'folder' ? 'Rename Folder' : 'Rename File';
        const placeholder = nodeType === 'folder' ? 'folder-name' : 'file-name.md';

        showModal(title, placeholder, async (newName) => {
            const trimmed = String(newName || '').trim();
            if (!trimmed) return;
            if (!isFile && (trimmed.includes('/') || trimmed.includes('\\'))) {
                const store = getNotificationStore();
                if (store) {
                    store.warning('Enter a folder name only (no slashes).', 'editor');
                }
                return;
            }
            const finalName = isFile ? buildRenamedFile(trimmed) : trimmed;
            if (!finalName) {
                const store = getNotificationStore();
                if (store) {
                    store.warning('Enter a valid name (no slashes).', 'editor');
                }
                return;
            }
            const combined = parentFolder ? `${parentFolder}/${finalName}` : finalName;
            const newPath = normalizeWorkspacePath(combined);
            if (!newPath || newPath === oldPath) return;
    
            try {
                await api('/api/rename', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ from: oldPath, to: newPath })
                });
                log(`Renamed: ${oldPath} -> ${newPath}`, 'success');
    
                // Update open tabs after rename
                updateOpenTabsAfterRename(oldPath, newPath, nodeType);
    
                await loadFileTree();
            } catch (err) {
                log(`Rename failed: ${err.message}`, 'error');
            }
        }, '', displayName);
    }
    
    function updateOpenTabsAfterRename(oldPath, newPath, nodeType) {
        // Normalize paths at entry point
        oldPath = normalizeWorkspacePath(oldPath);
        newPath = normalizeWorkspacePath(newPath);
    
        // Collect files and tabs to update
        const filesToUpdate = [];  // { oldFilePath, newFilePath }
        const tabsToUpdate = [];   // { tabId, oldFilePath, newFilePath }
    
        if (nodeType === 'folder') {
            // For folder rename, find all files/tabs under the old folder path
            const oldPrefix = oldPath + '/';
    
            // Find affected files
            for (const [filePath] of state.openFiles) {
                if (filePath.startsWith(oldPrefix)) {
                    const newFilePath = newPath + filePath.substring(oldPath.length);
                    filesToUpdate.push({ oldFilePath: filePath, newFilePath: normalizeWorkspacePath(newFilePath) });
                }
            }
    
            // Find affected tabs
            for (const [tabId, tabData] of state.openTabs) {
                if (tabData.path.startsWith(oldPrefix)) {
                    const newFilePath = newPath + tabData.path.substring(oldPath.length);
                    tabsToUpdate.push({ tabId, oldFilePath: tabData.path, newFilePath: normalizeWorkspacePath(newFilePath) });
                }
            }
        } else {
            // For file rename, update the file and all tabs with that path
            if (state.openFiles.has(oldPath)) {
                filesToUpdate.push({ oldFilePath: oldPath, newFilePath: newPath });
            }
    
            for (const [tabId, tabData] of state.openTabs) {
                if (tabData.path === oldPath) {
                    tabsToUpdate.push({ tabId, oldFilePath: oldPath, newFilePath: newPath });
                }
            }
        }
    
        // Update openFiles entries (move from old path to new path)
        filesToUpdate.forEach(({ oldFilePath, newFilePath }) => {
            const fileData = state.openFiles.get(oldFilePath);
            if (fileData) {
                state.openFiles.delete(oldFilePath);
                state.openFiles.set(newFilePath, fileData);
                log(`Updated file entry: ${oldFilePath} -> ${newFilePath}`, 'info');
            }
        });
    
        // Update each affected tab
        tabsToUpdate.forEach(({ tabId, oldFilePath, newFilePath }) => {
            const tabData = state.openTabs.get(tabId);
            if (!tabData) return;
    
            // Update the path in the tab data
            tabData.path = newFilePath;
    
            // Update the tab element
            const tab = document.querySelector(`.tab[data-tab-id="${tabId}"]`);
            if (tab) {
                tab.dataset.path = newFilePath;
                const tabName = tab.querySelector('.tab-name');
                if (tabName) {
                    tabName.textContent = newFilePath.split('/').pop();
                }
            }
    
            // Update activeFile if this is the active tab
            if (state.activeTabId === tabId) {
                state.activeFile = newFilePath;
            }
    
            log(`Updated tab: ${oldFilePath} -> ${newFilePath}`, 'info');
        });
        persistEditorState();
    }
    
    // Collect all folder paths from the file tree for move dialog
    function collectFolderPaths(node, currentPath = '') {
        const results = [];
    
        // Build path for this node
        let nodePath = currentPath;
        if (node.name && node.name !== 'workspace') {
            nodePath = currentPath ? `${currentPath}/${node.name}` : node.name;
            nodePath = normalizeWorkspacePath(nodePath);
        }
    
        if (node.type === 'folder') {
            // Include this folder (except synthetic root)
            if (nodePath) {
                results.push(nodePath);
            }
    
            if (Array.isArray(node.children)) {
                for (const child of node.children) {
                    results.push(...collectFolderPaths(child, nodePath));
                }
            }
        }
    
        return results;
    }
    
    function getAllFolderPaths() {
        if (!state.fileTree) return [''];
        const paths = collectFolderPaths(state.fileTree, '');
        // Always include root (empty string) as first option
        return [''].concat(paths.sort());
    }
    
    async function promptMove(oldPath, nodeType = 'file') {
        oldPath = normalizeWorkspacePath(oldPath);
    
        // Split old path into folder + basename
        let oldFolder = '';
        let oldName = oldPath;
        const lastSlash = oldPath.lastIndexOf('/');
        if (lastSlash !== -1) {
            oldFolder = oldPath.substring(0, lastSlash);
            oldName = oldPath.substring(lastSlash + 1);
        }
    
        // Collect folders
        let folders = getAllFolderPaths();
    
        // If moving a folder, filter out invalid destinations (self and descendants)
        if (nodeType === 'folder') {
            folders = folders.filter(folder => {
                if (!folder) return true; // root always allowed
                if (folder === oldPath) return false;
                return !folder.startsWith(oldPath + '/');
            });
        }
    
        // Build modal DOM
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
    
        const modal = document.createElement('div');
        modal.className = 'modal move-modal';
    
        const title = document.createElement('div');
        title.className = 'modal-title';
        title.textContent = nodeType === 'folder' ? 'Move Folder' : 'Move File';
    
        const folderLabel = document.createElement('label');
        folderLabel.className = 'move-label';
        folderLabel.textContent = 'Destination folder:';
    
        const folderSelect = document.createElement('select');
        folderSelect.className = 'modal-input move-select';
        for (const folder of folders) {
            const option = document.createElement('option');
            option.value = folder;
            option.textContent = folder || '/ (Project root)';
            if (folder === oldFolder) {
                option.selected = true;
            }
            folderSelect.appendChild(option);
        }
    
        const nameLabel = document.createElement('label');
        nameLabel.className = 'move-label';
        nameLabel.textContent = nodeType === 'folder' ? 'Folder name:' : 'File name:';
    
        const nameInput = document.createElement('input');
        nameInput.type = 'text';
        nameInput.className = 'modal-input';
        nameInput.value = oldName;
    
        const preview = document.createElement('div');
        preview.className = 'move-preview';
    
        function updatePreview() {
            const folder = folderSelect.value;
            const name = nameInput.value.trim();
            if (!name) {
                preview.textContent = 'New path: (invalid – empty name)';
                preview.classList.add('invalid');
                return;
            }
            const combined = folder ? `${folder}/${name}` : name;
            const normalized = normalizeWorkspacePath(combined);
            preview.textContent = `New path: ${normalized}`;
            preview.classList.remove('invalid');
        }
    
        folderSelect.addEventListener('change', updatePreview);
        nameInput.addEventListener('input', updatePreview);
        updatePreview();
    
        const buttons = document.createElement('div');
        buttons.className = 'modal-buttons';
    
        const cancelBtn = document.createElement('button');
        cancelBtn.className = 'modal-btn modal-btn-secondary';
        cancelBtn.textContent = 'Cancel';
    
        const okBtn = document.createElement('button');
        okBtn.className = 'modal-btn modal-btn-primary';
        okBtn.textContent = 'Move';
    
        buttons.appendChild(cancelBtn);
        buttons.appendChild(okBtn);
    
        modal.appendChild(title);
        modal.appendChild(folderLabel);
        modal.appendChild(folderSelect);
        modal.appendChild(nameLabel);
        modal.appendChild(nameInput);
        modal.appendChild(preview);
        modal.appendChild(buttons);
    
        overlay.appendChild(modal);
        document.body.appendChild(overlay);
    
        function closeModal() {
            overlay.remove();
        }
    
        // Focus filename input and select it
        nameInput.focus();
        nameInput.select();
    
        return new Promise(resolve => {
            cancelBtn.addEventListener('click', () => {
                closeModal();
                resolve();
            });
    
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) {
                    closeModal();
                    resolve();
                }
            });
    
            okBtn.addEventListener('click', async () => {
                const folder = folderSelect.value;
                const name = nameInput.value.trim();
                if (!name) {
                    nameInput.focus();
                    return;
                }
                const combined = folder ? `${folder}/${name}` : name;
                const newPath = normalizeWorkspacePath(combined);
                if (!newPath || newPath === oldPath) {
                    closeModal();
                    resolve();
                    return;
                }
    
                try {
                    await api('/api/rename', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ from: oldPath, to: newPath })
                    });
                    log(`Moved: ${oldPath} -> ${newPath}`, 'success');
    
                    // Update open tabs after move
                    updateOpenTabsAfterRename(oldPath, newPath, nodeType);
    
                    await loadFileTree();
                } catch (err) {
                    log(`Move failed: ${err.message}`, 'error');
                }
    
                closeModal();
                resolve();
            });
    
            nameInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    okBtn.click();
                } else if (e.key === 'Escape') {
                    cancelBtn.click();
                }
            });
    
            folderSelect.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    cancelBtn.click();
                }
            });
        });
    }

    async function promptMoveMultiple(paths) {
        const normalized = Array.from(new Set((paths || []).map(path => normalizeTreePath(path)).filter(Boolean)));
        const files = normalized.filter(path => !isOutlinePath(path));
        if (!files.length) return;

        const folders = getAllFolderPaths();
        const initialFolders = files.map(path => {
            const lastSlash = path.lastIndexOf('/');
            return lastSlash === -1 ? '' : path.substring(0, lastSlash);
        });
        const firstFolder = initialFolders[0] || '';
        const defaultFolder = initialFolders.every(folder => folder === firstFolder) ? firstFolder : '';

        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';

        const modal = document.createElement('div');
        modal.className = 'modal move-modal';

        const title = document.createElement('div');
        title.className = 'modal-title';
        title.textContent = `Move ${files.length} File${files.length === 1 ? '' : 's'}`;

        const folderLabel = document.createElement('label');
        folderLabel.className = 'move-label';
        folderLabel.textContent = 'Destination folder:';

        const folderSelect = document.createElement('select');
        folderSelect.className = 'modal-input move-select';
        folders.forEach(folder => {
            const option = document.createElement('option');
            option.value = folder;
            option.textContent = folder || '/ (Project root)';
            if (folder === defaultFolder) {
                option.selected = true;
            }
            folderSelect.appendChild(option);
        });

        const preview = document.createElement('div');
        preview.className = 'move-preview';

        const hint = document.createElement('div');
        hint.className = 'modal-hint';
        hint.textContent = 'Files keep their current names; only the folder changes.';

        const fileList = document.createElement('div');
        fileList.className = 'prep-file-list';
        fileList.style.maxHeight = '160px';
        files.slice(0, 12).forEach(path => {
            const card = document.createElement('div');
            card.className = 'prep-file-card';
            const icon = document.createElement('span');
            icon.className = 'prep-file-icon';
            icon.textContent = '📄';
            const info = document.createElement('div');
            info.className = 'prep-file-info';
            const name = document.createElement('span');
            name.className = 'prep-file-name';
            name.title = path;
            name.textContent = path.split('/').pop();
            const folder = document.createElement('span');
            folder.className = 'prep-file-size';
            folder.textContent = path.substring(0, path.lastIndexOf('/')) || '/';
            info.appendChild(name);
            info.appendChild(folder);
            card.appendChild(icon);
            card.appendChild(info);
            fileList.appendChild(card);
        });
        if (files.length > 12) {
            const more = document.createElement('div');
            more.className = 'prep-summary-empty';
            more.textContent = `+${files.length - 12} more`;
            fileList.appendChild(more);
        }

        function updatePreview() {
            const folder = folderSelect.value;
            const label = folder ? folder : '/ (Project root)';
            preview.textContent = `Move to: ${label}`;
            preview.classList.remove('invalid');
        }

        folderSelect.addEventListener('change', updatePreview);
        updatePreview();

        const buttons = document.createElement('div');
        buttons.className = 'modal-buttons';

        const cancelBtn = document.createElement('button');
        cancelBtn.className = 'modal-btn modal-btn-secondary';
        cancelBtn.textContent = 'Cancel';

        const okBtn = document.createElement('button');
        okBtn.className = 'modal-btn modal-btn-primary';
        okBtn.textContent = 'Move';

        buttons.appendChild(cancelBtn);
        buttons.appendChild(okBtn);

        modal.appendChild(title);
        modal.appendChild(folderLabel);
        modal.appendChild(folderSelect);
        modal.appendChild(preview);
        modal.appendChild(hint);
        modal.appendChild(fileList);
        modal.appendChild(buttons);

        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        function closeModal() {
            overlay.remove();
        }

        return new Promise(resolve => {
            cancelBtn.addEventListener('click', () => {
                closeModal();
                resolve();
            });

            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) {
                    closeModal();
                    resolve();
                }
            });

            okBtn.addEventListener('click', async () => {
                const folder = folderSelect.value;
                const moves = files.map(path => {
                    const base = path.split('/').pop();
                    const combined = folder ? `${folder}/${base}` : base;
                    return {
                        from: path,
                        to: normalizeWorkspacePath(combined)
                    };
                }).filter(move => move.to && move.to !== move.from);

                if (!moves.length) {
                    closeModal();
                    resolve();
                    return;
                }

                okBtn.disabled = true;
                let movedCount = 0;
                let failedCount = 0;

                for (const move of moves) {
                    try {
                        await api('/api/rename', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ from: move.from, to: move.to })
                        });
                        updateOpenTabsAfterRename(move.from, move.to, 'file');
                        movedCount += 1;
                    } catch (err) {
                        failedCount += 1;
                        log(`Move failed: ${move.from} -> ${move.to}: ${err.message}`, 'error');
                    }
                }

                if (movedCount) {
                    log(`Moved ${movedCount} file(s).`, 'success');
                }
                if (failedCount) {
                    log(`Failed to move ${failedCount} file(s).`, 'error');
                }

                const store = getNotificationStore();
                if (store) {
                    if (failedCount) {
                        store.warning(`Moved ${movedCount} file(s); ${failedCount} failed.`, 'editor');
                    } else {
                        store.success(`Moved ${movedCount} file(s).`, 'editor');
                    }
                }

                clearTreeSelection();
                await loadFileTree();
                closeModal();
                resolve();
            });

            overlay.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    cancelBtn.click();
                }
            });
        });
    }
    
    async function promptDelete(path, nodeType = 'file') {
        path = normalizeWorkspacePath(path);
        if (!confirm(`Delete "${path}"?`)) return;
    
        try {
            await api(`/api/file?path=${encodeURIComponent(path)}`, {
                method: 'DELETE'
            });
            log(`Deleted: ${path}`, 'success');
    
            // Close all tabs for this path (and children if folder)
            closeTabsForPath(path, nodeType === 'folder');
    
            await loadFileTree();
        } catch (err) {
            log(`Delete failed: ${err.message}`, 'error');
        }
    }
    
    async function promptNewFile(type = 'file', parentPath = '') {
        parentPath = normalizeWorkspacePath(parentPath);
        // Default placeholder based on type
        let placeholder = type === 'folder' ? 'folder-name' : 'file.txt';

        const title = parentPath ? `New ${type} in ${parentPath}` : `New ${type}`;

        showModal(title, placeholder, async (path) => {
            path = normalizeWorkspacePath(path);
            if (!path) return;
            if (parentPath && !path.includes('/')) {
                path = normalizeWorkspacePath(`${parentPath}/${path}`);
            }
    
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
            const scopedResults = results.filter(result => {
                if (!result || !result.file || explorerScope === 'all') {
                    return true;
                }
                const prefix = explorerScope === 'story' ? 'Story/' : 'Compendium/';
                return result.file.startsWith(prefix);
            });
    
            if (scopedResults.length === 0) {
                elements.searchResults.innerHTML = '<div class="search-no-results">No results found</div>';
                const store = getNotificationStore();
                if (store) {
                    store.editorSearchNoResults(query, true);
                }
                return;
            }
    
            // Group results by file
            const grouped = new Map();
            scopedResults.forEach(result => {
                if (!grouped.has(result.file)) {
                    grouped.set(result.file, []);
                }
                grouped.get(result.file).push(result);
            });
    
            elements.searchResults.innerHTML = '';
    
            // Render grouped results
            grouped.forEach((fileResults, file) => {
                // File header (collapsible)
                const fileGroup = document.createElement('div');
                fileGroup.className = 'search-file-group';
    
                const fileHeader = document.createElement('div');
                fileHeader.className = 'search-file-header';
                fileHeader.innerHTML = `
                    <span class="search-file-icon">📄</span>
                    <span class="search-file-name">${escapeHtml(file)}</span>
                    <span class="search-file-count">${fileResults.length}</span>
                `;
                fileGroup.appendChild(fileHeader);
    
                // Matches container
                const matchesContainer = document.createElement('div');
                matchesContainer.className = 'search-matches expanded';
    
                fileResults.forEach(result => {
                    const item = document.createElement('div');
                    item.className = 'search-match';
    
                    // Highlight the query in preview
                    const highlightedPreview = highlightSearchMatch(result.preview, query);
    
                    item.innerHTML = `
                        <span class="search-match-line">${result.line}</span>
                        <span class="search-match-preview">${highlightedPreview}</span>
                    `;
                    item.addEventListener('click', (e) => {
                        e.stopPropagation();
                        jumpToSearchResult(result.file, result.line, query);
                    });
                    matchesContainer.appendChild(item);
                });
    
                fileGroup.appendChild(matchesContainer);
    
                // Toggle collapse on header click
                fileHeader.addEventListener('click', () => {
                    matchesContainer.classList.toggle('expanded');
                    fileHeader.classList.toggle('collapsed');
                });
    
                elements.searchResults.appendChild(fileGroup);
            });
    
            log(`Search found ${scopedResults.length} results in ${grouped.size} files for "${query}"`, 'info');
        } catch (err) {
            log(`Search failed: ${err.message}`, 'error');
        }
    }
    
    function highlightSearchMatch(text, query) {
        if (!query) return escapeHtml(text);
        const escaped = escapeHtml(text);
        const queryEscaped = escapeHtml(query);
        const regex = new RegExp(`(${queryEscaped.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
        return escaped.replace(regex, '<mark class="search-highlight">$1</mark>');
    }
    
    async function jumpToSearchResult(file, line, query) {
        try {
            await openFile(file);
            if (state.editor) {
                // Reveal line in center
                state.editor.revealLineInCenter(line);
    
                // Find the query in this line and select it
                const model = state.editor.getModel();
                if (model && query) {
                    const lineContent = model.getLineContent(line);
                    const matchIndex = lineContent.toLowerCase().indexOf(query.toLowerCase());
                    if (matchIndex !== -1) {
                        // Select the matched text
                        state.editor.setSelection({
                            startLineNumber: line,
                            startColumn: matchIndex + 1,
                            endLineNumber: line,
                            endColumn: matchIndex + query.length + 1
                        });
                    } else {
                        // Just position cursor at start of line
                        state.editor.setPosition({ lineNumber: line, column: 1 });
                    }
                } else {
                    state.editor.setPosition({ lineNumber: line, column: 1 });
                }
                state.editor.focus();
            }
        } catch (err) {
            log(`Failed to open search result: ${err.message}`, 'error');
        }
    }
    
    function openWorkspaceSearch() {
        // Switch to Search tab
        document.querySelectorAll('.console-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.console-panel').forEach(p => p.classList.remove('active'));
    
        const searchTab = document.querySelector('.console-tab[data-tab="search"]');
        const searchPanel = document.getElementById('search-panel');
    
        if (searchTab && searchPanel) {
            searchTab.classList.add('active');
            searchPanel.classList.add('active');
        }
    
        // Focus the search input
        updateSearchPlaceholder();
        elements.searchInput.focus();
        elements.searchInput.select();
    
        log('Project search (Ctrl+Shift+F)', 'info');
    }
    

    function getExplorerVisible() {
        const stored = localStorage.getItem('explorer-visible');
        // Default to true (visible) if not set
        return stored === null ? true : stored === '1';
    }
    
    function setExplorerVisible(visible) {
        if (!elements.explorerPanel) return;
        elements.explorerPanel.classList.toggle('is-hidden', !visible);
        localStorage.setItem('explorer-visible', visible ? '1' : '0');
        updateExplorerScopeUI();

        // When showing the explorer panel, sync with versioning state
        if (visible && window.versioning) {
            window.versioning.syncWithExplorerState();
            // Update button states based on versioning state
            const commitBtn = document.getElementById('btn-commit');
            if (commitBtn) {
                commitBtn.classList.toggle('active', window.versioning.isActive());
            }
        }
    }

    window.initSplitters = initSplitters;
    window.initMonaco = initMonaco;
    window.loadFileTree = loadFileTree;
    window.renderFileTree = renderFileTree;
    window.openFile = openFile;
    window.openFileInNewTab = openFileInNewTab;
    window.explorePath = explorePath;
    window.promptRename = promptRename;
    window.promptMove = promptMove;
    window.promptDelete = promptDelete;
    window.promptNewFile = promptNewFile;
    window.closeTabsForPath = closeTabsForPath;
    window.saveCurrentFile = saveCurrentFile;
    window.saveAllFiles = saveAllFiles;
    window.openWorkspaceSearch = openWorkspaceSearch;
    window.performSearch = performSearch;
    window.getExplorerVisible = getExplorerVisible;
    window.setExplorerVisible = setExplorerVisible;
    window.setExplorerScope = setExplorerScope;
    window.getExplorerScope = getExplorerScope;
    window.canRenamePath = canRenamePath;
    window.isPrepMultiSelectEnabled = isPrepMultiSelectEnabled;
    window.getTreeSelection = getTreeSelection;
    window.setTreeSelection = setTreeSelection;
    window.clearTreeSelection = clearTreeSelection;
    window.promptMoveMultiple = promptMoveMultiple;
    window.restoreEditorState = restoreEditorState;
    window.reloadOpenFile = reloadOpenFile;
    window.reloadAllOpenFiles = reloadAllOpenFiles;
})();
