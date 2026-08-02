#!/usr/bin/env bash
set -euo pipefail

ICON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/src/main/resources/icons"
SIZES=(16 32 48 96 128)

if ! command -v rsvg-convert >/dev/null 2>&1; then
  echo "rsvg-convert not found. Install it with: brew install librsvg" >&2
  exit 1
fi

for s in "${SIZES[@]}"; do
  rsvg-convert -w "$s" -h "$s" "$ICON_DIR/icon.svg" -o "$ICON_DIR/icon-${s}.png"
done

echo "Generated PNG icons in $ICON_DIR"
