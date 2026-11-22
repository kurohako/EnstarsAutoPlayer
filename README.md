# EnstarsAutoPlayer V3.1 — Android no-Root chart playback prototype

This is **experimental** and is not a full automatic Perfect Combo player. Android accessibility gestures are used for taps; no Root required.

## Video-derived offline chart (2026-11-22)
- Input: user-provided 1280x720, 60 FPS recording (`1000029417.mp4`), 137.28 seconds
- Detection: 660 *visual candidate* events from full-frame offline color and trajectory tracking
- 507 blue Tap candidates are bundled directly into the app (504 tagged high, 3 medium by internal heuristics)
- 111 orange and 42 pink candidates require manual gesture-type, hold path and direction verification; they are **not played**
- Video ends with 860 Combo, which **does not prove** that all discrete notes have been extracted
- Video timestamps estimate judgment arrivals; the set is incomplete and may contain errors. It is **not** an official or verified score.

## Use on phone
1. Install the APK from GitHub Actions, enable EnstarsAutoPlayer Accessibility Service.
2. Open the exact song/difficulty from the supplied source recording.
3. If necessary, manually calibrate the nine judgment points using **Edit 9 points**, drag each handle, then **SAVE**.
4. Choose **USE BUNDLED VIDEO CHART (507 TAP candidates)**. Alternatively, select **Import chart (.txt or video .json)** for your own chart.
5. Choose **SHOW FLOATING START / STOP**; switch to game.
6. Press **START**; when the **first** note reaches its judgment circle press **SYNC FIRST NOTE** to anchor the video-based timeline.
7. **STOP** in the game or from the app to cancel pending gestures.

No screenshot loop is needed for this chart mode. A separate legacy experimental live screenshot detector remains available and has limitations on Android 16.

## Project structure
- `ChartParser.kt`: reader for esmchartplayer text chart format, and dispatch to video JSON reader
- `VideoChartParser.kt`: imports `enstars-video-v1` candidate JSON, retains high/medium blue Tap candidates
- `BundledVideoChart.kt`: compressed offline 507-Tap dataset from the supplied recording
- `AutoPlayService.kt`: scheduling, manual first-note synchronization, AccessibilityService gestures
- `DebugOverlay.kt`: touch-through overlay and draggable calibration handles
- `MainActivity.kt`: chart import, bundled selection, diagnostics and player controls

## Limitations and APK upgrading
- Flick, slides, hold duration, and special Ensemble notes are **not implemented** in the video chart player.
- First-note synchronization is manual; Android gesture latency and video/audio clock mismatch may cause MISS.
- GitHub Actions currently produces debug APKs. A permanent release signing key is **not configured** unless repository secrets are set. Consequently, debug APKs from different builds may require a one-time reinstall and **may not** preserve settings; back up calibration coordinates first.
- A signed release upgrade APK is only built if all four repository secrets are configured: `ENSTARS_KEYSTORE_BASE64`, `ENSTARS_KEYSTORE_PASSWORD`, `ENSTARS_KEY_ALIAS`, and `ENSTARS_KEY_PASSWORD`. Keep the keystore backed up privately. Do not commit secrets.

## Build
GitHub Actions > Build Android APK > successful workflow > Artifacts > EnstarsAutoPlayer-debug-apk.
