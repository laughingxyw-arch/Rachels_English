package com.rachelsenglish.practice

data class ListeningEnvironment(val headphones: Boolean,val volume: Int,val maximum: Int,val fixed: Boolean=false)
enum class VolumeBand { MUTED, LOW, NORMAL, HIGH }

// Volume steps describe the system setting, not measured loudness or sound pressure.
fun volumeBand(environment: ListeningEnvironment,previous: VolumeBand=VolumeBand.NORMAL): VolumeBand {
    if(environment.fixed||environment.maximum<=0)return VolumeBand.NORMAL
    if(environment.volume<=0)return VolumeBand.MUTED
    val fraction=environment.volume.toFloat()/environment.maximum
    return when {
        fraction<=.15f||(previous==VolumeBand.LOW&&fraction<.2f)->VolumeBand.LOW
        fraction>=.85f||(previous==VolumeBand.HIGH&&fraction>.8f)->VolumeBand.HIGH
        else->VolumeBand.NORMAL
    }
}

class ListeningFeedback {
    private var headphones: Boolean?=null
    private var band=VolumeBand.NORMAL
    private val reported=mutableSetOf<VolumeBand>()
    private var lastNotice: Long?=null
    fun reset(){headphones=null;band=VolumeBand.NORMAL;reported.clear();lastNotice=null}
    fun update(environment: ListeningEnvironment,now: Long): String? {
        val connected=environment.headphones&&headphones!=true
        headphones=environment.headphones
        band=volumeBand(environment,band)
        if(lastNotice?.let {now-it<12000}==true)return null
        val notice=if(band!=VolumeBand.NORMAL&&band !in reported) {
            reported.add(band)
            when(band){
                VolumeBand.MUTED->"媒体音量已静音，可用音量键调高。"
                VolumeBand.LOW->"媒体音量较低，可用音量键调整。"
                VolumeBand.HIGH->"当前音量偏高，可以调低后再听。"
                else->null
            }
        } else if(connected) "耳机已连接。" else null
        if(notice!=null)lastNotice=now
        return if(connected&&notice!=null&&band!=VolumeBand.NORMAL)"耳机已连接。$notice" else notice
    }
}
