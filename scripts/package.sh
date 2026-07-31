#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:-$(git describe --tags --always 2>/dev/null || echo "0.1.0")}

echo "=== Running sbt packageDev ==="
sbt --client packageDev

echo "=== Creating extension ZIP ==="
cd dist
zip -r "../extension-${VERSION}.zip" .
cd ..

echo "=== Creating source ZIP ==="
zip -r "source-${VERSION}.zip" . \
  -x "dist/*" target/\* .git/\* .bsp/\* .idea/\* \
  -x "extension-*.zip" "source-*.zip"

echo ""
echo "Done: extension-${VERSION}.zip, source-${VERSION}.zip"
