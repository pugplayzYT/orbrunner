"""
Shared pytest fixtures for the OrbRunner server tests.

Each test gets an isolated temp directory for versions/changelogs/launcher
so tests never interfere with each other or the real server data.
"""

import os
import sys
import pytest

# Make sure `import app` resolves to server/app.py
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import app as server_app


@pytest.fixture
def client(tmp_path, monkeypatch):
    """
    Flask test client with all file paths redirected to a temp directory.
    Each test that uses this fixture gets a clean, isolated environment.
    """
    versions_dir = tmp_path / "versions"
    changelogs_dir = tmp_path / "changelogs"
    launcher_dir = tmp_path / "launcher"

    versions_dir.mkdir()
    changelogs_dir.mkdir()
    launcher_dir.mkdir()

    monkeypatch.setattr(server_app, "VERSIONS_DIR",   str(versions_dir))
    monkeypatch.setattr(server_app, "METADATA_FILE",  str(versions_dir / "metadata.json"))
    monkeypatch.setattr(server_app, "CHANGELOGS_DIR", str(changelogs_dir))
    monkeypatch.setattr(server_app, "LAUNCHER_DIR",   str(launcher_dir))
    monkeypatch.setattr(server_app, "AUTH_TOKEN",     "changeme")

    server_app.app.config["TESTING"] = True
    with server_app.app.test_client() as c:
        yield c


@pytest.fixture
def client_with_version(client, tmp_path, monkeypatch):
    """
    Same as `client` but with a fake v1.0 game JAR and changelog pre-loaded.
    Use this when tests need an existing version to work with.
    """
    # Write a fake JAR
    jar_path = tmp_path / "versions" / "orbrunner-v1.0.jar"
    jar_path.write_bytes(b"PK\x03\x04 fake jar content for testing")

    # Write a fake changelog
    cl_path = tmp_path / "changelogs" / "v1.0.md"
    cl_path.write_text(
        "# v1.0 - The First Release\n"
        "## Release Date: January 1, 2025\n\n"
        "Initial release.\n\n"
        "### What's New\n"
        "- Everything\n",
        encoding="utf-8",
    )

    # Seed the metadata file
    import json
    import time
    meta = {
        "versions": [{
            "version": "v1.0",
            "size": jar_path.stat().st_size,
            "uploaded_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        }]
    }
    (tmp_path / "versions" / "metadata.json").write_text(json.dumps(meta))

    yield client
