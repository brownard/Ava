package com.example.ava.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ava.R
import com.example.ava.settings.defaultTimerFinishedSound
import com.example.ava.settings.defaultWakeSound
import com.example.ava.ui.screens.BackNavigationScreen
import com.example.ava.ui.screens.settings.components.DocumentSetting
import com.example.ava.ui.screens.settings.components.DocumentTreeSetting
import com.example.ava.ui.screens.settings.components.IntSetting
import com.example.ava.ui.screens.settings.components.SelectSetting
import com.example.ava.ui.screens.settings.components.SwitchSetting
import com.example.ava.ui.screens.settings.components.TextSetting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) = BackNavigationScreen(
    navController = navController,
    title = stringResource(R.string.label_settings),
) { innerPadding ->
    val satelliteState by viewModel.satelliteSettingsState.collectAsStateWithLifecycle(null)
    val microphoneState by viewModel.microphoneSettingsState.collectAsStateWithLifecycle(null)
    val playerState by viewModel.playerSettingsState.collectAsStateWithLifecycle(null)
    val disabledLabel = stringResource(R.string.label_disabled)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        val enabled = satelliteState != null
        item {
            TextSetting(
                name = stringResource(R.string.label_voice_satellite_name),
                value = satelliteState?.name ?: "",
                enabled = enabled,
                validation = { viewModel.validateName(it) },
                onConfirmRequest = { viewModel.saveName(it) }
            )
        }
        item {
            IntSetting(
                name = stringResource(R.string.label_voice_satellite_port),
                value = satelliteState?.serverPort,
                enabled = enabled,
                validation = { viewModel.validatePort(it) },
                onConfirmRequest = { viewModel.saveServerPort(it) }
            )
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_voice_satellite_autostart),
                description = stringResource(R.string.description_voice_satellite_autostart),
                value = satelliteState?.autoStart ?: false,
                enabled = enabled,
                onCheckedChange = { viewModel.saveAutoStart(it) }
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
                        viewModel.saveWakeWord(it.id, microphoneState?.wakeWords ?: emptyList())
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
                onClearRequest = { viewModel.saveSecondWakeWord(null, null) },
                onConfirmRequest = {
                    if (it != null) {
                        viewModel.saveSecondWakeWord(it.id, microphoneState?.wakeWords)
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
                        viewModel.saveCustomWakeWordDirectory(it)
                    }
                },
                onClearRequest = { viewModel.resetCustomWakeWordDirectory() }
            )
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_voice_satellite_enable_wake_sound),
                description = stringResource(R.string.description_voice_satellite_play_wake_sound),
                value = playerState?.enableWakeSound ?: true,
                enabled = enabled,
                onCheckedChange = { viewModel.saveEnableWakeSound(it) }
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
                        viewModel.saveWakeSound(it)
                    }
                },
                onClearRequest = {
                    viewModel.resetWakeSound()
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
                        viewModel.saveTimerFinishedSound(it)
                    }
                },
                onClearRequest = { viewModel.resetTimerFinishedSound() }
            )
        }
        item {
            SwitchSetting(
                name = stringResource(R.string.label_timer_sound_repeat),
                description = stringResource(R.string.description_timer_sound_repeat),
                value = playerState?.repeatTimerFinishedSound ?: true,
                enabled = enabled,
                onCheckedChange = { viewModel.saveRepeatTimerFinishedSound(it) }
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
                onCheckedChange = { viewModel.saveEnableErrorSound(it) }
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
                        viewModel.saveErrorSound(it)
                    }
                },
                onClearRequest = { viewModel.resetErrorSound() }
            )
        }
    }
}