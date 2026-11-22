package com.enstars.autoplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibratedViewportTest {
 @Test fun savedDragCoordinatesAndHalfLaneUseSameOffsetLandscapeRectangle() {
  val bounds = CalibratedViewport(1200, 620, left = 35, top = 24)
  val first = TouchPoint(435f, 374f)
  val second = TouchPoint(535f, 424f)
  val drag = bounds.normalized(first)
  val next = bounds.normalized(second)
  val xs = List(9) { if (it == 3) drag.x else next.x }
  val ys = List(9) { if (it == 3) drag.y else next.y }
  assertPoint(first, bounds.point(3f, xs, ys))
  assertPoint(TouchPoint(485f, 399f), bounds.point(3.5f, xs, ys))

  val drawingBounds = CalibratedViewport(bounds.width, bounds.height)
  val drawn = drawingBounds.point(3.5f, xs, ys)
  assertPoint(TouchPoint(drawn.x + bounds.left, drawn.y + bounds.top), bounds.point(3.5f, xs, ys))
 }

 @Test fun calibratedPortraitRoundTripsAndRotatesFlickDirections() {
  val bounds = CalibratedViewport(620, 1200, left = 35, top = 24)
  val drag = TouchPoint(310f, 540f)
  val saved = bounds.normalized(drag)
  val center = bounds.point(4f, List(9) { saved.x }, List(9) { saved.y })
  assertPoint(drag, center)
  val rightFlick = bounds.flick(center, NoteKind.FLICK_RIGHT)
  assertEquals(center.x, rightFlick.x, .01f)
  assertTrue(rightFlick.y < center.y)
  val bottom = bounds.project(0f, 1f)
  assertEquals(TouchPoint(654f, 1223f), bottom)
 }

 private fun assertPoint(expected: TouchPoint, actual: TouchPoint) {
  assertEquals(expected.x, actual.x, .01f)
  assertEquals(expected.y, actual.y, .01f)
 }
}
