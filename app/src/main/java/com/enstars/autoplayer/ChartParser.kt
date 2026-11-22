package com.enstars.autoplayer

/** esmchartplayer format reader. Returns tap/skill heads only; other gestures are not simulated. */
object ChartParser {
 data class Tap(val timeMs:Long,val lane:Int)
 data class Chart(val difficulty:String,val taps:List<Tap>,val skipped:Int)
 private data class Section(val offset:Double,val beats:Int,val bpm:Double)
 private val sectionRegex=Regex("""^(\d+)#([\d.]+):(\d+):([\d.]+)$""")
 private val noteRegex=Regex("""@([OSLRUD])(-?\d+(?:\.5)?)(?:G\d+)?""")
 private val clockRegex=Regex("""^(?:(\d+):|:)?(?:(\d+)_)?(?:_(\d+))?(?:\+(\d+)/(\d+)|\.(\d+))?$""")
 fun parse(input:String):Chart {
  val sections=mutableMapOf<Int,Section>()
  val taps=mutableListOf<Tap>()
  var difficulty=""
  var skipped=0
  var section=1;var bar=0;var beat=0;var subBeat=0.0
  fun seconds(prefix:String):Double? {
   if(prefix.isEmpty())return sections[section]?.let{it.offset+(bar*it.beats+beat+subBeat)*60.0/it.bpm}
   if(prefix.matches(Regex("""\d+\.\d+""")))return prefix.toDoubleOrNull()
   val match=clockRegex.matchEntire(prefix)?:return null
   val (sec,b,bt,num,den,dec)=match.destructured
   if(sec.isNotEmpty())section=sec.toInt()
   if(b.isNotEmpty())bar=b.toInt()
   if(bt.isNotEmpty())beat=bt.toInt()
   if(num.isNotEmpty()&&den.toIntOrNull()!=null&&den.toInt()!=0)subBeat=num.toDouble()/den.toDouble()
   else if(dec.isNotEmpty())subBeat=("0."+dec).toDouble()
   else if(b.isNotEmpty()||bt.isNotEmpty())subBeat=0.0
   val s=sections[section]?:return null
   if(s.bpm<=0.0||s.beats<=0)return null
   return s.offset+(bar*s.beats+beat+subBeat)*60.0/s.bpm
  }
  for(raw in input.lineSequence()){
   val line=raw.substringBefore("//").trim()
   if(line.isEmpty())continue
   if(difficulty.isEmpty()&&line.contains("_LEVEL_")){difficulty=line;continue}
   val sec=sectionRegex.matchEntire(line)
   if(sec!=null){
    val (no,offset,beats,bpm)=sec.destructured
    sections[no.toInt()]=Section(offset.toDouble(),beats.toInt(),bpm.toDouble())
    continue
   }
   if(line.startsWith("v=")||line.startsWith("stop=")||line.startsWith(">")||
      (line.startsWith("G")&&line.contains(">")))continue
   val at=line.indexOf('@')
   val marker=line.indexOfAny(charArrayOf('?','!','~'))
   if(at<0&&marker<0)continue
   val split=if(at>=0)at else marker
   val head=line.substring(0,split).substringBefore('=').trim()
   val firstTime=seconds(head)
   if(firstTime==null){skipped++;continue}
   // A line can contain several simultaneous heads and hold tails.
   // Only parse notes before the first '=' (hold tail); do not treat tails as taps.
   val heads=line.substring(split).substringBefore('=')
   val matches=noteRegex.findAll(heads).toList()
   if(matches.isEmpty()){skipped++;continue}
   for(m in matches){
    val pos=m.groupValues[2].toDoubleOrNull()
    val lane=pos?.toInt()?.plus(4)
    if((m.groupValues[1]=="O"||m.groupValues[1]=="S")&&
       pos!=null&&pos==pos.toInt().toDouble()&&lane!=null&&lane in 0..8){
     taps+=Tap((firstTime*1000).toLong(),lane)
    }else skipped++
   }
   if(line.contains('='))skipped++
  }
  return Chart(difficulty,taps.distinct().sortedBy{it.timeMs},skipped)
 }
}
