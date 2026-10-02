package com.rachelsenglish.practice
import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
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
  rule.mainClock.autoAdvance=false
  rule.runOnUiThread {model.toggle()}
  rule.waitUntil(10000){model.playback.progress>.1f&&model.playback.progress<.8f}
  rule.runOnUiThread {model.pause()}
  rule.mainClock.advanceTimeBy(32);rule.mainClock.autoAdvance=true
  val retainedProgress=model.playback.progress
  rule.onNodeWithContentDescription("练习设置").performClick()
  rule.onNodeWithText("中文翻译").assertExists()
  rule.onNodeWithTag("repeat-setting").performClick()
  rule.onNodeWithTag("repeat-2").performClick()
  rule.runOnIdle {assertEquals(2,model.repeatCount);assertEquals(2,model.playback.total);assertTrue(model.playback.paused);assertEquals(retainedProgress,model.playback.progress,.0001f)}
  rule.onNodeWithTag("repeat-setting").performClick()
  rule.onNodeWithTag("repeat-5").performClick()
  rule.runOnIdle {assertEquals(5,model.repeatCount);assertEquals(5,model.playback.total);assertTrue(model.playback.paused);assertEquals(retainedProgress,model.playback.progress,.0001f);model.updateRepeatCount(3)}
  rule.onNodeWithTag("appearance-setting").performScrollTo().performClick()
  rule.onNodeWithText("减少透明效果").performScrollTo().performClick()
  rule.runOnIdle {assertTrue(model.reduceTransparency)}
  rule.onNodeWithText("增强对比度").performScrollTo().performClick()
  rule.runOnIdle {assertTrue(model.enhanceContrast)}
  rule.onNodeWithText("增强对比度").performScrollTo().performClick()
  rule.onNodeWithText("减少透明效果").performScrollTo().performClick()
  rule.runOnIdle {assertFalse(model.reduceTransparency);assertFalse(model.enhanceContrast)}
  rule.onNodeWithTag("appearance-setting").performScrollTo().performClick()
  screenshot("settings")
  rule.onNodeWithText("中文翻译").performClick()
  rule.runOnIdle {assertTrue(model.translation);model.updateTranslation(false)}
  rule.onNodeWithContentDescription("关闭设置").performClick()
  rule.waitForIdle()
  rule.runOnIdle {model.notify("媒体音量已静音")}
  rule.onNodeWithTag("notice").assertExists()
  screenshot("notice")
  rule.runOnIdle {model.consumeMessage()}
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
 @Test fun courseContainerExpandsAndInterruptedBackRestoresIt(){
  val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
  fun animationScale(value: String){automation.executeShellCommand("settings put global animator_duration_scale $value").use {fd->java.io.FileInputStream(fd.fileDescriptor).use {it.readBytes()}}}
  fun frames(ms: Long){repeat((ms/16).toInt()){rule.mainClock.advanceTimeByFrame();rule.waitForIdle();Thread.sleep(20)}}
  try {
   animationScale("1");rule.activityRule.scenario.recreate()
   rule.waitUntil(10000){rule.onAllNodesWithTag("cover-ready-4dXbgvm4_7g",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
   val course=rule.onNodeWithTag("course-4dXbgvm4_7g").fetchSemanticsNode().boundsInRoot
   rule.mainClock.autoAdvance=false
   rule.onNodeWithTag("course-4dXbgvm4_7g").performClick()
   val model=ViewModelProvider(rule.activity)[PracticeModel::class.java]
   rule.waitUntil(10000){model.opened!=null}
   rule.waitForIdle();rule.mainClock.advanceTimeByFrame();rule.waitForIdle()
   frames(160)
   screenshot("course-expand-mid")
   val middle=rule.onNodeWithTag("lesson").fetchSemanticsNode().boundsInRoot
   assertTrue("The course surface must expand beyond the source row",middle.height>course.height)
   frames(1000)
   val expanded=rule.onNodeWithTag("lesson").fetchSemanticsNode().boundsInRoot
   assertTrue("The container must keep growing through the transition",expanded.height>=middle.height)
   rule.runOnUiThread {
    val back=rule.activity.onBackPressedDispatcher
    back.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f,200f,0f,androidx.activity.BackEventCompat.EDGE_LEFT))
    back.dispatchOnBackProgressed(androidx.activity.BackEventCompat(100f,200f,.55f,androidx.activity.BackEventCompat.EDGE_LEFT))
   }
   frames(160)
   screenshot("course-gesture-mid")
   val gestureBounds=rule.onNodeWithTag("lesson").fetchSemanticsNode().boundsInRoot
   assertTrue("The gesture must move the course surface before release",gestureBounds.height<expanded.height)
   val expected=expanded.height+(course.height-expanded.height)*.55f
   assertEquals("Gesture progress must directly control the container geometry",expected,gestureBounds.height,20f)
   rule.runOnUiThread {rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()}
   frames(32)
   rule.runOnUiThread {
    val back=rule.activity.onBackPressedDispatcher
    back.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f,200f,0f,androidx.activity.BackEventCompat.EDGE_LEFT))
    back.dispatchOnBackProgressed(androidx.activity.BackEventCompat(80f,200f,.25f,androidx.activity.BackEventCompat.EDGE_LEFT))
   }
   frames(80)
   screenshot("course-gesture-regrab")
   rule.runOnUiThread {rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()}
   frames(1000)
   assertNotNull(model.opened)
   val restored=rule.onNodeWithTag("lesson").fetchSemanticsNode().boundsInRoot
   assertEquals("Cancelled gestures must restore the full reading surface",expanded.height,restored.height,2f)
   rule.runOnUiThread {rule.activity.onBackPressedDispatcher.onBackPressed()}
   rule.waitForIdle();rule.mainClock.advanceTimeByFrame();rule.waitForIdle();frames(160)
   rule.waitUntil(10000){rule.onAllNodesWithTag("cover-ready-epfQlb_Tgco",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
   screenshot("course-return-mid")
   frames(1000)
   rule.onNodeWithTag("library").assertExists()
  } finally {rule.mainClock.autoAdvance=true;animationScale("0")}
 }
 @Test fun backdropSamplesContentAndContrastRemovesTransparency(){
  val enhanced=androidx.compose.runtime.mutableStateOf(false)
  rule.runOnUiThread {rule.activity.setContent {
   val source=rememberBackdropSource()
   androidx.compose.runtime.CompositionLocalProvider(LocalBackdropSource provides source,LocalMaterialStyle provides MaterialStyle(frosted=!enhanced.value,highContrast=enhanced.value)) {
    Box(Modifier.fillMaxSize()) {
     androidx.compose.foundation.Canvas(Modifier.fillMaxSize().captureBackdrop(source)) {
      drawRect(androidx.compose.ui.graphics.Color(0xffdd624c),size=androidx.compose.ui.geometry.Size(size.width/2,size.height))
      drawRect(androidx.compose.ui.graphics.Color(0xff3869dc),topLeft=androidx.compose.ui.geometry.Offset(size.width/2,0f),size=androidx.compose.ui.geometry.Size(size.width/2,size.height))
     }
     FrostedSurface(Modifier.align(androidx.compose.ui.Alignment.Center).width(280.dp).height(96.dp).testTag("sample-surface")) {
      androidx.compose.material3.Text("Clear controls",modifier=Modifier.align(androidx.compose.ui.Alignment.Center),color=androidx.compose.ui.graphics.Color.Black)
     }
    }
   }
  }}
  rule.waitForIdle();Thread.sleep(200)
  val surface=rule.onNodeWithTag("sample-surface",useUnmergedTree=true)
  assertTrue(surface.fetchSemanticsNode().config[FrostedMaterialKey])
  val pixels=surface.captureToImage().toPixelMap()
  val left=pixels[pixels.width/4,pixels.height*3/4];val right=pixels[pixels.width*3/4,pixels.height*3/4]
  assertTrue("Real background must influence the floating material",left.red-right.red>.025f&&right.blue-left.blue>.025f)
  val nearLeft=pixels[pixels.width/2-6,pixels.height*3/4];val nearRight=pixels[pixels.width/2+6,pixels.height*3/4]
  assertTrue("The background seam must be blurred rather than only tinted",left.red-nearLeft.red>.005f&&nearRight.red-right.red>.005f)
  screenshot("material-sample")
  rule.runOnIdle {enhanced.value=true}
  rule.waitForIdle();Thread.sleep(200)
  assertFalse(surface.fetchSemanticsNode().config[FrostedMaterialKey])
  val solid=surface.captureToImage().toPixelMap()
  val first=solid[solid.width/4,solid.height*3/4];val second=solid[solid.width*3/4,solid.height*3/4]
  assertTrue("High contrast must remove background transparency",kotlin.math.abs(first.red-second.red)<.01f&&kotlin.math.abs(first.blue-second.blue)<.01f)
  screenshot("material-contrast")
 }

}
