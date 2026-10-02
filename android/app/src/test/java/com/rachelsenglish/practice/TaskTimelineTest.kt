package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test
class TaskTimelineTest {
 private fun clip(group: Int,duration: Double,repeat: Int=1)=Clip(group,"g$group.wav",duration,0.0,0.0,true,repeat,3)
 @Test fun originalUsesDurationOfTheRemainingTaskAndIncludesItsGap(){
  val t=TaskTimeline(listOf(clip(2,2.0),clip(3,8.0)),false,false,1.5f)
  assertEquals(10.5,t.totalSeconds,.0001)
  assertTrue(t.spansSentences)
  assertEquals(1f/10.5f,t.progress(0,1.0),.0001f)
  assertEquals(2.5f/10.5f,t.progress(0,2.0,.5),.0001f)
  assertEquals(t.progress(0,2.0,.5),t.progress(1,0.0),.0001f)
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
}
