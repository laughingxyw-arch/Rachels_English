package com.rachelsenglish.practice

import androidx.compose.animation.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CourseMotionTest {
    @Test fun defaultProgressThresholdCanLeaveSeveralPhysicalPixels(){
        val animation=TargetBasedAnimation(spring<Float>(1f,500f),Float.VectorConverter,.94f,1f)
        val previous=animation.getValueFromNanos((animation.durationNanos-16_666_667L).coerceAtLeast(0L))
        assertTrue("The old stop threshold allows a visible endpoint correction",abs(1f-previous)*2560f>1f)
    }
    @Test fun lastSampleLandsWithinOnePixelAt60And120Hz(){
        for(extent in listOf(480f,960f,2560f,4096f))for(interval in listOf(16_666_667L,8_333_333L)) {
            for(start in listOf(0f,.5f,.94f))for(target in listOf(0f,1f)) {
                if(start==target)continue
                val animation=TargetBasedAnimation(courseProgressSpring(extent),Float.VectorConverter,start,target)
                val previous=animation.getValueFromNanos((animation.durationNanos-interval).coerceAtLeast(0L))
                assertTrue("Endpoint correction must remain subpixel: extent=$extent interval=$interval start=$start target=$target",abs(target-previous)*extent<1f)
            }
        }
    }
    @Test fun viewportThresholdHasASafeFallback(){
        assertEquals(.25f/2560f,courseProgressSpring(2560f).visibilityThreshold!!,0f)
        for(extent in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY))assertEquals(.25f/4096f,courseProgressSpring(extent).visibilityThreshold!!,0f)
    }
}
