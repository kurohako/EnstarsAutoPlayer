package com.enstars.autoplayer

import org.junit.Assert.*
import org.junit.Test

class ChartGesturePlayerTest {
 @Test fun aManualOpeningTapAnchorsPlaybackWithoutInjectingTheFirstNoteAgain() {
  val clock = FakeClock(5004L)
  val output = FakeOutput(clock)
  val first = PlayNote(6833L, 5f, NoteKind.TAP)
  val next = PlayNote(7541L, 7f, NoteKind.TAP)
  val player = ChartGesturePlayer(listOf(first, next), clock, output, isolateFirstChord = true) {}
  assertTrue(player.canSyncFromManualTap)

  player.sync(5000L, 20L, manualFirstTap = true)
  assertEquals(ChartGesturePlayer.FirstGesture.MANUAL_TAP, player.summary().firstGesture)
  assertEquals(0, player.summary().submitted)
  clock.advanceTo(5715L)
  assertTrue(output.batches.isEmpty())
  clock.advanceTo(5716L)
  assertEquals(listOf(next), output.batches.single().notes)
  assertEquals(5728L, output.sentAt.single() + output.delays.single())
  clock.advanceTo(5758L)
  output.complete()
  assertEquals(1, player.summary().completed)
  assertEquals(ChartGesturePlayer.FirstGesture.MANUAL_TAP, player.summary().firstGesture)
 }

 @Test fun aSingleManualTapFinishesWithoutSubmittingAnAndroidGesture() {
  val clock = FakeClock(5000L)
  val output = FakeOutput(clock)
  val player = ChartGesturePlayer(listOf(PlayNote(1000L, 2f, NoteKind.TAP)), clock, output,
   isolateFirstChord = true) {}
  player.sync(5000L, 0L, manualFirstTap = true)
  assertTrue(player.summary().finished)
  assertTrue(output.batches.isEmpty())
  assertEquals(ChartGesturePlayer.FirstGesture.MANUAL_TAP, player.summary().firstGesture)
 }

 @Test fun releaseAnchorsTheFirstChordAndItsCancellationLeavesNextHeadOnTime() {
  val clock = FakeClock(5002L)
  val output = FakeOutput(clock)
  val first = listOf(PlayNote(1000L, 2f, NoteKind.TAP), PlayNote(1000L, 6f, NoteKind.TAP))
  val next = PlayNote(1040L, 2f, NoteKind.TAP)
  val player = ChartGesturePlayer(first + next, clock, output, isolateFirstChord = true) {}
  assertFalse(player.canSyncFromManualTap)
  val touch = SyncTap()
  touch.press()
  assertTrue(output.batches.isEmpty())

  player.sync(checkNotNull(touch.release(5000L)), 0L)
  assertEquals(first, output.batches.single().notes)
  assertEquals(ChartGesturePlayer.FirstGesture.SUBMITTED, player.summary().firstGesture)
  clock.advanceTo(5010L)
  output.complete(false)
  assertEquals(ChartGesturePlayer.FirstGesture.CANCELLED, player.summary().firstGesture)

  clock.advanceTo(5028L)
  assertEquals(listOf(next), output.batches.last().notes)
  assertEquals(5040L, output.sentAt.last() + output.delays.last())
  clock.advanceTo(5070L)
  output.complete()
  assertEquals(1, player.summary().completed)
  assertEquals(2, player.summary().cancelled)
 }

 @Test fun cancellationReschedulesOnlyFutureHeadsWithoutCountingThemTwice() {
  val clock = FakeClock(5000L)
  val output = FakeOutput(clock)
  val notes = listOf(1000L, 1065L, 1130L).map { PlayNote(it, 4f, NoteKind.TAP) }
  val player = ChartGesturePlayer(notes, clock, output) {}
  player.sync(5000L, 0L)
  assertEquals(notes, output.batches.single().notes)

  clock.advanceTo(5070L)
  output.complete(false)
  clock.advanceTo(5118L)
  assertEquals(notes.takeLast(1), output.batches.last().notes)
  assertEquals(5130L, output.sentAt.last() + output.delays.last())
  clock.advanceTo(5160L)
  output.complete()
  assertEquals(3, player.summary().submitted)
  assertEquals(1, player.summary().completed)
  assertEquals(2, player.summary().cancelled)
  assertEquals(1, player.summary().rescheduled)
  assertTrue(player.summary().finished)
 }

 @Test fun aSecondCancellationDoesNotRescheduleTheSameFutureHeadAgain() {
  val clock = FakeClock(5000L)
  val output = FakeOutput(clock)
  val notes = listOf(1000L, 1065L).map { PlayNote(it, 4f, NoteKind.TAP) }
  val player = ChartGesturePlayer(notes, clock, output) {}
  player.sync(5000L, 0L)
  output.complete(false)
  clock.advanceTo(5053L)
  output.complete(false)
  clock.advanceTo(5200L)
  assertEquals(2, output.batches.size)
  assertEquals(2, player.summary().cancelled)
  assertEquals(1, player.summary().rescheduled)
  assertTrue(player.summary().finished)
 }

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
