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
    var messageIsError by mutableStateOf(false);private set
    var drill by mutableStateOf(false);private set
    var repeatCount by mutableIntStateOf(prefs.getInt("repeats",3).coerceIn(2,5));private set
    var translation by mutableStateOf(prefs.getBoolean("translation",false));private set
    var cues by mutableStateOf(prefs.getBoolean("cues",true));private set
    var loop by mutableStateOf(prefs.getBoolean("loop",false));private set
    var shadow by mutableStateOf(prefs.getBoolean("shadow",false));private set
    var gap by mutableFloatStateOf(prefs.getFloat("gap",1.5f));private set
    var playback by mutableStateOf(Playback());private set
    private val player=ExoPlayer.Builder(application).build().apply {
        setAudioAttributes(AudioAttributes.DEFAULT,true);setHandleAudioBecomingNoisy(true)
    }
    private var queue=emptyList<Clip>();private var index=0;private var playAll=true;private var openJob: Job?=null;private var openToken=0
    private val listening=ListeningMonitor(application) { removed ->
        if(removed&&opened!=null&&playback.running){pause();notify("耳机已断开 · 播放暂停")}
    }
    private val feedback=ListeningFeedback()
    private var foreground=false
    init {
        player.addListener(object: Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {if(state==Player.STATE_ENDED&&playback.running&&!playback.waiting)enterWait()}
            override fun onPlayerError(error: PlaybackException) {stop();notify("音频播放失败，请重新打开课程。",true)}
            override fun onPlayWhenReadyChanged(ready: Boolean,reason: Int) {
                if(!ready&&(reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS||reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)&&playback.running)pause()
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
        viewModelScope.launch {while(true){delay(800);if(foreground&&opened!=null&&playback.running&&!playback.paused)checkListening()}}
    }
    fun foreground(value: Boolean){foreground=value}
    private fun checkListening(){feedback.update(listening.environment(),android.os.SystemClock.elapsedRealtime())?.let {notify(it)}}
    fun notify(text: String,error: Boolean=false){messageIsError=error;message=text}
    fun consumeMessage(){message=null}
    fun sync(explicit: Boolean=true){if(syncing)return;syncing=true;viewModelScope.launch {
        try {courses=repository.sync();if(explicit)notify("课程已更新")}catch(e: Exception){if(explicit)notify("同步失败，已缓存课程仍可使用。",true)}finally{syncing=false}
    }}
    fun open(course: Course){val token=++openToken;openJob?.cancel();loadingId=course.id;openJob=viewModelScope.launch {
        try {val result=repository.open(course);stop();opened=result;drill=false;feedback.reset()
            playback=Playback(selected=prefs.getInt("position.${course.id}",0).coerceIn(result.lesson.groups.indices))
            if(result.offlineFallback)notify("已打开本地课程")
        }catch(e: kotlinx.coroutines.CancellationException){throw e}catch(e: Exception){notify("课程下载失败，请稍后重试。",true)}finally{if(token==openToken)loadingId=null}
    }}
    fun back(){openToken++;openJob?.cancel();loadingId=null;stop();opened=null}
    fun updateDrill(value: Boolean){if(drill==value)return;val resume=playback.running;val paused=playback.paused;stop();drill=value;playback=playback.copy(progress=0f);if(resume){start();if(paused)pause()}}
    fun updateRepeatCount(value: Int){
        val count=value.coerceIn(2,5);if(count==repeatCount)return
        repeatCount=count;prefs.edit().putInt("repeats",count).apply()
        if(!drill||!playback.running)return
        val data=opened?:return;val current=queue.getOrNull(index)?:return
        val updated=practiceQueue(data.lesson,current.group,true,playAll,count)
        val nextIndex=updated.indexOfFirst {it.file==current.file&&it.whole==current.whole&&it.repeat==current.repeat.coerceAtMost(count)}
        if(nextIndex>=0){queue=updated;index=nextIndex;playback=playback.copy(repeat=current.repeat.coerceAtMost(count),total=count)}
    }
    fun updateTranslation(v: Boolean){translation=v;prefs.edit().putBoolean("translation",v).apply()}
    fun updateCues(v: Boolean){cues=v;prefs.edit().putBoolean("cues",v).apply()}
    fun updateLoop(v: Boolean){loop=v;prefs.edit().putBoolean("loop",v).apply()}
    fun updateShadow(v: Boolean){shadow=v;prefs.edit().putBoolean("shadow",v).apply()}
    fun updateGap(v: Float){gap=v;prefs.edit().putFloat("gap",v).apply()}
    fun start(selected: Int=playback.selected,all: Boolean=true){val data=opened?:return;stop();playAll=all;queue=practiceQueue(data.lesson,selected,drill,all,repeatCount);index=0;if(queue.isNotEmpty()){checkListening();begin()}}
    fun previous(){start((playback.selected-1).coerceAtLeast(0))}
    fun next(){val last=opened?.lesson?.groups?.lastIndex?:return;start((playback.selected+1).coerceAtMost(last))}
    fun toggle(){when{!playback.running->start();playback.paused->{checkListening();playback=playback.copy(paused=false);if(!playback.waiting)player.play()};else->pause()}}
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
    private fun advance(){
        index=nextClipIndex(queue,index,loop);if(index<queue.size){begin();return}
        stop();playback=playback.copy(progress=1f)
    }
    override fun onCleared(){listening.close();player.release();super.onCleared()}
}
