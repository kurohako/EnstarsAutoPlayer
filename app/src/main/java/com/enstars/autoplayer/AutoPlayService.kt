package com.enstars.autoplayer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executors

class AutoPlayService : AccessibilityService() {
 companion object {
  @Volatile var instance: AutoPlayService? = null
   private set
  // Accessibility screenshots are rate-limited to one request per 333 ms on Android 16.
  private const val SCREENSHOT_INTERVAL_MS = 350L
 }

 private val handler = Handler(Looper.getMainLooper())
 private val clock = HandlerGestureClock(handler)
 private val imageWorker = Executors.newSingleThreadExecutor()
 private lateinit var overlay: DebugOverlay
 private var playing = false
 private var generation = 0
 private var config = GameConfig()
 private var detector = NoteDetector(config)
 private var dispatched = 0
 private var completed = 0
 private var cancelled = 0
 private var rejected = 0
 private var skipped = 0
 private var lateBatches = 0
 private var maxDispatchLateMs = 0L
 private var rescheduled = 0
 private var firstGesture = ChartGesturePlayer.FirstGesture.PENDING
 private var dispatchError = ""
 private var detected = 0
 private var frames = 0
 private var screenshotErrors = 0
 private var chartPlayer: ChartGesturePlayer? = null
 private var livePlayer: ChartGesturePlayer? = null
 private val liveNotes = mutableListOf<PlayNote>()

 private fun diagState(state: String) {
  getSharedPreferences("sessionDiagnostics", MODE_PRIVATE).edit()
   .putString("state", state)
   .putLong("heartbeatAt", System.currentTimeMillis())
   .putInt("taps", dispatched)
   .putInt("completed", completed)
   .putInt("cancelled", cancelled)
   .putInt("rejected", rejected)
   .putInt("skipped", skipped)
   .putInt("lateBatches", lateBatches)
   .putLong("maxDispatchLateMs", maxDispatchLateMs)
   .putInt("rescheduled", rescheduled)
   .putString("firstGesture", firstGesture.name)
   .putString("lastDispatchError", dispatchError.ifEmpty { "None" }).apply()
 }

 private fun recordEnd(reason: String) {
  getSharedPreferences("sessionDiagnostics", MODE_PRIVATE).edit()
   .putString("lastEndReason", reason)
   .putLong("lastEndAt", System.currentTimeMillis()).apply()
 }

 private fun endSession(reason: String) {
  if (playing) recordEnd(reason)
  stopPlayback()
  diagState("STOPPED")
  if (::overlay.isInitialized) overlay.close()
 }

 override fun onServiceConnected() {
  super.onServiceConnected()
  instance = this
  overlay = DebugOverlay(this)
  val prefs = getSharedPreferences("sessionDiagnostics", MODE_PRIVATE)
  if (prefs.getString("state", "IDLE") in listOf("PLAYING", "SYNC_WAIT", "SYNC_ARMED", "LIVE_DETECT")) {
   recordEnd("SERVICE_RESTARTED_UNEXPECTEDLY")
  }
  prefs.edit().putString("state", "READY").apply()
 }

 override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
 override fun onInterrupt() = endSession("ACCESSIBILITY_INTERRUPTED")
 override fun onDestroy() {
  endSession("SERVICE_DESTROYED")
  imageWorker.shutdown()
  instance = null
  super.onDestroy()
 }

 fun start() = showCalibration(GameConfig.load(this))

 private fun showCalibration(calibration: GameConfig) {
  stopPlayback()
  config = calibration
  detector = NoteDetector(config)
  overlay.show()
  overlay.showFixed(config)
 }

 fun restartCalibration() = start()

 fun editPoints() {
  stopPlayback()
  config = GameConfig.load(this)
  overlay.edit(config) { config = GameConfig.load(this) }
 }

 fun startAutoplay() {
  if (!::overlay.isInitialized) return
  start()
  resetCounts()
  playing = true
  overlay.showPlayerControls(onStart = null, onStop = ::stopFromOverlay)
  overlay.updateStatus("AUTO TAP - EXPERIMENTAL")
  diagState("LIVE_DETECT")
  val epoch = generation
  handler.post { capture(epoch) }
 }

 fun showFloatingControls() {
  if (!::overlay.isInitialized) return
  start()
  showChartControls()
  overlay.updateStatus("FLOATING CONTROL READY")
 }

 private fun showChartControls() {
  overlay.showPlayerControls(onStart = {
   val offset = getSharedPreferences("chart", MODE_PRIVATE).getLong("syncOffset", 0L)
   startChartAutoplay(offset)
  }, onStop = ::stopFromOverlay)
 }

 fun startChartAutoplay(offsetMs: Long) {
  if (!::overlay.isInitialized) return
  if (chartPlayer != null) {
   overlay.updateStatus("CHART ALREADY ACTIVE")
   return
  }
  val raw = getSharedPreferences("chart", MODE_PRIVATE).getString("chartText", null)
  if (raw.isNullOrBlank()) {
   overlay.updateStatus("NO CHART IMPORTED")
   return
  }
  val chart = try {
   ChartParser.parse(raw)
  } catch (e: Exception) {
   overlay.updateStatus("CHART ERROR " + e.javaClass.simpleName)
   return
  }

  val epoch = generation + 1
  val savedConfig = GameConfig.load(this)
  val player = ChartGesturePlayer(chart.events, clock, gestureOutput(savedConfig), isolateFirstChord = true) { stats ->
   if (playing && generation == epoch) {
    val firstGestureChanged = firstGesture != stats.firstGesture
    updateCounts(stats)
    val label = if (stats.finished) "DONE" else "CHART"
    overlay.updateStatus("SYNC ${stats.firstGesture} | $label ${stats.submitted}/${stats.total} OK ${stats.completed}" +
     " CXL ${stats.cancelled} REJ ${stats.rejected} SKIP ${stats.overflow}" +
     " RETRY ${stats.rescheduled} LATE ${stats.lateBatches} (${stats.maxDispatchLateMs}ms)")
    if (stats.finished) {
     recordEnd("CHART_FINISHED")
     stopPlayback()
     diagState("FINISHED")
     showChartControls()
    } else if (firstGestureChanged) {
     diagState("PLAYING")
    }
   }
  }
  val first = player.first
  if (first == null) {
   overlay.updateStatus("CHART HAS NO PLAYABLE NOTES")
   return
  }

  showCalibration(savedConfig)
  resetCounts()
  playing = true
  chartPlayer = player
  diagState("SYNC_WAIT")
  val manual = player.canSyncFromManualTap
  val onSync: (Long) -> Unit = onSync@{ anchorAt ->
   if (playing && generation == epoch && chartPlayer === player) {
    player.sync(anchorAt, offsetMs, manualFirstTap = manual)
    if (!playing || generation != epoch) return@onSync
    diagState("PLAYING")
    val heartbeat = object : Runnable {
     override fun run() {
      if (playing && generation == epoch) {
       updateCounts(player.summary())
       diagState("PLAYING")
       handler.postDelayed(this, 1000L)
      }
     }
    }
    handler.postDelayed(heartbeat, 1000L)
   }
  }
  if (manual) {
   overlay.showManualSyncAtLane(first.lane, ::stopFromOverlay, onArmed = {
    if (playing && generation == epoch) diagState("SYNC_ARMED")
   }, onSync = onSync)
  } else {
   overlay.showPlayerControls(onStart = null, onStop = ::stopFromOverlay)
   val lane = (first.lane + 1).toString().removeSuffix(".0")
   overlay.updateStatus("SYNC LANE $lane - RELEASE ON FIRST NOTE")
   overlay.showSyncAtLane(first.lane, onSync)
  }
 }

 fun testTap() {
  start()
  val epoch = generation
  handler.postDelayed({
   val point = overlay.touchViewport()?.point(4f, config.laneXs, config.laneYs)
   if (point == null) {
    overlay.updateStatus("TEST TAP: CALIBRATION VIEW UNAVAILABLE")
    return@postDelayed
   }
   val path = Path().apply { moveTo(point.x, point.y) }
   val gesture = GestureDescription.Builder()
    .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L)).build()
   val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
    override fun onCompleted(gestureDescription: GestureDescription?) {
     if (epoch == generation) overlay.updateStatus("TEST TAP COMPLETED")
    }
    override fun onCancelled(gestureDescription: GestureDescription?) {
     if (epoch == generation) overlay.updateStatus("TEST TAP CANCELLED")
    }
   }, handler)
   if (!accepted) overlay.updateStatus("TEST TAP REJECTED")
  }, 5000L)
  overlay.updateStatus("TEST TAP IN 5 SEC - OPEN GAME")
 }

 private fun resetCounts() {
  dispatched = 0
  completed = 0
  cancelled = 0
  rejected = 0
  skipped = 0
  lateBatches = 0
  maxDispatchLateMs = 0L
  rescheduled = 0
  firstGesture = ChartGesturePlayer.FirstGesture.PENDING
  dispatchError = ""
  detected = 0
  frames = 0
  screenshotErrors = 0
 }

 private fun updateCounts(stats: ChartGesturePlayer.Stats, accumulate: Boolean = false) {
  if (!accumulate) resetCounts()
  dispatched += stats.submitted
  completed += stats.completed
  cancelled += stats.cancelled
  rejected += stats.rejected
  skipped += stats.overflow
  lateBatches += stats.lateBatches
  maxDispatchLateMs = maxOf(maxDispatchLateMs, stats.maxDispatchLateMs)
  rescheduled += stats.rescheduled
  if (!accumulate) firstGesture = stats.firstGesture
  if (stats.error.isNotEmpty()) dispatchError = stats.error
 }

 private fun stopPlayback() {
  playing = false
  generation++
  chartPlayer?.let {
   it.stop()
   updateCounts(it.summary())
  }
  livePlayer?.let {
   it.stop()
   updateCounts(it.summary(), accumulate = true)
  }
  chartPlayer = null
  livePlayer = null
  liveNotes.clear()
  handler.removeCallbacksAndMessages(null)
 }

 fun hideWhenIdle() {
  if (!playing && ::overlay.isInitialized) overlay.close()
 }
 fun stopFromOverlay() = endSession("LONG_PRESS_STOP")
 fun stop() = endSession("STOP_FROM_APP")

 private fun isLive(epoch: Int) = playing && chartPlayer == null && generation == epoch

 private fun capture(epoch: Int) {
  if (!isLive(epoch)) return
  val requestedAt = SystemClock.uptimeMillis()
  fun nextCapture(backoffMs: Long = 0L) {
   if (isLive(epoch)) {
    val nextAt = maxOf(requestedAt + SCREENSHOT_INTERVAL_MS, SystemClock.uptimeMillis() + backoffMs)
    handler.postAtTime({ capture(epoch) }, nextAt)
   }
  }
  try {
   takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
    override fun onSuccess(result: ScreenshotResult) {
     val buffer = result.hardwareBuffer
     val viewport = if (isLive(epoch)) overlay.touchViewport() else null
     if (viewport == null) {
      buffer.close()
      nextCapture()
      return
     }
     val analyzer = detector
     imageWorker.execute {
      val frame = runCatching {
       buffer.use {
        val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return@use null
        val bitmap = try {
         wrapped.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
         wrapped.recycle()
        } ?: return@use null
        try {
         analyzer.detect(bitmap, viewport, result.timestamp)
        } finally {
         bitmap.recycle()
        }
       }
      }
      handler.post {
       if (!isLive(epoch)) return@post
       frame.onSuccess { hits ->
        if (hits != null) acceptLiveFrame(epoch, hits)
       }.onFailure {
        overlay.updateStatus("PROCESS ERROR " + it.javaClass.simpleName)
       }
       nextCapture()
      }
     }
    }

    override fun onFailure(errorCode: Int) {
     if (!isLive(epoch)) return
     screenshotErrors++
     overlay.updateStatus("CAPTURE ERROR $errorCode x$screenshotErrors OK $frames")
     diagState("LIVE_DETECT")
     nextCapture(350L)
    }
   })
  } catch (e: Exception) {
   screenshotErrors++
   overlay.updateStatus("CAPTURE ERROR " + e.javaClass.simpleName)
   nextCapture(350L)
  }
 }

 private fun acceptLiveFrame(epoch: Int, hits: List<BlueNoteTracker.Hit>) {
  frames++
  detected += hits.size
  liveNotes.addAll(hits.map { PlayNote(it.targetAtMs, it.lane.toFloat(), NoteKind.TAP) })
  playLiveNotes(epoch)
  if (frames % 10 == 0) {
   showLiveCounts()
   diagState("LIVE_DETECT")
  }
 }

 private fun gestureOutput(config: GameConfig) =
  AccessibilityGestureOutput(this, handler, config, overlay::touchViewport)

 private fun playLiveNotes(epoch: Int) {
  if (!isLive(epoch) || livePlayer != null || liveNotes.isEmpty()) return
  val notes = liveNotes.toList()
  liveNotes.clear()
  val player = ChartGesturePlayer(notes, clock, gestureOutput(config)) { stats ->
   if (isLive(epoch) && stats.finished) {
    livePlayer = null
    updateCounts(stats, accumulate = true)
    showLiveCounts()
    diagState("LIVE_DETECT")
    handler.post { playLiveNotes(epoch) }
   }
  }
  val first = player.first ?: return
  livePlayer = player
  player.sync(first.timeMs, 0L)
 }

 private fun showLiveCounts() {
  overlay.updateStatus("AUTO TAP $dispatched OK $completed CXL $cancelled / HITS $detected")
 }
}
