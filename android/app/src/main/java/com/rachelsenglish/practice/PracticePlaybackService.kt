package com.rachelsenglish.practice

import android.app.PendingIntent
import android.content.Intent
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PracticePlaybackService: MediaSessionService() {
    private var session: MediaSession?=null
    private var sessionPlayer: PracticeSessionPlayer?=null
    override fun onCreate(){
        super.onCreate()
        val model=(application as PracticeApplication).model
        val player=PracticeSessionPlayer(model){
            // Android 15+ grants audio focus only to a visible app or a foreground service.
            // Publish the resumed logical task before the clip requests focus.
            if(model.playback.running&&!model.playback.paused)session?.let {onUpdateNotification(it,true)}
        }
        sessionPlayer=player
        session=MediaSession.Builder(this,player)
            .setSessionActivity(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        addSession(session!!)
        if(model.playback.running&&!model.playback.paused)onUpdateNotification(session!!,true)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo)=session
    override fun onDestroy(){
        (application as PracticeApplication).model.stop()
        session?.release();session=null
        sessionPlayer?.release();sessionPlayer=null
        super.onDestroy()
    }
}

// Report the whole listening task as playing, including deliberate silent gaps.
// Exposing the clip ExoPlayer directly would report ENDED between every repeat.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class PracticeSessionPlayer(private val model: PracticeModel,private val onTaskStateChanged: () -> Unit): SimpleBasePlayer(Looper.getMainLooper()) {
    private var lastDiscontinuity=0
    private var itemKey: List<Any?> =emptyList()
    private var playlist=emptyList<MediaItemData>()
    init {model.mediaChanged={invalidateState();onTaskStateChanged()}}
    override fun getState(): State {
        val data=model.opened;val p=model.playback
        val duration=model.mediaDurationMs
        val key=listOf(data,model.mediaTaskId,model.drill,model.repeatCount,p.selected,duration,model.mediaArtwork)
        if(itemKey!=key){
            itemKey=key
            playlist=if(data==null||duration<=0)emptyList() else {
                val text=data.lesson.groups[p.selected].phrases.joinToString(" "){it.text}
                val metadata=MediaMetadata.Builder().setTitle(text)
                    .setArtist("${data.course.title} · ${if(model.drill)"Drill · ${model.repeatCount}×" else "原句"}")
                    .setAlbumTitle(data.course.title)
                    .setArtworkData(model.mediaArtwork,MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
                // One system track represents one complete task. Clip/repeat changes
                // update metadata and position without replacing its stable identity.
                val id="${data.course.id}:task:${model.mediaTaskId}"
                listOf(MediaItemData.Builder(id).setDurationUs(duration*1000).setIsSeekable(true)
                    .setMediaItem(MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()).build())
            }
        }
        val commands=Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE,Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,Player.COMMAND_GET_CURRENT_MEDIA_ITEM,Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,Player.COMMAND_RELEASE)
        if(duration>0)commands.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        if(p.selected>0)commands.addAll(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_SEEK_TO_PREVIOUS)
        if(p.selected<(data?.lesson?.groups?.lastIndex?:0))commands.addAll(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,Player.COMMAND_SEEK_TO_NEXT)
        val state=State.Builder().setAvailableCommands(commands.build())
            .setPlaylist(playlist).setCurrentMediaItemIndex(0)
            .setPlaybackState(if(p.running&&playlist.isNotEmpty())Player.STATE_READY else if(model.mediaEnded&&playlist.isNotEmpty())Player.STATE_ENDED else Player.STATE_IDLE)
            .setPlayWhenReady(p.running&&!p.paused,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs {model.mediaPositionMs}
        if(lastDiscontinuity!=model.mediaDiscontinuity){lastDiscontinuity=model.mediaDiscontinuity;state.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION,model.mediaPositionMs)}
        return state.build()
    }
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if(playWhenReady){if(!model.playback.running||model.playback.paused)model.toggle()}else model.pause()
        return Futures.immediateVoidFuture()
    }
    override fun handlePrepare(): ListenableFuture<*> =Futures.immediateVoidFuture()
    override fun handleStop(): ListenableFuture<*>{model.stop();return Futures.immediateVoidFuture()}
    override fun handleRelease(): ListenableFuture<*>{model.mediaChanged=null;return Futures.immediateVoidFuture()}
    override fun handleSeek(mediaItemIndex: Int,positionMs: Long,seekCommand: Int): ListenableFuture<*> {
        if(seekCommand==Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM){model.seekMedia(positionMs);return Futures.immediateVoidFuture()}
        val paused=model.playback.paused
        when(seekCommand){
            Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM->model.previous()
            Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM->model.next()
        }
        if(paused)model.pause()
        return Futures.immediateVoidFuture()
    }
}
