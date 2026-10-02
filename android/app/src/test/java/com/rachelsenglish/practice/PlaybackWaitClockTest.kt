package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test
class PlaybackWaitClockTest {
    @Test fun delayedBackgroundTickUsesActualElapsedTime(){
        val clock=PlaybackWaitClock();clock.start(1000,3f)
        assertEquals(2.6f,clock.remaining(1400),.0001f)
        assertEquals(0f,clock.remaining(9000),.0001f)
    }
    @Test fun resumeDoesNotCountTimeSpentPaused(){
        val clock=PlaybackWaitClock();clock.start(1000,3f)
        val remaining=clock.remaining(2000)
        clock.start(10000,remaining)
        assertEquals(1.5f,clock.remaining(10500),.0001f)
    }
    @Test fun changedGapReplacesTheOldDeadline(){
        val clock=PlaybackWaitClock();clock.start(1000,10f);clock.start(2000,.5f)
        assertEquals(.25f,clock.remaining(2250),.0001f)
        assertEquals(0f,clock.remaining(2501),.0001f)
    }
}
