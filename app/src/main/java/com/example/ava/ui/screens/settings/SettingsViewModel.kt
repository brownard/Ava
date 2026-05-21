package com.example.ava.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ava.R
import com.example.ava.settings.MicrophoneSettingsStore
import com.example.ava.settings.PlayerSettingsStore
import com.example.ava.settings.VoiceSatelliteSettingsStore
import com.example.ava.settings.availableWakeWords
import com.example.ava.settings.defaultErrorSound
import com.example.ava.settings.defaultTimerFinishedSound
import com.example.ava.settings.defaultWakeSound
import com.example.ava.wakewords.models.WakeWordWithId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val microphoneSettingsStore: MicrophoneSettingsStore
) : ViewModel() {
    val satelliteSettingsState = satelliteSettingsStore.getFlow()

    val microphoneSettingsState = microphoneSettingsStore.getFlow().map { settings ->
        val wakeWords = settings.availableWakeWords(context)
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

    fun saveName(name: String) = viewModelScope.launch {
        if (validateName(name).isNullOrBlank()) {
            satelliteSettingsStore.name.set(name)
        } else {
            Timber.w("Cannot save invalid server name: $name")
        }
    }

    fun saveServerPort(port: Int?) = viewModelScope.launch {
        if (validatePort(port).isNullOrBlank()) {
            satelliteSettingsStore.serverPort.set(port!!)
        } else {
            Timber.w("Cannot save invalid server port: $port")
        }
    }

    fun saveAutoStart(autoStart: Boolean) = viewModelScope.launch {
        satelliteSettingsStore.autoStart.set(autoStart)
    }

    fun saveTrustAllSSLCerts(trustAllSSlCerts: Boolean) = viewModelScope.launch {
        satelliteSettingsStore.trustAllSSLCerts.set(trustAllSSlCerts)
    }

    fun saveWakeWord(wakeWordId: String, availableWakeWords: List<WakeWordWithId>) =
        viewModelScope.launch {
            if (availableWakeWords.any { it.id == wakeWordId }) {
                microphoneSettingsStore.wakeWord.set(wakeWordId)
            } else {
                Timber.w("Cannot save unknown wake word: $wakeWordId")
            }
        }

    fun saveSecondWakeWord(wakeWordId: String?, availableWakeWords: List<WakeWordWithId>?) =
        viewModelScope.launch {
            if (wakeWordId == null || availableWakeWords?.any { it.id == wakeWordId } ?: false) {
                microphoneSettingsStore.secondWakeWord.set(wakeWordId)
            } else {
                Timber.w("Cannot save unknown wake word: $wakeWordId")
            }
        }

    fun saveCustomWakeWordDirectory(uri: Uri?) = viewModelScope.launch {
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

    fun resetCustomWakeWordDirectory() = viewModelScope.launch {
        microphoneSettingsStore.customWakeWordLocation.set(null)
    }

    fun saveEnableWakeSound(enableWakeSound: Boolean) = viewModelScope.launch {
        playerSettingsStore.enableWakeSound.set(enableWakeSound)
    }

    fun saveWakeSound(uri: Uri?) = viewModelScope.launch {
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

    fun resetWakeSound() = viewModelScope.launch {
        playerSettingsStore.wakeSound.set(defaultWakeSound)
    }

    fun saveTimerFinishedSound(uri: Uri?) = viewModelScope.launch {
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

    fun resetTimerFinishedSound() = viewModelScope.launch {
        playerSettingsStore.timerFinishedSound.set(defaultTimerFinishedSound)
    }

    fun saveRepeatTimerFinishedSound(repeatTimerFinishedSound: Boolean) = viewModelScope.launch {
        playerSettingsStore.repeatTimerFinishedSound.set(repeatTimerFinishedSound)
    }

    fun saveEnableErrorSound(enableErrorSound: Boolean) = viewModelScope.launch {
        playerSettingsStore.enableErrorSound.set(enableErrorSound)
    }

    fun saveErrorSound(uri: Uri?) = viewModelScope.launch {
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            playerSettingsStore.errorSound.set(uri.toString())
        }
    }

    fun resetErrorSound() = viewModelScope.launch {
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
}