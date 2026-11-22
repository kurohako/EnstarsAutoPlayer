package com.enstars.autoplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.hypot

class DebugOverlay(private val context:Context){
 private val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
 private val view=OverlayView(context)
 private var attached=false
 private var editing=false
 private var onSave:(()->Unit)?=null
 fun show(){
  if(attached)return
  val p=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT)
  p.gravity=Gravity.TOP or Gravity.START
  wm.addView(view,p);attached=true
 }
 private fun flags(){
  if(!attached)return
  val p=view.layoutParams as WindowManager.LayoutParams
  p.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
   (if(editing)0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
  wm.updateViewLayout(view,p)
 }
 fun showFixed(config:GameConfig){
  view.xs=config.laneXs.toMutableList()
  view.ys=config.laneYs.toMutableList()
  view.status="FIXED 9 POINTS"
  view.invalidate()
 }
 fun edit(config:GameConfig,save:()->Unit){
  showFixed(config);onSave=save;editing=true;view.editing=true
  view.status="DRAG 1-9 / TAP TOP RIGHT TO SAVE"
  flags();view.invalidate()
 }
 fun saveEdit(){
  if(!editing)return
  GameConfig.save(context,view.xs,view.ys)
  editing=false;view.editing=false;view.status="9 POINTS SAVED"
  flags();view.invalidate();onSave?.invoke()
 }
 fun updateStatus(status:String){view.status=status;view.invalidate()}
 fun close(){
  editing=false
  if(attached){wm.removeView(view);attached=false}
 }
 private inner class OverlayView(context:Context):View(context){
  var xs=GameConfig().laneXs.toMutableList()
  var ys=GameConfig().laneYs.toMutableList()
  var editing=false
  var status="FIXED"
  private var active=-1
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  private fun screenPoint(i:Int):Pair<Float,Float> =
   if(width>=height)Pair(xs[i]*width,ys[i]*height)
   else Pair(ys[i]*width,(1f-xs[i])*height)
  private fun store(i:Int,x:Float,y:Float){
   if(width>=height){xs[i]=(x/width).coerceIn(0f,1f);ys[i]=(y/height).coerceIn(0f,1f)}
   else{xs[i]=(1f-y/height).coerceIn(0f,1f);ys[i]=(x/width).coerceIn(0f,1f)}
  }
  override fun onTouchEvent(event:MotionEvent):Boolean{
   if(!editing)return false
   when(event.actionMasked){
    MotionEvent.ACTION_DOWN->{
     if(event.x>width*.75f&&event.y<height*.16f){saveEdit();return true}
     active=(0..8).minByOrNull{val p=screenPoint(it);hypot(event.x-p.first,event.y-p.second)}?:-1
     return true
    }
    MotionEvent.ACTION_MOVE->{if(active>=0){store(active,event.x,event.y);invalidate()};return true}
    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{active=-1;return true}
   }
   return true
  }
  override fun onDraw(canvas:Canvas){
   val density=resources.displayMetrics.density
   paint.style=Paint.Style.FILL;paint.color=0xCC101820.toInt()
   canvas.drawRoundRect(5*density,5*density,290*density,44*density,8*density,8*density,paint)
   paint.color=Color.WHITE;paint.textSize=13*density
   canvas.drawText(status,12*density,29*density,paint)
   if(editing){
    paint.color=0xCC147D34.toInt()
    canvas.drawRoundRect(width*.78f,4*density,width-4*density,42*density,8*density,8*density,paint)
    paint.color=Color.WHITE;canvas.drawText("SAVE",width*.81f,28*density,paint)
   }
   for(i in 0..8){
    val (x,y)=screenPoint(i)
    paint.color=if(editing)Color.YELLOW else Color.GREEN
    paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density
    canvas.drawCircle(x,y,17*density,paint)
    canvas.drawLine(x-23*density,y,x+23*density,y,paint)
    canvas.drawLine(x,y-23*density,x,y+23*density,paint)
    paint.style=Paint.Style.FILL;canvas.drawCircle(x,y,3*density,paint)
    paint.textSize=13*density;canvas.drawText("${i+1}",x+16*density,y-9*density,paint)
   }
  }
 }
}
