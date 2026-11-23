package com.enstars.autoplayer

import java.util.Base64
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.InflaterInputStream
import org.json.JSONObject
import org.json.JSONArray

/** 508 unverified TAP candidates (one first-hit correction) extracted from the user-provided 60fps video.
 * Payload is zlib-compressed records: [big-endian timeMs(4), lane1to9(1), confidence(1)].
 */
object BundledVideoChart {
 internal const val SOURCE_VIDEO = "1000029417.mp4"
 private const val PAYLOAD = "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"
 // Direction evidence: pink circular arrows consistently point left in sampled
 // source frames; orange glyphs below were checked by right-arrow outline.
 // Timing is video-estimated and still requires adjustment on device.
 private const val LEFT = "15164:8;24717:8;25326:4;32767:9;44550:1;64050:9;64467:7;64683:1;67237:9;67683:7;68117:1;69817:1;69833:7;70700:1;70700:7;71550:1;74117:1;77138:9;77333:7;77550:1;77967:4;88483:4;88483:9;90400:3;91267:3;92117:3;92133:9;94683:1;101550:1;103267:7;103683:7;104117:7;106683:7;107117:7;107533:7;110967:1;110967:7;111833:1;111833:7;112683:1;115267:1;128117:2"
 private const val RIGHT = "15083:2;21400:9;44550:9;63817:1;64250:3;67900:3;68100:9;70267:3;70387:9;71133:3;71133:9;71533:9;74133:9;77767:6;78200:9;88050:1;88050:6;90833:7;91700:7;101550:9;111400:9;112283:3;115267:9;128117:8"
 private const val SLOW_TAPS = "24033:7:2;24167:3:2;26433:2:1;28033:6:1;28633:3:1;36567:6:2;37433:4:1;40767:2:1;41367:2:2;44100:7:1;47833:3:1;50100:8:2;51233:3:2;52633:9:2;54700:7:1;56433:3:1;59033:8:2;62900:4:1;65967:4:2;66767:2:1;66767:8:2;74300:7:1;76433:2:2;80300:4:1;80967:8:1;81033:4:1;81767:8:2;85900:6:1;87633:7:1;94900:7:1;95367:2:2;102367:2:1;102767:2:2;104300:6:1;109633:8:2;112833:4:1;113700:6:1;113767:2:1;121633:6:1;122233:4:1;124833:2:1;126833:5:1"
 private const val SLOW_RETIMING = "7583:8:-42;11967:8:-34;16567:7:-34;16900:7:-34;20433:8:-33;22267:2:-45;23767:6:-34;29150:1:-45;35633:8:-33;36317:2:-42;41450:8:-42;43567:8:-34;43583:2:-42;43900:6:-34;49050:6:-42;50767:2:-34;50983:8:-42;51833:8:-33;53233:3:-33;56967:1:-34;57833:5:-33;58050:6:-42;58900:7:-34;61033:5:-33;62117:4:-42;64900:6:-34;70900:2:-34;71967:9:-34;72833:5:-33;72850:1:-42;73700:5:-34;74667:2:-45;74967:7:-34;75633:3:-33;76050:7:-42;80767:2:-34;83983:8:-42;88900:6:-34;89117:2:-42;92767:2:-34;93833:3:-33;96833:6:-33;99833:6:-33;102833:8:-33;104967:2:-34;104967:8:-34;105833:2:-33;105833:8:-33;106367:4:-34;107967:2:-34;108833:4:-33;108833:6:-33;110117:6:-42;114633:3:-33;116767:8:-34;116983:4:-42;117833:9:-33;118700:3:-34;120850:2:-42;121917:2:-42;124700:9:-34;125317:4:-42"
 private const val SLOW_RIGHT = "64700:9;67433:1;76633:5;77100:3;94700:9;111367:3;112633:9"
 fun asJson(enableExperimentalFlicks:Boolean=true,useSlowRefinement:Boolean=false):String {
  val timingMap=if(useSlowRefinement)SLOW_RETIMING.split(";").mapNotNull{entry->
   val p=entry.split(":")
   if(p.size!=3)null else {
    val at=p[0].toLongOrNull()
    val lane=p[1].toIntOrNull()
    val change=p[2].toLongOrNull()
    if(at==null||lane==null||change==null)null else (at to lane) to change
   }
  }.toMap() else emptyMap()

  val compressed=Base64.getDecoder().decode(PAYLOAD)
  val stream=DataInputStream(InflaterInputStream(ByteArrayInputStream(compressed)))
  val notes=JSONArray()
  stream.use { input ->
   repeat(507) {
    val time=input.readInt()
    val lane=input.readUnsignedByte()
    val score=input.readUnsignedByte()
    require(time>=0&&lane in 1..9)
    val corrected=time+timingMap.getOrDefault(time.toLong() to lane,0L)
    notes.put(JSONObject().put("timeMs",corrected).put("lane",lane)
     .put("type","tap").put("confidence",if(score==1)"high" else "medium"))
   }
  }
  // Verified against the source video: first lane-6 hit at ~6.83 seconds.
  // The original vision tracker missed this head, making first-note sync
  // incorrectly anchor to the second note (~7.58 seconds, lane 8).
  notes.put(JSONObject().put("timeMs",6833).put("lane",6)
   .put("type","tap").put("confidence","medium"))
  if(useSlowRefinement){
   for(entry in SLOW_TAPS.split(";")){
    val parts=entry.split(":")
    if(parts.size!=3)continue
    val time=parts[0].toLongOrNull()?:continue
    val lane=parts[1].toIntOrNull()?:continue
    val confidence=if(parts[2]=="1")"high" else "medium"
    notes.put(JSONObject().put("timeMs",time).put("lane",lane)
     .put("type","tap").put("confidence",confidence)
     .put("source","two-method slow-video verification"))
   }
  }
  if(enableExperimentalFlicks){
   for((coded,type) in listOf(LEFT to "flick_left",RIGHT to "flick_right")){
    for(entry in coded.split(";")){
     val parts=entry.split(":")
     if(parts.size!=2)continue
     val at=parts[0].toLongOrNull()?:continue
     val lane=parts[1].toIntOrNull()?:continue
     notes.put(JSONObject().put("timeMs",at).put("lane",lane)
      .put("type",type).put("confidence","medium")
      .put("source","visual arrow candidate; timing unverified"))
    }
   }
  }
  if(useSlowRefinement&&enableExperimentalFlicks){
   for(entry in SLOW_RIGHT.split(";")){
    val p=entry.split(":")
    if(p.size!=2)continue
    val at=p[0].toLongOrNull()?:continue
    val lane=p[1].toIntOrNull()?:continue
    notes.put(JSONObject().put("timeMs",at).put("lane",lane)
     .put("type","flick_right").put("confidence","medium")
     .put("source","slow-video green right-arrow inspection"))
   }
  }
  return JSONObject().put("format","enstars-video-v2")
   .put("sourceVideo",SOURCE_VIDEO)
   .put("referenceVideo",if(useSlowRefinement)"1000029428.mp4" else "none")
   .put("slowRefinement",useSlowRefinement)
   .put("referenceAlignmentMs",if(useSlowRefinement)3500 else 0)
   .put("recognition","Unverified taps + experimental left/right flicks; holds not inferred")
   .put("notes",notes).toString()
 }
}
