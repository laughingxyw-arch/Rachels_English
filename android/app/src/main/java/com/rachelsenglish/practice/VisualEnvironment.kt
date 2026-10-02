package com.rachelsenglish.practice

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.*
import android.database.ContentObserver
import android.os.*
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner

data class VisualEnvironment(val reducedMotion: Boolean=false,val highContrast: Boolean=false,
    val powerSaver: Boolean=false,val lowRam: Boolean=false,val api: Int=Build.VERSION.SDK_INT)
data class MaterialStyle(val frosted: Boolean=false,val highContrast: Boolean=false,val reducedMotion: Boolean=false)
fun materialStyle(environment: VisualEnvironment,reduceTransparency: Boolean,enhanceContrast: Boolean): MaterialStyle {
    val contrast=environment.highContrast||enhanceContrast
    return MaterialStyle(environment.api>=31&&!environment.powerSaver&&!environment.lowRam&&!reduceTransparency&&!contrast,contrast,environment.reducedMotion)
}
val LocalMaterialStyle=staticCompositionLocalOf {MaterialStyle()}

@Composable fun rememberVisualEnvironment(): VisualEnvironment {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
    fun read(): VisualEnvironment {
        val resolver=context.contentResolver
        val accessibility=context.getSystemService(AccessibilityManager::class.java)
        val contrast=if(Build.VERSION.SDK_INT>=36)accessibility.isHighContrastTextEnabled else Settings.Secure.getInt(resolver,"high_text_contrast_enabled",0)==1
        val colorContrast=if(Build.VERSION.SDK_INT>=34)context.getSystemService(UiModeManager::class.java).contrast>=.5f else false
        return VisualEnvironment(Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f,
            contrast||colorContrast,context.getSystemService(PowerManager::class.java).isPowerSaveMode,
            context.getSystemService(ActivityManager::class.java).isLowRamDevice)
    }
    var environment by remember {mutableStateOf(read())}
    DisposableEffect(context,lifecycle) {
        fun refresh(){environment=read()}
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {override fun onChange(selfChange: Boolean){refresh()}}
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        context.contentResolver.registerContentObserver(Settings.Secure.getUriFor("high_text_contrast_enabled"),false,observer)
        val receiver=object: BroadcastReceiver(){override fun onReceive(context: Context?,intent: Intent?){refresh()}}
        val filter=IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED) else context.registerReceiver(receiver,filter)
        val lifecycleObserver=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME)refresh()}
        lifecycle.lifecycle.addObserver(lifecycleObserver)
        val clearContrast=if(Build.VERSION.SDK_INT>=34){
            val manager=context.getSystemService(UiModeManager::class.java)
            val listener=UiModeManager.ContrastChangeListener {refresh()}
            manager.addContrastChangeListener(context.mainExecutor,listener)
            val cleanup: ()->Unit={manager.removeContrastChangeListener(listener)};cleanup
        } else {{}}
        val clearText=if(Build.VERSION.SDK_INT>=36){
            val manager=context.getSystemService(AccessibilityManager::class.java)
            val listener=AccessibilityManager.HighContrastTextStateChangeListener {refresh()}
            manager.addHighContrastTextStateChangeListener(context.mainExecutor,listener)
            val cleanup: ()->Unit={manager.removeHighContrastTextStateChangeListener(listener)};cleanup
        } else {{}}
        onDispose {context.contentResolver.unregisterContentObserver(observer);context.unregisterReceiver(receiver);lifecycle.lifecycle.removeObserver(lifecycleObserver);clearContrast();clearText()}
    }
    return environment
}
