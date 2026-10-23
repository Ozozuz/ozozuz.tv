# Validation — 2026-10-22

- JDK 17 compilation of all application classes against the Android 35 platform JAR: passed.
- JVM parser tests: **7 passed** (HTML entity decoding, sections, Italian plot, hostile image filename/CDN/banner ignored, optional fields, challenge/missing schema errors, HTTPS URL validation).
- Parser integration against HTML fetched from the supplied live home: **3 sections**, **30 + 30 + 10 titles**, **70/70 poster references extracted**. The downloaded HTML is not bundled in the project.
- An extracted poster URL responded HTTP 200 with Content-Type image/webp.
- Reproducible standalone SDK build using `tools/build-sdk.sh`: **passed**, including pinned jsoup checksum verification, Java compilation, resource packaging, D8 conversion, zip alignment and APK signing.
- APK signature verification: **v2 and v3 passed**, one development signer. Package `it.reozoz.streamclean`, version 0.1.0, minimum API 26, target API 35.
- Gradle wrapper generation: passed. Full Gradle build was attempted but this environment could not resolve the Android Gradle plugin from its repositories; the delivered APK was built using the verified standalone SDK path instead. The Gradle build configuration and GitHub Actions workflow are provided but were not successfully executed end-to-end here.
- Follow-up on Android 8.0 x86_64 software emulator: APK installation and activity startup passed. No AndroidRuntime crashes were recorded. Default margins and initial soft keyboard behavior were corrected and visually verified.
- Instrumentation checks on the Android 8 emulator, with a previously downloaded real home HTML fixture: Android-side parser, native sections, Film and TV filters, matching/empty local filtering, details dialog and invalid HTTP URL rejection passed on the pre-catalogue-search version. Live networking in the emulator failed DNS resolution; live poster loading was not verified there.

## Catalogue search fix — version 0.2.0

- The original search filtered only the 70 home cards. It now requests the site's actual catalogue search route `/it/search?q=...` using the selected origin, with a 400 ms debounce and stale-response rejection.
- `GET https://streamingcommunityz.build/it/search?q=South%20Park` returned HTTP 200. The new parser extracted the series **South Park**, ID **4800**, type **tv**, including its poster and plot. This response contained 60 results out of a total of 62.
- JVM tests: **10 passed**, including catalogue responses, empty results and missing-schema failure.
- Version 0.2.0 compiled and signed successfully using `tools/build-sdk.sh` (versionCode 2). No new emulator run was performed after the user requested that the slow emulator testing stop.
- Search currently displays the first server results page, with loaded/total counts; pagination remains unimplemented.


## Title navigation and embedded playback — version 0.3.0

- Full native title screen added, shared by home and catalogue search. Series use real season routes and episode IDs; films use the direct watch route.
- Actual South Park detail HTML parsed: **29 seasons**, **13 episodes in season 1**, **18 in season 2**. The movie detail response was also parsed successfully.
- Actual watch response for title 4800 / episode 28566 matched the requested episode. Its official iframe URL was extracted while ignoring the advertising script in the surrounding HTML.
- A newly requested iframe produced an official vixcloud embed endpoint that responded **HTTP 200**. Tokens are short-lived; the app requests fresh frame data for each playback attempt and provides a reload action.
- Provider HTML and JavaScript inspected: JW Player resources are hosted on vixcloud.cc; observed media/thumbnail CDN is vix-content.net. The native resource policy allows those hosts and blocks unrelated scripts.
- JVM regression tests: **16 passed**, covering catalogue/search, seasons/episodes, movies, watch episode identity, iframe host validation, advertising exclusion and player resource/navigation policy.
- APK compiled and v2/v3 signature verified, versionCode **3**, versionName **0.3.0**. Same development key as earlier versions.
- No new emulator run was performed. Full video decoding, audio, fullscreen and device-specific WebView behavior have not been verified on an Android device. HTTP 200 from the embed is not an end-to-end playback test.


## HLS playback regression fix — version 0.3.1

- Reproduced the filter failure from the reported South Park S11E2 episode (episode ID 28759). Provider embed, HLS master and audio rendition each returned HTTP 200.
- The actual rendition declares 167 audio fragments at a rotating CDN, `sc-u1-01.blackpuma78.boats`, under `/hls/.../audio/ita/*.html`. Every fragment was rejected by the previous fixed-domain/extension policy.
- Added a per-playback allowlist built from actual provider HLS responses, including alternate audio and variant playlists. Registered URLs retain their query parameters and are checked individually, independent of file suffix or changing CDN hostname.
- Playlist responses retain real server CORS headers; no CORS or TLS checks are disabled. Direct HEAD of a declared fragment returned HTTP 200, Content-Type text/html, Access-Control-Allow-Origin: *. The fragment payload itself was not downloaded for this check.
- Integration regression against the downloaded real master/rendition: **167/167** formerly blocked audio URLs now accepted; an unrelated advertising script remains rejected.
- JVM regression tests: **20 passed**. Added tests cover HTML-suffix chunks, rotating hosts, exact query matching, relative variants/chunks, separate audio, unauthorized manifests, unsafe URL schemes/credentials/ports, and reset between playback sessions.
- APK compiled and v2/v3 signatures verified, versionCode 4 / versionName 0.3.1. No emulator was started; actual playback/decoding still needs device confirmation.


## Per-title background artwork — version 0.4.0

- User confirmed that actual video playback works in version 0.3.1. This release changes title-page presentation only; the playback implementation is preserved.
- Title detail parsing now selects the official background image; cover, mobile cover and poster are used as fallbacks when that type is absent. Only validated image filenames are accepted and fetched from the selected site's exact CDN host.
- The selected South Park image was downloaded and inspected: the official 1600×900 background matches the user's supplied reference. Real detail HTML selects an artwork filename successfully.
- Added a fixed native background layer, proportional center crop, dark veil and vertical gradient. Episode list backgrounds are transparent, with a translucent selected-row highlight and darker button surfaces.
- Artwork is loaded/decoded off the UI thread with bounded dimensions and an 8 MiB memory cache; season changes reuse the same image. Network or decoding failure leaves the dark fallback and does not block browsing/player access.
- Existing JVM regression suite: **20 passed**. All Android classes compiled and APK signing verification passed, versionCode 5 / versionName 0.4.0. No emulator was started; final appearance should be checked on the user's device.


## Visual refresh — version 0.5.0

- Retained official per-title background artwork with dark scrim and gradient; native text, cards, buttons and inputs now share a consistent dark/mint visual style.
- Replaced the platform season spinner with a custom bottom panel: numbered grid, selected-season highlight, scroll to the current season, close and outside-tap dismissal. Episodes are selected directly from native cards, without an additional stock picker.
- Added real episode preview filenames from season data; filenames are validated before an exact CDN request. Thumbnails load off the UI thread, share an 8 MiB cache, and check the target view's URL before applying a bitmap to recycled rows. Missing images keep a numbered placeholder.
- Real previously downloaded South Park responses yielded thumbnails for **13/13 season 1 episodes** and **18/18 season 2 episodes**. The first episode's preview was requested separately from the live CDN and inspected.
- JVM regression suite: **21 passed**, including a new parser case for episode image selection, Italian-language preference, missing images and unsafe/external image filenames.
- Complete Android SDK compilation, packaging and v2/v3 APK signature verification passed for versionCode **6** / versionName **0.5.0**, using the same development signing key.
- Player resource policy, HLS allowlist and embedded player implementation are preserved. The reported player control overlap (request point 3) is deferred as requested; the series player remains 180 dp high.
- No emulator was started. On-device visual layout and the custom season panel remain to be checked on the user's phone; this iteration was verified through compilation, parser/data checks and regression tests.


## Player control clearance — version 0.5.1

- The supplied playback screenshot shows the provider's progress bar crossing its central pause and +/-10 second controls. Series playback previously used a fixed 180 dp container.
- Replaced that fixed height with 16:9 of the actual available width plus 56 dp of control clearance, with a 260 dp minimum. It is recalculated when playback starts or the root width changes. Film playback retains its existing larger remaining-height area.
- Full Android 35 SDK compilation, packaging, alignment and v2/v3 signature verification passed, versionCode 7 / versionName 0.5.1. Same development signing key and package identity for installation over the previous version.
- No changes to embedded provider code, HLS/resource policy, title parsing, season navigation or episode thumbnails. Existing JVM regression suite remains passing (21 tests).
- No emulator or device playback/touch test was performed. The overlap correction needs confirmation on the user's phone.


## Reference-based visual redesign — version 0.6.0

- Implemented the style from the three user-supplied screenshots: near-black background, graphite panels, fine neutral borders, small corner radii, monospaced headings/controls, green accents and bracketed active navigation. Removed large pill shapes, heading shadows, oversized branding and marketing footer copy.
- Restyled catalogue, featured title, search/URL inputs, filters, detail screen, episode previews, selected episode, playback buttons and season grid. Plot text remains sans-serif for reading. Selected controls expose native selected state for accessibility.
- Moved the home URL editor behind the visible `dominio` control; the selected host stays visible in the status line, and a loading or URL error reopens the address editor. Existing fetch/search routes and origin persistence are retained.
- Title artwork remains specific to each film/series, but is desaturated and subdued behind the neutral interface. Preview thumbnails and posters retain their original images.
- Player height calculation and the working embedded playback implementation remain unchanged from 0.5.1.
- Full Android SDK compilation, packaging and v2/v3 signature verification passed, versionCode 8 / versionName 0.6.0. Existing 21 JVM regression tests passed. No emulator or on-device visual/touch verification was performed.


## Repository preparation — ozozuz.tv

- Repository name and README follow the author's supplied sarcastic English README references. Android app name, package, version, source behaviour and the existing 0.6.0 APK are unchanged.
- Added a native SVG banner, build/implementation documentation, release notes, and the exact existing APK with its SHA-256 checksum.
- Excluded local signing keys from Git; the standalone SDK build creates a local debug key if none exists, and Gradle reuses a local key when available or its normal user-local debug key otherwise.
- CI configuration builds/tests the app and verifies the checked-in APK before creating a version release. The workflow has not yet run on GitHub; local SDK build, APK signature and regression-test results are recorded above.
- Repository visibility is to remain private, per the author's explicit request. No repository or release was created during local preparation.


## Branding, launcher icon and home links — version 0.6.1

- App/launcher label and home wordmark now read `ozozuz.wrapper`; package ID and development signing key are unchanged for installation over earlier builds.
- Added exactly three fixed home-footer buttons: GitHub profile, X profile, and the planned private ozozuz.tv repository. All use explicit browser intents on tap; no link is opened automatically. The footer hides for nonempty search queries and is not added to the title/player activity.
- Generated original adult succubus art and edited the exact user-selected image to blonde hair/eyebrows and punk styling. Added 48/72/96/144/192 px launcher PNGs and an adaptive icon with a separate graphite background and padded transparent foreground.
- Full Android SDK compilation, alignment and v2/v3 APK signature checks passed for versionName 0.6.1 / versionCode 9. Existing 21 JVM regression tests passed. Launcher label/icon resources were checked in the packaged manifest; no emulator or phone test was performed.
- Repository publication remains pending; no GitHub repository was created or pushed during this change.


## Final naming — version 0.6.2

- User-selected final app and repository name: `ozozuz.tv`; Android label, home wordmark, project name, README, banner, download filename and Repository footer link updated together.
- The wordmark accent span now uses the correct 7–9 bounds for the shorter name. Package ID, signing key, icon artwork and playback implementation remain unchanged.
- VersionCode 10 / versionName 0.6.2. Android SDK compilation, v2/v3 signing checks and all 21 JVM regression tests passed. No emulator was started.


## Watched episodes and MP4 downloads — version 0.7.0

- Added manual watched flags in app-private SharedPreferences, keyed by stable title/episode IDs. The player checkbox and thumbnail badge share the same store; no remote sync or automatic marking on Play.
- Added explicit SAF destination selection, one foreground download with progress/cancellation, a two-hour bounded wake lock, cleanup of partial output and temporary tracks, and native VideoView playback of the completed MP4.
- Download uses a snapshot of the exact master manifest already accepted by the embedded player. Existing WebView restrictions and exact-resource HLS allowlist remain in place. Selects quality up to 720p and Italian/default audio, handles ordinary AES-128 and byte ranges, and remuxes supported tracks with Android MediaExtractor/MediaMuxer. Unsupported live/discontinuous/DRM formats fail explicitly.
- All **32 JVM tests passed**, including 11 new cases for quality/audio selection, exact segment URLs, init/ranges, AES sequence/explicit IV and rejected formats.
- Real provider master and renditions were retrieved: 334 video segments and 334 audio segments, AES-128, no discontinuities or init-map changes. Only the first video/audio segments were downloaded and decrypted for inspection. ffprobe identified H.264 video and AAC audio. This is not an end-to-end full episode download test.
- Complete Android SDK 35 build and v2/v3 signature verification passed for versionCode 11 / versionName 0.7.0. No emulator was started and no on-device watched/download/local-playback UI test was performed.
