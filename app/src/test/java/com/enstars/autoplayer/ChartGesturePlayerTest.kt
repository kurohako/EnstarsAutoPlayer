package com.enstars.autoplayer

import org.junit.Assert.*
import org.junit.Test

class ChartGesturePlayerTest {
 @Test fun playingOneBatchNeverPreemptsItOrMovesTheSongClock() {
  val clock = FakeClock(5000L)
  val output = FakeOutput(clock)
  val notes = listOf(1000L, 1100L, 1200L).map { PlayNote(it, 4f, NoteKind.TAP) }
  val player = ChartGesturePlayer(notes, clock, output) {}
  player.sync(4980L, 0L)
  assertEquals(listOf(5000L), output.sentAt)

  clock.advanceTo(5110L)
  assertEquals(1, output.sentAt.size)
  output.complete()
  assertEquals(listOf(5000L, 5110L), output.sentAt)
  output.complete()
  clock.advanceTo(5168L)
  assertEquals(listOf(5000L, 5110L, 5168L), output.sentAt)
  assertEquals(listOf(0L, 0L, 12L), output.delays)
  output.complete()
  assertEquals(3, player.summary().completed)
  assertEquals(2, player.summary().lateBatches)
  assertEquals(30L, player.summary().maxDispatchLateMs)
  assertTrue(player.summary().finished)
 }

 @Test fun continuedHoldWaitsForItsBoundaryEvenWithAnEarlyCallback() {
  val clock = FakeClock(5000L)
  val output = FakeOutput(clock)
  val notes = listOf(PlayNote(1000L, 0f, NoteKind.HOLD, 2100L))
  val player = ChartGesturePlayer(notes, clock, output) {}
  player.sync(5000L, 0L)
  output.complete()
  clock.advanceTo(5499L)
  assertEquals(listOf(5000L), output.sentAt)
  clock.advanceTo(5500L)
  assertEquals(listOf(5000L, 5500L), output.sentAt)
  assertTrue(output.batches.last().strokes.single().continuesFromPrevious)
  assertEquals(1, player.summary().submitted)
  assertEquals(0, player.summary().completed)
  player.stop()
  assertEquals(1, output.releases)
  output.complete()
  clock.advanceTo(7000L)
  assertEquals(2, output.sentAt.size)
  assertEquals(1, player.summary().cancelled)
 }

 @Test fun aCancelledHoldDoesNotCancelLaterTapBatches() {
  val clock = FakeClock(5000L)
  val output = FakeOutput(clock)
  val notes = listOf(PlayNote(1000L, 0f, NoteKind.HOLD, 2000L), PlayNote(1600L, 3f, NoteKind.TAP))
  val player = ChartGesturePlayer(notes, clock, output) {}
  player.sync(5000L, 25L)
  clock.advanceTo(5025L)
  output.complete(false)

  clock.advanceTo(5525L)
  val remaining = output.batches.last().strokes.single()
  assertFalse(remaining.continuesFromPrevious)
  assertEquals(NoteKind.TAP, remaining.note.kind)
  output.complete()
  assertEquals(2, player.summary().submitted)
  assertEquals(1, player.summary().completed)
  assertEquals(1, player.summary().cancelled)
  assertEquals(1, output.discards)
 }

 @Test fun rejectedContinuationCountsTheHoldOnceAndRejectsItsNewNotes() {
  val clock = FakeClock(5000L)
  val output = FakeOutput(clock)
  val notes = listOf(
   PlayNote(1000L, 0f, NoteKind.HOLD, 2200L),
   PlayNote(1600L, 3f, NoteKind.TAP),
   PlayNote(2500L, 4f, NoteKind.TAP)
  )
  val player = ChartGesturePlayer(notes, clock, output) {}
  player.sync(5000L, 0L)
  output.complete()
  output.accept = false
  clock.advanceTo(5500L)
  assertEquals(1, player.summary().cancelled)
  assertEquals(1, player.summary().rejected)
  assertEquals(1, output.releases)
  output.accept = true
  clock.advanceTo(6500L)
  output.complete()
  assertEquals(2, player.summary().submitted)
  assertEquals(1, player.summary().completed)
  assertTrue(player.summary().finished)
 }

 private class FakeClock(private var now: Long) : ChartGesturePlayer.Clock {
  private val tasks = linkedMapOf<Runnable, Long>()
  override fun now() = now
  override fun postAt(timeMs: Long, task: Runnable) {
   tasks[task] = timeMs
  }
  override fun remove(task: Runnable) {
   tasks.remove(task)
  }
  fun advanceTo(time: Long) {
   now = time
   while (true) {
    val next = tasks.filterValues { it <= now }.minByOrNull { it.value }?.key ?: return
    tasks.remove(next)
    next.run()
   }
  }
 }

 private class FakeOutput(private val clock: FakeClock) : ChartGesturePlayer.Output {
  val sentAt = mutableListOf<Long>()
  val delays = mutableListOf<Long>()
  val batches = mutableListOf<GestureBatcher.Batch>()
  var releases = 0
  var discards = 0
  var accept = true
  private var result: ((Boolean) -> Unit)? = null
  override fun send(batch: GestureBatcher.Batch, startDelayMs: Long, onResult: (Boolean) -> Unit): Boolean {
   check(result == null)
   sentAt.add(clock.now())
   delays.add(startDelayMs)
   batches.add(batch)
   if (accept) result = onResult
   return accept
  }
  override fun release() {
   releases++
  }
  override fun discard() {
   discards++
  }
  fun complete(success: Boolean = true) {
   val callback = checkNotNull(result)
   result = null
   callback(success)
  }
 }
}
