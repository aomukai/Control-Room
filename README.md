# Control Room

A lightweight, Cursor-like writing IDE built as a Java + HTML desktop application. Designed for creative writers who want a distraction-free environment with AI assistance.

---

## Features

* **Monaco Editor** – Full-featured text editor with syntax highlighting
* **File Management** – Browse, create, rename, and delete files/folders
* **Tab System** – Open multiple files with dirty state tracking
* **Resizable Panels** – Adjustable layout with persistent sizes
* **Console** – Real-time logging of actions and backend messages
* **Search** – Full-text search across workspace files
* **AI Chat (stub)** – Chat interface placeholder for future AI integration
* **AI Actions (stub)** – Preview, apply, or reject proposed changes

---

## Installation

### For End Users (No Java Required)

#### Windows Installer

* Download the latest `Control-Room-<version>.exe` from the Releases page
* Double-click to install
* Creates Start Menu and Desktop shortcuts
* Includes a bundled Java runtime (no separate Java installation required)

#### Portable Version (Windows / macOS / Linux)

* Download `Control-Room-<version>-portable.zip`
* Extract anywhere
* Run:

  * **Windows**: double-click the included launcher (e.g. `Start-Control-Room.bat`, if present)
  * **macOS / Linux**: run `./start.sh`

> Portable means *no installer and bundled runtime*. User data is still stored in your Documents folder by default.

---

## For Developers

### Requirements

* Java 17+ JDK
* Gradle (or use the included Gradle wrapper)

### Run from Source

```bash
git clone <repository-url>
cd control-room
./gradlew run        # Linux / macOS
gradlew.bat run      # Windows
```

On Windows, you can also double-click `run.bat`.

---

## Usage

### Workspace Location

By default, Control Room stores user files in:

* **Windows**: `%USERPROFILE%\\Documents\\Control-Room\\workspace`
* **macOS**: `~/Documents/Control-Room/workspace`
* **Linux**: `~/Control-Room/workspace`

The workspace is created automatically on first run.

### Bundled Sample Workspace (Repository Only)

This repository contains example content under:

```
sample-workspace/
```

This folder is **not used directly by the application** at runtime. It exists solely as:

* reference content
* demo files
* a starting point for developers and contributors

The actual workspace used by the application always lives in the user’s Documents folder unless explicitly overridden via command-line arguments.

---

### Keyboard Shortcuts

* **Ctrl + S** – Save current file

---

### Log Files

Logs are written to:

* **Windows**: `%APPDATA%\\Control-Room\\logs\\control-room.log`
* **macOS**: `~/Library/Logs/Control-Room/control-room.log`
* **Linux**: `~/.local/share/Control-Room/logs/control-room.log`

---

## Building Distributions

### Prerequisites

1. Java 17+ JDK with `jpackage` (included in standard JDK distributions)
2. **Windows only (optional)**: WiX Toolset 3.x for MSI generation

### Check Environment

```bash
./gradlew checkJpackage
```

### Build Commands

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

### Output Locations

| Artifact     | Location                                                  |
| ------------ | --------------------------------------------------------- |
| Portable ZIP | `build/distributions/Control-Room-<version>-portable.zip` |
| Windows EXE  | `build/installer/Control-Room-<version>.exe`              |
| Windows MSI  | `build/installer/Control-Room-<version>.msi`              |
| App Image    | `build/app-image/Control-Room/`                           |

---

## Command-Line Options (Developer Mode)

```bash
--workspace <path>   # use a custom workspace directory
--port <number>     # specify server port
--dev               # enable development mode logging
```

Example:

```bash
./gradlew run --args="--workspace ./my-project --port 9000 --dev"
```

---

## Project Structure

```
control-room/
├── build.gradle
├── settings.gradle
├── run.bat / run.sh
├── README.md
├── sample-workspace/
├── src/main/
│   ├── java/com/miniide/
│   │   ├── Main.java
│   │   ├── AppConfig.java
│   │   ├── AppLogger.java
│   │   ├── BrowserLauncher.java
│   │   ├── FileService.java
│   │   └── models/
│   │       ├── FileNode.java
│   │       └── SearchResult.java
│   └── resources/
│       ├── public/
│       │   ├── index.html
│       │   ├── styles.css
│       │   └── app.js
│       └── icons/
│           └── ICON-README.md
└── gradle/wrapper/
```

> Note: Java package names still use `com.miniide` internally. This is cosmetic and may be renamed later.

---

## API Endpoints

| Method | Endpoint             | Description             |
| ------ | -------------------- | ----------------------- |
| GET    | `/api/tree`          | Get workspace file tree |
| GET    | `/api/file?path=...` | Read file               |
| PUT    | `/api/file?path=...` | Save file               |
| POST   | `/api/file`          | Create file or folder   |
| DELETE | `/api/file?path=...` | Delete file or folder   |
| POST   | `/api/rename`        | Rename or move file     |
| GET    | `/api/search?q=...`  | Search workspace        |
| POST   | `/api/ai/chat`       | AI chat (stub)          |

---

## Security Notes

* Designed for **local use only**
* No authentication
* Full access within the workspace directory
* Not intended to be exposed to the internet

All paths are validated to prevent directory traversal outside the workspace root.

---

## Troubleshooting

### Port Already in Use

If port 8080 is unavailable, Control Room automatically selects a free port. Check the console output for the actual URL.

### Browser Does Not Open

* Check the console or log file for the URL
* Open manually in a browser (e.g. `http://localhost:8080/`)

### Java Errors (Developer Mode)

Ensure Java 17+ is installed:

```bash
java -version
```

End users do **not** need Java; it is bundled with releases.

---

## License

MIT
