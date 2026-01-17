package com.example.ava.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.example.ava.R
import com.example.ava.microwakeword.AssetWakeWordProvider
import com.example.ava.microwakeword.WakeWordProvider
import com.example.ava.microwakeword.WakeWordWithId
import com.example.ava.settings.MicrophoneSettingsStore
import com.example.ava.settings.PlayerSettingsStore
import com.example.ava.settings.VoiceSatelliteSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Immutable
data class MicrophoneState(
    val wakeWord: WakeWordWithId,
    val wakeWords: List<WakeWordWithId>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val satelliteSettingsStore: VoiceSatelliteSettingsStore,
    private val playerSettingsStore: PlayerSettingsStore,
    private val microphoneSettingsStore: MicrophoneSettingsStore
) : ViewModel() {
    private val wakeWordProvider: WakeWordProvider = AssetWakeWordProvider(context.assets)
    private val wakeWords = wakeWordProvider.getWakeWords()

    val satelliteSettingsState = satelliteSettingsStore.getFlow()

    val microphoneSettingsState = microphoneSettingsStore.getFlow().map {
        MicrophoneState(
            wakeWord = wakeWords.firstOrNull { wakeWord ->
                wakeWord.id == it.wakeWord
            } ?: wakeWords.first(),
            wakeWords = wakeWords
        )
    }

    val playerSettingsState = playerSettingsStore.getFlow()

    suspend fun saveName(name: String) {
        if (validateName(name).isNullOrBlank()) {
            satelliteSettingsStore.name.set(name)
        } else {
            Log.w(TAG, "Cannot save invalid server name: $name")
        }
    }

    suspend fun saveServerPort(port: Int?) {
        if (validatePort(port).isNullOrBlank()) {
            satelliteSettingsStore.serverPort.set(port!!)
        } else {
            Log.w(TAG, "Cannot save invalid server port: $port")
        }
    }

    suspend fun saveAutoStart(autoStart: Boolean) {
        satelliteSettingsStore.autoStart.set(autoStart)
    }

    suspend fun saveWakeWord(wakeWordId: String) {
        if (validateWakeWord(wakeWordId).isNullOrBlank()) {
            microphoneSettingsStore.wakeWord.set(wakeWordId)
        } else {
            Log.w(TAG, "Cannot save invalid wake word: $wakeWordId")
        }
    }

    suspend fun saveEnableWakeSound(enableWakeSound: Boolean) {
        playerSettingsStore.enableWakeSound.set(enableWakeSound)
    }


    fun validateName(name: String): String? =
        if (name.isBlank())
            context.getString(R.string.validation_voice_satellite_name_empty)
        else null


    fun validatePort(port: Int?): String? =
        if (port == null || port < 1 || port > 65535)
            context.getString(R.string.validation_voice_satellite_port_invalid)
        else null

    fun validateWakeWord(wakeWordId: String): String? {
        val wakeWordWithId = wakeWords.firstOrNull { it.id == wakeWordId }
        return if (wakeWordWithId == null)
            context.getString(R.string.validation_voice_satellite_wake_word_invalid)
        else
            null
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}