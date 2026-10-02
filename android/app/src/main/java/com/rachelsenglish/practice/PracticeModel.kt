package com.rachelsenglish.practice

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Playback(val selected: Int=0,val running: Boolean=false,val paused: Boolean=false,
    val progress: Float=0f,val source: Double=-1.0,val repeat: Int=0,val total: Int=0,
    val waitSeconds: Float=0f,val waiting: Boolean=false,val whole: Boolean=true)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PracticeModel(application: Application): AndroidViewModel(application) {
    val repository=CourseRepository(application)
    private val prefs=application.getSharedPreferences("practice-native",0)
    var courses by mutableStateOf(repository.courses);private set
    var opened by mutableStateOf<OpenLesson?>(null);private set
    var loadingId by mutableStateOf<String?>(null);private set
    var syncing by mutableStateOf(false);private set
    var message by mutableStateOf<String?>(null);private set
    var drill by mutableStateOf(false);private set
    var translation by mutableStateOf(prefs.getBoolean("translation",false));private set
    var cues by mutableStateOf(prefs.getBoolean("cues",true));private set
    var loop by mutableStateOf(prefs.getBoolean("loop",false));private set
    var shadow by mutableStateOf(prefs.getBoolean("shadow",false));private set
    var gap by mutableFloatStateOf(prefs.getFloat("gap",1.5f));private set
    var playback by mutableStateOf(Playback());private set
    private val player=ExoPlayer.Builder(application).build().apply {
        setAudioAttributes(AudioAttributes.DEFAULT,true);setHandleAudioBecomingNoisy(true)
    }
    private var queue=emptyList<Clip>();private var index=0;private var openJob: Job?=null
    init {
        player.addListener(object: Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {if(state==Player.STATE_ENDED&&playback.running&&!playback.waiting)enterWait()}
            override fun onPlayerError(error: PlaybackException) {stop();message="音频播放失败，请重新打开课程。"}
            override fun onPlayWhenReadyChanged(ready: Boolean,reason: Int) {
                if(!ready&&reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS&&playback.running)pause()
            }
        })
        viewModelScope.launch {
            var last=android.os.SystemClock.elapsedRealtime()
            while(true){delay(32);val now=android.os.SystemClock.elapsedRealtime();val delta=(now-last).coerceAtMost(100)/1000f;last=now
                val state=playback
                if(state.running&&!state.paused){
                    if(state.waiting){val left=state.waitSeconds-delta;if(left<=0)advance() else playback=state.copy(waitSeconds=left)}
                    else if(player.isPlaying){queue.getOrNull(index)?.let { clip ->
                        val elapsed=player.currentPosition/1000.0
                        playback=state.copy(progress=(elapsed/clip.duration).toFloat().coerceIn(0f,1f),source=clip.sourceStart+elapsed-clip.lead)
                    }}
                }
            }
        }
        sync(false)
    }
    fun consumeMessage(){message=null}
    fun sync(explicit: Boolean=true){if(syncing)return;syncing=true;viewModelScope.launch {
        try {courses=repository.sync();if(explicit)message="课程已更新"}catch(e: Exception){if(explicit)message="同步失败，已缓存课程仍可使用。"}finally{syncing=false}
    }}
    fun open(course: Course){openJob?.cancel();loadingId=course.id;openJob=viewModelScope.launch {
        try {val result=repository.open(course);stop();opened=result;drill=false
            playback=Playback(selected=prefs.getInt("position.${course.id}",0).coerceIn(result.lesson.groups.indices))
            if(result.offlineFallback)message="暂时无法更新，已打开本地课程。"
        }catch(e: kotlinx.coroutines.CancellationException){throw e}catch(e: Exception){message="课程下载失败，请稍后重试。"}finally{loadingId=null}
    }}
    fun back(){openJob?.cancel();loadingId=null;stop();opened=null}
    fun setDrill(value: Boolean){stop();drill=value;playback=playback.copy(progress=0f)}
    fun setTranslation(v: Boolean){translation=v;prefs.edit().putBoolean("translation",v).apply()}
    fun setCues(v: Boolean){cues=v;prefs.edit().putBoolean("cues",v).apply()}
    fun setLoop(v: Boolean){loop=v;prefs.edit().putBoolean("loop",v).apply()}
    fun setShadow(v: Boolean){shadow=v;prefs.edit().putBoolean("shadow",v).apply()}
    fun setGap(v: Float){gap=v;prefs.edit().putFloat("gap",v).apply()}
    fun start(selected: Int=playback.selected,all: Boolean=false){val data=opened?:return;stop();queue=practiceQueue(data.lesson,selected,drill,all);index=0;if(queue.isNotEmpty())begin()}
    fun previous(){start((playback.selected-1).coerceAtLeast(0))}
    fun next(){val last=opened?.lesson?.groups?.lastIndex?:return;start((playback.selected+1).coerceAtMost(last))}
    fun toggle(){when{!playback.running->start();playback.paused->{playback=playback.copy(paused=false);if(!playback.waiting)player.play()};else->pause()}}
    fun pause(){if(playback.running){playback=playback.copy(paused=true);player.pause()}}
    fun stop(){playback=playback.copy(running=false,paused=false,waiting=false,source=-1.0,repeat=0);player.stop()}
    private fun begin(){val data=opened?:return;val clip=queue.getOrNull(index)?:return
        playback=Playback(clip.group,true,false,0f,-1.0,if(drill)clip.repeat else 0,clip.total,whole=clip.whole)
        prefs.edit().putInt("position.${data.course.id}",clip.group).apply()
        player.setMediaItem(MediaItem.fromUri(repository.audio(data,clip.file)));player.prepare();player.play()
    }
    private fun enterWait(){val clip=queue.getOrNull(index)?:return
        if(index==queue.lastIndex&&!shadow){advance();return}
        val next=queue.getOrNull(index+1)
        val seconds=if(shadow)maxOf(.8,(clip.duration-.2)*gap).toFloat() else if(drill){if(next?.group==clip.group&&next.repeat>1).7f else 1.2f}else .5f
        playback=playback.copy(progress=1f,waiting=true,source=-1.0,waitSeconds=seconds)
    }
    private fun advance(){index++;if(index<queue.size){begin();return}
        if(loop){opened?.let {queue=practiceQueue(it.lesson,playback.selected,drill,false);index=0;begin()}}
        else {stop();playback=playback.copy(progress=1f)}
    }
    override fun onCleared(){player.release();super.onCleared()}
}
