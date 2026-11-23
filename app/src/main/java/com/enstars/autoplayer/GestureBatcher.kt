package com.enstars.autoplayer

/** Plans Android gestures on the chart clock. Every continued pointer occupies one stroke. */
object GestureBatcher {
 private const val MAX_STROKES = 10
 private const val MAX_BATCH_MS = 500L
 private const val MERGE_GAP_MS = 65L
 const val TAP_MS = 30L

 data class Position(val lane: Float, val flick: NoteKind? = null)
 data class Segment(val startMs: Long, val endMs: Long, val from: Position, val to: Position)
 data class Stroke(
  val noteId: Int,
  val note: PlayNote,
  val segment: Segment,
  val startMs: Long,
  val endMs: Long,
  val continuesFromPrevious: Boolean,
  val continuesInNext: Boolean
 )
 data class Batch(val startMs: Long, val endMs: Long, val strokes: List<Stroke>) {
  val notes: List<PlayNote> get() = strokes.map { it.note }
 }
 data class Plan(val batches: List<Batch>, val overflow: Int, val total: Int)
 private data class Track(val id: Int, val note: PlayNote, var segments: List<Segment>)
 private data class ActiveSegment(val track: Track, val index: Int) {
  val segment get() = track.segments[index]
  val hasNext get() = index + 1 < track.segments.size
 }
 private data class Pointer(val track: Track, val id: Int) {
  fun canLiftTapAt(timeMs: Long) = track.note.kind == NoteKind.TAP && track.note.timeMs < timeMs
 }

 fun plan(events: List<PlayNote>, isolateFirstChord: Boolean = false): Plan {
  val sorted = events.filter {
   it.lane in 0f..8f && it.timeMs in 0L..Long.MAX_VALUE - MAX_BATCH_MS &&
    (!it.kind.isSustained || it.endTimeMs in (it.timeMs + 1)..Long.MAX_VALUE - MAX_BATCH_MS)
  }.distinctBy { it.copy(confidence = "") }
   .sortedWith(compareBy<PlayNote> { it.timeMs }.thenByDescending { it.kind.isSustained }.thenBy { it.lane })
  val nextHeadOnLane = sorted.groupBy { it.lane }.mapValues { (_, notes) ->
   notes.map { it.timeMs }.distinct().zipWithNext().toMap()
  }
  val tracks = sorted.mapIndexed { id, note ->
   Track(id, note, segments(note, nextHeadOnLane.getValue(note.lane)[note.timeMs]))
  }

  // In Android 16 the injector keeps used pointer IDs until the last pointer goes up.
  // Once IDs 0..9 have been used, overlapping new pointers would both receive ID 10.
  val pointers = mutableListOf<Pointer>()
  var nextPointerId = 0
  val playable = tracks.filter { track ->
   val time = track.note.timeMs
   pointers.removeAll { it.track.segments.last().endMs <= time }
   val reused = pointers.firstOrNull { it.id == MAX_STROKES }
   if (reused != null || pointers.size == MAX_STROKES) {
    val blocker = reused ?: pointers.firstOrNull { it.canLiftTapAt(time) }
    if (blocker == null || !blocker.canLiftTapAt(time)) return@filter false
    // Preserve other-lane tap duration unless a pointer must lift to admit the new head.
    val tap = blocker.track.segments.single()
    blocker.track.segments = listOf(tap.copy(endMs = maxOf(tap.startMs + 1L, time - 1L)))
    pointers.remove(blocker)
   }
   if (pointers.isEmpty()) nextPointerId = 0
   pointers.add(Pointer(track, nextPointerId.coerceAtMost(MAX_STROKES)))
   nextPointerId++
   true
  }.sortedWith(compareBy<Track> { it.note.timeMs }.thenBy { it.note.lane })

  val batches = mutableListOf<Batch>()
  var overflow = tracks.size - playable.size
  var nextNote = 0
  var continuing = emptyList<ActiveSegment>()
  var previousEnd = 0L
  while (nextNote < playable.size || continuing.isNotEmpty()) {
   val start = if (continuing.isEmpty()) maxOf(previousEnd, playable[nextNote].note.timeMs) else previousEnd
   // Android validates all pointers at the first step of a continuation.
   // New fingers at the same boundary start one millisecond later.
   val earliestHead = start + if (continuing.isEmpty()) 0 else 1
   val window = continuing.toMutableList()
   var boundary = start + MAX_BATCH_MS
   if (isolateFirstChord && batches.isEmpty()) {
    val nextHeadTime = playable.firstOrNull { it.note.timeMs > start }?.note?.timeMs
    if (nextHeadTime != null) boundary = minOf(boundary, nextHeadTime)
   }
   var latestEnd = start
   fun include(active: ActiveSegment) {
    latestEnd = maxOf(latestEnd, active.segment.endMs)
    if (active.hasNext) boundary = minOf(boundary, active.segment.endMs)
   }
   continuing.forEach(::include)
   while (nextNote < playable.size) {
    val time = maxOf(playable[nextNote].note.timeMs, earliestHead)
    if (time >= boundary || time - latestEnd > MERGE_GAP_MS) break
    var chordEnd = nextNote + 1
    while (chordEnd < playable.size && playable[chordEnd].note.timeMs <= time) chordEnd++
    val chord = playable.subList(nextNote, chordEnd).filter { it.segments.first().endMs > time }
    if (window.size + chord.size > MAX_STROKES || window.any { it.segment.endMs == time }) {
     boundary = time
     break
    }
    chord.forEach { track ->
     val active = ActiveSegment(track, 0)
     window.add(active)
     include(active)
    }
    overflow += chordEnd - nextNote - chord.size
    nextNote = chordEnd
   }

   if (window.isEmpty()) continue
   val end = minOf(boundary, latestEnd)
   val strokes = window.mapIndexed { index, active ->
    val continuation = index < continuing.size
    val begin = if (continuation) start else maxOf(active.segment.startMs, earliestHead)
    Stroke(
     active.track.id, active.track.note, active.segment, begin, minOf(end, active.segment.endMs),
     continuation, active.segment.endMs > end || (active.segment.endMs == end && active.hasNext)
    )
   }
   batches.add(Batch(start, end, strokes))
   continuing = window.filter { it.segment.endMs > end || (it.segment.endMs == end && it.hasNext) }
    .map { if (it.segment.endMs == end) it.copy(index = it.index + 1) else it }
   previousEnd = end
  }
  return Plan(batches, overflow, tracks.size)
 }

 private fun segments(note: PlayNote, nextHeadOnLane: Long?): List<Segment> {
  val head = Position(note.lane)
  if (note.kind == NoteKind.TAP) {
   val naturalEnd = note.timeMs + TAP_MS
   val end = if (nextHeadOnLane != null && nextHeadOnLane <= naturalEnd) {
    maxOf(note.timeMs + 1L, nextHeadOnLane - 1L)
   } else naturalEnd
   return listOf(Segment(note.timeMs, end, head, head))
  }
  if (!note.kind.isSustained) {
   return listOf(Segment(note.timeMs, note.timeMs + 75L, head, head.copy(flick = note.kind)))
  }

  val points = mutableListOf(ChartWaypoint(note.timeMs, note.lane))
  if (note.kind == NoteKind.SLIDE) {
   points.addAll(note.path.filter {
    it.timeMs in (note.timeMs + 1)..note.endTimeMs && it.lane in 0f..8f
   }.sortedBy { it.timeMs }.distinctBy { it.timeMs })
  }
  if (points.last().timeMs < note.endTimeMs) {
   points.add(ChartWaypoint(note.endTimeMs, points.last().lane))
  }
  val result = points.zipWithNext { from, to ->
   Segment(from.timeMs, to.timeMs, Position(from.lane), Position(to.lane))
  }.toMutableList()
  note.releaseKind?.let { flick ->
   val tail = Position(points.last().lane)
   result.add(Segment(note.endTimeMs, note.endTimeMs + 70L, tail, tail.copy(flick = flick)))
  }
  return result
 }
}
