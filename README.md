# Nature Home — Philips 58PUS8506/12 cleanup

A reversible debloat plus a minimal, Apple TV-style home screen for the 2021 Philips
58PUS8506/12 (Android TV 11, TP Vision `org.droidtv` platform) on the home LAN.

**TV:** 192.168.0.44 (Ethernet) · **ADB:** `192.168.0.44:5555`
**Board:** `PH1M_EA_9970A` · **Build:** `RTT2.211108.001` · 1920×1080 @ 320 dpi

## Result

- **26 packages disabled by this project**, all with `pm disable-user --user 0`.
  Nothing was uninstalled.
- **19 packages were already disabled by the factory** (Freeview stack, Aurora,
  Prime Video, Apple TV+). They were left exactly as they were and are **not** in the
  restore record.
- **45 disabled packages in total** (26 + 19), down from 19.
- Stock launcher replaced by **Nature Home v2**, which survives a reboot.
- **Free RAM: 499 MB → 754 MB**; used RAM 2,203 MB → 1,940 MB; swap in use
  89.6 MB → 22.5 MB (measured with `dumpsys meminfo`, before vs. after).
- The biggest wins were the stock launcher (291 MB RSS), `tvrecommendations`
  (105 MB), `candeebug` (100 MB) and Play Movies (96 MB), all gone.
- Restore path tested on the TV: stock launcher can be re-enabled and made HOME
  again, then Nature Home restored.

## What was disabled

Applied in four reviewed batches; see [`debloat/`](debloat/). The exact list of
packages this project disabled is [`backup/disabled-by-us.txt`](backup/disabled-by-us.txt).

| Batch | Packages | Why |
| --- | --- | --- |
| [`00-stock-launcher.txt`](debloat/00-stock-launcher.txt) | `com.google.android.tvlauncher` | Replaced by Nature Home. Kept enabled until Nature Home was HOME. |
| [`01-philips-telemetry.txt`](debloat/01-philips-telemetry.txt) | `candeebug`, `gel`, `usagelogger`, `tv.frameworklogger`, `nettvrecommender`, `recommendation`, `nettv.market`, `demome`, `m7opapp`, `nettv_launcherapp`, `nettvapp1814` (ARTE), `nettvapp4546` (MEGOGO), `nettvapp5326`, `nettvapp5512` (Fishing TV), `whalelive` | Philips telemetry, Top Picks/Shop promos, retail demo, preloaded promo apps. |
| [`02-google.txt`](debloat/02-google.txt) | `feedback`, `partnersetup`, `onetimeinitializer`, `sss.authbridge`, calendar sync, `play.games`, `videos` | Google telemetry and unused Google apps. **Note:** `com.google.android.youtube.tvmusic` was re-enabled for the YouTube Music dock icon. |
| [`03-optional-features.txt`](debloat/03-optional-features.txt) | `amazonalexa`, `playfi.softap`, `tvrecommendations` | Alexa bridge (Google Assistant kept), Play-Fi access point (Bluetooth audio kept), stock recommendation service. |

### Kept on purpose

YouTube, Netflix, Jellyfin, Spotify, **Rakuten TV** (`tv.wuaki.apptv`), live TV and
tuner (`xtv`, `playtv`, `channels`, `epgdata`, `dvbi`), TV Guide/EPG, HbbTV, the
Philips Sources drawer, Ambilight, Bluetooth, Google Assistant (`katniss`), Chromecast
(`mediashell`), Play Store, firmware update, DLNA, IP control, TalkBack, and the
TV engine packages (`eum`, `aurora`, `xtvsystem`, `homeintentresolver`, …).
User-installed apps (iVysílání, Twitch, T-Mobile TV, …) were not touched.
The Sources drawer was verified after the debloat: Watch TV (Antenna), Watch
Satellite, USB, Network, Recordings and HDMI inputs are all still listed, and its
“Android TV Home” entry resolves to Nature Home.

### Trade-offs

- Disabling `com.google.android.tvrecommendations` also removes the Android TV
  notification panel. Nature Home replaces that surface.
- The stock launcher's Play Next / Shop rows and the Philips Top Picks row are gone.
- Alexa is gone; Google Assistant still starts (verified in logcat with
  `am start -a android.intent.action.ASSIST`).
- DTS Play-Fi's softAP is gone; Bluetooth audio is untouched.
- Live TV and TV Guide screens are DRM-protected, so `adb screencap` returns 0 bytes
  for them. They were verified through the activity stack
  (`org.droidtv.playtv/.PlayTvActivity`, `org.droidtv.channels/.ChannelsActivity`).

## Nature Home launcher v2

### Visuals
- Full-bleed rotating nature wallpapers: 8 bundled photos, 45 s crossfade, slow Ken Burns zoom.
- Large clock and date, 24-hour UK format.
- **Weather** in top-right: current temperature + condition + next-hour precipitation outlook
  (Open-Meteo, no API key, IP geolocation with 7-day cache, optional `location.txt` override).
- **Frosted dock** with half-size icons, no labels:
  **Live TV → YouTube → Netflix → Jellyfin → Spotify → Rakuten TV → YouTube Music → Apps**

### Dock behaviour
| Item | Action |
| --- | --- |
| **Live TV** | Opens `org.droidtv.playtv/.PlayTvActivity` (standard tuner) |
| **YouTube** | `com.google.android.youtube.tv` |
| **Netflix** | `com.netflix.ninja` |
| **Jellyfin** | `org.jellyfin.androidtv` |
| **Spotify** | `com.spotify.tv.android` |
| **Rakuten TV** | `tv.wuaki.apptv` (also triggered by remote Rakuten button) |
| **YouTube Music** | `com.google.android.youtube.tvmusic` (opens Music tab in YouTube TV app) |
| **Apps** | Opens full app drawer |

### App drawer
- Grid with every remaining launchable app plus TV Settings.
- Platform plumbing (`devicesetup`, `nettvregistration`, `eum`, promo rows,
  `contentexplorer`, `channels`, `playtv`, `mimi`, carrier apps) and unlabelled
  components are filtered out; long labels get two lines.
- Drawer closes on HOME/resume and after launching an app; focus returns to the dock.

### Remote button handling
| Button | Key code | Intent / Action | Result |
| --- | --- | --- | --- |
| **Rakuten** | 779 | `LAUNCH_NATIVE_APP` + `KEY_EVENT=779` | Opens Rakuten TV (`tv.wuaki.apptv`) |
| **IVI / Source** | 790 | `LAUNCH_NATIVE_APP` + `KEY_EVENT=790` | Opens Live TV |
| **All Apps** | — | `android.intent.action.ALL_APPS` | Opens Nature Home drawer |
| **Prime Video** | 788 | Explicit component `com.amazon.amazonvideo.livingroom` | **Cannot remap** (factory-disabled, needs root) |
| **YouTube** | 781 | `setPackage("com.google.android.youtube.tv")` | Opens YouTube (handled by YouTube app) |

### Wallpapers — three sources, hundreds of images
1. **Bundled** — 8 photos in the APK (always available).
2. **Local files** — drop any `.jpg/.jpeg/.png/.webp` into  
   `/sdcard/Android/data/com.jirigrill.naturehome/files/wallpapers/`  
   (`./scripts/wallpapers.sh push ~/Pictures/nature`).
3. **URL manifest** — list `https://` image URLs (one per line, `#` comments) in  
   `files/wallpapers/urls.txt`  
   (`./scripts/wallpapers.sh urls urls.txt`).  
   Images are downloaded once, cached under `files/wallpaper-cache/`, and
   rotated alongside local/bundled sources. No storage permission required.

### Technical
- D-pad only, no web views, no analytics, no third-party libs.
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `QUERY_ALL_PACKAGES`.
- Source: [`launcher/`](launcher/) · wallpapers and credits: [`wallpapers/`](wallpapers/).

Build and install:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
/opt/homebrew/opt/gradle-8.7/bin/gradle -p launcher assembleDebug
adb install -r launcher/app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity com.jirigrill.naturehome/.MainActivity
```

On this Philips firmware, `set-home-activity` alone did not override the stock HOME
until the stock launcher was disabled. The debloat script enforces that ordering:
it refuses to disable `com.google.android.tvlauncher` unless Nature Home is already
the resolved HOME.

## Startup screen / boot behaviour

**There is no user-facing "startup screen" setting on this Philips model.**
Android TV always launches the resolved `CATEGORY_HOME` activity on boot.
Since Nature Home is set as the HOME activity (and the stock launcher is disabled),
the TV boots directly into Nature Home — no extra configuration needed.

The `persist.odm.tpv.sys.ACR_ActApp` property points to the last interactive app,
but the HOME intent takes precedence on cold boot.

## Limitations & Known Issues

| Feature | Status | Reason |
| --- | --- | --- |
| **Prime Video button → Jellyfin / Live TV** | ❌ Not possible | GlobalKey fires an **explicit component intent** to the factory-disabled `com.amazon.amazonvideo.livingroom` package. No third-party app can intercept it without root / system APK replacement. |
| **YouTube playback speed selector** | ❌ Not possible | YouTube TV app is a separate closed-source app; no launcher-level API to modify its playback UI. |
| **System-wide remote lock** | ❌ Not possible | No Android TV API to disable the IR/Bluetooth remote at kernel/hardware level. Third-party apps only receive input when in foreground. |

## Restore

```bash
./scripts/restore.sh                    # re-enable the 26 packages, stock HOME, keep the app
./scripts/restore.sh --remove-launcher  # also uninstall Nature Home
```

What it does:

1. Re-enables `com.google.android.tvlauncher` first, then every package in
   `backup/disabled-by-us.txt`.
2. Leaves the 19 factory-disabled packages disabled (they are not in the record).
3. Restores the original HOME component from `backup/home-before.txt`
   (`com.google.android.tvlauncher/.MainActivity`) and presses HOME.
4. Optionally uninstalls Nature Home, then takes `screenshots/99-restored.png`.

The HOME switch was validated on the TV (stock launcher resumed, then Nature Home
restored). Package re-enabling is the exact reverse of the recorded disable commands.
Reboot the TV once after restoring if the stock launcher or notification panel does
not reappear immediately.

## Backup

[`backup/`](backup/) contains the full pre-change state: all packages with APK paths,
enabled/disabled/system/third-party lists, full `dumpsys package`, `getprop`, all three
settings namespaces, display size/density, disk usage, process and memory snapshots,
the original HOME activity, and the 19 factory-disabled packages. The first screenshot
is [`screenshots/00-before.png`](screenshots/00-before.png).

## Screenshots

| File | What it shows |
| --- | --- |
| [`00-before.png`](screenshots/00-before.png) | Stock launcher: Shop tab, sponsored Play Next, Twitch row. |
| [`05-home-rakuten.png`](screenshots/05-home-rakuten.png) | Nature Home with the complete six-item dock (v1). |
| [`12-batch-c-home.png`](screenshots/12-batch-c-home.png) | Home after all debloat batches. |
| [`14-final-drawer.png`](screenshots/14-final-drawer.png) | Final app drawer: only real apps remain. |
| [`15-after-reboot.png`](screenshots/15-after-reboot.png) | Nature Home after a full reboot. |
| [`16-restore-home-test.png`](screenshots/16-restore-home-test.png) | Stock launcher restored during the restore-path test. |
| [`17-final-home.png`](screenshots/17-final-home.png) | Final home, drawer closed after returning from an app. |
| [`18-sources-drawer.png`](screenshots/18-sources-drawer.png) | Philips Sources drawer intact: antenna, satellite, USB, network, HDMI, recordings. |
| [`20-v2-home.png`](screenshots/20-v2-home.png) | Nature Home v2: weather top-right, 8-icon dock, YouTube Music, Live TV. |

The TV's screencap occasionally catches a frame mid-transition, which looks like
ghosting in a screenshot but is not visible on the panel. Clean shots were taken
after the screen settled.

## Security

ADB over TCP is unencrypted. When you are done, turn off **USB debugging / Network
debugging** in Developer options. The TV does not need ADB for normal use; the restore
script is the only thing that does.

## Environment used

- macOS (Apple Silicon), `adb` from Homebrew `android-platform-tools`
- OpenJDK 17, Android SDK platform/build-tools 34, Gradle 8.7
- Build output: `launcher/app/build/outputs/apk/debug/app-debug.apk`