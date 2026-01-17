package com.example.ava.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PlayerSettings(
    val volume: Float = 1.0f,
    val muted: Boolean = false,
    val enableWakeSound: Boolean = true,
    val wakeSound: String = "asset:///sounds/wake_word_triggered.flac",
    val timerFinishedSound: String = "asset:///sounds/timer_finished.flac",
)

private val DEFAULT = PlayerSettings()

@Singleton
class PlayerSettingsStore @Inject constructor(@ApplicationContext context: Context) :
    SettingsStoreImpl<PlayerSettings>(
        context = context,
        default = DEFAULT,
        fileName = "player_settings.json",
        serializer = PlayerSettings.serializer()
    ) {
    val volume = SettingState(getFlow().map { it.volume }) { value ->
        update { it.copy(volume = value) }
    }

    val muted = SettingState(getFlow().map { it.muted }) { value ->
        update { it.copy(muted = value) }
    }

    val enableWakeSound = SettingState(getFlow().map { it.enableWakeSound }) { value ->
        update { it.copy(enableWakeSound = value) }
    }
    val wakeSound = SettingState(getFlow().map { it.wakeSound }) { value ->
        update { it.copy(wakeSound = value) }
    }

    val timerFinishedSound = SettingState(getFlow().map { it.timerFinishedSound }) { value ->
        update { it.copy(timerFinishedSound = value) }
    }
}