package com.enstars.autoplayer
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
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
 private var calibrated=false
 private var calibrationCount=0
 private var previousPoints:List<JudgmentPointDetector.Point>?=null
 private var lastTap=LongArray(0)
 private val pending = mutableListOf<Runnable>()
 override fun onServiceConnected(){super.onServiceConnected();instance=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?)=Unit
 override fun onInterrupt(){stop()}
 override fun onDestroy(){stop();instance=null;super.onDestroy()}
 fun start(){
  if(running)return
  config=GameConfig.load(this);detector=NoteDetector(config)
  lastTap=LongArray(config.laneXs.size);calibrated=false;calibrationCount=0;previousPoints=null;running=true;handler.post(::capture)
 }
 fun stop(){running=false;handler.removeCallbacksAndMessages(null);pending.clear()}
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
  val now=SystemClock.uptimeMillis()
  if(!calibrated){
   val points=pointDetector.locate(bitmap)
   if(points!=null && points.size==9){
    val prior=previousPoints
    val stable=prior!=null && prior.zip(points).all{(a,b)->
     kotlin.math.hypot(a.x-b.x,a.y-b.y)<bitmap.width*.025f
    }
    calibrationCount=if(stable)calibrationCount+1 else 1
    previousPoints=points
    if(calibrationCount>=3){
     config=config.copy(laneXs=points.map{it.x/bitmap.width},laneYs=points.map{it.y/bitmap.height})
     detector=NoteDetector(config)
     calibrated=true
    }
   }else calibrationCount=0
   return
  }
  val hits=detector.detect(bitmap,now)
  if(hits.isEmpty())return
  // Schedule using a monotonic clock. Dispatch is still subject to Android latency.
  hits.groupBy { it.delayMs / 16L }.forEach { (_, group) ->
   val delay=group.minOf { it.delayMs }.coerceAtLeast(0)
   handler.postDelayed({
    if(!running)return@postDelayed
    val gesture=GestureDescription.Builder()
    group.forEach { hit ->
     val path=Path().apply {
      moveTo(config.laneXs[hit.lane]*bitmap.width,config.laneYs[hit.lane]*bitmap.height)
     }
     gesture.addStroke(GestureDescription.StrokeDescription(path,0,45))
    }
    try { dispatchGesture(gesture.build(),null,null) } catch(_:Exception) {}
   },delay)
  }
 }
 private fun schedule(){if(running)handler.postDelayed(::capture,config.pollMs)}
}