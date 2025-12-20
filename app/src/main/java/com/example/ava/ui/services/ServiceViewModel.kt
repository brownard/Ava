package com.example.ava.ui.services

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.example.ava.services.VoiceSatelliteService
import com.example.ava.settings.VoiceSatelliteSettingsStore
import com.example.ava.settings.voiceSatelliteSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ServiceViewModel(application: Application) : AndroidViewModel(application) {
    private var created = false
    private val settings = VoiceSatelliteSettingsStore(application.voiceSatelliteSettingsStore)

    private val _satellite = MutableStateFlow<VoiceSatelliteService?>(null)
    val satellite = _satellite.asStateFlow()

    private val serviceConnection = bindService(application) {
        _satellite.value = it
    }

    override fun onCleared() {
        application.unbindService(serviceConnection)
        super.onCleared()
    }

    private fun bindService(
        context: Context,
        connectedChanged: (VoiceSatelliteService?) -> Unit
    ): ServiceConnection {
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                (binder as? VoiceSatelliteService.VoiceSatelliteBinder)?.let {
                    connectedChanged(it.service)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connectedChanged(null)
            }
        }
        val serviceIntent = Intent(context, VoiceSatelliteService::class.java)
        val bound = context.bindService(
            serviceIntent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        if (!bound)
            Log.e(TAG, "Cannot bind to VoiceAssistantService")
        return serviceConnection
    }

    fun autoStartServiceIfRequired() {
        if (created)
            return
        created = true
        viewModelScope.launch {
            val autoStart = settings.autoStart.get()
            if (autoStart)
                _satellite.dropWhile { it == null }.first()?.startVoiceSatellite()
        }
    }

    companion object {
        private const val TAG = "ServiceViewModel"
    }
}