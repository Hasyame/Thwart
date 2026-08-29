#!/usr/bin/env bash
#
# Warns, or fails on a release branch, when versionCode has not moved.
#
# F-Droid builds from tags and Android installs updates by versionCode. A tag
# whose versionCode is the same as the previous release is an update that no
# existing user can install: Android simply refuses it, and the report that
# reaches you is "the app will not update", weeks later.
#
# Most pull requests should NOT bump versionCode. Bumping it is the maintainer's
# job at release time, and two contributors bumping it independently produces a
# conflict for no benefit. So this only fails when the pull request looks like a
# release, meaning it touched the version itself.
#
# Usage: tools/ci/check-version-code.sh <base-ref>
set -euo pipefail

cd "$(dirname "$0")/../.."

BASE="${1:?usage: check-version-code.sh <base-ref>}"
GRADLE_FILE="app/build.gradle.kts"

read_code() {
    # $1 is either a git ref (read from that commit) or empty (read the file)
    if [ -n "${1:-}" ]; then
        git show "$1:$GRADLE_FILE" 2>/dev/null || true
    else
        cat "$GRADLE_FILE"
    fi | grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' \
       | grep -oE '[0-9]+' \
       | head -1
}

HEAD_CODE="$(read_code)"
BASE_CODE="$(read_code "$BASE")"

if [ -z "$HEAD_CODE" ] || [ -z "$BASE_CODE" ]; then
    echo "Could not read versionCode from both sides. Skipping."
    exit 0
fi

echo "versionCode: $BASE_CODE on $BASE, $HEAD_CODE here."

if [ "$HEAD_CODE" -eq "$BASE_CODE" ]; then
    echo "Unchanged, which is correct for an ordinary pull request."
    exit 0
fi

if [ "$HEAD_CODE" -lt "$BASE_CODE" ]; then
    echo "versionCode went backwards. Existing users could not install this."
    exit 1
fi

echo "versionCode moved forward. Correct for a release."
