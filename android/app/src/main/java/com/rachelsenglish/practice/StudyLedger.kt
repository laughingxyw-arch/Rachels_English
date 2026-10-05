package com.rachelsenglish.practice

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Eligibility is per course and local date, never inferred from opening a page. */
data class StudyDay(val day: String,val course: String,val audioMs: Long,val shadowMs: Long) {
    val effectiveMs: Long get()=if(audioMs>=60_000)audioMs+shadowMs else 0
}
class StudyLedger(initial: List<StudyDay> =emptyList()) {
    private val rows=initial.associateBy {it.day to it.course}.toMutableMap()
    private val dirty=mutableSetOf<Pair<String,String>>()
    fun takeChanges(): List<StudyDay> {val changes=dirty.mapNotNull(rows::get);dirty.clear();return changes}
    fun snapshot()=rows.values.sortedWith(compareBy<StudyDay> {it.day}.thenBy {it.course})
    fun credit(course: String,startMs: Long,endMs: Long,audioMs: Long,shadowMs: Long,zone: ZoneId) {
        if(endMs<=startMs||audioMs<0||shadowMs<0||audioMs+shadowMs==0L)return
        var cursor=startMs;var allocatedAudio=0L;var allocatedShadow=0L
        while(cursor<endMs){
            val date=Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val until=minOf(endMs,date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            val fraction=(until-startMs).toDouble()/(endMs-startMs)
            val nextAudio=if(until==endMs)audioMs else (audioMs*fraction).toLong()
            val nextShadow=if(until==endMs)shadowMs else (shadowMs*fraction).toLong()
            val key=date.toString() to course;val old=rows[key]?:StudyDay(key.first,course,0,0)
            rows[key]=old.copy(audioMs=old.audioMs+nextAudio-allocatedAudio,shadowMs=old.shadowMs+nextShadow-allocatedShadow)
            dirty.add(key)
            allocatedAudio=nextAudio;allocatedShadow=nextShadow;cursor=until
        }
    }
}
/** Fixed bins across months: 0, <5, <15, <30, and 30+ effective minutes. */
fun studyHeatLevel(milliseconds: Long)=when {
    milliseconds<=0->0
    milliseconds<300_000->1
    milliseconds<900_000->2
    milliseconds<1_800_000->3
    else->4
}
fun studyDates(today: LocalDate): List<LocalDate> {
    val first=today.minusMonths(2).withDayOfMonth(1)
    return generateSequence(first){it.plusDays(1)}.takeWhile {it<=today}.toList()
}

/** Only measured progress through audible audio qualifies; seeking never credits time. */
class StudyPlaybackMeter {
    private var position: Long?=null
    fun reset(positionMs: Long?=null){position=positionMs}
    fun advance(positionMs: Long,wallMs: Long,leadMs: Long,endMs: Long): Long {
        val old=position;position=positionMs
        if(old==null||wallMs<=0||positionMs<old)return 0
        val progress=(positionMs.coerceIn(leadMs,endMs)-old.coerceIn(leadMs,endMs)).coerceAtLeast(0)
        // Seek-like jumps are discarded rather than filling the skipped time.
        return if(progress>wallMs+150)0 else minOf(progress,wallMs)
    }
}
