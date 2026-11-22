package com.enstars.autoplayer

/** Facade for both video-derived JSON and esmchartplayer text charts. */
object ChartParser {
 data class Tap(val timeMs:Long,val lane:Int)
 data class Chart(
  val difficulty:String,
  val events:List<PlayNote>,
  val skipped:Int
 ) {
  val taps:List<Tap> get()=events.filter{it.kind==NoteKind.TAP}
   .map{Tap(it.timeMs,it.lane.toInt())}
  val counts:Map<NoteKind,Int> get()=events.groupingBy{it.kind}.eachCount()
 }
 fun parse(input:String):Chart {
  return if(input.trimStart().startsWith("{"))VideoChartParser.parse(input)
   else RichTextChartParser.parse(input)
 }
}
