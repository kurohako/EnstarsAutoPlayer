package com.enstars.autoplayer

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Detects nine white circular judgment targets directly from the current frame.
 * Uses a coarse-to-fine circular color/edge score, followed by arc validation.
 * No stored screen coordinates are used as detection inputs.
 */
class JudgmentPointDetector {
 data class Point(val x:Float,val y:Float,val confidence:Float)
 private var cached:List<Point>?=null
 private var missed=0

 fun locate(bitmap:Bitmap):List<Point>? {
  val w=bitmap.width;val h=bitmap.height
  if(w<200||h<120)return cached
  val scale=640f/w
  val sw=640
  val sh=(h*scale).toInt().coerceAtLeast(1)
  val small=Bitmap.createScaledBitmap(bitmap,sw,sh,true)
  try {
   val radii=listOf((sw*.018f).toInt(),(sw*.021f).toInt(),(sw*.025f).toInt()).map{it.coerceAtLeast(5)}.distinct()
   val candidates=mutableListOf<Point>()
   val step=5
   val yStart=(sh*.39f).toInt()
   val yEnd=(sh*.96f).toInt()
   for(y in yStart until yEnd step step) for(x in (sw*.12f).toInt() until (sw*.89f).toInt() step step) {
    var best=0f
    for(r in radii){
     if(x-r-3<0||x+r+3>=sw||y-r-3<0||y+r+3>=sh)continue
     var ring=0f;var outside=0f
     for(k in 0 until 12){
      val a=k*Math.PI/6
      val dx=kotlin.math.cos(a).toFloat()
      val dy=kotlin.math.sin(a).toFloat()
      ring+=whiteness(small.getPixel((x+dx*r).toInt(),(y+dy*r).toInt()))
      outside+=whiteness(small.getPixel((x+dx*(r+4)).toInt(),(y+dy*(r+4)).toInt()))
     }
     val center=whiteness(small.getPixel(x,y))
     val score=(ring/12f)*.65f + center*.20f + ((ring-outside)/12f).coerceAtLeast(0f)*.15f
     if(score>best)best=score
    }
    if(best>.72f)candidates.add(Point(x/scale,y/scale,best))
   }
   // Non-maximum suppression and left-to-right arc consistency.
   val picked=mutableListOf<Point>()
   for(p in candidates.sortedByDescending{it.confidence}){
    if(picked.none{hypot(it.x-p.x,it.y-p.y)<w*.045f})picked.add(p)
    if(picked.size>=45)break
   }
   val sorted=picked.sortedBy{it.x}
   // Search ordered 9-point sequences constrained by a U-shaped arc.
   // The point geometry is inferred from detected candidates, not a fixed template.
   val best=selectArc(sorted,w,h)
   if(best!=null){cached=best;missed=0;return best}
   missed++
   if(missed>20)cached=null
   return cached
  } finally {if(small!==bitmap)small.recycle()}
 }

 private fun whiteness(c:Int):Float{
  val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
  val min=minOf(r,g,b);val max=maxOf(r,g,b)
  return (min/255f)*((1f-(max-min)/255f)*.7f+.3f)
 }

 private fun selectArc(points:List<Point>,w:Int,h:Int):List<Point>?{
  if(points.size<9)return null
  // Group points by approximate normalized X buckets only for candidate grouping.
  // These buckets are broad; actual output centers always come from image pixels.
  val bands=9
  val left=w*.13f;val right=w*.87f
  val groups=(0 until bands).map{i->
   val target=left+(right-left)*i/8f
   points.filter{abs(it.x-target)<w*.065f}
    .sortedByDescending{it.confidence}.take(5)
  }
  if(groups.any{it.isEmpty()})return null
  var beam=listOf(emptyList<Point>() to 0f)
  for(i in 0 until 9){
   val next=mutableListOf<Pair<List<Point>,Float>>()
   for((seq,score) in beam)for(p in groups[i]){
    if(seq.isNotEmpty()&&p.x<=seq.last().x+w*.025f)continue
    if(seq.isNotEmpty()){
     val dy=p.y-seq.last().y
     if(i<=4&&dy < -h*.045f)continue
     if(i>=5&&dy > h*.045f)continue
    }
    next.add((seq+p) to (score+p.confidence))
   }
   beam=next.sortedByDescending{it.second}.take(80)
   if(beam.isEmpty())return null
  }
  return beam.firstOrNull{(seq,_)->
   seq[4].y>seq[0].y+h*.22f &&
   seq[4].y>seq[8].y+h*.22f &&
   abs(seq[0].y-seq[8].y)<h*.13f
  }?.first
 }
}
