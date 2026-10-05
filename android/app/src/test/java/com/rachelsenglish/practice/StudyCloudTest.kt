package com.rachelsenglish.practice
import org.junit.Test
import org.junit.Assert.*
class StudyCloudTest {
 private fun row(audio: Long,shadow: Long=0)=StudyDay("2026-10-05","hLgMIwFeE88",audio,shadow)
 @Test fun retriesAndRestoredLocalCountersNeverDuplicate(){
  val remote=listOf(StudyContribution("a",row(60000)),StudyContribution("b",row(20000)))
  assertEquals(80000L,mergeStudy("a",listOf(row(60000)),remote).single().audioMs)
  assertEquals(85000L,mergeStudy("a",listOf(row(65000)),remote).single().audioMs)
 }
 @Test fun reinstallAddsOnlyNewPracticeToOldDevices(){
  val remote=listOf(StudyContribution("a",row(60000)))
  assertEquals(65000L,mergeStudy("new",listOf(row(5000)),remote).single().effectiveMs)
 }
 @Test fun eligibilityAppliedAfterDevicesAreCombined(){
  val remote=listOf(StudyContribution("a",row(30000,1000)))
  assertEquals(0L,mergeStudy("b",listOf(row(25000)),remote).single().effectiveMs)
  assertEquals(61000L,mergeStudy("b",listOf(row(30000)),remote).single().effectiveMs)
 }
}
