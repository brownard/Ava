package com.example.ava.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.example.ava.R
import com.example.ava.settings.MicrophoneSettingsStore
import com.example.ava.settings.PlayerSettingsStore
import com.example.ava.settings.VoiceSatelliteSettingsStore
import com.example.ava.settings.defaultErrorSound
import com.example.ava.settings.defaultTimerFinishedSound
import com.example.ava.settings.defaultWakeSound
import com.example.ava.wakewords.models.WakeWordWithId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

@Immutable
data class MicrophoneState(
    val wakeWord: WakeWordWithId,
    val secondWakeWord: WakeWordWithId?,
    val wakeWords: List<WakeWordWithId>,
    val customWakeWordLocation: Uri?,
    val audioSource: Int,
    val micGainDb: Int,
    val enableNoiseSuppressor: Boolean,
    val enableAutomaticGainControl: Boolean,
    val enableAcousticEchoCanceler: Boolean,
    val probabilityCutoffOverride: Float?,
    val slidingWindowSizeOverride: Int?
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val satelliteSettingsStore: VoiceSatelliteSettingsStore,
    private val playerSettingsStore: PlayerSettingsStore,
    private val microphoneSettingsStore: MicrophoneSettingsStore
) : ViewModel() {
    val satelliteSettingsState = satelliteSettingsStore.getFlow()

    val microphoneSettingsState = combine(
        microphoneSettingsStore.getFlow(),
        microphoneSettingsStore.availableWakeWords
    ) { settings, wakeWords ->
        MicrophoneState(
            wakeWord = wakeWords.firstOrNull { wakeWord ->
                wakeWord.id == settings.wakeWord
            } ?: wakeWords.first(),
            secondWakeWord = wakeWords.firstOrNull { wakeWord ->
                wakeWord.id == settings.secondWakeWord
            },
            wakeWords = wakeWords,
            customWakeWordLocation = settings.customWakeWordLocation?.toUri(),
            audioSource = settings.audioSource,
            micGainDb = settings.micGainDb,
            enableNoiseSuppressor = settings.enableNoiseSuppressor,
            enableAutomaticGainControl = settings.enableAutomaticGainControl,
            enableAcousticEchoCanceler = settings.enableAcousticEchoCanceler,
            probabilityCutoffOverride = settings.probabilityCutoffOverride,
            slidingWindowSizeOverride = settings.slidingWindowSizeOverride
        )
    }

    val playerSettingsState = playerSettingsStore.getFlow()

    suspend fun saveName(name: String) {
        if (validateName(name).isNullOrBlank()) {
            satelliteSettingsStore.name.set(name)
        } else {
            Timber.w("Cannot save invalid server name: $name")
        }
    }

    suspend fun saveServerPort(port: Int?) {
        if (validatePort(port).isNullOrBlank()) {
            satelliteSettingsStore.serverPort.set(port!!)
        } else {
            Timber.w("Cannot save invalid server port: $port")
        }
    }

    suspend fun saveAutoStart(autoStart: Boolean) {
        satelliteSettingsStore.autoStart.set(autoStart)
    }

    suspend fun saveScreenIdleTimeoutSeconds(seconds: Int?) {
        if (validateScreenIdleTimeoutSeconds(seconds).isNullOrBlank()) {
            satelliteSettingsStore.screenIdleTimeoutSeconds.set(seconds!!)
        } else {
            Timber.w("Cannot save invalid screen idle timeout: $seconds")
        }
    }

    fun validateScreenIdleTimeoutSeconds(seconds: Int?): String? =
        if (seconds == null || seconds < 0 || seconds > 300)
            context.getString(R.string.validation_screen_idle_timeout_invalid)
        else null

    suspend fun saveAllowRotation(allow: Boolean) {
        satelliteSettingsStore.allowRotation.set(allow)
    }

    suspend fun saveWakeWord(wakeWordId: String) {
        if (validateWakeWord(wakeWordId).isNullOrBlank()) {
            microphoneSettingsStore.wakeWord.set(wakeWordId)
        } else {
            Timber.w("Cannot save invalid wake word: $wakeWordId")
        }
    }

    suspend fun saveSecondWakeWord(wakeWordId: String?) {
        if (wakeWordId == null || validateWakeWord(wakeWordId).isNullOrBlank()) {
            microphoneSettingsStore.secondWakeWord.set(wakeWordId)
        } else {
            Timber.w("Cannot save invalid wake word: $wakeWordId")
        }
    }

    suspend fun saveCustomWakeWordDirectory(uri: Uri?) {
        if (uri != null) {
            // Get persistable permission to read from the location
            // ToDo: This should potentially handled elsewhere
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            microphoneSettingsStore.customWakeWordLocation.set(uri.toString())
        }
    }

    suspend fun resetCustomWakeWordDirectory() {
        microphoneSettingsStore.customWakeWordLocation.set(null)
    }

    suspend fun saveAudioSource(audioSource: Int) {
        microphoneSettingsStore.audioSource.set(audioSource)
    }

    suspend fun saveMicGainDb(gainDb: Int?) {
        if (validateMicGainDb(gainDb).isNullOrBlank()) {
            microphoneSettingsStore.micGainDb.set(gainDb ?: 0)
        } else {
            Timber.w("Cannot save invalid mic gain: $gainDb")
        }
    }

    suspend fun saveEnableNoiseSuppressor(enabled: Boolean) {
        microphoneSettingsStore.enableNoiseSuppressor.set(enabled)
    }

    suspend fun saveEnableAutomaticGainControl(enabled: Boolean) {
        microphoneSettingsStore.enableAutomaticGainControl.set(enabled)
    }

    suspend fun saveEnableAcousticEchoCanceler(enabled: Boolean) {
        microphoneSettingsStore.enableAcousticEchoCanceler.set(enabled)
    }

    suspend fun saveProbabilityCutoffOverride(value: String) {
        val parsed = value.takeIf { it.isNotBlank() }?.toFloatOrNull()
        if (validateProbabilityCutoff(value).isNullOrBlank()) {
            microphoneSettingsStore.probabilityCutoffOverride.set(parsed)
        } else {
            Timber.w("Cannot save invalid probability cutoff: $value")
        }
    }

    suspend fun saveSlidingWindowSizeOverride(value: Int?) {
        if (validateSlidingWindowSize(value).isNullOrBlank()) {
            microphoneSettingsStore.slidingWindowSizeOverride.set(value)
        } else {
            Timber.w("Cannot save invalid sliding window size: $value")
        }
    }

    fun validateMicGainDb(gainDb: Int?): String? =
        if (gainDb != null && gainDb !in 0..24)
            context.getString(R.string.validation_mic_gain_db_invalid)
        else null

    fun validateProbabilityCutoff(value: String): String? {
        if (value.isBlank()) return null
        val v = value.toFloatOrNull()
        return if (v == null || v < 0.1f || v > 0.99f)
            context.getString(R.string.validation_probability_cutoff_invalid)
        else null
    }

    fun validateSlidingWindowSize(value: Int?): String? =
        if (value != null && value !in 2..15)
            context.getString(R.string.validation_sliding_window_size_invalid)
        else null

    suspend fun saveEnableWakeSound(enableWakeSound: Boolean) {
        playerSettingsStore.enableWakeSound.set(enableWakeSound)
    }

    suspend fun saveWakeSound(uri: Uri?) {
        if (uri != null) {
            // Get persistable permission to read from the location
            // ToDo: This should potentially handled elsewhere
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            playerSettingsStore.wakeSound.set(uri.toString())
        }
    }

    suspend fun resetWakeSound() {
        playerSettingsStore.wakeSound.set(defaultWakeSound)
    }

    suspend fun saveTimerFinishedSound(uri: Uri?) {
        if (uri != null) {
            // Get persistable permission to read from the location
            // ToDo: This should potentially handled elsewhere
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            playerSettingsStore.timerFinishedSound.set(uri.toString())
        }
    }

    suspend fun resetTimerFinishedSound() {
        playerSettingsStore.timerFinishedSound.set(defaultTimerFinishedSound)
    }

    suspend fun saveRepeatTimerFinishedSound(repeatTimerFinishedSound: Boolean) {
        playerSettingsStore.repeatTimerFinishedSound.set(repeatTimerFinishedSound)
    }

    suspend fun saveEnableErrorSound(enableErrorSound: Boolean) {
        playerSettingsStore.enableErrorSound.set(enableErrorSound)
    }

    suspend fun saveErrorSound(uri: Uri?) {
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            playerSettingsStore.errorSound.set(uri.toString())
        }
    }

    suspend fun resetErrorSound() {
        playerSettingsStore.errorSound.set(defaultErrorSound)
    }

    fun validateName(name: String): String? =
        if (name.isBlank())
            context.getString(R.string.validation_voice_satellite_name_empty)
        else null


    fun validatePort(port: Int?): String? =
        if (port == null || port < 1 || port > 65535)
            context.getString(R.string.validation_voice_satellite_port_invalid)
        else null

    suspend fun validateWakeWord(wakeWordId: String): String? {
        val wakeWordWithId = microphoneSettingsStore.availableWakeWords.first()
            .firstOrNull { it.id == wakeWordId }
        return if (wakeWordWithId == null)
            context.getString(R.string.validation_voice_satellite_wake_word_invalid)
        else
            null
    }
}