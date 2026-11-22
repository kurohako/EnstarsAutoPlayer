package com.enstars.autoplayer

import org.json.JSONObject

/**
 * Imports offline video-derived candidates (enstars-video-v1).
 * Never treats unverified flick/hold colors as playable taps.
 * Confidence filtering is deliberately conservative.
 */
object VideoChartParser {
 fun parse(json:String):ChartParser.Chart {
  val root=JSONObject(json)
  require(root.optString("format")=="enstars-video-v1"){"Unsupported video chart format"}
  val notes=root.getJSONArray("notes")
  val taps=ArrayList<ChartParser.Tap>()
  var skipped=0
  for(i in 0 until notes.length()){
   val note=notes.optJSONObject(i)
   if(note==null){skipped++;continue}
   val type=note.optString("type")
   val confidence=note.optString("confidence")
   val time=note.optLong("timeMs",-1L)
   val lane=note.optInt("lane",-1)
   if(type=="tap"&&(confidence=="high"||confidence=="medium")&&
     time>=0L&&lane in 1..9){
    taps.add(ChartParser.Tap(time,lane-1))
   }else skipped++
  }
  val distinct=taps.distinct().sortedBy{it.timeMs}
  require(distinct.isNotEmpty()){"No supported tap candidates"}
  return ChartParser.Chart("VIDEO CANDIDATES (not verified)",distinct,skipped)
 }
}
