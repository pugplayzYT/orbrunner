> [!CAUTION]
> ## ⛔ External Pull Requests Are Not Accepted
> OrbRunner is a solo-developed project. **Pull requests from outside the development team are automatically closed** — no exceptions.
>
> **If you have a bug report or feature suggestion, please use Issues:**
> - 🐛 [Report a bug](../../issues/new?template=bug_report.yml)
> - 💡 [Suggest a feature](../../issues/new?template=feature_suggestion.yml)

# OrbRunner

A 3D first-person maze escape game built with Java, LWJGL, and OpenGL. Navigate procedurally generated rooms, collect keys, and find the exit.

---

## Playing

The easiest way to play is through the official launcher. It connects to the OrbRunner distribution server automatically — no configuration needed.

| Platform | Download |
|---|---|
| Windows / macOS / Linux | [**Download Launcher**](https://github.com/pugplayzYT/orbrunner/releases/latest/download/launcher-1.0.0.jar) |

**Requires Java 17+** — [Download from Adoptium](https://adoptium.net/)

```bash
java -jar launcher-1.0.0.jar
```

The launcher checks for updates, shows patch notes, lets you download any version, and launches the game. Official builds connect to the official server out of the box.

---

## Self-Hosting

> [!NOTE]
> You may clone this repository and run your own server for personal use. **Redistributing the game — modified or unmodified — is not permitted.**

If you want to host the game on your own server (e.g. for a local network or private group), you need to run all three components yourself.

### Requirements

- Java 17+ — [Adoptium](https://adoptium.net/)
- Python 3.11+ — [python.org](https://www.python.org/)
- Git

### Quick Setup

```bash
# 1. Clone
git clone https://github.com/pugplayzYT/orbrunner.git
cd orbrunner

# 2. Point the launcher at your server
#    Edit gradle.properties:
#    orbrunnerServerUrl=http://your-server:5000

# 3. Build
./gradlew clean jar          # game JAR
./gradlew :launcher:jar      # launcher JAR (server URL baked in)

# 4. Start the server
pip install -r server/requirements.txt
python server/app.py
# → Prints a random auth token and saves it to server/.auth_token

# 5. Upload the game build
python server/upload.py      # reads URL and token automatically
```

For a full walkthrough of the server setup, CI/CD wiring, and GitHub Secrets, see **[setup.md](setup.md)**.

---

## Architecture

```
orbrunner/
├── src/            Game (Java / LWJGL / OpenGL)
├── launcher/       Launcher (Java / JavaFX)
├── server/         Distribution server (Python / Flask)
└── gradle.properties  Server URL config (baked into launcher at build time)
```

### Game

- **Procedural generation** — seed-based room layouts (standard, courtyard, bedroom) with connecting tunnels
- **First-person 3D** — rendered entirely in OpenGL; menus and HUD drawn in-engine
- **Runs system** — save/resume multi-session runs in `~/.orbCollectorGame/runs/`
- **Key collection** — find keys to unlock the escape door
- **HUD** — minimap, key counter, hot/cold proximity indicator
- **Audio** — ambient loops and sound effects with real-time volume control

### Launcher

- Connects to the distribution server, lists available versions, downloads and caches JARs locally (`~/.orbrunner/versions/`)
- Shows per-version patch notes with markdown rendering
- Settings panel syncs live with the running game
- Uninstall any version from the bottom bar

### Server

| Endpoint | Description |
|---|---|
| `GET /api/latest` | Latest version metadata |
| `GET /api/versions` | All available versions |
| `GET /api/download/<version>` | Download a game JAR |
| `GET /api/changelog/<version>` | Version changelog (markdown) |
| `POST /api/upload` | Upload a build (requires auth token) |

Generates a cryptographically secure random auth token on every start. Token is saved to `server/.auth_token` (gitignored).

---

## CI/CD

On every push to `master` the pipeline:

1. Builds and tests everything in parallel
2. Checks if a GitHub Release already exists for the current version
3. If not — creates a release with both JARs, tags the commit, and uploads the game JAR to the distribution server

The official server URL is stored as a `SERVER_URL` repository secret and baked into the launcher JAR at CI build time. To update it, change the secret and run **Actions → Rebuild Launcher**.

---

## Project Structure

### Game (`src/main/java/ohio/pugnetgames/chad/`)

| Package | Key Files | Description |
|---|---|---|
| `game/` | `GamePanel.java` | Main game loop and state machine |
| `game/` | `Player.java` | First-person camera, movement, collision |
| `game/` | `WorldLoader.java` | Procedural room and tunnel generation |
| `game/` | `InGameUI.java` | Menus and dialogs rendered in OpenGL |
| `game/` | `HudRenderer.java` | 2D overlays — minimap, objectives, key count |
| `game/` | `SoundManager.java` | Audio playback and volume control |
| `core/` | `RunManager.java` | Save/load runs across sessions |
| `core/` | `ScoreManager.java` | Best-time persistence |

### Launcher (`launcher/src/main/java/ohio/pugnetgames/chad/launcher/`)

| File | Description |
|---|---|
| `LauncherUI.java` | Full UI — main screen, settings, changelog, progress overlays |
| `GameManager.java` | Server API calls, local JAR cache, version management |
| `DownloadTask.java` | Threaded download with progress binding |
| `SettingsManager.java` | Live settings sync via WatchService |
| `MarkdownRenderer.java` | Renders update logs as styled JavaFX nodes |

---

## Dependencies

### Game
- [LWJGL 3.3.3](https://www.lwjgl.org/) — OpenGL, GLFW, STB, ASSIMP
- [MP3SPI / JLayer](https://www.javazoom.net/) — MP3 audio
- [Gson 2.10.1](https://github.com/google/gson) — JSON

### Launcher
- [JavaFX 17.0.2](https://openjfx.io/)
- [Gson 2.10.1](https://github.com/google/gson)

### Server
- [Flask 3+](https://flask.palletsprojects.com/)

---

## Feedback

OrbRunner is developed solely by [pugplayzYT](https://github.com/pugplayzYT). External pull requests are automatically closed.

- 🐛 [Report a bug](../../issues/new?template=bug_report.yml)
- 💡 [Suggest a feature](../../issues/new?template=feature_suggestion.yml)
