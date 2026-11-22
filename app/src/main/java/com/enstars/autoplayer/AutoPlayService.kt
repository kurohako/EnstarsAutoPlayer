package com.enstars.autoplayer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent

/** Experimental tap-only autoplay. Requires manual nine-point calibration. */
class AutoPlayService:AccessibilityService(){
 companion object{@Volatile var instance:AutoPlayService?=null;private set}
 private val handler=Handler(Looper.getMainLooper())
 private lateinit var overlay:DebugOverlay
 private var playing=false
 private var busy=false
 private var generation=0
 private var config=GameConfig()
 private var detector=NoteDetector(config)
 private val scheduled=mutableListOf<Runnable>()
 override fun onServiceConnected(){super.onServiceConnected();instance=this;overlay=DebugOverlay(this)}
 override fun onAccessibilityEvent(event:AccessibilityEvent?)=Unit
 override fun onInterrupt(){stop()}
 override fun onDestroy(){stop();instance=null;super.onDestroy()}
 fun start(){
  stopPlayback()
  config=GameConfig.load(this);detector=NoteDetector(config)
  overlay.show();overlay.showFixed(config)
 }
 fun restartCalibration(){start()}
 fun editPoints(){
  stopPlayback()
  config=GameConfig.load(this)
  overlay.show()
  overlay.edit(config){config=GameConfig.load(this);detector=NoteDetector(config)}
 }
 fun startAutoplay(){
  if(!::overlay.isInitialized)return
  start()
  playing=true
  overlay.updateStatus("TAP ONLY - EXPERIMENTAL")
  handler.post(::capture)
 }
 private fun stopPlayback(){
  playing=false;generation++
  handler.removeCallbacksAndMessages(null)
  scheduled.clear()
 }
 fun stop(){stopPlayback();if(::overlay.isInitialized)overlay.close()}
 private fun capture(){
  if(!playing)return
  if(busy){handler.postDelayed(::capture,80L);return}
  busy=true
  val epoch=generation
  try{
   takeScreenshot(Display.DEFAULT_DISPLAY,mainExecutor,object:TakeScreenshotCallback{
    override fun onSuccess(result:ScreenshotResult){
     try{
      val buffer=result.hardwareBuffer
      try{
       val wrapped=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
       val bitmap=wrapped?.copy(Bitmap.Config.ARGB_8888,false)
       try{if(bitmap!=null&&playing&&epoch==generation)process(bitmap,epoch)}
       finally{bitmap?.recycle()}
      }finally{buffer.close()}
     }catch(_:Exception){}finally{busy=false;next(epoch)}
    }
    override fun onFailure(errorCode:Int){busy=false;next(epoch)}
   })
  }catch(_:Exception){busy=false;next(epoch)}
 }
 private fun next(epoch:Int){if(playing&&epoch==generation)handler.postDelayed(::capture,config.pollMs)}
 private fun process(bitmap:Bitmap,epoch:Int){
  val portrait=bitmap.height>bitmap.width
  val analyzed=if(portrait){
   val m=Matrix().apply{postRotate(90f)}
   Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,m,true)
  }else bitmap
  try{
   for(hit in detector.detect(analyzed,SystemClock.uptimeMillis())){
    val lane=hit.lane
    if(lane !in 0..8)continue
    val runnable=Runnable{
     if(!playing||epoch!=generation)return@Runnable
     val nx=config.laneXs[lane];val ny=config.laneYs[lane]
     val x=if(portrait)ny*bitmap.width else nx*bitmap.width
     val y=if(portrait)(1f-nx)*bitmap.height else ny*bitmap.height
     val path=Path().apply{moveTo(x,y)}
     val stroke=GestureDescription.StrokeDescription(path,0,45)
     dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),null,null)
    }
    scheduled.add(runnable)
    handler.postDelayed(runnable,hit.delayMs.coerceIn(0L,120L))
   }
  }finally{if(analyzed!==bitmap)analyzed.recycle()}
 }
}
