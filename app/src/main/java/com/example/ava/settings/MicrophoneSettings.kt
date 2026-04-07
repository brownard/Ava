package com.example.ava.settings

import android.content.Context
import android.media.MediaRecorder
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class MicrophoneSettings(
    val wakeWord: String = "okay_nabu",
    val secondWakeWord: String? = null,
    val stopWord: String = "stop",
    val customWakeWordLocation: String? = null,
    val muted: Boolean = false,
    val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    val micGainDb: Int = 0,
    val enableNoiseSuppressor: Boolean = true,
    val enableAutomaticGainControl: Boolean = true,
    val enableAcousticEchoCanceler: Boolean = true,
    val probabilityCutoffOverride: Float? = null,
    val slidingWindowSizeOverride: Int? = null
)

private val DEFAULT = MicrophoneSettings()

/**
 * Used to inject a concrete implementation of MicrophoneSettingsStore
 */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class MicrophoneSettingsModule() {
    @Binds
    abstract fun bindMicrophoneSettingsStore(microphoneSettingsStoreImpl: MicrophoneSettingsStoreImpl): MicrophoneSettingsStore
}

interface MicrophoneSettingsStore : SettingsStore<MicrophoneSettings> {
    val wakeWord: SettingState<String>
    val secondWakeWord: SettingState<String?>
    val stopWord: SettingState<String>
    val customWakeWordLocation: SettingState<String?>
    val muted: SettingState<Boolean>
    val audioSource: SettingState<Int>
    val micGainDb: SettingState<Int>
    val enableNoiseSuppressor: SettingState<Boolean>
    val enableAutomaticGainControl: SettingState<Boolean>
    val enableAcousticEchoCanceler: SettingState<Boolean>
    val probabilityCutoffOverride: SettingState<Float?>
    val slidingWindowSizeOverride: SettingState<Int?>
    val availableWakeWords: Flow<List<WakeWordWithId>>
    val availableStopWords: Flow<List<WakeWordWithId>>
}

val MicrophoneSettingsStore.activeWakeWords
    get() = SettingState(
        flow = combine(wakeWord, secondWakeWord) { wakeWord, secondWakeWord ->
            listOfNotNull(wakeWord, secondWakeWord)
        }
    ) {
        if (it.size > 0) {
            wakeWord.set(it[0])
            secondWakeWord.set(it.getOrNull(1))
        } else Timber.w("Attempted to set empty active wake word list")
    }

val MicrophoneSettingsStore.activeStopWords
    get() = SettingState(
        flow = stopWord.map { listOf(it) }
    ) {
        if (it.size > 0) {
            stopWord.set(it[0])
        } else Timber.w("Attempted to set empty stop word list")
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

    override val audioSource = SettingState(getFlow().map { it.audioSource }) { value ->
        update { it.copy(audioSource = value) }
    }

    override val micGainDb = SettingState(getFlow().map { it.micGainDb }) { value ->
        update { it.copy(micGainDb = value) }
    }

    override val enableNoiseSuppressor = SettingState(getFlow().map { it.enableNoiseSuppressor }) { value ->
        update { it.copy(enableNoiseSuppressor = value) }
    }

    override val enableAutomaticGainControl = SettingState(getFlow().map { it.enableAutomaticGainControl }) { value ->
        update { it.copy(enableAutomaticGainControl = value) }
    }

    override val enableAcousticEchoCanceler = SettingState(getFlow().map { it.enableAcousticEchoCanceler }) { value ->
        update { it.copy(enableAcousticEchoCanceler = value) }
    }

    override val probabilityCutoffOverride = SettingState(getFlow().map { it.probabilityCutoffOverride }) { value ->
        update { it.copy(probabilityCutoffOverride = value) }
    }

    override val slidingWindowSizeOverride = SettingState(getFlow().map { it.slidingWindowSizeOverride }) { value ->
        update { it.copy(slidingWindowSizeOverride = value) }
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