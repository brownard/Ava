package com.example.ava.services

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC
import androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH
import androidx.media3.common.C.USAGE_ASSISTANT
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.ava.esphome.Stopped
import com.example.ava.esphome.voicesatellite.VoiceSatellite
import com.example.ava.esphome.voicesatellite.VoiceSatelliteAudioInput
import com.example.ava.esphome.voicesatellite.VoiceSatellitePlayer
import com.example.ava.notifications.createVoiceSatelliteServiceNotification
import com.example.ava.notifications.createVoiceSatelliteServiceNotificationChannel
import com.example.ava.nsd.NsdRegistration
import com.example.ava.nsd.registerVoiceSatelliteNsd
import com.example.ava.players.AudioPlayer
import com.example.ava.players.AudioPlayerImpl
import com.example.ava.settings.MicrophoneSettingsStore
import com.example.ava.settings.PlayerSettingsStore
import com.example.ava.settings.VoiceSatelliteSettings
import com.example.ava.settings.VoiceSatelliteSettingsStore
import com.example.ava.utils.translate
import com.example.ava.wakelocks.WifiWakeLock
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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

    private val wifiWakeLock = WifiWakeLock()
    private var voiceSatelliteNsd = AtomicReference<NsdRegistration?>(null)
    private val _voiceSatellite = MutableStateFlow<VoiceSatellite?>(null)

    val voiceSatelliteState = _voiceSatellite.flatMapLatest {
        it?.state ?: flowOf(Stopped)
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
                _voiceSatellite.value = createVoiceSatellite(settings).apply { start() }
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
                satellite.audioInput.activeWakeWords.drop(1).onEach {
                    microphoneSettingsStore.wakeWord.set(it.firstOrNull().orEmpty())
                    microphoneSettingsStore.secondWakeWord.set(it.elementAtOrNull(1))
                },
                satellite.audioInput.muted.drop(1).onEach {
                    microphoneSettingsStore.muted.set(it)
                },
                satellite.player.volume.drop(1).onEach {
                    playerSettingsStore.volume.set(it)
                },
                satellite.player.muted.drop(1).onEach {
                    playerSettingsStore.muted.set(it)
                }
            )
        }.launchIn(lifecycleScope)
    }

    private suspend fun createVoiceSatellite(satelliteSettings: VoiceSatelliteSettings): VoiceSatellite {
        val microphoneSettings = microphoneSettingsStore.get()
        val audioInput = VoiceSatelliteAudioInput(
            activeWakeWords = listOfNotNull(
                microphoneSettings.wakeWord,
                microphoneSettings.secondWakeWord
            ),
            activeStopWords = listOf(microphoneSettings.stopWord),
            availableWakeWords = microphoneSettingsStore.availableWakeWords.first(),
            availableStopWords = microphoneSettingsStore.availableStopWords.first(),
            muted = microphoneSettings.muted
        )

        val playerSettings = playerSettingsStore.get()
        val player = VoiceSatellitePlayer(
            ttsPlayer = createAudioPlayer(
                USAGE_ASSISTANT,
                AUDIO_CONTENT_TYPE_SPEECH,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK

            ),
            mediaPlayer = createAudioPlayer(
                USAGE_MEDIA,
                AUDIO_CONTENT_TYPE_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ),
            enableWakeSound = playerSettingsStore.enableWakeSound,
            wakeSound = playerSettingsStore.wakeSound,
            timerFinishedSound = playerSettingsStore.timerFinishedSound
        ).apply {
            setVolume(playerSettings.volume)
            setMuted(playerSettings.muted)
        }

        return VoiceSatellite(
            coroutineContext = lifecycleScope.coroutineContext,
            name = satelliteSettings.name,
            port = satelliteSettings.serverPort,
            audioInput = audioInput,
            player = player,
            settingsStore = satelliteSettingsStore
        )
    }

    private fun updateNotificationOnStateChanges() = _voiceSatellite
        .flatMapLatest {
            it?.state ?: emptyFlow()
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

    fun createAudioPlayer(usage: Int, contentType: Int, focusGain: Int): AudioPlayer {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        return AudioPlayerImpl(audioManager, focusGain) {
            ExoPlayer.Builder(this@VoiceSatelliteService).setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(contentType)
                    .build(),
                false
            ).build()
        }
    }

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