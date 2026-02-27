"""
Tests for the OrbRunner distribution server API.
"""

import io
import json


# ── Landing Page ─────────────────────────────────────────────────────────────

class TestLandingPage:
    def test_returns_200(self, client):
        resp = client.get("/")
        assert resp.status_code == 200

    def test_contains_game_name(self, client):
        resp = client.get("/")
        assert b"ORBRUNNER" in resp.data

    def test_shows_no_version_when_empty(self, client):
        resp = client.get("/")
        assert resp.status_code == 200  # should not crash with no versions


# ── GET /api/versions ────────────────────────────────────────────────────────

class TestVersionsList:
    def test_returns_empty_list_initially(self, client):
        resp = client.get("/api/versions")
        assert resp.status_code == 200
        assert resp.get_json() == []

    def test_returns_version_after_upload(self, client_with_version):
        resp = client_with_version.get("/api/versions")
        assert resp.status_code == 200
        data = resp.get_json()
        assert len(data) == 1
        assert data[0]["version"] == "v1.0"
        assert "size" in data[0]
        assert "uploaded_at" in data[0]


# ── GET /api/latest ──────────────────────────────────────────────────────────

class TestLatestVersion:
    def test_404_when_no_versions(self, client):
        resp = client.get("/api/latest")
        assert resp.status_code == 404

    def test_returns_latest_version(self, client_with_version):
        resp = client_with_version.get("/api/latest")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["version"] == "v1.0"


# ── GET /api/download/<version> ──────────────────────────────────────────────

class TestDownload:
    def test_404_for_missing_version(self, client):
        resp = client.get("/api/download/v99.99")
        assert resp.status_code == 404

    def test_downloads_existing_version(self, client_with_version):
        resp = client_with_version.get("/api/download/v1.0")
        assert resp.status_code == 200
        assert resp.content_type == "application/java-archive"
        assert b"PK" in resp.data  # fake JAR starts with PK


# ── GET /api/changelog/<version> ────────────────────────────────────────────

class TestChangelog:
    def test_400_for_invalid_version_format(self, client):
        # String that reaches the route but fails the v\d+.\d+ regex
        resp = client.get("/api/changelog/not-a-version!")
        assert resp.status_code == 400

    def test_path_traversal_blocked(self, client):
        # Flask URL router normalises ../../ away before hitting the route → 404
        resp = client.get("/api/changelog/../../etc/passwd")
        assert resp.status_code == 404

    def test_400_for_non_version_string(self, client):
        resp = client.get("/api/changelog/foobar")
        assert resp.status_code == 400

    def test_404_for_missing_changelog(self, client):
        resp = client.get("/api/changelog/v99.99")
        assert resp.status_code == 404

    def test_returns_markdown_content(self, client_with_version):
        resp = client_with_version.get("/api/changelog/v1.0")
        assert resp.status_code == 200
        assert b"v1.0" in resp.data
        assert b"# " in resp.data  # should contain markdown heading

    def test_returns_utf8_text(self, client_with_version):
        resp = client_with_version.get("/api/changelog/v1.0")
        assert "text/plain" in resp.content_type
        assert "utf-8" in resp.content_type.lower()


# ── POST /api/upload ─────────────────────────────────────────────────────────

class TestUpload:
    def test_403_with_no_token(self, client):
        resp = client.post("/api/upload")
        assert resp.status_code == 403

    def test_403_with_wrong_token(self, client):
        resp = client.post("/api/upload", headers={"X-Auth-Token": "wrongtoken"})
        assert resp.status_code == 403

    def test_400_missing_version_field(self, client):
        resp = client.post(
            "/api/upload",
            headers={"X-Auth-Token": "changeme"},
            data={"jar": (io.BytesIO(b"PK fake"), "test.jar")},
            content_type="multipart/form-data",
        )
        assert resp.status_code == 400

    def test_400_missing_jar_file(self, client):
        resp = client.post(
            "/api/upload",
            headers={"X-Auth-Token": "changeme"},
            data={"version": "v2.0"},
            content_type="multipart/form-data",
        )
        assert resp.status_code == 400

    def test_successful_upload(self, client):
        fake_jar = io.BytesIO(b"PK\x03\x04 fake jar content")
        resp = client.post(
            "/api/upload",
            headers={"X-Auth-Token": "changeme"},
            data={"version": "v2.0", "jar": (fake_jar, "orbrunner-v2.0.jar")},
            content_type="multipart/form-data",
        )
        assert resp.status_code == 201
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["version"] == "v2.0"
        assert data["size"] > 0

    def test_version_appears_in_list_after_upload(self, client):
        fake_jar = io.BytesIO(b"PK\x03\x04 fake jar content")
        client.post(
            "/api/upload",
            headers={"X-Auth-Token": "changeme"},
            data={"version": "v2.0", "jar": (fake_jar, "orbrunner-v2.0.jar")},
            content_type="multipart/form-data",
        )
        resp = client.get("/api/versions")
        versions = [v["version"] for v in resp.get_json()]
        assert "v2.0" in versions

    def test_versions_sorted_correctly(self, client):
        """Versions should be sorted by number, not lexicographically."""
        for version in ["v1.0", "v10.0", "v2.0"]:
            fake_jar = io.BytesIO(b"PK fake")
            client.post(
                "/api/upload",
                headers={"X-Auth-Token": "changeme"},
                data={"version": version, "jar": (fake_jar, f"orbrunner-{version}.jar")},
                content_type="multipart/form-data",
            )

        resp = client.get("/api/versions")
        versions = [v["version"] for v in resp.get_json()]
        assert versions == ["v1.0", "v2.0", "v10.0"]

    def test_reuploading_replaces_existing(self, client):
        """Re-uploading the same version should not create a duplicate."""
        for _ in range(2):
            fake_jar = io.BytesIO(b"PK fake")
            client.post(
                "/api/upload",
                headers={"X-Auth-Token": "changeme"},
                data={"version": "v1.0", "jar": (fake_jar, "orbrunner-v1.0.jar")},
                content_type="multipart/form-data",
            )

        resp = client.get("/api/versions")
        versions = [v["version"] for v in resp.get_json()]
        assert versions.count("v1.0") == 1


# ── GET /download/launcher ───────────────────────────────────────────────────

class TestLauncherDownload:
    def test_404_when_no_launcher(self, client):
        resp = client.get("/download/launcher")
        assert resp.status_code == 404

    def test_downloads_launcher_when_present(self, client, tmp_path, monkeypatch):
        import app as server_app
        launcher_jar = tmp_path / "launcher" / "orbrunner-launcher.jar"
        launcher_jar.write_bytes(b"PK\x03\x04 fake launcher jar")
        monkeypatch.setattr(server_app, "LAUNCHER_DIR", str(tmp_path / "launcher"))

        resp = client.get("/download/launcher")
        assert resp.status_code == 200
        assert b"PK" in resp.data
