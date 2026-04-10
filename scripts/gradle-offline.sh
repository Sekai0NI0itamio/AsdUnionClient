#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_VERSION="${GRADLE_VERSION:-2.14.1}"

LOCAL_GRADLE_HOME="$ROOT_DIR/.gradle-dist/gradle-$GRADLE_VERSION"
SHARED_ROOT="$ROOT_DIR/../../AsdUnionClient"
SHARED_GRADLE_HOME="$SHARED_ROOT/.gradle-dist/gradle-$GRADLE_VERSION"

LOCAL_GRADLE_USER_HOME="$ROOT_DIR/.gradle-user-home"
SHARED_GRADLE_USER_HOME="$SHARED_ROOT/.gradle-user-home"

if [[ -d "$LOCAL_GRADLE_HOME" ]]; then
  GRADLE_HOME="$LOCAL_GRADLE_HOME"
elif [[ -d "$SHARED_GRADLE_HOME" ]]; then
  GRADLE_HOME="$SHARED_GRADLE_HOME"
else
  echo "Missing Gradle $GRADLE_VERSION. Expected one of:" >&2
  echo "  $LOCAL_GRADLE_HOME" >&2
  echo "  $SHARED_GRADLE_HOME" >&2
  exit 1
fi

if [[ -d "$LOCAL_GRADLE_USER_HOME" ]]; then
  GRADLE_USER_HOME_DIR="$LOCAL_GRADLE_USER_HOME"
elif [[ -d "$SHARED_GRADLE_USER_HOME" ]]; then
  GRADLE_USER_HOME_DIR="$SHARED_GRADLE_USER_HOME"
else
  mkdir -p "$LOCAL_GRADLE_USER_HOME"
  GRADLE_USER_HOME_DIR="$LOCAL_GRADLE_USER_HOME"
fi

exec arch -x86_64 /usr/bin/java \
  -Dorg.gradle.native=false \
  -cp "$GRADLE_HOME/lib/*" \
  org.gradle.launcher.GradleMain \
  -g "$GRADLE_USER_HOME_DIR" \
  --offline \
  --no-daemon \
  "$@"

