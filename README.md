# Mini-IDE

A lightweight, Cursor-like writing IDE built as a Java + HTML desktop application. Designed for creative writers who want a distraction-free environment with AI assistance.

## Features

- **Monaco Editor**: Full-featured code/text editor with syntax highlighting
- **File Management**: Browse, create, rename, and delete files/folders
- **Tab System**: Open multiple files with dirty state tracking
- **Resizable Panels**: Adjustable layout with persistent sizes
- **Console**: Real-time logging of actions and API responses
- **Search**: Full-text search across workspace files
- **AI Chat**: Chat interface for writing assistance (stub implementation)
- **AI Actions**: Preview, apply, or reject proposed changes

## Installation

### For End Users (No Java Required)

**Windows Installer:**
- Download `Mini-IDE-1.0.0.exe` from the releases page
- Double-click to install
- Creates Start Menu and Desktop shortcuts
- Includes bundled Java runtime - no separate installation needed

**Portable Version (Windows/macOS/Linux):**
- Download `Mini-IDE-1.0.0-portable.zip`
- Extract anywhere
- Run:
  - **Windows**: Double-click `Start-Mini-IDE.bat`
  - **macOS**: Double-click `Mini-IDE.app` or run `./start.sh`
  - **Linux**: Run `./start.sh` or `./Mini-IDE/bin/Mini-IDE`
- Includes bundled Java runtime - no separate installation needed

### For Developers

Requirements:
- Java 17+ JDK
- Gradle (or use included wrapper)

```bash
# Clone and run
git clone <repository>
cd mini-ide
./gradlew run        # Linux/macOS
gradlew.bat run      # Windows
```

Or double-click `run.bat` on Windows for development mode.

## Usage

### Workspace Location

By default, Mini-IDE stores your files in:
- **Windows**: `%USERPROFILE%\Documents\Mini-IDE\workspace`
- **macOS**: `~/Documents/Mini-IDE/workspace`
- **Linux**: `~/Mini-IDE/workspace`

On first run, sample files are created to help you get started.

### Keyboard Shortcuts

- **Ctrl+S**: Save current file

### Log Files

Logs are stored in:
- **Windows**: `%APPDATA%\Mini-IDE\logs\mini-ide.log`
- **macOS**: `~/Library/Logs/Mini-IDE/mini-ide.log`
- **Linux**: `~/.local/share/Mini-IDE/logs/mini-ide.log`

## Building Distributions

### Prerequisites

1. **Java 17+ JDK** with jpackage (included in JDK 17+)
2. **WiX Toolset 3.0+** (Windows only, for MSI creation - EXE works without it)
   - Download from: https://wixtoolset.org/

### Check Requirements

```bash
./gradlew checkJpackage
```

### Build Commands

```bash
# === Recommended for Windows ===
./gradlew distWindowsFrictionless
# Creates:
#   - build/distributions/Mini-IDE-1.0.0-portable.zip (bundled runtime)
#   - build/installer/Mini-IDE-1.0.0.exe (installer)

# === Portable ZIP with bundled runtime ===
./gradlew dist
# Creates: build/distributions/Mini-IDE-1.0.0-portable.zip

# === Individual Installers ===
./gradlew jpackageWinExe     # Windows EXE installer
./gradlew jpackageWinMsi     # Windows MSI installer (requires WiX)
./gradlew jpackageMacDmg     # macOS DMG
./gradlew jpackageLinuxDeb   # Linux DEB package

# === App Image (bundled runtime, no installer) ===
./gradlew jpackageAppImage
```

### Output Locations

| Artifact | Location |
|----------|----------|
| Portable ZIP | `build/distributions/Mini-IDE-1.0.0-portable.zip` |
| Windows EXE | `build/installer/Mini-IDE-1.0.0.exe` |
| Windows MSI | `build/installer/Mini-IDE-1.0.0.msi` |
| App Image | `build/app-image/Mini-IDE/` |

### What's in the Portable ZIP?

```
Mini-IDE-1.0.0-portable/
├── Start-Mini-IDE.bat    # Windows launcher (double-click)
├── start.sh              # macOS/Linux launcher
├── README.txt            # Quick start guide
└── Mini-IDE/             # App with bundled Java runtime
    ├── Mini-IDE.exe      # Main executable (Windows)
    └── runtime/          # Bundled JRE (no system Java needed)
```

## Developer Command Line Options

```bash
# Specify custom workspace
./gradlew run --args="--workspace /path/to/workspace"

# Specify custom port
./gradlew run --args="--port 3000"

# Development mode (console logging enabled)
./gradlew run --args="--dev"

# Combine options
./gradlew run --args="--workspace ./my-project --port 9000 --dev"
```

## Project Structure

```
mini-ide/
├── build.gradle                 # Build configuration with jpackage
├── settings.gradle
├── run.bat / run.sh            # Development launch scripts
├── README.md
├── src/main/
│   ├── java/com/miniide/
│   │   ├── Main.java           # Application entry point
│   │   ├── AppConfig.java      # Configuration handling
│   │   ├── AppLogger.java      # Logging utility
│   │   ├── BrowserLauncher.java # Cross-platform browser opening
│   │   ├── FileService.java    # File operations
│   │   └── models/
│   │       ├── FileNode.java
│   │       └── SearchResult.java
│   └── resources/
│       ├── public/             # Frontend files
│       │   ├── index.html
│       │   ├── styles.css
│       │   └── app.js
│       └── icons/              # Application icons
│           └── ICON-README.md
└── gradle/wrapper/             # Gradle wrapper
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/tree` | Get workspace file tree |
| GET | `/api/file?path=...` | Read file contents |
| PUT | `/api/file?path=...` | Save file contents |
| POST | `/api/file` | Create file/folder |
| DELETE | `/api/file?path=...` | Delete file/folder |
| POST | `/api/rename` | Rename/move file |
| GET | `/api/search?q=...` | Search files |
| POST | `/api/ai/chat` | AI chat (stub) |

## Tech Stack

- **Backend**: Java 17 + Javalin 5
- **JSON**: Jackson
- **Build**: Gradle with jpackage
- **Frontend**: Vanilla HTML/CSS/JS
- **Editor**: Monaco Editor (via CDN)
- **Layout**: Split.js (via CDN)

## Customizing Icons

To add custom application icons:

1. Create icon files in `src/main/resources/icons/`:
   - `app-icon.ico` (Windows, 256x256 multi-resolution)
   - `app-icon.icns` (macOS, 512x512 or 1024x1024)
   - `app-icon.png` (Linux, 256x256)

2. See `src/main/resources/icons/ICON-README.md` for detailed instructions.

## Security Notes

This application is designed for **local use only**:
- No authentication
- Full filesystem access within workspace
- Should not be exposed to the internet

Path traversal is prevented by validating all paths stay within the workspace root.

## Troubleshooting

### Port Already in Use

Mini-IDE automatically finds an available port if 8080 is busy. Check the console output or log file for the actual URL.

### Browser Doesn't Open

The browser should open automatically. If not:
1. Check the console/log for the URL
2. Open manually: http://localhost:8080/

### Java Not Found (Developer Mode)

For development, ensure Java 17+ JDK is installed:
```bash
java -version  # Should show 17+
```

Note: End users don't need Java - it's bundled in the portable ZIP and installers.

### jpackage Fails

1. Run `./gradlew checkJpackage` to diagnose
2. Windows EXE works without extra tools
3. Windows MSI requires WiX Toolset
4. Ensure you're using JDK (not JRE)

## License

MIT
