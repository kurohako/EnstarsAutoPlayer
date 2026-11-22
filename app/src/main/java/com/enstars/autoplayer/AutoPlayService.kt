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
 private var dispatched=0
 private var detected=0
 private var frames=0
 private var screenshotErrors=0
 private var captureBackoffMs=0L
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
  dispatched=0;detected=0;frames=0;screenshotErrors=0;captureBackoffMs=0L
  overlay.updateStatus("AUTO TAP - EXPERIMENTAL")
  handler.post(::capture)
 }
 fun showFloatingControls(){
  if(!::overlay.isInitialized)return
  start()
  overlay.showPlayerControls(
   onStart={
    val offset=getSharedPreferences("chart",MODE_PRIVATE).getLong("syncOffset",0L)
    startChartAutoplay(offset)
   },
   onStop={stop()}
  )
  overlay.updateStatus("FLOATING CONTROL READY")
 }
 fun startChartAutoplay(offsetMs:Long){
  if(!::overlay.isInitialized)return
  val raw=getSharedPreferences("chart",MODE_PRIVATE).getString("chartText",null)
  if(raw.isNullOrBlank())return
  val chart=try{ChartParser.parse(raw)}catch(_:Exception){return}
  if(chart.taps.size<2)return
  start()
  playing=true
  val epoch=generation
  val taps=chart.taps.sortedBy{it.timeMs}
  overlay.showPlayerControls(
   onStart={startChartAutoplay(offsetMs)},
   onStop={stop()}
  )
  overlay.updateStatus("WAIT FIRST NOTE - SYNC")
  overlay.showSyncButton{
   if(!playing||generation!=epoch)return@showSyncButton
   val syncAt=SystemClock.uptimeMillis()
   val first=taps.first().timeMs
   val grouped=taps.drop(1).groupBy{it.timeMs}.toSortedMap()
   overlay.updateStatus("CHART SYNCED / "+grouped.size+" EVENTS")
   for((time,notes) in grouped){
    val whenMs=syncAt+(time-first)+offsetMs
    if(whenMs<syncAt)continue
    handler.postAtTime({
     if(playing&&generation==epoch){
      val metrics=resources.displayMetrics
      val portrait=metrics.heightPixels>metrics.widthPixels
      val builder=GestureDescription.Builder()
      for(note in notes){
       if(note.lane !in 0..8)continue
       val nx=config.laneXs[note.lane];val ny=config.laneYs[note.lane]
       val x=if(portrait)ny*metrics.widthPixels else nx*metrics.widthPixels
       val y=if(portrait)(1f-nx)*metrics.heightPixels else ny*metrics.heightPixels
       val path=Path().apply{moveTo(x,y)}
       builder.addStroke(GestureDescription.StrokeDescription(path,0,40))
      }
      if(dispatchGesture(builder.build(),null,null)){
       dispatched+=notes.size
       if(dispatched%10==0)overlay.updateStatus("CHART TAPS "+dispatched)
      }
     }
    },whenMs)
   }
  }
 }
 fun testTap(){
  stopPlayback()
  config=GameConfig.load(this)
  overlay.show();overlay.showFixed(config)
  handler.postDelayed({
  val metrics=resources.displayMetrics
  val landscape=metrics.widthPixels>=metrics.heightPixels
  val nx=config.laneXs[4];val ny=config.laneYs[4]
  val x=if(landscape)nx*metrics.widthPixels else ny*metrics.widthPixels
  val y=if(landscape)ny*metrics.heightPixels else (1f-nx)*metrics.heightPixels
  val path=Path().apply{moveTo(x,y)}
  val gesture=GestureDescription.Builder()
   .addStroke(GestureDescription.StrokeDescription(path,0,60)).build()
  val accepted=dispatchGesture(gesture,object:GestureResultCallback(){
   override fun onCompleted(gestureDescription:GestureDescription?){overlay.updateStatus("TEST TAP COMPLETED")}
   override fun onCancelled(gestureDescription:GestureDescription?){overlay.updateStatus("TEST TAP CANCELLED")}
  },null)
  if(!accepted)overlay.updateStatus("TEST TAP REJECTED")
  },5000L)
  overlay.updateStatus("TEST TAP IN 5 SEC - OPEN GAME")
 }
 private fun stopPlayback(){
  playing=false;generation++
  handler.removeCallbacksAndMessages(null)
  busy=false
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
     }catch(e:Exception){overlay.updateStatus("PROCESS ERROR "+e.javaClass.simpleName)}finally{busy=false;captureBackoffMs=0L;next(epoch)}
    }
    override fun onFailure(errorCode:Int){
     busy=false
     screenshotErrors++
     captureBackoffMs=if(errorCode==3)500L else 250L
     overlay.updateStatus("CAPTURE ERROR "+errorCode+" x"+screenshotErrors+" OK "+frames)
     next(epoch)
    }
   })
  }catch(e:Exception){busy=false;overlay.updateStatus("CAPTURE ERROR "+e.javaClass.simpleName);next(epoch)}
 }
 private fun next(epoch:Int){if(playing&&epoch==generation)handler.postDelayed(::capture,maxOf(config.pollMs.toLong(),captureBackoffMs))}
 private fun process(bitmap:Bitmap,epoch:Int){
  val portrait=bitmap.height>bitmap.width
  val analyzed=if(portrait){
   val m=Matrix().apply{postRotate(90f)}
   Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,m,true)
  }else bitmap
  try{
   frames++
   if(frames%20==0)overlay.updateStatus("OK "+frames+" ERR "+screenshotErrors+" HITS "+detected+" TAPS "+dispatched)
   val hits=detector.detect(analyzed,SystemClock.uptimeMillis())
   detected+=hits.size
   if(hits.isNotEmpty()){
    // One gesture with multiple strokes avoids cancelling simultaneous notes.
    val epochHits=hits.filter{it.lane in 0..8}
    if(epochHits.isNotEmpty()){
     val builder=GestureDescription.Builder()
     for(hit in epochHits){
      val nx=config.laneXs[hit.lane];val ny=config.laneYs[hit.lane]
      val x=if(portrait)ny*bitmap.width else nx*bitmap.width
      val y=if(portrait)(1f-nx)*bitmap.height else ny*bitmap.height
      val path=Path().apply{moveTo(x,y)}
      builder.addStroke(GestureDescription.StrokeDescription(path,hit.delayMs.coerceIn(0L,130L),42L))
     }
     if(playing&&epoch==generation){
      val accepted=dispatchGesture(builder.build(),null,null)
      if(accepted)dispatched+=epochHits.size
      else overlay.updateStatus("AUTO GESTURE REJECTED")
      overlay.updateStatus("AUTO TAP ${dispatched} / EXPERIMENTAL")
     }
    }
   }
  }finally{if(analyzed!==bitmap)analyzed.recycle()}
 }
}
