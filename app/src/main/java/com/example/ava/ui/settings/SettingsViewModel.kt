package com.example.ava.ui.settings

import android.app.Application
import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import com.example.ava.R
import com.example.ava.microwakeword.AssetWakeWordProvider
import com.example.ava.microwakeword.WakeWordProvider
import com.example.ava.microwakeword.WakeWordWithId
import com.example.ava.preferences.VoiceAssistantPreferencesStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.collections.firstOrNull
import kotlin.collections.map
import kotlin.text.isBlank
import kotlin.text.toIntOrNull

data class SettingState<T>(
    val value: T,
    val isValid: Boolean = true,
    val validation: String = ""
)

data class SettingsState(
    val name: SettingState<String> = SettingState(""),
    val port: SettingState<Int?> = SettingState(null),
    val wakeWord: SettingState<String> = SettingState("")
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesStore = VoiceAssistantPreferencesStore(application)
    private val wakeWordProvider: WakeWordProvider = AssetWakeWordProvider(application.assets)

    val nameTextState = TextFieldState()
    val portTextState = TextFieldState()
    val wakeWordTextState = TextFieldState()

    val _wakeWords = MutableStateFlow<List<WakeWordWithId>>(listOf())
    val wakeWords = _wakeWords.map { it.map { it.wakeWord.wake_word } }

    val validationState = combine(
        snapshotFlow { nameTextState.text }.map {
            validateName(it.toString())
        },
        snapshotFlow { portTextState.text }.map {
            validatePort(it.toString().toIntOrNull())
        },
        snapshotFlow { wakeWordTextState.text }.map {
            validateWakeWord(it.toString())
        },
        transform = { name, port, wakeWord -> SettingsState(name, port, wakeWord) }
    )

    suspend fun loadSettings() {
        val wakeWords = wakeWordProvider.getWakeWords()
        _wakeWords.value = wakeWords
        val settings = preferencesStore.getSettings()
        nameTextState.setTextAndPlaceCursorAtEnd(settings.name)
        portTextState.setTextAndPlaceCursorAtEnd(settings.serverPort.toString())
        wakeWordTextState.setTextAndPlaceCursorAtEnd(
            wakeWords.firstOrNull { it.id == settings.wakeWord }?.wakeWord?.wake_word ?: ""
        )
    }

    suspend fun saveSettings() {
        val settingsState = validationState.first()
        if (settingsState.name.isValid && settingsState.port.isValid && settingsState.wakeWord.isValid) {
            val settings = preferencesStore.getSettings()
            preferencesStore.save(
                settings.copy(
                    name = settingsState.name.value,
                    serverPort = settingsState.port.value!!,
                    wakeWord = settingsState.wakeWord.value
                )
            )
        } else {
            Log.w(TAG, "Cannot save invalid settings: $settingsState")
        }
    }

    private fun validateName(name: String): SettingState<String> =
        if (name.isBlank())
            SettingState(
                name,
                false,
                application.getString(R.string.validation_voice_satellite_name_empty)
            )
        else
            SettingState(name)


    private fun validatePort(port: Int?): SettingState<Int?> =
        if (port == null || port < 1 || port > 65535)
            SettingState(
                port,
                false,
                application.getString(R.string.validation_voice_satellite_port_invalid)
            )
        else
            SettingState(port)

    private fun validateWakeWord(wakeWord: String): SettingState<String> {
        val wakeWordWithId = _wakeWords.value.firstOrNull { it.wakeWord.wake_word == wakeWord }
        if (wakeWordWithId == null)
            return SettingState(
                wakeWord,
                false,
                application.getString(R.string.validation_voice_satellite_wake_word_invalid)
            )
        else
            return SettingState(wakeWordWithId.id)
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}