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
 private var lastTap=LongArray(0)
 override fun onServiceConnected(){super.onServiceConnected();instance=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?)=Unit
 override fun onInterrupt(){stop()}
 override fun onDestroy(){stop();instance=null;super.onDestroy()}
 fun start(){
  if(running)return
  config=GameConfig.load(this);detector=NoteDetector(config)
  lastTap=LongArray(config.laneXs.size);running=true;handler.post(::capture)
 }
 fun stop(){running=false;handler.removeCallbacksAndMessages(null)}
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
  val lanes=detector.detect(bitmap).filter{now-lastTap[it]>=config.cooldownMs}
  if(lanes.isEmpty())return
  val gesture=GestureDescription.Builder()
  lanes.forEach{lane->
   val path=Path().apply{moveTo(config.laneXs[lane]*bitmap.width,config.hitY*bitmap.height)}
   gesture.addStroke(GestureDescription.StrokeDescription(path,0,45))
   lastTap[lane]=now
  }
  dispatchGesture(gesture.build(),null,null)
 }
 private fun schedule(){if(running)handler.postDelayed(::capture,config.pollMs)}
}