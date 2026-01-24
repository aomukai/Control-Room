# Codebase Routing Map

Purpose: A high-signal map to pick the right 3 files without grepping. Docs live in `docs/index.md`.
Notes: `docs/**` is intentionally skipped here. Vendor/generated/assets are collapsed at the end.

## Root (Build + Run)
Owns: build config, wrappers, and launch scripts. Start here for build/run and environment.
- `build.gradle` - role: Gradle build script; owns: JVM deps/build tasks; key symbols: Gradle plugins/deps; touchpoints: `settings.gradle`, `gradle.properties`.
- `settings.gradle` - role: Gradle project settings; owns: root project name; key symbols: `rootProject.name`; touchpoints: `build.gradle`.
- `gradle.properties` - role: Gradle config; owns: JVM/Gradle flags; key symbols: property keys; touchpoints: `build.gradle`.
- `gradlew`, `gradlew.bat` - role: Gradle wrapper launchers; owns: build/run entry; key symbols: wrapper scripts; touchpoints: `gradle/wrapper/gradle-wrapper.properties`.
- `package.json`, `package-lock.json` - role: frontend tooling metadata; owns: Node deps; key symbols: deps list; touchpoints: `src/main/resources/public`.
- `run.sh`, `run.bat` - role: app launch scripts; owns: local run workflow; key symbols: shell entry; touchpoints: `src/main/java/com/miniide/Main.java`.

## Backend (Java) - `src/main/java/com/miniide`
Owns: HTTP API, project-scoped services, persistence, schedulers, provider integrations.
Start here for backend flow: `src/main/java/com/miniide/Main.java`.

### App Boot + Core Wiring
- `src/main/java/com/miniide/Main.java` - role: server bootstrap + controller registration; owns: lifecycle + schedulers; key symbols: `Main.main`, `registerExceptionHandlers`; touchpoints: `src/main/java/com/miniide/AppConfig.java`, `src/main/java/com/miniide/ProjectContext.java`, `src/main/java/com/miniide/controllers/*`.
- `src/main/java/com/miniide/AppConfig.java` - role: config/paths/ports; owns: workspace/log/settings directories; key symbols: `AppConfig.Builder`, `getConfiguredWorkspaceRoot`, `findAvailablePort`; touchpoints: `src/main/java/com/miniide/Main.java`, `run.sh`.
- `src/main/java/com/miniide/AppLogger.java` - role: logging setup + console output; owns: log file + console channel; key symbols: `AppLogger.initialize`, `info/warn/error`; touchpoints: `src/main/java/com/miniide/Main.java`, controllers/services.
- `src/main/java/com/miniide/BrowserLauncher.java` - role: open UI in browser; owns: launch behavior; key symbols: `openBrowserDelayed`; touchpoints: `src/main/java/com/miniide/Main.java`.
- `src/main/java/com/miniide/AgentTurnGate.java` - role: serialized agent turn queue; owns: single-active-agent rule; key symbols: queue/lock helpers; touchpoints: `src/main/resources/public/app/agents.js`, chat workflows.
- `src/main/java/com/miniide/CircuitBreakerConfig.java` - role: circuit breaker thresholds; owns: stop-hook config; key symbols: config fields; touchpoints: `src/main/java/com/miniide/CircuitBreakerValidator.java`.
- `src/main/java/com/miniide/CircuitBreakerValidator.java` - role: validate comment/issue content; owns: safety enforcement; key symbols: validator methods; touchpoints: `src/main/java/com/miniide/controllers/IssueController.java`.

### Project Context + Workspace
- `src/main/java/com/miniide/ProjectContext.java` - role: project-scoped service holder; owns: service reload on project switch; key symbols: `load`, `switchWorkspace`; touchpoints: `src/main/java/com/miniide/controllers/WorkspaceController.java`, `src/main/java/com/miniide/WorkspaceService.java`.
- `src/main/java/com/miniide/WorkspaceService.java` - role: project filesystem + metadata; owns: workspace root + file listing helpers; key symbols: workspace accessors; touchpoints: `src/main/java/com/miniide/controllers/FileController.java`, `src/main/java/com/miniide/controllers/WorkspaceController.java`.
- `src/main/java/com/miniide/FileService.java` - role: filesystem access + normalization; owns: file IO helpers; key symbols: file ops; touchpoints: `src/main/java/com/miniide/controllers/FileController.java`.
- `src/main/java/com/miniide/PreparedWorkspaceService.java` - role: prepared mode metadata; owns: prepared project state; key symbols: prepared state getters; touchpoints: `src/main/java/com/miniide/ProjectPreparationService.java`, `src/main/java/com/miniide/controllers/PreparationController.java`.
- `src/main/java/com/miniide/ProjectPreparationService.java` - role: ingest/canon prep; owns: project preparation workflow + outputs; key symbols: ingest + reindex; touchpoints: `src/main/java/com/miniide/controllers/PreparationController.java`, prepared workspace models.

### Stores, Services, Schedulers
- `src/main/java/com/miniide/IssueMemoryService.java` - role: issue storage + CRUD; owns: `data/issues.json` per project; key symbols: issue CRUD + comment add; touchpoints: `src/main/java/com/miniide/controllers/IssueController.java`, `src/main/resources/public/api.js`.
- `src/main/java/com/miniide/MemoryService.java` - role: librarian memory substrate; owns: memory items/versions/events; key symbols: `create`, `get`, `getEvidence`, `decay`; touchpoints: `src/main/java/com/miniide/controllers/MemoryController.java`, `src/main/resources/public/api.js`.
- `src/main/java/com/miniide/NotificationStore.java` - role: notification persistence + filters; owns: `data/notifications.json`; key symbols: push/list/mark read; touchpoints: `src/main/java/com/miniide/controllers/NotificationController.java`, `src/main/resources/public/notifications.js`.
- `src/main/java/com/miniide/CreditStore.java` - role: credit event storage; owns: `workspace/<project>/.control-room/credits/credits.json`; key symbols: list/create; touchpoints: `src/main/java/com/miniide/controllers/CreditController.java`.
- `src/main/java/com/miniide/PatchService.java` - role: patch proposals + apply; owns: patch proposal persistence; key symbols: create/apply/reject; touchpoints: `src/main/java/com/miniide/controllers/PatchController.java`, patch models.
- `src/main/java/com/miniide/PromptRegistry.java` - role: prompt tools registry; owns: prompt tool JSON file; key symbols: list/save/delete; touchpoints: `src/main/java/com/miniide/controllers/PromptController.java`, `src/main/resources/public/app.js`.
- `src/main/java/com/miniide/DashboardLayoutStore.java` - role: dashboard widget layout storage; owns: layout JSON; key symbols: load/save layout; touchpoints: `src/main/java/com/miniide/controllers/DashboardController.java`, `src/main/resources/public/app/widgets.js`.
- `src/main/java/com/miniide/DecayConfigStore.java` - role: decay scheduler config; owns: `data/decay-config.json`; key symbols: load/save config; touchpoints: `src/main/java/com/miniide/MemoryDecayScheduler.java`, `src/main/java/com/miniide/controllers/MemoryController.java`.
- `src/main/java/com/miniide/MemoryDecayScheduler.java` - role: background decay runner; owns: scheduled memory pruning; key symbols: `start/stop`; touchpoints: `src/main/java/com/miniide/MemoryService.java`, `src/main/java/com/miniide/Main.java`.
- `src/main/java/com/miniide/PatchCleanupConfigStore.java` - role: patch cleanup config; owns: patch cleanup settings; key symbols: load/save config; touchpoints: `src/main/java/com/miniide/PatchCleanupScheduler.java`, `src/main/java/com/miniide/controllers/PatchController.java`.
- `src/main/java/com/miniide/PatchCleanupScheduler.java` - role: background patch cleanup; owns: cleanup cadence; key symbols: `start/stop`; touchpoints: `src/main/java/com/miniide/PatchService.java`, `src/main/java/com/miniide/Main.java`.
- `src/main/java/com/miniide/storage/JsonStorage.java` - role: generic JSON persistence helper; owns: load/save utilities; key symbols: read/write; touchpoints: stores/services above.

### Agents + Endpoints
- `src/main/java/com/miniide/AgentRegistry.java` - role: agent roster persistence; owns: `workspace/<project>/.control-room/agents/agents.json`; key symbols: list/save/update; touchpoints: `src/main/java/com/miniide/controllers/AgentController.java`, `src/main/resources/public/app/agents.js`.
- `src/main/java/com/miniide/AgentEndpointRegistry.java` - role: agent endpoint persistence; owns: `agent-endpoints.json`; key symbols: get/set endpoints; touchpoints: `src/main/java/com/miniide/controllers/AgentController.java`, provider services.

### Providers + Settings
- `src/main/java/com/miniide/providers/ProviderChatService.java` - role: chat provider orchestration; owns: provider selection/dispatch; key symbols: chat request pipeline; touchpoints: `src/main/java/com/miniide/controllers/ChatController.java`, `src/main/java/com/miniide/providers/chat/*`.
- `src/main/java/com/miniide/providers/ProviderModelsService.java` - role: fetch model lists; owns: provider model discovery; key symbols: list models; touchpoints: `src/main/java/com/miniide/controllers/SettingsController.java`, `src/main/java/com/miniide/providers/models/*`.
- `src/main/java/com/miniide/providers/chat/ChatProviderFactory.java` - role: provider factory; owns: chat provider selection; key symbols: `create`; touchpoints: `ProviderChatService`.
- `src/main/java/com/miniide/providers/models/ModelsProviderFactory.java` - role: provider factory; owns: models provider selection; key symbols: `create`; touchpoints: `ProviderModelsService`.
- `src/main/java/com/miniide/settings/SettingsService.java` - role: key/security settings; owns: settings storage + migrations; key symbols: get/update security, keys; touchpoints: `src/main/java/com/miniide/controllers/SettingsController.java`.
- `src/main/java/com/miniide/settings/KeyVault.java` - role: encrypted key storage; owns: vault encryption/decryption; key symbols: lock/unlock; touchpoints: `SettingsService`, `EncryptedVaultFile`.
- `src/main/java/com/miniide/settings/PlaintextKeyStore.java` - role: plaintext key storage; owns: unencrypted key file; key symbols: read/write keys; touchpoints: `SettingsService`.
- `src/main/java/com/miniide/settings/EncryptedVaultFile.java` - role: encrypted vault data model; owns: vault file structure; key symbols: data fields; touchpoints: `KeyVault`.
- `src/main/java/com/miniide/settings/AgentKeysFile.java` - role: provider key records; owns: key entries; key symbols: list/add/remove; touchpoints: `SettingsService`.
- `src/main/java/com/miniide/settings/AgentKeysMetadataFile.java` - role: key metadata; owns: key labels/status; key symbols: metadata records; touchpoints: `SettingsService`.
- `src/main/java/com/miniide/settings/KeyMigrationService.java` - role: migrate key storage format; owns: conversion flows; key symbols: migration routines; touchpoints: `SettingsService`.
- `src/main/java/com/miniide/settings/SecuritySettings.java` - role: security config model; owns: mode/lock status; key symbols: fields/getters; touchpoints: `SettingsService`, `SettingsController`.

### Controllers (HTTP API)
Start here for endpoints; each routes to a service/store.
- `src/main/java/com/miniide/controllers/AgentController.java` - role: agent + endpoint API; owns: agents + role settings; key symbols: routes `GET /api/agents`, `POST /api/agents`, `PUT /api/agents/{id}`, `GET/PUT /api/agent-endpoints`, `GET/PUT /api/agents/role-settings`; touchpoints: `AgentRegistry`, `AgentEndpointRegistry`, `roleSettingsApi` in `src/main/resources/public/api.js`.
- `src/main/java/com/miniide/controllers/AudioController.java` - role: audio asset list; owns: ambient tracks list; key symbols: `GET /api/audio`; touchpoints: `src/main/resources/public/app/workbench.js`.
- `src/main/java/com/miniide/controllers/ChatController.java` - role: AI chat API; owns: agent/provider routing + memory escalation; key symbols: `POST /api/ai/chat`; touchpoints: `ProviderChatService`, `MemoryService`, `src/main/resources/public/api.js`.
- `src/main/java/com/miniide/controllers/CreditController.java` - role: credits API; owns: profiles + events; key symbols: `GET /api/credits/profiles`, `POST /api/credits`; touchpoints: `CreditStore`, `src/main/resources/public/api.js`.
- `src/main/java/com/miniide/controllers/DashboardController.java` - role: widget layout API; owns: dashboard layout; key symbols: `GET/POST /api/dashboard/layout`; touchpoints: `DashboardLayoutStore`, `src/main/resources/public/app/widgets.js`.
- `src/main/java/com/miniide/controllers/FileController.java` - role: file tree + editor file ops; owns: file CRUD/search; key symbols: `GET /api/tree`, `GET/PUT/POST/DELETE /api/file`, `GET /api/search`; touchpoints: `WorkspaceService`, `src/main/resources/public/app/editor.js`.
- `src/main/java/com/miniide/controllers/IssueController.java` - role: issue API; owns: issues + comments + governance; key symbols: `GET /api/issues`, `POST /api/issues/{id}/comments`; touchpoints: `IssueMemoryService`, `NotificationStore`, `CreditStore`, `src/main/resources/public/api.js`.
- `src/main/java/com/miniide/controllers/MemoryController.java` - role: memory API; owns: memory items/versions/decay; key symbols: `POST /api/memory`, `GET /api/memory/{id}`, `POST /api/memory/decay`; touchpoints: `MemoryService`, `MemoryDecayScheduler`, `src/main/resources/public/api.js`.
- `src/main/java/com/miniide/controllers/NotificationController.java` - role: notifications API; owns: notification CRUD + read state; key symbols: `GET /api/notifications`, `POST /api/notifications/mark-all-read`; touchpoints: `NotificationStore`, `src/main/resources/public/notifications.js`.
- `src/main/java/com/miniide/controllers/PatchController.java` - role: patch proposals API; owns: create/apply/reject/audit; key symbols: `POST /api/patches`, `POST /api/patches/{id}/apply`, `GET /api/patches/{id}/audit`; touchpoints: `PatchService`, `src/main/resources/public/app.js`.
- `src/main/java/com/miniide/controllers/PreparationController.java` - role: project preparation API; owns: ingest/reindex/canon review; key symbols: `POST /api/preparation/ingest`, `POST /api/preparation/reindex/scene`; touchpoints: `ProjectPreparationService`, `src/main/resources/public/app.js`.
- `src/main/java/com/miniide/controllers/PromptController.java` - role: prompt tools API; owns: prompt registry CRUD; key symbols: `GET/POST/PUT/DELETE /api/prompts`; touchpoints: `PromptRegistry`, `src/main/resources/public/app.js`.
- `src/main/java/com/miniide/controllers/SettingsController.java` - role: keys/security/providers API; owns: key storage + model lists; key symbols: `GET/PUT /api/settings/security`, `GET /api/providers/models`; touchpoints: `SettingsService`, `ProviderModelsService`, `src/main/resources/public/app.js`.
- `src/main/java/com/miniide/controllers/TtsController.java` - role: TTS config + test; owns: voice list/settings/test; key symbols: `GET /api/tts/voices`, `POST /api/tts/test`; touchpoints: `data/voices`, `src/main/resources/public/app.js`.
- `src/main/java/com/miniide/controllers/VersioningController.java` - role: versioning API; owns: snapshots/history/changes; key symbols: `GET /api/versioning/status`, `POST /api/versioning/publish`; touchpoints: `src/main/resources/public/app/versioning.js`.
- `src/main/java/com/miniide/controllers/WorkspaceController.java` - role: workspace/project selection; owns: project switching + metadata; key symbols: `POST /api/workspace/select`, `GET/PUT /api/workspace/metadata`; touchpoints: `WorkspaceService`, `src/main/resources/public/app.js`.

### Models (Data Shapes)
Owns: JSON payload shapes for API + persistence. Start here when adding fields.
- Agent domain: `src/main/java/com/miniide/models/Agent.java`, `AgentEndpointConfig.java`, `AgentCapabilityProfile.java`, `AgentMemoryProfile.java`, `AgentPersonalityConfig.java`, `AgentToolCapability.java`, `AgentModelRecord.java`, `AgentPerformanceStats.java`, `AgentCreditProfile.java`, `AgentAutoActionConfig.java`, `AgentsFile.java`, `AgentEndpointsFile.java`, `RoleFreedomSettings.java`.
  - role: agent roster + capabilities + settings; owns: agents JSON shape; key symbols: model fields; touchpoints: `AgentRegistry`, `AgentController`, `src/main/resources/public/app/agents.js`.
- Issues + comments: `src/main/java/com/miniide/models/Issue.java`, `Comment.java`.
  - role: issue + comment schema; owns: issue JSON; key symbols: fields + enums; touchpoints: `IssueMemoryService`, `IssueController`, `src/main/resources/public/api.js`.
- Memory/librarian: `src/main/java/com/miniide/models/MemoryItem.java`, `MemoryVersion.java`, `R5Event.java`.
  - role: memory items + rep levels + evidence; owns: memory JSON; key symbols: fields; touchpoints: `MemoryService`, `MemoryController`.
- Patch review: `src/main/java/com/miniide/models/PatchProposal.java`, `PatchFileChange.java`, `PatchAuditEntry.java`, `PatchProvenance.java`.
  - role: patch proposal + diff/audit schema; owns: patch JSON; key symbols: fields; touchpoints: `PatchService`, `PatchController`, patch review UI.
- Notifications: `src/main/java/com/miniide/models/Notification.java`.
  - role: notification schema; owns: notification JSON; key symbols: fields + enums; touchpoints: `NotificationStore`, `NotificationController`, `src/main/resources/public/notifications.js`.
- Versioning/history: `src/main/java/com/miniide/models/Snapshot.java`, `SnapshotFile.java`, `TextEdit.java`.
  - role: snapshot + diff data; owns: snapshot JSON; key symbols: fields; touchpoints: `VersioningController`, `src/main/resources/public/app/history.js`.
- Story/compendium prep: `src/main/java/com/miniide/models/StoryManifest.java`, `StoryRegistry.java`, `StoryScene.java`, `CanonManifest.java`, `CanonCard.java`, `IngestManifest.java`, `IngestEvidence.java`, `IngestPointer.java`, `IngestSourceContext.java`, `IngestIgnoredInput.java`, `IngestStats.java`, `HookMatch.java`, `SceneSegment.java`.
  - role: prepared mode + ingest artifacts; owns: canon/prep JSON; key symbols: fields; touchpoints: `ProjectPreparationService`, `PreparedWorkspaceService`, `PreparationController`.
- Workspace/search: `src/main/java/com/miniide/models/WorkspaceMetadata.java`, `FileNode.java`, `SearchResult.java`.
  - role: project metadata + file tree/search; owns: workspace JSON; key symbols: fields; touchpoints: `WorkspaceService`, `FileController`.

## Frontend (Static UI) - `src/main/resources/public`
Owns: browser UI, state, and module wiring. Start here for UI flow: `src/main/resources/public/app.js`.

### App Shell + Global State
- `src/main/resources/public/index.html` - role: UI shell; owns: layout + script includes; key symbols: DOM IDs; touchpoints: `src/main/resources/public/app/dom.js`, `src/main/resources/public/styles.css`.
- `src/main/resources/public/styles.css` - role: global styles; owns: UI look/feel; key symbols: CSS variables/selectors; touchpoints: HTML + all modules.
- `src/main/resources/public/state.js` - role: client state + role templates; owns: `window.state`, `ROLE_TEMPLATES`, `DEFAULT_ROLE_CHARTERS`; key symbols: `canonicalizeRole`; touchpoints: `src/main/resources/public/app.js`, `src/main/resources/public/api.js`.
- `src/main/resources/public/api.js` - role: API wrapper layer; owns: `window.*Api` objects; key symbols: `issueApi`, `agentApi`, `patchApi`, `memoryApi`, `notificationsApi`, `workspaceApi`, `versioningApi`; touchpoints: all UI modules.
- `src/main/resources/public/modals.js` - role: modal framework; owns: modal rendering + escape helpers; key symbols: `showModal`, `showConfigurableModal`, `escapeHtml`; touchpoints: `src/main/resources/public/app.js`, `src/main/resources/public/app/editor.js`.
- `src/main/resources/public/notifications.js` - role: notification store + toasts; owns: in-memory notification state; key symbols: `createNotificationStore`, `push/info/success/warning/error`; touchpoints: `NotificationController`, `src/main/resources/public/app/workbench.js`.
- `src/main/resources/public/app/dom.js` - role: DOM cache; owns: `window.elements` references; key symbols: elements map; touchpoints: all UI modules.

### UI Modules (By Feature)
- `src/main/resources/public/app/boot.js` - role: app init and boot flow; owns: startup sequence; key symbols: `init`; touchpoints: `src/main/resources/public/app.js`, `src/main/resources/public/api.js`.
- `src/main/resources/public/app.js` - role: main UI controller; owns: view mode, console logs, workspace load, major event wiring; key symbols: `log`, `loadWorkspaceInfo`, `showConfirmDialog`; touchpoints: `src/main/resources/public/api.js`, `src/main/resources/public/app/agents.js`, `src/main/resources/public/app/editor.js`.
- `src/main/resources/public/app/editor.js` - role: editor view + file tree; owns: Monaco tabs, explorer scope, search; key symbols: `renderExplorerTree`, `openFile`, `saveCurrentFile`; touchpoints: `FileController`, `src/main/resources/public/app/history.js`, `src/main/resources/public/app/versioning.js`.
- `src/main/resources/public/app/history.js` - role: history viewer UI; owns: snapshots diff viewer; key symbols: `showHistoryViewer`, `fetchFileHistory`; touchpoints: `VersioningController`, `src/main/resources/public/app/editor.js`.
- `src/main/resources/public/app/versioning.js` - role: versioning panel UI; owns: changes list + publish; key symbols: `refreshChanges`, `loadSnapshots`; touchpoints: `VersioningController`, `src/main/resources/public/app/history.js`.
- `src/main/resources/public/app/workbench.js` - role: workbench panels; owns: issue board + newsfeed; key symbols: `renderIssueBoard`, `createIssueCard`, `renderWorkbenchNewsfeed`; touchpoints: `IssueController`, `NotificationController`, `src/main/resources/public/app/widgets.js`.
- `src/main/resources/public/app/widgets.js` - role: dashboard widgets; owns: layout + widget registry; key symbols: `renderWidgetDashboard`, `registerBuiltInWidgets`, `tryPushWidgets`; touchpoints: `DashboardController`, `src/main/resources/public/app/workbench.js`.
- `src/main/resources/public/app/agents.js` - role: agent roster UI; owns: agent cards + activity state; key symbols: `setAgentActivityState`, `enqueueAgentTurn`, `isAssistantAgent`; touchpoints: `AgentController`, `AgentTurnGate`, `src/main/resources/public/app.js`.
- `src/main/resources/public/app/util.js` - role: shared UI helpers; owns: formatting + markdown renderer; key symbols: `renderSimpleMarkdown`, `buildChatPrompt`, `extractStopHook`; touchpoints: `app.js`, chat UIs.

## Scripts
Owns: local tooling and manual utilities. Start here for local setup helpers.
- `scripts/setup-piper.ps1` - role: TTS setup helper; owns: Piper installation steps; key symbols: script flow; touchpoints: `src/main/java/com/miniide/controllers/TtsController.java`.
- `scripts/start-piper.bat` - role: start Piper TTS; owns: local TTS server launch; key symbols: script flow; touchpoints: TTS endpoints.
- `scripts/manual/circuit_breaker_test.ps1` - role: manual test; owns: circuit breaker validation flow; key symbols: script flow; touchpoints: `CircuitBreakerValidator`.

## Runtime Data (Tracked)
Owns: default or sample data that runtime uses or reads.
- `data/patches.json` - role: patch proposal storage; owns: patch entries; key symbols: patch records; touchpoints: `PatchService`.
- `data/voices/*.onnx` + `data/voices/*.onnx.json` - role: TTS voice models; owns: local voice assets; key symbols: model files; touchpoints: `TtsController`.

## Project Storage (Runtime)
Owns: per-project data and settings.
- `workspace/` - role: project roots; owns: per-project files and `.control-room` data; key symbols: project tree; touchpoints: `WorkspaceService`, `ProjectContext`.

## Assets + Vendor (Collapsed)
Owns: icons, audio, and third-party libs. Usually not a starting point.
- `assets/` - heroicons SVG set (source assets).
- `src/main/resources/public/assets/` - UI icon assets (lucide/heroicons).
- `src/main/resources/public/audio/` - ambient audio tracks.
- `node_modules/` - third-party frontend deps.
- `gradle/wrapper/` - Gradle wrapper JAR and config.
