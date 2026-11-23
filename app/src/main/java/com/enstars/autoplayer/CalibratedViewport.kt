package com.enstars.autoplayer

import kotlin.math.floor

data class TouchPoint(val x: Float, val y: Float) {
 fun interpolate(to: TouchPoint, fraction: Float) =
  TouchPoint(x + (to.x - x) * fraction, y + (to.y - y) * fraction)
}

/** The overlay and injected touches share the same calibrated display rectangle. */
data class CalibratedViewport(val width: Int, val height: Int, val left: Int = 0, val top: Int = 0) {
 init {
  require(width > 0 && height > 0)
 }

 private val landscape = width >= height

 fun point(lane: Float, xs: List<Float>, ys: List<Float>): TouchPoint {
  val clamped = lane.coerceIn(0f, 8f)
  val low = floor(clamped).toInt()
  val high = (low + 1).coerceAtMost(8)
  val fraction = clamped - low
  val x = xs[low] * (1f - fraction) + xs[high] * fraction
  val y = ys[low] * (1f - fraction) + ys[high] * fraction
  return project(x, y)
 }

 fun project(x: Float, y: Float): TouchPoint {
  val local = if (landscape) TouchPoint(x * width, y * height) else TouchPoint(y * width, (1f - x) * height)
  return clamp(TouchPoint(local.x + left, local.y + top))
 }

 fun normalized(point: TouchPoint): TouchPoint {
  val x = ((point.x - left) / width).coerceIn(0f, 1f)
  val y = ((point.y - top) / height).coerceIn(0f, 1f)
  return if (landscape) TouchPoint(x, y) else TouchPoint(1f - y, x)
 }

 fun flick(point: TouchPoint, kind: NoteKind): TouchPoint {
  val amount = maxOf(width, height) * .058f
  val dx = when (kind) {
   NoteKind.FLICK_LEFT -> -amount
   NoteKind.FLICK_RIGHT -> amount
   else -> 0f
  }
  val dy = when (kind) {
   NoteKind.FLICK_UP -> -amount
   NoteKind.FLICK_DOWN -> amount
   else -> 0f
  }
  return clamp(if (landscape) TouchPoint(point.x + dx, point.y + dy) else TouchPoint(point.x + dy, point.y - dx))
 }

 fun nudge(point: TouchPoint): TouchPoint {
  val dx = if (point.x + .5f < left + width - 1) 1f else -1f
  val dy = if (point.y + .5f < top + height - 1) 1f else -1f
  return clamp(if (width > 1) TouchPoint(point.x + dx, point.y) else TouchPoint(point.x, point.y + dy))
 }

 private fun clamp(point: TouchPoint) = TouchPoint(
  point.x.coerceIn(left.toFloat(), (left + width - 1).toFloat()),
  point.y.coerceIn(top.toFloat(), (top + height - 1).toFloat())
 )
}
