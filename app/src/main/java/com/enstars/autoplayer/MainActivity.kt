package com.enstars.autoplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity:Activity(){
 private val chartRequest=451
 private lateinit var chartInfo:TextView
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=chartRequest||resultCode!=RESULT_OK)return
  val uri=data?.data?:return
  try{
   val raw=contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}
    ?:throw IllegalStateException("Empty chart")
   val chart=ChartParser.parse(raw)
   if(chart.taps.isEmpty())throw IllegalArgumentException("No supported taps found")
   getSharedPreferences("chart",MODE_PRIVATE).edit().putString("chartText",raw).apply()
   chartInfo.text="Imported: "+chart.difficulty+" | TAP "+chart.taps.size+" | skipped "+chart.skipped+" | first "+chart.taps.first().timeMs+" ms"
  }catch(e:Exception){chartInfo.text="Chart import failed: "+e.message}
 }

 override fun onResume(){super.onResume();AutoPlayService.instance?.stop()}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(30,28,30,30)
   gravity=Gravity.CENTER_HORIZONTAL
  }
  fun label(s:String)=root.addView(TextView(this).apply{text=s;textSize=16f})
  fun button(s:String,action:()->Unit)=root.addView(Button(this).apply{
   text=s;setOnClickListener{action()}
   layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
  })
  fun service():AutoPlayService?{
   val s=AutoPlayService.instance
   if(s==null)Toast.makeText(this,"Enable Accessibility Service first",Toast.LENGTH_LONG).show()
   return s
  }

  label("Enstars AutoPlayer V3.1 - Video chart importer")
  chartInfo=TextView(this).apply{text="No chart imported yet";textSize=15f}
  root.addView(chartInfo)
  button("USE BUNDLED VIDEO CHART (507 TAP candidates)"){
   try{
    val raw=BundledVideoChart.asJson()
    val chart=ChartParser.parse(raw)
    getSharedPreferences("chart",MODE_PRIVATE).edit().putString("chartText",raw).apply()
    chartInfo.text="VIDEO: "+chart.taps.size+" candidate TAPs (incomplete)"
    Toast.makeText(this,"Bundled video chart selected",Toast.LENGTH_LONG).show()
   }catch(e:Exception){
    chartInfo.text="Bundled chart error: "+e.message
   }
  }
  button("Import chart (.txt or video .json)"){
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
    addCategory(Intent.CATEGORY_OPENABLE)
    type="*/*"
    putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/json","text/plain","application/octet-stream"))
   },chartRequest)
  }
  getSharedPreferences("chart",MODE_PRIVATE).getString("chartText",null)?.let{
   try{
    val chart=ChartParser.parse(it)
    chartInfo.text="Saved: "+chart.difficulty+" | TAP "+chart.taps.size+" | skipped "+chart.skipped
   }catch(_:Exception){}
  }
  label("Imported video candidates are incomplete. Only medium/high TAP candidates are playable; flick and orange notes are skipped.")
  label("Tap SYNC FIRST NOTE when the first tap reaches its judgment circle.")
  val syncOffset=EditText(this).apply{
   inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
   setText(getSharedPreferences("chart",MODE_PRIVATE).getLong("syncOffset",0L).toString())
   hint="Timing offset ms (default 0)"
  }
  root.addView(syncOffset)
  button("START CHART TAP (SYNC FIRST NOTE)"){
   val offset=syncOffset.text.toString().toLongOrNull()
   if(offset==null||offset !in -500L..500L){
    Toast.makeText(this,"Offset must be -500..500 ms",Toast.LENGTH_SHORT).show()
   }else{
    getSharedPreferences("chart",MODE_PRIVATE).edit().putLong("syncOffset",offset).apply()
    service()?.startChartAutoplay(offset)
    Toast.makeText(this,"Switch to game and sync on first note",Toast.LENGTH_LONG).show()
   }
  }

  label("1. Open the game Live screen. Return here and choose Edit 9 points. Drag each yellow crosshair to its judgment circle. Tap SAVE in the upper right.")
  button("Open Accessibility Settings"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
  button("Show saved 9 points"){service()?.start()}
  button("SHOW FLOATING START / STOP"){service()?.showFloatingControls()}
  button("Edit 9 points (drag on game)"){
   service()?.editPoints()
   Toast.makeText(this,"Switch to game, drag 9 targets, tap SAVE at top right",Toast.LENGTH_LONG).show()
  }
  button("Reload saved positions"){service()?.start()}
  label("2. Experimental Tap-only autoplay. Unsupported: hold, slide, flick, simultaneous gestures. Test in a non-competitive setting.")
  button("Test one tap at point 5"){service()?.testTap()}
  button("Start experimental TAP autoplay"){service()?.startAutoplay()}
  button("STOP autoplay and hide overlay"){service()?.stop()}
  val p=getSharedPreferences("game",MODE_PRIVATE)
  label("Brightness threshold (0..255)")
  val threshold=EditText(this).apply{
   inputType=android.text.InputType.TYPE_CLASS_NUMBER
   setText(p.getInt("threshold",190).toString())
  }
  root.addView(threshold)
  button("Save threshold"){
   val v=threshold.text.toString().toIntOrNull()
   if(v==null||v !in 0..255)Toast.makeText(this,"Invalid threshold",Toast.LENGTH_SHORT).show()
   else{p.edit().putInt("threshold",v).apply();Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show()}
  }
  label("Note detection is experimental. No automatic target recognition. Saved positions are normalized for the screen.")
  setContentView(ScrollView(this).apply{addView(root)})
 }
}
