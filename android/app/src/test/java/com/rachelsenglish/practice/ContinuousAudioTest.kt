package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test
class ContinuousAudioTest {
    private val lesson=Lesson(listOf(
        Sentence(0,"a.wav",1.1,.08,1.25,listOf(Phrase("a",1.1,2.15)),"",emptyList()),
        Sentence(1,"b.wav",2.52,.08,1.56,listOf(Phrase("b",2.52,3.88)),"",emptyList()),
        Sentence(2,"c.wav",4.59,.08,2.41,listOf(Phrase("c",4.59,6.8)),"",emptyList())
    ),emptyList(),ContinuousAudio("continuous.wav",1.1,5.7))
    @Test fun continuousTimelineKeepsOriginalPauseAndHasNoClipPadding(){
        val q=continuousQueue(lesson,0);val t=TaskTimeline(q,false,false,1f)
        assertEquals(5.7,t.totalSeconds,.00001)
        assertEquals(1.42,q[0].duration,.00001)
        assertTrue(q.all {it.file=="continuous.wav"&&it.lead==0.0})
        assertTrue(t.waits.all {it==0.0})
        assertEquals(0,continuousClipIndex(q,2.3)) // Native pause stays in the same stream.
        assertEquals(1,continuousClipIndex(q,2.52))
        assertEquals(2,continuousClipIndex(q,6.8))
    }
    @Test fun tappingMiddleSentenceCreatesExactlyTheRemainingTask(){
        val q=continuousQueue(lesson,1);val t=TaskTimeline(q,false,false,1f)
        assertEquals(4.28,t.totalSeconds,.00001)
        assertEquals(1,q.first().group)
        for(ms in 0 until 4280 step 31){
            val point=t.locate(ms/1000.0)
            assertFalse(point.waiting)
            assertEquals(ms/1000.0,t.elapsedSeconds(point.index,point.clipSeconds),.000001)
        }
    }
    @Test fun overlappingSpeechDoesNotDuplicateAnySamples(){
        val overlapping=lesson.copy(groups=listOf(lesson.groups[0],lesson.groups[1].copy(start=2.0),lesson.groups[2]))
        val q=continuousQueue(overlapping,0)
        assertEquals(.9,q[0].duration,.000001)
        assertEquals(5.7,TaskTimeline(q,false,false,1f).totalSeconds,.00001)
    }
}
