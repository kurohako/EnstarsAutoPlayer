package com.enstars.autoplayer
import android.content.Context
data class GameConfig(
 val laneXs: List<Float> = listOf(.15f,.29f,.43f,.57f,.71f,.85f),
 val hitY: Float = .82f,
 val threshold: Int = 190,
 val pollMs: Long = 130L,
 val cooldownMs: Long = 260L
) {
 companion object {
  fun load(context: Context): GameConfig {
   val p=context.getSharedPreferences("game",Context.MODE_PRIVATE)
   val xs=p.getString("xs","0.15,0.29,0.43,0.57,0.71,0.85")!!
    .split(",").mapNotNull { it.trim().toFloatOrNull()?.takeIf { v -> v in 0f..1f } }
   return GameConfig(xs.ifEmpty { GameConfig().laneXs },p.getFloat("y",.82f).coerceIn(.05f,.95f),p.getInt("threshold",190).coerceIn(0,255))
  }
 }
}