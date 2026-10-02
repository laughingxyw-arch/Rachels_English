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
        val player=PracticeSessionPlayer(model)
        sessionPlayer=player
        session=MediaSession.Builder(this,player)
            .setSessionActivity(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
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
private class PracticeSessionPlayer(private val model: PracticeModel): SimpleBasePlayer(Looper.getMainLooper()) {
    private var lesson: OpenLesson?=null
    private var mode: Boolean?=null
    private var playlist=emptyList<MediaItemData>()
    init {model.mediaChanged={invalidateState()}}
    override fun getState(): State {
        val data=model.opened
        if(lesson!==data||mode!=model.drill){
            lesson=data;mode=model.drill
            playlist=data?.lesson?.groups?.map { sentence ->
                val text=sentence.phrases.joinToString(" "){it.text}
                val metadata=MediaMetadata.Builder().setTitle(text)
                    .setArtist("${data.course.title} · ${if(model.drill)"Drill" else "原句"}")
                    .setAlbumTitle(data.course.title).build()
                MediaItemData.Builder("${data.course.id}:${sentence.id}")
                    .setMediaItem(MediaItem.Builder().setMediaId("${data.course.id}:${sentence.id}")
                        .setMediaMetadata(metadata).build()).build()
            }?:emptyList()
        }
        val p=model.playback
        val commands=Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE,Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,Player.COMMAND_GET_CURRENT_MEDIA_ITEM,Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,Player.COMMAND_RELEASE)
        if(p.selected>0)commands.addAll(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_SEEK_TO_PREVIOUS)
        if(p.selected<playlist.lastIndex)commands.addAll(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,Player.COMMAND_SEEK_TO_NEXT)
        return State.Builder().setAvailableCommands(commands.build())
            .setPlaylist(playlist).setCurrentMediaItemIndex(if(playlist.isEmpty())0 else p.selected)
            .setPlaybackState(if(p.running&&playlist.isNotEmpty())Player.STATE_READY else Player.STATE_IDLE)
            .setPlayWhenReady(p.running&&!p.paused,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs {model.mediaPositionMs}.build()
    }
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if(playWhenReady){if(!model.playback.running||model.playback.paused)model.toggle()}else model.pause()
        return Futures.immediateVoidFuture()
    }
    override fun handlePrepare(): ListenableFuture<*> =Futures.immediateVoidFuture()
    override fun handleStop(): ListenableFuture<*>{model.stop();return Futures.immediateVoidFuture()}
    override fun handleRelease(): ListenableFuture<*>{model.mediaChanged=null;return Futures.immediateVoidFuture()}
    override fun handleSeek(mediaItemIndex: Int,positionMs: Long,seekCommand: Int): ListenableFuture<*> {
        val paused=model.playback.paused
        when(seekCommand){
            Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM->model.previous()
            Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM->model.next()
        }
        if(paused)model.pause()
        return Futures.immediateVoidFuture()
    }
}
