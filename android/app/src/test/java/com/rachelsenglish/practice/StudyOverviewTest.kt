package com.rachelsenglish.practice

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class StudyOverviewTest {
 @Test fun mondayWeekCrossesYearAndCountsDistinctEligibleDays(){
  val rows=listOf(
   StudyDay("2025-12-28","a",60000,120000),
   StudyDay("2025-12-29","a",60000,60000),
   StudyDay("2025-12-29","b",120000,60000),
   StudyDay("2026-01-01","a",60000,0),
   StudyDay("2026-01-01","short",59999,600000),
   StudyDay("2026-01-02","future",60000,0))
  val result=studyOverview(rows,LocalDate.of(2026,1,1))
  assertEquals(StudyTotals(240000,120000,2),result.week)
  assertEquals(StudyTotals(300000,240000,3),result.lifetime)
  assertEquals(StudyTotals(180000,120000,1),result.byDay[LocalDate.of(2025,12,29)])
  assertFalse(result.byDay.containsKey(LocalDate.of(2026,1,2)))
  // Monday starts a new week without dropping lifetime history.
  val next=studyOverview(rows.dropLast(1),LocalDate.of(2026,1,5))
  assertEquals(StudyTotals(),next.week);assertEquals(result.lifetime,next.lifetime)
 }
 @Test fun secondsAreAggregatedBeforeRoundingAndEmptyRecordsStayEmpty(){
  val rows=listOf(StudyDay("2026-11-17","a",89999,1),StudyDay("2026-11-17","b",60001,30000))
  val result=studyOverview(rows,LocalDate.of(2026,11,17))
  assertEquals(180001L,result.week.effectiveMs)
  assertEquals("3 分钟",studyDuration(result.week.effectiveMs));assertEquals(1,result.week.days)
  val empty=studyOverview(emptyList(),LocalDate.of(2026,11,17))
  assertEquals(StudyTotals(),empty.lifetime);assertTrue(empty.byDay.isEmpty())
 }
 @Test fun durationsRemainReadableWithoutTurningShortFollowTimeIntoZero(){
  assertEquals("0 分钟",studyDuration(0));assertEquals("不足 1 分钟",studyDuration(59999))
  assertEquals("1 分钟",studyDuration(60000));assertEquals("59 分钟",studyDuration(3599999))
  assertEquals("1 小时",studyDuration(3600000));assertEquals("12 小时 35 分钟",studyDuration(45300000))
 }
}
