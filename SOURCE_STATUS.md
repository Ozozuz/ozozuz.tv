# ozozuz.tv 0.10.2 — source snapshot

VersionName 0.10.2 / versionCode 19; installed package unchanged.

## Home-only domain resolution

The `.build/127.0.0.1:443` failure precedes HTTP. Verification confirmed that
`.build` returns HTTP 301 to `https://streamingcommunityz.boo/` when reachable.

- Only the home/domain bootstrap uses OkHttp with system DNS and an HTTPS DNS
  fallback when system lookup fails or returns unusable addresses such as loopback.
  TLS certificate and hostname verification remain enabled.
- After the final home is recognized, MainActivity persists its origin and passes
  it to the existing catalogue flows. Search, title pages, player discovery and
  images keep the previous HttpsURLConnection client and its host restrictions.
- No domain is guessed from a hardcoded fallback list. `.boo` is the new default
  for fresh installations; saved domains continue through the home redirect flow.
- The previous episode history and toolbar changes are retained.

## Verification — 2026-11-02

- `testDebugUnitTest`: 125 tests, zero failures/errors/skips.
- `assembleDebug`: successful; 0.10.2 / code 19; APK signature verified and matches the previously attached 0.10.1 APK.
- `lintDebug`: zero errors, 44 warnings.
- Live JVM check: configured DNS-over-HTTPS recovered two usable addresses from a simulated system loopback answer. The production home client started at `.build` and resolved `.boo` with three catalogue sections.
- Live search/title check with the unchanged client: South Park season 17 returned 10 episodes.
- Confirmed that SafeHttp code before the home-only `domainPage` transport is byte-for-byte identical to 0.10.1.


No physical-phone testing was performed. The CI/release process is unchanged.

---

## Historical 0.10.1 source snapshot

Gradle declares versionName 0.10.1 and versionCode 18. The installed package remains
`it.reozoz.streamclean.preview`.

## Changes

- Per-title playback history restores the last successfully opened episode and its season, including from favourites and across catalogue-domain changes. The last chronological opening wins, including earlier seasons and episodes. Browsing seasons, failed player requests and watched-checkbox changes do not replace this history.
- Activity recreation also preserves the season currently being browsed. Missing seasons fall back to the catalogue default; missing episodes retain their season without overwriting saved history.
- Home loading follows at most five HTTPS redirects, including HTTP redirects, HTML meta refresh and complete static JavaScript navigation statements. A new origin is persisted only after the existing home parser recognizes the final catalogue. Ordinary title, player and image requests retain their host restrictions. No JavaScript is evaluated during domain discovery.
- The online title toolbar no longer contains the rotation button. The provider's fullscreen control retains automatic landscape rotation.

## Verification — 2026-11-02

- `testDebugUnitTest`: 119 tests, no failures, errors or skipped tests.
- `assembleDebug`: successful; version 0.10.1 / code 18 and APK signature verified.
- `lintDebug`: no errors, 44 warnings.
- The production `SafeHttp.resolveHome` client successfully recognized `https://streamingcommunityz.boo/` and three catalogue sections in a live JVM check.
- JDK 17, Gradle 8.13 and Android SDK 35; Robolectric UI coverage on APIs 26 and 35. No physical-phone or Chromecast testing was performed.

If the old domain cannot be reached or displays an interactive challenge, automatic
migration cannot discover its destination; the manual domain field remains available.
Playback history begins with successful episode openings after this update: the old
watched booleans contain no chronological information to migrate.

The build remains debug-signed. Installing over an existing application while retaining
its data requires the same signing key. The CI/release process is unchanged.

---

## Historical 0.10.0 source snapshot

Previously recovered source tree. Gradle declares versionName 0.10.0 and versionCode 17.
Package remains it.reozoz.streamclean.preview; launcher label remains ozozuz.tv.

## Included changes

- Full-window online player without a fixed toolbar, transient exit button, immersive system bars and orientation restoration.
- PlaybackWindow manages FLAG_KEEP_SCREEN_ON and releases the playback-owned flag in background or on player close.
- Offline player hides its header in fullscreen and retains the attached video surface.
- Episode and season downloads start directly in private files/downloads/title-ID/season-N storage, with no file/folder picker.
- Completed files are exposed through a restricted FileProvider for playback/Cast and managed from the in-app Downloads screen.
- New PlaybackWindowTest and ManagedDownloadsTest cases are included.

## Verification status

Reviewed and built on 2026-10-27 with JDK 17, Gradle 8.13 and Android SDK 35:

- `testDebugUnitTest`: 89 tests, no failures, errors or skipped tests.
- `assembleDebug`: successful; APK version 0.10.0 / versionCode 17 and debug signature verified.
- `lintDebug`: no errors; 48 warnings remain, primarily UI/localization and maintenance suggestions.
- Two Robolectric fixtures were corrected: FileProvider initialization in the offline-player test, and recognition of the Android 13+ notification permission prompt in the season-download test.

No physical phone/Chromecast validation was performed. Existing results through 0.9.3 remain historical; the checks above validate this source snapshot's build and automated tests.

No prebuilt APK is checked into this source tree. The GitHub workflow builds a debug APK and publishes it with a SHA-256 checksum to Releases for each new application version on `main`. Successful builds also retain the APK as an Actions artifact. Signing keys, local configuration, build outputs and caches are excluded from the repository.

The GitHub workflow builds/tests/lints sources and verifies the built APK signature before uploading it. Android setup explicitly installs `platform-tools`, avoiding the obsolete `tools` package. The release job downloads that exact build artifact and runs `tools/publish-release.sh`, which generates the versioned APK filename and checksum, uses the version's release notes, and tags the tested source commit. Existing releases are left unchanged. A CI debug key cannot update an existing installation signed with a different key.

Build with JDK 17, Android SDK 35 and the included Gradle wrapper:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The publication review changed documentation, test fixtures and CI configuration. Application Java/resources retain the supplied 0.10.0 implementation.
