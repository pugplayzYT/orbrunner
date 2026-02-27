# OrbRunner

> [!NOTE]
> **Usage & Licensing**
>
> This repository is **source-available**, not open source.
>
> **You may:**
> - Clone and build the project for personal, non-commercial use
> - Run your own private distribution server (e.g. for a local network)
> - Fork the repository to develop and submit pull requests
> - Study and learn from the code
>
> **You may not:**
> - Redistribute this game — modified or unmodified — in any form (uploading elsewhere, repackaging, sharing builds publicly)
> - Use any part of this codebase in a commercial product without explicit written permission
> - Publish a fork as a standalone project or release
>
> Forks exist for the purpose of contributing back via pull requests. A fork is not a licence to distribute.
>
> © [pugplayzYT](https://github.com/pugplayzYT). All rights reserved.

---

OrbRunner is a 3D first-person maze escape game built from scratch in Java using LWJGL and OpenGL — no game engine. Every frame, every menu, every HUD element is rendered directly in OpenGL.

The goal is simple: navigate a procedurally generated maze, collect all the keys, and find the exit before the horror overtakes you. Each run is named and saved to disk so you can quit and come back later to the exact same maze, same position, keys already collected.

**What's in the repo:**

- **Game** — the actual game, a fat JAR you can run directly
- **Launcher** — a JavaFX app that connects to a distribution server, manages versions, and launches the game
- **Server** — a small Flask app that stores and serves versioned game JARs

---

## The Game

Rooms are procedurally generated from a seeded layout — standard corridors, courtyards, bedrooms, and padded cells. They connect via tunnels and every world is fully reproducible from its seed.

The HUD shows a minimap, a key counter, and a hot/cold indicator that pulses closer to the exit. Volume, FOV, mouse sensitivity, and fog density are all adjustable from the in-game pause menu. There is no Swing, no AWT — everything including menus and the game-over screen renders as OpenGL geometry.

Runs are stored under `~/.orbCollectorGame/runs/` as plain-text `.dat` files. Completed runs are archived with your finish time. Best times are tracked separately.

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

Always use `clean` — Gradle caches aggressively and will ship the wrong version string or resources without it.

### Build & Run the Launcher

The launcher bakes the server URL in at build time. Edit `gradle.properties` first if you want it to point somewhere other than `http://localhost:5000`:

```bash
# gradle.properties
orbrunnerServerUrl=http://localhost:5000
```

Then build and run:

```bash
./gradlew :launcher:jar
java -jar launcher/build/libs/launcher-*.jar
```

The launcher stores its own config at `~/.orbrunner/launcher.json` and caches downloaded JARs at `~/.orbrunner/versions/`.

### Run the Distribution Server

```bash
pip install -r server/requirements.txt
python server/app.py
# Generates a random auth token on first start, saves it to server/.auth_token
```

To upload a build:

```bash
python server/upload.py                                      # reads server/.env and server/.auth_token automatically
python server/upload.py --server http://localhost:5000 --token <token>  # or pass explicitly
```

### Run the Tests

```bash
./gradlew test          # Java unit tests (JUnit 5)
pytest server/tests/ -v # Flask server tests
```

---

## Contributing

Pull requests are welcome. To keep things consistent, there are a few requirements before a PR will be accepted.

### Before You Start

- Open a discussion or leave a comment on a relevant commit before building something large. No point building something that won't land.
- Keep changes focused. One feature or fix per PR.

### Requirements for Every PR

**1. Changelog entry** (required for any source code change)

Create a new file at `src/main/resources/update_logs/vX.X.md`. Pick the version number based on what you changed:

| Change | Version bump |
|--------|-------------|
| Bug fix, small tweak | +0.1 |
| New feature | +0.25 |
| Major system / rewrite | +1.0 |

Keep decimals clean: `v3.6`, `v4.0` — not `v3.55` or `v3.125`. If the current version is `v3.5` and you're adding a feature, your changelog is `v3.75.md`.

Changelog format:

```markdown
# vX.X - Short Title

## Release Date: Month Day, Year

One sentence describing the update.

### What changed

- **Feature name** — what it does and why it matters
- **Another change** — description
```

**2. Add the filename to the index**

Append your changelog filename as a new line at the end of `src/main/resources/update_logs/index.txt`:

```
v3.75.md
```

**3. Copy the changelog to the server directory**

```
server/changelogs/vX.X.md
```

This must be an exact copy of the game-side changelog.

**4. Tests must pass**

```bash
./gradlew test
pytest server/tests/ -v
```

Write tests for any new or changed behavior. If your PR adds a feature, the test coverage for that feature should be in the same PR.

**5. Commit message format**

```
<type>: <description> (vX.X)
```

Types: `feat`, `fix`, `refactor`, `ci`, `docs`, `chore`

Examples:
```
feat: add sprint mechanic with stamina bar (v3.75)
fix: correct key spawn overlap in courtyard rooms (v3.6)
```

No AI attribution lines (`Co-Authored-By`, `Generated with`, etc.).

### What Will Be Rejected

- PRs without a changelog entry when source code changed
- PRs with failing tests
- PRs that touch multiple unrelated things at once
- PRs that break the build (`./gradlew jar` must succeed)

### The CI Check

The `require-changelog` workflow runs on every PR and will fail if it detects source code changes without a corresponding changelog file. Fix it before asking for review.

---

## Architecture

```
orbrunner/
├── src/            Game (Java / LWJGL / OpenGL)
├── launcher/       Launcher (Java / JavaFX)
├── server/         Distribution server (Python / Flask)
└── gradle.properties  Server URL baked into launcher at build time
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

Auth token is generated on first start and saved to `server/.auth_token` (gitignored).

---

## CI

On every push to `master` and on every PR, the pipeline builds and tests in parallel:

- Game JAR (`./gradlew jar`)
- Launcher JAR (`./gradlew :launcher:jar`)
- Java unit tests (`./gradlew test`)
- Flask server tests (`pytest server/tests/ -v`)

Build artifacts are kept for 7 days. There are no automatic releases — deploys are done manually via `server/upload.py`.

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
