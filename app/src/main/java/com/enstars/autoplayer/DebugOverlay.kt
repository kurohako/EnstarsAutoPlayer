package com.enstars.autoplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * The full-screen drawing surface is ALWAYS non-touchable.
 * Only nine small movable handle windows and the SAVE button intercept touches.
 */
class DebugOverlay(private val context:Context){
 private val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
 private val view=OverlayView(context)
 private var attached=false
 private var editing=false
 private var onSave:(()->Unit)?=null
 private val handles=mutableListOf<View>()
 private var saveButton:TextView?=null
 private var syncButton:TextView?=null
 private var syncLane:Float?=null
 private val floatingButtons=mutableListOf<View>()
 private val density=context.resources.displayMetrics.density
 private val handleSize=(54*density).roundToInt()
 private val windowFlags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
  WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
 private fun params(w:Int,h:Int,x:Int,y:Int,touchable:Boolean)=
  WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
   windowFlags or (if(touchable)0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
   PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;this.x=x;this.y=y}
 fun show(){
  if(attached)return
  wm.addView(view,params(-1,-1,0,0,false));attached=true
 }
 fun showFixed(config:GameConfig){
  removeControls()
  editing=false
  view.xs=config.laneXs.toMutableList()
  view.ys=config.laneYs.toMutableList()
  view.status="FIXED 9 POINTS"
  view.showTargets=true
  view.invalidate()
 }
 fun edit(config:GameConfig,save:()->Unit){
  show()
  showFixed(config)
  editing=true;onSave=save
  view.status="DRAG YELLOW 1-9 / SAVE"
  view.invalidate()
  view.post{if(editing&&attached)installControls()}
 }
 private fun installControls(){
  removeControls()
  if(view.width==0||view.height==0)return
  for(i in 0..8){
   val handle=object:View(context){
    private val marker=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas){
     val center=handleSize/2f
     marker.style=Paint.Style.FILL
     marker.color=0x6633CCFF
     canvas.drawCircle(center,center,center-2*density,marker)
     marker.style=Paint.Style.STROKE
     marker.strokeWidth=2.5f*density
     marker.color=Color.YELLOW
     canvas.drawCircle(center,center,center-3*density,marker)
     marker.style=Paint.Style.FILL
     marker.color=Color.WHITE
     marker.textAlign=Paint.Align.CENTER
     marker.textSize=17*density
     marker.isFakeBoldText=true
     canvas.drawText("${i+1}",center,center+6*density,marker)
    }
   }
   val (x,y)=view.screenPoint(i)
   var startX=0f;var startY=0f
   var moved=false
   handle.setOnTouchListener{_,event->
    when(event.actionMasked){
     MotionEvent.ACTION_DOWN->{
      startX=event.rawX;startY=event.rawY;moved=false;true
     }
     MotionEvent.ACTION_MOVE->{
      val dx=event.rawX-startX;val dy=event.rawY-startY
      if(!moved && (dx*dx+dy*dy)>=(8*density)*(8*density))moved=true
      if(moved){
       val p=view.screenPoint(i)
       view.store(i,p.first+dx,p.second+dy)
       startX=event.rawX;startY=event.rawY
       positionHandle(i)
       view.invalidate()
      }
      true
     }
     MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->true
     else->true
    }
   }
   handles.add(handle)
   wm.addView(handle,params(handleSize,handleSize,(x-handleSize/2).roundToInt(),(y-handleSize/2).roundToInt(),true))
  }
  val button=TextView(context).apply{
   text="SAVE"
   textSize=15f
   gravity=Gravity.CENTER
   setTextColor(Color.WHITE)
   setBackgroundColor(0xDD187C35.toInt())
   setOnClickListener{saveEdit()}
  }
  saveButton=button
  wm.addView(button,params((75*density).roundToInt(),(40*density).roundToInt(),
   view.width-(85*density).roundToInt(),(6*density).roundToInt(),true))
 }
 private fun positionHandle(i:Int){
  if(i !in handles.indices)return
  val p=view.screenPoint(i)
  val lp=handles[i].layoutParams as WindowManager.LayoutParams
  lp.x=(p.first-handleSize/2).roundToInt()
  lp.y=(p.second-handleSize/2).roundToInt()
  wm.updateViewLayout(handles[i],lp)
 }
 private fun removeControls(){
  for(handle in handles)wm.removeView(handle)
  handles.clear()
  saveButton?.let{wm.removeView(it)}
  saveButton=null
 }
 fun saveEdit(){
  if(!editing)return
  GameConfig.save(context,view.xs,view.ys)
  editing=false
  removeControls()
  view.status="9 POINTS SAVED"
  view.invalidate()
  onSave?.invoke()
 }
 fun showPlayerControls(onStart:()->Unit,onStop:()->Unit){
  show()
  view.showTargets=false
  view.invalidate()
  clearPlayerControls()
  val items=listOf("START" to onStart,"HOLD STOP" to onStop)
  for((index,item) in items.withIndex()){
   val button=TextView(context).apply{
    text=item.first
    textSize=12f
    gravity=Gravity.CENTER
    setTextColor(Color.WHITE)
    setBackgroundColor(if(index==0)0xDD187C35.toInt() else 0xDDB32626.toInt())
    if(index==0){
     setOnClickListener{item.second.invoke()}
    }else{
     // An injected chart tap lasts ~24ms and must NEVER stop playback.
     // Physical long press is required to stop.
     isLongClickable=true
     setOnLongClickListener{item.second.invoke();true}
    }
   }
   floatingButtons.add(button)
   wm.addView(button,params(((if(index==0)78 else 105)*density).roundToInt(),(42*density).roundToInt(),
    (12*density).roundToInt(),((115+index*48)*density).roundToInt(),true))
  }
 }
 private fun clearPlayerControls(){
  for(button in floatingButtons)wm.removeView(button)
  floatingButtons.clear()
 }
 private fun clearSyncTarget(){
  syncButton?.let{wm.removeView(it)}
  syncButton=null
  syncLane=null
 }
 private fun placeSyncTarget(){
  val button=syncButton?:return
  val lane=syncLane?:return
  if(!attached||view.width<1||view.height<1)return
  val (x,y)=view.screenPointLane(lane)
  val p=button.layoutParams as WindowManager.LayoutParams
  val size=(60*density).roundToInt()
  p.width=size;p.height=size
  p.x=(x-size/2).roundToInt();p.y=(y-size/2).roundToInt()
  wm.updateViewLayout(button,p)
 }
 /**
  * The only touchable sync window is centered over the calibrated first note.
  * Finger-down time anchors the song clock; releasing removes the button
  * before AccessibilityService injects the note gesture.
  */
 fun showSyncAtLane(lane:Float,onSync:(Long)->Unit){
  show()
  clearSyncTarget()
  view.showTargets=false
  view.invalidate()
  syncLane=lane.coerceIn(0f,8f)
  var triggered=false
  val button=TextView(context).apply{
   text="SYNC"
   textSize=11f
   gravity=Gravity.CENTER
   setTextColor(Color.WHITE)
   background=GradientDrawable().apply{
    shape=GradientDrawable.OVAL
    setColor(0xAA047C91.toInt())
    setStroke((3*density).roundToInt(),Color.CYAN)
   }
   setOnTouchListener{_,e->
    when(e.actionMasked){
     MotionEvent.ACTION_DOWN->{
      if(!triggered){
       triggered=true
       val fingerDownAt=SystemClock.uptimeMillis()
       // Remove overlay before dispatching an injected gesture. We deliberately
       // do NOT wait for ACTION_UP: that added a full finger-press duration to
       // the first note while the game never saw the original touch.
       post {
        if(syncButton===this){
         clearSyncTarget()
         onSync(fingerDownAt)
        }
       }
      }
      true
     }
     MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->true
     else->true
    }
   }
  }
  syncButton=button
  val size=(60*density).roundToInt()
  val (x,y)=view.screenPointLane(syncLane!!)
  wm.addView(button,params(size,size,(x-size/2).roundToInt(),(y-size/2).roundToInt(),true))
  view.post{placeSyncTarget()}
 }
 /** Use precisely the same overlay dimensions for SYNC and injected gestures.
  * DisplayMetrics can exclude an inset or contain stale orientation dimensions.
  */
 fun touchViewport():Pair<Int,Int>{
  val w=view.width.takeIf{it>0}?:context.resources.displayMetrics.widthPixels
  val h=view.height.takeIf{it>0}?:context.resources.displayMetrics.heightPixels
  return Pair(w,h)
 }
 fun syncTargetCenter(lane:Float):Pair<Float,Float> = view.screenPointLane(lane)
 fun updateStatus(status:String){view.status=status;view.invalidate()}
 fun close(){
  editing=false
  removeControls()
  clearSyncTarget()
  clearPlayerControls()
  if(attached){wm.removeView(view);attached=false}
 }
 private inner class OverlayView(context:Context):View(context){
  var xs=GameConfig().laneXs.toMutableList()
  var ys=GameConfig().laneYs.toMutableList()
  var status="FIXED"
  var showTargets=true
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
   super.onSizeChanged(w,h,oldw,oldh)
   post{placeSyncTarget()}
  }
  fun screenPointLane(lane:Float):Pair<Float,Float>{
   val lo=kotlin.math.floor(lane).toInt().coerceIn(0,8)
   val hi=(lo+1).coerceAtMost(8)
   val t=(lane-lo).coerceIn(0f,1f)
   val px=xs[lo]*(1-t)+xs[hi]*t
   val py=ys[lo]*(1-t)+ys[hi]*t
   return if(width>=height)Pair(px*width,py*height)
    else Pair(py*width,(1f-px)*height)
  }
  fun screenPoint(i:Int):Pair<Float,Float> =
   if(width>=height)Pair(xs[i]*width,ys[i]*height)
   else Pair(ys[i]*width,(1f-xs[i])*height)
  fun store(i:Int,x:Float,y:Float){
   if(width>=height){
    xs[i]=(x/width).coerceIn(0f,1f)
    ys[i]=(y/height).coerceIn(0f,1f)
   }else{
    xs[i]=(1f-y/height).coerceIn(0f,1f)
    ys[i]=(x/width).coerceIn(0f,1f)
   }
  }
  override fun onDraw(canvas:Canvas){
   paint.style=Paint.Style.FILL
   paint.color=0xCC101820.toInt()
   canvas.drawRoundRect(5*density,5*density,230*density,44*density,8*density,8*density,paint)
   paint.color=Color.WHITE
   paint.textSize=13*density
   canvas.drawText(status,12*density,29*density,paint)
   if(showTargets)for(i in 0..8){
    val (x,y)=screenPoint(i)
    paint.color=if(editing)Color.YELLOW else Color.GREEN
    paint.style=Paint.Style.STROKE
    paint.strokeWidth=2*density
    canvas.drawCircle(x,y,17*density,paint)
    canvas.drawLine(x-23*density,y,x+23*density,y,paint)
    canvas.drawLine(x,y-23*density,x,y+23*density,paint)
    paint.style=Paint.Style.FILL
    canvas.drawCircle(x,y,3*density,paint)
    paint.textSize=13*density
    canvas.drawText("${i+1}",x+16*density,y-9*density,paint)
   }
  }
 }
}
