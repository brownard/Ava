package com.example.ava.services

import android.app.NotificationManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.ava.esphome.EspHomeDevice
import com.example.ava.esphome.Stopped
import com.example.ava.notifications.createVoiceSatelliteServiceNotification
import com.example.ava.notifications.createVoiceSatelliteServiceNotificationChannel
import com.example.ava.nsd.NsdRegistration
import com.example.ava.nsd.registerVoiceSatelliteNsd
import com.example.ava.settings.MicrophoneSettingsStore
import com.example.ava.settings.PlayerSettingsStore
import com.example.ava.settings.VoiceSatelliteSettings
import com.example.ava.settings.VoiceSatelliteSettingsStore
import com.example.ava.tasker.ActivityConfigAvaActivity
import com.example.ava.tasker.AvaActivityRunner
import com.example.ava.utils.translate
import com.example.ava.wakelocks.WifiWakeLock
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class VoiceSatelliteService() : LifecycleService() {
    @Inject
    lateinit var satelliteSettingsStore: VoiceSatelliteSettingsStore

    @Inject
    lateinit var microphoneSettingsStore: MicrophoneSettingsStore

    @Inject
    lateinit var playerSettingsStore: PlayerSettingsStore

    @Inject
    lateinit var deviceBuilder: DeviceBuilder

    private val wifiWakeLock = WifiWakeLock()
    private var voiceSatelliteNsd = AtomicReference<NsdRegistration?>(null)
    private val _voiceSatellite = MutableStateFlow<EspHomeDevice?>(null)

    val voiceSatelliteState = _voiceSatellite.flatMapLatest {
        it?.voiceAssistant?.state ?: flowOf(Stopped)
    }

    val voiceTimers = _voiceSatellite.flatMapLatest {
        it?.voiceAssistant?.allTimers ?: flowOf(listOf())
    }

    fun startVoiceSatellite() {
        val serviceIntent = Intent(this, this::class.java)
        applicationContext.startForegroundService(serviceIntent)
    }

    fun stopVoiceSatellite() {
        val satellite = _voiceSatellite.getAndUpdate { null }
        if (satellite != null) {
            Timber.d("Stopping voice satellite")
            satellite.close()
            voiceSatelliteNsd.getAndSet(null)?.unregister(this)
            wifiWakeLock.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        wifiWakeLock.create(applicationContext, TAG)
        createVoiceSatelliteServiceNotificationChannel(this)
        updateNotificationOnStateChanges()
        startSettingsWatcher()
        startTaskerStateObserver()
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
            if (_voiceSatellite.value == null) {
                Timber.d("Starting voice satellite")
                startForeground(
                    2,
                    createVoiceSatelliteServiceNotification(
                        this@VoiceSatelliteService,
                        Stopped.translate(resources)
                    )
                )
                satelliteSettingsStore.ensureMacAddressIsSet()
                val settings = satelliteSettingsStore.get()
                _voiceSatellite.value =
                    deviceBuilder.buildVoiceSatellite(lifecycleScope.coroutineContext)
                        .apply { start() }
                voiceSatelliteNsd.set(registerVoiceSatelliteNsd(settings))
                wifiWakeLock.acquire()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startSettingsWatcher() {
        _voiceSatellite.flatMapLatest { satellite ->
            if (satellite == null) emptyFlow()
            else merge(
                // Update settings when satellite changes,
                // dropping the initial value to avoid overwriting
                // settings with the initial/default values
                satellite.voiceAssistant.voiceInput.activeWakeWords.drop(1).onEach {
                    microphoneSettingsStore.wakeWord.set(it.firstOrNull().orEmpty())
                    microphoneSettingsStore.secondWakeWord.set(it.elementAtOrNull(1))
                },
                satellite.voiceAssistant.voiceInput.muted.drop(1).onEach {
                    microphoneSettingsStore.muted.set(it)
                },
                satellite.voiceAssistant.player.volume.drop(1).onEach {
                    playerSettingsStore.volume.set(it)
                },
                satellite.voiceAssistant.player.muted.drop(1).onEach {
                    playerSettingsStore.muted.set(it)
                }
            )
        }.launchIn(lifecycleScope)
    }

    private fun startTaskerStateObserver() {
        combine(voiceSatelliteState, voiceTimers) { state, timers ->
            AvaActivityRunner.updateState(state, timers)
            ActivityConfigAvaActivity::class.java.requestQuery(this)
        }.launchIn(lifecycleScope)
    }

    private fun updateNotificationOnStateChanges() = _voiceSatellite
        .flatMapLatest {
            it?.voiceAssistant?.state ?: emptyFlow()
        }
        .onEach {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
                2,
                createVoiceSatelliteServiceNotification(
                    this@VoiceSatelliteService,
                    it.translate(resources)
                )
            )
        }
        .launchIn(lifecycleScope)

    private fun registerVoiceSatelliteNsd(settings: VoiceSatelliteSettings) =
        registerVoiceSatelliteNsd(
            context = this@VoiceSatelliteService,
            name = settings.name,
            port = settings.serverPort,
            macAddress = settings.macAddress
        )

    override fun onDestroy() {
        _voiceSatellite.getAndUpdate { null }?.close()
        voiceSatelliteNsd.getAndSet(null)?.unregister(this)
        wifiWakeLock.release()
        super.onDestroy()
    }

    companion object {
        const val TAG = "VoiceSatelliteService"
    }
}