# Control Room

Control Room is a desktop writing IDE with an agent workbench. It combines a fast, Monaco-based editor with a strategic layer for planning, issues, and collaboration. The app is built as a Java + HTML desktop application.

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
- Agent registry and profile system (avatars, roles, personalities, instructions).
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
- POST `/api/agents`
- GET `/api/agents/{id}`
- PUT `/api/agents/{id}`
- POST `/api/agents/import`

Notifications:

- GET `/api/notifications`
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

Uses [Heroicons](https://heroicons.com/) by Tailwind Labs.
