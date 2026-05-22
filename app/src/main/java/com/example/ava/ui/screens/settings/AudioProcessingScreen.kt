package com.example.ava.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ava.R
import com.example.ava.ui.screens.BackNavigationScreen
import com.example.ava.ui.screens.settings.components.SelectSetting
import com.example.ava.ui.screens.settings.components.SwitchSetting

private const val HELP_URI = "https://github.com/brownard/Ava/blob/master/docs/AUDIO_PROCESSING.md"

@Composable
fun HelpButton(uri: String) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = uri.toUri()
            }
            context.startActivity(intent)
        }
    ) {
        Icon(
            painter = painterResource(R.drawable.help_24px),
            contentDescription = stringResource(R.string.label_help),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun AudioProcessingScreen(
    navController: NavController,
    viewModel: AudioProcessingViewModel = hiltViewModel()
) = BackNavigationScreen(
    navController = navController,
    title = stringResource(R.string.label_audio_processing),
    topBarActions = { HelpButton(HELP_URI) }
) { innerPadding ->
    val audioProcessingState by viewModel.audioProcessingState.collectAsStateWithLifecycle(null)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        val enabled = audioProcessingState != null

        item {
            SelectSetting(
                name = stringResource(R.string.label_audio_source),
                description = stringResource(R.string.description_audio_source),
                selected = audioProcessingState?.audioSource,
                items = audioSources,
                enabled = enabled,
                key = { it.key },
                value = { it?.value ?: "" },
                itemDescription = { it.descriptionResource?.let { id -> stringResource(id) } },
                onConfirmRequest = {
                    if (it != null) {
                        viewModel.saveAudioSource(it)
                    }
                }
            )
        }

        item {
            SwitchSetting(
                name = stringResource(R.string.label_communication_mode),
                description = stringResource(R.string.description_communication_mode),
                value = audioProcessingState?.communicationMode ?: false,
                enabled = enabled,
                onCheckedChange = { viewModel.saveAudioMode(it) }
            )
        }

        item {
            SwitchSetting(
                name = stringResource(R.string.label_speakerphone),
                description = stringResource(R.string.description_speakerphone),
                value = audioProcessingState?.speakerphone ?: false,
                enabled = enabled,
                onCheckedChange = { viewModel.saveSpeakerphone(it) }
            )
        }
    }
}