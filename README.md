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

## What It Does

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

Requires **Android 8.0 / API 26 or later**. Current version: **0.7.0**.

Download [ozozuz-tv-v0.7.0.apk](apk/ozozuz-tv-v0.7.0.apk), open it on your phone,
and allow installation from the app you used to download it when Android asks.

The installed app is called **ozozuz.tv**. It keeps package ID
`it.reozoz.streamclean` and the existing development signer, so it updates the
previous StreamClean builds. The repository and the installed app finally have
the same name. Naming things briefly threatened to become a separate project.

The launcher icon is a blonde punk succubus. The app supervises my clicks;
the branding department remains unsupervised.

[APK SHA-256](apk/SHA256SUMS) · [Release notes](docs/releases/v0.7.0.md)

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
6. For a local copy, press Play once, then **Scarica ↓**, and choose a local
   folder. Progress and cancellation appear in the app and download notification.
   When finished, **Apri MP4** plays the saved file inside the app.
7. Watch the episode. This was the original requirement. Somehow it took software.

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

- Search shows the first server results page, currently up to 60 items. Loaded
  and total counts are displayed when available; pagination is not implemented.
- No account support, domain discovery or challenge bypass.
- Downloads run one at a time, prefer the best quality up to 720p and Italian
  audio when available (otherwise the default track). Only video and audio are
  exported; subtitles are not included. Interrupted jobs restart from the beginning.
- MP4 export supports finite HLS with H.264/H.265 and AAC, including ordinary
  AES-128 segments. DRM, SAMPLE-AES and discontinuities are not supported.
- Previous/next episode navigation stays inside the selected season.
- An absent thumbnail keeps a placeholder. An absent video remains absent.
  The app has not been taught necromancy.
- Provider-specific playback, audio, subtitles and quality controls depend on the
  embedded player and the device's WebView. Provider changes can break things.
- Playback has been confirmed on the author's phone during development. The new
  watched/download features compile and have parser coverage, but the full
  Android download and local playback flow still needs a phone check.

## Build

Use **JDK 17**, **Android SDK 35**, and the included **Gradle 8.13** wrapper:

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The standalone SDK path has also been verified:

```bash
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk ./tools/build-sdk.sh
```

It requires build-tools **35.0.0**, Python 3, curl and sha256sum, and writes
`build/sdk/StreamClean.apk`. The script creates a local development signing key
on the first build if one is missing; private signing files are not committed.
Your locally built APK may use a different signer from the prebuilt download and
therefore cannot be installed over it. This is Android doing its job for once.

The Android workflow builds and tests pushes and pull requests. After those checks pass on `main`, the workflow publishes the checked-in APK
and its checksum if that version does not already have a release. Existing
release assets are left unchanged. Both repository contents and releases remain private while the repository
is private.

## Under the Bonnet

| Part | Job |
| --- | --- |
| `HomeParser` / `TitleParser` | Read catalogue, search, titles, seasons and episodes |
| `MainActivity` / `TitleActivity` | Native navigation and presentation |
| `TitleBackdrop` / `ThumbnailLoader` | Bounded image loading and caches |
| `EmbeddedPlayer` / `PlayerPolicy` | Embedded playback and resource/navigation restrictions |
| `MediaAllowlist` / `PlaylistHttp` | Per-playback HLS URL scope and playlist forwarding |
| `WatchedEpisodes` | Local manual watched flags, keyed by title and episode IDs |
| `HlsDownloadPlan` / `EpisodeDownloadEngine` | Select audio/video, download segments and remux MP4 |
| `EpisodeDownloadService` / `DownloadedVideoActivity` | Background progress, cancellation and local playback |

The **32 JVM tests** cover parsing, catalogue search, episode identity, image
filename validation, player policy, HLS URL handling, download track selection, ranges and AES IVs. They do not certify the
behaviour of every future advert somebody decides to attach to a television show.

[Android implementation notes](docs/ANDROID.md) · [Validation history](VALIDATION.md)

## Why Does This Exist?

Because my professional threat model did not account for my own thumb.

I could have called it a "hardened media consumption gateway" and put it on my CV.
Unfortunately, we all know I just wanted to watch South Park without accidentally
opening a second career in online gambling.
