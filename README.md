> **0.10.0 source snapshot (versionCode 17).** Includes the fullscreen/wake-state and private in-app download changes. No prebuilt 0.10.0 APK is included; build/tests for these changes are pending. See [SOURCE_STATUS.md](SOURCE_STATUS.md).

# ozozuz.tv

**An Android streaming client for when the weakest link is your own thumb.**

I work in cybersecurity. I can explain the attack chain, identify the suspicious
redirect, and tell you exactly why you should not click that button.

Then I try to watch a TV series and lose a tactical engagement against three
fake Play buttons and an advert telling me I have won an iPhone.

```text
Volevo solo guardare una puntata.
Ho aperto un casinò.
```

So I built an app. Growing older gracefully was apparently not in the backlog.

![ozozuz.tv — the threat model is me](assets/ozozuz-tv.svg)

<img src="assets/mascot-icon-v2.png" alt="ozozuz.tv mascot" width="180">

## What It Does

- Forces landscape playback even with Android auto-rotate disabled. My neck is
  no longer part of the video decoder.
- Casts the selected online video or downloaded MP4 to a Google Cast receiver,
  with device selection and native TV controls. The television has been promoted.
- Saves films and series in **★ preferiti**, searchable locally and stable across
  catalogue-domain changes. Outsourcing my memory now has a dedicated tab.
- Downloads a whole selected season sequentially, skips existing files and keeps
  an interrupted queue ready to resume. Temporary links are fetched per episode.
- Manages completed files in **↓ download**: grouped by series/season, offline
  playback, watched marks, file sizes and deletion. The file manager is off duty.
- Reads the catalogue and renders a native Android interface. The surrounding
  website does not get to bring its entire advertising department.
- Searches the actual catalogue, including titles that are not on the homepage.
  A search engine that searches things. We are healing.
- Opens films and TV series from both the home screen and search results.
- Gives seasons a numbered grid and episodes their own thumbnail, title and plot.
  Choosing episode 2 should not require the dexterity of a bomb disposal unit.
- Embeds the site's video player, with previous/next episode controls and a way
  to reload short-lived links when they expire before my attention span does.
- Blocks external windows, downloads and navigation away from the embedded player.
  My thumb has been placed on a performance improvement plan.
- Marks episodes as **Già vista**, stored only on the device, with a small tick
  on their thumbnails. My memory has been assigned persistent storage.
- Downloads episodes as MP4 with audio, progress, cancellation and local playback.
  One explicit button, zero external download-page adventures.
- Uses a restrained black/graphite interface with monospaced headings, fine
  borders and green accents. The series artwork stays in the background, where
  backgrounds traditionally belong.
- Lets you change the HTTPS catalogue domain from **dominio**, because apparently
  even the website cannot commit to an address.

The home footer links to [GitHub](https://github.com/Ozozuz),
[X](https://x.com/0zozuz) and this repository. These open your normal browser,
only after you tap them. They are absent from search results and the player.

## Install

Requires **Android 8.0 / API 26 or later**. This archive contains **0.10.0 sources**, versionCode **17**, and no prebuilt APK. Build instructions are below. The launcher label is **ozozuz.tv**; the installed package remains `it.reozoz.streamclean.preview` for continuity with 0.8/0.9.

Updating an existing installation requires the same signing certificate. Private signing material is excluded from this archive.

## Use

1. Open the app. If the catalogue domain has moved, open **dominio**, paste the
   current HTTPS address and tap **Carica**.
2. Browse the home sections or search by title. The **tutti / film / serie tv**
   filters apply to the current results.
3. Open a series, choose its season and tap an episode. For a film, tap
   **Guarda film**.
4. Press Play in the embedded player. **Prec. / Succ.** move inside the selected
   season; **Ricarica** requests a fresh player link.
5. Use **Già vista** below the player to mark or unmark the episode. The tick
   stays on its thumbnail after closing the app and changing catalogue domains.
6. For a local copy, press Play once, then **Scarica ↓**. Files are saved directly
   in private app storage. Progress and cancellation appear in the app and download notification.
   When finished, **Apri MP4** plays the saved file inside the app.
7. Watch the episode. This was the original requirement. Somehow it took software.

Use **★ preferiti** on the home screen to access saved films/series. The small star
on a title card or the open detail page adds/removes it; it turns green when saved. Search and type filters also
work on the saved list.

Use **↓ stagione** next to the season selector to start the managed season queue.
The app saves one MP4 per available episode under its private `files/downloads/` folder, grouped by title and season; no destination picker is opened.
Completed files are skipped; a process interruption leaves the remaining queue
for **↓ riprendi**. Cancellation keeps finished files and discards the queue.
Failed episodes can be retried by starting that season again.

Open **↓ download** from home to browse and play saved episodes without internet
or access to the catalogue. Files are grouped by series/season, show their size
and watched status, and can be deleted individually. Existing 0.7.0 file records
are recognized, with title/episode metadata recovered from their filenames.
If a file was moved or deleted externally, the list shows it as unavailable.

Tap the **landscape icon** in either player to force landscape. It works even when Android
auto-rotate is locked; Back restores the previous orientation. Online playback
uses an immersive full-window player without reloading the page. Playback keeps the screen awake while the player is foregrounded and releases the playback-owned wake flag on background/close.

Tap the **Cast icon** to choose a Chromecast or Google Cast-enabled TV. Selecting
a device from a video page sends the current video; **TV ▶** appears when a device is connected and sends the currently
selected episode. The landscape icon appears while the local player is open. The mini controller and Cast
notification provide pause/seek/volume controls. Disconnect using Cast or tap
**Interrompi** in the relay notification. Google Play Services and the same
IPv4 Wi-Fi network are required. The phone relays this one video and must stay
connected, including for downloaded MP4s. Playback is native on the receiver;
there is no transcoding, and unsupported codecs/DRM are not converted.

Long-press the Cast icon to open Android screen-cast settings, where supported
by the phone. This is a separate screen-mirroring fallback. AirPlay and DLNA
receivers are not implemented as native video targets.

Back leaves fullscreen first, then closes the player, then returns to the catalogue.

## How It Avoids My Usual Mistakes

The catalogue is fetched over HTTPS and parsed with jsoup. The app reads the
page's structured data and builds native views; it does not execute the scripts
from the catalogue, search or title pages.

The WebView is reserved for the embedded video player. It has no JavaScript/Java
bridge, file/content access, device permissions, download support or permission
to open extra windows. JavaScript remains enabled there because the actual player
needs it, which is inconvenient but less inconvenient than inventing video.

Media URLs are accepted from the provider's HLS playlists **one exact URL at a
time**, including their query strings. Rotating CDN names and fragments ending
in `.html` work without giving every other script on the internet a visitor badge.
TLS and server CORS checks stay in place.

This reduces the clutter and exposure; it does not turn an untrusted provider
into a trusted one. The site and CDN still receive normal network requests,
including your IP address. My clicking privileges were reduced, not my need to
understand what the app connects to.

## Current Limitations

- Chromecast integration and forced rotation require a final test on physical
  hardware. Startup/controller lifecycle is covered by Robolectric on API 26/35;
  no physical phone/Chromecast test was performed for 0.9.2.
- Search shows the first server results page, currently up to 60 items. Loaded
  and total counts are displayed when available; pagination is not implemented.
- No account support, domain discovery or challenge bypass.
- Downloads run one at a time, prefer the best quality up to 720p and Italian
  audio when available (otherwise the default track). Only video and audio are
  exported; subtitles are not included. An interrupted episode restarts; completed episodes in a season queue are retained.
- MP4 export supports finite HLS with H.264/H.265 and AAC, including ordinary
  AES-128 segments. DRM, SAMPLE-AES and discontinuities are not supported.
- Previous/next episode navigation stays inside the selected season.
- An absent thumbnail keeps a placeholder. An absent video remains absent.
  The app has not been taught necromancy.
- Provider-specific playback, audio, subtitles and quality controls depend on the
  embedded player and the device's WebView. Provider changes can break things.
- Playback has been confirmed on the author's phone during development. The new
  watched/download features compile and have parser coverage, but the full
  Android season queue and download-library flow still needs a phone check.

## Build

Use **JDK 17**, **Android SDK 35**, and the included **Gradle 8.13** wrapper:

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The compatibility script now delegates to Gradle so AndroidX and Cast AAR
resources and manifests are merged correctly:

```bash
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk ./tools/build-sdk.sh
```

It also writes `build/sdk/StreamClean.apk`. Gradle reuses `debug.keystore` in the
project root when present, otherwise it uses its user-local development key.
Private signing files are excluded from Git and source archives. The previously delivered
0.9.3 APK reused the 0.8/0.9 signer and installed package while keeping its data. The original 0.7.0 signer was lost during workspace cleanup;
that installation remains separate. Keep the current key backed up privately.
Different signing keys cannot update each other. Android doing its job for once.

The packaged Android workflow builds and tests pushes and pull requests, then uploads the newly built debug APK as an Actions artifact. Automatic release publication is disabled in this source-only snapshot because no matching prebuilt 0.10.0 APK is included.

## Under the Bonnet

| Part | Job |
| --- | --- |
| `HomeParser` / `TitleParser` | Read catalogue, search, titles, seasons and episodes |
| `MainActivity` / `TitleActivity` | Native navigation and presentation |
| `TitleBackdrop` / `ThumbnailLoader` | Bounded image loading and caches |
| `EmbeddedPlayer` / `PlayerPolicy` | Embedded playback and resource/navigation restrictions |
| `MediaAllowlist` / `PlaylistHttp` | Per-playback HLS URL scope and playlist forwarding |
| `PlaybackRotation` | App-local landscape request and restoration |
| `CastUi` / `CastRelayService` | Google Cast discovery, controls and temporary receiver-only Wi-Fi media relay |
| `WatchedEpisodes` | Local manual watched flags, keyed by title and episode IDs |
| `HlsDownloadPlan` / `EpisodeDownloadEngine` | Select audio/video, download segments and remux MP4 |
| `EpisodeDownloadService` / `DownloadedVideoActivity` | Background progress, cancellation and local playback |

Historical **0.9.3 validation recorded 73 tests** covering parsing, catalogue search, episode identity, image
filename validation, player policy, HLS URL handling, download track selection, ranges, AES IVs, Cast playlist rewriting and the receiver-only HTTP relay.
Robolectric startup/controller tests exercise Android 8 and 15 resources,
activity resume/recreation, the SDK controller initialization regression,
small-screen toolbar bounds and favorite toggling from an open title page.
Only the remote-TV binding is replaced in controller-view tests. They do not certify the
behaviour of every future advert somebody decides to attach to a television show.

[Android implementation notes](docs/ANDROID.md) · [Validation history](VALIDATION.md)

## Why Does This Exist?

Because my professional threat model did not account for my own thumb.

I could have called it a "hardened media consumption gateway" and put it on my CV.
Unfortunately, we all know I just wanted to watch South Park without accidentally
opening a second career in online gambling.
