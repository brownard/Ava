package com.example.ava.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.example.ava.R
import com.example.ava.settings.DisplaySettingsStore
import com.example.ava.settings.MicrophoneSettingsStore
import com.example.ava.settings.PlayerSettingsStore
import com.example.ava.settings.VoiceSatelliteSettingsStore
import com.example.ava.settings.defaultTimerFinishedSound
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
    val customWakeWordLocation: Uri?
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val satelliteSettingsStore: VoiceSatelliteSettingsStore,
    private val playerSettingsStore: PlayerSettingsStore,
    private val microphoneSettingsStore: MicrophoneSettingsStore,
    private val displaySettingsStore: DisplaySettingsStore
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

            customWakeWordLocation = settings.customWakeWordLocation?.toUri()
        )
    }

    val playerSettingsState = playerSettingsStore.getFlow()

    val displaySettingsState = displaySettingsStore.getFlow()

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

    suspend fun saveEnableWakeSound(enableWakeSound: Boolean) {
        playerSettingsStore.enableWakeSound.set(enableWakeSound)
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

    suspend fun saveWakeScreen(wakeScreen: Boolean) {
        displaySettingsStore.wakeScreen.set(wakeScreen)
    }

    suspend fun saveHideSystemBars(hideSystemBars: Boolean) {
        displaySettingsStore.hideSystemBars.set(hideSystemBars)
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
