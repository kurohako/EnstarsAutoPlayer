package com.enstars.autoplayer

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.InflaterInputStream
import org.json.JSONObject
import org.json.JSONArray

/** 425 unverified TAP candidates extracted from the user-provided 60fps video.
 * Payload is zlib-compressed records: [big-endian timeMs(4), lane1to9(1), confidence(1)].
 */
object BundledVideoChart {
 private const val PAYLOAD = "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"
 fun asJson():String {
  val compressed=Base64.decode(PAYLOAD,Base64.DEFAULT)
  val stream=DataInputStream(InflaterInputStream(ByteArrayInputStream(compressed)))
  val notes=JSONArray()
  stream.use { input ->
   repeat(425) {
    val time=input.readInt()
    val lane=input.readUnsignedByte()
    val score=input.readUnsignedByte()
    require(time>=0&&lane in 1..9)
    notes.put(JSONObject().put("timeMs",time).put("lane",lane)
     .put("type","tap").put("confidence",if(score==1)"high" else "medium"))
   }
  }
  return JSONObject().put("format","enstars-video-v1")
   .put("sourceVideo","1000029417.mp4")
   .put("recognition","Unverified video candidates, incomplete")
   .put("notes",notes).toString()
 }
}
