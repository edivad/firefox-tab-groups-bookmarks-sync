#!/usr/bin/env bash
set -euo pipefail

MANIFEST="src/main/resources/manifest.json"

usage() {
  echo "Usage: $0 [--minor] [--push] [VERSION]"
  echo ""
  echo "Bumps the extension version in $MANIFEST, commits 'Release X.Y.Z' and tags vX.Y.Z."
  echo ""
  echo "Options:"
  echo "  --minor   bump minor version (0.1.1 -> 0.2.0)"
  echo "  --push    push branch and tag automatically"
  echo "  VERSION   explicit new version (e.g. 0.2.0); defaults to patch bump"
  exit 1
}

version_gt() {
  IFS='.' read -r a1 a2 a3 <<< "$1"
  IFS='.' read -r b1 b2 b3 <<< "$2"
  if ((a1 > b1)); then return 0; fi
  if ((a1 < b1)); then return 1; fi
  if ((a2 > b2)); then return 0; fi
  if ((a2 < b2)); then return 1; fi
  if ((a3 > b3)); then return 0; fi
  return 1
}

PUSH=false
BUMP_MINOR=false
EXPLICIT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --minor) BUMP_MINOR=true ;;
    --push) PUSH=true ;;
    -h|--help) usage ;;
    *) if [[ -n "$EXPLICIT" ]]; then usage; fi; EXPLICIT="$1" ;;
  esac
  shift
done

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Working tree is not clean. Commit or stash changes first." >&2
  exit 1
fi

if [[ ! -f "$MANIFEST" ]]; then
  echo "Manifest not found: $MANIFEST" >&2
  exit 1
fi

CURRENT="$(jq -r '.version' "$MANIFEST")"
if ! [[ "$CURRENT" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Unexpected current version in manifest: $CURRENT" >&2
  exit 1
fi

if [[ -n "$EXPLICIT" ]]; then
  NEW="$EXPLICIT"
elif [[ "$BUMP_MINOR" == true ]]; then
  IFS='.' read -r major minor _ <<< "$CURRENT"
  NEW="$major.$((minor + 1)).0"
else
  IFS='.' read -r major minor patch <<< "$CURRENT"
  NEW="$major.$minor.$((patch + 1))"
fi

if ! [[ "$NEW" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Invalid version format (expected X.Y.Z): $NEW" >&2
  exit 1
fi

if ! version_gt "$NEW" "$CURRENT"; then
  echo "New version $NEW must be greater than current $CURRENT" >&2
  exit 1
fi

if git rev-parse -q --verify "refs/tags/v$NEW" >/dev/null; then
  echo "Tag v$NEW already exists" >&2
  exit 1
fi

jq --arg v "$NEW" '.version = $v' "$MANIFEST" > "$MANIFEST.tmp"
mv "$MANIFEST.tmp" "$MANIFEST"

git add "$MANIFEST"
git commit -m "Release $NEW"
git tag "v$NEW"

echo "Released $NEW (was $CURRENT)"

if [[ "$PUSH" == true ]]; then
  git push origin HEAD
  git push origin "v$NEW"
else
  echo "Run to publish:"
  echo "  git push origin HEAD && git push origin v$NEW"
fi
