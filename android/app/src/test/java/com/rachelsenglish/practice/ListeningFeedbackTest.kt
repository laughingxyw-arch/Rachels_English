package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test

class ListeningFeedbackTest {
    @Test fun unknownAndFixedVolumeDoNotWarn(){
        assertEquals(VolumeBand.NORMAL,volumeBand(ListeningEnvironment(false,0,0)))
        assertEquals(VolumeBand.NORMAL,volumeBand(ListeningEnvironment(false,10,10,true)))
    }
    @Test fun thresholdHysteresisAvoidsFlapping(){
        assertEquals(VolumeBand.MUTED,volumeBand(ListeningEnvironment(false,0,100)))
        assertEquals(VolumeBand.LOW,volumeBand(ListeningEnvironment(false,15,100)))
        assertEquals(VolumeBand.LOW,volumeBand(ListeningEnvironment(false,18,100),VolumeBand.LOW))
        assertEquals(VolumeBand.NORMAL,volumeBand(ListeningEnvironment(false,21,100),VolumeBand.LOW))
        assertEquals(VolumeBand.HIGH,volumeBand(ListeningEnvironment(true,85,100)))
        assertEquals(VolumeBand.HIGH,volumeBand(ListeningEnvironment(true,82,100),VolumeBand.HIGH))
        assertEquals(VolumeBand.NORMAL,volumeBand(ListeningEnvironment(true,79,100),VolumeBand.HIGH))
    }
    @Test fun volumeNoticeWinsAndDoesNotRepeatPerSentence(){
        val policy=ListeningFeedback();val high=ListeningEnvironment(true,14,15)
        assertTrue(policy.update(high,0)!!.contains("偏高"))
        assertNull(policy.update(high,20000))
        assertNull(policy.update(high.copy(volume=8),40000))
        assertNull(policy.update(high,60000))
        policy.reset();assertNotNull(policy.update(high,80000))
    }
    @Test fun cooldownDefersNewWarningAndNormalVolumeIsQuiet(){
        val policy=ListeningFeedback();val normal=ListeningEnvironment(true,8,15)
        assertEquals("耳机已连接。",policy.update(normal,0))
        assertNull(policy.update(normal.copy(volume=0),1000))
        assertTrue(policy.update(normal.copy(volume=0),12000)!!.contains("静音"))
        assertNull(policy.update(normal,25000))
    }
}
