package com.rachelsenglish.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Gesture owns displacement; a new drag interrupts the release spring in place. */
@Stable
internal class RefreshMotion {
    var distanceFraction by mutableFloatStateOf(0f);private set
    var gestureVersion=0;private set
    private var settling: Job?=null
    fun drag(pixels: Float,threshold: Float): Float {
        settling?.cancel();settling=null;gestureVersion++
        val before=distanceFraction
        val resistance=if(pixels>0)2f*(1f+(before-1f).coerceAtLeast(0f)*1.5f) else 2f
        distanceFraction=(before+pixels/threshold/resistance).coerceIn(0f,1.65f)
        return (distanceFraction-before)*threshold*resistance
    }
    fun settle(scope: CoroutineScope,target: Float,reduced: Boolean,velocity: Float=0f){
        settling?.cancel()
        settling=scope.launch {
            if(reduced){distanceFraction=target;return@launch}
            val motion=Animatable(distanceFraction)
            motion.animateTo(target,spring(.88f,380f),initialVelocity=velocity.coerceIn(-4f,4f)) {distanceFraction=value.coerceAtLeast(0f)}
        }
    }
}
