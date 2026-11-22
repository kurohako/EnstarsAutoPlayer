package com.enstars.autoplayer

/** Reads documented esmchartplayer format including flicks and timed hold knots. */
object RichTextChartParser {
 private data class Section(val offset:Double,val beats:Int,val bpm:Double)
 private val sectionPattern=Regex("""^(\d+)#([\d.]+):(\d+):([\d.]+)$""")
 // e.g 1:5_0+0/4, :5_0+0/4, _2+1/4, +2/4, 12.500
 private val timePattern=Regex("""^(?:(\d+):|:)?(?:(\d+)_|_(\d+))?(\d+)?(?:\+(\d+)/(\d+)|\.(\d+))?$""")
 private val headPattern=Regex("""@([OSLRUD])(-?\d+(?:\.5)?)(?:G\d+)?""")
 private val tailPattern=Regex("""@([OSLRUD]?)(-?\d+(?:\.5)?)(?:G\d+)?""")
 fun parse(input:String):ChartParser.Chart {
  val sections=mutableMapOf<Int,Section>()
  val result=mutableListOf<PlayNote>()
  var difficulty=""
  var skipped=0
  var secNo=1;var bar=0;var beat=0;var fraction=0.0
  var currentMs=0L
  fun clock(notation:String):Long? {
   val text=notation.trim().removeSuffix("?")
   if(text.isEmpty())return currentMs
   if(text.matches(Regex("""\d+\.\d+"""))){
    currentMs=(text.toDouble()*1000).toLong()
    return currentMs
   }
   val m=timePattern.matchEntire(text)?:return null
   val (sec,b,bt,directBeat,numer,denom,dec)=m.destructured
   if(sec.isNotBlank()){secNo=sec.toInt();bar=0;beat=0;fraction=0.0}
   if(b.isNotBlank()){bar=b.toInt();beat=0;fraction=0.0}
   if(bt.isNotBlank()){beat=bt.toInt();fraction=0.0}
   if(directBeat.isNotBlank()){beat=directBeat.toInt();fraction=0.0}
   if(numer.isNotBlank()){
    val den=denom.toDoubleOrNull()?:return null
    if(den==0.0)return null
    fraction=numer.toDouble()/den
   }else if(dec.isNotBlank())fraction=("0."+dec).toDouble()
   val segment=sections[secNo]?:return null
   if(segment.bpm<=0||segment.beats<=0)return null
   currentMs=((segment.offset+(bar*segment.beats+beat+fraction)*60.0/segment.bpm)*1000).toLong()
   return currentMs
  }
  fun kind(code:String):NoteKind?=when(code){
   "O","S"->NoteKind.TAP
   "L"->NoteKind.FLICK_LEFT
   "R"->NoteKind.FLICK_RIGHT
   "U"->NoteKind.FLICK_UP
   "D"->NoteKind.FLICK_DOWN
   else->null
  }
  for(raw in input.lineSequence()){
   val line=raw.substringBefore("//").trim()
   if(line.isBlank())continue
   if(difficulty.isBlank()&&line.contains("_LEVEL_")){difficulty=line;continue}
   val section=sectionPattern.matchEntire(line)
   if(section!=null){
    val (number,offset,beats,bpm)=section.destructured
    sections[number.toInt()]=Section(offset.toDouble(),beats.toInt(),bpm.toDouble())
    continue
   }
   if(line.startsWith("v=")||line.startsWith("stop=")||line.startsWith(">")||
      (line.startsWith("G")&&line.contains(">")))continue
   if(line.contains("!")||line.contains("~")||line.endsWith("?")){skipped++;continue}
   val firstAt=line.indexOf('@')
   if(firstAt<0)continue
   val prefix=line.substring(0,firstAt)
   val atMs=clock(prefix)
   if(atMs==null){skipped++;continue}
   val chunks=line.substring(firstAt).split("=")
   val heads=headPattern.findAll(chunks.first()).toList()
   if(heads.isEmpty()){skipped++;continue}
   // Knot notation after = belongs only to the last head on this line.
   val knots=mutableListOf<ChartWaypoint>()
   var release:NoteKind?=null
   if(chunks.size>1){
    for(tail in chunks.drop(1)){
     val tailAt=tail.indexOf('@')
     if(tailAt<0){skipped++;continue}
     val kt=clock(tail.substring(0,tailAt))
     val note=tailPattern.find(tail.substring(tailAt))
     val pos=note?.groupValues?.get(2)?.toFloatOrNull()
     if(kt==null||pos==null||pos !in -4f..4f){skipped++;continue}
     knots.add(ChartWaypoint(kt,pos+4f))
     release=kind(note.groupValues[1])?.takeIf{it!=NoteKind.TAP}
    }
   }
   for((index,m) in heads.withIndex()){
    val pos=m.groupValues[2].toFloatOrNull()
    val type=kind(m.groupValues[1])
    if(pos==null||pos !in -4f..4f||type==null){skipped++;continue}
    val lane=pos+4f
    if(index==heads.lastIndex && knots.isNotEmpty()){
     val valid=knots.filter{it.timeMs>=atMs}.sortedBy{it.timeMs}
     if(valid.isNotEmpty()&&valid.last().timeMs>atMs){
      val slide=valid.any{it.lane!=lane}
      result+=PlayNote(atMs,lane,if(slide)NoteKind.SLIDE else NoteKind.HOLD,
       valid.last().timeMs,valid,releaseKind=release)
     }else{skipped++;result+=PlayNote(atMs,lane,type)}
    }else result+=PlayNote(atMs,lane,type)
   }
  }
  val unique=result.distinctBy{listOf(it.timeMs,it.lane,it.kind,it.endTimeMs)}
   .sortedWith(compareBy<PlayNote>{it.timeMs}.thenBy{it.lane})
  return ChartParser.Chart(difficulty,unique,skipped)
 }
}
