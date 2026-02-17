package com.example.ava.ui.screens.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
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
import com.example.ava.ui.screens.settings.components.DocumentSetting
import com.example.ava.ui.screens.settings.components.DocumentTreeSetting
import com.example.ava.ui.screens.settings.components.IntSetting
import com.example.ava.ui.screens.settings.components.SelectSetting
import com.example.ava.ui.screens.settings.components.SwitchSetting
import com.example.ava.ui.screens.settings.components.TextSetting
import kotlinx.coroutines.launch

@Composable
fun VoiceSatelliteSettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val satelliteState by viewModel.satelliteSettingsState.collectAsStateWithLifecycle(null)
    val microphoneState by viewModel.microphoneSettingsState.collectAsStateWithLifecycle(null)
    val playerState by viewModel.playerSettingsState.collectAsStateWithLifecycle(null)
    val displayState by viewModel.displaySettingsState.collectAsStateWithLifecycle(null)
    val disabledLabel = stringResource(R.string.label_disabled)

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
            Divider()
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
            Divider()
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
            Divider()
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_wake_screen),
                description = stringResource(R.string.description_wake_screen),
                value = displayState?.wakeScreen ?: false,
                enabled = enabled,
                onCheckedChange = {
                    coroutineScope.launch {
                        viewModel.saveWakeScreen(it)
                    }
                }
            )
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_hide_system_bars),
                description = stringResource(R.string.description_hide_system_bars),
                value = displayState?.hideSystemBars ?: false,
                enabled = enabled,
                onCheckedChange = {
                    coroutineScope.launch {
                        viewModel.saveHideSystemBars(it)
                    }
                }
            )
        }
    }
}
