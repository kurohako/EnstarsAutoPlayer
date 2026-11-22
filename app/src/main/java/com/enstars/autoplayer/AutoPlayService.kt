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
 private var lockedPoints:List<JudgmentPointDetector.Point>?=null
 private var lockStartedAt=0L
 private var calibrationRequestedAt=0L
 private var lastFrameWidth=1
 private var lastFrameHeight=1
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
  resetCalibration()
  calibrationRequestedAt=SystemClock.uptimeMillis()
  config=GameConfig.load(this);detector=NoteDetector(config)
  lastTap=LongArray(config.laneXs.size);running=true;overlay.show();overlay.update(null,"WAITING FOR LIVE...",1,1);handler.post(::capture)
 }
 fun stop(){running=false;handler.removeCallbacksAndMessages(null);pending.clear();if(::overlay.isInitialized)overlay.close()}
 private fun resetCalibration(){
  calibrated=false;lockedPoints=null;calibrationCount=0;previousPoints=null;lockStartedAt=0L
 }
 fun restartCalibration(){
  resetCalibration()
  calibrationRequestedAt=SystemClock.uptimeMillis()
  if(!running)start()
  else{
   handler.removeCallbacksAndMessages(null)
   overlay.update(null,"WAITING FOR LIVE...",lastFrameWidth,lastFrameHeight)
   if(!busy)handler.post(::capture)
  }
 }
 private fun capture(){
  if(!running||calibrated)return
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
  // V2.3 one-shot calibration only; no gestures. Never scan after lock.
  // Portrait captures are rotated for arc detection and then mapped back.
  if(calibrated)return
  lastFrameWidth=bitmap.width;lastFrameHeight=bitmap.height
  val portrait=bitmap.height>bitmap.width
  val analyzed=if(portrait){
   val m=Matrix().apply{postRotate(90f)}
   Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,m,true)
  }else bitmap
  try{
   val detection=pointDetector.locate(analyzed)
   val points=detection.points?.map { p ->
    if(portrait) JudgmentPointDetector.Point(p.y,bitmap.height-p.x,p.confidence)
    else p
   }
   if(points!=null&&points.size==9){
    val now=SystemClock.uptimeMillis()
    if(lockStartedAt==0L)lockStartedAt=now
    val prior=previousPoints
    val stable=prior!=null&&prior.zip(points).all{(a,b)->
     kotlin.math.hypot(a.x-b.x,a.y-b.y)<bitmap.width.coerceAtLeast(bitmap.height)*.025f
    }
    calibrationCount=if(stable)calibrationCount+1 else 1
    previousPoints=points
    if(calibrationCount>=2){
     lockedPoints=points.toList()
     calibrated=true
     config=config.copy(laneXs=points.map{it.x/bitmap.width},laneYs=points.map{it.y/bitmap.height})
     val elapsed=(SystemClock.uptimeMillis()-lockStartedAt).coerceAtLeast(0L)
     val fromStart=(SystemClock.uptimeMillis()-calibrationRequestedAt).coerceAtLeast(0L)
     val drift=previousPoints?.zip(points)?.map{(a,b)->
      kotlin.math.hypot(a.x-b.x,a.y-b.y)
     }?.maxOrNull() ?: 0f
     getSharedPreferences("calibration",MODE_PRIVATE).edit()
      .putLong("lock_ms",elapsed)
      .putLong("request_to_lock_ms",fromStart)
      .putFloat("stability_px",drift)
      .putInt("width",bitmap.width)
      .putInt("height",bitmap.height)
      .putLong("saved_at",System.currentTimeMillis())
      .putString("points",points.joinToString(";"){"${it.x},${it.y}"})
      .apply()
     overlay.update(lockedPoints,"9/9 LOCKED ${elapsed}ms",bitmap.width,bitmap.height)
     return
    }
   }else{
    calibrationCount=0
    previousPoints=null
    lockStartedAt=0L
   }
   val elapsed=if(lockStartedAt==0L)0L else SystemClock.uptimeMillis()-lockStartedAt
   val status=when{
    points!=null->"9/9 VERIFYING ${elapsed}ms"
    else->"WAITING FOR 9 POINTS (${detection.detected})"
   }
   overlay.update(points,status,bitmap.width,bitmap.height)
  }finally{if(analyzed!==bitmap)analyzed.recycle()}
 }
 private fun schedule(){if(running&&!calibrated)handler.postDelayed(::capture,80L)}
}