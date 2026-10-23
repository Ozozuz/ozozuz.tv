#!/usr/bin/env bash
# Called by CI only after the build/tests and prebuilt APK checks pass.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${GH_TOKEN:?GitHub token is required}"
: "${GH_REPO:?Set GH_REPO to owner/repository}"
: "${GITHUB_SHA:?The exact source commit is required}"
app_version="$(sed -nE "s/.*versionName '([^']+)'.*/\1/p" app/build.gradle)"
[[ "$app_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
release_tag="v$app_version"
release_notes="docs/releases/$release_tag.md"
[[ -f "$release_notes" ]]
apk_files=(apk/*.apk)
[[ ${#apk_files[@]} -eq 1 && -f "${apk_files[0]}" ]]
(cd apk && sha256sum -c SHA256SUMS)
if gh release view "$release_tag" >/dev/null 2>&1; then
  printf 'Release %s already exists; published assets are left unchanged.\n' "$release_tag"
  exit 0
fi
IFS= read -r release_title < "$release_notes"
release_title="${release_title#\# }"
gh release create "$release_tag" "${apk_files[0]}" apk/SHA256SUMS \
  --target "$GITHUB_SHA" --title "$release_title" \
  --notes-file "$release_notes"
