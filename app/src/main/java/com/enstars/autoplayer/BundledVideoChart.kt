package com.enstars.autoplayer

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.InflaterInputStream
import org.json.JSONObject
import org.json.JSONArray

/** 508 unverified TAP candidates (one first-hit correction) extracted from the user-provided 60fps video.
 * Payload is zlib-compressed records: [big-endian timeMs(4), lane1to9(1), confidence(1)].
 */
object BundledVideoChart {
 private const val PAYLOAD = "eNollnlUjXkYx+/v7SbEmMguWRJhOI0ZWycplGWUMwwTkuFYspuMmXDsEZJiiKKEkMRUxyHODe3ZKy0iSpTKcrPPcdLczzP/fM9zPs/ned7fvb2979XpbKKbKp2uW0e9KXsc10zpcIW6/49NTDngKl3HcfAR3ylTurRrZsqRFy1M6XrUzJRuemq3CKZGzWFqVDX+6AnMugcz5b4PPjZFL8mGiWVwT1e411L2eOXh/zyBbVO3sWHqF9K7hO6MtpKv8GfOYc/MYupZDfi+P9Cd2wQydy1T8/qzbV4ifL499fxIqRvxFw6THI258B+mlvVh57JA6uVW1Cum4ay4ydTKx5z29+GcPGA8ZE0dZmAcm7cPow7awFRQLjt3bKTe6Q3f5YgT3Jk6uBK+uwwnZAt1qDl83/fUkfLpDmdSH22v6UyZComxgcTUsudYDDxWtsUehZ8cTH0ynvrUAurTfTnh6WjOf2YgU2dcuGL8OrrxJZgJdnQTtmImOZqbMiWVT3c5HH6lAG74lllDX4ihhm8g9Tr8agR7rvnrJXGuy18tLYBu2kvqjAPwjCOYmasxs7w4Q1YyJLs9JLs3TvYb/NzuzObGUN8IgN+ci3+rH+atd/DbN6nvnKC+29RMkjPfrae+NwaeZwPJG8Bp8y24VsEopgoOS91IfX+jJslVihyZLZIzFOczW1zJbEk8/gN/M0m6pXI/P7RTkpiPCuFl53Aeb1eS8Ce+mOVD9ZLsqdBxxYoavuenaZDKSGafzcV87gSpsoZU74RUZ7LnRRQ7a+YzW+vCVerke6szQl5egrwKg7z2Y/MbN/w3k5k1zocbN7DZWIlvfMfm+mnw+lvwt39D3sZC3i3l6u/HaJJ0P3Rl/4cqnI8GnE+D4J/CcT4vgjdshTQU0/36E+SrD92v1+GN1vDGBk3SRJSuyLRTqSiTqbQ/Lcggk6PMZpscpe+Dow+GmPN/pMzLMJuXYVqOZNbygpLEbBGC03Iq3ZYL6H7D3a5adTCThLcqx7HaJBlGt80oTZLa+r0mSd2OO1a1XwDp4M6GDgls6GhrLtmM/ATpdI+rd6rgnJ3jIF1a4nfZhNllLye0mQ7pOkgviW9rCbF9htPtLFM98un2jOeKdjzTlF0x3I7np+p1jSn7NnD7IIj9IU7YeyV7+gzTS+I4tGbKoRTiUIfTz4A/9AM5zBsyfAfd4fvxnTbAnRKpnTvRdeZZpEYshY/Igbu4U7vEwUduxhlZRe06nq7rDLpuPeFu8hndJ0I8htD1iISMbaVJ4ozlWa3GvoBP1OAT60hPF7hnsl4S0ysY7mXU63Rq0nS6ky/Cp+yhnvIO85cATRI+1RUyrQXfwPTFkuv4fmaMxp9xmu5MG00Sf+YHuj4G/po+d+jOkr+77wYlyYbZHvgLE0g/K4jfNiXJ1CJf+OLe8MVD2LaE56RaskrqEiWJuTQRvmyHmST+8jnMrnDiHljZhq6/A2dYpXBWeeP8kYfzVyg8wA9nDW9JtbYTfONH9m8+9X+yZ8tyuluHMBvYHT/wX8i2fMj2CEiQP+fZMYGpnW3Ys7OBK+4q5Cq7V0N2y50T4gUPSWd2D08btWcX20J1dENzMEMfwMOc4WHyGfe2hew9Rndfe/iBC+wJl/+Rg/K3OMhzzJT4hwbAIyw0SUjEE64YeQnz8GC6hzey4YifJokT5QaPOokT3Rwe3RkezZtXRefDT8h/4okq6tjx8NirmiTmyXDIqRWaJOT0OK4b110vyVRcDRvOpGmSkHh+Oah4fjmos7z1VIIzs+escc71wjn3ivo87251vohuoh2ZxJtXJXlRJ9dqktQXkjRJ6ou86dTF49QpBWxIkefe5Sz4lRDIFX6nKcNAiMGRK6Z2gKTW6yUhV3M557Uw7orrizhtunw/6XIvpb+HZPyKk3EbP9ORbZn78bPW42Q9hWfL0y+b3z8qx5YNOdXcPzmfcHLvQW4c1CRxbq5k6tY4vSTmbSs23Ob3mylx7hRw793lrafunqV7L5Buno+ZJDy/F5vzvypJ9hQU071/3lySbmEQZtEkrlj0G1csdsApaa1JYpaU4jxIhpRaMlW6C/OhPNkeOcMfFVCXtaVbdp98PATnyXr4E96YqnwaZyhfwHkq5J31lF8d6mk5TmWKksR5Ju+X5/LMfy5PoapB+NVdlCTOC0+cFwa6NYFcq9YHXrsMp84D/6WtmSTOy8/4r/KUJObrQ/A3hTjG8dRGfz6v8SGkvqcmyZ76OvbXf2mi/gNUDisJ"
 // Direction evidence: pink circular arrows consistently point left in sampled
 // source frames; orange glyphs below were checked by right-arrow outline.
 // Timing is video-estimated and still requires adjustment on device.
 private const val LEFT = "15164:8;24717:8;25326:4;32767:9;44550:1;64050:9;64467:7;64683:1;67237:9;67683:7;68117:1;69817:1;69833:7;70700:1;70700:7;71550:1;74117:1;77138:9;77333:7;77550:1;77967:4;88483:4;88483:9;90400:3;91267:3;92117:3;92133:9;94683:1;101550:1;103267:7;103683:7;104117:7;106683:7;107117:7;107533:7;110967:1;110967:7;111833:1;111833:7;112683:1;115267:1;128117:2"
 private const val RIGHT = "15083:2;21400:9;44550:9;63817:1;64250:3;67900:3;68100:9;70267:3;70387:9;71133:3;71133:9;71533:9;74133:9;77767:6;78200:9;88050:1;88050:6;90833:7;91700:7;101550:9;111400:9;112283:3;115267:9;128117:8"
 fun asJson(enableExperimentalFlicks:Boolean=true):String {
  val compressed=Base64.decode(PAYLOAD,Base64.DEFAULT)
  val stream=DataInputStream(InflaterInputStream(ByteArrayInputStream(compressed)))
  val notes=JSONArray()
  stream.use { input ->
   repeat(507) {
    val time=input.readInt()
    val lane=input.readUnsignedByte()
    val score=input.readUnsignedByte()
    require(time>=0&&lane in 1..9)
    notes.put(JSONObject().put("timeMs",time).put("lane",lane)
     .put("type","tap").put("confidence",if(score==1)"high" else "medium"))
   }
  }
  // Verified against the source video: first lane-6 hit at ~6.83 seconds.
  // The original vision tracker missed this head, making first-note sync
  // incorrectly anchor to the second note (~7.58 seconds, lane 8).
  notes.put(JSONObject().put("timeMs",6833).put("lane",6)
   .put("type","tap").put("confidence","medium"))
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
  return JSONObject().put("format","enstars-video-v2")
   .put("sourceVideo","1000029417.mp4")
   .put("recognition","Unverified taps + experimental left/right flicks; holds not inferred")
   .put("notes",notes).toString()
 }
}
