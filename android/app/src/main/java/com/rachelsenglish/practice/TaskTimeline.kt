package com.rachelsenglish.practice

/** One finite listening task, including repetitions and the pauses between clips. */
class TaskTimeline(private val queue: List<Clip>,drill: Boolean,shadow: Boolean,gap: Float) {
    val waits=DoubleArray(queue.size){i -> clipWaitSeconds(queue,i,drill,shadow,gap,false)}
    private val starts=DoubleArray(queue.size)
    val totalSeconds: Double
    val spansSentences=queue.map {it.group}.distinct().size>1
    init {
        var elapsed=0.0
        queue.forEachIndexed {i,clip -> starts[i]=elapsed;elapsed+=clip.duration+waits[i]}
        totalSeconds=elapsed
    }
    fun progress(index: Int,clipElapsed: Double,waitElapsed: Double=0.0): Float {
        if(totalSeconds<=0)return 0f
        if(index>=queue.size)return 1f
        val clip=queue.getOrNull(index)?:return 0f
        return ((starts[index]+clipElapsed.coerceIn(0.0,clip.duration)+waitElapsed.coerceIn(0.0,waits[index]))/totalSeconds).toFloat().coerceIn(0f,1f)
    }
}
fun clipWaitSeconds(queue: List<Clip>,index: Int,drill: Boolean,shadow: Boolean,gap: Float,loop: Boolean): Double {
    val clip=queue.getOrNull(index)?:return 0.0
    if(index==queue.lastIndex&&!shadow&&!loop)return 0.0
    val next=queue.getOrNull(index+1)
    return if(shadow)maxOf(.8,(clip.duration-.2)*gap)
        else if(drill){if(next?.group==clip.group&&next.repeat>1).7 else 1.2}
        else .5
}
