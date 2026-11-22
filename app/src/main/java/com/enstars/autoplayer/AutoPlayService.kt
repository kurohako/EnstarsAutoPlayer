package com.enstars.autoplayer

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Fixed-coordinate debug mode. No screenshot scanning and no touch dispatch. */
class AutoPlayService:AccessibilityService(){
 companion object { @Volatile var instance:AutoPlayService?=null; private set }
 private lateinit var overlay:DebugOverlay
 override fun onServiceConnected(){
  super.onServiceConnected()
  instance=this
  overlay=DebugOverlay(this)
 }
 override fun onAccessibilityEvent(event:AccessibilityEvent?)=Unit
 override fun onInterrupt(){stop()}
 override fun onDestroy(){stop();instance=null;super.onDestroy()}
 fun start(){
  if(!::overlay.isInitialized)return
  val config=GameConfig.load(this)
  overlay.show()
  overlay.showFixed(config)
 }
 fun restartCalibration(){
  // Compatibility: refresh saved fixed coordinates, do not run recognition.
  start()
 }
 fun stop(){
  if(::overlay.isInitialized)overlay.close()
 }
}
