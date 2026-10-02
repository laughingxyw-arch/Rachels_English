package com.rachelsenglish.practice
import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

 @org.junit.Before fun resetSession(){rule.runOnUiThread {(rule.activity.application as PracticeApplication).model.apply {back();consumeMessage()}}}
 @org.junit.After fun closeSession(){rule.runOnUiThread {(rule.activity.application as PracticeApplication).model.back()}}
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
  val model=(rule.activity.application as PracticeApplication).model
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
  rule.onNodeWithTag("mode-toggle").performClick()
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
  val model=(rule.activity.application as PracticeApplication).model
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
 @Test fun towerTitleKeepsItsLayoutAndDurationFollowsTheCover(){
  val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
  fun scale(value: String){automation.executeShellCommand("settings put global animator_duration_scale $value").use {fd->java.io.FileInputStream(fd.fileDescriptor).use {it.readBytes()}}}
  fun frame(){rule.mainClock.advanceTimeByFrame();rule.waitForIdle();Thread.sleep(15)}
  fun titleLines()=rule.onAllNodesWithTag("course-title-epfQlb_Tgco",useUnmergedTree=true).fetchSemanticsNodes().map {it.config[CourseTitleLineCountKey]}
  fun badgeValues()=rule.onAllNodesWithTag("cover-duration-epfQlb_Tgco",useUnmergedTree=true).fetchSemanticsNodes().map {it.config[CoverBadgeOpacityKey]}
  try {
   rule.activity.getSharedPreferences("practice-native",0).edit().putInt("position.epfQlb_Tgco",0).commit()
   scale("1");rule.activityRule.scenario.recreate()
   rule.waitUntil(10000){rule.onAllNodesWithTag("cover-ready-epfQlb_Tgco",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
   val model=(rule.activity.application as PracticeApplication).model
   assertTrue(titleLines().all {it==1})
   assertTrue(badgeValues().all {it==1f})
   rule.mainClock.autoAdvance=false
   rule.onNodeWithTag("course-epfQlb_Tgco").performClick()
   rule.waitUntil(10000){model.opened?.course?.id=="epfQlb_Tgco"}
   var enteringIntermediate=false
   repeat(60){
    frame()
    assertTrue("Tower Bridge must not reflow or reveal one word first",titleLines().all {it==1})
    if(badgeValues().any {it>.05f&&it<.95f})enteringIntermediate=true
   }
   assertTrue("Duration must fade with the entering cover",enteringIntermediate)
   assertTrue(badgeValues().all {it==0f})
   rule.runOnUiThread {rule.activity.onBackPressedDispatcher.onBackPressed()}
   var returningIntermediate=false
   repeat(60){
    frame()
    assertTrue("Returning title must retain its line layout",titleLines().all {it==1})
    if(badgeValues().any {it>.05f&&it<.95f})returningIntermediate=true
   }
   assertTrue("Duration must emerge before the return handoff",returningIntermediate)
   assertTrue(badgeValues().all {it==1f})
   screenshot("tower-return-settled")
  } finally {rule.mainClock.autoAdvance=true;scale("0")}
 }
 @Test fun transportShowsTaskProgressAndHidesSingleSentenceAndLoopProgress(){
  val model=(rule.activity.application as PracticeApplication).model
  rule.runOnUiThread {model.updateLoop(false);model.updateShadow(false)}
  rule.onNodeWithTag("course-epfQlb_Tgco").performClick()
  rule.waitUntil(10000){model.opened!=null}
  rule.mainClock.autoAdvance=false
  rule.runOnUiThread {model.start(0)}
  rule.waitUntil(10000){model.playback.progress>.1f&&model.playback.progress<.8f}
  assertNotNull(model.playback.taskProgress)
  assertTrue(model.playback.taskProgress!!<model.playback.progress)
  rule.waitUntil(10000){model.playback.selected==1}
  assertTrue("Task progress must survive the audio clip boundary",model.playback.taskProgress!!>.02f)
  rule.runOnUiThread {model.pause()}
  rule.mainClock.advanceTimeBy(1000)
  val surface=rule.onNodeWithTag("player-surface",useUnmergedTree=true)
  surface.assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))
  rule.runOnUiThread {model.start(0,false);model.pause()}
  rule.mainClock.advanceTimeBy(1000)
  assertNull(model.playback.taskProgress)
  surface.assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))
  rule.runOnUiThread {model.start(0);model.updateLoop(true);model.pause()}
  rule.mainClock.advanceTimeBy(1000)
  assertNull(model.playback.taskProgress)
  surface.assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))
  rule.runOnUiThread {model.updateLoop(false);model.back()}
  rule.mainClock.advanceTimeBy(1000);rule.mainClock.autoAdvance=true
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
   val model=(rule.activity.application as PracticeApplication).model
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
 @Test fun releasedBackGestureLandsWithoutATerminalHeightJump(){
  val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
  fun animationScale(value: String){automation.executeShellCommand("settings put global animator_duration_scale $value").use {fd->java.io.FileInputStream(fd.fileDescriptor).use {it.readBytes()}}}
  fun frame(){rule.mainClock.advanceTimeByFrame();rule.waitForIdle();Thread.sleep(10)}
  try {
   animationScale("1");rule.activityRule.scenario.recreate()
   rule.waitUntil(10000){rule.onAllNodesWithTag("cover-ready-4dXbgvm4_7g",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
   val target=rule.onNodeWithTag("course-4dXbgvm4_7g",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
   rule.onNodeWithTag("course-4dXbgvm4_7g").performClick()
   rule.waitUntil(10000){rule.onAllNodesWithTag("lesson").fetchSemanticsNodes().isNotEmpty()}
   rule.waitForIdle();rule.mainClock.autoAdvance=false
   val model=(rule.activity.application as PracticeApplication).model
   rule.runOnUiThread {
    val back=rule.activity.onBackPressedDispatcher
    back.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f,200f,0f,androidx.activity.BackEventCompat.EDGE_LEFT))
    back.dispatchOnBackProgressed(androidx.activity.BackEventCompat(160f,200f,.94f,androidx.activity.BackEventCompat.EDGE_LEFT))
   }
   repeat(12){frame()}
   var lastHeight=rule.onNodeWithTag("lesson").fetchSemanticsNode().boundsInRoot.height
   rule.runOnUiThread {rule.activity.onBackPressedDispatcher.onBackPressed()}
   var landingHeight: Float?=null
   val samples=mutableListOf(lastHeight)
   for(i in 0..120){
    frame()
    // AnimatedContent may retain an invisible outgoing layout after geometry finishes.
    // Choose the actual scene, not the audio model or that old hidden layout's size.
    if(!rule.onNodeWithTag("reading-space",useUnmergedTree=true).fetchSemanticsNode().config[ReadingSceneActiveKey]){
     landingHeight=rule.onNodeWithTag("course-4dXbgvm4_7g",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.height
     break
    }
    lastHeight=rule.onNodeWithTag("lesson").fetchSemanticsNode().boundsInRoot.height
    samples.add(lastHeight)
   }
   assertNotNull("A released back gesture must finish",landingHeight)
   val landed=landingHeight!!
   screenshot("course-gesture-landed")
   assertEquals("The final animation frame must land on the same height as the static card; tail=$samples",lastHeight,landed,1f)
   assertEquals("The resting card must retain its original height",target.height,landed,1f)
   repeat(12){frame()}
   assertNull("The completed visual return must close the old lesson",model.opened)
   assertEquals("No layout correction may resize the card after handoff",landed,rule.onNodeWithTag("course-4dXbgvm4_7g",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.height,1f)
  } finally {rule.mainClock.autoAdvance=true;animationScale("0")}
 }
 @Test fun homeAcceptsRealTouchAtTheFirstReturnHandoffFrame(){
  val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
  fun scale(value: String){automation.executeShellCommand("settings put global animator_duration_scale $value").use {fd->java.io.FileInputStream(fd.fileDescriptor).use {it.readBytes()}}}
  fun frame(){rule.mainClock.advanceTimeByFrame();rule.waitForIdle();Thread.sleep(10)}
  try {
   rule.activity.getSharedPreferences("practice-native",0).edit().putInt("position.epfQlb_Tgco",0).commit()
   scale("1");rule.activityRule.scenario.recreate()
   rule.waitUntil(10000){rule.onAllNodesWithTag("cover-ready-epfQlb_Tgco",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
   val model=(rule.activity.application as PracticeApplication).model
   for(gesture in listOf(false,true))for(target in listOf("epfQlb_Tgco","4dXbgvm4_7g")){
    rule.mainClock.autoAdvance=true
    val restingTarget=rule.onNodeWithTag("course-$target",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
    rule.onNodeWithTag("course-epfQlb_Tgco").performClick()
    rule.waitUntil(10000){model.opened?.course?.id=="epfQlb_Tgco"}
    rule.waitForIdle()
    val origin=model.opened!!
    rule.mainClock.autoAdvance=false
    rule.runOnUiThread {
     val back=rule.activity.onBackPressedDispatcher
     if(gesture){
      back.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f,200f,0f,androidx.activity.BackEventCompat.EDGE_LEFT))
      back.dispatchOnBackProgressed(androidx.activity.BackEventCompat(160f,200f,.94f,androidx.activity.BackEventCompat.EDGE_LEFT))
     }else model.back()
    }
    if(gesture){repeat(3){frame()};rule.runOnUiThread {rule.activity.onBackPressedDispatcher.onBackPressed()}}
    var touched=false
    for(i in 0..120){
     frame()
     val course=rule.onAllNodesWithTag("course-$target",useUnmergedTree=true).fetchSemanticsNodes().firstOrNull()?:continue
     val reading=rule.onNodeWithTag("reading-space",useUnmergedTree=true).fetchSemanticsNode().config[ReadingSceneActiveKey]
     val departing=rule.onAllNodesWithTag("lesson",useUnmergedTree=true).fetchSemanticsNodes().firstOrNull()
     if(!reading||departing!=null&&departing.boundsInRoot.height<=restingTarget.height+1f){
      // Inject an actual coordinate tap. Semantics performClick would bypass occlusion.
      val point=restingTarget.center
      val root=rule.onNodeWithTag("reading-space",useUnmergedTree=true)
      val rootBounds=root.fetchSemanticsNode().boundsInRoot
      root.performTouchInput {click(point-rootBounds.topLeft)}
      touched=true;break
     }
    }
    assertTrue("The return must reach its visual handoff",touched)
    rule.waitUntil(3000){model.loadingId==target||model.opened?.course?.id==target&&model.opened!==origin}
    repeat(90){frame()}
    assertEquals("A tap at handoff must reopen the requested course",target,model.opened?.course?.id)
    assertNotSame("The old return must not retain or close the new session",origin,model.opened)
    rule.runOnUiThread {model.back()};repeat(90){frame()}
   }
  } finally {rule.mainClock.autoAdvance=true;scale("0")}
 }
 @Test fun finishingOldReturnPreservesPendingAndSameCourseReopen(){
  val model=(rule.activity.application as PracticeApplication).model
  rule.onNodeWithTag("course-epfQlb_Tgco").performClick()
  rule.waitUntil(10000){model.opened?.course?.id=="epfQlb_Tgco"}
  val origin=model.opened!!
  rule.runOnUiThread {
   model.open(origin.course)
   model.finishBack(origin)
   assertEquals("Old return must preserve the pending request",origin.course.id,model.loadingId)
  }
  rule.waitUntil(10000){model.opened!=null&&model.opened!==origin}
  val reopened=model.opened!!
  rule.runOnIdle {
   model.finishBack(origin)
   assertSame("Equal course data must not make the new session count as the old one",reopened,model.opened)
   model.open(reopened.course)
  }
  rule.waitUntil(10000){model.opened!=null&&model.opened!==reopened}
  val newest=model.opened!!
  rule.runOnIdle {
   assertEquals("The regression must cover structurally equal course sessions",reopened,newest)
   model.finishBack(reopened)
   assertSame("Already-completed reopen must also survive old return cleanup",newest,model.opened)
   model.back()
  }
 }
 @Test fun restoredSentenceIsPresentFromFirstLayoutWithoutAnEntryScroll(){
  val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
  fun scale(value: String){automation.executeShellCommand("settings put global animator_duration_scale $value").use {fd->java.io.FileInputStream(fd.fileDescriptor).use {it.readBytes()}}}
  fun frame(){rule.mainClock.advanceTimeByFrame();rule.waitForIdle();Thread.sleep(10)}
  try {
   rule.activity.getSharedPreferences("practice-native",0).edit().putInt("position.4dXbgvm4_7g",2).commit()
   scale("1");rule.activityRule.scenario.recreate()
   rule.waitUntil(10000){rule.onAllNodesWithTag("cover-ready-4dXbgvm4_7g",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
   rule.mainClock.autoAdvance=false
   rule.onNodeWithTag("course-4dXbgvm4_7g").performClick()
   val model=(rule.activity.application as PracticeApplication).model
   rule.waitUntil(10000){model.opened?.course?.id=="4dXbgvm4_7g"}
   var samples=0
   repeat(100){
    frame()
    val node=rule.onAllNodesWithTag("dialogue-list",useUnmergedTree=true).fetchSemanticsNodes().firstOrNull()
    if(node!=null){
     samples++
     assertEquals("The restored sentence must be first from the initial layout, not after entry",3,node.config[ReadingFirstItemKey])
     assertEquals("Entry must not animate a second scroll",0,node.config[ReadingFirstOffsetKey])
    }
   }
   assertTrue("The regression must sample entry and the settled reading page",samples>60)
   val last=model.opened!!.lesson.groups.lastIndex
   rule.runOnUiThread {model.start(last);model.pause()}
   repeat(100){frame()}
   rule.onNodeWithTag("sentence-$last").assertIsDisplayed()
   val followed=rule.onNodeWithTag("dialogue-list",useUnmergedTree=true).fetchSemanticsNode().config[ReadingFirstItemKey]
   assertTrue("Subsequent playback changes must still follow the new sentence",followed>3)
   rule.runOnUiThread {model.back()};repeat(90){frame()}
  } finally {rule.mainClock.autoAdvance=true;scale("0")}
 }
 @Test fun playerModeRollsDownAndSettingsPressDoesNotPaintAGrayRow(){
  val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
  fun scale(value: String){automation.executeShellCommand("settings put global animator_duration_scale $value").use {fd->java.io.FileInputStream(fd.fileDescriptor).use {it.readBytes()}}}
  fun frame(){rule.mainClock.advanceTimeByFrame();rule.waitForIdle();Thread.sleep(15)}
  try {
   scale("1");rule.activityRule.scenario.recreate()
   rule.onNodeWithTag("course-4dXbgvm4_7g").performClick()
   rule.waitUntil(10000){rule.onAllNodesWithTag("lesson").fetchSemanticsNodes().isNotEmpty()}
   rule.waitForIdle();rule.mainClock.autoAdvance=false
   val model=(rule.activity.application as PracticeApplication).model
   val mode=rule.onNodeWithTag("mode-toggle",useUnmergedTree=true)
   val bounds=mode.fetchSemanticsNode().boundsInRoot
   assertTrue(bounds.height/rule.activity.resources.displayMetrics.density>=47.9f)
   rule.onAllNodesWithTag("mode-original").assertCountEquals(0)
   mode.performClick();repeat(4){frame()}
   val middle=mode.fetchSemanticsNode().config[ModeRollPositionKey]
   assertTrue(model.drill);assertTrue("The roller must pass through intermediate positions",middle>0f&&middle<1f)
   screenshot("player-mode-mid")
   mode.performClick()
   var previous=middle
   repeat(80){frame();val position=mode.fetchSemanticsNode().config[ModeRollPositionKey];assertTrue("Repeated taps must keep rolling down without a reset",position>=previous-.002f);previous=position}
   assertFalse(model.drill);assertEquals(2f,previous,.002f)
   screenshot("player-mode-original")
   rule.onNodeWithContentDescription("练习设置").performClick();repeat(70){frame()}
   val row=rule.onNodeWithTag("setting-循环当前组",useUnmergedTree=true)
   val before=row.captureToImage().toPixelMap()
   row.performTouchInput {down(androidx.compose.ui.geometry.Offset(10f,center.y))};repeat(15){frame()}
   val during=row.captureToImage().toPixelMap()
   for(x in listOf(4,10,20)){
    val y=4
    val a=before[x,y];val b=during[x,y]
    assertEquals("A held row must not paint a ripple background",a.red,b.red,.015f)
    assertEquals(a.green,b.green,.015f);assertEquals(a.blue,b.blue,.015f)
   }
   screenshot("settings-row-held")
   val wasChecked=model.loop
   row.performTouchInput {up()};repeat(30){frame()}
   assertEquals(!wasChecked,model.loop)
   rule.onNodeWithContentDescription("关闭设置").performClick();repeat(70){frame()}
   rule.runOnUiThread {model.back()};repeat(90){frame()}
  } finally {rule.mainClock.autoAdvance=true;scale("0")}
 }
 @Test fun settingsBackgroundTracksOpeningClosingAndDragReversal(){
  val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
  fun animationScale(value: String){automation.executeShellCommand("settings put global animator_duration_scale $value").use {fd->java.io.FileInputStream(fd.fileDescriptor).use {it.readBytes()}}}
  fun frame(){rule.mainClock.advanceTimeByFrame();rule.waitForIdle();Thread.sleep(15)}
  fun width()=rule.onNodeWithTag("reading-space",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.width
  try {
   animationScale("1");rule.activityRule.scenario.recreate()
   rule.onNodeWithTag("course-4dXbgvm4_7g").performClick()
   rule.onNodeWithTag("lesson").assertExists()
   rule.waitForIdle()
   val full=width()
   rule.mainClock.autoAdvance=false
   rule.onNodeWithContentDescription("练习设置").performClick()
   val opening=mutableListOf<Float>()
   repeat(50){frame();opening.add(width());if(it==5)screenshot("settings-opening")}
   val contracted=width()
   assertTrue("The scene must retain the subtle retreat",contracted<full*.995f)
   assertTrue("Opening must contain intermediate scales, not one jump",opening.any {it>contracted+.1f&&it<full-.1f})
   assertTrue("Opening should smoothly retreat",opening.zipWithNext().all {(before,after)->after<=before+.25f})
   val heading=rule.onNodeWithText("练习设置")
   heading.performTouchInput {down(center);moveBy(androidx.compose.ui.geometry.Offset(0f,100f),delayMillis=32)}
   repeat(3){frame()}
   val dragged=width()
   assertTrue("Dragging the sheet down must restore part of the scene",dragged>contracted+.1f)
   heading.performTouchInput {moveBy(androidx.compose.ui.geometry.Offset(0f,-70f),delayMillis=32)}
   repeat(3){frame()}
   assertTrue("Reversing the drag must reverse scene depth",width()<dragged-.1f)
   heading.performTouchInput {up()}
   repeat(50){frame()}
   rule.onNodeWithContentDescription("关闭设置").performClick()
   val closing=mutableListOf<Float>();var restoredWhileVisible=false
   repeat(50){frame();val current=width();closing.add(current)
    if(current>contracted+.1f&&current<full-.1f&&rule.onAllNodesWithTag("settings-sheet",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty())restoredWhileVisible=true
    if(it==5)screenshot("settings-closing")
   }
   assertTrue("The scene must restore while the panel is still closing",restoredWhileVisible)
   assertTrue("Closing must contain intermediate scales",closing.any {it>contracted+.1f&&it<full-.1f})
   assertEquals("A closed panel must restore the full scene",full,width(),.1f)
   rule.onAllNodesWithTag("settings-sheet",useUnmergedTree=true).assertCountEquals(0)
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

 @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
 @Test fun backgroundPlaybackContinuesAndMediaControlsOperateTheSameQueue(){
  val model=(rule.activity.application as PracticeApplication).model
  rule.runOnUiThread {model.open(model.courses.first {it.id=="4dXbgvm4_7g"})}
  rule.waitUntil(10000){model.opened!=null}
  rule.mainClock.autoAdvance=false
  rule.runOnUiThread {model.updateLoop(false);model.updateShadow(false);model.start(0)}
  rule.waitUntil(10000){model.playback.progress>.05f}
  val context=rule.activity.applicationContext
  lateinit var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>
  rule.runOnUiThread {
   future=androidx.media3.session.MediaController.Builder(context,
    androidx.media3.session.SessionToken(context,android.content.ComponentName(context,PracticePlaybackService::class.java))).buildAsync()
  }
  val controller=future.get(10,java.util.concurrent.TimeUnit.SECONDS)
  try {
   rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
   rule.waitUntil(20000){model.playback.selected>0}
   assertTrue(model.playback.running);assertFalse(model.playback.paused)
   val notifications=context.getSystemService(android.app.NotificationManager::class.java).activeNotifications
   assertTrue(notifications.any {it.notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE!=0})
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {controller.pause()}
   rule.waitUntil(5000){model.playback.paused}
   val selected=model.playback.selected
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {controller.seekToNextMediaItem()}
   rule.waitUntil(5000){model.playback.selected==selected+1&&model.playback.paused}
   assertTrue(model.playback.paused)
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {controller.play()}
   rule.waitUntil(10000){!model.playback.paused&&model.playback.progress>.05f}
   rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
   val retained=model.playback.selected
   rule.activityRule.scenario.recreate()
   assertSame(model,(rule.activity.application as PracticeApplication).model)
   assertTrue(model.playback.running);assertEquals(retained,model.playback.selected)
  } finally {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {controller.release();model.back()}
   rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
   rule.mainClock.autoAdvance=true
  }
 }
 @Test fun screenOffDrillContinuesThroughTheSilentRepeatGap(){
  val model=(rule.activity.application as PracticeApplication).model
  rule.runOnUiThread {model.open(model.courses.first {it.id=="4dXbgvm4_7g"})}
  rule.waitUntil(10000){model.opened!=null}
  rule.mainClock.autoAdvance=false
  rule.runOnUiThread {model.updateLoop(false);model.updateShadow(true);model.updateGap(1f);model.updateDrill(true);model.start(0)}
  rule.waitUntil(10000){model.playback.progress>.05f}
  val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
  try {
   automation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
   rule.waitUntil(20000){model.playback.waiting}
   assertTrue(model.playback.running);assertFalse(model.playback.paused)
   rule.waitUntil(20000){model.playback.repeat>=2&&!model.playback.waiting&&model.playback.progress>.05f}
   assertTrue(model.playback.running);assertFalse(model.playback.paused)
  } finally {
   automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
   automation.executeShellCommand("wm dismiss-keyguard").close()
   rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
   rule.runOnUiThread {model.back();model.updateShadow(false);model.updateGap(1.5f)}
   rule.mainClock.autoAdvance=true
  }
 }

 @Test fun systemThemeChangesInPlaceAndKeepsTheListeningSessionAndSheet(){
  val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
  fun night(value: String){automation.executeShellCommand("cmd uimode night $value").use {fd->java.io.FileInputStream(fd.fileDescriptor).use {it.readBytes()}}}
  fun dark()=rule.onNodeWithTag("app-root",useUnmergedTree=true).fetchSemanticsNode().config[DarkThemeKey]
  val model=(rule.activity.application as PracticeApplication).model
  try {
   night("yes");rule.waitUntil(10000){dark()}
   val home=rule.onNodeWithTag("app-root",useUnmergedTree=true).captureToImage().toPixelMap()
   assertTrue(home[3,3].red<.15f)
   val card=rule.onNodeWithTag("course-4dXbgvm4_7g").captureToImage().toPixelMap()
   assertTrue(card[card.width-8,card.height/2].red<.2f)
   screenshot("dark-home")
   rule.onNodeWithTag("course-4dXbgvm4_7g").performClick()
   rule.waitUntil(10000){model.opened!=null}
   rule.mainClock.autoAdvance=false
   rule.runOnUiThread {model.updateLoop(true);model.updateShadow(false);model.updateDrill(false);model.start(0)}
   rule.waitUntil(10000){model.playback.progress>.05f}
   val activity=rule.activity;val lesson=model.opened
   night("no");rule.waitUntil(10000){!dark()}
   assertSame(activity,rule.activity);assertSame(lesson,model.opened)
   assertTrue(model.playback.running);assertFalse(model.playback.paused)
   night("yes");rule.waitUntil(10000){dark()}
   rule.runOnUiThread {model.pause()};rule.mainClock.advanceTimeBy(1000);rule.mainClock.autoAdvance=true
   screenshot("dark-lesson")
   rule.onNodeWithContentDescription("练习设置").performClick()
   rule.onNodeWithTag("settings-sheet").assertExists()
   night("no");rule.waitUntil(10000){!dark()};rule.onNodeWithTag("settings-sheet").assertExists()
   night("yes");rule.waitUntil(10000){dark()};rule.onNodeWithTag("settings-sheet").assertExists()
   assertSame(activity,rule.activity);assertSame(lesson,model.opened);assertTrue(model.playback.paused)
   screenshot("dark-settings")
   rule.runOnIdle {model.updateEnhanceContrast(true)}
   rule.onNodeWithTag("player-surface",useUnmergedTree=true).assert(SemanticsMatcher.expectValue(FrostedMaterialKey,false))
   screenshot("dark-high-contrast")
   rule.onNodeWithContentDescription("关闭设置").performClick()
   rule.runOnIdle {model.updateEnhanceContrast(false);model.updateReduceTransparency(true)}
   rule.onNodeWithTag("player-surface",useUnmergedTree=true).assert(SemanticsMatcher.expectValue(FrostedMaterialKey,false))
   rule.runOnIdle {model.updateReduceTransparency(false)}
  } finally {
   rule.mainClock.autoAdvance=true;night("no")
   rule.runOnUiThread {model.back();model.updateEnhanceContrast(false);model.updateReduceTransparency(false);model.updateLoop(false)}
  }
 }

}
