# OrbRunner

A 3D first-person maze escape game built with Java, LWJGL, and OpenGL. Navigate procedurally generated rooms, collect keys, and find the exit.

---

## Getting Started

### Requirements

- Java 17+ — [Adoptium](https://adoptium.net/)
- Python 3.11+ — [python.org](https://www.python.org/)
- Git

### Build & Run the Game

```bash
git clone https://github.com/pugplayzYT/orbrunner.git
cd orbrunner

./gradlew clean jar
java -jar build/libs/orbrunner-*.jar
```

### Build & Run the Launcher

Edit `gradle.properties` to point at your server (defaults to `http://localhost:5000`), then:

```bash
./gradlew :launcher:jar
java -jar launcher/build/libs/launcher-*.jar
```

### Run the Distribution Server

```bash
pip install -r server/requirements.txt
python server/app.py
# Prints a random auth token on first start, saves it to server/.auth_token

# Upload a build
python server/upload.py
```

### Run the Tests

```bash
./gradlew test          # Java unit tests (JUnit 5)
pytest server/tests/ -v # Flask server tests
```

---

## Contributing

Pull requests are welcome. If your change touches source code, include a changelog entry at `src/main/resources/update_logs/vX.X.md` and add the filename to `index.txt`.

---

## Architecture

```
orbrunner/
├── src/            Game (Java / LWJGL / OpenGL)
├── launcher/       Launcher (Java / JavaFX)
├── server/         Distribution server (Python / Flask)
└── gradle.properties  Server URL config (baked into launcher at build time)
```

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

### Server

| Endpoint | Description |
|---|---|
| `GET /api/latest` | Latest version metadata |
| `GET /api/versions` | All available versions |
| `GET /api/download/<version>` | Download a game JAR |
| `GET /api/changelog/<version>` | Version changelog (markdown) |
| `POST /api/upload` | Upload a build (requires auth token) |

The server generates a cryptographically secure random auth token on first start. Token is saved to `server/.auth_token` (gitignored).

---

## CI

On every push to `master` the pipeline builds and tests everything in parallel — game JAR, launcher JAR, Java unit tests, and Flask server tests. Build artifacts are uploaded and kept for 7 days. There are no automatic releases; deploys are done manually with `server/upload.py`.

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
