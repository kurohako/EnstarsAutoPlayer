package com.enstars.autoplayer

/** A physical press can start the chart once, after its input stream ends. */
internal class SyncTap {
 private var pressed = false
 private var confirmed = false

 fun press() {
  pressed = !confirmed
 }

 fun cancel() {
  pressed = false
 }

 fun release(eventTimeMs: Long): Long? {
  if (!pressed) return null
  pressed = false
  confirmed = true
  return eventTimeMs
 }
}
