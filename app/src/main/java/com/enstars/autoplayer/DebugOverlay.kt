package com.enstars.autoplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Accessibility overlay: touch-through, non-focusable, removed on stop. */
class DebugOverlay(private val context: Context) {
 private val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
 private val view=OverlayView(context)
 private var attached=false

 fun show() {
  if(attached)return
  val params=WindowManager.LayoutParams(
   WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
   WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
   android.graphics.PixelFormat.TRANSLUCENT
  )
  params.gravity=Gravity.TOP or Gravity.START
  try {wm.addView(view,params);attached=true} catch(_:Exception){}
 }
 fun update(points:List<JudgmentPointDetector.Point>?,status:String,sourceW:Int,sourceH:Int){
  view.points=points
  view.status=status
  view.sourceW=sourceW
  view.sourceH=sourceH
  view.invalidate()
 }
 fun close(){
  if(attached)try{wm.removeView(view)}catch(_:Exception){}finally{attached=false}
 }
 private class OverlayView(context:Context):View(context){
  var points:List<JudgmentPointDetector.Point>?=null
  var status="Scanning..."
  var sourceW=1;var sourceH=1
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  override fun onDraw(canvas:Canvas){
   super.onDraw(canvas)
   val density=resources.displayMetrics.density
   paint.style=Paint.Style.FILL
   paint.color=0xCC101820.toInt()
   canvas.drawRoundRect(8*density,8*density,250*density,49*density,10*density,10*density,paint)
   paint.color=Color.WHITE
   paint.textSize=15*density
   canvas.drawText(status,17*density,34*density,paint)
   val sx=width.toFloat()/sourceW.coerceAtLeast(1)
   val sy=height.toFloat()/sourceH.coerceAtLeast(1)
   points?.forEachIndexed{index,p->
    val x=p.x*sx;val y=p.y*sy
    paint.color=Color.GREEN
    paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density
    canvas.drawCircle(x,y,18*density,paint)
    canvas.drawLine(x-24*density,y,x+24*density,y,paint)
    canvas.drawLine(x,y-24*density,x,y+24*density,paint)
    paint.style=Paint.Style.FILL
    canvas.drawCircle(x,y,3*density,paint)
    paint.textSize=13*density
    canvas.drawText("${index+1}",x+17*density,y-10*density,paint)
   }
  }
 }
}
