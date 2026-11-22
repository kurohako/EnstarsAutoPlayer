package com.enstars.autoplayer
import android.graphics.Bitmap
import android.graphics.Color
class NoteDetector(private val config:GameConfig) {
 fun detect(bitmap:Bitmap):List<Int> {
  val w=bitmap.width; val h=bitmap.height
  val y=(config.hitY*h).toInt().coerceIn(0,h-1)
  return config.laneXs.indices.filter { lane ->
   val x=(config.laneXs[lane]*w).toInt().coerceIn(0,w-1)
   var bright=0; var total=0
   val dx=(w*.012f).toInt().coerceAtLeast(2)
   val dy=(h*.008f).toInt().coerceAtLeast(2)
   for(yy in (y-dy).coerceAtLeast(0)..(y+dy).coerceAtMost(h-1) step 2)
    for(xx in (x-dx).coerceAtLeast(0)..(x+dx).coerceAtMost(w-1) step 2) {
     val c=bitmap.getPixel(xx,yy)
     val lum=(Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000
     if(lum>=config.threshold) bright++
     total++
    }
   total>0 && bright.toFloat()/total>=.42f
  }
 }
}