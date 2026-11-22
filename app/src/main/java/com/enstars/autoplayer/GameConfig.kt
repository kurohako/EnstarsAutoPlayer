package com.enstars.autoplayer
import android.content.Context

data class GameConfig(
 val laneXs:List<Float> = listOf(301f,392f,512f,653f,807f,960f,1101f,1222f,1313f).map{it/1536f},
 val laneYs:List<Float> = listOf(340f,465f,561f,622f,642f,622f,561f,465f,340f).map{it/698f},
 val hitY:Float=.82f,
 val threshold:Int=190
){
 companion object {
  fun load(context:Context):GameConfig{
   val p=context.getSharedPreferences("game",Context.MODE_PRIVATE)
   val defaults=GameConfig()
   fun read(key:String,fallback:List<Float>):List<Float>{
    val values=p.getString(key,null)?.split(",")?.mapNotNull{it.trim().toFloatOrNull()}
    return values?.takeIf{it.size==9&&it.all{v->v in 0f..1f}}?:fallback
   }
   return defaults.copy(
    laneXs=read("xs",defaults.laneXs),
    laneYs=read("ys",defaults.laneYs),
    threshold=p.getInt("threshold",190).coerceIn(0,255)
   )
  }
  fun save(context:Context,xs:List<Float>,ys:List<Float>){
   require(xs.size==9&&ys.size==9&&xs.all{it in 0f..1f}&&ys.all{it in 0f..1f})
   context.getSharedPreferences("game",Context.MODE_PRIVATE).edit()
    .putString("xs",xs.joinToString(","))
    .putString("ys",ys.joinToString(",")).apply()
  }
 }
}
