package com.example.ava.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.example.ava.utils.getRandomMacAddressString
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

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
)

private val DEFAULT = VoiceSatelliteSettings()

val Context.voiceSatelliteSettingsStore: DataStore<VoiceSatelliteSettings> by dataStore(
    fileName = "voice_satellite_settings.json",
    serializer = SettingsSerializer(VoiceSatelliteSettings.serializer(), DEFAULT),
    corruptionHandler = defaultCorruptionHandler(DEFAULT)
)

class VoiceSatelliteSettingsStore(dataStore: DataStore<VoiceSatelliteSettings>) :
    SettingsStoreImpl<VoiceSatelliteSettings>(dataStore, DEFAULT) {

    val name = SettingState(getFlow().map { it.name }) { value ->
        update { it.copy(name = value) }
    }

    val serverPort = SettingState(getFlow().map { it.serverPort }) { value ->
        update { it.copy(serverPort = value) }
    }

    suspend fun ensureMacAddressIsSet() {
        update {
            if (it.macAddress == DEFAULT_MAC_ADDRESS) it.copy(macAddress = getRandomMacAddressString()) else it
        }
    }
}