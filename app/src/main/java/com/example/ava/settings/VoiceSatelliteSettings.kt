package com.example.ava.settings

import android.content.Context
import androidx.datastore.dataStoreFile
import com.example.ava.server.DEFAULT_SERVER_PORT
import com.example.ava.utils.getRandomMacAddressString
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import javax.inject.Singleton

private const val SETTINGS_FILE_NAME = "voice_satellite_settings.json"

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
    val autoStart: Boolean = false,
    val trustAllSSLCerts: Boolean = false,
)

private val DEFAULT = VoiceSatelliteSettings()

/**
 * Used to inject a concrete implementation of VoiceSatelliteSettingsStore
 */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
object VoiceSatelliteSettingsModule {
    @Provides
    @Singleton
    fun provideVoiceSatelliteSettingsStore(@ApplicationContext context: Context): VoiceSatelliteSettingsStore =
        object : VoiceSatelliteSettingsStore,
            SettingsStore<VoiceSatelliteSettings> by SettingsStoreImpl(
                default = DEFAULT,
                produceFile = { context.dataStoreFile(SETTINGS_FILE_NAME) },
                serializer = VoiceSatelliteSettings.serializer()
            ) {}
}

interface VoiceSatelliteSettingsStore : SettingsStore<VoiceSatelliteSettings> {
    /**
     * The display name of the voice satellite.
     */
    val name: SettingState<String>
        get() = setting(get = { name }, set = { copy(name = it) })

    /**
     * The port the voice satellite should listen on.
     */
    val serverPort: SettingState<Int>
        get() = setting(get = { serverPort }, set = { copy(serverPort = it) })

    /**
     * Whether the voice satellite should be started automatically when the app is opened.
     */
    val autoStart: SettingState<Boolean>
        get() = setting(get = { autoStart }, set = { copy(autoStart = it) })

    /**
     * Whether to trust all SSL certificates.
     */
    val trustAllSSLCerts: SettingState<Boolean>
        get() = setting(
            get = { this.trustAllSSLCerts },
            set = { copy(trustAllSSLCerts = it) })

    /**
     * Ensures that a mac address has been generated and persisted.
     */
    suspend fun ensureMacAddressIsSet() {
        update {
            if (it.macAddress == DEFAULT_MAC_ADDRESS) it.copy(macAddress = getRandomMacAddressString()) else it
        }
    }
}