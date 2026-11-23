# EnstarsAutoPlayer V3.8

- For a chart that opens with a single Tap, press **ARM SYNC** once Live has started. Hit the marked first note in the game. The first game touch sets the chart clock, and playback starts with the following note.
- For a chart that opens with a chord, Flick, Hold or Slide, lift your finger from the **SYNC** ring as the first note reaches its judgment point. In this mode the touch release sets the chart clock. The ring follows the saved calibration in both modes.
- The timing offset shifts injected notes in both modes. The injected opening note or chord has its own batch. Batches wait for the previous gesture result and carry held pointers across Slide knots and Flick releases. After cancellation, the player reschedules future note heads once at their original deadlines.
- Experimental live detection tracks multiple visible blue heads per lane. Android 16 limits accessibility screenshots to one request every 333 ms, so heads can still pass between frames. Imported charts supply the timing for offline playback.
- **SHOW LAST STOP REASON** includes the first-note SYNC source, rescheduled notes, and dispatch latency. `MANUAL_TAP` indicates a game touch was observed. **HOLD STOP** ends playback.

## V3.7 experimental slow-video chart refinement

## November 2026 slow-reference comparison
- Compared the unblocked slow-note reference recording `1000029428.mp4` with the existing original-speed chart and the V3.6 test recording `1000029427.mp4`.
- A **+3500ms recording-origin difference** reconciles their recurring Tap sequences; do not multiply the song chart timestamps by a playback speed factor.
- Independent color-peak and blue-circle tracking around the judgment targets agreed on **42 new candidate Tap events** (508 → 550).
- Visual inspection of green right-arrow heads adds **7 right Flick candidates** (24 → 31). Left Flick stays at 42; red pixels on orange Hold/Slide borders created false left-arrow detections, so were not promoted.
- 62 previously matched Tap timestamps were moved conservatively by at most 45ms (only on agreement between the two analyzers). Final playable candidate counts: **550 Tap + 42 left + 31 right = 623**.
- Hold/Slide durations are **still unknown** in the supplied video analysis. Their playback engine works only when a complete, independently validated chart supplies timings and paths. This is not a complete 860 Combo chart.

### Compare original and refined results on the same phone
1. In V3.7, enable **Use slow-video refinements** (default on), then select **USE BUILT-IN VIDEO CHART**.
2. Keep the same manually calibrated nine points, difficulty and timing offset; start the live and tap the first-note SYNC circle.
3. To compare, disable the refinement checkbox and select the built-in chart again (the app reloads the saved chart). No reinstall is needed for comparisons.
4. Existing V3.6 users can import the separate `enstars-video-v2` slow-refined JSON artifact directly without installing a new APK.


Build: GitHub Actions > **Build Android APK** > latest successful run > **EnstarsAutoPlayer-debug-apk**.
Package: `com.enstars.autoplayer`. Android 11+; manual Accessibility Service enablement required.

## What's new in V3.6
- **SYNC ON FIRST NOTE**: Start the offline chart and switch to the game. The SYNC ring marks the first calibrated judgment point, and the service schedules the chart as soon as the SYNC touch ends.
- **Typed gestures**: Tap, left/right/up/down Flick, timed stationary Hold, path-based Slide and optional Flick release at the end of a Hold/Slide.
- **Multi-touch overlap planner**: simultaneous or overlapping notes share gesture batches. Sustained notes use `continueStroke()` across batches at timed knots, release Flicks, and stroke limits.
- **Built-in 60-FPS chart candidates**: 508 blue Tap candidates, 42 left-Flick visual candidates, 24 right-Flick visually screened arrow candidates, derived from the user-provided music video. Flick guesses are enabled by default, but can be disabled before selecting the chart. *Timing and all candidate classifications remain experimental and may be wrong.*
- **Video JSON v2 and esmchartplayer .txt chart imports**: if exact Hold/Slide end times are provided, V3.6 plays them automatically; ordinary colored blobs alone are not sufficient to infer hold durations.
- Existing manual nine-point drag calibration, touch-through overlay, hold-to-stop control, and stop-reason diagnostics are retained.

## Phone test
1. Install the APK and enable EnstarsAutoPlayer under Android Accessibility settings.
2. Open the game and calibrate nine targets once (Edit 9 points > drag > SAVE).
3. In the app, select **USE BUILT-IN VIDEO CHART** for the supplied `ハム太郎とっとこうた` Special recording, or import a complete third-party `.txt` / V3.6 `.json` chart.
4. Return to game. In the app you can press **SHOW FLOATING START / STOP** beforehand, then press floating **START**.
5. If **ARM SYNC** appears, press it in Live, then tap the marked first note yourself. For a **SYNC** ring without an arming button, lift your finger as the first note reaches the target.
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
Android `AccessibilityService.dispatchGesture()` can cancel an active gesture when submitting an unrelated gesture. The planner respects the platform's 10-stroke limit per batch and reports overlaps that exceed the available touch pointers as `SKIP`. Gesture completion reports Android's injection result. Game judgments and input latency require a device test. Built-in video estimates may miss or misclassify notes. Hold and Slide endpoints require a complete typed chart or verified annotation.

## APK upgrading
The project uses `versionName=3.8.x` and increases `versionCode` with the Actions run number. A signed release APK requires persistent GitHub Secrets (`ENSTARS_KEYSTORE_BASE64`, `ENSTARS_KEYSTORE_PASSWORD`, `ENSTARS_KEY_ALIAS`, `ENSTARS_KEY_PASSWORD`). Android requires the same signing key for in-place upgrades. Back up calibration before any uninstall.

## Build verification
With Java 17, Android SDK Platform 35 and Gradle 8.9 installed, run `gradle :app:testDebugUnitTest :app:assembleDebug`. The **Build Android APK** workflow publishes the Debug APK on pull requests to `main` after the unit tests pass.
