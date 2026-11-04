package com.rachelsenglish.practice

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AacPlaybackInstrumentedTest {
 @Test fun aacLessonSupportsPreparationSeekingAndPlayback() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val sample=java.io.File(instrumentation.targetContext.cacheDir,"aac-playback-sample.m4a")
  instrumentation.context.assets.open("aac-playback-sample.m4a").use { input->sample.outputStream().use { input.copyTo(it) } }
  val ready=CountDownLatch(1);val ended=CountDownLatch(1)
  var player: ExoPlayer?=null
  try {
   instrumentation.runOnMainSync {
    player=ExoPlayer.Builder(instrumentation.targetContext).build().apply {
     addListener(object: Player.Listener {
      override fun onPlaybackStateChanged(state: Int) {
       if(state==Player.STATE_READY)ready.countDown()
       if(state==Player.STATE_ENDED)ended.countDown()
      }
     })
     setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(sample)));prepare()
    }
   }
   assertTrue("AAC course must prepare",ready.await(10,TimeUnit.SECONDS))
   instrumentation.runOnMainSync {
    val p=player!!
    assertTrue("AAC duration must preserve course timing",kotlin.math.abs(p.duration-3500)<60)
    p.seekTo(1000)
    assertFalse("Seek must preserve paused state",p.playWhenReady)
    p.play()
   }
   assertTrue("AAC course must play through to its end",ended.await(10,TimeUnit.SECONDS))
  } finally {
   instrumentation.runOnMainSync {player?.release()};sample.delete()
  }
 }
}
