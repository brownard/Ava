package com.example.ava.settings

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

const val defaultWakeSound = "asset:///sounds/wake_word_triggered.flac"
const val defaultTimerFinishedSound = "asset:///sounds/timer_finished.flac"


@Serializable
data class PlayerSettings(
    val volume: Float = 1.0f,
    val muted: Boolean = false,
    val enableWakeSound: Boolean = true,
    val wakeSound: String = defaultWakeSound,
    val timerFinishedSound: String = defaultTimerFinishedSound,
    val repeatTimerFinishedSound: Boolean = true,
)

private val DEFAULT = PlayerSettings()

/**
 * Used to inject a concrete implementation of PlayerSettingsStore
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerSettingsModule() {
    @Binds
    abstract fun bindPlayerSettingsStore(playerSettingsStoreImpl: PlayerSettingsStoreImpl): PlayerSettingsStore
}

interface PlayerSettingsStore : SettingsStore<PlayerSettings> {
    /**
     * The volume of the player.
     */
    val volume: SettingState<Float>

    /**
     * The muted state of the player.
     */
    val muted: SettingState<Boolean>

    /**
     * Whether the wake sound should be played when the wake word is triggered.
     */
    val enableWakeSound: SettingState<Boolean>

    /**
     * The path to the wake sound file.
     */
    val wakeSound: SettingState<String>

    /**
     * The path to the timer finished sound file.
     */
    val timerFinishedSound: SettingState<String>

    /**
     * Whether the timer alarm repeats until the user stops it.
     */
    val repeatTimerFinishedSound: SettingState<Boolean>
}

@Singleton
class PlayerSettingsStoreImpl @Inject constructor(@ApplicationContext context: Context) :
    PlayerSettingsStore, SettingsStoreImpl<PlayerSettings>(
    context = context,
    default = DEFAULT,
    fileName = "player_settings.json",
    serializer = PlayerSettings.serializer()
) {
    override val volume = SettingState(getFlow().map { it.volume }) { value ->
        update { it.copy(volume = value) }
    }

    override val muted = SettingState(getFlow().map { it.muted }) { value ->
        update { it.copy(muted = value) }
    }

    override val enableWakeSound = SettingState(getFlow().map { it.enableWakeSound }) { value ->
        update { it.copy(enableWakeSound = value) }
    }
    override val wakeSound = SettingState(getFlow().map { it.wakeSound }) { value ->
        update { it.copy(wakeSound = value) }
    }

    override val timerFinishedSound =
        SettingState(getFlow().map { it.timerFinishedSound }) { value ->
            update { it.copy(timerFinishedSound = value) }
        }

    override val repeatTimerFinishedSound =
        SettingState(getFlow().map { it.repeatTimerFinishedSound }) { value ->
            update { it.copy(repeatTimerFinishedSound = value) }
        }
}