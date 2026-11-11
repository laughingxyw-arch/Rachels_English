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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
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
    internal var courseOpener: suspend (Course)->OpenLesson=repository::open
    private val prefs=application.getSharedPreferences("practice-native",0)
    var courses by mutableStateOf(repository.courses);private set
    var opened by mutableStateOf<OpenLesson?>(null,referentialEqualityPolicy());private set
    private var readingVisible by mutableStateOf(false)
    val readingLesson: OpenLesson? get()=opened.takeIf {readingVisible}
    var readingSessionId=0L;private set
    var inlineCourseId by mutableStateOf<String?>(null);private set
    var courseChanging by mutableStateOf(false);private set
    private var courseSwitchJob: Job?=null
    private var courseSwitchToken=0
    private var switchingAutomatically=false
    private var playbackCourses by mutableStateOf(emptyList<Course>())
    private val unavailableCourses=mutableSetOf<String>()
    private var unavailableNoticeShown=false
    private var prefetchedCourse: Course?=null
    private var prefetchJob: Deferred<Result<OpenLesson>>?=null
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
    private var continuousActive=false
    internal var audioLoadCount=0;private set
    internal val usesContinuousAudio: Boolean get()=continuousActive
    internal val isAudioPlaying: Boolean get()=player.isPlaying
    private fun makeQueue(lesson: Lesson,start: Int)=if(continuousActive)continuousQueue(lesson,start) else practiceQueue(lesson,start,drill,playAll,repeatCount)
    private var queue=emptyList<Clip>();private var index=0;private var playAll=true;private var openJob: Job?=null;private var openToken=0
    private var taskStart=0
    private var timeline=TaskTimeline(emptyList(),false,false,1f)
    private var waitTotal=0f
    private val listening=ListeningMonitor(application) { removed ->
        if(removed&&opened!=null&&playback.running){pause();notify("耳机已断开 · 播放暂停")}
    }
    private val feedback=ListeningFeedback()
    private var foreground=false
    private val studyCloud=StudyCloud()
    private val studyDevice=prefs.getString("study-device",null)?:java.util.UUID.randomUUID().toString().replace("-","").also {prefs.edit().putString("study-device",it).apply()}
    private var studyKey=prefs.getString("study-key",null)?:studySecret().also {prefs.edit().putString("study-key",it).apply()}
    private var studySession=prefs.getString("study-session",null)
    private var studyAccount by mutableStateOf(prefs.getString("study-account",null))
    val passkeyLinked: Boolean get()=studyAccount!=null
    private val studyCacheIdentity: String get()=studyAccount?.let {"account:$it"}?:studyKey
    private val studyAuthorization: String get()=studySession?:studyKey
    var passkeyBusy by mutableStateOf(false);private set
    private var autoRestoreAttempted=false
    private val passkeys=PasskeyClient(BuildConfig.CONTENT_BASE_URL)
    fun restoreWithPasskey(activity: android.app.Activity,ticket: String?=null,automatic: Boolean=false){
        if(passkeyBusy||automatic&&(autoRestoreAttempted||passkeyLinked&&studySession!=null))return
        autoRestoreAttempted=true;passkeyBusy=true
        viewModelScope.launch {
            try {
                while(!studyReady)delay(25)
                studySyncJob?.cancel();studySyncJob?.join()
                val session=if(ticket!=null)passkeys.register(activity,ticket) else try {
                    passkeys.restore(activity)
                }catch(e: Exception){
                    // A privately provisioned legacy alias proves this existing installation
                    // owns the recovered profile. Unknown/fresh installs cannot enroll here.
                    if(!passkeyLinked&&(e is androidx.credentials.exceptions.NoCredentialException||e is PasskeyUnavailable&&e.status==404))passkeys.register(activity,token=studyKey)
                    else throw e
                }
                // Persist before adopting the identity. A cancelled prompt never changes the cache.
                withContext(Dispatchers.IO){check(prefs.edit().putString("study-session",session.token).putString("study-account",session.account).putLong("study-session-expires",session.expires).commit())}
                studySession=session.token;studyAccount=session.account
                remoteStudy=withContext(Dispatchers.IO){studyStore.readRemote(studyCacheIdentity)}
                studyRevision++;passkeyBusy=false;syncStudy(true)
            }catch(e: kotlinx.coroutines.CancellationException){throw e}
            catch(e: Exception){if(!automatic)notify(passkeyMessage(e),true)}
            finally {passkeyBusy=false}
        }
    }
    var studySyncing by mutableStateOf(false);private set
    var studySyncStatus by mutableStateOf("待同步");private set
    var studyRevision by mutableIntStateOf(0);private set
    private var remoteStudy=emptyList<StudyContribution>()
    private var studySyncJob: Job?=null
    fun syncStudy(explicit: Boolean=false,restore: String?=null){
        if(!studyReady||studySyncing||passkeyBusy)return
        if(passkeyLinked&&studySession==null){studySyncStatus="请验证身份";return}
        val key=restore?.lowercase()?.filterNot {it=='-'||it.isWhitespace()}?:studyAuthorization
        if(!key.matches(Regex("(?:session\\.)?[a-f0-9]{64}"))){notify("同步凭据不可用",true);return}
        val cacheIdentity=if(restore!=null)key else studyCacheIdentity
        sampleStudy();flushStudy();val local=studyLedger.snapshot()
        studySyncing=true;studySyncStatus="同步中"
        studySyncJob=viewModelScope.launch {
            try {
                val rows=withContext(Dispatchers.IO){
                    // Restoration must find an existing account before registering/uploading anything.
                    if(restore!=null)studyCloud.download(key)
                    studyStore.write(local)
                    studyCloud.upload(key,studyDevice,local)
                    studyCloud.download(key).also {studyStore.cacheRemote(cacheIdentity,it)}
                }
                if(restore!=null){withContext(Dispatchers.IO){check(prefs.edit().putString("study-key",key).commit())};studyKey=key}
                remoteStudy=rows;studyRevision++;studySyncStatus=if(studyLedger.snapshot()==local)"已同步" else "待同步"
                if(explicit)notify(if(restore!=null)"学习记录已恢复" else "学习记录已同步")
            }catch(e: kotlinx.coroutines.CancellationException){throw e}
            catch(e: StudyAuthenticationRequired){
                if(studyAccount!=null){studySession=null;withContext(Dispatchers.IO){prefs.edit().remove("study-session").commit()}}
                studySyncStatus="请验证身份";if(explicit)notify("请用通行密钥验证后继续同步")
            }
            catch(e: Exception){studySyncStatus="待同步";if(explicit)notify(if(restore!=null)"恢复失败，请检查恢复码和网络" else "暂未同步，记录已保存在本机",true)}
            finally{studySyncing=false}
        }
    }
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
        if(studyReady&&data!=null&&clip!=null&&p.running&&!p.paused&&!courseChanging){
            if(p.waiting){
                val remaining=waitClock.remaining(now)
                studyWaitRemaining?.let {before->if(shadow)follow=minOf(elapsed,((before-remaining).coerceAtLeast(0f)*1000).toLong())}
                studyWaitRemaining=remaining;studyMeter.reset()
            }else{
                studyWaitRemaining=null
                if(player.isPlaying||player.playbackState==Player.STATE_ENDED){
                    val measured=if(continuousActive)studyMeter.advance(player.currentPosition,elapsed,0,((data.lesson.continuous?.duration?:0.0)*1000).toLong())
                        else studyMeter.advance(player.currentPosition,elapsed,(clip.lead*1000).toLong(),((clip.duration-.12)*1000).toLong().coerceAtLeast((clip.lead*1000).toLong()))
                    if(volumeBand(listening.environment())!=VolumeBand.MUTED)audio=measured
                }else studyMeter.reset(player.currentPosition)
            }
            if(audio+follow>0){
                // Anchor to monotonic elapsed time when the wall clock is adjusted.
                val end=if(kotlin.math.abs(wall-previousWall-elapsed)<=2000)wall else previousWall+elapsed
                studyLedger.credit(data.course.id,previousWall,end,audio,follow,java.time.ZoneId.systemDefault());studyDirty=true;if(!studySyncing&&studySyncStatus!="待同步")studySyncStatus="待同步"
            }
        }else{studyMeter.reset();studyWaitRemaining=null}
    }
    private fun flushStudy(){if(studyReady&&studyDirty){studyDirty=false;studyWrites.trySend(studyLedger.takeChanges())}}
    fun studySnapshot(): List<StudyDay>{sampleStudy();flushStudy();return mergeStudy(studyDevice,studyLedger.snapshot(),remoteStudy)}

    init {
        viewModelScope.launch {
            studyLedger=StudyLedger(withContext(Dispatchers.IO){check(prefs.edit().putString("study-device",studyDevice).putString("study-key",studyKey).commit());studyStore.read()});remoteStudy=withContext(Dispatchers.IO){studyStore.readRemote(studyCacheIdentity)};studyReady=true;studyRevision++;syncStudy()
            for(rows in studyWrites)withContext(Dispatchers.IO){studyStore.write(rows)}
        }
        viewModelScope.launch {while(true){delay(60_000);syncStudy()}}
        viewModelScope.launch {var ticks=0;while(true){delay(500);sampleStudy();if(++ticks%20==0)flushStudy()}}
        player.addListener(object: Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {if(state==Player.STATE_ENDED&&playback.running&&!playback.waiting){if(continuousActive)finishContinuous() else enterWait()}}
            override fun onPlayerError(error: PlaybackException) {
                if(loopMode==PracticeLoop.ALL_COURSES){opened?.course?.id?.let {unavailableCourses.add(it)};changeCourse(1,true)}
                else {stop();notify("音频播放失败，请重新打开课程。",true)}
            }
            override fun onPlayWhenReadyChanged(ready: Boolean,reason: Int) {
                if(!ready&&(reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS||reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)&&playback.running)pause()
            }
        })
        viewModelScope.launch {
            while(true){delay(if(foreground)32 else 100);val now=android.os.SystemClock.elapsedRealtime()
                val state=playback
                if(state.running&&!state.paused&&!courseChanging){
                    if(state.waiting){val left=waitClock.remaining(now);if(left<=0)advance() else playback=state.copy(waitSeconds=left,taskProgress=taskProgress(1f,left))}
                    else if(player.isPlaying&&continuousActive)updateContinuousPosition(player.currentPosition)
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
    fun foreground(value: Boolean){foreground=value;if(!value){sampleStudy();flushStudy()};syncStudy()}
    private fun checkListening(){feedback.update(listening.environment(),android.os.SystemClock.elapsedRealtime())?.let {notify(it)}}
    fun notify(text: String,error: Boolean=false){messageIsError=error;message=text}
    fun consumeMessage(){message=null}
    fun sync(explicit: Boolean=true,quietSuccess: Boolean=false){if(syncing)return;syncing=true;viewModelScope.launch {
        try {val previous=courses;val next=repository.sync();courses=next;prepareNextCourse();if(explicit&&!quietSuccess)notify(if(next==previous)"已是最新" else "已更新课程")}catch(e: Exception){if(explicit)notify("同步失败，已缓存课程仍可使用。",true)}finally{syncing=false}
    }}
    private fun cancelCourseSwitch(){courseSwitchToken++;courseSwitchJob?.cancel();courseSwitchJob=null;courseChanging=false;switchingAutomatically=false}
    private fun cancelPrefetch(){prefetchJob?.cancel();prefetchJob=null;prefetchedCourse=null}
    private fun setArtwork(result: OpenLesson){
        artworkJob?.cancel();mediaArtwork=null
        artworkJob=viewModelScope.launch {
            val bytes=repository.mediaArtwork(result.course)
            if(opened===result){mediaArtwork=bytes;mediaChanged?.invoke()}
        }
    }
    fun open(course: Course){
        if(opened?.course?.id==course.id&&!readingVisible&&playback.running&&!courseChanging){
            openToken++;openJob?.cancel();loadingId=null;readingVisible=true;readingSessionId++;inlineCourseId=null;return
        }
        val wasChanging=courseChanging;cancelCourseSwitch();if(wasChanging)stopPlayback()
        val token=++openToken;openJob?.cancel();loadingId=course.id;openJob=viewModelScope.launch {
        try {val result=courseOpener(course);if(token!=openToken)return@launch
            stopPlayback();queue=emptyList();index=0;refreshTimeline();opened=result;readingVisible=true;readingSessionId++;inlineCourseId=null;if(loopMode!=PracticeLoop.ALL_COURSES)drill=false;feedback.reset()
            setArtwork(result)
            playback=Playback(selected=prefs.getInt("position.${course.id}",0).coerceIn(result.lesson.groups.indices))
            if(result.offlineFallback)notify("已打开本地课程")
        }catch(e: kotlinx.coroutines.CancellationException){throw e}catch(e: Exception){notify("课程下载失败，请稍后重试。",true)}finally{if(token==openToken)loadingId=null}
    }}
    fun back(){openToken++;openJob?.cancel();loadingId=null;stop();readingVisible=false;readingSessionId++;inlineCourseId=null;opened=null;artworkJob?.cancel();mediaArtwork=null;mediaChanged?.invoke()}
    private fun hideReading(){readingVisible=false;readingSessionId++;inlineCourseId=null}
    fun leaveReading(){
        if(loopMode==PracticeLoop.ALL_COURSES&&opened!=null){openToken++;openJob?.cancel();loadingId=null;hideReading()}
        else back()
    }
    fun finishBack(origin: OpenLesson?){if(opened===origin){
        if(loopMode==PracticeLoop.ALL_COURSES)hideReading()
        else {
            // Do not cancel a new manual open which is still downloading while
            // this visual return settles onto the library.
            stop();readingVisible=false;readingSessionId++;inlineCourseId=null;opened=null
            artworkJob?.cancel();mediaArtwork=null;mediaChanged?.invoke()
        }
    }}
    fun finishReadingBack(session: Long){if(session==readingSessionId)finishBack(opened)}
    private fun navigationCourses(): List<Course> =if(loopMode==PracticeLoop.ALL_COURSES&&playbackCourses.isNotEmpty())playbackCourses else courses
    fun canChangeCourse(direction: Int): Boolean {
        val id=opened?.course?.id?:return false
        val entries=navigationCourses();val at=entries.indexOfFirst {it.id==id}
        return at>=0&&(if(loopMode==PracticeLoop.ALL_COURSES)entries.isNotEmpty() else if(direction>0)at<entries.lastIndex else at>0)
    }
    private fun capturePlaybackCourses(){playbackCourses=courses.toList();unavailableCourses.clear();unavailableNoticeShown=false;cancelPrefetch()}
    private fun prepareNextCourse(){
        if(loopMode!=PracticeLoop.ALL_COURSES||opened==null)return
        val entries=navigationCourses();val id=courseNavigationOrder(entries.map {it.id},opened!!.course.id,1,true).firstOrNull {it !in unavailableCourses}?:return
        val next=courses.firstOrNull {it.id==id}?:entries.first {it.id==id}
        if(prefetchedCourse==next&&prefetchJob!=null)return
        cancelPrefetch();prefetchedCourse=next
        prefetchJob=viewModelScope.async {
            try {val result=courseOpener(next);repository.cover(next);Result.success(result)}
            catch(e: kotlinx.coroutines.CancellationException){throw e}
            catch(e: Exception){Result.failure(e)}
        }
    }
    fun changeCourse(direction: Int,automatic: Boolean=false){
        val origin=opened?:return
        if(automatic&&courseChanging)return
        if(!automatic){if(unavailableCourses.isNotEmpty())cancelPrefetch();unavailableCourses.clear();unavailableNoticeShown=false}
        if(loopMode==PracticeLoop.ALL_COURSES&&playbackCourses.none {it.id==origin.course.id})capturePlaybackCourses()
        val entries=navigationCourses().toList()
        val latest=courses.associateBy {it.id};val captured=entries.associateBy {it.id}
        val candidates=courseNavigationOrder(entries.map {it.id},origin.course.id,direction,loopMode==PracticeLoop.ALL_COURSES)
            .filter {it !in unavailableCourses}.map {id->latest[id]?:captured.getValue(id)}
        if(candidates.isEmpty()){
            if(automatic){stop();notify("暂无可播放课程，请检查网络后重试。",true)}
            return
        }
        cancelCourseSwitch();openToken++;openJob?.cancel();loadingId=null
        sampleStudy();audioStartToken++
        if(playback.waiting)playback=playback.copy(waitSeconds=waitClock.remaining(android.os.SystemClock.elapsedRealtime()))
        releaseWaitLock();player.pause()
        val token=++courseSwitchToken;courseChanging=true;switchingAutomatically=automatic;mediaChanged?.invoke()
        if(playback.running&&!playback.paused)waitLock.acquire(120_000)
        courseSwitchJob=viewModelScope.launch {
            var skipped=unavailableCourses.isNotEmpty()
            try {
                // One minute for the complete traversal, rather than a fresh
                // network timeout for every course in a large offline library.
                val result=withTimeout(60_000){
                    var available: OpenLesson?=null
                    for(course in candidates){
                        available=try {
                            if(prefetchedCourse==course&&prefetchJob!=null)prefetchJob!!.await().getOrThrow() else courseOpener(course)
                        }catch(e: kotlinx.coroutines.CancellationException){throw e}
                        catch(e: Exception){android.util.Log.w("CoursePlayback","Course unavailable: ${course.id}",e);unavailableCourses.add(course.id);skipped=true;continue}
                        break
                    }
                    available
                }
                if(token!=courseSwitchToken)return@launch
                if(result==null){stopPlayback();notify("暂时无法切换课程，请检查网络后重试。",true);return@launch}
                val play=playback.running&&!playback.paused
                stopPlayback();queue=emptyList();index=0;opened=result;inlineCourseId=if(readingVisible)result.course.id else null
                setArtwork(result);playback=Playback();courseChanging=false;switchingAutomatically=false
                start(0,playWhenReady=play,resetFailures=false)
                if(skipped&&!unavailableNoticeShown){unavailableNoticeShown=true;notify("部分课程暂不可用，已跳过")}
            }catch(e: TimeoutCancellationException){
                if(token==courseSwitchToken){stopPlayback();cancelPrefetch();notify("暂时无法切换课程，请检查网络后重试。",true)}
            }finally{if(token==courseSwitchToken){releaseWaitLock();courseChanging=false;switchingAutomatically=false;courseSwitchJob=null}}
        }
    }

    fun updateDrill(value: Boolean){if(drill==value)return;val resume=playback.running;val paused=playback.paused;stopPlayback();drill=value;mediaChanged?.invoke();playback=playback.copy(progress=0f);if(resume)start(playWhenReady=!paused)}
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
        val wasContinuous=continuousActive
        val source=currentSource();val paused=playback.paused
        sampleStudy()
        if(mode!=PracticeLoop.ALL_COURSES){cancelPrefetch();if(switchingAutomatically)stop()}
        else capturePlaybackCourses()
        loopMode=mode;prefs.edit().putString("loop-mode",mode.name).apply()
        val shouldContinuous=canUseContinuous()
        if(playback.running&&wasContinuous!=shouldContinuous){restartAtSource(source,paused);return}
        if(mode==PracticeLoop.LESSON&&playAll&&opened!=null&&queue.isNotEmpty()){
            val current=queue.getOrNull(index);val ended=index>=queue.size
            val updated=makeQueue(opened!!.lesson,0)
            index=if(ended)updated.size else updated.indexOfFirst {it.group==current?.group&&it.repeat==current?.repeat&&it.file==current?.file}.coerceAtLeast(0)
            queue=updated;taskStart=0
        }
        refreshTimeline();prepareNextCourse()
    }
    private fun canUseContinuous()=!drill&&!shadow&&playAll&&loopMode!=PracticeLoop.SENTENCE&&opened?.lesson?.continuous!=null
    private fun currentSource(): Double {
        if(continuousActive)return (opened?.lesson?.continuous?.sourceStart?:0.0)+player.currentPosition/1000.0
        val clip=queue.getOrNull(index)?:return playback.source
        return clip.sourceStart+player.currentPosition/1000.0-clip.lead
    }
    private fun restartAtSource(source: Double,paused: Boolean){
        val selected=playback.selected;val all=playAll
        start(selected,all,playWhenReady=!paused)
        val clip=queue.getOrNull(index)?:return
        if(continuousActive){
            val offset=((source-opened!!.lesson.continuous!!.sourceStart)*1000).toLong().coerceAtLeast(0)
            player.seekTo(offset);studyMeter.reset(offset);updateContinuousPosition(offset)
        }else{
            val offset=((source-clip.sourceStart+clip.lead)*1000).toLong().coerceIn(0,(clip.duration*1000).toLong())
            player.seekTo(offset);studyMeter.reset(offset)
            playback=playback.copy(progress=(offset/1000.0/clip.duration).toFloat(),source=source)
        }
        if(paused)pause()
    }
    fun updateShadow(v: Boolean){
        if(shadow==v)return
        val source=currentSource();val paused=playback.paused
        sampleStudy();shadow=v;prefs.edit().putBoolean("shadow",v).apply()
        if(playback.running&&continuousActive!=canUseContinuous())restartAtSource(source,paused) else refreshTimeline()
    }
    fun updateGap(v: Float){sampleStudy();gap=v;prefs.edit().putFloat("gap",v).apply();refreshTimeline()}
    fun start(selected: Int=playback.selected,all: Boolean=true,playWhenReady: Boolean=true,resetFailures: Boolean=true){
        val data=opened?:return
        if(resetFailures){cancelCourseSwitch();unavailableCourses.clear();unavailableNoticeShown=false}
        if(loopMode==PracticeLoop.ALL_COURSES&&playbackCourses.none {it.id==data.course.id})capturePlaybackCourses()
        stopPlayback();mediaTaskId++;playAll=all;taskStart=if(loopMode==PracticeLoop.LESSON&&all)0 else selected
        continuousActive=canUseContinuous();queue=makeQueue(data.lesson,taskStart);index=queue.indexOfFirst {it.group==selected}.coerceAtLeast(0);refreshTimeline()
        if(queue.isNotEmpty()){if(playWhenReady){ensurePlaybackService();checkListening()};begin(!playWhenReady);prepareNextCourse()}
    }
    fun previous(){start((playback.selected-1).coerceAtLeast(0),playWhenReady=!playback.running||!playback.paused)}
    fun next(){val last=opened?.lesson?.groups?.lastIndex?:return;start((playback.selected+1).coerceAtMost(last),playWhenReady=!playback.running||!playback.paused)}
    fun toggle(){if(courseChanging){val playing=playback.running&&!playback.paused;playback=playback.copy(running=true,paused=playing);if(playing)releaseWaitLock() else {ensurePlaybackService();if(!waitLock.isHeld)waitLock.acquire(120_000)};return};when{!playback.running->start();playback.paused->{sampleStudy();studyMeter.reset(player.currentPosition);studyWaitRemaining=playback.waitSeconds;checkListening();ensurePlaybackService();playback=playback.copy(paused=false);if(playback.waiting)scheduleWait() else playAudio()};else->pause()}}
    fun pause(){sampleStudy();flushStudy();syncStudy();audioStartToken++;if(playback.running&&!playback.paused){
        val remaining=if(playback.waiting&&!courseChanging)waitClock.remaining(android.os.SystemClock.elapsedRealtime()) else playback.waitSeconds
        playback=playback.copy(paused=true,waitSeconds=remaining);releaseWaitLock();player.pause()
    }}
    fun stop(){cancelCourseSwitch();cancelPrefetch();stopPlayback()}
    private fun stopPlayback(){sampleStudy();flushStudy();syncStudy();studyMeter.reset();studyWaitRemaining=null;audioStartToken++;releaseWaitLock();playback=playback.copy(running=false,paused=false,waiting=false,source=-1.0,repeat=0,taskProgress=null);player.stop()}
    fun seekMedia(positionMs: Long){
        if(queue.isEmpty()||opened==null||mediaTimeline.clipCount==0)return
        sampleStudy();studyMeter.reset();studyWaitRemaining=null
        if(continuousActive){seekContinuous(positionMs);return}
        val paused=!playback.running||playback.paused
        // The system exposes integer milliseconds; its displayed endpoint can
        // round down from the precise audio timeline by less than one millisecond.
        val point=if(positionMs>=mediaDurationMs)TaskPoint(mediaTimeline.clipCount,0.0,0.0,false)
            else mediaTimeline.locate(positionMs.coerceAtLeast(0)/1000.0)
        if(point.index>=mediaTimeline.clipCount){
            if(loopMode==PracticeLoop.ALL_COURSES){changeCourse(1,true);return}
            if(anyLoop){seekMedia(0);return}
            index=queue.size;stop();playback=playback.copy(selected=queue.last().group,progress=1f,taskProgress=if(timeline.spansSentences)1f else null);mediaChanged?.invoke();return
        }
        audioStartToken++;releaseWaitLock();player.pause()
        index=mediaOffset+point.index
        begin(paused)
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
    private fun begin(paused: Boolean=false){studyMeter.reset(0);studyWaitRemaining=null;releaseWaitLock();val data=opened?:return;val clip=queue.getOrNull(index)?:return
        if(paused)player.pause()
        playback=Playback(clip.group,true,paused,0f,-1.0,if(drill)clip.repeat else 0,clip.total,whole=clip.whole,taskProgress=taskProgress(0f))
        prefs.edit().putInt("position.${data.course.id}",clip.group).apply()
        if(continuousActive){
            val audio=data.lesson.continuous!!;val position=((clip.sourceStart-audio.sourceStart)*1000).toLong()
            val id="continuous-${data.course.id}-${data.course.version}"
            if(player.currentMediaItem?.mediaId==id&&player.playbackState!=Player.STATE_IDLE)player.seekTo(position)
            else {audioLoadCount++;player.setMediaItem(MediaItem.Builder().setMediaId(id).setUri(repository.audio(data,audio.audioFile)).build(),position);player.prepare()}
            studyMeter.reset(position);updateContinuousPosition(position)
        }else {audioLoadCount++;player.setMediaItem(MediaItem.fromUri(repository.audio(data,clip.file)));player.prepare()}
        if(paused)player.pause() else playAudio()
    }
    private fun updateContinuousPosition(positionMs: Long){
        val audio=opened?.lesson?.continuous?:return
        val source=audio.sourceStart+positionMs/1000.0
        index=continuousClipIndex(queue,source)
        val clip=queue.getOrNull(index)?:return
        val progress=((source-clip.sourceStart)/clip.duration).toFloat().coerceIn(0f,1f)
        if(playback.selected!=clip.group)prefs.edit().putInt("position.${opened!!.course.id}",clip.group).apply()
        playback=playback.copy(selected=clip.group,progress=progress,source=source,waiting=false,waitSeconds=0f,taskProgress=taskProgress(progress))
    }
    private fun finishContinuous(){
        sampleStudy()
        if(loopMode==PracticeLoop.LESSON){mediaDiscontinuity++;index=0;begin()}
        else if(loopMode==PracticeLoop.ALL_COURSES){changeCourse(1,true)}
        else {index=queue.size;stop();playback=playback.copy(selected=queue.last().group,progress=1f,taskProgress=if(timeline.spansSentences)1f else null)}
    }
    private fun seekContinuous(positionMs: Long){
        val paused=!playback.running||playback.paused
        if(positionMs>=mediaDurationMs){
            if(loopMode==PracticeLoop.LESSON){seekContinuous(0);return}
            finishContinuous();return
        }
        val audio=opened!!.lesson.continuous!!
        val absolute=((queue.first().sourceStart-audio.sourceStart)*1000).toLong()+positionMs.coerceAtLeast(0)
        audioStartToken++;releaseWaitLock();player.pause()
        // Re-prepare only after an explicit stop or after completing the task.
        if(player.playbackState==Player.STATE_IDLE){index=continuousClipIndex(queue,audio.sourceStart+absolute/1000.0);begin()}
        player.seekTo(absolute);studyMeter.reset(absolute)
        playback=playback.copy(running=true,paused=paused)
        updateContinuousPosition(absolute)
        if(!paused){ensurePlaybackService();playAudio()}
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
        if(loopMode==PracticeLoop.ALL_COURSES){changeCourse(1,true);return}
        stop();playback=playback.copy(progress=1f,taskProgress=if(!loop&&timeline.spansSentences)1f else null)
    }
    private fun taskProgress(clipProgress: Float,waitLeft: Float?=null): Float? {
        if(loop||!timeline.spansSentences)return null
        val clip=queue.getOrNull(index)?:return 1f
        return timeline.progress(index,clip.duration*clipProgress.coerceIn(0f,1f),
            if(waitLeft==null)0.0 else (waitTotal-waitLeft).coerceAtLeast(0f).toDouble())
    }
    private fun refreshTimeline(){
        timeline=TaskTimeline(queue,drill,shadow,gap,!continuousActive&&loopMode==PracticeLoop.LESSON)
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
            if(!playback.paused&&!courseChanging)scheduleWait()
        }
        playback=playback.copy(taskProgress=taskProgress(playback.progress,if(playback.waiting)playback.waitSeconds else null))
    }
    override fun onCleared(){releaseWaitLock();listening.close();player.release();super.onCleared()}
}
