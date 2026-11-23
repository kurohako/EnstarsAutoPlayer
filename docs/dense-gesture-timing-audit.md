# Dense gesture timing audit (offline, 2026-11-23)

Source: user-provided slow recording `1000029428.mp4` and its prior derived `EnstarsAutoPlayer_slow_refined_chart_v3.6.json` (623 candidates: 550 TAP, 42 left flick, 31 right flick). This is an **offline plan audit**, not a measured Android input trace.

## Measured chart intervals
- 112 adjacent TAP pairs are <=65 ms apart; 86 are <=30 ms apart.
- Dense half-second TAP windows include 61.5s (6), 82.0s (6), 26.0s (5), 50.5s (5), 54.0s (5).
- Under the current `GestureBatcher.plan` rules, this dataset produces 438 gesture batches, max 5 notes per batch, zero dropped due to 10-stroke limit. This assumes the provided candidate data are correct.
- The bundled chart has no confirmed HOLD start/end annotations. Orange note visuals in the recording alone do not establish accurate release times.

## Important interpretation
- Zero planned overflow does **not** prove that Android dispatched every gesture on time or that all game judgments registered.
- The code's `lateBatches` only measures when `send()` starts >10ms after target. It does not measure the actual MotionEvent down time delivered to the game.
- `completed` is an AccessibilityService callback, not proof of PERFECT/GREAT in the game.
- Source recording time, chart time, and game playback time must be aligned before declaring missing TAPs.
- Do not invent hold durations. Label start/end in the slow video (matching `holdId`) or obtain verified chart data.

## On-device verification required
Record per batch: target uptime, send uptime, scheduled stroke delay, callback completion/cancellation, note IDs/lanes/kinds. Compare to a 60fps+ gameplay recording and identify missed judgments. Actual input-event arrival needs device-side tracing; the Accessibility callback alone is insufficient.
