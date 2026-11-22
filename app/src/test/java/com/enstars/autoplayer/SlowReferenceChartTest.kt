package com.enstars.autoplayer

import org.junit.Assert.*
import org.junit.Test

class SlowReferenceChartTest {
 @Test fun bundledOriginalAndRefinedCounts(){
  val old=ChartParser.parse(BundledVideoChart.asJson(true,false))
  val refined=ChartParser.parse(BundledVideoChart.asJson(true,true))
  assertEquals(508,old.counts[NoteKind.TAP])
  assertEquals(24,old.counts[NoteKind.FLICK_RIGHT])
  assertEquals(42,old.counts[NoteKind.FLICK_LEFT])
  assertEquals(550,refined.counts[NoteKind.TAP])
  assertEquals(31,refined.counts[NoteKind.FLICK_RIGHT])
  assertEquals(42,refined.counts[NoteKind.FLICK_LEFT])
  assertEquals(623,refined.events.size)
  for (chart in listOf(old,refined)){
   val plan=GestureBatcher.plan(chart.events)
   assertEquals(0,plan.overflow)
   assertEquals(chart.events.size,plan.batches.sumOf{batch->batch.strokes.count{!it.continuesFromPrevious}})
  }
 }
 @Test fun slowReferenceCanDisableUnverifiedFlicks(){
  val chart=ChartParser.parse(BundledVideoChart.asJson(false,true))
  assertEquals(550,chart.events.size)
  assertTrue(chart.events.all{it.kind==NoteKind.TAP})
 }
 @Test fun correctionAndFirstSyncArePreserved(){
  val chart=ChartParser.parse(BundledVideoChart.asJson(true,true))
  assertTrue(chart.events.first().timeMs==6833L)
  assertTrue(chart.events.any{it.kind==NoteKind.TAP&&it.lane==7f&&it.timeMs==7541L})
  assertTrue(chart.events.any{it.kind==NoteKind.FLICK_RIGHT&&it.lane==8f&&it.timeMs==64700L})
  assertTrue(chart.events.any{it.kind==NoteKind.TAP&&it.lane==6f&&it.timeMs==24033L})
 }
}
