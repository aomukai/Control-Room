# Control Room

Control Room is a desktop writing IDE with an agent workbench. It combines a fast, Monaco-based editor with a strategic layer for planning, issues, and collaboration. The app is built as a Java + HTML desktop application.

This is a pre-release developer build; UI elements and flows may change.

## What It Is

Control Room separates writing from orchestration:

- Editor mode: hands-on writing, tabs, search, and patch review.
- Workbench mode: agents, issues, and a real-time newsfeed.
- Issues and memory: project knowledge captured as issues with comments and notifications.

## Current Capabilities

- Monaco editor with tabs and dirty-state tracking.
- Workspace tree: create, rename, move, delete files and folders.
- In-file search and workspace search.
- Patch review flow (diff modal) designed for AI-generated edits.
- Workbench with agent sidebar, issue board MVP, and newsfeed.
- Agent onboarding wizard, settings modal, and role settings modal.
- Agent registry and profile system (avatars, roles, personalities, instructions, endpoints).
- Agent availability lights and retired-model "nursing home" modal.
- Sortable agent roster with persisted ordering.
- Notification system (toast, status bar, notification center).
- JSON persistence for issues and notifications.
- Workspace switching with per-workspace data under `workspace/<name>`.

## Run From Source

Requirements:

- Java 17+ JDK
- Gradle (or use the included Gradle wrapper)

Run:

```bash
git clone <repository-url>
cd control-room
./gradlew run        # Linux / macOS
gradlew.bat run      # Windows
```

On Windows you can also double-click `run.bat`.

## Workspace And Data Locations

Default workspace:

- Windows: `%USERPROFILE%\Documents\Control-Room\workspace`
- macOS: `~/Documents/Control-Room/workspace`
- Linux: `~/Control-Room/workspace`

Repository-only sample content:

```
sample-workspace/
```

Logs:

- Windows: `%APPDATA%\Control-Room\logs\control-room.log`
- macOS: `~/Library/Logs/Control-Room/control-room.log`
- Linux: `~/.local/share/Control-Room/logs/control-room.log`

## Command-Line Options

```bash
--workspace <path>   # use a custom workspace directory
--port <number>      # specify server port
--dev                # enable development mode logging
```

Example:

```bash
./gradlew run --args="--workspace ./my-project --port 9000 --dev"
```

## Building Distributions

Prerequisites:

1. Java 17+ JDK with `jpackage`
2. Windows only (optional): WiX Toolset 3.x for MSI generation

Check environment:

```bash
./gradlew checkJpackage
```

Build commands:

```bash
# Recommended (Windows)
./gradlew distWindowsFrictionless

# Portable ZIP (bundled runtime)
./gradlew dist

# Individual installers
./gradlew jpackageWinExe
./gradlew jpackageWinMsi   # requires WiX
./gradlew jpackageMacDmg
./gradlew jpackageLinuxDeb

# App image (no installer)
./gradlew jpackageAppImage
```

Output locations:

| Artifact     | Location                                                  |
| ------------ | --------------------------------------------------------- |
| Portable ZIP | `build/distributions/Control-Room-<version>-portable.zip` |
| Windows EXE  | `build/installer/Control-Room-<version>.exe`              |
| Windows MSI  | `build/installer/Control-Room-<version>.msi`              |
| App Image    | `build/app-image/Control-Room/`                           |

## API Overview

Issues:

- GET `/api/issues`
- GET `/api/issues/{id}`
- POST `/api/issues`
- PUT `/api/issues/{id}`
- DELETE `/api/issues/{id}`
- POST `/api/issues/{id}/comments`

Agents:

- GET `/api/agents`
- GET `/api/agents/all`
- POST `/api/agents`
- GET `/api/agents/{id}`
- PUT `/api/agents/{id}`
- PUT `/api/agents/{id}/status`
- PUT `/api/agents/order`
- POST `/api/agents/import`
- GET `/api/agent-endpoints`
- GET `/api/agent-endpoints/{id}`
- PUT `/api/agent-endpoints/{id}`
- GET `/api/agents/role-settings`
- GET `/api/agents/role-settings/{role}`
- PUT `/api/agents/role-settings/{role}`

Chat:

- POST `/api/ai/chat`

Settings and Providers:

- GET `/api/settings/security`
- PUT `/api/settings/security`
- POST `/api/settings/security/unlock`
- POST `/api/settings/security/lock`
- GET `/api/settings/keys`
- POST `/api/settings/keys`
- DELETE `/api/settings/keys/{provider}/{id}`
- GET `/api/providers/models`

Workspace:

- GET `/api/workspace/info`
- POST `/api/workspace/select`
- POST `/api/workspace/open`
- POST `/api/workspace/terminal`

Files:

- GET `/api/tree`
- GET `/api/file`
- PUT `/api/file`
- POST `/api/file`
- DELETE `/api/file`
- POST `/api/rename`
- POST `/api/duplicate`
- GET `/api/search`
- GET `/api/segments`
- POST `/api/file/reveal`
- POST `/api/file/open-folder`

Notifications:

- GET `/api/notifications`
- GET `/api/notifications/unread-count`
- GET `/api/notifications/{id}`
- POST `/api/notifications`
- PUT `/api/notifications/{id}`
- DELETE `/api/notifications/{id}`
- POST `/api/notifications/mark-all-read`
- POST `/api/notifications/clear`

## Security Notes

- Designed for local use only.
- No authentication.
- Full access within the workspace directory.
- Not intended to be exposed to the internet.

## License

MIT

## Credits

Control Room uses 
[Heroicons](https://heroicons.com/) by Tailwind Labs,
[Lucide] (https://lucide.dev/) by Lucide Contributors,
[Piper] (https://github.com/rhasspy/piper)
