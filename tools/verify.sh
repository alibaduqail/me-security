#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
python3 tools/validate_assets.py

# Run sequentially because the GameTest variants share a development world.
./gradlew test build runGameTestServer --console=plain "$@"
./gradlew test build runGameTestServer -PwithWireless=true --console=plain "$@"

artifact_version="$(grep '^mod_version=' gradle.properties | cut -d= -f2-)"
artifact="build/libs/ae2security-${artifact_version}.jar"
test -f "$artifact"
entries="$(jar tf "$artifact")"
grep -q '^assets/ae2security/ae2guide/security_terminal.md$' <<< "$entries"
if grep -qiE 'SecureDrive|secure_drive|PinAttemptTracker' <<< "$entries"; then
    echo "Removed Secure Drive feature remains in distribution JAR" >&2
    exit 1
fi
if grep -qE 'AuditProbes|\.DS_Store' <<< "$entries"; then
    echo "Unexpected diagnostic or desktop file in distribution JAR" >&2
    exit 1
fi
shasum -a 256 "$artifact" > "$artifact.sha256"
cat "$artifact.sha256"
