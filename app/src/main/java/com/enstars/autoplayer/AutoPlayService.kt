package com.enstars.autoplayer
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
class AutoPlayService:AccessibilityService() {
 companion object { @Volatile var instance:AutoPlayService?=null; private set }
 private val handler=Handler(Looper.getMainLooper())
 private var running=false
 private var busy=false
 private lateinit var config:GameConfig
 private lateinit var detector:NoteDetector
 private val pointDetector=JudgmentPointDetector()
 private lateinit var overlay:DebugOverlay
 private var calibrated=false
 private var calibrationCount=0
 private var previousPoints:List<JudgmentPointDetector.Point>?=null
 private var lastTap=LongArray(0)
 private val pending = mutableListOf<Runnable>()
 override fun onServiceConnected(){super.onServiceConnected();instance=this;overlay=DebugOverlay(this)}
 override fun onAccessibilityEvent(event:AccessibilityEvent?)=Unit
 override fun onInterrupt(){stop()}
 override fun onDestroy(){stop();instance=null;super.onDestroy()}
 fun start(){
  if(running)return
  config=GameConfig.load(this);detector=NoteDetector(config)
  lastTap=LongArray(config.laneXs.size);calibrated=false;calibrationCount=0;previousPoints=null;running=true;overlay.show();handler.post(::capture)
 }
 fun stop(){running=false;handler.removeCallbacksAndMessages(null);pending.clear();if(::overlay.isInitialized)overlay.close()}
 private fun capture(){
  if(!running)return
  if(busy){schedule();return}
  busy=true
  try {
   takeScreenshot(Display.DEFAULT_DISPLAY,mainExecutor,object:TakeScreenshotCallback{
    override fun onSuccess(result:ScreenshotResult){
     try{
      val buffer=result.hardwareBuffer
      try {
       val wrapped=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
       val bitmap=wrapped?.copy(Bitmap.Config.ARGB_8888,false)
       try{if(bitmap!=null&&running)process(bitmap)}finally{bitmap?.recycle()}
      }finally{buffer.close()}
     }catch(_:Exception){}finally{busy=false;schedule()}
    }
    override fun onFailure(errorCode:Int){busy=false;schedule()}
   })
  }catch(_:Exception){busy=false;schedule()}
 }
 private fun process(bitmap:Bitmap){
  // V2 diagnostic mode: recognition only, no touch gestures.
  // Portrait captures are rotated for arc detection and then mapped back.
  val portrait=bitmap.height>bitmap.width
  val analyzed=if(portrait){
   val m=Matrix().apply{postRotate(90f)}
   Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,m,true)
  }else bitmap
  try{
   val detected=pointDetector.locate(analyzed)
   val points=detected?.map { p ->
    if(portrait) JudgmentPointDetector.Point(p.y,bitmap.height-p.x,p.confidence)
    else p
   }
   if(points!=null&&points.size==9){
    val prior=previousPoints
    val stable=prior!=null&&prior.zip(points).all{(a,b)->
     kotlin.math.hypot(a.x-b.x,a.y-b.y)<bitmap.width.coerceAtLeast(bitmap.height)*.025f
    }
    calibrationCount=if(stable)calibrationCount+1 else 1
    previousPoints=points
    calibrated=calibrationCount>=3
   }else{
    calibrationCount=0
    calibrated=false
    previousPoints=null
   }
   val status=when{
    calibrated->"9/9 LOCKED - DEBUG ONLY"
    points!=null->"9/9 found - stabilizing $calibrationCount/3"
    else->"0/9 - scanning..."
   }
   overlay.update(points,status,bitmap.width,bitmap.height)
  }finally{if(analyzed!==bitmap)analyzed.recycle()}
 }
 private fun schedule(){if(running)handler.postDelayed(::capture,config.pollMs)}
}