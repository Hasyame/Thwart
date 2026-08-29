#!/usr/bin/env bash
#
# Fails if the tag being released disagrees with the versionName in the build.
#
# F-Droid reads versions from tags, and Android installs updates by
# versionCode. A tag of v1.30.0 on a build that still says 1.29.0 produces a
# release that F-Droid records under one version and the phone reports as
# another, and the mismatch is invisible until somebody cannot update.
#
# Accepts a plain release tag (v1.30.0) or a pre-release (v1.30.0-beta.1): the
# suffix is stripped before comparing, since a beta of 1.30.0 is built from a
# tree that says 1.30.0.
#
# Usage: tools/ci/check-tag-matches-version.sh v1.30.0
set -euo pipefail

cd "$(dirname "$0")/../.."

TAG="${1:?usage: check-tag-matches-version.sh <tag>}"

# v1.30.0-beta.1 -> 1.30.0
TAG_VERSION="${TAG#v}"
TAG_VERSION="${TAG_VERSION%%-*}"

BUILD_VERSION="$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]+"' app/build.gradle.kts \
    | grep -oE '"[^"]+"' \
    | tr -d '"' \
    | head -1)"

if [ -z "$BUILD_VERSION" ]; then
    echo "Could not read versionName from app/build.gradle.kts."
    exit 1
fi

echo "tag $TAG implies version $TAG_VERSION; the build says $BUILD_VERSION."

if [ "$TAG_VERSION" != "$BUILD_VERSION" ]; then
    cat <<MESSAGE
These must match.

Either the tag is wrong, or versionName in app/build.gradle.kts was not bumped
before tagging. Fix whichever is wrong, delete the tag, and tag again.
MESSAGE
    exit 1
fi

echo "Tag and versionName agree."
