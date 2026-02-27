"""
OrbRunner Upload Script
Builds the game JAR and uploads it to the distribution server.

Usage:
    python server/upload.py --server http://localhost:5000 --token changeme
    python server/upload.py --server http://localhost:5000 --token changeme --version v3.0
"""

import argparse
import os
import subprocess
import sys
import requests


def get_current_version(project_root: str) -> str:
    """Read the latest version from update_logs/index.txt."""
    index_path = os.path.join(project_root, "src", "main", "resources", "update_logs", "index.txt")
    with open(index_path, "r") as f:
        lines = [line.strip() for line in f if line.strip() and not line.startswith("#")]
    if not lines:
        raise RuntimeError("No versions found in index.txt")
    # Last line is the latest version, e.g. "v3.0.md" -> "v3.0"
    return lines[-1].replace(".md", "")


def build_jar(project_root: str) -> str:
    """Run gradlew jar and return the path to the built JAR."""
    print("[*] Building game JAR...")

    gradlew = os.path.join(project_root, "gradlew.bat" if os.name == "nt" else "gradlew")
    result = subprocess.run(
        [gradlew, "jar"],
        cwd=project_root,
        capture_output=True,
        text=True,
    )

    if result.returncode != 0:
        print("[!] Build failed:")
        print(result.stderr)
        sys.exit(1)

    print("[+] Build successful")

    # Find the JAR in build/libs/
    libs_dir = os.path.join(project_root, "build", "libs")
    jars = [f for f in os.listdir(libs_dir) if f.endswith(".jar")]
    if not jars:
        print("[!] No JAR found in build/libs/")
        sys.exit(1)

    jar_path = os.path.join(libs_dir, jars[0])
    size_mb = os.path.getsize(jar_path) / (1024 * 1024)
    print(f"[+] JAR: {jar_path} ({size_mb:.1f} MB)")
    return jar_path


def upload_jar(server_url: str, token: str, version: str, jar_path: str):
    """Upload the JAR to the distribution server."""
    print(f"[*] Uploading {version} to {server_url}...")

    url = f"{server_url.rstrip('/')}/api/upload"

    with open(jar_path, "rb") as f:
        response = requests.post(
            url,
            headers={"X-Auth-Token": token},
            data={"version": version},
            files={"jar": (os.path.basename(jar_path), f, "application/java-archive")},
        )

    if response.status_code == 201:
        data = response.json()
        size_mb = data["size"] / (1024 * 1024)
        print(f"[+] Upload successful! {version} ({size_mb:.1f} MB)")
    else:
        print(f"[!] Upload failed (HTTP {response.status_code}):")
        print(response.text)
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description="Build and upload OrbRunner to the distribution server")
    parser.add_argument("--server", required=True, help="Server URL (e.g. http://localhost:5000)")
    parser.add_argument("--token", default=None, help="Auth token for the upload endpoint (default: read from server/.auth_token)")
    parser.add_argument("--version", help="Version string (default: read from index.txt)")
    parser.add_argument("--skip-build", action="store_true", help="Skip the build step, upload existing JAR")
    args = parser.parse_args()

    # Determine project root (assumes this script is in server/)
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    # Resolve auth token
    token = args.token
    if token is None:
        token_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".auth_token")
        if os.path.exists(token_file):
            with open(token_file) as _tf:
                token = _tf.read().strip()
            print(f"[*] Token read from {token_file}")
        else:
            print("[!] No --token provided and no .auth_token file found — start the server first or pass --token")
            sys.exit(1)

    version = args.version or get_current_version(project_root)
    print(f"[*] Version: {version}")

    if args.skip_build:
        # Find existing JAR
        libs_dir = os.path.join(project_root, "build", "libs")
        jars = [f for f in os.listdir(libs_dir) if f.endswith(".jar")]
        if not jars:
            print("[!] No JAR found in build/libs/ — run without --skip-build first")
            sys.exit(1)
        jar_path = os.path.join(libs_dir, jars[0])
    else:
        jar_path = build_jar(project_root)

    upload_jar(args.server, token, version, jar_path)


if __name__ == "__main__":
    main()
