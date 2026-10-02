package com.rachelsenglish.practice

// Progress per second, sampled from the actual gesture rather than a fixed exit timer.
class GestureVelocity {
    private var progress=0f;private var time=0L
    var velocity=0f;private set
    fun add(value: Float,millis: Long){
        val dt=millis-time
        if(time!=0L&&dt in 1..120)velocity=((value-progress)*1000/dt).coerceIn(-6f,6f)
        else velocity=0f
        progress=value;time=millis
    }
}
