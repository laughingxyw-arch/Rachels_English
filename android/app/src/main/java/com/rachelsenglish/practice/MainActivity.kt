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
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        model=(application as PracticeApplication).model
        setContent { PracticeApp(model) }
    }
    override fun onResume(){super.onResume();model.foreground(true)}
    override fun onStop(){model.foreground(false);super.onStop()}
}
