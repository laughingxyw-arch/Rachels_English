package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test
class TaskTimelineTest {
 private fun clip(group: Int,duration: Double,repeat: Int=1)=Clip(group,"g$group.wav",duration,0.0,0.0,true,repeat,3)
 @Test fun originalUsesRemainingDurationWithoutAnArtificialGap(){
  val t=TaskTimeline(listOf(clip(2,2.0),clip(3,8.0)),false,false,1.5f)
  assertEquals(10.0,t.totalSeconds,.0001)
  assertTrue(t.spansSentences)
  assertEquals(1f/10.0f,t.progress(0,1.0),.0001f)
  assertEquals(2f/10f,t.progress(0,2.0),.0001f)
  assertEquals(t.progress(0,2.0),t.progress(1,0.0),.0001f)
  assertEquals(1f,t.progress(1,8.0),.0001f)
 }
 @Test fun drillAccumulatesRepeatsAndDoesNotResetAtClipBoundaries(){
  val q=listOf(clip(0,2.0,1),clip(0,2.0,2),clip(0,2.0,3),clip(1,4.0,1))
  val t=TaskTimeline(q,true,false,1.5f)
  assertEquals(12.6,t.totalSeconds,.0001)
  for(i in 0 until q.lastIndex)assertEquals(t.progress(i,q[i].duration,t.waits[i]),t.progress(i+1,0.0),.0001f)
  assertEquals(1f,t.progress(q.lastIndex,4.0),.0001f)
 }
 @Test fun finalShadowWaitBelongsToTheTask(){
  val t=TaskTimeline(listOf(clip(0,2.0),clip(1,2.0)),false,true,2f)
  assertEquals(11.2,t.totalSeconds,.0001)
  assertTrue(t.progress(1,2.0)<1f)
  assertEquals(1f,t.progress(1,2.0,3.6),.0001f)
 }
 @Test fun singleSentenceDrillIsNotAMultiSentenceTask(){
  val t=TaskTimeline(listOf(clip(0,2.0,1),clip(0,2.0,2),clip(0,2.0,3)),true,false,1f)
  assertFalse(t.spansSentences)
  assertFalse(TaskTimeline(emptyList(),false,false,1f).spansSentences)
 }
 @Test fun seekingDistinguishesAudioAndIntentionalSilence(){
  val t=TaskTimeline(listOf(clip(0,2.0),clip(1,4.0)),false,false,1f)
  assertEquals(TaskPoint(0,1.0,0.0,false),t.locate(1.0))
  assertEquals(TaskPoint(1,.25,0.0,false),t.locate(2.25))
  assertEquals(TaskPoint(1,0.0,0.0,false),t.locate(2.0))
  assertEquals(TaskPoint(1,1.0,0.0,false),t.locate(3.0))
  assertEquals(TaskPoint(2,0.0,0.0,false),t.locate(100.0))
 }
 @Test fun seekPositionRoundTripsAcrossEveryDrillRepeatAndGap(){
  val q=listOf(clip(0,2.0,1),clip(0,2.0,2),clip(1,4.0,1))
  val t=TaskTimeline(q,true,true,1.5f)
  for(ms in 0 until (t.totalSeconds*1000).toInt() step 37){
   val position=ms/1000.0;val point=t.locate(position)
   assertEquals(position,t.elapsedSeconds(point.index,point.clipSeconds,point.waitSeconds),.000001)
  }
 }
 @Test fun aLoopReportsOneCycleIncludingItsClosingGap(){
  val t=TaskTimeline(listOf(clip(0,2.0,1),clip(0,2.0,2)),true,false,1f,true)
  assertEquals(5.9,t.totalSeconds,.00001)
  assertEquals(1,t.locate(5.3).index);assertTrue(t.locate(5.3).waiting);assertEquals(.6,t.locate(5.3).waitSeconds,.00001)
 }

}
