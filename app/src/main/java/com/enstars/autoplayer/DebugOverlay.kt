package com.enstars.autoplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyCharacterMap
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
 private var lastViewport:CalibratedViewport?=null
 private var editing=false
 private var onSave:(()->Unit)?=null
 private val handles=mutableListOf<View>()
 private var saveButton:TextView?=null
 private var syncButton:TextView?=null
 private var syncAdded=false
 private var syncLane:Float?=null
 private val floatingButtons=mutableListOf<View>()
 private val density=context.resources.displayMetrics.density
 private val handleSize=(54*density).roundToInt()
 private val windowFlags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
  WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
 private fun params(w:Int,h:Int,x:Int,y:Int,touchable:Boolean)=
  WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
   windowFlags or (if(touchable)0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
   PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.LEFT;this.x=x;this.y=y}
 private fun markerParams(size:Int,lane:Float,bounds:CalibratedViewport):WindowManager.LayoutParams{
  val (x,y)=bounds.point(lane,view.xs,view.ys)
  return params(size,size,(x-size/2f).roundToInt(),(y-size/2f).roundToInt(),true).apply{
   // These small windows use display coordinates, including when centered at a viewport edge.
   setFitInsetsTypes(0)
   layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
   flags=flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
  }
 }
 fun show(){
  if(attached)return
  wm.addView(view,params(-1,-1,0,0,false));attached=true
 }
 fun showFixed(config:GameConfig){
  removeControls()
  clearSyncTarget()
  clearPlayerControls()
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
  val bounds=touchViewport()?:return
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
       view.store(i,p.x+dx,p.y+dy)
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
   wm.addView(handle,markerParams(handleSize,i.toFloat(),bounds))
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
  val bounds=touchViewport()?:return
  wm.updateViewLayout(handles[i],markerParams(handleSize,i.toFloat(),bounds))
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
 fun showPlayerControls(onStart: (()->Unit)?,onStop:()->Unit){
  show()
  view.showTargets=false
  view.invalidate()
  clearPlayerControls()
  val items=listOfNotNull(onStart?.let{"START" to it},"HOLD STOP" to onStop)
  for(item in items){
   val start=item.first=="START"
   val button=TextView(context).apply{
    text=item.first
    textSize=12f
    gravity=Gravity.CENTER
    setTextColor(Color.WHITE)
    setBackgroundColor(if(start)0xDD187C35.toInt() else 0xDDB32626.toInt())
    if(start){
     setOnClickListener{item.second.invoke()}
    }else{
     setOnTouchListener{_,event->event.deviceId==KeyCharacterMap.VIRTUAL_KEYBOARD}
     isLongClickable=true
     setOnLongClickListener{item.second.invoke();true}
    }
   }
   floatingButtons.add(button)
   wm.addView(button,params(((if(start)78 else 105)*density).roundToInt(),(42*density).roundToInt(),
    (12*density).roundToInt(),((if(start)115 else 163)*density).roundToInt(),true))
  }
 }
 private fun clearPlayerControls(){
  for(button in floatingButtons)wm.removeView(button)
  floatingButtons.clear()
 }
 private fun clearSyncTarget(){
  if(syncAdded)syncButton?.let{wm.removeViewImmediate(it)}
  syncAdded=false
  syncButton=null
  syncLane=null
 }
 private fun placeSyncTarget(){
  val button=syncButton?:return
  val lane=syncLane?:return
  val bounds=touchViewport()?:return
  val size=(60*density).roundToInt()
  val p=markerParams(size,lane,bounds)
  if(syncAdded)wm.updateViewLayout(button,p)
  else{
   wm.addView(button,p)
   syncAdded=true
  }
 }
 /**
  * The only touchable sync window is centered over the calibrated first note.
  * Let the physical touch finish before sending a new gesture to the game.
  */
 fun showSyncAtLane(lane:Float,onSync:(Long)->Unit){
  show()
  clearSyncTarget()
  view.showTargets=false
  view.invalidate()
  syncLane=lane.coerceIn(0f,8f)
  val tap=SyncTap()
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
     MotionEvent.ACTION_DOWN->tap.press()
     MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN->tap.cancel()
     MotionEvent.ACTION_UP->{
      if(e.x<0f||e.x>=width||e.y<0f||e.y>=height)tap.cancel()
      tap.release(e.eventTime)?.let{releasedAt->
       // Acknowledge ACTION_UP before detaching the window or injecting.
       post {
        if(syncButton===this){
         clearSyncTarget()
         onSync(releasedAt)
        }
       }
      }
     }
    }
    true
   }
  }
  syncButton=button
  placeSyncTarget()
 }
 fun touchViewport():CalibratedViewport?{
  if(!attached||view.width<1||view.height<1)return null
  val origin=IntArray(2)
  view.getLocationOnScreen(origin)
  return CalibratedViewport(view.width,view.height,origin[0],origin[1])
 }
 fun updateStatus(status:String){view.status=status;view.invalidate()}
 fun close(){
  editing=false
  lastViewport=null
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
  override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int){
   super.onLayout(changed,left,top,right,bottom)
   post{
    val bounds=touchViewport()
    if(bounds!=lastViewport){
     lastViewport=bounds
     if(editing&&attached)installControls()
     placeSyncTarget()
    }
   }
  }
  fun screenPoint(i:Int)=CalibratedViewport(width,height).point(i.toFloat(),xs,ys)
  fun store(i:Int,x:Float,y:Float){
   val normalized=CalibratedViewport(width,height).normalized(TouchPoint(x,y))
   xs[i]=normalized.x
   ys[i]=normalized.y
  }
  override fun onDraw(canvas:Canvas){
   paint.style=Paint.Style.FILL
   paint.color=0xCC101820.toInt()
   paint.textSize=13*density
   val right=(paint.measureText(status)+24*density).coerceAtMost(width-5*density)
   canvas.drawRoundRect(5*density,5*density,right,44*density,8*density,8*density,paint)
   paint.color=Color.WHITE
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
