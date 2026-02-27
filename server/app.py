"""
OrbRunner Distribution Server
Flask app that stores and serves versioned game JARs.
"""

import os
import re
import json
import time
import hashlib
import secrets
from flask import Flask, request, jsonify, send_file, abort, Response, render_template

app = Flask(__name__)

# --- Configuration ---
BASE_DIR       = os.path.dirname(os.path.abspath(__file__))
VERSIONS_DIR   = os.path.join(BASE_DIR, "versions")
METADATA_FILE  = os.path.join(VERSIONS_DIR, "metadata.json")
CHANGELOGS_DIR = os.path.join(BASE_DIR, "changelogs")
LAUNCHER_DIR   = os.path.join(BASE_DIR, "launcher")
_TOKEN_FILE    = os.path.join(BASE_DIR, ".auth_token")

_env_token = os.environ.get("ORBRUNNER_AUTH_TOKEN")
if _env_token:
    AUTH_TOKEN = _env_token
else:
    AUTH_TOKEN = secrets.token_urlsafe(32)
    with open(_TOKEN_FILE, "w") as _tf:
        _tf.write(AUTH_TOKEN)

os.makedirs(VERSIONS_DIR,   exist_ok=True)
os.makedirs(CHANGELOGS_DIR, exist_ok=True)
os.makedirs(LAUNCHER_DIR,   exist_ok=True)


def _load_metadata():
    """Load the version metadata JSON, or return an empty structure.
    Backfills any missing SHA-256 hashes for JARs that were uploaded before
    hash tracking was introduced.
    """
    if os.path.exists(METADATA_FILE):
        with open(METADATA_FILE, "r") as f:
            data = json.load(f)
        # Backfill missing hashes for pre-existing JARs
        changed = False
        for v in data.get("versions", []):
            if not v.get("hash"):
                jar = _jar_path(v["version"])
                if os.path.exists(jar):
                    v["hash"] = _compute_hash(jar)
                    changed = True
        if changed:
            _save_metadata(data)
        return data
    return {"versions": []}


def _save_metadata(data):
    """Persist version metadata to disk."""
    with open(METADATA_FILE, "w") as f:
        json.dump(data, f, indent=2)


def _jar_path(version: str) -> str:
    """Return the expected JAR file path for a given version string."""
    return os.path.join(VERSIONS_DIR, f"orbrunner-{version}.jar")


def _compute_hash(filepath: str) -> str:
    """Compute the SHA-256 hash of a file and return it as a hex string."""
    sha256 = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            sha256.update(chunk)
    return sha256.hexdigest()


# ──────────────────────────────────────────────
#  API Endpoints
# ──────────────────────────────────────────────


@app.route("/api/versions", methods=["GET"])
def list_versions():
    """Return all available versions with metadata."""
    meta = _load_metadata()
    return jsonify(meta["versions"])


@app.route("/api/latest", methods=["GET"])
def latest_version():
    """Return the latest version string."""
    meta = _load_metadata()
    if not meta["versions"]:
        return jsonify({"version": None, "message": "No versions uploaded yet"}), 404
    latest = meta["versions"][-1]
    return jsonify(latest)


@app.route("/api/download/<version>", methods=["GET"])
def download_version(version):
    """Stream the JAR for the requested version."""
    jar = _jar_path(version)
    if not os.path.exists(jar):
        abort(404, description=f"Version {version} not found")

    return send_file(
        jar,
        mimetype="application/java-archive",
        as_attachment=True,
        download_name=f"orbrunner-{version}.jar",
    )


@app.route("/api/changelog/<version>", methods=["GET"])
def get_changelog(version):
    """Return the raw markdown changelog for a version (plain text)."""
    if not re.match(r'^v[\d.]+$', version):
        abort(400, description="Invalid version format")
    path = os.path.join(CHANGELOGS_DIR, f"{version}.md")
    if not os.path.exists(path):
        abort(404, description=f"Changelog for {version} not found")
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    return Response(content, mimetype="text/plain; charset=utf-8")


@app.route("/api/upload", methods=["POST"])
def upload_version():
    """
    Upload a new game build.
    Requires:
      - Header: X-Auth-Token
      - Form field: version (e.g. "v3.0")
      - Form file: jar (the JAR file)
    """
    # Auth check
    token = request.headers.get("X-Auth-Token", "")
    if token != AUTH_TOKEN:
        abort(403, description="Invalid auth token")

    version = request.form.get("version")
    jar_file = request.files.get("jar")

    if not version:
        abort(400, description="Missing 'version' form field")
    if not jar_file:
        abort(400, description="Missing 'jar' file upload")

    # Save the JAR
    dest = _jar_path(version)
    jar_file.save(dest)
    file_size = os.path.getsize(dest)
    file_hash = _compute_hash(dest)

    # Update metadata
    meta = _load_metadata()

    # Remove existing entry for this version if re-uploading
    meta["versions"] = [v for v in meta["versions"] if v["version"] != version]

    meta["versions"].append({
        "version": version,
        "size": file_size,
        "uploaded_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "hash": file_hash,
    })

    # Sort by version string (works for vX.X format)
    meta["versions"].sort(key=lambda v: _version_sort_key(v["version"]))
    _save_metadata(meta)

    return jsonify({
        "status": "ok",
        "version": version,
        "size": file_size,
        "hash": file_hash,
    }), 201


def _version_sort_key(version_str: str):
    """
    Parse 'v3.0' into (3, 0) for sorting.
    Handles versions like v1.0, v2.1, v10.25, etc.
    """
    stripped = version_str.lstrip("v")
    parts = stripped.split(".")
    result = []
    for p in parts:
        try:
            result.append(int(p))
        except ValueError:
            result.append(0)
    return tuple(result)


@app.route("/download/launcher", methods=["GET"])
def download_launcher():
    """Download the launcher JAR."""
    jar = os.path.join(LAUNCHER_DIR, "orbrunner-launcher.jar")
    if not os.path.exists(jar):
        abort(404, description="Launcher not available yet — check back soon.")
    return send_file(
        jar,
        mimetype="application/java-archive",
        as_attachment=True,
        download_name="orbrunner-launcher.jar",
    )


@app.route("/", methods=["GET"])
def index():
    """Game landing page."""
    meta = _load_metadata()
    latest = meta["versions"][-1]["version"] if meta["versions"] else None
    launcher_available = os.path.exists(os.path.join(LAUNCHER_DIR, "orbrunner-launcher.jar"))
    return render_template("index.html", latest_version=latest, launcher_available=launcher_available)


if __name__ == "__main__":
    print(f"[OrbRunner Server] Versions directory: {VERSIONS_DIR}")
    if _env_token:
        print("[OrbRunner Server] Auth token: (set via ORBRUNNER_AUTH_TOKEN env var)")
    else:
        print(f"[OrbRunner Server] Auth token: {AUTH_TOKEN}")
        print(f"[OrbRunner Server] Token saved to: {_TOKEN_FILE}")
    app.run(host="0.0.0.0", port=5000, debug=True)
