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
  // Allow the platform window surface to present the settled Compose frame.
  Thread.sleep(200)
  val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  val bitmap=instrumentation.uiAutomation.takeScreenshot()
  saveImage(name,bitmap)
 }
 private fun saveImage(name: String,bitmap: android.graphics.Bitmap){
  val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
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
  val icon=rule.activity.packageManager.getApplicationIcon(rule.activity.applicationInfo)
  val bitmap=android.graphics.Bitmap.createBitmap(384,384,android.graphics.Bitmap.Config.ARGB_8888)
  icon.setBounds(0,0,384,384);icon.draw(android.graphics.Canvas(bitmap));saveImage("launcher-icon",bitmap)
  rule.waitUntil(10000){rule.onAllNodesWithTag("cover-ready-4dXbgvm4_7g",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
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
  rule.onNodeWithContentDescription("上一句").assertIsNotEnabled()
  val target=rule.onNodeWithContentDescription("播放").fetchSemanticsNode().boundsInRoot
  val density=rule.activity.resources.displayMetrics.density
  assertTrue(target.width/density>=47.9f);assertTrue(target.height/density>=47.9f)
  screenshot("lesson")
  rule.onNodeWithTag("mode-drill").performClick()
  rule.runOnIdle {assertTrue(model.drill);assertTrue(model.playback.paused)}
  rule.onNodeWithContentDescription("练习设置").performClick()
  rule.onNodeWithText("中文翻译").assertExists()
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
 @Test fun originalLoopsCurrentSentenceThenContinuesToTheEnd(){
  val model=ViewModelProvider(rule.activity)[PracticeModel::class.java]
  rule.runOnIdle {model.updateShadow(false);model.updateLoop(true)}
  rule.onNodeWithTag("course-epfQlb_Tgco").performClick()
  rule.waitUntil(10000){rule.onAllNodesWithTag("lesson").fetchSemanticsNodes().isNotEmpty()}
  rule.mainClock.autoAdvance=false
  rule.runOnUiThread {model.start(0)}
  // The first original clip is short: observe the wait and restart of group 0.
  rule.waitUntil(10000){model.playback.waiting}
  rule.waitUntil(5000){model.playback.running&&!model.playback.waiting&&model.playback.progress>.05f}
  assertEquals(0,model.playback.selected)
  rule.runOnUiThread {model.updateLoop(false)}
  rule.waitUntil(10000){model.playback.selected==1}
  // Starting at the penultimate sentence must reach the last and finish.
  val last=model.opened!!.lesson.groups.lastIndex
  rule.runOnUiThread {model.start(last-1)}
  rule.waitUntil(15000){model.playback.selected==last}
  rule.waitUntil(15000){!model.playback.running&&model.playback.progress==1f}
  rule.mainClock.advanceTimeBy(1000);rule.mainClock.autoAdvance=true
  screenshot("lesson-last")
  rule.runOnUiThread {rule.activity.onBackPressedDispatcher.onBackPressed()}
  rule.waitUntil(5000){rule.onAllNodesWithTag("library").fetchSemanticsNodes().isNotEmpty()}
 }
}
