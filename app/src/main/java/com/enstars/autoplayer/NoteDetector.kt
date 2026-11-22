package com.enstars.autoplayer

import android.graphics.Bitmap
import android.graphics.Color

/** Samples moving blue tap heads along the nine calibrated lane rays. */
internal class NoteDetector(private val config: GameConfig) {
 private val tracker = BlueNoteTracker()
 private var pixels = IntArray(0)

 fun detect(bitmap: Bitmap, viewport: CalibratedViewport, capturedAtMs: Long): List<BlueNoteTracker.Hit> {
  val width = bitmap.width
  val height = bitmap.height
  if (pixels.size < width * height) pixels = IntArray(width * height)
  bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
  val radius = (minOf(viewport.width, viewport.height) * .022f).toInt().coerceAtLeast(5)
  val origin = viewport.project(.525f, .115f)
  val scores = FloatArray(BlueNoteTracker.SAMPLE_COUNT)
  val hits = mutableListOf<BlueNoteTracker.Hit>()
  for (lane in 0..8) {
   val target = viewport.point(lane.toFloat(), config.laneXs, config.laneYs)
   for (index in scores.indices) {
    val point = origin.interpolate(target, BlueNoteTracker.progress(index))
    var blue = 0
    var total = 0
    for (dy in -radius..radius step 3) {
     for (dx in -radius..radius step 3) {
      if (dx * dx + dy * dy > radius * radius) continue
      val x = point.x.toInt() + dx
      val y = point.y.toInt() + dy
      if (x !in 0 until width || y !in 0 until height) continue
      val color = pixels[y * width + x]
      val r = Color.red(color)
      val g = Color.green(color)
      val b = Color.blue(color)
      if (b > 115 && b - r > 20 && b - g > 3 && b > r * 1.12f) blue++
      total++
     }
    }
    scores[index] = if (total == 0) 0f else blue.toFloat() / total
   }
   hits.addAll(tracker.update(lane, BlueNoteTracker.peaks(scores), capturedAtMs))
  }
  return hits
 }
}
