package com.rachelsenglish.practice
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class NativePracticeTest {
 private fun screenshot(name: String){
  val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  val folder=instrumentation.targetContext.getExternalFilesDir("screenshots")!!
  folder.mkdirs()
  instrumentation.uiAutomation.takeScreenshot().let {bitmap ->java.io.File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
 }

 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun bundledLessonPlaysAndSettingsRemainNative(){
  screenshot("home")
  rule.onNodeWithTag("course-epfQlb_Tgco").performClick()
  rule.waitUntil(10000){rule.onAllNodesWithTag("lesson").fetchSemanticsNodes().isNotEmpty()}
  rule.onNodeWithTag("sentence-0").performClick()
  val model=ViewModelProvider(rule.activity)[PracticeModel::class.java]
  rule.waitUntil(10000){model.playback.progress>.05f}
  rule.onNodeWithContentDescription("暂停").performClick()
  rule.runOnIdle {assertTrue(model.playback.paused);assertFalse(model.translation)}
  screenshot("lesson")
  rule.onNodeWithTag("mode-drill").performClick()
  rule.runOnIdle {assertTrue(model.drill)}
  rule.onNodeWithContentDescription("练习设置").performClick()
  screenshot("settings")
  rule.onNodeWithText("中文翻译").performClick()
  rule.runOnIdle {assertTrue(model.translation);model.updateTranslation(false)}
  rule.onNodeWithContentDescription("关闭设置").performClick()
  rule.waitForIdle()
  rule.onNodeWithContentDescription("返回课程").performClick()
  rule.waitUntil(5000){rule.onAllNodesWithTag("library").fetchSemanticsNodes().isNotEmpty()}
 }
}
