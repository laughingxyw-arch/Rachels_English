package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class StudyLedgerTest {
 private val zone=ZoneId.of("Asia/Shanghai")
 private val start=LocalDate.of(2026,10,5).atStartOfDay(zone).toInstant().toEpochMilli()
 @Test fun fiveSecondsAndSeparateShortCoursesNeverQualify(){
  val ledger=StudyLedger();repeat(20){ledger.credit("course$it",start,start+5000,5000,0,zone)}
  assertEquals(0L,ledger.snapshot().sumOf {it.effectiveMs})
  assertEquals(0,studyHeatLevel(ledger.snapshot().sumOf {it.effectiveMs}))
 }
 @Test fun sessionsAccumulatePerCourseAndCreditShadowOnlyAfterAudioThreshold(){
  val ledger=StudyLedger();ledger.credit("one",start,start+30000,30000,20000,zone)
  assertEquals(0L,ledger.snapshot().single().effectiveMs)
  ledger.credit("one",start+30000,start+59999,29999,0,zone)
  assertEquals(0L,ledger.snapshot().single().effectiveMs)
  ledger.credit("one",start+59999,start+60000,1,0,zone)
  assertEquals(80000L,ledger.snapshot().single().effectiveMs)
  val restored=StudyLedger(ledger.snapshot());restored.credit("one",start+60000,start+61000,1000,0,zone)
  assertEquals(81000L,restored.snapshot().single().effectiveMs)
 }
 @Test fun midnightSeparatesEligibilityAndPreservesExactTotals(){
  val ledger=StudyLedger();val midnight=start+86400000
  ledger.credit("one",midnight-30000,midnight+30000,60000,10001,zone)
  assertEquals(60000L,ledger.snapshot().sumOf {it.audioMs})
  assertEquals(10001L,ledger.snapshot().sumOf {it.shadowMs})
  assertTrue(ledger.snapshot().all {it.effectiveMs==0L})
  ledger.credit("one",midnight,midnight+30000,30000,0,zone)
  assertEquals(65001L,ledger.snapshot().last().effectiveMs)
 }
 @Test fun daylightSavingUsesCalendarMidnightInsteadOfFixed24Hours(){
  val dst=ZoneId.of("America/New_York");val midnight=LocalDate.of(2026,3,9).atStartOfDay(dst).toInstant().toEpochMilli()
  val ledger=StudyLedger();ledger.credit("one",midnight-1000,midnight+1000,2000,0,dst)
  assertEquals(listOf("2026-03-08","2026-03-09"),ledger.snapshot().map {it.day})
  assertEquals(listOf(1000L,1000L),ledger.snapshot().map {it.audioMs})
 }
 @Test fun databaseWritesContainOnlyChangedCoursesAndDoNotDropPendingEligibility(){
  val ledger=StudyLedger(listOf(StudyDay("2026-10-04","old",60000,0)))
  ledger.credit("one",start,start+5000,5000,0,zone)
  assertEquals("one",ledger.takeChanges().single().course);assertTrue(ledger.takeChanges().isEmpty())
  assertEquals(2,ledger.snapshot().size)
 }
 @Test fun seekingPaddingAndPausedWallTimeNeverInflateListening(){
  val meter=StudyPlaybackMeter();meter.reset(0)
  assertEquals(420L,meter.advance(500,500,80,2000))
  assertEquals(0L,meter.advance(1900,100,80,2000))
  assertEquals(100L,meter.advance(2100,200,80,2000))
  assertEquals(0L,meter.advance(3000,900,80,2000))
  meter.reset(0);assertEquals(100L,meter.advance(180,10000,80,2000))
  meter.reset();assertEquals(0L,meter.advance(500,500,80,2000))
  meter.reset(900);assertEquals(0L,meter.advance(100,500,80,2000))
 }
 @Test fun heatBinsStayFixedAndCalendarCrossesYearBoundaries(){
  assertEquals(listOf(0,1,1,2,3,4),listOf(0L,60000,299999,300000,900000,1800000).map(::studyHeatLevel))
  val dates=studyDates(LocalDate.of(2026,1,5));assertEquals(LocalDate.of(2025,11,1),dates.first());assertEquals(LocalDate.of(2026,1,5),dates.last());assertEquals(66,dates.size)
 }
}
