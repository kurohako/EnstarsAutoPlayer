package com.enstars.autoplayer

import org.json.JSONArray
import org.json.JSONObject

internal object BundledChartSupplement {
 private data class Head(val timeMs: Long, val lane: Float)

 fun apply(bundledJson: String, supplementJson: String): String {
  val bundled = JSONObject(bundledJson)
  require(bundled.optString("sourceVideo") == BundledVideoChart.SOURCE_VIDEO) {
   "Select a built-in chart first"
  }
  val supplement = JSONObject(supplementJson)
  require(supplement.optString("format") == "enstars-video-v2") { "Use a typed enstars-video-v2 JSON file" }
  val parsed = VideoChartParser.parse(supplementJson)
  val rows = supplement.getJSONArray("notes")
  require(parsed.events.isNotEmpty() && parsed.events.size == rows.length() && parsed.skipped == 0) {
   "Every entry needs a supported note type and timing, including the end of any Hold or Slide"
  }

  val existing = bundled.getJSONArray("notes")
  val originalHeads = (0 until existing.length()).map { existing.getJSONObject(it).head() }.toSet()
  val editedHeads = mutableSetOf<Head>()
  val replacedHeads = mutableSetOf<Head>()
  val additions = (0 until rows.length()).map { index ->
   val row = rows.getJSONObject(index)
   val head = row.head()
   require(editedHeads.add(head)) { "Multiple entries use the same time and lane" }
   if (row.has("replaceTimeMs")) {
    val old = head.copy(timeMs = row.getLong("replaceTimeMs"))
    require(old in originalHeads && replacedHeads.add(old)) {
     "Each replacement must identify an existing built-in note on the same lane"
    }
   }
   row.remove("replaceTimeMs")
   row
  }

  val merged = JSONArray()
  for (index in 0 until existing.length()) {
   val note = existing.getJSONObject(index)
   if (note.head() !in editedHeads && note.head() !in replacedHeads) merged.put(note)
  }
  additions.forEach { merged.put(it) }
  return bundled.put("notes", merged).toString()
 }

 private fun JSONObject.head() = Head(getLong("timeMs"), getDouble("lane").toFloat())
}
