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

@Serializable
data class DisplaySettings(
    val wakeScreen: Boolean = false,
    val hideSystemBars: Boolean = false
)

private val DEFAULT = DisplaySettings()

/**
 * Used to inject a concrete implementation of DisplaySettingsStore
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DisplaySettingsModule() {
    @Binds
    abstract fun bindDisplaySettingsStore(displaySettingsStoreImpl: DisplaySettingsStoreImpl): DisplaySettingsStore
}

interface DisplaySettingsStore : SettingsStore<DisplaySettings> {
    /**
     * Whether to wake the screen when an event occurs.
     */
    val wakeScreen: SettingState<Boolean>

    /**
     * Whether to hide the system bars.
     */
    val hideSystemBars: SettingState<Boolean>
}

@Singleton
class DisplaySettingsStoreImpl @Inject constructor(@param:ApplicationContext private val context: Context) :
    DisplaySettingsStore, SettingsStoreImpl<DisplaySettings>(
    context = context,
    default = DEFAULT,
    fileName = "display_settings.json",
    serializer = DisplaySettings.serializer()
) {
    override val wakeScreen = SettingState(getFlow().map { it.wakeScreen }) { value ->
        update { it.copy(wakeScreen = value) }
    }

    override val hideSystemBars = SettingState(getFlow().map { it.hideSystemBars }) { value ->
        update { it.copy(hideSystemBars = value) }
    }
}
