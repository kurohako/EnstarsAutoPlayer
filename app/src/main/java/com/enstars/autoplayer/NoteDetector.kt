package com.enstars.autoplayer

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import kotlin.math.abs

/**
 * Lightweight per-lane motion tracker. Unlike a static brightness trigger,
 * it estimates the arrival of a moving bright cluster at the hit line.
 * This is a generic prototype, NOT a trained Enstars note recognizer.
 */
class NoteDetector(private val config: GameConfig) {
    data class Hit(val lane: Int, val delayMs: Long)
    private data class Track(var y: Float, var timeMs: Long, var velocity: Float = 0f, var fired: Boolean = false)
    private val tracks = arrayOfNulls<Track>(config.laneXs.size)
    private val lastEmitted = LongArray(config.laneXs.size)
    private val maxLookaheadMs = 110L

    fun detect(bitmap: Bitmap, capturedAtMs: Long = SystemClock.uptimeMillis()): List<Hit> {
        val width = bitmap.width
        val height = bitmap.height
        val judgeY = config.hitY * height
        val result = mutableListOf<Hit>()
        config.laneXs.forEachIndexed { lane, normalizedX ->
            val x = (normalizedX * width).toInt().coerceIn(0, width - 1)
            val radius = (width * .012f).toInt().coerceAtLeast(2)
            val startY = (judgeY - height * .28f).toInt().coerceAtLeast(0)
            val endY = (judgeY + height * .015f).toInt().coerceAtMost(height - 1)
            val stride = (height / 260).coerceAtLeast(2)
            var candidate: Float? = null
            for (y in startY..endY step stride) {
                var count = 0
                var total = 0
                for (xx in (x - radius).coerceAtLeast(0)..(x + radius).coerceAtMost(width - 1) step 3) {
                    val c = bitmap.getPixel(xx, y)
                    val luma = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                    if (luma >= config.threshold) count++
                    total++
                }
                if (total > 0 && count.toFloat() / total > .60f) candidate = y.toFloat()
            }
            val previous = tracks[lane]
            if (candidate == null) {
                if (previous != null && capturedAtMs - previous.timeMs > 350) tracks[lane] = null
                return@forEachIndexed
            }
            val currentY = candidate!!
            if (previous == null || capturedAtMs - previous.timeMs > 350 ||
                currentY < previous.y - height * .04f) {
                tracks[lane] = Track(currentY, capturedAtMs)
                return@forEachIndexed
            }
            val elapsed = (capturedAtMs - previous.timeMs).coerceAtLeast(1)
            val delta = currentY - previous.y
            if (delta > 0 && delta < height * .16f) {
                val measured = delta / elapsed
                previous.velocity = if (previous.velocity > 0) previous.velocity * .55f + measured * .45f else measured
            }
            previous.y = currentY
            previous.timeMs = capturedAtMs
            if (previous.fired || previous.velocity <= .03f) return@forEachIndexed
            val eta = ((judgeY - currentY) / previous.velocity).toLong()
            if (eta in 0..maxLookaheadMs && capturedAtMs - lastEmitted[lane] > config.cooldownMs) {
                result.add(Hit(lane, eta))
                previous.fired = true
                lastEmitted[lane] = capturedAtMs
            }
        }
        return result
    }
}
