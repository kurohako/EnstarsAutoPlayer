package com.enstars.autoplayer

import org.junit.Assert.*
import org.junit.Test

class BundledChartSupplementTest {
 // Synthetic inputs exercise the same merge and planning code used by the app.
 private val bundled = """
  {"format":"enstars-video-v2","sourceVideo":"${BundledVideoChart.SOURCE_VIDEO}","notes":[
   {"timeMs":1000,"lane":5,"type":"tap","confidence":"high"},
   {"timeMs":1360,"lane":2,"type":"tap","confidence":"medium"},
   {"timeMs":1360,"lane":8,"type":"tap","confidence":"high"}
  ]}
 """.trimIndent()

 @Test fun addedTapAndVerifiedHoldUseTheBundledClockAndPlayTogether() {
  val supplement = """
   {"format":"enstars-video-v2","notes":[
    {"timeMs":1350,"replaceTimeMs":1360,"lane":2,"type":"hold","confidence":"high","endTimeMs":2450},
    {"timeMs":1510,"lane":6,"type":"tap","confidence":"high"}
   ]}
  """.trimIndent()
  val chart = ChartParser.parse(BundledChartSupplement.apply(bundled, supplement))
  assertEquals(4, chart.events.size)
  assertEquals(1000L, chart.events.first().timeMs)
  assertEquals(NoteKind.TAP, chart.events.first().kind)
  assertEquals(1, chart.counts[NoteKind.HOLD])
  assertTrue(chart.events.any { it.timeMs == 1360L && it.lane == 7f })
  assertFalse(chart.events.any { it.timeMs == 1360L && it.lane == 1f })
  val plan = GestureBatcher.plan(chart.events, isolateFirstChord = true)
  assertEquals(0, plan.overflow)
  val hold = plan.batches.flatMap { it.strokes }.filter { it.note.kind == NoteKind.HOLD }
  assertEquals(1350L, hold.first().startMs)
  assertTrue(hold.dropLast(1).all { it.continuesInNext })
  assertEquals(2450L, hold.last().endMs)
  assertFalse(hold.last().continuesInNext)
  assertTrue(plan.batches.any { batch ->
   batch.strokes.any { it.note.timeMs == 1510L } && batch.strokes.any { it.note.kind == NoteKind.HOLD }
  })
 }

 @Test fun aHoldWithoutATailCannotReplaceAnExistingCandidate() {
  val supplement = """
   {"format":"enstars-video-v2","notes":[
    {"timeMs":1360,"lane":2,"type":"hold","confidence":"high"}
   ]}
  """.trimIndent()
  assertThrows(IllegalArgumentException::class.java) {
   BundledChartSupplement.apply(bundled, supplement)
  }
 }

 @Test fun aRetimeMustNameAnExistingHeadOnTheSameLane() {
  val supplement = """
   {"format":"enstars-video-v2","notes":[
    {"timeMs":1370,"replaceTimeMs":1360,"lane":3,"type":"tap","confidence":"high"}
   ]}
  """.trimIndent()
  assertThrows(IllegalArgumentException::class.java) {
   BundledChartSupplement.apply(bundled, supplement)
  }
 }
}
