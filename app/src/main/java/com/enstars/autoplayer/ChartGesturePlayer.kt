package com.enstars.autoplayer

/** Serializes gesture submissions while keeping deadlines on the original SYNC clock. */
class ChartGesturePlayer(
 chartEvents: List<PlayNote>,
 private val clock: Clock,
 private val output: Output,
 isolateFirstChord: Boolean = false,
 private val progress: (Stats) -> Unit
) {
 interface Clock {
  fun now(): Long
  fun postAt(timeMs: Long, task: Runnable)
  fun remove(task: Runnable)
 }

 interface Output {
  /** Results are delivered asynchronously after an accepted submission. */
  fun send(batch: GestureBatcher.Batch, startDelayMs: Long, onResult: (Boolean) -> Unit): Boolean
  fun release()
  fun discard()
 }

 enum class FirstGesture { PENDING, SUBMITTED, COMPLETED, CANCELLED, REJECTED }

 data class Stats(
  val submitted: Int, val completed: Int, val cancelled: Int, val rejected: Int,
  val overflow: Int, val total: Int, val lateBatches: Int, val maxDispatchLateMs: Long,
  val rescheduled: Int, val firstGesture: FirstGesture, val finished: Boolean, val error: String
 )

 private val plan = GestureBatcher.plan(chartEvents, isolateFirstChord)
 private val firstStart = plan.batches.firstOrNull()?.startMs
 private val active = mutableSetOf<Int>()
 private val submitted = mutableSetOf<Int>()
 private val rescheduled = mutableSetOf<Int>()
 private var retryBatch: GestureBatcher.Batch? = null
 private var pending: Runnable? = null
 private var stopped = false
 private var synced = false
 private var finished = false
 private var nextBatch = 0
 private var base = 0L
 private var completed = 0
 private var cancelled = 0
 private var rejected = 0
 private var error = ""
 private var lateBatches = 0
 private var maxDispatchLateMs = 0L
 private var lastReportAt = 0L
 private var firstGesture = FirstGesture.PENDING

 val first = plan.batches.firstOrNull()?.strokes?.firstOrNull()?.note

 fun summary() = Stats(
  submitted.size, completed, cancelled, rejected, plan.overflow, plan.total, lateBatches,
  maxDispatchLateMs, rescheduled.size, firstGesture, finished, error
 )

 fun stop() {
  if (stopped) return
  stopped = true
  pending?.let(clock::remove)
  cancelActive()
  if (firstGesture == FirstGesture.SUBMITTED) firstGesture = FirstGesture.CANCELLED
  releasePointers()
 }

 fun sync(anchorUptimeMs: Long, timingOffsetMs: Long) {
  val firstStartMs = firstStart ?: return
  if (stopped || synced) return
  synced = true
  base = anchorUptimeMs - firstStartMs + timingOffsetMs
  scheduleNext()
 }

 private fun scheduleNext() {
  while (!stopped && (retryBatch != null || nextBatch < plan.batches.size)) {
   val planned = retryBatch?.also { retryBatch = null } ?: plan.batches[nextBatch++]
   val strokes = planned.strokes.filter { !it.continuesFromPrevious || it.noteId in active }
   if (strokes.isEmpty()) continue
   val batch = planned.copy(strokes = strokes)
   val targetAt = base + batch.startMs
   // Once an independent batch is safe to send, allow for Handler wake-up jitter.
   // Continuations wait at the boundary because a stationary pointer's callback can arrive early.
   val lead = if (batch.startMs == firstStart || strokes.any { it.continuesFromPrevious }) 0L else 12L
   val dispatchAt = targetAt - lead
   if (dispatchAt <= clock.now()) {
    send(batch, targetAt)
   } else {
    val task = Runnable {
     pending = null
     if (!stopped) send(batch, targetAt)
    }
    pending = task
    clock.postAt(dispatchAt, task)
   }
   return
  }
  if (!stopped) {
   finished = true
   report(force = true)
  }
 }

 private fun send(batch: GestureBatcher.Batch, targetAt: Long) {
  val now = clock.now()
  val late = (now - targetAt).coerceAtLeast(0L)
  maxDispatchLateMs = maxOf(maxDispatchLateMs, late)
  if (late > 10L) lateBatches++

  val starting = batch.strokes.filterNot { it.continuesFromPrevious }
  val first = batch.startMs == firstStart
  val accepted = try {
   output.send(batch, (targetAt - now).coerceAtLeast(0L)) { success ->
    if (!stopped) {
     if (first) firstGesture = if (success) FirstGesture.COMPLETED else FirstGesture.CANCELLED
     if (success) {
      batch.strokes.filterNot { it.continuesInNext }.forEach {
       if (active.remove(it.noteId)) completed++
      }
     } else {
      rescheduleFutureHeads(batch)
      cancelActive()
      output.discard()
     }
     // Submit the next due gesture before refreshing the overlay.
     scheduleNext()
     if (!finished) report(force = first || !success)
    }
   }
  } catch (e: Exception) {
   error = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
   false
  }
  if (accepted) {
   starting.forEach {
    active.add(it.noteId)
    submitted.add(it.noteId)
   }
   if (first) firstGesture = FirstGesture.SUBMITTED
   report(force = first)
  } else {
   if (first) firstGesture = FirstGesture.REJECTED
   rejected += starting.size
   cancelActive()
   releasePointers()
   report(force = true)
   // A disconnected service can reject every gesture left in a dense passage.
   val task = Runnable {
    pending = null
    scheduleNext()
   }
   pending = task
   clock.postAt(clock.now(), task)
  }
 }

 private fun rescheduleFutureHeads(batch: GestureBatcher.Batch) {
  val now = clock.now()
  // A callback cannot say which touches reached the game. Only retry heads whose
  // original down time is still ahead, so a possibly delivered note is never replayed.
  val future = batch.strokes.filter {
   !it.continuesFromPrevious && base + it.startMs > now && rescheduled.add(it.noteId)
  }
  if (future.isEmpty()) return
  future.forEach { active.remove(it.noteId) }
  retryBatch = batch.copy(
   startMs = future.minOf { it.startMs }, endMs = future.maxOf { it.endMs }, strokes = future
  )
 }

 private fun cancelActive() {
  cancelled += active.size
  active.clear()
 }

 private fun releasePointers() {
  try {
   output.release()
  } catch (e: RuntimeException) {
   error = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
   output.discard()
  }
 }

 private fun report(force: Boolean = false) {
  val now = clock.now()
  if (force || now - lastReportAt >= 220L) {
   lastReportAt = now
   progress(summary())
  }
 }
}
