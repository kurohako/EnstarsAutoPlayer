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
   val rough=selectArc(candidates,w,h)
   val chosen=rough?.map { refineCircleCenter(small,it.x*sw/w,it.y*sh/h)?.let { q -> Point(q.x*w/sw,q.y*h/sh,q.confidence) } ?: it }
   if(chosen!=null){lastGood=chosen;missing=0}
   else if(++missing>12)lastGood=null
   return Result(chosen,candidates,candidates.size)
  }finally{if(small!==bitmap)small.recycle()}
 }

 /**
  * Multiscale radial template derived from the supplied concentric-ring icon:
  * bright central disc, darker annulus, bright outer ring, darker exterior.
  * Connecting lines are suppressed by robust angular sampling, independent of line direction.
  * Coordinates are refined from the image, never from hardcoded lane positions.
  */
 private fun refineCircleCenter(image:Bitmap,cx:Float,cy:Float):Point? {
  val w=image.width;val h=image.height
  val nominal=(w*.019f).coerceAtLeast(5f)
  val radii=listOf(.72f,.86f,1f,1.14f,1.28f).map{it*nominal}
  var bestScore=Float.NEGATIVE_INFINITY
  var best:Point?=null
  val search=(nominal*.6f).toInt().coerceIn(3,13)
  for(y in (cy.toInt()-search)..(cy.toInt()+search) step 2){
   for(x in (cx.toInt()-search)..(cx.toInt()+search) step 2){
    if(x<2||y<2||x>=w-2||y>=h-2)continue
    val center=light(image.getPixel(x,y))
    for(r in radii){
     if(x-r*1.35f<0||x+r*1.35f>=w||y-r*1.35f<0||y+r*1.35f>=h)continue
     // All angles are eligible. End targets have diagonal incoming lines;
     // robust trimming discards contaminated sectors regardless of direction.
     val ringSamples=ArrayList<Float>(24)
     val moatSamples=ArrayList<Float>(24)
     val outsideSamples=ArrayList<Float>(24)
     for(k in 0 until 24){
      val theta=(k+.5)*Math.PI/12.0
      val dx=cos(theta).toFloat();val dy=sin(theta).toFloat()
      ringSamples.add(light(image.getPixel((x+dx*r).toInt(),(y+dy*r).toInt())))
      moatSamples.add(light(image.getPixel((x+dx*r*.65f).toInt(),(y+dy*r*.65f).toInt())))
      outsideSamples.add(light(image.getPixel((x+dx*r*1.28f).toInt(),(y+dy*r*1.28f).toInt())))
     }
     val ring=trimmedMean(ringSamples)
     val moat=trimmedMean(moatSamples)
     val outside=trimmedMean(outsideSamples)
     // Prefer a bright, circular ring around a bright disc, with darker
     // surrounding annulus. No assumption about line orientation.
     val score=.28f*center+.42f*ring+.23f*(ring-moat)+
      .07f*(ring-outside)-.10f*hypot(x-cx,y-cy)/nominal
     if(score>bestScore){
      bestScore=score
      best=Point(x.toFloat(),y.toFloat(),score)
     }
    }
   }
  }
  // Reject ambiguous refinement rather than forcing a shift.
  return best?.takeIf{bestScore>.57f && hypot(it.x-cx,it.y-cy)<nominal*.65f}
 }
 private fun trimmedMean(samples:List<Float>):Float{
  val sorted=samples.sorted()
  val trim=(sorted.size*.17f).toInt()
  return sorted.subList(trim,sorted.size-trim).average().toFloat()
 }
 private fun light(color:Int):Float{
  val r=Color.red(color);val g=Color.green(color);val b=Color.blue(color)
  return minOf(r,g,b)/255f
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
   // Bounding-box midpoint is less biased than a pixel centroid when one side of a ring is brighter.
   val cx=(minX+maxX)*.5f*stride
   val cy=(minY+maxY)*.5f*stride
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
