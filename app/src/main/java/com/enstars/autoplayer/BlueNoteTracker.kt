package com.enstars.autoplayer

import kotlin.math.abs

/** Associates separate blue heads on each lane across sampled frames. */
internal class BlueNoteTracker {
 data class Hit(val lane: Int, val targetAtMs: Long)
 private data class Track(
  var progress: Float,
  var seenAt: Long,
  var velocity: Float = 0f,
  var fired: Boolean = false
 )
 private val lanes = List(9) { mutableListOf<Track>() }

 fun update(lane: Int, peaks: List<Float>, capturedAtMs: Long): List<Hit> {
  val tracks = lanes[lane]
  tracks.removeAll { track ->
   val elapsed = capturedAtMs - track.seenAt
   elapsed > 800L || (track.fired && track.progress + track.velocity * elapsed > 1.08f)
  }
  tracks.sortBy { it.progress }
  val assignments = associate(tracks, peaks, capturedAtMs)
  val hits = mutableListOf<Hit>()
  for ((peak, trackIndex) in assignments.withIndex()) {
   val position = peaks[peak]
   if (trackIndex < 0) {
    tracks.add(Track(position, capturedAtMs))
    continue
   }
   val track = tracks[trackIndex]
   val advance = position - track.progress
   if (advance > .006f) {
    val velocity = advance / (capturedAtMs - track.seenAt)
    track.velocity = if (track.velocity > 0f) track.velocity * .45f + velocity * .55f else velocity
   }
   track.progress = position
   track.seenAt = capturedAtMs
   if (!track.fired && advance > .006f && track.velocity >= .00005f) {
    val eta = ((1f - position) / track.velocity).toLong()
    if (eta in 0L..350L) {
     hits.add(Hit(lane, capturedAtMs + eta))
     track.fired = true
    }
   }
  }
  return hits
 }

 private fun associate(tracks: List<Track>, peaks: List<Float>, capturedAtMs: Long): IntArray {
  // Heads travel in order along a lane. Prefer more matches, then lower prediction error.
  val score = Array(tracks.size + 1) { FloatArray(peaks.size + 1) }
  val match = Array(tracks.size) { BooleanArray(peaks.size) }
  val reward = (peaks.size + 1).toFloat()
  for (i in tracks.indices.reversed()) {
   val track = tracks[i]
   val elapsed = capturedAtMs - track.seenAt
   val expected = track.progress + (if (track.velocity > 0f) track.velocity else .0007f) * elapsed
   val tolerance = if (track.velocity > 0f) .10f + elapsed * .00015f else .30f
   for (j in peaks.indices.reversed()) {
    val skipped = maxOf(score[i + 1][j], score[i][j + 1])
    val error = abs(expected - peaks[j])
    val valid = elapsed > 0L && peaks[j] >= track.progress - .025f && error <= tolerance
    val matched = if (valid) score[i + 1][j + 1] + reward - error else -1f
    match[i][j] = matched > skipped
    score[i][j] = maxOf(skipped, matched)
   }
  }
  val assignments = IntArray(peaks.size) { -1 }
  var i = 0
  var j = 0
  while (i < tracks.size && j < peaks.size) {
   when {
    match[i][j] -> assignments[j++] = i++
    score[i + 1][j] >= score[i][j + 1] -> i++
    else -> j++
   }
  }
  return assignments
 }

 companion object {
  private const val FIRST_PROGRESS = .15f
  private const val STEP = .02f
  const val SAMPLE_COUNT = 41

  fun progress(index: Int) = FIRST_PROGRESS + index * STEP

  fun peaks(scores: FloatArray): List<Float> {
   val result = mutableListOf<Float>()
   var start = 0
   while (start < scores.size) {
    val score = scores[start]
    var end = start
    while (end + 1 < scores.size && scores[end + 1] == score) end++
    val left = scores.getOrElse(start - 1) { 0f }
    val right = scores.getOrElse(end + 1) { 0f }
    val leftValley = minOf(left, scores.getOrElse(start - 2) { 0f })
    val rightValley = minOf(right, scores.getOrElse(end + 2) { 0f })
    if (score > .10f && score > maxOf(left, right) &&
     score - maxOf(leftValley, rightValley) >= .015f) {
     result.add(FIRST_PROGRESS + (start + end) * .5f * STEP)
    }
    start = end + 1
   }
   return result
  }
 }
}
