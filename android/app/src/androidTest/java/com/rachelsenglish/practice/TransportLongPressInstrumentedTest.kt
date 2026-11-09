package com.rachelsenglish.practice

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TransportLongPressInstrumentedTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun shortPressLongPressAndCancellationAreIndependent(){
  var sentence=0;var course=0
  val clickEnabled=mutableStateOf(true)
  rule.activity.setContent {GlyphButton("下一句","next",{sentence++},enabled=clickEnabled.value,longClick={course++},longLabel="下一课")}
  val button=rule.onNodeWithContentDescription("下一句")
  button.performTouchInput {click()};assertEquals(1,sentence)
  button.performTouchInput {down(center);advanceEventTime(430);up()}
  assertEquals(2,sentence);assertEquals(0,course)
  button.performTouchInput {down(center);advanceEventTime(600);up()}
  assertEquals(2,sentence);assertEquals(1,course)
  button.performTouchInput {down(center);advanceEventTime(1800);up()}
  assertEquals("Holding cannot repeatedly skip courses",2,course);assertEquals(2,sentence)
  button.performTouchInput {down(center);advanceEventTime(100);moveTo(Offset(width+100f,height+100f));advanceEventTime(600);up()}
  assertEquals("Leaving before the threshold cancels",2,course);assertEquals(2,sentence)
  rule.runOnUiThread {clickEnabled.value=false}
  button.performTouchInput {click()};assertEquals(2,sentence)
  button.performTouchInput {down(center);advanceEventTime(600);up()};assertEquals(3,course)
  rule.runOnIdle {val actions=button.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.CustomActions];assertTrue(actions.first {it.label=="下一课"}.action())}
  assertEquals(4,course)
 }
}
