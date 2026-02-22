package com.example.ava.stubs

import com.example.ava.settings.SettingState
import com.example.ava.settings.VoiceSatelliteSettings
import com.example.ava.settings.VoiceSatelliteSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow

fun <T> stubSettingState(value: T): SettingState<T> {
    val state = MutableStateFlow(value)
    return SettingState(state) { state.value = it }
}

// Simplified Settings Store: Use a secondary constructor or default property values
open class StubVoiceSatelliteSettingsStore(
    val settings: VoiceSatelliteSettings = VoiceSatelliteSettings(
        name = "Test", macAddress = "00:11:22:33:44:55", autoStart = false, serverPort = 16054
    )
) : VoiceSatelliteSettingsStore {
    override val name = stubSettingState(settings.name)
    override val serverPort = stubSettingState(settings.serverPort)
    override val autoStart = stubSettingState(settings.autoStart)
    override suspend fun ensureMacAddressIsSet() {}
    override fun getFlow() = MutableStateFlow(settings)
    override suspend fun get() = settings
    override suspend fun update(transform: suspend (VoiceSatelliteSettings) -> VoiceSatelliteSettings) {}
}