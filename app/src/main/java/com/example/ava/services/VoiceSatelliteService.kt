package com.example.ava.services

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.ava.esphome.VoiceSatellite
import com.example.ava.microwakeword.AssetWakeWordProvider
import com.example.ava.notifications.createVoiceSatelliteServiceNotification
import com.example.ava.players.TtsPlayer
import com.example.ava.preferences.VoiceAssistantPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

class VoiceSatelliteService() : LifecycleService() {

    private val settingsStore = VoiceAssistantPreferencesStore(this)
    private val _voiceSatelliteStateFlow = MutableStateFlow<VoiceSatellite?>(null)
    val voiceSatelliteStateFlow = _voiceSatelliteStateFlow.asStateFlow()

    fun startVoiceSatellite() {
        val serviceIntent = Intent(this, this::class.java)
        applicationContext.startForegroundService(serviceIntent)
    }

    fun stopVoiceSatellite() {
        val satellite = _voiceSatelliteStateFlow.getAndUpdate { null }
        if (satellite != null) {
            Log.d(TAG, "Stopping voice satellite")
            satellite.close()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    class VoiceSatelliteBinder(val service: VoiceSatelliteService) : Binder()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return VoiceSatelliteBinder(this)
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleScope.launch {
            // already started?
            if (_voiceSatelliteStateFlow.value == null) {
                Log.d(TAG, "Starting voice satellite")
                startForeground(
                    2,
                    createVoiceSatelliteServiceNotification(this@VoiceSatelliteService)
                )
                val settings = settingsStore.getSettings()
                val wakeWordProvider = AssetWakeWordProvider(assets)
                val ttsPlayer = TtsPlayer(this@VoiceSatelliteService)
                val satellite = VoiceSatellite(
                    lifecycleScope.coroutineContext,
                    settings,
                    wakeWordProvider,
                    ttsPlayer
                )
                _voiceSatelliteStateFlow.value = satellite
                satellite.start()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        _voiceSatelliteStateFlow.getAndUpdate { null }?.close()
        super.onDestroy()
    }

    companion object {
        const val TAG = "VoiceSatelliteService"
    }
}