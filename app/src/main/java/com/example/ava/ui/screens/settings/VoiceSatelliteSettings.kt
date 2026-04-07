package com.example.ava.ui.screens.settings

import android.media.MediaRecorder
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ava.R
import com.example.ava.settings.defaultTimerFinishedSound
import com.example.ava.settings.defaultWakeSound
import com.example.ava.ui.screens.settings.components.DocumentSetting
import com.example.ava.ui.screens.settings.components.DocumentTreeSetting
import com.example.ava.ui.screens.settings.components.IntSetting
import com.example.ava.ui.screens.settings.components.SelectSetting
import com.example.ava.ui.screens.settings.components.SwitchSetting
import com.example.ava.ui.screens.settings.components.TextSetting
import kotlinx.coroutines.launch

private data class AudioSourceOption(val source: Int, val label: String)

@Composable
fun VoiceSatelliteSettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val satelliteState by viewModel.satelliteSettingsState.collectAsStateWithLifecycle(null)
    val microphoneState by viewModel.microphoneSettingsState.collectAsStateWithLifecycle(null)
    val playerState by viewModel.playerSettingsState.collectAsStateWithLifecycle(null)
    val disabledLabel = stringResource(R.string.label_disabled)

    val audioSources = listOf(
        AudioSourceOption(MediaRecorder.AudioSource.VOICE_RECOGNITION, stringResource(R.string.audio_source_voice_recognition)),
        AudioSourceOption(MediaRecorder.AudioSource.MIC, stringResource(R.string.audio_source_mic)),
        AudioSourceOption(MediaRecorder.AudioSource.VOICE_COMMUNICATION, stringResource(R.string.audio_source_voice_communication)),
        AudioSourceOption(MediaRecorder.AudioSource.UNPROCESSED, stringResource(R.string.audio_source_unprocessed))
    )

    LazyColumn(
        modifier = modifier
    ) {
        val enabled = satelliteState != null
        item {
            TextSetting(
                name = stringResource(R.string.label_voice_satellite_name),
                value = satelliteState?.name ?: "",
                enabled = enabled,
                validation = { viewModel.validateName(it) },
                onConfirmRequest = {
                    coroutineScope.launch {
                        viewModel.saveName(it)
                    }
                }
            )
        }
        item {
            IntSetting(
                name = stringResource(R.string.label_voice_satellite_port),
                value = satelliteState?.serverPort,
                enabled = enabled,
                validation = { viewModel.validatePort(it) },
                onConfirmRequest = {
                    coroutineScope.launch {
                        viewModel.saveServerPort(it)
                    }
                }
            )
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_voice_satellite_autostart),
                description = stringResource(R.string.description_voice_satellite_autostart),
                value = satelliteState?.autoStart ?: false,
                enabled = enabled,
                onCheckedChange = {
                    coroutineScope.launch {
                        viewModel.saveAutoStart(it)
                    }
                }
            )
        }
        item {
            IntSetting(
                name = stringResource(R.string.label_screen_idle_timeout),
                description = stringResource(R.string.description_screen_idle_timeout),
                value = satelliteState?.screenIdleTimeoutSeconds,
                enabled = enabled,
                validation = { viewModel.validateScreenIdleTimeoutSeconds(it) },
                onConfirmRequest = {
                    coroutineScope.launch {
                        viewModel.saveScreenIdleTimeoutSeconds(it)
                    }
                }
            )
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_allow_rotation),
                description = stringResource(R.string.description_allow_rotation),
                value = satelliteState?.allowRotation ?: false,
                enabled = enabled,
                onCheckedChange = {
                    coroutineScope.launch {
                        viewModel.saveAllowRotation(it)
                    }
                }
            )
        }
        item {
            HorizontalDivider()
        }
        item {
            SelectSetting(
                name = stringResource(R.string.label_voice_satellite_first_wake_word),
                selected = microphoneState?.wakeWord,
                items = microphoneState?.wakeWords,
                enabled = enabled,
                key = { it.id },
                value = { it?.wakeWord?.wake_word ?: "" },
                onConfirmRequest = {
                    if (it != null) {
                        coroutineScope.launch {
                            viewModel.saveWakeWord(it.id)
                        }
                    }
                }
            )
        }
        item {
            SelectSetting(
                name = stringResource(R.string.label_voice_satellite_second_wake_word),
                selected = microphoneState?.secondWakeWord,
                items = microphoneState?.wakeWords,
                enabled = enabled,
                key = { it.id },
                value = { it?.wakeWord?.wake_word ?: disabledLabel },
                onClearRequest = {
                    coroutineScope.launch {
                        viewModel.saveSecondWakeWord(null)
                    }
                },
                onConfirmRequest = {
                    if (it != null) {
                        coroutineScope.launch {
                            viewModel.saveSecondWakeWord(it.id)
                        }
                    }
                }
            )
        }
        item {
            DocumentTreeSetting(
                name = stringResource(R.string.label_custom_wake_words),
                description = stringResource(R.string.description_custom_wake_word_location),
                value = microphoneState?.customWakeWordLocation,
                enabled = enabled,
                onResult = {
                    if (it != null) {
                        coroutineScope.launch {
                            viewModel.saveCustomWakeWordDirectory(it)
                        }
                    }
                },
                onClearRequest = {
                    coroutineScope.launch { viewModel.resetCustomWakeWordDirectory() }
                }
            )
        }
        item {
            SelectSetting(
                name = stringResource(R.string.label_audio_source),
                description = stringResource(R.string.description_audio_source),
                selected = audioSources.firstOrNull { it.source == microphoneState?.audioSource },
                items = audioSources,
                enabled = enabled,
                key = { it.source },
                value = { it?.label ?: "" },
                onConfirmRequest = { option ->
                    if (option != null) coroutineScope.launch {
                        viewModel.saveAudioSource(option.source)
                    }
                }
            )
        }
        item {
            IntSetting(
                name = stringResource(R.string.label_mic_gain_db),
                description = stringResource(R.string.description_mic_gain_db),
                value = microphoneState?.micGainDb,
                enabled = enabled,
                validation = { viewModel.validateMicGainDb(it) },
                onConfirmRequest = {
                    coroutineScope.launch { viewModel.saveMicGainDb(it) }
                }
            )
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_noise_suppressor),
                description = stringResource(R.string.description_noise_suppressor),
                value = microphoneState?.enableNoiseSuppressor ?: true,
                enabled = enabled,
                onCheckedChange = {
                    coroutineScope.launch { viewModel.saveEnableNoiseSuppressor(it) }
                }
            )
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_automatic_gain_control),
                description = stringResource(R.string.description_automatic_gain_control),
                value = microphoneState?.enableAutomaticGainControl ?: true,
                enabled = enabled,
                onCheckedChange = {
                    coroutineScope.launch { viewModel.saveEnableAutomaticGainControl(it) }
                }
            )
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_acoustic_echo_canceler),
                description = stringResource(R.string.description_acoustic_echo_canceler),
                value = microphoneState?.enableAcousticEchoCanceler ?: true,
                enabled = enabled,
                onCheckedChange = {
                    coroutineScope.launch { viewModel.saveEnableAcousticEchoCanceler(it) }
                }
            )
        }
        item {
            TextSetting(
                name = stringResource(R.string.label_probability_cutoff),
                description = stringResource(R.string.description_probability_cutoff),
                value = microphoneState?.probabilityCutoffOverride?.toString() ?: "",
                enabled = enabled,
                validation = { viewModel.validateProbabilityCutoff(it) },
                onConfirmRequest = {
                    coroutineScope.launch { viewModel.saveProbabilityCutoffOverride(it) }
                }
            )
        }
        item {
            IntSetting(
                name = stringResource(R.string.label_sliding_window_size),
                description = stringResource(R.string.description_sliding_window_size),
                value = microphoneState?.slidingWindowSizeOverride,
                enabled = enabled,
                validation = { viewModel.validateSlidingWindowSize(it) },
                onConfirmRequest = {
                    coroutineScope.launch { viewModel.saveSlidingWindowSizeOverride(it) }
                }
            )
        }
        item {
            HorizontalDivider()
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_voice_satellite_enable_wake_sound),
                description = stringResource(R.string.description_voice_satellite_play_wake_sound),
                value = playerState?.enableWakeSound ?: true,
                enabled = enabled,
                onCheckedChange = {
                    coroutineScope.launch {
                        viewModel.saveEnableWakeSound(it)
                    }
                }
            )
        }
        item {
            DocumentSetting(
                name = stringResource(R.string.label_custom_wake_sound),
                description = stringResource(R.string.description_custom_wake_sound_location),
                value = if (playerState?.wakeSound != defaultWakeSound) playerState?.wakeSound?.toUri() else null,
                enabled = enabled,
                mimeTypes = arrayOf("audio/*"),
                onResult = {
                    if (it != null) {
                        coroutineScope.launch {
                            viewModel.saveWakeSound(it)
                        }
                    }
                },
                onClearRequest = {
                    coroutineScope.launch { viewModel.resetWakeSound() }
                }
            )
        }
        item {
            HorizontalDivider()
        }
        item {
            DocumentSetting(
                name = stringResource(R.string.label_custom_timer_sound),
                description = stringResource(R.string.description_custom_timer_sound_location),
                value = if (playerState?.timerFinishedSound != defaultTimerFinishedSound) playerState?.timerFinishedSound?.toUri() else null,
                enabled = enabled,
                mimeTypes = arrayOf("audio/*"),
                onResult = {
                    if (it != null) {
                        coroutineScope.launch {
                            viewModel.saveTimerFinishedSound(it)
                        }
                    }
                },
                onClearRequest = {
                    coroutineScope.launch { viewModel.resetTimerFinishedSound() }
                }
            )
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_timer_sound_repeat),
                description = stringResource(R.string.description_timer_sound_repeat),
                value = playerState?.repeatTimerFinishedSound ?: true,
                enabled = enabled,
                onCheckedChange = {
                    coroutineScope.launch {
                        viewModel.saveRepeatTimerFinishedSound(it)
                    }
                }
            )
        }
        item {
            HorizontalDivider()
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_voice_satellite_enable_error_sound),
                description = stringResource(R.string.description_voice_satellite_enable_error_sound),
                value = playerState?.enableErrorSound ?: false,
                enabled = enabled,
                onCheckedChange = {
                    coroutineScope.launch {
                        viewModel.saveEnableErrorSound(it)
                    }
                }
            )
        }
        item {
            DocumentSetting(
                name = stringResource(R.string.label_custom_error_sound),
                description = stringResource(R.string.description_custom_error_sound_location),
                value = playerState?.errorSound?.toUri(),
                enabled = enabled,
                mimeTypes = arrayOf("audio/*"),
                onResult = {
                    if (it != null) {
                        coroutineScope.launch {
                            viewModel.saveErrorSound(it)
                        }
                    }
                },
                onClearRequest = {
                    coroutineScope.launch { viewModel.resetErrorSound() }
                }
            )
        }
    }
}