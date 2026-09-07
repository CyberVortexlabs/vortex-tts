#!/bin/sh
set -eu

APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.7
DIST_DIR="$APP_DIR/.gradle-local"
DIST_ZIP="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
DIST_HOME="$DIST_DIR/gradle-${GRADLE_VERSION}"
GRADLE_BIN="$DIST_HOME/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$DIST_DIR"
  if [ ! -f "$DIST_ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 --connect-timeout 15 "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$DIST_ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$DIST_ZIP" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    else
      echo "curl or wget is required to bootstrap Gradle ${GRADLE_VERSION}." >&2
      exit 1
    fi
  fi
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required to bootstrap Gradle." >&2; exit 1; }
  unzip -q -o "$DIST_ZIP" -d "$DIST_DIR"
fi

exec "$GRADLE_BIN" "$@"
