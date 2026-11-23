package com.enstars.autoplayer

import org.json.JSONObject

/** Versioned video chart schema. Unsupported color candidates are never guessed. */
object VideoChartParser {
 fun parse(json:String):ChartParser.Chart {
  val root=JSONObject(json)
  val format=root.optString("format")
  require(format=="enstars-video-v1"||format=="enstars-video-v2"){"Unsupported format: $format"}
  val notes=root.getJSONArray("notes")
  val result=ArrayList<PlayNote>()
  val holdStarts=mutableMapOf<String,JSONObject>()
  val holdEnds=mutableMapOf<String,JSONObject>()
  var skipped=0
  for(i in 0 until notes.length()){
   val note=notes.optJSONObject(i)
   if(note==null){skipped++;continue}
   val rawType=note.optString("type")
   if(rawType=="hold_start"||rawType=="hold_end"){
    val id=note.optString("holdId","")
    if(id.isBlank()){skipped++;continue}
    if(rawType=="hold_start")holdStarts[id]=note else holdEnds[id]=note
    continue
   }
   val confidence=note.optString("confidence","unknown")
   if(confidence=="low"||confidence=="unknown"){skipped++;continue}
   val time=note.optLong("timeMs",-1L)
   val lane=note.optDouble("lane",-1.0).toFloat()-1f
   if(time<0L||lane !in 0f..8f){skipped++;continue}
   val kind=when(note.optString("type")){
    "tap","skill"->NoteKind.TAP
    "flick_left","left_flick"->NoteKind.FLICK_LEFT
    "flick_right","right_flick"->NoteKind.FLICK_RIGHT
    "flick_up"->NoteKind.FLICK_UP
    "flick_down"->NoteKind.FLICK_DOWN
    "hold"->NoteKind.HOLD
    "slide","slide_hold"->NoteKind.SLIDE
    else->null
   }
   if(kind==null){skipped++;continue}
   val isLong=kind==NoteKind.HOLD||kind==NoteKind.SLIDE
   val end=if(isLong)note.optLong("endTimeMs",-1L) else time
   if(isLong && (end<=time||end-time>30000L)){skipped++;continue}
   val path=mutableListOf<ChartWaypoint>()
   val points=note.optJSONArray("path")
   if(points!=null){
    for(j in 0 until points.length()){
     val p=points.optJSONObject(j)?:continue
     val pointTime=p.optLong("timeMs",-1L)
     val pointLane=p.optDouble("lane",-1.0).toFloat()-1f
     if(pointTime in time..end&&pointLane in 0f..8f){
      path.add(ChartWaypoint(pointTime,pointLane))
     }
    }
   }
   if(kind==NoteKind.SLIDE && path.isEmpty()){skipped++;continue}
   val release=when(note.optString("release","")){
    "flick_left"->NoteKind.FLICK_LEFT
    "flick_right"->NoteKind.FLICK_RIGHT
    "flick_up"->NoteKind.FLICK_UP
    "flick_down"->NoteKind.FLICK_DOWN
    else->null
   }
   result.add(PlayNote(time,lane,kind,end,path.sortedBy{it.timeMs},confidence,release))
  }
  for((id,start) in holdStarts){
   val finish=holdEnds[id]
   if(finish==null){skipped++;continue}
   val begin=start.optLong("timeMs",-1L)
   val end=finish.optLong("timeMs",-1L)
   val lane=start.optDouble("lane",-1.0).toFloat()-1f
   val endLane=finish.optDouble("lane",-1.0).toFloat()-1f
   if(begin<0||end<=begin||end-begin>30000L||lane !in 0f..8f||endLane !in 0f..8f){
    skipped++;continue
   }
   val confidence=start.optString("confidence","unknown")
   if(confidence!="high"&&confidence!="medium"){skipped++;continue}
   val rawPath=start.optJSONArray("path")
   val path=mutableListOf<ChartWaypoint>()
   if(rawPath!=null){
    for(j in 0 until rawPath.length()){
     val point=rawPath.optJSONObject(j)?:continue
     val pt=point.optLong("timeMs",-1L)
     val pl=point.optDouble("lane",-1.0).toFloat()-1f
     if(pt in (begin+1) until end && pl in 0f..8f)path.add(ChartWaypoint(pt,pl))
    }
   }
   val moving=kotlin.math.abs(lane-endLane)>.1f || path.any{kotlin.math.abs(it.lane-lane)>.1f}
   if(moving && path.isEmpty()){skipped++;continue}
   val knots=(path+ChartWaypoint(end,endLane)).sortedBy{it.timeMs}
   val release=when(finish.optString("release","")){
    "flick_left"->NoteKind.FLICK_LEFT
    "flick_right"->NoteKind.FLICK_RIGHT
    "flick_up"->NoteKind.FLICK_UP
    "flick_down"->NoteKind.FLICK_DOWN
    else->null
   }
   result.add(PlayNote(begin,lane,if(moving)NoteKind.SLIDE else NoteKind.HOLD,
    end,if(moving)knots else emptyList(),confidence,release))
  }
  skipped+=holdEnds.keys.count{it !in holdStarts}
  val unique=result.distinctBy{listOf(it.timeMs,it.lane,it.kind,it.endTimeMs)}
   .sortedWith(compareBy<PlayNote>{it.timeMs}.thenBy{it.lane})
  // Two video detectors may report the same blue circle a frame apart.
  // Remove only near-identical TAP candidates on the same lane.
  val deduplicated=mutableListOf<PlayNote>()
  for(note in unique){
   if(note.kind==NoteKind.TAP && deduplicated.any{
     it.kind==NoteKind.TAP && it.lane==note.lane &&
      kotlin.math.abs(it.timeMs-note.timeMs)<=25L
    }){
    skipped++
    continue
   }
   deduplicated.add(note)
  }
  return ChartParser.Chart(
   if(format=="enstars-video-v2")"VIDEO V2 (typed)" else "VIDEO V1 (candidates)",
   deduplicated,skipped)
 }
}
