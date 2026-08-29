#!/usr/bin/env bash
#
# Fails if the release runtime classpath contains a dependency F-Droid will not
# accept.
#
# Why this exists: a well meaning contributor adds Firebase for crash reporting,
# it is merged, and the first anybody hears of it is a rejected F-Droid build
# weeks later. The app's promise is also that it collects nothing, and half this
# list would quietly break that promise.
#
# The list of coordinates lives in proprietary-coordinates.txt next to this
# script, so extending it is editing a text file rather than a workflow.
#
# Usage: tools/ci/check-dependencies.sh
set -euo pipefail

cd "$(dirname "$0")/../.."

BLOCKLIST="tools/ci/proprietary-coordinates.txt"
ALLOWLIST="tools/ci/proprietary-allowed.txt"

echo "Resolving the release runtime classpath..."
# `dependencies` prints the whole tree; releaseRuntimeClasspath is what actually
# ships, as opposed to test or debug-only dependencies which F-Droid ignores.
RESOLVED="$(./gradlew --no-daemon --quiet :app:dependencies \
    --configuration releaseRuntimeClasspath 2>/dev/null || true)"

if [ -z "$RESOLVED" ]; then
    echo "Could not resolve the classpath. Failing rather than passing blind."
    exit 1
fi

# Lines look like "+--- com.squareup.okhttp3:okhttp:5.0.0". Pull out the
# coordinate and drop the version, so the blocklist never has to know versions.
COORDINATES="$(printf '%s\n' "$RESOLVED" \
    | grep -oE '[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+:[a-zA-Z0-9._+-]+' \
    | sed 's/:[^:]*$//' \
    | sort -u)"

failed=0
while IFS= read -r prefix; do
    case "$prefix" in ''|\#*) continue ;; esac
    while IFS= read -r found; do
        [ -z "$found" ] && continue
        if [ -f "$ALLOWLIST" ] && grep -qxF "$found" "$ALLOWLIST"; then
            echo "  allowed by $ALLOWLIST: $found"
            continue
        fi
        echo "REJECTED: $found matches blocked prefix '$prefix'"
        failed=1
    done <<< "$(printf '%s\n' "$COORDINATES" | grep -F "$prefix" || true)"
done < "$BLOCKLIST"

if [ "$failed" -ne 0 ]; then
    cat <<'MESSAGE'

F-Droid will not build the app with these dependencies, and several of them
would also break the promise that this app collects nothing about its users.

If you believe one of these is genuinely free software and should be allowed,
add its exact `group:artifact` to tools/ci/proprietary-allowed.txt and say why
in your pull request.
MESSAGE
    exit 1
fi

echo "No blocked dependencies on the release runtime classpath."
