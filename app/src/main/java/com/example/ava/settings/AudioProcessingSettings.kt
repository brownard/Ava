package com.example.ava.settings

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import javax.inject.Singleton

private const val SETTINGS_FILE_NAME = "audio_processing_settings.json"

@Serializable
data class AudioProcessingSettings(
    val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    val audioMode: Int = AudioManager.MODE_NORMAL,
    val speakerphone: Boolean = false,
)

private val DEFAULT = AudioProcessingSettings()

/**
 * Used to inject a concrete implementation of AudioProcessingSettingsStore
 */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
object AudioProcessingSettingsModule {
    @Provides
    @Singleton
    fun provideAudioProcessingSettingsStore(@ApplicationContext context: Context): AudioProcessingSettingsStore =
        object : AudioProcessingSettingsStore,
            SettingsStore<AudioProcessingSettings> by SettingsStoreImpl(
                default = DEFAULT,
                produceFile = { context.dataStoreFile(SETTINGS_FILE_NAME) },
                serializer = AudioProcessingSettings.serializer()
            ) {}
}

interface AudioProcessingSettingsStore : SettingsStore<AudioProcessingSettings> {
    val audioSource: SettingState<Int>
        get() = setting(get = { audioSource }, set = { copy(audioSource = it) })

    val audioMode: SettingState<Int>
        get() = setting(get = { audioMode }, set = { copy(audioMode = it) })

    val speakerphone: SettingState<Boolean>
        get() = setting(get = { this.speakerphone }, set = { copy(speakerphone = it) })
}