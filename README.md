# EnstarsAutoPlayer V3.6 — Android no-Root rhythm chart automation (experimental)

Build: GitHub Actions > **Build Android APK** > latest successful run > **EnstarsAutoPlayer-debug-apk**.
Package: `com.enstars.autoplayer`. Android 11+; manual Accessibility Service enablement required.

## What's new in V3.6
- **SYNC ON FIRST NOTE**: Start the offline chart, switch to the game, and touch the small SYNC ring located directly on the first calibrated note target when the note crosses the judgment circle. Finger-down time is used as chart time origin; the ring disappears on release and the service injects the first note gesture.
- **Typed gestures**: Tap, left/right/up/down Flick, timed stationary Hold, path-based Slide and optional Flick release at the end of a Hold/Slide.
- **Multi-touch overlap planner**: simultaneous or overlapping notes are batched into one `GestureDescription` to avoid Android cancelling ongoing holds. Slides with multiple timed knots use `continueStroke()` for each segment.
- **Built-in 60-FPS chart candidates**: 508 blue Tap candidates, 42 left-Flick visual candidates, 24 right-Flick visually screened arrow candidates, derived from the user-provided music video. Flick guesses are enabled by default, but can be disabled before selecting the chart. *Timing and all candidate classifications remain experimental and may be wrong.*
- **Video JSON v2 and esmchartplayer .txt chart imports**: if exact Hold/Slide end times are provided, V3.6 plays them automatically; ordinary colored blobs alone are not sufficient to infer hold durations.
- Existing manual nine-point drag calibration, touch-through overlay, hold-to-stop control, and stop-reason diagnostics are retained.

## Phone test
1. Install the APK and enable EnstarsAutoPlayer under Android Accessibility settings.
2. Open the game and calibrate nine targets once (Edit 9 points > drag > SAVE).
3. In the app, select **USE BUILT-IN VIDEO CHART** for the supplied `ハム太郎とっとこうた` Special recording, or import a complete third-party `.txt` / V3.6 `.json` chart.
4. Return to game. In the app you can press **SHOW FLOATING START / STOP** beforehand, then press floating **START**.
5. Wait for the first note. The **SYNC** ring is over its judgment point; tap that ring in time with the first note. The overlay is otherwise touch-through.
6. To stop intentionally, long-press **HOLD STOP**. For unexpected exits, open **SHOW LAST STOP REASON**.

## Structured V3.6 chart JSON
The `examples/v3.6-typed-chart-EXAMPLE-ONLY.json` file is *demonstration data*, not real song timing.

`format` must be `enstars-video-v2`; `notes` is a list of objects:
- `timeMs`: hit time relative to the chart clock, milliseconds
- `lane`: judgment location left-to-right 1..9, half lanes allowed
- `type`: `tap`, `flick_left`, `flick_right`, `flick_up`, `flick_down`, `hold`, `slide`
- `confidence`: `high` or `medium` (low and unknown are skipped)
- For `hold`/`slide`: `endTimeMs` required and greater than `timeMs`
- `path` for slide: array of `{"timeMs": ...,"lane":...}` waypoints
- Optional `release`: `flick_left`, `flick_right`, `flick_up`, `flick_down`

`esmchartplayer` text charts recognize O/S taps, L/R/U/D directional flicks and chained `=` timed hold knots, including nonjudgment turns. These source formats may express exceptional sequences this beta cannot reproduce.

## Technical boundaries
Android `AccessibilityService.dispatchGesture()` cancels active gestures when another is submitted. The planner groups overlapping events; no-Root service cannot guarantee zero latency or uninterrupted multi-touch on every phone. Gestures have a maximum of 10 strokes; excess strokes in one batch are skipped with `SKIP` diagnostics rather than crashing the app. Video estimates may miss or misclassify notes. The built-in chart **does not contain verified Hold/Slide endpoints**: those need complete typed chart data or offline manual annotation, and are not fabricated.

## APK upgrading
The project supports `versionName=3.6.x` and monotonically increasing `versionCode`. A signed release APK requires persistent GitHub Secrets (`ENSTARS_KEYSTORE_BASE64`, `ENSTARS_KEYSTORE_PASSWORD`, `ENSTARS_KEY_ALIAS`, `ENSTARS_KEY_PASSWORD`). Without those, the workflow publishes only Debug APKs and seamless in-place upgrades are **not guaranteed** across signing keys. Back up calibration before any uninstall.

## Build verification
GitHub Actions builds the Debug APK and runs `:app:testDebugUnitTest` tests for typed parser and gesture overlap plans.
