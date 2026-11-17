package com.rachelsenglish.practice

import java.time.LocalDate

/** Aggregate eligible course/day records before rounding; count each calendar day once. */
data class StudyTotals(val audioMs: Long=0,val shadowMs: Long=0,val days: Int=0) {
    val effectiveMs: Long get()=audioMs+shadowMs
}
data class StudyOverview(val byDay: Map<LocalDate,StudyTotals>,val week: StudyTotals,val lifetime: StudyTotals)
fun studyOverview(rows: List<StudyDay>,today: LocalDate): StudyOverview {
    val byDay=rows.filter {it.effectiveMs>0}.groupBy {LocalDate.parse(it.day)}.filterKeys {it<=today}
        .mapValues {(_,records)->StudyTotals(records.sumOf {it.audioMs},records.sumOf {it.shadowMs},1)}
    val monday=today.minusDays((today.dayOfWeek.value-1).toLong())
    fun total(values: Collection<StudyTotals>)=StudyTotals(values.sumOf {it.audioMs},values.sumOf {it.shadowMs},values.sumOf {it.days})
    return StudyOverview(byDay,total(byDay.filterKeys {it>=monday}.values),total(byDay.values))
}
fun studyDuration(milliseconds: Long): String {
    if(milliseconds<=0)return "0 分钟"
    val minutes=milliseconds/60_000
    if(minutes==0L)return "不足 1 分钟"
    val hours=minutes/60;val remainder=minutes%60
    return if(hours==0L)"$minutes 分钟" else if(remainder==0L)"$hours 小时" else "$hours 小时 $remainder 分钟"
}
