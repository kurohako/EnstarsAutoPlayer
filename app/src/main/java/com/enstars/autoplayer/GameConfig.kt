package com.enstars.autoplayer
import android.content.Context
data class GameConfig(
 val laneXs: List<Float> = listOf(301f,392f,512f,653f,807f,960f,1101f,1222f,1313f).map{it/1536f},
 val laneYs: List<Float> = listOf(340f,465f,561f,622f,642f,622f,561f,465f,340f).map{it/698f},
 val hitY: Float = .82f,
 val threshold: Int = 190,
 val pollMs: Long = 130L,
 val cooldownMs: Long = 260L
) {
 companion object {
  fun load(context: Context): GameConfig {
   val p=context.getSharedPreferences("game",Context.MODE_PRIVATE)
   val xs=p.getString("xs",null)?.split(",")?.mapNotNull{it.trim().toFloatOrNull()?.takeIf{v->v in 0f..1f}}
   val defaults=GameConfig()
   val useXs=xs?.takeIf{it.size==9} ?: defaults.laneXs
   return GameConfig(laneXs=useXs,laneYs=defaults.laneYs,hitY=p.getFloat("y",.82f),threshold=p.getInt("threshold",190).coerceIn(0,255))
  }
 }
}
