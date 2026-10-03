package com.rachelsenglish.practice

/** One finite listening task, including repetitions and the pauses between clips. */
class TaskTimeline(private val queue: List<Clip>,drill: Boolean,shadow: Boolean,gap: Float,loop: Boolean=false) {
    val waits=DoubleArray(queue.size){i -> clipWaitSeconds(queue,i,drill,shadow,gap,loop)}
    private val starts=DoubleArray(queue.size)
    val totalSeconds: Double
    val spansSentences=queue.map {it.group}.distinct().size>1
    init {
        var elapsed=0.0
        queue.forEachIndexed {i,clip -> starts[i]=elapsed;elapsed+=clip.duration+waits[i]}
        totalSeconds=elapsed
    }
    fun elapsedSeconds(index: Int,clipElapsed: Double,waitElapsed: Double=0.0): Double {
        if(index>=queue.size)return totalSeconds
        val clip=queue.getOrNull(index)?:return 0.0
        return (starts[index]+clipElapsed.coerceIn(0.0,clip.duration)+waitElapsed.coerceIn(0.0,waits[index])).coerceIn(0.0,totalSeconds)
    }
    fun locate(seconds: Double): TaskPoint {
        val position=seconds.coerceIn(0.0,totalSeconds)
        if(queue.isEmpty()||position>=totalSeconds)return TaskPoint(queue.size,0.0,0.0,false)
        var low=0;var high=starts.lastIndex
        while(low<high){val mid=(low+high+1)/2;if(starts[mid]<=position)low=mid else high=mid-1}
        val elapsed=position-starts[low];val duration=queue[low].duration
        return TaskPoint(low,minOf(elapsed,duration),(elapsed-duration).coerceAtLeast(0.0),elapsed>=duration)
    }
    fun progress(index: Int,clipElapsed: Double,waitElapsed: Double=0.0): Float {
        return if(totalSeconds<=0)0f else (elapsedSeconds(index,clipElapsed,waitElapsed)/totalSeconds).toFloat()
    }
}
data class TaskPoint(val index: Int,val clipSeconds: Double,val waitSeconds: Double,val waiting: Boolean)
fun clipWaitSeconds(queue: List<Clip>,index: Int,drill: Boolean,shadow: Boolean,gap: Float,loop: Boolean): Double {
    val clip=queue.getOrNull(index)?:return 0.0
    if(index==queue.lastIndex&&!shadow&&!loop)return 0.0
    val next=queue.getOrNull(index+1)
    return if(shadow)maxOf(.8,(clip.duration-.2)*gap)
        else if(drill){if(next?.group==clip.group&&next.repeat>1).7 else 1.2}
        else .5
}
