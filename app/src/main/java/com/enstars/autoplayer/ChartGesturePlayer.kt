package com.enstars.autoplayer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.SystemClock
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Offline chart dispatcher for unrooted Android.
 * Overlapping Tap/Flick/Hold/Slide strokes are packed into one multi-touch
 * GestureDescription. The Android API cancels previous uncompleted gestures,
 * so overlap must NOT be dispatched as separate gesture calls.
 */
class ChartGesturePlayer(
 private val service:AccessibilityService,
 private val handler:Handler,
 private val config:GameConfig,
 chartEvents:List<PlayNote>,
 private val progress:(Stats)->Unit
) {
 data class Stats(
  val submitted:Int,val completed:Int,val cancelled:Int,val rejected:Int,
  val overflow:Int,val total:Int,val error:String=""
 )
 private val plan=GestureBatcher.plan(chartEvents)
 private val runnables=mutableListOf<Runnable>()
 private var stopped=false
 private var submitted=0
 private var completed=0
 private var cancelled=0
 private var rejected=0
 private var error=""
 val total:Int=plan.batches.sumOf{it.notes.size}
 val first:PlayNote?=plan.batches.firstOrNull()?.notes?.firstOrNull()
 fun summary():Stats=Stats(submitted,completed,cancelled,rejected,plan.overflow,total,error)
 fun stop(){
  stopped=true
  runnables.forEach{handler.removeCallbacks(it)}
  runnables.clear()
 }
 fun sync(anchorUptimeMs:Long,timingOffsetMs:Long){
  if(stopped||plan.batches.isEmpty())return
  val base=anchorUptimeMs-plan.batches.first().startMs+timingOffsetMs
  plan.batches.forEach{batch->
   val due=base+batch.startMs
   val task=Runnable{if(!stopped)send(batch)}
   runnables.add(task)
   if(due<=SystemClock.uptimeMillis())handler.post(task)
   else handler.postAtTime(task,due)
  }
 }
 private fun gameCoords(lane:Float):Pair<Float,Float>{
  val clamped=lane.coerceIn(0f,8f)
  val low=floor(clamped).toInt()
  val high=(low+1).coerceAtMost(8)
  val ratio=clamped-low
  val x=config.laneXs[low]*(1f-ratio)+config.laneXs[high]*ratio
  val y=config.laneYs[low]*(1f-ratio)+config.laneYs[high]*ratio
  return Pair(x,y)
 }
 private fun screenCoords(lane:Float,w:Int,h:Int):Pair<Float,Float>{
  val (x,y)=gameCoords(lane)
  return if(w>=h)Pair(x*w,y*h) else Pair(y*w,(1f-x)*h)
 }
 private fun shifted(x:Float,y:Float,kind:NoteKind,w:Int,h:Int):Pair<Float,Float>{
  // Direction is defined in the LANDSCAPE game coordinate system.
  val amount=if(w>=h)w*.058f else h*.058f
  val dx=when(kind){NoteKind.FLICK_LEFT->-amount;NoteKind.FLICK_RIGHT->amount;else->0f}
  val dy=when(kind){NoteKind.FLICK_UP->-amount;NoteKind.FLICK_DOWN->amount;else->0f}
  return if(w>=h)Pair((x+dx).coerceIn(1f,w-2f),(y+dy).coerceIn(1f,h-2f))
   else Pair((x+dy).coerceIn(1f,w-2f),(y-dx).coerceIn(1f,h-2f))
 }
 private fun makeStroke(note:PlayNote,startMs:Long,w:Int,h:Int):GestureDescription.StrokeDescription{
  val duration=(note.end()-note.timeMs).coerceIn(24L,30000L)
  val startDelay=(note.timeMs-startMs).coerceAtLeast(0L)
  val (startX,startY)=screenCoords(note.lane,w,h)
  val path=Path().apply{
   moveTo(startX,startY)
   when(note.kind){
    NoteKind.FLICK_LEFT,NoteKind.FLICK_RIGHT,
    NoteKind.FLICK_UP,NoteKind.FLICK_DOWN->{
     val (dx,dy)=shifted(startX,startY,note.kind,w,h)
     lineTo(dx,dy)
    }
    NoteKind.SLIDE->{
     for(point in note.path.sortedBy{it.timeMs}){
      val (px,py)=screenCoords(point.lane,w,h)
      lineTo(px,py)
     }
     if(note.releaseKind!=null){
      val (px,py)=screenCoords(note.path.lastOrNull()?.lane?:note.lane,w,h)
      val (fx,fy)=shifted(px,py,note.releaseKind,w,h)
      lineTo(fx,fy)
     }
    }
    NoteKind.HOLD->{
     if(note.releaseKind!=null){
      val (fx,fy)=shifted(startX,startY,note.releaseKind,w,h)
      lineTo(fx,fy)
     }
    }
    else->{}
   }
  }
  return GestureDescription.StrokeDescription(path,startDelay,duration)
 }
 private fun send(batch:GestureBatcher.Batch){
  val metrics=service.resources.displayMetrics
  val w=metrics.widthPixels
  val h=metrics.heightPixels
  try{
   val builder=GestureDescription.Builder()
   batch.notes.forEach{builder.addStroke(makeStroke(it,batch.startMs,w,h))}
   val gesture=builder.build()
   val accepted=service.dispatchGesture(gesture,object:AccessibilityService.GestureResultCallback(){
    override fun onCompleted(gestureDescription:GestureDescription?){
     if(stopped)return
     completed++
     progress(summary())
    }
    override fun onCancelled(gestureDescription:GestureDescription?){
     if(stopped)return
     cancelled++
     progress(summary())
    }
   },handler)
   if(accepted)submitted+=batch.notes.size else rejected++
  }catch(e:Exception){
   rejected++
   error=e.javaClass.simpleName+": "+(e.message?:"")
  }
  progress(summary())
 }
}
