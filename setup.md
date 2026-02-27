# OrbRunner — Setup Guide

> **Latest release: <!-- LATEST_VERSION -->v3.5<!-- /LATEST_VERSION -->**
> [View on GitHub →](https://github.com/pugplayzYT/orbrunner/releases/download/v3.5/orbrunner-v3.5.jar)

This guide covers three things:

- **Playing** — download and run the game with the launcher
- **Developing** — build and run everything locally
- **Hosting** — run your own distribution server and wire up the full CI/CD pipeline

---

## Playing

### 1. Download the Launcher

The launcher handles downloading, updating, and launching the game for you.

| Platform | Download |
|----------|----------|
| Windows / macOS / Linux | [orbrunner-launcher.jar](https://github.com/pugplayzYT/orbrunner/releases/latest/download/launcher-1.0.0.jar) |

**Requires Java 17+** — [Download from Adoptium](https://adoptium.net/)

### 2. Run the Launcher

```bash
java -jar launcher-1.0.0.jar
```

The launcher connects to the distribution server, checks for the latest version, and lets you download and launch any available version. On first run it will prompt you to install.

---

## Developer Setup

### Prerequisites

| Tool | Version | Where |
|------|---------|-------|
| Java JDK | 17+ | [Adoptium](https://adoptium.net/) |
| Python | 3.11+ | [python.org](https://www.python.org/) |
| Git | Any | [git-scm.com](https://git-scm.com/) |

### Clone

```bash
git clone https://github.com/pugplayzYT/orbrunner.git
cd orbrunner
```

### Build the Game

```bash
./gradlew clean jar
```

Output: `build/libs/orbrunner-vX.X.jar`

Run it directly:

```bash
java -jar build/libs/orbrunner-*.jar
```

### Build the Launcher

```bash
./gradlew :launcher:jar
```

Output: `launcher/build/libs/launcher-1.0.0.jar`

### Run the Tests

```bash
./gradlew test            # Java unit tests (JUnit 5)
pytest server/tests/ -v   # Flask server tests
```

---

## Server Setup

The distribution server is a small Flask app that stores versioned JARs and serves them to launchers.

### Install Dependencies

```bash
pip install -r server/requirements.txt
```

### Configure

Copy the example env file and fill in your public-facing URL:

```bash
cp server/.env.example server/.env
```

Edit `server/.env`:

```
ORBRUNNER_SERVER_URL=https://your-server-url.com
```

Use `http://localhost:5000` for local development.

### Start the Server

```bash
python server/app.py
```

On first start the server generates a cryptographically secure random auth token, prints it, and saves it to `server/.auth_token`:

```
[OrbRunner Server] Auth token: <random-token>
[OrbRunner Server] Token saved to: server/.auth_token
```

Both `server/.env` and `server/.auth_token` are gitignored — they never leave your machine.

### Upload a Build to the Server

After building the game JAR, upload it:

```bash
python server/upload.py
```

The script auto-reads the server URL from `server/.env` and the token from `server/.auth_token`. You can also pass them explicitly:

```bash
python server/upload.py --server https://your-server-url.com --token <token>
```

---

## How It All Hooks Together

```
┌──────────────────┐   push    ┌──────────────────┐  upload  ┌──────────────────┐
│   Developer      │ ────────▶ │  GitHub Actions  │ ───────▶ │  Flask Server    │
│                  │           │  CI Pipeline     │          │  (distribution)  │
│  git push master │           │  · builds JARs   │          │  · stores JARs   │
└──────────────────┘           │  · runs tests    │          │  · serves API    │
                               │  · creates tag   │          └────────┬─────────┘
                               │  · GitHub Release│                   │
                               └──────────────────┘       /api/latest │ /api/download
                                                                       │
                                                      ┌────────────────▼─────────────┐
                                                      │  OrbRunner Launcher           │
                                                      │  (on player's machine)        │
                                                      │  · polls for updates          │
                                                      │  · downloads & launches JARs  │
                                                      └──────────────────────────────┘
```

### Step-by-Step Flow

1. Developer pushes a commit to `master`
2. GitHub Actions builds both JARs and runs all tests in parallel
3. CI reads the version from `src/main/resources/update_logs/index.txt`
4. If no GitHub Release exists for that version yet, CI:
   - Creates a git tag (`vX.X`)
   - Creates a GitHub Release with both JARs as downloadable assets
   - Uploads the game JAR to the distribution server via `server/upload.py`
5. Players' launchers poll `/api/latest`, detect the new version, and download it

### Pointing the Launcher at Your Server

The launcher stores its server URL in `~/.orbrunner/launcher.json`, created on first run. Edit it to point at a custom server:

```json
{
  "serverUrl": "https://your-server-url.com",
  "selectedVersion": null,
  "lastPlayedVersion": null
}
```

---

## CI/CD Pipeline Setup

The pipeline lives in `.github/workflows/ci.yml`. To enable the full deploy step you need two repository secrets.

### Add GitHub Secrets

Go to **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|--------|-------|
| `SERVER_URL` | The public URL of your Flask distribution server |
| `SERVER_TOKEN` | The auth token printed when the server starts (`server/.auth_token`) |

### What the Pipeline Does Automatically

| Event | What happens |
|-------|-------------|
| Push to `master` | Builds JARs, runs tests, creates GitHub Release if version is new, uploads game JAR to server |
| Every commit | Updates this file (`setup.md`) with the latest version number and download links |

### Keeping the Token Stable

The server generates a new random token every restart. To avoid updating `SERVER_TOKEN` in GitHub Secrets after each restart, pin the token with an environment variable before starting:

```bash
export ORBRUNNER_AUTH_TOKEN=your-fixed-secret-token
python server/app.py
```

Set `SERVER_TOKEN` in GitHub Secrets to that same value once and you're done.

---

*This file is kept up to date automatically by the CI pipeline on every commit.*
