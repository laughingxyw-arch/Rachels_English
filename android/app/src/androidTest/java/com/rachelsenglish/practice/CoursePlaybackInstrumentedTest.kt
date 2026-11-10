package com.rachelsenglish.practice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred

class CoursePlaybackInstrumentedTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val model get()=(rule.activity.application as PracticeApplication).model
 private fun openTower(){
  rule.waitUntil(20000){!model.syncing}
  rule.runOnUiThread {model.back();model.updateLoopMode(PracticeLoop.OFF);model.updateShadow(false);model.open(model.courses.first {it.id=="epfQlb_Tgco"})}
  rule.waitUntil(15000){model.opened?.course?.id=="epfQlb_Tgco"}
 }
 private fun awaitCourse(id: String){
  try {rule.waitUntil(45000){model.opened?.course?.id==id&&!model.courseChanging}}
  catch(e: androidx.compose.ui.test.ComposeTimeoutException){
   throw AssertionError("Expected course=$id current=${model.opened?.course?.id} changing=${model.courseChanging} loop=${model.loopMode} drill=${model.drill} playback=${model.playback}",e)
  }
 }
 private fun reset(){rule.runOnUiThread {model.back();model.courseOpener=model.repository::open;model.updateLoopMode(PracticeLoop.OFF);model.updateRepeatCount(3);model.updateShadow(false)};rule.mainClock.autoAdvance=true}
 @Test fun allCoursesContinuesInBackgroundAndPausedCourseSwitchKeepsMode(){
  try {
   openTower();rule.mainClock.autoAdvance=false
   val entries=model.courses.toList();val at=entries.indexOfFirst {it.id=="epfQlb_Tgco"};val next=entries[(at+1)%entries.size]
   val localFixture=model.opened!!
   rule.runOnUiThread {model.courseOpener={course->
    // Playlist traversal uses local real audio; remote-package parsing and AAC
    // decoding are verified separately, without depending on CI download speed.
    if(course.id in setOf("epfQlb_Tgco","4dXbgvm4_7g"))model.repository.open(course) else localFixture.copy(course=course)
   }}
   rule.runOnUiThread {model.updateLoopMode(PracticeLoop.ALL_COURSES);model.start(0)}
   rule.waitUntil(10000){model.playback.progress>0f}
   rule.runOnUiThread {model.leaveReading();assertNull(model.readingLesson);assertTrue(model.playback.running)}
   rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
   rule.runOnUiThread {
    // Keep the full source available while ActivityScenario waits for onStop.
    // Starting the short last sentence could finish and cross courses before
    // the test's seek, accidentally seeking the *next* course to its end.
    assertEquals("Seek must still target the original course","epfQlb_Tgco",model.opened?.course?.id)
    model.seekMedia((model.mediaDurationMs-100).coerceAtLeast(0))
   }
   awaitCourse(next.id)
   rule.runOnUiThread {assertNull(model.readingLesson);assertTrue(model.playback.running);assertFalse(model.playback.paused);model.pause()}
   rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
   rule.mainClock.advanceTimeBy(1200)
   rule.onNodeWithTag("library").assertExists()
   rule.onAllNodesWithTag("lesson").assertCountEquals(0)
   rule.runOnUiThread {model.updateDrill(true);model.updateRepeatCount(2);model.changeCourse(-1)}
   awaitCourse("epfQlb_Tgco")
   rule.runOnUiThread {assertTrue(model.drill);assertEquals(2,model.repeatCount);assertEquals(0,model.playback.selected);assertTrue(model.playback.paused);assertNull(model.readingLesson)}
   // The last lesson wraps to the captured first course, including a paused Drill task.
   rule.runOnUiThread {model.changeCourse(1)}
   awaitCourse(next.id)
   if(at+1==entries.lastIndex){
    rule.runOnUiThread {model.changeCourse(1)}
    awaitCourse(entries.first().id)
    rule.runOnUiThread {assertEquals(0,model.playback.selected);assertTrue(model.playback.paused);assertTrue(model.drill)}
   }
  }finally{rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);reset()}
 }
 @Test fun pendingSwitchHonorsPauseAndAllUnavailableCoursesStopOnce(){
  try {
   openTower();rule.mainClock.autoAdvance=false
   val entries=model.courses.toList();val at=entries.indexOfFirst {it.id=="epfQlb_Tgco"};val next=entries[(at+1)%entries.size]
   val gate=CompletableDeferred<OpenLesson>()
   val fixture=model.opened!!.copy(course=next)
   rule.runOnUiThread {
    model.courseOpener={gate.await()};model.updateLoopMode(PracticeLoop.ALL_COURSES);model.start(0);model.changeCourse(1);model.toggle()
   }
   Thread.sleep(250)
   rule.runOnUiThread {assertFalse("Pending course changes must not leave the old audio playing",model.isAudioPlaying)}
   gate.complete(fixture)
   rule.waitUntil(15000){model.opened?.course?.id==next.id&&!model.courseChanging}
   rule.runOnUiThread {assertTrue(model.playback.paused);assertEquals(0,model.playback.selected);model.stop();model.updateLoopMode(PracticeLoop.OFF)}
   var attempts=0
   rule.runOnUiThread {
    model.courseOpener={attempts++;throw java.io.IOException("Unavailable course fixture")}
    model.updateLoopMode(PracticeLoop.ALL_COURSES);model.start(0);model.seekMedia(model.mediaDurationMs)
   }
   rule.waitUntil(10000){!model.courseChanging&&!model.playback.running}
   val count=attempts
   assertTrue("One speculative request plus one bounded traversal",count<=entries.size+1)
   Thread.sleep(500)
   assertEquals("Unavailable courses must not spin or retry forever",count,attempts)
  }finally{reset()}
 }
}
