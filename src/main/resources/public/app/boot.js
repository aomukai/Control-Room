// Boot module (refactor split)
(function() {
    'use strict';

    function log(message, level) {
        if (window.log) {
            window.log(message, level);
        }
    }

    // Initialize
    function init() {
        log('Control Room starting...', 'info');
    
        window.initSplitters();
        window.initMonaco();
        window.initConsoleTabs();
        window.initAIActions();
        window.initAIFoundation();
        window.initSidebarButtons();
        window.initEventListeners();
        window.initMemoryModeratorControls();
        window.initPromptToolsControls();
        window.initNotifications();
        window.initWorkbenchNewsfeedSubscription(); // Newsfeed updates
        window.loadFileTree();
        window.loadAgents();
        window.loadWorkspaceInfo();
    
        // Initialize widget system
        window.registerBuiltInWidgets();
    
        // Set initial view mode (starts in Editor mode)
        window.setViewMode('editor');
    
        // Welcome message in chat
        window.addChatMessage('assistant', 'Hello! I\'m your AI writing assistant. How can I help you with your creative writing project today?');
    
        log('Control Room ready!', 'success');
        log('Tip: Use the sidebar toggle to switch Workbench and Editor views.', 'info');
    }

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
