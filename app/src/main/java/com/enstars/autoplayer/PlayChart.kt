package com.enstars.autoplayer

/** All times are song/video relative in milliseconds; lanes are 0..8, half lanes allowed. */
enum class NoteKind { TAP, FLICK_LEFT, FLICK_RIGHT, FLICK_UP, FLICK_DOWN, HOLD, SLIDE }

data class ChartWaypoint(val timeMs:Long,val lane:Float)

data class PlayNote(
 val timeMs:Long,
 val lane:Float,
 val kind:NoteKind,
 val endTimeMs:Long=timeMs,
 val path:List<ChartWaypoint> = emptyList(),
 val confidence:String="source",
 val releaseKind:NoteKind?=null
) {
 fun end():Long = when(kind) {
  NoteKind.TAP -> timeMs+32L
  NoteKind.FLICK_LEFT,NoteKind.FLICK_RIGHT,NoteKind.FLICK_UP,NoteKind.FLICK_DOWN -> timeMs+85L
  NoteKind.HOLD,NoteKind.SLIDE -> maxOf(timeMs+32L,endTimeMs)+(if(releaseKind!=null)70L else 0L)
 }
}

/**
 * Accessible gesture dispatch cancels any previous gesture. Merge overlapping
 * notes into ONE gesture (multiple strokes) rather than dispatching separately.
 *
 * Android limits one gesture to 10 strokes. Excess simultaneous notes are
 * counted as skipped rather than silently cancelling the preceding hold.
 */
object GestureBatcher {
 data class Batch(val startMs:Long,val endMs:Long,val notes:List<PlayNote>)
 data class Plan(val batches:List<Batch>,val overflow:Int)
 fun plan(events:List<PlayNote>):Plan {
  val sorted=events.filter{it.lane in 0f..8f && it.timeMs>=0}
   .distinctBy{listOf(it.timeMs,it.lane,it.kind,it.endTimeMs)}
   .sortedWith(compareBy<PlayNote>{it.timeMs}.thenBy{it.lane})
  val batches=mutableListOf<Batch>()
  var overflow=0
  var group=mutableListOf<PlayNote>()
  var start=0L;var end=0L
  fun flush(){
   if(group.isEmpty())return
   // Prefer holds and slides: cancelling them would lose several judgments.
   val allowed=group.sortedWith(compareByDescending<PlayNote>{
    it.kind==NoteKind.HOLD||it.kind==NoteKind.SLIDE
   }.thenBy{it.timeMs}).take(10)
   overflow+=group.size-allowed.size
   batches.add(Batch(start,end,allowed.sortedBy{it.timeMs}))
   group=mutableListOf()
  }
  for(note in sorted) {
   val finish=note.end()
   // Merge near-consecutive taps as one gesture to prevent Android from
   // cancelling a gesture whose last input event is still being processed.
   // 65ms tolerance is bounded by platform stroke limit below.
   if(group.isNotEmpty() && group.size>=10 && note.timeMs<=end){
    // Impossible to send >10 overlapping strokes. Do not create a second
    // simultaneous gesture, which would cancel the first ten touches.
    overflow++
    continue
   }
   if(group.isNotEmpty() && (note.timeMs>end+65L || group.size>=10))flush()
   if(group.isEmpty()){start=note.timeMs;end=finish}
   else end=maxOf(end,finish)
   group.add(note)
  }
  flush()
  return Plan(batches,overflow)
 }
}
