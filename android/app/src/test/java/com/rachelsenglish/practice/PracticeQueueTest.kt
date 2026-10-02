package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test
class PracticeQueueTest {
 private val lesson=Lesson(listOf(
  Sentence(0,"g0.wav",0.0,.08,2.0,listOf(Phrase("one",0.0,1.0)),"",emptyList()),
  Sentence(1,"g1.wav",2.0,.08,2.0,listOf(Phrase("two",2.0,3.0)),"",emptyList())),
  listOf(DrillBlock(0,"part1.wav",1.0,0.0,.08,false,3),DrillBlock(0,"part2.wav",1.0,1.0,.08,false,3),DrillBlock(0,"g0.wav",2.0,0.0,.08,true,3),DrillBlock(1,"g1.wav",2.0,2.0,.08,true,3)))
 @Test fun drillRepeatsPartsThenWholeSentence(){val q=practiceQueue(lesson,0,true,false)
  assertEquals(listOf("part1.wav","part1.wav","part1.wav","part2.wav","part2.wav","part2.wav","g0.wav","g0.wav","g0.wav"),q.map {it.file})
  assertEquals(listOf(1,2,3,1,2,3,1,2,3),q.map {it.repeat});assertTrue(q.takeLast(3).all {it.whole})}
 @Test fun originalDoesNotRepeat(){assertEquals(listOf("g0.wav","g1.wav"),practiceQueue(lesson,0,false,true).map {it.file})}
 @Test fun fullDrillContinuesIntoFollowingGroups(){assertEquals(12,practiceQueue(lesson,0,true,true).size);assertEquals(3,practiceQueue(lesson,1,true,true).size)}
 @Test fun cachePathsCannotEscape(){listOf("../x","/x","https://x","a\\b","a/../../b").forEach {assertFalse(safePath(it))};assertTrue(safePath("audio/course/g01.wav"))}
}
