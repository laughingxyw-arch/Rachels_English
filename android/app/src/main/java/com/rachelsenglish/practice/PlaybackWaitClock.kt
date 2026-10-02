package com.rachelsenglish.practice

// A monotonic deadline prevents deep sleep or a delayed tick from stretching a gap.
class PlaybackWaitClock {
    private var deadline=0L
    fun start(now: Long,seconds: Float){deadline=now+(seconds.coerceAtLeast(0f)*1000).toLong()}
    fun remaining(now: Long)=((deadline-now).coerceAtLeast(0L)/1000f)
}
