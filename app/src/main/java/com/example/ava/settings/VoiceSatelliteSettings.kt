package com.example.ava.settings

import android.content.Context
import com.example.ava.utils.getRandomMacAddressString
import dagger.hilt.android.qualifiers.ApplicationContext
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
val DEFAULT_MAC_ADDRESS = "00:00:00:00:00:00"

@Serializable
data class VoiceSatelliteSettings(
    val name: String = "Android Voice Assistant",
    val serverPort: Int = 6053,
    val macAddress: String = DEFAULT_MAC_ADDRESS,
    val autoStart: Boolean = false
)

private val DEFAULT = VoiceSatelliteSettings()

@Singleton
class VoiceSatelliteSettingsStore @Inject constructor(@ApplicationContext context: Context) :
    SettingsStoreImpl<VoiceSatelliteSettings>(
        context = context,
        default = DEFAULT,
        fileName = "voice_satellite_settings.json",
        serializer = VoiceSatelliteSettings.serializer()
    ) {
    val name = SettingState(getFlow().map { it.name }) { value ->
        update { it.copy(name = value) }
    }

    val serverPort = SettingState(getFlow().map { it.serverPort }) { value ->
        update { it.copy(serverPort = value) }
    }

    val autoStart = SettingState(getFlow().map { it.autoStart }) { value ->
        update { it.copy(autoStart = value) }
    }

    suspend fun ensureMacAddressIsSet() {
        update {
            if (it.macAddress == DEFAULT_MAC_ADDRESS) it.copy(macAddress = getRandomMacAddressString()) else it
        }
    }
}