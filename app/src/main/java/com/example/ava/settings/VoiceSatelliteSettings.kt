package com.example.ava.settings

import android.content.Context
import com.example.ava.server.DEFAULT_SERVER_PORT
import com.example.ava.utils.getRandomMacAddressString
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

// The voice satellite uses a mac address as a unique identifier.
// The use of the actual mac address on Android is discouraged/not available
// depending on the Android version.
// Instead a random string of bytes should be generated and persisted to the settings.
// The default value below should only used to detect when a random value hasn't been
// generated and persisted yet and should be replaced with a random value when it is.
const val DEFAULT_MAC_ADDRESS = "00:00:00:00:00:00"

@Serializable
data class VoiceSatelliteSettings(
    val name: String = "Android Voice Assistant",
    val serverPort: Int = DEFAULT_SERVER_PORT,
    val macAddress: String = DEFAULT_MAC_ADDRESS,
    val autoStart: Boolean = false
)

private val DEFAULT = VoiceSatelliteSettings()

/**
 * Used to inject a concrete implementation of VoiceSatelliteSettingsStore
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceSatelliteSettingsModule() {
    @Binds
    abstract fun bindVoiceSatelliteSettingsStore(voiceSatelliteSettingsStoreImpl: VoiceSatelliteSettingsStoreImpl): VoiceSatelliteSettingsStore
}

interface VoiceSatelliteSettingsStore : SettingsStore<VoiceSatelliteSettings> {
    /**
     * The display name of the voice satellite.
     */
    val name: SettingState<String>

    /**
     * The port the voice satellite should listen on.
     */
    val serverPort: SettingState<Int>

    /**
     * Whether the voice satellite should be started automatically when the app is opened.
     */
    val autoStart: SettingState<Boolean>

    /**
     * Ensures that a mac address has been generated and persisted.
     */
    suspend fun ensureMacAddressIsSet()
}

@Singleton
class VoiceSatelliteSettingsStoreImpl @Inject constructor(@ApplicationContext context: Context) :
    VoiceSatelliteSettingsStore, SettingsStoreImpl<VoiceSatelliteSettings>(
    context = context,
    default = DEFAULT,
    fileName = "voice_satellite_settings.json",
    serializer = VoiceSatelliteSettings.serializer()
) {
    override val name = SettingState(getFlow().map { it.name }) { value ->
        update { it.copy(name = value) }
    }

    override val serverPort = SettingState(getFlow().map { it.serverPort }) { value ->
        update { it.copy(serverPort = value) }
    }

    override val autoStart = SettingState(getFlow().map { it.autoStart }) { value ->
        update { it.copy(autoStart = value) }
    }

    override suspend fun ensureMacAddressIsSet() {
        update {
            if (it.macAddress == DEFAULT_MAC_ADDRESS) it.copy(macAddress = getRandomMacAddressString()) else it
        }
    }
}