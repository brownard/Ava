package com.example.ava.settings

import android.content.Context
import androidx.core.net.toUri
import com.example.ava.wakewords.models.WakeWordWithId
import com.example.ava.wakewords.providers.AssetWakeWordProvider
import com.example.ava.wakewords.providers.DocumentTreeWakeWordProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

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
@Module
@InstallIn(SingletonComponent::class)
abstract class MicrophoneSettingsModule() {
    @Binds
    abstract fun bindMicrophoneSettingsStore(microphoneSettingsStoreImpl: MicrophoneSettingsStoreImpl): MicrophoneSettingsStore
}

interface MicrophoneSettingsStore : SettingsStore<MicrophoneSettings> {
    /**
     * The wake word to use for wake word detection.
     */
    val wakeWord: SettingState<String>

    /**
     * Optional second wake word to use for wake word detection.
     */
    val secondWakeWord: SettingState<String?>

    /**
     * The stop word to use for stop word detection.
     */
    val stopWord: SettingState<String>

    /**
     * The Uri of the directory containing custom wake words or null if not set.
     */
    val customWakeWordLocation: SettingState<String?>

    /**
     * The muted state of the microphone.
     */
    val muted: SettingState<Boolean>

    /**
     * Returns a list of available wake words from configured providers.
     */
    val availableWakeWords: Flow<List<WakeWordWithId>>

    /**
     * Returns a list of available stop words from configured providers.
     */
    val availableStopWords: Flow<List<WakeWordWithId>>
}

@Singleton
class MicrophoneSettingsStoreImpl @Inject constructor(@param:ApplicationContext private val context: Context) :
    MicrophoneSettingsStore, SettingsStoreImpl<MicrophoneSettings>(
    context = context,
    default = DEFAULT,
    fileName = "microphone_settings.json",
    serializer = MicrophoneSettings.serializer()
) {
    override val wakeWord = SettingState(getFlow().map { it.wakeWord }) { value ->
        update { it.copy(wakeWord = value) }
    }

    override val secondWakeWord = SettingState(getFlow().map { it.secondWakeWord }) { value ->
        update { it.copy(secondWakeWord = value) }
    }

    override val stopWord = SettingState(getFlow().map { it.stopWord }) { value ->
        update { it.copy(stopWord = value) }
    }

    override val customWakeWordLocation =
        SettingState(getFlow().map { it.customWakeWordLocation }) { value ->
            update { it.copy(customWakeWordLocation = value) }
        }

    override val muted = SettingState(getFlow().map { it.muted }) { value ->
        update { it.copy(muted = value) }
    }

    override val availableWakeWords = customWakeWordLocation.mapLatest {
        if (it != null)
            AssetWakeWordProvider(context.assets).get() + DocumentTreeWakeWordProvider(
                context,
                it.toUri()
            ).get()
        else
            AssetWakeWordProvider(context.assets).get()
    }

    override val availableStopWords = flow {
        emit(
            AssetWakeWordProvider(
                context.assets,
                "stopWords"
            ).get()
        )
    }
}