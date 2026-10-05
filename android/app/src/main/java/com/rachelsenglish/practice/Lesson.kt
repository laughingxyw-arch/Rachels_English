package com.rachelsenglish.practice

import org.json.JSONObject

data class Course(val id: String, val title: String, val label: String, val cover: String, val version: String,
    val sha256: String, val bundle: String, val seconds: Int, val count: Int)
data class Phrase(val text: String, val start: Double, val end: Double)
data class Sentence(val id: Int, val audioFile: String, val start: Double, val lead: Double,
    val duration: Double, val phrases: List<Phrase>, val translation: String, val cues: List<String>)
data class DrillBlock(val group: Int, val audioFile: String, val duration: Double, val sourceStart: Double,
    val lead: Double, val whole: Boolean, val repeats: Int)
data class Lesson(val groups: List<Sentence>, val drill: List<DrillBlock>) {
    companion object {
        fun parse(raw: String): Lesson {
            val json=JSONObject(raw);val groups=json.getJSONArray("groups");val cues=json.optJSONArray("cues")
            val sentences=(0 until groups.length()).map { i ->
                val g=groups.getJSONObject(i);require(g.getInt("id")==i)
                val phrases=g.getJSONArray("phrases")
                Sentence(i,g.getString("audioFile"),g.getDouble("start"),g.getDouble("lead"),g.getDouble("duration"),
                    (0 until phrases.length()).map { val p=phrases.getJSONObject(it);Phrase(p.getString("text"),p.getDouble("start"),p.getDouble("end")) },
                    g.optString("zh"),cues?.optJSONArray(i)?.let { a -> (0 until a.length()).map(a::getString) }?:emptyList())
            }
            require(sentences.isNotEmpty() && sentences.size<=1000)
            val blocks=json.getJSONArray("drill")
            val drill=(0 until blocks.length()).map { i -> val b=blocks.getJSONObject(i)
                DrillBlock(b.getInt("group"),b.getString("audioFile"),b.getDouble("duration_seconds"),b.getDouble("sourceStart"),b.getDouble("lead"),b.getBoolean("whole"),b.getInt("repeats")) }
            sentences.forEach { require(safePath(it.audioFile)&&it.duration.isFinite()&&it.duration>0&&it.phrases.isNotEmpty()) }
            drill.forEach { require(it.group in sentences.indices&&safePath(it.audioFile)&&it.duration.isFinite()&&it.duration>0&&it.repeats in 1..20) }
            return Lesson(sentences,drill)
        }
    }
}
fun safePath(path: String)=path.isNotEmpty()&&!path.startsWith('/')&&!path.contains("..")&&!path.contains('\\')&&!path.contains(':')
data class Clip(val group: Int, val file: String, val duration: Double, val sourceStart: Double,
    val lead: Double, val whole: Boolean, val repeat: Int=1, val total: Int=1)
fun practiceQueue(lesson: Lesson, selected: Int, drill: Boolean, all: Boolean, repeats: Int=3): List<Clip> =
    if(drill) lesson.drill.filter { if(all) it.group>=selected else it.group==selected }.flatMap { b ->
        (1..repeats.coerceIn(2,5)).map { Clip(b.group,b.audioFile,b.duration,b.sourceStart,b.lead,b.whole,it,repeats.coerceIn(2,5)) }
    } else lesson.groups.filter { if(all) it.id>=selected else it.id==selected }.map { Clip(it.id,it.audioFile,it.duration,it.start,it.lead,true) }

enum class PracticeLoop(val label: String) { OFF("关闭"), SENTENCE("单句"), LESSON("整集") }
fun nextClipIndex(queue: List<Clip>,index: Int,loop: Boolean): Int {
    val group=queue.getOrNull(index)?.group
    return if(loop&&group!=null&&queue.getOrNull(index+1)?.group!=group)queue.indexOfFirst {it.group==group} else index+1
}
fun nextClipIndex(queue: List<Clip>,index: Int,mode: PracticeLoop): Int =when(mode){
    PracticeLoop.OFF->index+1
    PracticeLoop.SENTENCE->nextClipIndex(queue,index,true)
    PracticeLoop.LESSON->if(index==queue.lastIndex)0 else index+1
}
