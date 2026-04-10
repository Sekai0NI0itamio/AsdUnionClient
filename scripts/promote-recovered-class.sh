#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  echo "Usage: $0 <fully.qualified.ClassName>" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REL_PATH="${1//./\/}.java"

PREFERRED_SRC="$ROOT_DIR/recovery/decompiled-grouped/$REL_PATH"
FALLBACK_SRC="$ROOT_DIR/recovery/decompiled-src/$REL_PATH"
DEST="$ROOT_DIR/src/main/java/$REL_PATH"

if [[ -f "$PREFERRED_SRC" ]]; then
  SRC="$PREFERRED_SRC"
elif [[ -f "$FALLBACK_SRC" ]]; then
  SRC="$FALLBACK_SRC"
else
  echo "Recovered source not found for $1" >&2
  echo "Checked:" >&2
  echo "  $PREFERRED_SRC" >&2
  echo "  $FALLBACK_SRC" >&2
  exit 1
fi

mkdir -p "$(dirname "$DEST")"
cp "$SRC" "$DEST"
echo "Copied $SRC -> $DEST"

