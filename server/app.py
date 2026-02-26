"""
OrbRunner Distribution Server
Flask app that stores and serves versioned game JARs.
"""

import os
import re
import json
import time
from flask import Flask, request, jsonify, send_file, abort, Response

app = Flask(__name__)

# --- Configuration ---
VERSIONS_DIR   = os.path.join(os.path.dirname(os.path.abspath(__file__)), "versions")
METADATA_FILE  = os.path.join(VERSIONS_DIR, "metadata.json")
CHANGELOGS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "changelogs")
AUTH_TOKEN     = os.environ.get("ORBRUNNER_AUTH_TOKEN", "changeme")

os.makedirs(VERSIONS_DIR,   exist_ok=True)
os.makedirs(CHANGELOGS_DIR, exist_ok=True)


def _load_metadata():
    """Load the version metadata JSON, or return an empty structure."""
    if os.path.exists(METADATA_FILE):
        with open(METADATA_FILE, "r") as f:
            return json.load(f)
    return {"versions": []}


def _save_metadata(data):
    """Persist version metadata to disk."""
    with open(METADATA_FILE, "w") as f:
        json.dump(data, f, indent=2)


def _jar_path(version: str) -> str:
    """Return the expected JAR file path for a given version string."""
    return os.path.join(VERSIONS_DIR, f"orbrunner-{version}.jar")


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

    # Update metadata
    meta = _load_metadata()

    # Remove existing entry for this version if re-uploading
    meta["versions"] = [v for v in meta["versions"] if v["version"] != version]

    meta["versions"].append({
        "version": version,
        "size": file_size,
        "uploaded_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    })

    # Sort by version string (works for vX.X format)
    meta["versions"].sort(key=lambda v: _version_sort_key(v["version"]))
    _save_metadata(meta)

    return jsonify({
        "status": "ok",
        "version": version,
        "size": file_size,
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


@app.route("/", methods=["GET"])
def index():
    """Simple landing page."""
    return jsonify({
        "name": "OrbRunner Distribution Server",
        "endpoints": {
            "/api/versions": "GET — list all versions",
            "/api/latest": "GET — get latest version info",
            "/api/download/<version>": "GET — download a version JAR",
            "/api/upload": "POST — upload a new version (requires auth)",
        },
    })


if __name__ == "__main__":
    print(f"[OrbRunner Server] Versions directory: {VERSIONS_DIR}")
    print(f"[OrbRunner Server] Auth token: {'(set via env)' if os.environ.get('ORBRUNNER_AUTH_TOKEN') else AUTH_TOKEN}")
    app.run(host="0.0.0.0", port=5000, debug=True)
