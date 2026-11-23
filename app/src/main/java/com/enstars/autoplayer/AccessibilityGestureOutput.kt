package com.enstars.autoplayer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.SystemClock
import kotlin.math.roundToInt

class HandlerGestureClock(private val handler: Handler) : ChartGesturePlayer.Clock {
 override fun now() = SystemClock.uptimeMillis()
 override fun postAt(timeMs: Long, task: Runnable) {
  handler.postAtTime(task, timeMs)
 }
 override fun remove(task: Runnable) {
  handler.removeCallbacks(task)
 }
}

class AccessibilityGestureOutput(
 private val service: AccessibilityService,
 private val handler: Handler,
 private val config: GameConfig,
 private val viewport: () -> CalibratedViewport?
) : ChartGesturePlayer.Output {
 private data class Continuation(
  val stroke: GestureDescription.StrokeDescription,
  val end: TouchPoint
 )

 private val continuations = mutableMapOf<Int, Continuation>()

 override fun send(batch: GestureBatcher.Batch, startDelayMs: Long, onResult: (Boolean) -> Unit): Boolean {
  val bounds = viewport() ?: return false
  val builder = GestureDescription.Builder()
  val next = mutableMapOf<Int, Continuation>()
  val starts = batch.strokes.map { stroke ->
   if (stroke.continuesFromPrevious) continuations.getValue(stroke.noteId).end
   else position(stroke.segment.from, bounds)
  }
  val ends = batch.strokes.map { stroke ->
   val segment = stroke.segment
   val fraction = (stroke.endMs - segment.startMs).toFloat() / (segment.endMs - segment.startMs)
   position(segment.from, bounds).interpolate(position(segment.to, bounds), fraction)
  }.toMutableList()

  // Android emits no MotionEvent for an entirely stationary continued packet.
  // A one-pixel move keeps a quiet hold alive until the next scheduled packet.
  if (batch.strokes.all { it.continuesFromPrevious && it.continuesInNext } &&
   starts.zip(ends).all { (from, to) ->
    from.x.roundToInt() == to.x.roundToInt() && from.y.roundToInt() == to.y.roundToInt()
   }) {
   ends[0] = bounds.nudge(starts[0])
  }

  batch.strokes.forEachIndexed { index, stroke ->
   val path = path(starts[index], ends[index])
   val delay = startDelayMs + stroke.startMs - batch.startMs
   val duration = stroke.endMs - stroke.startMs
   val description = if (stroke.continuesFromPrevious) {
    continuations.getValue(stroke.noteId).stroke.continueStroke(path, delay, duration, stroke.continuesInNext)
   } else {
    GestureDescription.StrokeDescription(path, delay, duration, stroke.continuesInNext)
   }
   builder.addStroke(description)
   if (stroke.continuesInNext) next[stroke.noteId] = Continuation(description, ends[index])
  }

  val accepted = service.dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
   override fun onCompleted(gestureDescription: GestureDescription?) = onResult(true)
   override fun onCancelled(gestureDescription: GestureDescription?) = onResult(false)
  }, handler)
  if (accepted) {
   continuations.clear()
   continuations.putAll(next)
  }
  return accepted
 }

 override fun release() {
  if (continuations.isEmpty()) return
  val builder = GestureDescription.Builder()
  continuations.values.forEach { continuation ->
   val end = continuation.end
   builder.addStroke(continuation.stroke.continueStroke(path(end, end), 0L, 1L, false))
  }
  continuations.clear()
  service.dispatchGesture(builder.build(), null, null)
 }

 override fun discard() {
  continuations.clear()
 }

 private fun position(position: GestureBatcher.Position, bounds: CalibratedViewport): TouchPoint {
  val point = bounds.point(position.lane, config.laneXs, config.laneYs)
  return position.flick?.let { bounds.flick(point, it) } ?: point
 }

 private fun path(from: TouchPoint, to: TouchPoint) = Path().apply {
  moveTo(from.x, from.y)
  if (from != to) lineTo(to.x, to.y)
 }
}
