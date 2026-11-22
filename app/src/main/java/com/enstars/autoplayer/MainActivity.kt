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
  label("Enstars AutoPlayer V2.8 - Manual 9-point calibration")
  label("1. Open the game Live screen. Return here and choose Edit 9 points. Drag each yellow crosshair to its judgment circle. Tap SAVE in the upper right.")
  button("Open Accessibility Settings"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
  button("Show saved 9 points"){service()?.start()}
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
