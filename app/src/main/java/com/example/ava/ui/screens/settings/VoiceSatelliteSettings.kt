package com.example.ava.ui.screens.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ava.R
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
            SelectSetting(
                name = stringResource(R.string.label_voice_satellite_wake_word),
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
    }
}