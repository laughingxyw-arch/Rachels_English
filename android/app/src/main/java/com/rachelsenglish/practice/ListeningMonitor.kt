package com.rachelsenglish.practice

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

// Connected devices only: do not infer that a particular Bluetooth output is active.
class ListeningMonitor(context: Context,private val changed: (removed: Boolean)->Unit) {
    private val audio=context.getSystemService(AudioManager::class.java)
    private fun connected()=audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {device ->
        device.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_HEARING_AID,AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
    private var headphones=connected()
    private val callback=object: AudioDeviceCallback() {
        private fun refresh(){val value=connected();val removed=headphones&&!value;headphones=value;changed(removed)}
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>){refresh()}
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>){refresh()}
    }
    init {audio.registerAudioDeviceCallback(callback,Handler(Looper.getMainLooper()))}
    fun environment()=ListeningEnvironment(headphones,audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),audio.isVolumeFixed)
    fun close(){audio.unregisterAudioDeviceCallback(callback)}
}
