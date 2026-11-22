package com.enstars.autoplayer

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/** Detects actual white judgment circles; never uses fixed screen tap coordinates. */
class JudgmentPointDetector {
 data class Point(val x:Float,val y:Float,val confidence:Float)
 data class Result(val points:List<Point>?,val candidates:List<Point>,val detected:Int)
 private var lastGood:List<Point>?=null
 private var missing=0

 fun locate(bitmap:Bitmap):Result {
  val w=bitmap.width; val h=bitmap.height
  if(w<200||h<120)return Result(null,emptyList(),0)
  val sw=640;val sh=(h.toFloat()*sw/w).roundToInt().coerceAtLeast(1)
  val small=Bitmap.createScaledBitmap(bitmap,sw,sh,true)
  try {
   val candidates=extractCandidates(small).map{Point(it.x*w/sw,it.y*h/sh,it.confidence)}
   val chosen=selectArc(candidates,w,h)
   if(chosen!=null){lastGood=chosen;missing=0}
   else if(++missing>12)lastGood=null
   return Result(chosen,candidates,candidates.size)
  }finally{if(small!==bitmap)small.recycle()}
 }

 private fun extractCandidates(image:Bitmap):List<Point>{
  val w=image.width;val h=image.height
  val stride=2
  val gw=w/stride;val gh=h/stride
  val bright=BooleanArray(gw*gh)
  for(gy in (gh*.28f).toInt() until (gh*.97f).toInt()){
   for(gx in (gw*.04f).toInt() until (gw*.96f).toInt()){
    val c=image.getPixel(gx*stride,gy*stride)
    val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
    val min=minOf(r,g,b);val max=maxOf(r,g,b)
    bright[gy*gw+gx]=min>=195 && max-min<=58
   }
  }
  val seen=BooleanArray(bright.size)
  val queue=IntArray(bright.size)
  val found=mutableListOf<Point>()
  for(i in bright.indices){
   if(!bright[i]||seen[i])continue
   var head=0;var tail=1;queue[0]=i;seen[i]=true
   var sumX=0L;var sumY=0L;var minX=gw;var maxX=0;var minY=gh;var maxY=0
   while(head<tail){
    val v=queue[head++];val x=v%gw;val y=v/gw
    sumX+=x;sumY+=y
    minX=min(minX,x);maxX=max(maxX,x);minY=min(minY,y);maxY=max(maxY,y)
    for(d in intArrayOf(-gw,gw,-1,1)){
     val n=v+d
     if(n<0||n>=bright.size||seen[n]||!bright[n])continue
     if(abs(n%gw-x)+abs(n/gw-y)!=1)continue
     seen[n]=true;queue[tail++]=n
    }
   }
   val bw=maxX-minX+1;val bh=maxY-minY+1
   val diameter=(w*.025f/stride).coerceAtLeast(4f)
   if(tail<8||tail>1800||bw<diameter*.4f||bh<diameter*.4f)continue
   val ratio=bw.toFloat()/bh
   if(ratio !in .65f..1.55f)continue
   val fill=tail.toFloat()/(bw*bh)
   if(fill !in .27f..1.0f)continue
   val cx=(sumX.toFloat()/tail)*stride
   val cy=(sumY.toFloat()/tail)*stride
   val confidence=(1f-abs(1f-ratio)).coerceIn(0f,1f)*.45f+fill.coerceIn(0f,1f)*.55f
   found.add(Point(cx,cy,confidence))
  }
  return found.sortedByDescending{it.confidence}.take(100)
 }

 private fun selectArc(candidates:List<Point>,w:Int,h:Int):List<Point>?{
  if(candidates.size<9)return null
  // Prefer a direct geometry check when the detector already finds exactly nine
  // circular components. Perspective projection makes the real arc non-quadratic.
  if(candidates.size==9){
   val ordered=candidates.sortedBy{it.x}
   val dx=ordered.zipWithNext().map{(a,b)->b.x-a.x}
   val top=ordered.first().y
   val end=ordered.last().y
   val bottom=ordered[4].y
   val leftRise=(0..3).count{ordered[it+1].y>=ordered[it].y-h*.025f}
   val rightFall=(4..7).count{ordered[it+1].y<=ordered[it].y+h*.025f}
   if(dx.all{it>w*.035f} && dx.maxOrNull()!!<w*.20f &&
      bottom>top+h*.15f && bottom>end+h*.15f &&
      abs(top-end)<h*.17f && leftRise>=3 && rightFall>=3){
    return ordered
   }
  }
  // Fit a symmetric U-shaped quadratic using image-derived candidates.
  // Candidate selection is geometric; no predefined lane coordinates.
  val sorted=candidates.sortedBy{it.x}
  var best:List<Point>?=null
  var bestScore=Float.NEGATIVE_INFINITY
  val left=sorted.filter{it.x<w*.4f}.take(12)
  val right=sorted.filter{it.x>w*.6f}.take(12)
  val bottoms=sorted.filter{it.y>h*.65f}.take(15)
  for(a in left)for(b in right)for(mid in bottoms){
   if(mid.x<=a.x||mid.x>=b.x)continue
   val span=b.x-a.x
   if(span<w*.45f)continue
   if(mid.y-a.y<h*.15f||mid.y-b.y<h*.15f)continue
   val x0=mid.x;val denom=(a.x-x0)*(a.x-x0)+(b.x-x0)*(b.x-x0)
   if(denom<1f)continue
   val curvature=((a.y-mid.y)*(a.x-x0)*(a.x-x0)+(b.y-mid.y)*(b.x-x0)*(b.x-x0))/denom
   // Least-complex quadratic from three detected points.
   val da=(a.x-mid.x);val db=(b.x-mid.x)
   val slopeA=(a.y-mid.y)/da;val slopeB=(b.y-mid.y)/db
   val A=(slopeA-slopeB)/(da-db)
   val B=slopeA-A*da
   if(A>=0f||abs(B)>1.5f)continue
   val selected=mutableListOf<Point>()
   var score=0f
   for(i in 0..8){
    val targetX=a.x+span*i/8f
    val predictedY=mid.y+A*(targetX-x0)*(targetX-x0)+B*(targetX-x0)
    val p=candidates.filter{abs(it.x-targetX)<span*.065f && abs(it.y-predictedY)<h*.065f}
     .minByOrNull{abs(it.x-targetX)/span+abs(it.y-predictedY)/h-it.confidence*.025f}
     ?:break
    if(selected.any{hypot(it.x-p.x,it.y-p.y)<w*.025f})break
    selected.add(p)
    score+=p.confidence-abs(p.y-predictedY)/h
   }
   if(selected.size==9){
    val xs=selected.map{it.x}
    if(xs.zipWithNext().any{(x,y)->y<=x})continue
    val peak=selected[4].y
    if(peak<selected[0].y+h*.14f||peak<selected[8].y+h*.14f)continue
    if(score>bestScore){bestScore=score;best=selected}
   }
  }
  return best
 }
}
