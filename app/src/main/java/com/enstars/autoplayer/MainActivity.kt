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
import android.widget.TextView
import android.widget.Toast
class MainActivity:Activity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val prefs=getSharedPreferences("game",MODE_PRIVATE)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(36,50,36,24);gravity=Gravity.CENTER_HORIZONTAL}
  fun label(s:String)=root.addView(TextView(this).apply{text=s;textSize=16f})
  fun field(value:String):EditText=EditText(this).apply{
   setSingleLine(true);setText(value)
   root.addView(this,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
  }
  fun button(s:String,action:()->Unit)=root.addView(Button(this).apply{text=s;setOnClickListener{action()}})
  label("Enstars AutoPlayer V1 (experimental)")
  label("Lane X positions (0..1, comma separated)")
  val xs=field(prefs.getString("xs",GameConfig().laneXs.joinToString(","))!!)
  label("Legacy Y setting (curved 9-lane targets are used)")
  val y=field(prefs.getFloat("y",.82f).toString())
  label("Brightness threshold (0..255)")
  val threshold=field(prefs.getInt("threshold",190).toString())
  button("Save configuration"){
   val lanes=xs.text.toString().split(",").mapNotNull{it.trim().toFloatOrNull()}
   val yy=y.text.toString().toFloatOrNull()
   val t=threshold.text.toString().toIntOrNull()
   if(lanes.isEmpty()||lanes.size!=9||lanes.any{it !in 0f..1f}||yy==null||yy !in .05f.. .95f||t==null||t !in 0..255)
    Toast.makeText(this,"Invalid settings",Toast.LENGTH_LONG).show()
   else{
    prefs.edit().putString("xs",lanes.joinToString(",")).putFloat("y",yy).putInt("threshold",t).apply()
    Toast.makeText(this,"Saved. Restart detection to apply",Toast.LENGTH_SHORT).show()
   }
  }
  button("Open Accessibility Settings"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
  button("Start"){
   val service=AutoPlayService.instance
   if(service==null)Toast.makeText(this,"Enable accessibility service first",Toast.LENGTH_LONG).show()
   else{service.start();Toast.makeText(this,"Started",Toast.LENGTH_SHORT).show()}
  }
  button("Stop"){AutoPlayService.instance?.stop()}
  label("Experimental: no floating stop button. Return here to stop or disable the accessibility service.")
  setContentView(root)
 }
}