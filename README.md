# Xoro WMS (Android scanner app)

A small Android app for the warehouse scanners. It opens the Xoro WMS website full screen
(no Chrome address bar, no status/navigation bars) and can start automatically when the
scanner turns on. Barcode scans typed in by the scanner (Zebra DataWedge, Honeywell, etc. in
"keyboard" mode) go into the page exactly as they do in Chrome.

## Install on a scanner

1. On the scanner's browser, open the download link (always the latest build):
   **https://github.com/RishamChandi/XoroWMS/releases/latest/download/xoro-wms.apk**
2. Open the downloaded file. If Android asks, allow **Install unknown apps** for the browser,
   then tap **Install**.
3. Open **Xoro WMS**. The first time, it opens the **Settings** screen.
4. Enter the **WMS address** (use **Test connection** to check it), choose the options below,
   then tap **Save & Launch**. The WMS opens full screen.
5. If a yellow box asks for **Display over other apps**, tap it, switch it on and press Back.
   Android 10+ needs this for an app to open itself at startup.
6. Restart the scanner to check it opens straight into the WMS.

Updates: install a newer APK over the top; settings and the WMS login are kept.

## Settings screen

Open it any time: tap the **top-right corner** of the screen **5 times quickly** (within 3
seconds), then enter the admin PIN (default **1234**).

| Setting | What it does |
| --- | --- |
| WMS address + Test connection | The page the app opens. |
| Full screen (default ON) | Hides Android's status and navigation bars. Turn off to see them. |
| Open automatically when the scanner starts (default ON) | Opens the app after boot. Needs "Display over other apps" on Android 10+. |
| Use as Home screen (launcher mode) (default OFF) | Makes the app the scanner's Home screen: Home button and startup always open the WMS. |
| Change admin PIN · Reload · Open Android settings · Close app | |

### Startup: two ways

- **Open automatically at startup (recommended to start with).** The normal Android home
  screen stays; the app just opens itself after boot. Staff can still reach other apps via
  Settings → Close app.
- **Home-screen (launcher) mode.** Stronger kiosk: pressing Home also returns to the WMS.
  Turn it on in Settings; Android then shows its *Default Home app* screen — pick **Xoro WMS**.
  **To undo:** Settings → turn the switch off (or *Close app* → *Turn off & close*). Android goes
  back to the normal launcher; if it asks which Home app to use, pick the normal one
  (e.g. Launcher3/Quickstep). Zebra devices with *Enterprise Home Screen* can instead list
  Xoro WMS as an allowed/auto-launch app there.

The Back button navigates back inside the WMS but never closes the app. If Wi-Fi isn't ready
yet (e.g. right after boot), the app shows "Can't reach the WMS" and retries every 10 seconds.

## Building

GitHub Actions (`.github/workflows/build-apk.yml`) builds the APK on every push and pull
request (downloadable from the run's artifacts). Each push to `main` publishes a new release
`v1.0.<build>` with `xoro-wms.apk`, which the download link above always points to. It can
also be run by hand from the **Actions** tab.

Optional repository settings (Settings → Secrets and variables → Actions):

| Name | Type | Purpose |
| --- | --- | --- |
| `WMS_KIOSK_URL` | Variable | Pre-fill the WMS address in every build (normally left empty — set it in the app) |
| `WMS_KIOSK_PIN` | Variable | Default admin PIN baked into every build |
| `KIOSK_KEYSTORE_BASE64`, `KIOSK_KEYSTORE_PASSWORD`, `KIOSK_KEY_ALIAS`, `KIOSK_KEY_PASSWORD` | Secrets | Your own signing key. Without them the committed `keystore/wms-kiosk.jks` is used. |

The repository is public, so the committed fallback key is public too: set your own key in
secrets before rolling out widely, and then keep it — Android only installs an update over
the existing app when it is signed with the same key.

Local build (needs the Android SDK): `./gradlew assembleRelease`
