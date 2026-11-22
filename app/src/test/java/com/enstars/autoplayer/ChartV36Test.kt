package com.enstars.autoplayer

import org.junit.Assert.*
import org.junit.Test

class ChartV36Test {
 @Test fun textSupportsFlickHoldSlideAndRelativeTimes(){
  val source="""
EXPERT_LEVEL_28
1#0.500:4:120
1:0_0+0/4@O-4@R1
_1+0/4@O2=+2/4@O2
_2+0/4@O-1=_3+0/4@O1
_3+2/4@L-3
""".trimIndent()
  val chart=ChartParser.parse(source)
  assertEquals("EXPERT_LEVEL_28",chart.difficulty)
  assertEquals(5,chart.events.size)
  val tap=chart.events.first{it.kind==NoteKind.TAP}
  assertEquals(500L,tap.timeMs)
  assertEquals(0f,tap.lane)
  assertTrue(chart.events.any{it.kind==NoteKind.FLICK_RIGHT})
  assertTrue(chart.events.any{it.kind==NoteKind.FLICK_LEFT})
  val hold=chart.events.first{it.kind==NoteKind.HOLD}
  assertEquals(1000L,hold.timeMs)
  assertEquals(1250L,hold.endTimeMs)
  val slide=chart.events.first{it.kind==NoteKind.SLIDE}
  assertEquals(1500L,slide.timeMs)
  assertEquals(2000L,slide.endTimeMs)
  assertEquals(5f,slide.path.last().lane)
 }
 @Test fun visualJsonSupportsTypedEventsButSkipsUnknownColor(){
  val source="""{"format":"enstars-video-v2","notes":[
  {"timeMs":6833,"lane":6,"type":"tap","confidence":"high"},
  {"timeMs":7200,"lane":2,"type":"flick_right","confidence":"medium"},
  {"timeMs":7600,"lane":8,"type":"hold","endTimeMs":8150,"confidence":"high"},
  {"timeMs":8400,"lane":4,"type":"slide","endTimeMs":9500,"path":[
    {"timeMs":8900,"lane":5},{"timeMs":9500,"lane":6}],"confidence":"medium"},
  {"timeMs":8600,"lane":2,"type":"orange_candidate","confidence":"medium"}
]}"""
  val chart=ChartParser.parse(source)
  assertEquals(4,chart.events.size)
  assertEquals(1,chart.skipped)
  assertTrue(chart.events.any{it.kind==NoteKind.FLICK_RIGHT})
  assertTrue(chart.events.any{it.kind==NoteKind.HOLD})
  assertEquals(2,chart.events.first{it.kind==NoteKind.SLIDE}.path.size)
 }
 @Test fun overlappingHoldAndTapAreOneGestureBatch(){
  val notes=listOf(
   PlayNote(1000,3f,NoteKind.HOLD,2000),
   PlayNote(1250,4f,NoteKind.TAP),
   PlayNote(1500,5f,NoteKind.FLICK_LEFT),
   PlayNote(2400,6f,NoteKind.TAP)
  )
  val plan=GestureBatcher.plan(notes)
  assertEquals(2,plan.batches.size)
  assertEquals(3,plan.batches[0].notes.size)
  assertEquals(0,plan.overflow)
 }
 @Test fun relativeBeatSyntaxKeepsBarContext(){
  val chart=ChartParser.parse("""
HARD_LEVEL_20
1#0.000:4:120
1:5_0+0/4@O0
_1+0/4@O1
+2/4@O2
:6_0+0/4@O3
""".trimIndent())
  assertEquals(4,chart.events.size)
  assertEquals(10000L,chart.events[0].timeMs)
  assertEquals(10500L,chart.events[1].timeMs)
  assertEquals(10750L,chart.events[2].timeMs)
  assertEquals(12000L,chart.events[3].timeMs)
 }
}
