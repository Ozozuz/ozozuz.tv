# ozozuz.tv 0.10.0 — source snapshot

Latest recovered source tree. Gradle declares versionName 0.10.0 and versionCode 17.
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

No prebuilt APK is checked into this source tree. A debug APK was generated during review; successful GitHub Actions runs also publish it as an artifact. Signing keys, local configuration, build outputs and caches are excluded from the repository.

The GitHub workflow builds/tests/lints sources and uploads the newly built debug APK. Android setup explicitly installs `platform-tools`, avoiding the obsolete `tools` package. Prebuilt-APK checks and automatic release publication remain disabled for this source-only snapshot. tools/publish-release.sh is retained but requires matching APK, checksum and release notes before use. A CI debug key cannot update an existing installation signed with a different key.

Build with JDK 17, Android SDK 35 and the included Gradle wrapper:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The publication review changed documentation, test fixtures and CI configuration. Application Java/resources retain the supplied 0.10.0 implementation.
