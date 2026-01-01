# Control Room Refactoring Plan

This document outlines a comprehensive refactoring strategy for the god files identified in the codebase. Files are prioritized by impact and complexity.

---

## Priority 1: CRITICAL

### 1.1 Main.java (1,471 lines)

**Current State:** Monolithic API router containing 69 private endpoint handlers mixed with application bootstrap.

**Target State:** Thin bootstrap class delegating to domain-specific controllers.

#### Step 1: Create Controller Infrastructure

Create a base controller interface:

```java
// src/main/java/com/miniide/controllers/Controller.java
public interface Controller {
    void registerRoutes(Javalin app);
}
```

#### Step 2: Extract FileController

Extract file operations (~400 lines):

```java
// src/main/java/com/miniide/controllers/FileController.java
public class FileController implements Controller {
    private final WorkspaceService workspaceService;

    // Methods to extract:
    // - getTree()
    // - getFile()
    // - putFile()
    // - createFile()
    // - createFolder()
    // - deleteFile()
    // - renameFile()
    // - duplicateFile()
    // - search()
}
```

#### Step 3: Extract WorkspaceController

```java
// src/main/java/com/miniide/controllers/WorkspaceController.java
public class WorkspaceController implements Controller {
    // Methods to extract:
    // - openWorkspace()
    // - selectWorkspace()
    // - getWorkspaceInfo()
}
```

#### Step 4: Extract AgentController

```java
// src/main/java/com/miniide/controllers/AgentController.java
public class AgentController implements Controller {
    private final AgentRegistry agentRegistry;

    // Methods to extract:
    // - getAgents()
    // - createAgent()
    // - updateAgent()
    // - deleteAgent()
    // - setAgentStatus()
    // - reorderAgents()
    // - importAgent()
    // - getAgentEndpoints()
    // - updateAgentEndpoints()
}
```

#### Step 5: Extract SettingsController

```java
// src/main/java/com/miniide/controllers/SettingsController.java
public class SettingsController implements Controller {
    // Methods to extract:
    // - getSecuritySettings()
    // - updateSecuritySettings()
    // - getApiKeys()
    // - updateApiKeys()
    // - getVault()
    // - unlockVault()
}
```

#### Step 6: Extract NotificationController

```java
// src/main/java/com/miniide/controllers/NotificationController.java
public class NotificationController implements Controller {
    private final NotificationStore notificationStore;

    // Methods to extract (14 methods):
    // - getNotifications()
    // - dismissNotification()
    // - dismissAllNotifications()
    // - markAsRead()
    // - etc.
}
```

#### Step 7: Extract IssueController

```java
// src/main/java/com/miniide/controllers/IssueController.java
public class IssueController implements Controller {
    private final IssueStore issueStore;

    // Methods to extract (8 methods):
    // - getIssues()
    // - createIssue()
    // - updateIssue()
    // - deleteIssue()
    // - addComment()
    // - etc.
}
```

#### Step 8: Extract ProviderController

```java
// src/main/java/com/miniide/controllers/ProviderController.java
public class ProviderController implements Controller {
    // Methods to extract:
    // - getProviderModels()
    // - chat()
}
```

#### Step 9: Extract RoleController

```java
// src/main/java/com/miniide/controllers/RoleController.java
public class RoleController implements Controller {
    // Methods to extract:
    // - getRoleSettings()
    // - updateRoleSettings()
}
```

#### Step 10: Refactor Main.java

Final Main.java should be ~100 lines:

```java
public class Main {
    public static void main(String[] args) {
        // Load config
        AppConfig config = AppConfig.load();

        // Initialize services
        WorkspaceService workspaceService = new WorkspaceService(config);
        AgentRegistry agentRegistry = new AgentRegistry(config);
        // ... other services

        // Initialize Javalin
        Javalin app = Javalin.create(cfg -> {
            cfg.staticFiles.add("/public", Location.CLASSPATH);
        });

        // Register controllers
        List<Controller> controllers = List.of(
            new FileController(workspaceService),
            new WorkspaceController(workspaceService, config),
            new AgentController(agentRegistry),
            new SettingsController(settingsService),
            new NotificationController(notificationStore),
            new IssueController(issueStore),
            new ProviderController(chatService, modelsService),
            new RoleController(agentRegistry)
        );

        controllers.forEach(c -> c.registerRoutes(app));

        app.start(config.getPort());
    }
}
```

---

### 1.2 app.js (6,770 lines)

**Current State:** Monolithic frontend with all UI logic in a single file.

**Target State:** Modular ES6 structure with separated concerns.

#### Step 1: Create Module Structure

```
src/main/resources/public/
├── app.js              (reduced to ~200 lines - bootstrap only)
├── modules/
│   ├── state.js        (~100 lines - centralized state management)
│   ├── api.js          (~200 lines - all API calls)
│   ├── editor.js       (~800 lines - editor operations)
│   ├── fileTree.js     (~400 lines - file tree navigation)
│   ├── notifications.js (~300 lines - notification system)
│   ├── issues.js       (~500 lines - issue board)
│   ├── agents.js       (~600 lines - agent roster)
│   ├── settings.js     (~400 lines - settings panels)
│   ├── modals.js       (~300 lines - modal management)
│   └── utils.js        (~100 lines - shared utilities)
```

#### Step 2: Extract State Management (state.js)

```javascript
// modules/state.js
export const state = {
    editor: {
        openFiles: [],
        activeFile: null,
        segments: []
    },
    workspace: {
        path: null,
        tree: []
    },
    agents: [],
    notifications: [],
    issues: [],
    settings: {}
};

export function updateState(path, value) {
    // Immutable state update helper
}

export function getState(path) {
    // State getter helper
}
```

#### Step 3: Extract API Module (api.js)

```javascript
// modules/api.js
const BASE_URL = '';

export const api = {
    // File operations
    async getTree() { /* ... */ },
    async getFile(path) { /* ... */ },
    async saveFile(path, content) { /* ... */ },
    async createFile(path, type) { /* ... */ },
    async deleteFile(path) { /* ... */ },

    // Agent operations
    async getAgents() { /* ... */ },
    async createAgent(data) { /* ... */ },
    async updateAgent(id, data) { /* ... */ },

    // Notification operations
    async getNotifications() { /* ... */ },
    async dismissNotification(id) { /* ... */ },

    // Issue operations
    async getIssues() { /* ... */ },
    async createIssue(data) { /* ... */ },

    // Settings operations
    async getSettings() { /* ... */ },
    async updateSettings(data) { /* ... */ },

    // Provider operations
    async getModels(provider) { /* ... */ },
    async chat(agentId, message) { /* ... */ }
};
```

#### Step 4: Extract Editor Module (editor.js)

```javascript
// modules/editor.js
import { state, updateState } from './state.js';
import { api } from './api.js';

export const editor = {
    // Tab management
    openFile(path) { /* ... */ },
    closeFile(path) { /* ... */ },
    switchTab(path) { /* ... */ },

    // Content management
    async loadContent(path) { /* ... */ },
    async saveContent(path) { /* ... */ },
    markDirty(path) { /* ... */ },

    // Segment management
    getSegments(path) { /* ... */ },
    updateSegment(path, segmentId, content) { /* ... */ },

    // Rendering
    renderTabs() { /* ... */ },
    renderContent() { /* ... */ },

    // Event handlers
    handleKeyDown(event) { /* ... */ },
    handleInput(event) { /* ... */ }
};
```

#### Step 5: Extract File Tree Module (fileTree.js)

```javascript
// modules/fileTree.js
import { state, updateState } from './state.js';
import { api } from './api.js';

export const fileTree = {
    async load() { /* ... */ },
    render() { /* ... */ },
    expand(path) { /* ... */ },
    collapse(path) { /* ... */ },
    select(path) { /* ... */ },

    // Context menu
    showContextMenu(path, event) { /* ... */ },
    hideContextMenu() { /* ... */ },

    // Operations
    async createFile(parentPath) { /* ... */ },
    async createFolder(parentPath) { /* ... */ },
    async rename(path) { /* ... */ },
    async delete(path) { /* ... */ },
    async duplicate(path) { /* ... */ }
};
```

#### Step 6: Extract Notifications Module (notifications.js)

```javascript
// modules/notifications.js
import { state, updateState } from './state.js';
import { api } from './api.js';

export const notifications = {
    async load() { /* ... */ },
    render() { /* ... */ },

    show(notification) { /* ... */ },
    dismiss(id) { /* ... */ },
    dismissAll() { /* ... */ },
    markAsRead(id) { /* ... */ },

    // Polling
    startPolling(interval) { /* ... */ },
    stopPolling() { /* ... */ }
};
```

#### Step 7: Extract Issues Module (issues.js)

```javascript
// modules/issues.js
import { state, updateState } from './state.js';
import { api } from './api.js';

export const issues = {
    async load() { /* ... */ },
    render() { /* ... */ },

    // CRUD
    async create(data) { /* ... */ },
    async update(id, data) { /* ... */ },
    async delete(id) { /* ... */ },

    // Comments
    async addComment(issueId, comment) { /* ... */ },

    // Modal
    showModal(issue) { /* ... */ },
    hideModal() { /* ... */ },

    // Board rendering
    renderBoard() { /* ... */ },
    renderCard(issue) { /* ... */ }
};
```

#### Step 8: Extract Agents Module (agents.js)

```javascript
// modules/agents.js
import { state, updateState } from './state.js';
import { api } from './api.js';

export const agents = {
    async load() { /* ... */ },
    render() { /* ... */ },

    // CRUD
    async create(data) { /* ... */ },
    async update(id, data) { /* ... */ },
    async delete(id) { /* ... */ },

    // Status
    async setStatus(id, status) { /* ... */ },

    // Ordering
    async reorder(orderedIds) { /* ... */ },

    // Modals
    showSettingsModal(agent) { /* ... */ },
    showOnboardingWizard() { /* ... */ },

    // Roster rendering
    renderRoster() { /* ... */ },
    renderAgentCard(agent) { /* ... */ }
};
```

#### Step 9: Extract Settings Module (settings.js)

```javascript
// modules/settings.js
import { state, updateState } from './state.js';
import { api } from './api.js';

export const settings = {
    async load() { /* ... */ },
    render() { /* ... */ },

    // Security
    async updateSecurityMode(mode) { /* ... */ },

    // API Keys
    async updateApiKey(provider, key) { /* ... */ },

    // Vault
    async unlockVault(password) { /* ... */ },

    // Panels
    showPanel(panel) { /* ... */ },
    hidePanel() { /* ... */ }
};
```

#### Step 10: Refactor Main app.js

```javascript
// app.js - Bootstrap only
import { state } from './modules/state.js';
import { api } from './modules/api.js';
import { editor } from './modules/editor.js';
import { fileTree } from './modules/fileTree.js';
import { notifications } from './modules/notifications.js';
import { issues } from './modules/issues.js';
import { agents } from './modules/agents.js';
import { settings } from './modules/settings.js';

// Initialize application
async function init() {
    // Load initial data
    await Promise.all([
        fileTree.load(),
        agents.load(),
        notifications.load(),
        issues.load()
    ]);

    // Render initial UI
    fileTree.render();
    agents.render();
    notifications.render();
    issues.render();

    // Start polling
    notifications.startPolling(5000);

    // Setup global event listeners
    setupKeyboardShortcuts();
    setupResizeHandlers();
}

function setupKeyboardShortcuts() {
    document.addEventListener('keydown', (e) => {
        if (e.ctrlKey && e.key === 's') {
            e.preventDefault();
            editor.saveContent(state.editor.activeFile);
        }
        // ... other shortcuts
    });
}

// Start app
document.addEventListener('DOMContentLoaded', init);
```

---

## Priority 2: HIGH

### 2.1 ProviderChatService.java (333 lines)

**Current State:** Single class handling chat for 8 different AI providers with mixed logic.

**Target State:** Strategy pattern with provider-specific implementations.

#### Step 1: Create ChatProvider Interface

```java
// src/main/java/com/miniide/providers/chat/ChatProvider.java
public interface ChatProvider {
    String getProviderName();
    String chat(String model, List<Message> messages, String apiKey, String endpoint) throws Exception;
    String normalizeEndpoint(String endpoint);
}
```

#### Step 2: Create Abstract Base Class

```java
// src/main/java/com/miniide/providers/chat/AbstractChatProvider.java
public abstract class AbstractChatProvider implements ChatProvider {
    protected final HttpClient httpClient;
    protected final ObjectMapper objectMapper;

    protected AbstractChatProvider() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    protected HttpResponse<String> post(String url, String body, Map<String, String> headers) {
        // Common HTTP logic
    }
}
```

#### Step 3: Extract Provider Implementations

```java
// src/main/java/com/miniide/providers/chat/AnthropicChatProvider.java
public class AnthropicChatProvider extends AbstractChatProvider {
    @Override
    public String getProviderName() { return "anthropic"; }

    @Override
    public String chat(String model, List<Message> messages, String apiKey, String endpoint) {
        // Anthropic-specific payload and parsing
    }

    @Override
    public String normalizeEndpoint(String endpoint) {
        // Anthropic endpoint normalization
    }
}

// src/main/java/com/miniide/providers/chat/GeminiChatProvider.java
public class GeminiChatProvider extends AbstractChatProvider { /* ... */ }

// src/main/java/com/miniide/providers/chat/OllamaChatProvider.java
public class OllamaChatProvider extends AbstractChatProvider { /* ... */ }

// src/main/java/com/miniide/providers/chat/OpenRouterChatProvider.java
public class OpenRouterChatProvider extends AbstractChatProvider { /* ... */ }

// src/main/java/com/miniide/providers/chat/OpenAiCompatibleChatProvider.java
// Handles: OpenAI, Grok, TogetherAI, LMStudio, Jan, KoboldCPP, Custom
public class OpenAiCompatibleChatProvider extends AbstractChatProvider { /* ... */ }
```

#### Step 4: Create Provider Factory

```java
// src/main/java/com/miniide/providers/chat/ChatProviderFactory.java
public class ChatProviderFactory {
    private static final Map<String, ChatProvider> providers = new HashMap<>();

    static {
        register(new AnthropicChatProvider());
        register(new GeminiChatProvider());
        register(new OllamaChatProvider());
        register(new OpenRouterChatProvider());
        register(new OpenAiCompatibleChatProvider());
    }

    public static ChatProvider getProvider(String name) {
        ChatProvider provider = providers.get(name.toLowerCase());
        if (provider == null) {
            // Fall back to OpenAI-compatible for unknown providers
            return providers.get("openai");
        }
        return provider;
    }

    private static void register(ChatProvider provider) {
        providers.put(provider.getProviderName(), provider);
    }
}
```

#### Step 5: Simplify ProviderChatService

```java
// src/main/java/com/miniide/providers/ProviderChatService.java
public class ProviderChatService {
    public String chat(String provider, String model, List<Message> messages,
                       String apiKey, String endpoint) throws Exception {
        ChatProvider chatProvider = ChatProviderFactory.getProvider(provider);
        String normalizedEndpoint = chatProvider.normalizeEndpoint(endpoint);
        return chatProvider.chat(model, messages, apiKey, normalizedEndpoint);
    }
}
```

---

### 2.2 ProviderModelsService.java (317 lines)

**Current State:** Single class fetching models from 11+ providers.

**Target State:** Strategy pattern mirroring chat providers.

#### Step 1: Create ModelsProvider Interface

```java
// src/main/java/com/miniide/providers/models/ModelsProvider.java
public interface ModelsProvider {
    String getProviderName();
    List<ProviderModel> fetchModels(String apiKey, String endpoint) throws Exception;
    String normalizeEndpoint(String endpoint);
}
```

#### Step 2: Create Provider Implementations

```java
// src/main/java/com/miniide/providers/models/AnthropicModelsProvider.java
public class AnthropicModelsProvider extends AbstractModelsProvider {
    @Override
    public List<ProviderModel> fetchModels(String apiKey, String endpoint) {
        // Return static list of Anthropic models
    }
}

// Similar for other providers...
```

#### Step 3: Create Factory and Simplify Service

Follow same pattern as ProviderChatService.

---

### 2.3 SettingsService.java (302 lines)

**Current State:** Mixed concerns - security settings, key management, vault operations, migration.

**Target State:** Separated services with single responsibilities.

#### Step 1: Extract KeyVaultManager

```java
// src/main/java/com/miniide/settings/KeyVaultManager.java
public class KeyVaultManager {
    private final Path vaultPath;

    public KeyVaultManager(Path vaultPath) {
        this.vaultPath = vaultPath;
    }

    public boolean exists() { /* ... */ }
    public void create(String password) { /* ... */ }
    public KeyVault unlock(String password) { /* ... */ }
    public void save(KeyVault vault, String password) { /* ... */ }
    public void delete() { /* ... */ }
}
```

#### Step 2: Extract KeyMigrationService

```java
// src/main/java/com/miniide/settings/KeyMigrationService.java
public class KeyMigrationService {
    private final KeyVaultManager vaultManager;
    private final PlaintextKeyStore plaintextStore;

    public void migrateToEncrypted(String password) {
        // Read plaintext keys
        // Create vault
        // Store keys in vault
        // Delete plaintext keys
    }

    public void migrateToPlaintext(String password) {
        // Unlock vault
        // Read keys
        // Store as plaintext
        // Delete vault
    }
}
```

#### Step 3: Simplify SettingsService

```java
// src/main/java/com/miniide/settings/SettingsService.java
public class SettingsService {
    private final KeyVaultManager vaultManager;
    private final KeyMigrationService migrationService;
    private final PlaintextKeyStore plaintextStore;

    public SecuritySettings getSecuritySettings() { /* ... */ }

    public void updateSecurityMode(String mode, String password) {
        if ("encrypted".equals(mode)) {
            migrationService.migrateToEncrypted(password);
        } else {
            migrationService.migrateToPlaintext(password);
        }
    }

    public Map<String, String> getApiKeys() { /* ... */ }
    public void updateApiKey(String provider, String key) { /* ... */ }
}
```

---

## Priority 3: MEDIUM-HIGH

### 3.1 WorkspaceService.java (561 lines)

**Current State:** 22 public methods covering 6+ domains.

**Target State:** Facade pattern with specialized sub-services.

#### Step 1: Extract FileOperationsService

```java
// src/main/java/com/miniide/workspace/FileOperationsService.java
public class FileOperationsService {
    public void createFile(Path path) { /* ... */ }
    public void createFolder(Path path) { /* ... */ }
    public void delete(Path path) { /* ... */ }
    public void rename(Path from, Path to) { /* ... */ }
    public void duplicate(Path path) { /* ... */ }
    public String readFile(Path path) { /* ... */ }
    public void writeFile(Path path, String content) { /* ... */ }
}
```

#### Step 2: Extract DirectoryService

```java
// src/main/java/com/miniide/workspace/DirectoryService.java
public class DirectoryService {
    public List<FileNode> getTree(Path root) { /* ... */ }
    public List<FileNode> getChildren(Path dir) { /* ... */ }
    public boolean exists(Path path) { /* ... */ }
    public boolean isDirectory(Path path) { /* ... */ }
}
```

#### Step 3: Extract SearchService

```java
// src/main/java/com/miniide/workspace/SearchService.java
public class SearchService {
    public List<SearchResult> search(Path root, String query) { /* ... */ }
    public List<SearchResult> searchInFile(Path file, String query) { /* ... */ }
}
```

#### Step 4: WorkspaceService as Facade

```java
// src/main/java/com/miniide/WorkspaceService.java
public class WorkspaceService {
    private final FileOperationsService fileOps;
    private final DirectoryService directoryService;
    private final SearchService searchService;

    // Delegate methods or expose sub-services
}
```

---

## Priority 4: MEDIUM

### 4.1 AgentRegistry.java (459 lines)

**Target:** Split agent and role settings management.

```java
// src/main/java/com/miniide/agents/AgentRegistry.java
// Keep only agent CRUD operations

// src/main/java/com/miniide/agents/RoleSettingsRegistry.java
// Extract role settings CRUD

// src/main/java/com/miniide/agents/DefaultAgentsProvider.java
// Extract default agent definitions
```

### 4.2 AppConfig.java (355 lines)

**Target:** Separate config data from path utilities.

```java
// src/main/java/com/miniide/AppConfig.java
// Keep as data holder with builder

// src/main/java/com/miniide/util/PlatformPaths.java
// Extract static path utilities

// src/main/java/com/miniide/WorkspaceSelector.java
// Extract workspace persistence logic
```

### 4.3 NotificationStore.java (290 lines)

**Target:** Separate storage from notification creation.

```java
// src/main/java/com/miniide/notifications/NotificationStore.java
// Keep storage and lifecycle only

// src/main/java/com/miniide/notifications/NotificationFactory.java
// Extract all convenience methods (info, success, error, etc.)

// src/main/java/com/miniide/notifications/EditorNotifications.java
// Domain-specific notification builders
```

---

## Implementation Order

Recommended order based on dependencies and impact:

### Phase 1: Backend Controllers (Main.java)
1. Create Controller interface
2. Extract FileController
3. Extract AgentController
4. Extract remaining controllers
5. Refactor Main.java

### Phase 2: Provider Services
1. Create ChatProvider interface and implementations
2. Create ModelsProvider interface and implementations
3. Update ProviderChatService and ProviderModelsService

### Phase 3: Settings Refactoring
1. Extract KeyVaultManager
2. Extract KeyMigrationService
3. Simplify SettingsService

### Phase 4: Frontend Modularization (app.js)
1. Create module folder structure
2. Extract state.js and api.js (foundational modules)
3. Extract editor.js
4. Extract remaining modules
5. Refactor main app.js

### Phase 5: Secondary Refactoring
1. WorkspaceService decomposition
2. AgentRegistry split
3. AppConfig cleanup
4. NotificationStore extraction

---

## Testing Strategy

For each refactoring:

1. **Before refactoring:** Ensure existing tests pass (or write them if missing)
2. **During refactoring:** Keep tests green, refactor in small increments
3. **After refactoring:** Add unit tests for new classes

Key test areas:
- Controller route registration and handler delegation
- Provider strategy selection and execution
- State management in frontend modules
- Service delegation patterns

---

## Notes

- Each phase can be done independently
- Maintain backwards compatibility during transition
- Use feature flags if needed for gradual rollout
- Document any API changes for frontend/backend coordination
