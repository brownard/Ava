package com.example.ava.ui.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ava.services.VoiceSatelliteService
import com.example.ava.settings.VoiceSatelliteSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ServiceViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: VoiceSatelliteSettingsStore,
) : ViewModel() {
    private var created = false

    private val _satellite = MutableStateFlow<VoiceSatelliteService?>(null)
    val satellite = _satellite.asStateFlow()

    val voiceTimers = _satellite.flatMapLatest { service ->
        service?.voiceTimers ?: flowOf(emptyList())
    }

    val satelliteState = _satellite.flatMapLatest { service ->
        service?.voiceSatelliteState ?: flowOf(null)
    }

    private val serviceConnection = bindService(context) {
        _satellite.value = it
    }

    override fun onCleared() {
        context.unbindService(serviceConnection)
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
            Timber.e("Cannot bind to VoiceAssistantService")
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
}
