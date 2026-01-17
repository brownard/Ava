package com.example.ava.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class MicrophoneSettings(
    val wakeWord: String = "okay_nabu",
    val stopWord: String = "stop",
    val muted: Boolean = false
)

private val DEFAULT = MicrophoneSettings()

@Singleton
class MicrophoneSettingsStore @Inject constructor(@ApplicationContext context: Context) :
    SettingsStoreImpl<MicrophoneSettings>(
        context = context,
        default = DEFAULT,
        fileName = "microphone_settings.json",
        serializer = MicrophoneSettings.serializer()
    ) {
    val wakeWord = SettingState(getFlow().map { it.wakeWord }) { value ->
        update { it.copy(wakeWord = value) }
    }

    val stopWord = SettingState(getFlow().map { it.stopWord }) { value ->
        update { it.copy(stopWord = value) }
    }

    val muted = SettingState(getFlow().map { it.muted }) { value ->
        update { it.copy(muted = value) }
    }
}