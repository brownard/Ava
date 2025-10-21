package com.example.ava.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ava.utils.getRandomMacAddressString
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

val Context.voiceSatelliteDataStore: DataStore<Preferences> by preferencesDataStore(name = "voice_assistant_settings")

data class VoiceSatelliteSettings(val name: String, val wakeWord: String, val macAddress: String, val serverPort: Int)

object VoiceSatellitePreferenceKeys{
    val NAME = stringPreferencesKey("name")
    val WAKE_WORD = stringPreferencesKey("wake_word")
    val MAC_ADDRESS = stringPreferencesKey("mac_address")
    val SERVER_PORT = intPreferencesKey("server_port")
}

class VoiceAssistantPreferencesStore(context: Context) {
    private val dataStore = context.voiceSatelliteDataStore

    fun getSettingsFlow() =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e(TAG, "Error reading preferences, returning defaults", exception)
                    emit(emptyPreferences())
                } else throw exception
            }
            .filter { checkSaveDefaultSettings(it) }
            .map { createSettingsOrDefault(it) }
            .onEach { Log.d(TAG, "Loaded settings: $it") }

    suspend fun getSettings() = getSettingsFlow().first()

    private suspend fun checkSaveDefaultSettings(currentPreferences: Preferences): Boolean {
        if (currentPreferences[VoiceSatellitePreferenceKeys.NAME] != null
            && currentPreferences[VoiceSatellitePreferenceKeys.WAKE_WORD] != null
            && currentPreferences[VoiceSatellitePreferenceKeys.MAC_ADDRESS] != null
            && currentPreferences[VoiceSatellitePreferenceKeys.SERVER_PORT] != null
        ) return true

        save(createSettingsOrDefault(currentPreferences))
        return false
    }

    private fun createSettingsOrDefault(preferences: Preferences) = VoiceSatelliteSettings(
        preferences[VoiceSatellitePreferenceKeys.NAME] ?: DEFAULT_NAME,
        preferences[VoiceSatellitePreferenceKeys.WAKE_WORD] ?: DEFAULT_WAKE_WORD,
        preferences[VoiceSatellitePreferenceKeys.MAC_ADDRESS] ?: DEFAULT_MAC_ADDRESS,
        preferences[VoiceSatellitePreferenceKeys.SERVER_PORT] ?: DEFAULT_SERVER_PORT
    )

    suspend fun save(voiceSatelliteSettings: VoiceSatelliteSettings) {
        try {
            dataStore.edit { preferences ->
                preferences[VoiceSatellitePreferenceKeys.NAME] = voiceSatelliteSettings.name
                preferences[VoiceSatellitePreferenceKeys.WAKE_WORD] =
                    voiceSatelliteSettings.wakeWord
                preferences[VoiceSatellitePreferenceKeys.MAC_ADDRESS] =
                    voiceSatelliteSettings.macAddress
                preferences[VoiceSatellitePreferenceKeys.SERVER_PORT] =
                    voiceSatelliteSettings.serverPort
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error saving preferences", e)
        }
    }

    companion object {
        private const val TAG = "VoiceAssistantPreferences"
        private const val DEFAULT_NAME = "Android Voice Assistant"
        private const val DEFAULT_WAKE_WORD = "okay_nabu"
        private val DEFAULT_MAC_ADDRESS = getRandomMacAddressString()
        private const val DEFAULT_SERVER_PORT = 6053
    }
}