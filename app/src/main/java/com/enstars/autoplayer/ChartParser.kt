package com.enstars.autoplayer

/** Reader for the public esmchartplayer text chart format.
 * Timing is relative to the chart's own media clock, NOT to Android game start.
 * Only independent O/S taps are emitted. Holds, flicks and special markers are skipped.
 */
object ChartParser {
 data class Tap(val timeMs:Long,val lane:Int)
 data class Chart(val difficulty:String,val taps:List<Tap>,val skipped:Int)
 private data class Section(val offset:Double,val beats:Int,val bpm:Double)
 private val sectionRegex=Regex("""^(\d+)#([\d.]+):(\d+):([\d.]+)$""")
 private val timeRegex=Regex("""^(?:(\d+):)?(?:(\d+)_|_(\d+))?(?:(\d+))?(?:\+(\d+)/(\d+)|\.(\d+))?$""")
 private val noteRegex=Regex("""@([OSLRUD])(-?\d+(?:\.5)?)(?:G\d+)?""")
 fun parse(input:String):Chart {
  val sections=mutableMapOf<Int,Section>()
  val taps=mutableListOf<Tap>()
  var difficulty=""
  var skipped=0
  var section=1;var bar=0;var beat=0;var fraction=0.0
  for(raw in input.lineSequence()){
   val line=raw.substringBefore("//").trim()
   if(line.isBlank())continue
   if(difficulty.isBlank()&&line.contains("_LEVEL_")){difficulty=line;continue}
   val sec=sectionRegex.matchEntire(line)
   if(sec!=null){
    val (no,offset,beats,bpm)=sec.destructured
    sections[no.toInt()]=Section(offset.toDouble(),beats.toInt(),bpm.toDouble())
    continue
   }
   if(line.startsWith("v=")||line.startsWith("stop=")||line.startsWith("G")&&line.contains(">")||line.startsWith(">"))continue
   // A line may have multiple simultaneous notes, or hold segments.
   val firstAt=line.indexOf('@')
   if(firstAt<0)continue
   val prefix=line.substring(0,firstAt).substringBefore('=').trim()
   if(prefix.isNotEmpty()){
    if(prefix.matches(Regex("""\d+\.\d+"""))){
     // Absolute seconds are handled separately below.
    }else{
     val m=timeRegex.matchEntire(prefix)
     if(m==null){skipped++;continue}
     val (s,b,underBeat,plainBeat,num,den,dec)=m.destructured
     if(s.isNotEmpty())section=s.toInt()
     if(b.isNotEmpty())bar=b.toInt()
     if(underBeat.isNotEmpty())beat=underBeat.toInt()
     if(plainBeat.isNotEmpty())beat=plainBeat.toInt()
     fraction=when{
      den.isNotEmpty()&&den.toInt()!=0->num.toDouble()/den.toDouble()
      dec.isNotEmpty()->("0."+dec).toDouble()
      else->0.0
     }
    }
   }
   val s=sections[section]?:continue
   if(s.bpm<=0||s.beats<=0)continue
   val absolute=if(prefix.matches(Regex("""\d+\.\d+""")))prefix.toDouble()
    else s.offset+((bar*s.beats+beat+fraction)*60.0/s.bpm)
   val timeMs=(absolute*1000).toLong()
   if(line.contains('=')||line.contains('?')||line.contains('!')||line.contains('~')){
    skipped++;continue
   }
   val matches=noteRegex.findAll(line).toList()
   if(matches.isEmpty()){skipped++;continue}
   for(m in matches){
    val type=m.groupValues[1]
    val position=m.groupValues[2].toDoubleOrNull()
    val lane=position?.toInt()?.plus(4)
    if((type=="O"||type=="S")&&position!=null&&position==position.toInt().toDouble()&&lane!=null&&lane in 0..8){
     taps+=Tap(timeMs,lane)
    }else skipped++
   }
  }
  return Chart(difficulty,taps.distinct().sortedBy{it.timeMs},skipped)
 }
}
