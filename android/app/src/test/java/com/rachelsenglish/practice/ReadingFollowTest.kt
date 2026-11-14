package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test
class ReadingFollowTest {
 @Test fun leavesContextAroundShortSentencesInsteadOfOnlyAvoidingClipping(){
  assertEquals(300f,readingFollowDelta(750f,100f,1000f),.01f)
  assertEquals(-350f,readingFollowDelta(100f,100f,1000f),.01f)
  assertEquals(0f,readingFollowDelta(400f,100f,1000f),.01f)
 }
 @Test fun longSentenceFollowsTheSpokenLineInsteadOfTheEntireCard(){
  assertEquals(0f,readingFollowDelta(0f,1400f,1000f,450f,500f),.01f)
  assertEquals(425f,readingFollowDelta(0f,1400f,1000f,900f,950f),.01f)
  assertEquals(0f,readingFollowDelta(0f,1400f,0f,900f,950f),.01f)
 }
}
