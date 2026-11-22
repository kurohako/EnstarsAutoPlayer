package com.enstars.autoplayer

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import kotlin.math.*

/**
 * Experimental tap-note tracker for the nine radial Enstars lanes.
 * Tracks saturated blue note heads moving from the upper spawn area
 * toward manually calibrated targets. Does not handle hold/flick/slide.
 */
class NoteDetector(private val config:GameConfig){
 data class Hit(val lane:Int,val delayMs:Long)
 private data class Track(var progress:Float,var time:Long,var velocity:Float=0f,var fired:Boolean=false)
 private val tracks=arrayOfNulls<Track>(9)
 private val lastTap=LongArray(9)
 private val originX=.525f
 private val originY=.115f
 fun detect(bitmap:Bitmap,capturedAtMs:Long=SystemClock.uptimeMillis()):List<Hit>{
  val w=bitmap.width;val h=bitmap.height
  val hits=mutableListOf<Hit>()
  val sampleRadius=(min(w,h)*.012f).coerceAtLeast(3f)
  for(lane in 0..8){
   val tx=config.laneXs[lane];val ty=config.laneYs[lane]
   var bestProgress=-1f
   var bestScore=0f
   // Scan along the ray; do not include the stationary judgment circle.
   for(step in 10..87 step 3){
    val t=step/100f
    val cx=((originX+(tx-originX)*t)*w).toInt()
    val cy=((originY+(ty-originY)*t)*h).toInt()
    if(cx<2||cy<2||cx>=w-2||cy>=h-2)continue
    var blue=0;var total=0
    val rr=sampleRadius.toInt()
    for(dy in -rr..rr step 3)for(dx in -rr..rr step 3){
     if(dx*dx+dy*dy>rr*rr)continue
     val x=cx+dx;val y=cy+dy
     if(x !in 0 until w||y !in 0 until h)continue
     val color=bitmap.getPixel(x,y)
     val r=Color.red(color);val g=Color.green(color);val b=Color.blue(color)
     // Blue rim and white center: use blue chroma rather than brightness alone.
     if(b>125 && b>r*1.30f && b>g*1.07f && b-r>35)blue++
     total++
    }
    val score=if(total==0)0f else blue.toFloat()/total
    if(score>.24f && (score>bestScore*.85f||bestProgress<0f)){
     if(t>bestProgress){bestProgress=t;bestScore=score}
    }
   }
   val previous=tracks[lane]
   if(bestProgress<0f){
    if(previous!=null&&capturedAtMs-previous.time>220L)tracks[lane]=null
    continue
   }
   if(previous==null||capturedAtMs-previous.time>220L||
      bestProgress<previous.progress-.065f){
    tracks[lane]=Track(bestProgress,capturedAtMs)
    continue
   }
   val elapsed=(capturedAtMs-previous.time).coerceAtLeast(1L)
   val advance=bestProgress-previous.progress
   if(advance>.005f && advance<.19f){
    val velocity=advance/elapsed
    previous.velocity=if(previous.velocity>0f)previous.velocity*.45f+velocity*.55f else velocity
   }
   previous.progress=bestProgress
   previous.time=capturedAtMs
   if(previous.fired||previous.velocity<.00015f)continue
   val eta=((1f-bestProgress)/previous.velocity).toLong()
   if(eta in 0L..130L && capturedAtMs-lastTap[lane]>config.cooldownMs){
    hits.add(Hit(lane,eta))
    previous.fired=true
    lastTap[lane]=capturedAtMs
   }
  }
  return hits
 }
}
