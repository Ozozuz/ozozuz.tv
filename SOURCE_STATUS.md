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

Source structure, XML, archive integrity and packaging were checked. This snapshot has NOT been compiled or run through Gradle tests in this session. No device/Chromecast validation was performed. Existing VALIDATION.md results through 0.9.3 are historical, not validation of 0.10.0.

No 0.10.0 APK is available in the recovered workspace. The older 0.9.3 APK and its checksum are deliberately omitted. Signing keys, local configuration, build outputs and caches are omitted.

The packaged GitHub workflow builds/tests sources and uploads the newly built debug APK; prebuilt-APK checks and automatic release publication are disabled for this source-only snapshot. tools/publish-release.sh is retained but requires matching APK, checksum and release notes before use. A CI debug key cannot update an existing installation signed with a different key.

Build with JDK 17, Android SDK 35 and the included Gradle wrapper:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

No application Java/resource files were changed while packaging this snapshot. README/release status and CI packaging metadata were updated to avoid claiming an available 0.10.0 APK.
