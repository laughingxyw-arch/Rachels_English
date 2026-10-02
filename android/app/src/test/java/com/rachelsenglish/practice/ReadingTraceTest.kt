package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test
class ReadingTraceTest {
 @Test fun traceUsesPhraseTimingAndStopsAtItsBounds(){
  assertEquals(0f,phraseReadProgress(4.0,6.0,3.0),0f)
  assertEquals(.25f,phraseReadProgress(4.0,6.0,4.5),.0001f)
  assertEquals(1f,phraseReadProgress(4.0,6.0,7.0),0f)
  assertEquals(0f,phraseReadProgress(4.0,6.0,-1.0),0f)
 }
 @Test fun missingOrInvalidTimingDoesNotProduceAnInvalidStroke(){
  assertEquals(0f,phraseReadProgress(4.0,4.0,4.0),0f)
  assertEquals(0f,phraseReadProgress(4.0,6.0,Double.NaN),0f)
 }
}
