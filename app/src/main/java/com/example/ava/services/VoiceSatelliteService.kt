package com.example.ava.services

import android.app.NotificationManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.ava.esphome.Connected
import com.example.ava.esphome.EspHomeDevice
import com.example.ava.esphome.Stopped
import com.example.ava.esphome.voiceassistant.Listening
import com.example.ava.esphome.voiceassistant.Processing
import com.example.ava.esphome.voiceassistant.Responding
import com.example.ava.esphome.voiceassistant.Transcript
import com.example.ava.esphome.voiceassistant.VoiceError
import com.example.ava.notifications.createVoiceSatelliteServiceNotification
import com.example.ava.notifications.createVoiceSatelliteServiceNotificationChannel
import com.example.ava.nsd.NsdRegistration
import com.example.ava.nsd.registerVoiceSatelliteNsd
import com.example.ava.settings.VoiceSatelliteSettings
import com.example.ava.settings.VoiceSatelliteSettingsStore
import com.example.ava.tasker.observeTaskerState
import com.example.ava.utils.translate
import com.example.ava.wakelocks.ScreenWakeLock
import com.example.ava.wakelocks.WifiWakeLock
import com.example.esphomeproto.api.MediaPlayerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
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
    lateinit var deviceBuilder: DeviceBuilder

    private val wifiWakeLock = WifiWakeLock()
    private val screenWakeLock = ScreenWakeLock()
    private var voiceSatelliteNsd = AtomicReference<NsdRegistration?>(null)
    private val _voiceSatellite = MutableStateFlow<EspHomeDevice?>(null)

    val voiceSatelliteState = _voiceSatellite.flatMapLatest {
        it?.voiceAssistant?.state ?: flowOf(Stopped)
    }

    val voiceTimers = _voiceSatellite.flatMapLatest {
        it?.voiceAssistant?.allTimers ?: flowOf(listOf())
    }

    val mediaState = _voiceSatellite.flatMapLatest {
        it?.mediaPlayer?.mediaState ?: flowOf(MediaPlayerState.MEDIA_PLAYER_STATE_IDLE)
    }

    val mediaTitle = _voiceSatellite.flatMapLatest {
        it?.mediaPlayer?.mediaTitle ?: flowOf(null)
    }

    val mediaArtist = _voiceSatellite.flatMapLatest {
        it?.mediaPlayer?.mediaArtist ?: flowOf(null)
    }

    val mediaArtworkData = _voiceSatellite.flatMapLatest {
        it?.mediaPlayer?.artworkData ?: flowOf(null)
    }

    val mediaArtworkUri = _voiceSatellite.flatMapLatest {
        it?.mediaPlayer?.artworkUri ?: flowOf(null)
    }

    val transcript = _voiceSatellite.flatMapLatest {
        it?.voiceAssistant?.transcript ?: flowOf<Transcript?>(null)
    }

    val mediaPosition = _voiceSatellite.flatMapLatest { device ->
        val mp = device?.mediaPlayer ?: return@flatMapLatest flowOf(0L)
        mp.mediaState.flatMapLatest { state ->
            if (state == MediaPlayerState.MEDIA_PLAYER_STATE_PLAYING) {
                flow { while (true) { emit(mp.currentPosition); delay(500) } }
            } else {
                flowOf(mp.currentPosition)
            }
        }
    }

    val mediaDuration = _voiceSatellite.flatMapLatest { device ->
        val mp = device?.mediaPlayer ?: return@flatMapLatest flowOf(0L)
        mp.mediaState.flatMapLatest { state ->
            if (state != MediaPlayerState.MEDIA_PLAYER_STATE_IDLE) {
                flow { while (true) { emit(mp.duration); delay(500) } }
            } else {
                flowOf(0L)
            }
        }
    }

    fun startVoiceSatellite() {
        val serviceIntent = Intent(this, this::class.java)
        applicationContext.startForegroundService(serviceIntent)
    }

    fun toggleMediaPlayback() {
        _voiceSatellite.value?.mediaPlayer?.togglePlayback()
    }

    fun skipToNext() {
        _voiceSatellite.value?.mediaPlayer?.skipToNext()
    }

    fun skipToPrevious() {
        _voiceSatellite.value?.mediaPlayer?.skipToPrevious()
    }

    fun cancelTimer(timerId: String) {
        _voiceSatellite.value?.voiceAssistant?.cancelTimer(timerId)
    }

    fun addTimeToTimer(timerId: String, seconds: Int) {
        _voiceSatellite.value?.voiceAssistant?.addTimeToTimer(timerId, seconds)
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
        screenWakeLock.create(applicationContext, TAG)
        createVoiceSatelliteServiceNotificationChannel(this)
        updateNotificationOnStateChanges()
        updateScreenWakeLockOnStateChanges()
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

    private fun startTaskerStateObserver() = lifecycleScope.launch {
        _voiceSatellite.collectLatest { it?.observeTaskerState(this@VoiceSatelliteService) }
    }

    private fun updateNotificationOnStateChanges() = _voiceSatellite
        .flatMapLatest {
            it?.voiceAssistant?.state ?: emptyFlow()
        }
        .onEach {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
                2,
                createVoiceSatelliteServiceNotification(
                    this,
                    it.translate(resources)
                )
            )
        }
        .launchIn(lifecycleScope)

    private fun updateScreenWakeLockOnStateChanges() = lifecycleScope.launch {
        val scope = this
        var idleReleaseJob: Job? = null
        _voiceSatellite
            .flatMapLatest { it?.voiceAssistant?.state ?: emptyFlow() }
            .collect { state ->
                when (state) {
                    is Listening, is Processing, is Responding -> {
                        idleReleaseJob?.cancel()
                        idleReleaseJob = null
                        screenWakeLock.acquire()
                    }
                    is Connected, is VoiceError -> {
                        if (idleReleaseJob?.isActive != true) {
                            idleReleaseJob = scope.launch {
                                val timeoutSeconds = satelliteSettingsStore.screenIdleTimeoutSeconds.get()
                                delay(timeoutSeconds * 1000L)
                                screenWakeLock.release()
                            }
                        }
                    }
                    else -> {
                        idleReleaseJob?.cancel()
                        idleReleaseJob = null
                        screenWakeLock.release()
                    }
                }
            }
    }

    private fun registerVoiceSatelliteNsd(settings: VoiceSatelliteSettings) =
        registerVoiceSatelliteNsd(
            context = this,
            name = settings.name,
            port = settings.serverPort,
            macAddress = settings.macAddress
        )

    override fun onDestroy() {
        _voiceSatellite.getAndUpdate { null }?.close()
        voiceSatelliteNsd.getAndSet(null)?.unregister(this)
        wifiWakeLock.release()
        screenWakeLock.release()
        super.onDestroy()
    }

    companion object {
        const val TAG = "VoiceSatelliteService"
    }
}