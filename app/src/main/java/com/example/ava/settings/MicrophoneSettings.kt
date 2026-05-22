package com.example.ava.settings

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.dataStoreFile
import com.example.ava.wakewords.providers.AssetWakeWordProvider
import com.example.ava.wakewords.providers.DocumentTreeWakeWordProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Singleton

private const val SETTINGS_FILE_NAME = "microphone_settings.json"

@Serializable
data class MicrophoneSettings(
    val wakeWord: String = "okay_nabu",
    val secondWakeWord: String? = null,
    val stopWord: String = "stop",
    val customWakeWordLocation: String? = null,
    val muted: Boolean = false
)

private val DEFAULT = MicrophoneSettings()

/**
 * Used to inject a concrete implementation of MicrophoneSettingsStore
 */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
object MicrophoneSettingsModule {
    @Provides
    @Singleton
    fun provideMicrophoneSettingsStore(@ApplicationContext context: Context): MicrophoneSettingsStore =
        object : MicrophoneSettingsStore, SettingsStore<MicrophoneSettings> by SettingsStoreImpl(
            default = DEFAULT,
            produceFile = { context.dataStoreFile(SETTINGS_FILE_NAME) },
            serializer = MicrophoneSettings.serializer()
        ) {}
}

interface MicrophoneSettingsStore : SettingsStore<MicrophoneSettings> {
    /**
     * The wake word to use for wake word detection.
     */
    val wakeWord: SettingState<String>
        get() = setting(get = { wakeWord }, set = { copy(wakeWord = it) })

    /**
     * Optional second wake word to use for wake word detection.
     */
    val secondWakeWord: SettingState<String?>
        get() = setting(get = { secondWakeWord }, set = { copy(secondWakeWord = it) })

    /**
     * The stop word to use for stop word detection.
     */
    val stopWord: SettingState<String>
        get() = setting(get = { stopWord }, set = { copy(stopWord = it) })

    /**
     * The Uri of the directory containing custom wake words or null if not set.
     */
    val customWakeWordLocation: SettingState<String?>
        get() = setting(
            get = { customWakeWordLocation },
            set = { copy(customWakeWordLocation = it) })

    /**
     * The muted state of the microphone.
     */
    val muted: SettingState<Boolean>
        get() = setting(get = { muted }, set = { copy(muted = it) })

    /**
     * Helper property that allows getting and setting [wakeWord] and [secondWakeWord] as a list.
     */
    val activeWakeWords
        get() = SettingState(
            flow = combine(wakeWord, secondWakeWord) { wakeWord, secondWakeWord ->
                listOfNotNull(wakeWord, secondWakeWord)
            }
        ) {
            if (it.isNotEmpty()) {
                wakeWord.set(it[0])
                secondWakeWord.set(it.getOrNull(1))
            } else Timber.w("Attempted to set empty active wake word list")
        }

    /**
     * Helper property that allows getting and setting [stopWord] as a list.
     */
    val activeStopWords
        get() = SettingState(
            flow = stopWord.map { listOf(it) }
        ) {
            if (it.isNotEmpty()) {
                stopWord.set(it[0])
            } else Timber.w("Attempted to set empty stop word list")
        }
}

/**
 * Returns a list of available wake words from configured providers.
 */
suspend fun MicrophoneSettings.availableWakeWords(context: Context) =
    if (customWakeWordLocation != null) {
        AssetWakeWordProvider(assets = context.assets).get() + DocumentTreeWakeWordProvider(
            context = context,
            treeUri = customWakeWordLocation.toUri()
        ).get()
    } else AssetWakeWordProvider(assets = context.assets).get()


/**
 * Returns a list of available stop words from configured providers.
 */
suspend fun MicrophoneSettings.availableStopWords(context: Context) =
    AssetWakeWordProvider(
        assets = context.assets,
        path = "stopWords"
    ).get()