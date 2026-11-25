# Xtream TV for Android

An Android port of the macOS **Xtream TV** app from [`vanderck/iptv`](https://github.com/vanderck/iptv),
built for the Samsung Galaxy S23 (works on any phone running Android 8 or newer).

It signs in to an Xtream Codes provider and shows Live TV, Movies and Series in
the same dark, Apple TV–style look as the Mac app.

## Features

- **Sign in** with server, username and password, or paste a whole
  `…/get.php?username=…&password=…` link and the details are filled in for you.
  The password is stored encrypted with the Android Keystore.
- **Home**: a hero carousel of the newest movies and series, followed by the
  Up Next, Favorites, New Movies, New Series and Live Channels shelves.
- **Live TV**: channel groups as chips (All, Favorites, Recent, then each
  provider category), instant search, and a one-tap button to resume the last
  channel you watched.
- **Movies and Series**: one shelf per category (tap a shelf title to see all
  of it as a grid), detail pages with artwork, plot, cast and rating, a season
  picker and episode list, and **resume where you left off**.
- **Search** across all three catalogs at once, ignoring case and accents.
- **Favorites**: long-press any channel, movie or series, or tap the star.
- **Player** (ExoPlayer / Media3):
  - Live TV: a banner showing what's on now (with progress) and next from the
    provider's EPG. Swipe up or down, or use the arrows, to change channel
    within the group. The ☰ button opens a channel list, and double-tap zooms
    to fill the screen.
  - Movies and episodes: a scrubber with ±10 s/30 s skips, plus audio-track
    and subtitle selection.
  - **Picture-in-picture**: press the PiP button, or just go Home while
    something is playing (can be switched off in Settings).
- **Settings**: account status, expiry date and connections, live stream
  format (Automatic / MPEG-TS / HLS), reload library, and sign out.

Unlike macOS, Android's ExoPlayer plays raw MPEG-TS directly, so there is no
need for mpv. In *Automatic* mode, live channels are requested as `.ts` and
fall back to the panel's HLS (`.m3u8`) if a stream won't start. Movies play in
their own container (MP4, MKV, …).

## Installing on the S23

Every push is built by GitHub Actions (`.github/workflows/build.yml`).

1. Merge to `main`. The workflow then publishes a **Latest build** release
   containing `XtreamTV.apk`. Open
   `https://github.com/vanderck/iptv-s23/releases/latest-download/XtreamTV.apk`
   on the phone.
   Builds from other branches are under **Actions → the run → Artifacts**, as
   a zip containing the APK.
2. When Android asks, allow your browser (or My Files) to **install unknown apps**.
3. Open the APK and tap **Install**.

Pushing a tag such as `v1.0` creates a versioned release with the APK attached.

### Stable signing (recommended)

Android only installs an update over an existing app when both are signed with
the same key. Without secrets, CI signs with a debug key that it keeps in its
cache. That cache expires after a week without builds, and after that you would
need to uninstall before installing again (favorites and resume points would be
lost). To use your own permanent key, run this once on your Mac:

```bash
keytool -genkeypair -v -keystore xtreamtv.jks -alias xtreamtv -keyalg RSA -keysize 2048 -validity 10000
```

```bash
base64 -i xtreamtv.jks | pbcopy
```

Then add these repository secrets (**Settings → Secrets and variables → Actions**):

| Secret | Value |
| --- | --- |
| `XTREAM_KEYSTORE_BASE64` | the base64 text you just copied |
| `XTREAM_KEYSTORE_PASSWORD` | the keystore password |
| `XTREAM_KEY_ALIAS` | `xtreamtv` |
| `XTREAM_KEY_PASSWORD` | the key password |

Keep `xtreamtv.jks` somewhere safe and never commit it (`.gitignore` excludes `*.jks`).
After switching keys, uninstall the old build once.

## Building locally

Requires JDK 17+ and the Android SDK (Android Studio installs both).

```bash
./gradlew testDebugUnitTest assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Layout

```
app/src/main/java/com/vanderck/xtreamtv/
  data/      Xtream Codes client and lenient JSON models (ported from M3UKit/Xtream),
             TvModel (app state), ShelfBuilder, Store (settings, favorites, resume points)
  ui/        Compose screens: sign-in, Home, Live TV, Movies/Series, detail, grid,
             search, settings, and the floating tab bar
  player/    PlayerActivity (ExoPlayer, picture-in-picture, MediaSession) and its overlay
app/src/test/  Unit tests ported from the Mac app's XtreamTests
```
