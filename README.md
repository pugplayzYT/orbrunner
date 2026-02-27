> [!CAUTION]
> ## ⛔ External Pull Requests Are Not Accepted
> OrbRunner is a solo-developed project. **Pull requests from outside the development team are automatically closed** — no exceptions.
>
> Forking this repository to create your own version or competing project is also **not permitted** under the license.
>
> **If you have a bug report or feature suggestion, please use Issues:**
> - 🐛 [Report a bug](../../issues/new?template=bug_report.yml)
> - 💡 [Suggest a feature](../../issues/new?template=feature_suggestion.yml)

# OrbRunner

A 3D first-person maze escape game built with LWJGL (OpenGL/Java). Navigate procedurally generated rooms, collect keys, and find the exit. The project ships as three components: the game itself, a JavaFX launcher for version management, and a Flask distribution server.

---

## Architecture

```
orbrunner/
├── src/                        # Game (LWJGL / OpenGL)
├── launcher/                   # Launcher (JavaFX)
├── server/                     # Distribution server (Flask / Python)
├── build.gradle.kts            # Game build config
├── settings.gradle.kts         # Root Gradle settings (includes launcher)
└── .agent/workflows/           # Agent workflow docs
```

### Game
First-person 3D gameplay rendered entirely in OpenGL — no Swing. All menus, HUD, and pause screens are drawn in-engine.

- **Procedural generation** — seed-based room layouts (standard, courtyard, bedroom) with tunnels between rooms
- **Runs system** — save/resume multi-session runs stored in `~/.orbCollectorGame/runs/`
- **Key collection** — find keys scattered across rooms (especially on bedroom tables) to unlock the escape door
- **HUD** — minimap, key counter, hot/cold proximity indicator, popup messages
- **Audio** — looping ambient background + horror sound effects, volume controlled in real time
- **Settings** — sensitivity, FOV, fog density, master volume, invert Y — persisted to `~/.orbCollectorGame/settings.dat`

### Launcher
Dark-themed JavaFX desktop app for installing, updating, and launching the game.

- Lists available versions from the server, downloads and caches JARs locally (`~/.orbrunner/versions/`)
- Shows per-version patch notes with formatted markdown rendering
- Settings panel syncs live with the running game via a shared settings file and `WatchService`
- Server URL is configurable in `~/.orbrunner/launcher.json`

### Server
Minimal Flask HTTP API for distributing game builds.

| Endpoint | Description |
|---|---|
| `GET /api/versions` | List all available versions |
| `GET /api/latest` | Get latest version metadata |
| `GET /api/download/<version>` | Download a version JAR |
| `GET /api/changelog/<version>` | Get raw markdown changelog |
| `POST /api/upload` | Upload a new build (requires auth token) |

---

## Quick Start

### Running the game directly
```bash
./gradlew run
```

### Running the launcher
```bash
./gradlew :launcher:run
```
The launcher connects to `http://localhost:5000` by default.

### Starting the server
```bash
cd server
pip install -r requirements.txt
python app.py
```

The server listens on port 5000. Set `ORBRUNNER_AUTH_TOKEN` to change the upload auth token from the default `changeme`.

---

## Building

```bash
# Build game fat JAR → build/libs/orbrunner-{version}.jar
./gradlew jar

# Build launcher fat JAR → launcher/build/libs/launcher-1.0.0.jar
./gradlew :launcher:jar
```

The game version is read automatically from the last line of `src/main/resources/update_logs/index.txt`.

### Uploading a build to the server
```bash
python server/upload.py --server http://localhost:5000 --token changeme
# For production:
python server/upload.py --server https://your-server.com --token YOUR_TOKEN
```

---

## Project Structure

### Game (`src/main/java/ohio/pugnetgames/chad/`)

| Package | Key Files | Description |
|---|---|---|
| `game/` | `GamePanel.java` | Main game loop and state machine (`MAIN_MENU → PLAYING → PAUSED → GAME_OVER`) |
| `game/` | `Player.java` | First-person camera, movement, physics, collision |
| `game/` | `WorldLoader.java` | Procedural room/tunnel generation, furniture placement |
| `game/` | `InGameUI.java` | All menus and dialogs rendered in OpenGL |
| `game/` | `HudRenderer.java` | 2D overlays — minimap, objectives, key count |
| `game/` | `SoundManager.java` | Audio playback and volume control |
| `core/` | `RunManager.java` | Save/load runs across sessions |
| `core/` | `ScoreManager.java` | Best-time persistence (SHA-256 validated) |
| `core/` | `SettingsManager.java` | Game settings file I/O |
| `core/` | `BuildManager.java` | Feature flag loading from `build.properties` |

### Launcher (`launcher/src/main/java/ohio/pugnetgames/chad/launcher/`)

| File | Description |
|---|---|
| `LauncherApp.java` | JavaFX entry point, loads CSS stylesheet |
| `LauncherUI.java` | Full UI — main screen, settings overlay, changelog overlay, progress overlay |
| `GameManager.java` | HTTP calls to server, local JAR cache management |
| `DownloadTask.java` | Threaded download with progress binding |
| `SettingsManager.java` | Launcher-side settings sync (WatchService for live reload) |
| `MarkdownRenderer.java` | Parses update log markdown into styled JavaFX nodes |

---

## Configuration

### Feature Flags (`src/main/resources/build.properties`)
```properties
feature.freecam.enabled=true
feature.adminpanel.enabled=true
feature.debuglines.enabled=true
feature.allbedrooms.enabled=false
feature.allcourtyards.enabled=false
```

### Game Settings (`~/.orbCollectorGame/settings.dat`)
Written by both the launcher and the game. Changes from either side apply immediately while the game is running.
```
sensitivity=0.10
fieldOfView=60.0
fogDensity=0.07
masterVolume=1.0
invertY=false
```

### Launcher Config (`~/.orbrunner/launcher.json`)
```json
{
  "serverUrl": "http://localhost:5000",
  "selectedVersion": null,
  "lastPlayedVersion": null
}
```

---

## Update Logs

Changelogs live in `src/main/resources/update_logs/` and are displayed in both the in-game changelog panel and the launcher's "Patch Notes" viewer. Each file is plain markdown named after its version (e.g. `v3.2.md`).

---

## Feedback & Suggestions

OrbRunner is developed solely by [pugplayzYT](https://github.com/pugplayzYT). Pull requests are not accepted from outside the development team and will be automatically closed.

If you want to help shape the game, open an Issue:

- 🐛 **[Report a bug](../../issues/new?template=bug_report.yml)** — something broken, crashing, or behaving wrong
- 💡 **[Suggest a feature](../../issues/new?template=feature_suggestion.yml)** — an idea you'd like to see in the game

---

## Dependencies

### Game
- [LWJGL 3.3.3](https://www.lwjgl.org/) — OpenGL, GLFW, STB, ASSIMP bindings
- [JOML](https://github.com/JOML-CI/JOML) — Java OpenGL Math Library
- [MP3SPI / JLayer](https://www.javazoom.net/) — MP3 audio playback
- [Gson 2.10.1](https://github.com/google/gson) — JSON serialization

### Launcher
- [JavaFX 17.0.2](https://openjfx.io/) — UI framework
- [Gson 2.10.1](https://github.com/google/gson)

### Server
- [Flask](https://flask.palletsprojects.com/) — Python web framework
