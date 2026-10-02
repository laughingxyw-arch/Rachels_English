package com.rachelsenglish.practice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle

class MainActivity: ComponentActivity() {
    private lateinit var model: PracticeModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream=android.media.AudioManager.STREAM_MUSIC
        updateSystemBars()
        model=(application as PracticeApplication).model
        setContent { PracticeApp(model) }
    }
    private fun updateSystemBars(){
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        val dark=resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(if(dark)0xff10141b.toInt() else 0xfff7f8fb.toInt()))
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration){super.onConfigurationChanged(newConfig);updateSystemBars()}
    override fun onResume(){super.onResume();model.foreground(true)}
    override fun onStop(){model.foreground(false);super.onStop()}
}
