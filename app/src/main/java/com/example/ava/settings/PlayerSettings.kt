package com.example.ava.settings

import android.content.Context
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import javax.inject.Singleton

private const val SETTINGS_FILE_NAME = "player_settings.json"

const val defaultWakeSound = "asset:///sounds/wake_word_triggered.flac"
const val defaultTimerFinishedSound = "asset:///sounds/timer_finished.flac"
const val defaultErrorSound = "asset:///sounds/error.flac"

@Serializable
data class PlayerSettings(
    val volume: Float = 1.0f,
    val muted: Boolean = false,
    val enableWakeSound: Boolean = true,
    val wakeSound: String = defaultWakeSound,
    val timerFinishedSound: String = defaultTimerFinishedSound,
    val repeatTimerFinishedSound: Boolean = true,
    val enableErrorSound: Boolean = false,
    val errorSound: String = defaultErrorSound,
)

private val DEFAULT = PlayerSettings()

/**
 * Used to inject a concrete implementation of PlayerSettingsStore
 */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
object PlayerSettingsModule {
    @Provides
    @Singleton
    fun providePlayerSettingsStore(@ApplicationContext context: Context): PlayerSettingsStore =
        object : PlayerSettingsStore, SettingsStore<PlayerSettings> by SettingsStoreImpl(
            default = DEFAULT,
            produceFile = { context.dataStoreFile(SETTINGS_FILE_NAME) },
            serializer = PlayerSettings.serializer()
        ) {}
}

interface PlayerSettingsStore : SettingsStore<PlayerSettings> {
    /**
     * The volume of the player.
     */
    val volume: SettingState<Float>
        get() = setting(get = { volume }, set = { copy(volume = it) })

    /**
     * The muted state of the player.
     */
    val muted: SettingState<Boolean>
        get() = setting(get = { muted }, set = { copy(muted = it) })

    /**
     * Whether the wake sound should be played when the wake word is triggered.
     */
    val enableWakeSound: SettingState<Boolean>
        get() = setting(get = { enableWakeSound }, set = { copy(enableWakeSound = it) })

    /**
     * The path to the wake sound file.
     */
    val wakeSound: SettingState<String>
        get() = setting(get = { wakeSound }, set = { copy(wakeSound = it) })

    /**
     * The path to the timer finished sound file.
     */
    val timerFinishedSound: SettingState<String>
        get() = setting(get = { timerFinishedSound }, set = { copy(timerFinishedSound = it) })

    /**
     * Whether the timer alarm repeats until the user stops it.
     */
    val repeatTimerFinishedSound: SettingState<Boolean>
        get() = setting(
            get = { repeatTimerFinishedSound },
            set = { copy(repeatTimerFinishedSound = it) })

    /**
     * Whether the error sound should be played when an error occurs.
     */
    val enableErrorSound: SettingState<Boolean>
        get() = setting(get = { enableErrorSound }, set = { copy(enableErrorSound = it) })

    /**
     * The path to the error sound file.
     */
    val errorSound: SettingState<String>
        get() = setting(get = { errorSound }, set = { copy(errorSound = it) })
}