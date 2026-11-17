package com.rachelsenglish.practice

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StudyOverviewInstrumentedTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun overviewDetailsAndHistoricalPagingStayConsistentAcrossThemes(){
  val today=LocalDate.of(2026,11,17)
  val course=Course("sample","示例课程","访谈对白","","1","","",120,2)
  val rows=listOf(StudyDay(today.toString(),course.id,720000,360000),StudyDay("2026-10-03","removed",3600000,900000))
  val dark=mutableStateOf(false)
  var opened: Course?=null
  rule.runOnUiThread {rule.activity.setContent {
   val palette=practicePalette(dark.value,true)
   CompositionLocalProvider(LocalPracticePalette provides palette,LocalContentColor provides palette.ink,LocalReduced provides true) {
    MaterialTheme(colorScheme=practiceColorScheme(palette)) {
     Column(Modifier.fillMaxSize().background(palette.background).verticalScroll(rememberScrollState()).padding(28.dp)) {
      StudyCalendar(rows,listOf(course),{opened=it},today)
     }
    }
   }
  }}
  rule.onNodeWithTag("study-week").assertTextContains("18 分钟").assertTextContains("练习 1 天")
  rule.onNodeWithTag("study-lifetime").assertTextContains("1 小时 33 分钟").assertTextContains("练习 2 天")
  rule.onNodeWithTag("study-breakdown").assertDoesNotExist()
  rule.onNodeWithTag("study-day-summary").performScrollTo().performClick()
  rule.onNodeWithTag("study-breakdown").performScrollTo().assertIsDisplayed()
  rule.onNodeWithText("听音 12 分钟 · 跟读 6 分钟").assertIsDisplayed()
  rule.onNodeWithTag("study-course-duration-sample").performScrollTo().assertTextEquals("18 分钟")
  screenshot("study-overview-details")
  rule.runOnIdle {dark.value=true};rule.waitForIdle()
  rule.onNodeWithText("听音 12 分钟 · 跟读 6 分钟").assertExists()
  screenshot("study-overview-dark-details")
  rule.onNodeWithTag("study-day-summary").performScrollTo().performClick()
  rule.onNodeWithTag("study-breakdown").assertDoesNotExist()
  rule.onNodeWithTag("study-window").performScrollTo().performTouchInput {swipeRight()}
  rule.waitForIdle()
  rule.onNodeWithTag("study-week").assertTextContains("18 分钟")
  rule.onNodeWithTag("study-lifetime").assertTextContains("1 小时 33 分钟")
  rule.onNodeWithTag("study-window").performTouchInput {swipeLeft()};rule.waitForIdle()
  // Returning to the current three-month window selects today again.
  rule.onNodeWithTag("study-days").performScrollTo()
  rule.onNodeWithTag("study-course-duration-sample").assertExists()
  rule.onNodeWithText("示例课程").performScrollTo().performClick()
  rule.runOnIdle {assertEquals(course,opened)}
 }
 private fun screenshot(name: String){
  rule.waitForIdle();Thread.sleep(200)
  val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val bitmap=instrumentation.uiAutomation.takeScreenshot()
  val values=android.content.ContentValues().apply {
   put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"$name.png")
   put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
   put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/RachelsPractice")
  }
  val uri=context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
  context.contentResolver.openOutputStream(uri)!!.use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  bitmap.recycle()
 }
}
