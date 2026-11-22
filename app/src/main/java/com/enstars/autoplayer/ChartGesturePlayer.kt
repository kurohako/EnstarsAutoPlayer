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
 private var skippedForStrokeLimit=0
 val total:Int=plan.batches.sumOf{it.notes.size}
 val first:PlayNote?=plan.batches.firstOrNull()?.notes?.firstOrNull()
 fun summary():Stats=Stats(submitted,completed,cancelled,rejected,plan.overflow+skippedForStrokeLimit,total,error)
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
 private fun makeStrokes(note:PlayNote,startMs:Long,w:Int,h:Int):List<GestureDescription.StrokeDescription>{
  val startDelay=(note.timeMs-startMs).coerceAtLeast(0L)
  val (startX,startY)=screenCoords(note.lane,w,h)
  val result=mutableListOf<GestureDescription.StrokeDescription>()
  fun simplePath(x:Float,y:Float,endX:Float?=null,endY:Float?=null)=Path().apply{
   moveTo(x,y)
   if(endX!=null&&endY!=null)lineTo(endX,endY)
  }
  if(note.kind==NoteKind.TAP){
   return listOf(GestureDescription.StrokeDescription(simplePath(startX,startY),startDelay,30L))
  }
  if(note.kind in listOf(NoteKind.FLICK_LEFT,NoteKind.FLICK_RIGHT,NoteKind.FLICK_UP,NoteKind.FLICK_DOWN)){
   val (endX,endY)=shifted(startX,startY,note.kind,w,h)
   return listOf(GestureDescription.StrokeDescription(
    simplePath(startX,startY,endX,endY),startDelay,75L))
  }
  val holdDuration=(note.endTimeMs-note.timeMs).coerceIn(30L,30000L)
  if(note.kind==NoteKind.HOLD){
   val first=GestureDescription.StrokeDescription(
    simplePath(startX,startY),startDelay,holdDuration,note.releaseKind!=null)
   result.add(first)
   if(note.releaseKind!=null){
    val (endX,endY)=shifted(startX,startY,note.releaseKind,w,h)
    result.add(first.continueStroke(simplePath(startX,startY,endX,endY),
     startDelay+holdDuration,70L,false))
   }
   return result
  }
  // Slide: each timed knot is represented by a continuation segment.
  // Unlike a single lineTo() path this preserves timing between knots.
  val points=mutableListOf(ChartWaypoint(note.timeMs,note.lane))
  points.addAll(note.path.filter{it.timeMs>note.timeMs&&it.timeMs<=note.endTimeMs}.sortedBy{it.timeMs})
  if(points.last().timeMs<note.endTimeMs){
   points.add(ChartWaypoint(note.endTimeMs,points.last().lane))
  }
  if(points.size<2)return listOf(GestureDescription.StrokeDescription(
   simplePath(startX,startY),startDelay,holdDuration))
  var last:GestureDescription.StrokeDescription?=null
  for(i in 1 until points.size){
   val from=points[i-1]
   val to=points[i]
   val (px,py)=screenCoords(from.lane,w,h)
   val (qx,qy)=screenCoords(to.lane,w,h)
   val duration=(to.timeMs-from.timeMs).coerceAtLeast(1L)
   val delay=(from.timeMs-startMs).coerceAtLeast(0L)
   val path=simplePath(px,py,qx,qy)
   val keep=i<points.lastIndex||note.releaseKind!=null
   val next=if(last==null)GestureDescription.StrokeDescription(path,delay,duration,keep)
    else last.continueStroke(path,delay,duration,keep)
   result.add(next)
   last=next
  }
  if(note.releaseKind!=null&&last!=null){
   val tail=points.last()
   val (px,py)=screenCoords(tail.lane,w,h)
   val (qx,qy)=shifted(px,py,note.releaseKind,w,h)
   result.add(last.continueStroke(simplePath(px,py,qx,qy),
    (tail.timeMs-startMs).coerceAtLeast(0L),70L,false))
  }
  return result
 }
 private fun send(batch:GestureBatcher.Batch){
  val metrics=service.resources.displayMetrics
  val w=metrics.widthPixels
  val h=metrics.heightPixels
  try{
   val builder=GestureDescription.Builder()
   var strokeCount=0
   var validNotes=0
   for(note in batch.notes){
    val strokes=makeStrokes(note,batch.startMs,w,h)
    if(strokeCount+strokes.size>GestureDescription.getMaxStrokeCount()){
     skippedForStrokeLimit++
     continue
    }
    strokes.forEach{builder.addStroke(it)}
    strokeCount+=strokes.size
    validNotes++
   }
   if(strokeCount==0){progress(summary());return}
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
   if(accepted)submitted+=validNotes else rejected++
  }catch(e:Exception){
   rejected++
   error=e.javaClass.simpleName+": "+(e.message?:"")
  }
  progress(summary())
 }
}
