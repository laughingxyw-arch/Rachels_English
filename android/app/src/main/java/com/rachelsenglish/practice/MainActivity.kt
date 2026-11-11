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
        handleSetup(intent)
        setContent { PracticeApp(model) }
    }
    private fun handleSetup(incoming: android.content.Intent){
        if(incoming.getBooleanExtra("passkey-setup-consumed",false))return
        val ticket=passkeySetupTicket(incoming)
        if(ticket!=null){
            incoming.removeExtra("enrollment");incoming.putExtra("passkey-setup-consumed",true)
            window.decorView.post {model.restoreWithPasskey(this,ticket)}
        }
    }
    override fun onNewIntent(intent: android.content.Intent){super.onNewIntent(intent);setIntent(intent);handleSetup(intent)}
    private fun updateSystemBars(configuration: android.content.res.Configuration=resources.configuration){
        // During a live configuration callback the decor view can still expose its old
        // Resources. Resolve night mode from the delivered configuration, not the decor.
        val dark=(configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES
        val transparent=android.graphics.Color.TRANSPARENT
        val bars=if(dark)SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent,transparent)
        enableEdgeToEdge(statusBarStyle=bars,navigationBarStyle=bars)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(if(dark)0xff10141b.toInt() else 0xfff7f8fb.toInt()))
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration){super.onConfigurationChanged(newConfig);updateSystemBars(newConfig)}
    override fun onResume(){super.onResume();model.foreground(true);window.decorView.post {if(!isFinishing)model.restoreWithPasskey(this,automatic=true)}}
    override fun onStop(){model.foreground(false);super.onStop()}
}
