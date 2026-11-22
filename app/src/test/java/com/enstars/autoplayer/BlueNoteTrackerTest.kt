package com.enstars.autoplayer

import org.junit.Assert.*
import org.junit.Test

class BlueNoteTrackerTest {
 @Test fun scoresKeepTwoNearbyBluePeaksIncludingAFlatTop() {
  val scores = FloatArray(BlueNoteTracker.SAMPLE_COUNT)
  listOf(.08f, .22f, .22f, .14f, .17f, .24f, .18f, .09f).forEachIndexed { i, score ->
   scores[i + 22] = score
  }
  val peaks = BlueNoteTracker.peaks(scores)
  assertEquals(2, peaks.size)
  assertEquals(.62f, peaks[0], .001f)
  assertEquals(.69f, peaks[1], .001f)
 }

 @Test fun separatelyTrackedHeadsOnOneLaneCanHitLessThan260MsApart() {
  val tracker = BlueNoteTracker()
  assertTrue(tracker.update(4, listOf(.64f, .73f), 1000L).isEmpty())
  val hits = tracker.update(4, listOf(.79f, .88f), 1200L).sortedBy { it.targetAtMs }
  assertEquals(2, hits.size)
  assertEquals(listOf(4, 4), hits.map { it.lane })
  assertTrue(hits[1].targetAtMs - hits[0].targetAtMs in 118L..122L)
  assertTrue(tracker.update(4, listOf(.82f, .91f), 1240L).isEmpty())
 }

 @Test fun rateLimitedFramesCanTrackFastMotionButIgnoreAStationaryPeak() {
  val tracker = BlueNoteTracker()
  assertTrue(tracker.update(1, listOf(.18f, .34f, .95f), 1000L).isEmpty())
  val leading = tracker.update(1, listOf(.52f, .68f, .95f), 1350L)
  assertEquals(1, leading.size)
  val trailing = tracker.update(1, listOf(.86f, .95f), 1700L)
  assertEquals(1, trailing.size)
  assertTrue(trailing.single().targetAtMs - leading.single().targetAtMs in 160L..170L)
  assertTrue(tracker.update(1, listOf(.95f), 2050L).isEmpty())
 }
}
