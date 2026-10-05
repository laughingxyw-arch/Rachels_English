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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel

data class Playback(val selected: Int=0,val running: Boolean=false,val paused: Boolean=false,
    val progress: Float=0f,val source: Double=-1.0,val repeat: Int=0,val total: Int=0,
    val waitSeconds: Float=0f,val waiting: Boolean=false,val whole: Boolean=true,val taskProgress: Float?=null)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PracticeModel(application: Application): AndroidViewModel(application) {
    val repository=CourseRepository(application)
    private val prefs=application.getSharedPreferences("practice-native",0)
    var courses by mutableStateOf(repository.courses);private set
    var opened by mutableStateOf<OpenLesson?>(null,referentialEqualityPolicy());private set
    var loadingId by mutableStateOf<String?>(null);private set
    var syncing by mutableStateOf(false);private set
    var message by mutableStateOf<String?>(null);private set
    var messageIsError by mutableStateOf(false);private set
    var drill by mutableStateOf(false);private set
    var repeatCount by mutableIntStateOf(prefs.getInt("repeats",3).coerceIn(2,5));private set
    var reduceTransparency by mutableStateOf(prefs.getBoolean("reduce-transparency",false));private set
    var enhanceContrast by mutableStateOf(prefs.getBoolean("enhance-contrast",false));private set
    var translation by mutableStateOf(prefs.getBoolean("translation",false));private set
    var cues by mutableStateOf(prefs.getBoolean("cues",true));private set
    var loopMode by mutableStateOf(runCatching {PracticeLoop.valueOf(prefs.getString("loop-mode",null)?:if(prefs.getBoolean("loop",false))"SENTENCE" else "OFF")}.getOrDefault(PracticeLoop.OFF));private set
    val loop: Boolean get()=loopMode==PracticeLoop.SENTENCE
    private val anyLoop: Boolean get()=loopMode!=PracticeLoop.OFF
    var shadow by mutableStateOf(prefs.getBoolean("shadow",false));private set
    var gap by mutableFloatStateOf(prefs.getFloat("gap",1.5f));private set
    private var playbackState by mutableStateOf(Playback())
    var mediaChanged: (() -> Unit)?=null
    var playback: Playback
        get()=playbackState
        private set(value){
            val old=playbackState;playbackState=value
            // Position is supplied live; do not rebuild the media session on every UI tick.
            if(old.running!=value.running||old.paused!=value.paused||old.selected!=value.selected||old.waiting!=value.waiting)mediaChanged?.invoke()
        }
    var mediaTaskId=0;private set
    var mediaDiscontinuity=0;private set
    var mediaArtwork: ByteArray?=null;private set
    private var artworkJob: Job?=null
    private var mediaTimeline=TaskTimeline(emptyList(),false,false,1f)
    private var mediaOffset=0
    val mediaDurationMs: Long get()=(mediaTimeline.totalSeconds*1000).toLong()
    val mediaEnded: Boolean get()=queue.isNotEmpty()&&index>=queue.size&&!playback.running
    val mediaPositionMs: Long get(){
        val p=playback;val clip=queue.getOrNull(index)
        return if(clip==null)mediaDurationMs else (mediaTimeline.elapsedSeconds(index-mediaOffset,clip.duration*p.progress,
            if(p.waiting)(waitTotal-p.waitSeconds).coerceAtLeast(0f).toDouble() else 0.0)*1000).toLong()
    }
    private val audioHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var audioStartToken=0
    private fun playAudio(){
        val token=++audioStartToken
        // Let the media service publish its foreground notification before audio focus.
        // A subsequent pause/stop/seek invalidates this pending start.
        audioHandler.post {if(token==audioStartToken&&playback.running&&!playback.paused&&!playback.waiting)player.play()}
    }
    private val waitClock=PlaybackWaitClock()
    private val waitLock=(application.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
        .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"rachels:practice-gap").apply {setReferenceCounted(false)}
    private fun releaseWaitLock(){if(waitLock.isHeld)waitLock.release()}
    private fun scheduleWait(){
        waitClock.start(android.os.SystemClock.elapsedRealtime(),playback.waitSeconds)
        releaseWaitLock()
        if(playback.waitSeconds>0)waitLock.acquire((playback.waitSeconds*1000).toLong()+5000)
    }
    private fun ensurePlaybackService(){
        getApplication<Application>().startForegroundService(android.content.Intent(getApplication(),PracticePlaybackService::class.java))
    }
    private val player=ExoPlayer.Builder(application).build().apply {
        setAudioAttributes(AudioAttributes.DEFAULT,true);setHandleAudioBecomingNoisy(true);setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
    }
    private var queue=emptyList<Clip>();private var index=0;private var playAll=true;private var openJob: Job?=null;private var openToken=0
    private var taskStart=0
    private var timeline=TaskTimeline(emptyList(),false,false,1f)
    private var waitTotal=0f
    private val listening=ListeningMonitor(application) { removed ->
        if(removed&&opened!=null&&playback.running){pause();notify("耳机已断开 · 播放暂停")}
    }
    private val feedback=ListeningFeedback()
    private var foreground=false
    private val studyStore=StudyStore(application)
    private var studyLedger=StudyLedger()
    private var studyReady=false
    private val studyWrites=Channel<List<StudyDay>>(Channel.UNLIMITED)
    private val studyMeter=StudyPlaybackMeter()
    private var studySampleAt=android.os.SystemClock.elapsedRealtime()
    private var studySampleWall=System.currentTimeMillis()
    private var studyWaitRemaining: Float?=null
    private var studyDirty=false
    private fun sampleStudy(){
        val now=android.os.SystemClock.elapsedRealtime();val wall=System.currentTimeMillis()
        val elapsed=(now-studySampleAt).coerceAtLeast(0);val previousWall=studySampleWall
        studySampleAt=now;studySampleWall=wall
        val data=opened;val clip=queue.getOrNull(index);val p=playback
        var audio=0L;var follow=0L
        if(studyReady&&data!=null&&clip!=null&&p.running&&!p.paused){
            if(p.waiting){
                val remaining=waitClock.remaining(now)
                studyWaitRemaining?.let {before->if(shadow)follow=minOf(elapsed,((before-remaining).coerceAtLeast(0f)*1000).toLong())}
                studyWaitRemaining=remaining;studyMeter.reset()
            }else{
                studyWaitRemaining=null
                if(player.isPlaying||player.playbackState==Player.STATE_ENDED){
                    val measured=studyMeter.advance(player.currentPosition,elapsed,(clip.lead*1000).toLong(),((clip.duration-.12)*1000).toLong().coerceAtLeast((clip.lead*1000).toLong()))
                    if(volumeBand(listening.environment())!=VolumeBand.MUTED)audio=measured
                }else studyMeter.reset(player.currentPosition)
            }
            if(audio+follow>0){
                // Anchor to monotonic elapsed time when the wall clock is adjusted.
                val end=if(kotlin.math.abs(wall-previousWall-elapsed)<=2000)wall else previousWall+elapsed
                studyLedger.credit(data.course.id,previousWall,end,audio,follow,java.time.ZoneId.systemDefault());studyDirty=true
            }
        }else{studyMeter.reset();studyWaitRemaining=null}
    }
    private fun flushStudy(){if(studyReady&&studyDirty){studyDirty=false;studyWrites.trySend(studyLedger.takeChanges())}}
    fun studySnapshot(): List<StudyDay>{sampleStudy();flushStudy();return studyLedger.snapshot()}

    init {
        viewModelScope.launch {
            studyLedger=StudyLedger(withContext(Dispatchers.IO){studyStore.read()});studyReady=true
            for(rows in studyWrites)withContext(Dispatchers.IO){studyStore.write(rows)}
        }
        viewModelScope.launch {var ticks=0;while(true){delay(500);sampleStudy();if(++ticks%20==0)flushStudy()}}
        player.addListener(object: Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {if(state==Player.STATE_ENDED&&playback.running&&!playback.waiting)enterWait()}
            override fun onPlayerError(error: PlaybackException) {stop();notify("音频播放失败，请重新打开课程。",true)}
            override fun onPlayWhenReadyChanged(ready: Boolean,reason: Int) {
                if(!ready&&(reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS||reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)&&playback.running)pause()
            }
        })
        viewModelScope.launch {
            while(true){delay(if(foreground)32 else 100);val now=android.os.SystemClock.elapsedRealtime()
                val state=playback
                if(state.running&&!state.paused){
                    if(state.waiting){val left=waitClock.remaining(now);if(left<=0)advance() else playback=state.copy(waitSeconds=left,taskProgress=taskProgress(1f,left))}
                    else if(player.isPlaying){queue.getOrNull(index)?.let { clip ->
                        val elapsed=player.currentPosition/1000.0
                        playback=state.copy(progress=(elapsed/clip.duration).toFloat().coerceIn(0f,1f),source=clip.sourceStart+elapsed-clip.lead,taskProgress=taskProgress((elapsed/clip.duration).toFloat()))
                    }}
                }
            }
        }
        sync(false)
        viewModelScope.launch {while(true){delay(800);if(foreground&&opened!=null&&playback.running&&!playback.paused)checkListening()}}
    }
    fun foreground(value: Boolean){foreground=value;if(!value){sampleStudy();flushStudy()}}
    private fun checkListening(){feedback.update(listening.environment(),android.os.SystemClock.elapsedRealtime())?.let {notify(it)}}
    fun notify(text: String,error: Boolean=false){messageIsError=error;message=text}
    fun consumeMessage(){message=null}
    fun sync(explicit: Boolean=true){if(syncing)return;syncing=true;viewModelScope.launch {
        try {courses=repository.sync();if(explicit)notify("课程已更新")}catch(e: Exception){if(explicit)notify("同步失败，已缓存课程仍可使用。",true)}finally{syncing=false}
    }}
    fun open(course: Course){val token=++openToken;openJob?.cancel();loadingId=course.id;openJob=viewModelScope.launch {
        try {val result=repository.open(course);stop();queue=emptyList();index=0;refreshTimeline();opened=result;drill=false;feedback.reset()
            artworkJob?.cancel();mediaArtwork=null
            artworkJob=viewModelScope.launch {
                val bytes=repository.mediaArtwork(result.course)
                if(opened===result){mediaArtwork=bytes;mediaChanged?.invoke()}
            }
            playback=Playback(selected=prefs.getInt("position.${course.id}",0).coerceIn(result.lesson.groups.indices))
            if(result.offlineFallback)notify("已打开本地课程")
        }catch(e: kotlinx.coroutines.CancellationException){throw e}catch(e: Exception){notify("课程下载失败，请稍后重试。",true)}finally{if(token==openToken)loadingId=null}
    }}
    fun back(){openToken++;openJob?.cancel();loadingId=null;stop();opened=null;artworkJob?.cancel();mediaArtwork=null;mediaChanged?.invoke()}
    // A visual return may finish after the user has already requested another course.
    // Close only its original session, without cancelling the new loading job.
    fun finishBack(origin: OpenLesson?){if(opened===origin){stop();opened=null;artworkJob?.cancel();mediaArtwork=null;mediaChanged?.invoke()}}
    fun updateDrill(value: Boolean){if(drill==value)return;val resume=playback.running;val paused=playback.paused;stop();drill=value;mediaChanged?.invoke();playback=playback.copy(progress=0f);if(resume){start();if(paused)pause()}}
    fun updateRepeatCount(value: Int){
        val count=value.coerceIn(2,5);if(count==repeatCount)return
        sampleStudy();repeatCount=count;prefs.edit().putInt("repeats",count).apply()
        if(!drill||!playback.running)return
        val data=opened?:return;val current=queue.getOrNull(index)?:return
        val updated=practiceQueue(data.lesson,taskStart,true,playAll,count)
        val nextIndex=updated.indexOfFirst {it.file==current.file&&it.whole==current.whole&&it.repeat==current.repeat.coerceAtMost(count)}
        if(nextIndex>=0){queue=updated;index=nextIndex;playback=playback.copy(repeat=current.repeat.coerceAtMost(count),total=count);refreshTimeline()}
    }
    fun updateReduceTransparency(v: Boolean){reduceTransparency=v;prefs.edit().putBoolean("reduce-transparency",v).apply()}
    fun updateEnhanceContrast(v: Boolean){enhanceContrast=v;prefs.edit().putBoolean("enhance-contrast",v).apply()}
    fun updateTranslation(v: Boolean){translation=v;prefs.edit().putBoolean("translation",v).apply()}
    fun updateCues(v: Boolean){cues=v;prefs.edit().putBoolean("cues",v).apply()}
    fun updateLoop(v: Boolean)=updateLoopMode(if(v)PracticeLoop.SENTENCE else PracticeLoop.OFF)
    fun updateLoopMode(mode: PracticeLoop){
        if(mode==loopMode)return
        sampleStudy();loopMode=mode;prefs.edit().putString("loop-mode",mode.name).apply()
        if(mode==PracticeLoop.LESSON&&playAll&&opened!=null&&queue.isNotEmpty()){
            val current=queue.getOrNull(index);val ended=index>=queue.size
            val updated=practiceQueue(opened!!.lesson,0,drill,true,repeatCount)
            index=if(ended)updated.size else updated.indexOfFirst {it.file==current?.file&&it.repeat==current?.repeat}.coerceAtLeast(0)
            queue=updated;taskStart=0
        }
        refreshTimeline()
    }
    fun updateShadow(v: Boolean){sampleStudy();shadow=v;prefs.edit().putBoolean("shadow",v).apply();refreshTimeline()}
    fun updateGap(v: Float){sampleStudy();gap=v;prefs.edit().putFloat("gap",v).apply();refreshTimeline()}
    fun start(selected: Int=playback.selected,all: Boolean=true){val data=opened?:return;stop();mediaTaskId++;playAll=all;taskStart=if(loopMode==PracticeLoop.LESSON&&all)0 else selected;queue=practiceQueue(data.lesson,taskStart,drill,all,repeatCount);index=queue.indexOfFirst {it.group==selected}.coerceAtLeast(0);refreshTimeline();if(queue.isNotEmpty()){ensurePlaybackService();checkListening();begin()}}
    fun previous(){start((playback.selected-1).coerceAtLeast(0))}
    fun next(){val last=opened?.lesson?.groups?.lastIndex?:return;start((playback.selected+1).coerceAtMost(last))}
    fun toggle(){when{!playback.running->start();playback.paused->{sampleStudy();studyMeter.reset(player.currentPosition);studyWaitRemaining=playback.waitSeconds;checkListening();ensurePlaybackService();playback=playback.copy(paused=false);if(playback.waiting)scheduleWait() else playAudio()};else->pause()}}
    fun pause(){sampleStudy();flushStudy();audioStartToken++;if(playback.running&&!playback.paused){
        val remaining=if(playback.waiting)waitClock.remaining(android.os.SystemClock.elapsedRealtime()) else playback.waitSeconds
        playback=playback.copy(paused=true,waitSeconds=remaining);releaseWaitLock();player.pause()
    }}
    fun stop(){sampleStudy();flushStudy();studyMeter.reset();studyWaitRemaining=null;audioStartToken++;releaseWaitLock();playback=playback.copy(running=false,paused=false,waiting=false,source=-1.0,repeat=0,taskProgress=null);player.stop()}
    fun seekMedia(positionMs: Long){
        if(queue.isEmpty()||opened==null||mediaTimeline.clipCount==0)return
        sampleStudy();studyMeter.reset();studyWaitRemaining=null
        val paused=!playback.running||playback.paused
        // The system exposes integer milliseconds; its displayed endpoint can
        // round down from the precise audio timeline by less than one millisecond.
        val point=if(positionMs>=mediaDurationMs)TaskPoint(mediaTimeline.clipCount,0.0,0.0,false)
            else mediaTimeline.locate(positionMs.coerceAtLeast(0)/1000.0)
        if(point.index>=mediaTimeline.clipCount){
            if(anyLoop){seekMedia(0);return}
            index=queue.size;stop();playback=playback.copy(selected=queue.last().group,progress=1f,taskProgress=if(timeline.spansSentences)1f else null);mediaChanged?.invoke();return
        }
        audioStartToken++;releaseWaitLock();player.pause()
        index=mediaOffset+point.index
        begin()
        val clip=queue[index]
        player.seekTo((point.clipSeconds*1000).toLong())
        studyMeter.reset((point.clipSeconds*1000).toLong())
        waitTotal=clipWaitSeconds(queue,index,drill,shadow,gap,anyLoop).toFloat()
        playback=playback.copy(progress=(point.clipSeconds/clip.duration).toFloat(),waiting=point.waiting,
            waitSeconds=(waitTotal-point.waitSeconds).toFloat().coerceAtLeast(0f),
            source=if(point.waiting)-1.0 else clip.sourceStart+point.clipSeconds-clip.lead,
            taskProgress=taskProgress((point.clipSeconds/clip.duration).toFloat(),if(point.waiting)(waitTotal-point.waitSeconds).toFloat() else null))
        studyWaitRemaining=if(point.waiting)playback.waitSeconds else null
        if(point.waiting)waitClock.start(android.os.SystemClock.elapsedRealtime(),playback.waitSeconds)
        if(paused)pause() else if(point.waiting){player.pause();scheduleWait()}
        mediaChanged?.invoke()
    }
    private fun begin(){studyMeter.reset(0);studyWaitRemaining=null;releaseWaitLock();val data=opened?:return;val clip=queue.getOrNull(index)?:return
        playback=Playback(clip.group,true,false,0f,-1.0,if(drill)clip.repeat else 0,clip.total,whole=clip.whole,taskProgress=taskProgress(0f))
        prefs.edit().putInt("position.${data.course.id}",clip.group).apply()
        player.setMediaItem(MediaItem.fromUri(repository.audio(data,clip.file)));player.prepare();playAudio()
    }
    private fun enterWait(){sampleStudy();studyMeter.reset();val clip=queue.getOrNull(index)?:return
        if(index==queue.lastIndex&&!shadow&&!anyLoop){advance();return}
        val seconds=clipWaitSeconds(queue,index,drill,shadow,gap,anyLoop).toFloat()
        waitTotal=seconds;studyWaitRemaining=seconds
        playback=playback.copy(progress=1f,waiting=true,source=-1.0,waitSeconds=seconds,taskProgress=taskProgress(1f,seconds))
        scheduleWait()
    }
    private fun advance(){
        sampleStudy();val previous=index;index=nextClipIndex(queue,index,loopMode);if(index<=previous)mediaDiscontinuity++;if(index<queue.size){begin();return}
        stop();playback=playback.copy(progress=1f,taskProgress=if(!loop&&timeline.spansSentences)1f else null)
    }
    private fun taskProgress(clipProgress: Float,waitLeft: Float?=null): Float? {
        if(loop||!timeline.spansSentences)return null
        val clip=queue.getOrNull(index)?:return 1f
        return timeline.progress(index,clip.duration*clipProgress.coerceIn(0f,1f),
            if(waitLeft==null)0.0 else (waitTotal-waitLeft).coerceAtLeast(0f).toDouble())
    }
    private fun refreshTimeline(){
        timeline=TaskTimeline(queue,drill,shadow,gap,loopMode==PracticeLoop.LESSON)
        val group=queue.getOrNull(index)?.group?:queue.lastOrNull()?.group
        mediaOffset=if(loop)queue.indexOfFirst {it.group==group}.coerceAtLeast(0) else 0
        mediaTimeline=if(loop)TaskTimeline(queue.filter {it.group==group},drill,shadow,gap,true) else timeline
        mediaChanged?.invoke()
        if(!playback.running)return
        if(playback.waiting){
            val elapsedRatio=if(waitTotal>0)1f-playback.waitSeconds/waitTotal else 0f
            waitTotal=clipWaitSeconds(queue,index,drill,shadow,gap,anyLoop).toFloat()
            playback=playback.copy(waitSeconds=waitTotal*(1f-elapsedRatio.coerceIn(0f,1f)))
            studyWaitRemaining=playback.waitSeconds
            if(!playback.paused)scheduleWait()
        }
        playback=playback.copy(taskProgress=taskProgress(playback.progress,if(playback.waiting)playback.waitSeconds else null))
    }
    override fun onCleared(){releaseWaitLock();listening.close();player.release();super.onCleared()}
}
