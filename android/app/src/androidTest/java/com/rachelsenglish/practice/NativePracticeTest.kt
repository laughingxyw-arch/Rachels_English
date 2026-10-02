package com.rachelsenglish.practice
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class NativePracticeTest {
 private fun screenshot(name: String){
  rule.waitForIdle()
  val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val bitmap=instrumentation.uiAutomation.takeScreenshot()
  if(android.os.Build.VERSION.SDK_INT>=29){
   val values=android.content.ContentValues().apply {
    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"$name.png")
    put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/RachelsPractice")
   }
   val uri=context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
   context.contentResolver.openOutputStream(uri)!!.use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
  bitmap.recycle()
 }

 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun bundledLessonPlaysAndSettingsRemainNative(){
  screenshot("home")
  rule.onNodeWithTag("course-4dXbgvm4_7g").performClick()
  rule.waitUntil(10000){rule.onAllNodesWithTag("lesson").fetchSemanticsNodes().isNotEmpty()}
  // Audio uses real time; freeze Compose's test clock to avoid waiting until
  // all continuously updated playback frames settle (which means clip end).
  rule.mainClock.autoAdvance=false
  rule.onNodeWithTag("sentence-0").performClick()
  rule.mainClock.advanceTimeBy(32)
  val model=ViewModelProvider(rule.activity)[PracticeModel::class.java]
  rule.waitUntil(10000){model.playback.running&&model.playback.progress>.05f&&model.playback.progress<.8f}
  rule.mainClock.advanceTimeBy(32)
  rule.onNodeWithContentDescription("暂停").performClick()
  rule.mainClock.advanceTimeBy(32)
  rule.mainClock.autoAdvance=true
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
  rule.runOnUiThread {
   val back=rule.activity.onBackPressedDispatcher
   back.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f,200f,0f,androidx.activity.BackEventCompat.EDGE_LEFT))
   back.dispatchOnBackProgressed(androidx.activity.BackEventCompat(60f,200f,.4f,androidx.activity.BackEventCompat.EDGE_LEFT))
   back.dispatchOnBackCancelled()
  }
  rule.waitForIdle()
  rule.runOnIdle {assertNotNull(model.opened)}
  rule.onNodeWithContentDescription("返回课程").performClick()
  rule.waitUntil(5000){rule.onAllNodesWithTag("library").fetchSemanticsNodes().isNotEmpty()}
 }
}
