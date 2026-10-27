#!/usr/bin/env bash
# Publish the exact APK artifact produced by the successful build job.
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
if gh release view "$release_tag" >/dev/null 2>&1; then
  printf 'Release %s already exists; published assets are left unchanged.\n' "$release_tag"
  exit 0
fi
apk_source="app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$apk_source" ]] || { printf 'Built APK not found: %s\n' "$apk_source" >&2; exit 1; }
publish_dir="build/release"
apk_name="ozozuz-tv-$release_tag.apk"
mkdir -p "$publish_dir"
cp "$apk_source" "$publish_dir/$apk_name"
(cd "$publish_dir" && sha256sum "$apk_name" > SHA256SUMS && sha256sum -c SHA256SUMS)
IFS= read -r release_title < "$release_notes"
release_title="${release_title#\# }"
gh release create "$release_tag" "$publish_dir/$apk_name" "$publish_dir/SHA256SUMS" \
  --target "$GITHUB_SHA" --title "$release_title" \
  --notes-file "$release_notes"
