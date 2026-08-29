#!/usr/bin/env bash
#
# Fails if a pre-compiled binary is tracked in the repository.
#
# F-Droid builds everything from source and refuses artifacts whose source it
# cannot verify. A committed .jar, .aar or .so is therefore not merely untidy,
# it gets the whole app rejected. It is also the easiest way for something
# nobody has read to end up inside a signed release.
#
# Usage: tools/ci/check-no-binaries.sh
set -euo pipefail

cd "$(dirname "$0")/../.."

# The Gradle wrapper is the one exception, and it is not really an exception:
# F-Droid knows this file, checks it against the published Gradle releases, and
# the project would not build without it.
ALLOWED='^gradle/wrapper/gradle-wrapper\.jar$'

FOUND="$(git ls-files \
    | grep -iE '\.(jar|aar|so|dex|apk|aab)$' \
    | grep -vE "$ALLOWED" || true)"

if [ -n "$FOUND" ]; then
    echo "Pre-compiled binaries are tracked in this repository:"
    printf '  %s\n' $FOUND
    cat <<'MESSAGE'

F-Droid builds from source and refuses artifacts it cannot verify, so these
would get the app rejected.

If you need a library, add it as a Gradle dependency so it is fetched from a
repository and recorded in the lock, rather than committing the compiled file.
MESSAGE
    exit 1
fi

echo "No pre-compiled binaries tracked."
