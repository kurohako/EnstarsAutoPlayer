package com.enstars.autoplayer

import org.junit.Assert.*
import org.junit.Test

class GestureTimingTest {
 @Test fun denseRunsSplitAtStrokeBudgetWithoutDroppingNotes() {
  val notes = List(24) { index -> PlayNote(1000L + index * 20, (index % 9).toFloat(), NoteKind.TAP) }
  val plan = GestureBatcher.plan(notes)
  assertEquals(0, plan.overflow)
  assertEquals(notes, plan.batches.flatMap { it.notes })
  assertTrue(plan.batches.size > 1)
  assertValid(plan)
 }

 @Test fun repeatedTapsLiftBeforeTheNextSameLaneHead() {
  val notes = List(4) { index -> PlayNote(1000L + index * 20, 6f, NoteKind.TAP) }
  val plan = GestureBatcher.plan(notes)
  assertEquals(0, plan.overflow)
  val strokes = plan.batches.flatMap { it.strokes }
  assertEquals(notes.map { it.timeMs }, strokes.map { it.startMs })
  strokes.zipWithNext().forEach { (first, second) -> assertTrue(first.endMs <= second.startMs) }
  assertValid(plan)
 }

 @Test fun longHoldKeepsOverTenSuccessiveTapsAndItsFlickTail() {
  val hold = PlayNote(1000L, 0f, NoteKind.HOLD, 1850L, releaseKind = NoteKind.FLICK_RIGHT)
  val taps = List(18) { index -> PlayNote(1040L + index * 42, 4f, NoteKind.TAP) }
  val plan = GestureBatcher.plan(listOf(hold) + taps)
  val held = plan.batches.flatMap { it.strokes }.filter { it.note == hold }
  assertEquals(0, plan.overflow)
  assertEquals(taps, plan.batches.flatMap { it.notes }.filterNot { it == hold })
  assertEquals(1000L, held.first().startMs)
  assertEquals(1850L, held.last().startMs)
  assertEquals(1920L, held.last().endMs)
  assertEquals(NoteKind.FLICK_RIGHT, held.last().segment.to.flick)
  assertValid(plan)
 }

 @Test fun everySlideKnotAndReleaseKeepsThePointerAcrossPackets() {
  val waypoints = List(13) { index -> ChartWaypoint(1100L + index * 100, (index % 8).toFloat()) }
  val slide = PlayNote(1000L, 8f, NoteKind.SLIDE, 2300L, waypoints, releaseKind = NoteKind.FLICK_UP)
  val hold = PlayNote(1110L, 7f, NoteKind.HOLD, 1990L)
  val flick = PlayNote(1200L, 2f, NoteKind.FLICK_LEFT)
  val tap = PlayNote(1200L, 4f, NoteKind.TAP)
  val plan = GestureBatcher.plan(listOf(slide, hold, flick, tap))
  val strokes = plan.batches.flatMap { it.strokes }
  val slideStrokes = strokes.filter { it.note == slide }
  assertEquals(0, plan.overflow)
  assertEquals(waypoints.map { it.timeMs }, slideStrokes.dropLast(1).map { it.endMs })
  assertEquals(waypoints.map { it.lane }, slideStrokes.dropLast(1).map { it.segment.to.lane })
  assertEquals(NoteKind.FLICK_UP, slideStrokes.last().segment.to.flick)
  assertEquals(2370L, slideStrokes.last().endMs)
  assertEquals(1990L, strokes.last { it.note == hold }.endMs)
  assertEquals(1201L, strokes.single { it.note == tap }.startMs)
  assertEquals(NoteKind.FLICK_LEFT, strokes.single { it.note == flick }.segment.to.flick)
  assertValid(plan)
 }

 @Test fun aChordMovesTogetherWhenTheCurrentPacketCannotFitIt() {
  val notes = List(9) { index -> PlayNote(1000L + index * 35, 0f, NoteKind.TAP) } +
   listOf(PlayNote(1310L, 1f, NoteKind.TAP), PlayNote(1310L, 2f, NoteKind.TAP))
  val plan = GestureBatcher.plan(notes)
  assertEquals(0, plan.overflow)
  assertEquals(2, plan.batches.size)
  assertEquals(2, plan.batches.last().strokes.count { !it.continuesFromPrevious })
  assertValid(plan)
 }

 @Test fun exhaustedAndroidPointerIdsSkipOnlyTheUnrepresentableOverlap() {
  val hold = PlayNote(1000L, 8f, NoteKind.HOLD, 2200L)
  val taps = List(10) { index -> PlayNote(1040L + index * 50, 0f, NoteKind.TAP) }
  val flick = PlayNote(1540L, 1f, NoteKind.FLICK_LEFT)
  val competingTap = PlayNote(1550L, 3f, NoteKind.TAP)
  val laterTap = PlayNote(1640L, 3f, NoteKind.TAP)
  val plan = GestureBatcher.plan(listOf(hold, flick, competingTap, laterTap) + taps)
  val starts = plan.batches.flatMap { it.strokes }.filterNot { it.continuesFromPrevious }.map { it.note }
  assertEquals(1, plan.overflow)
  assertFalse(competingTap in starts)
  assertTrue(laterTap in starts)
  assertValid(plan)
 }

 private fun assertValid(plan: GestureBatcher.Plan) {
  var carry = emptySet<Int>()
  for (batch in plan.batches) {
   assertTrue(batch.strokes.size in 1..10)
   val continued = batch.strokes.filter { it.continuesFromPrevious }
   assertEquals(carry, continued.map { it.noteId }.toSet())
   assertTrue(continued.all { it.startMs == batch.startMs })
   for (stroke in batch.strokes) {
    assertTrue(stroke.startMs >= batch.startMs && stroke.endMs in (stroke.startMs + 1)..batch.endMs)
    if (continued.isNotEmpty() && !stroke.continuesFromPrevious) assertTrue(stroke.startMs > batch.startMs)
   }
   carry = batch.strokes.filter { it.continuesInNext }.map { it.noteId }.toSet()
  }
  assertTrue(carry.isEmpty())
  val starts = plan.batches.flatMap { it.strokes }.count { !it.continuesFromPrevious }
  assertEquals(plan.total, starts + plan.overflow)
 }
}
