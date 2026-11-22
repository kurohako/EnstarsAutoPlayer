package com.enstars.autoplayer

/** Serializes gesture submissions while keeping deadlines on the original SYNC clock. */
class ChartGesturePlayer(
 chartEvents: List<PlayNote>,
 private val clock: Clock,
 private val output: Output,
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

 data class Stats(
  val submitted: Int, val completed: Int, val cancelled: Int, val rejected: Int,
  val overflow: Int, val total: Int, val lateBatches: Int, val maxDispatchLateMs: Long,
  val finished: Boolean, val error: String
 )

 private val plan = GestureBatcher.plan(chartEvents)
 private val active = mutableSetOf<Int>()
 private var pending: Runnable? = null
 private var stopped = false
 private var synced = false
 private var finished = false
 private var nextBatch = 0
 private var base = 0L
 private var submitted = 0
 private var completed = 0
 private var cancelled = 0
 private var rejected = 0
 private var error = ""
 private var lateBatches = 0
 private var maxDispatchLateMs = 0L
 private var lastReportAt = 0L

 val first = plan.batches.firstOrNull()?.strokes?.firstOrNull()?.note

 fun summary() = Stats(
  submitted, completed, cancelled, rejected, plan.overflow, plan.total, lateBatches,
  maxDispatchLateMs, finished, error
 )

 fun stop() {
  if (stopped) return
  stopped = true
  pending?.let(clock::remove)
  cancelActive()
  releasePointers()
 }

 fun sync(anchorUptimeMs: Long, timingOffsetMs: Long) {
  if (stopped || synced || plan.batches.isEmpty()) return
  synced = true
  base = anchorUptimeMs - plan.batches.first().startMs + timingOffsetMs
  scheduleNext()
 }

 private fun scheduleNext() {
  while (!stopped && nextBatch < plan.batches.size) {
   val firstBatch = nextBatch == 0
   val planned = plan.batches[nextBatch++]
   val strokes = planned.strokes.filter { !it.continuesFromPrevious || it.noteId in active }
   if (strokes.isEmpty()) continue
   val batch = planned.copy(strokes = strokes)
   val targetAt = base + batch.startMs
   // Once an independent batch is safe to send, allow for Handler wake-up jitter.
   // Continuations wait at the boundary because a stationary pointer's callback can arrive early.
   val lead = if (firstBatch || strokes.any { it.continuesFromPrevious }) 0L else 12L
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
  val accepted = try {
   output.send(batch, (targetAt - now).coerceAtLeast(0L)) { success ->
    if (!stopped) {
     if (success) {
      batch.strokes.filterNot { it.continuesInNext }.forEach {
       if (active.remove(it.noteId)) completed++
      }
     } else {
      cancelActive()
      output.discard()
     }
     // Submit the next due gesture before refreshing the overlay.
     scheduleNext()
     if (!finished) report(force = !success)
    }
   }
  } catch (e: Exception) {
   error = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
   false
  }
  if (accepted) {
   starting.forEach { active.add(it.noteId) }
   submitted += starting.size
   report()
  } else {
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
