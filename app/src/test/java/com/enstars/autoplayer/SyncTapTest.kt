package com.enstars.autoplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncTapTest {
 @Test fun onlyACompleteUncancelledTouchConfirmsSync() {
  val tap = SyncTap()
  assertNull(tap.release(10L))
  tap.press()
  tap.cancel()
  assertNull(tap.release(20L))
  tap.press()
  assertEquals(42L, tap.release(42L))
  tap.press()
  assertNull(tap.release(60L))
 }
}
