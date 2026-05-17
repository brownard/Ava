package com.example.ava.ui.screens.settings

import android.media.AudioManager
import android.media.MediaRecorder
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ava.R
import com.example.ava.settings.AudioProcessingSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectItem(
    val key: Int,
    val value: String,
    @StringRes val descriptionResource: Int? = null
)

val audioSources = listOf(
    SelectItem(
        key = MediaRecorder.AudioSource.VOICE_RECOGNITION,
        value = "Voice Recognition",
        descriptionResource = R.string.description_audio_source_voice_recognition
    ),
    SelectItem(
        key = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        value = "Voice Communication",
        descriptionResource = R.string.description_audio_source_voice_communication
    ),
    SelectItem(
        key = MediaRecorder.AudioSource.MIC,
        value = "Mic",
        descriptionResource = R.string.description_audio_source_microphone
    ),
    SelectItem(
        key = MediaRecorder.AudioSource.CAMCORDER,
        value = "Camcorder",
        descriptionResource = R.string.description_audio_source_camcorder
    ),
    SelectItem(
        key = MediaRecorder.AudioSource.DEFAULT,
        value = "Default",
        descriptionResource = R.string.description_audio_source_default
    ),
    SelectItem(
        key = MediaRecorder.AudioSource.UNPROCESSED,
        value = "Unprocessed",
        descriptionResource = R.string.description_audio_source_unprocessed
    ),
)

data class AudioProcessingState(
    val audioSource: SelectItem,
    val communicationMode: Boolean,
    val speakerphone: Boolean,
)

@HiltViewModel
class AudioProcessingViewModel @Inject constructor(private val audioProcessingSettingsStore: AudioProcessingSettingsStore) :
    ViewModel() {

    val audioProcessingState = audioProcessingSettingsStore.getFlow().map { settings ->
        AudioProcessingState(
            audioSource = audioSources.firstOrNull { it.key == settings.audioSource }
                ?: audioSources.first { it.key == MediaRecorder.AudioSource.VOICE_RECOGNITION },
            communicationMode = settings.audioMode == AudioManager.MODE_IN_COMMUNICATION,
            speakerphone = settings.speakerphone,
        )
    }

    fun saveAudioSource(audioSource: SelectItem) = viewModelScope.launch {
        audioProcessingSettingsStore.audioSource.set(audioSource.key)
    }

    fun saveAudioMode(communicationMode: Boolean) = viewModelScope.launch {
        audioProcessingSettingsStore.audioMode.set(
            if (communicationMode) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        )
    }

    fun saveSpeakerphone(speakerphone: Boolean) = viewModelScope.launch {
        audioProcessingSettingsStore.speakerphone.set(speakerphone)
    }
}