package com.example.ava.ui.services

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.ava.R
import com.example.ava.esphome.Connected
import com.example.ava.esphome.Disconnected
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.ServerError
import com.example.ava.esphome.Stopped
import com.example.ava.permissions.VOICE_SATELLITE_PERMISSIONS
import com.example.ava.utils.translate
import com.example.esphomeproto.api.MediaPlayerState

@Composable
fun StartStopVoiceSatellite(viewModel: ServiceViewModel = hiltViewModel()) {
    val service by viewModel.satellite.collectAsStateWithLifecycle(null)
    val currentService = service

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentService == null) {
                StatusText(
                    text = "Service disconnected",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                val serviceState by currentService.voiceSatelliteState.collectAsStateWithLifecycle(
                    Stopped
                )
                val resources = LocalResources.current

                StatusText(
                    text = remember(serviceState) { serviceState.translate(resources) },
                    color = stateColor(serviceState)
                )

                StartStopWithPermissionsButton(
                    permissions = VOICE_SATELLITE_PERMISSIONS,
                    isStarted = serviceState !is Stopped,
                    onStart = { currentService.startVoiceSatellite() },
                    onStop = { currentService.stopVoiceSatellite() },
                    onPermissionDenied = { /*TODO*/ }
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun stateColor(state: EspHomeState) = when (state) {
    is Stopped, is Disconnected, is ServerError -> MaterialTheme.colorScheme.error
    is Connected -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun StartStopWithPermissionsButton(
    permissions: Array<String>,
    isStarted: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPermissionDenied: (deniedPermissions: Array<String>) -> Unit
) {
    val registerPermissionsResult = rememberLaunchWithMultiplePermissions(
        onPermissionGranted = onStart,
        onPermissionDenied = onPermissionDenied
    )

    val label = stringResource(if (isStarted) R.string.label_stop_service else R.string.label_start_service)

    Button(
        onClick = {
            if (isStarted) onStop()
            else registerPermissionsResult.launch(permissions)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isStarted) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.primary,
            contentColor = if (isStarted) MaterialTheme.colorScheme.onSecondary
            else MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier.fillMaxWidth(0.7f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun rememberLaunchWithMultiplePermissions(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: (deniedPermissions: Array<String>) -> Unit = { }
): ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val deniedPermissions = granted.filter { !it.value }.keys.toTypedArray()
        if (deniedPermissions.isEmpty()) {
            onPermissionGranted()
        } else {
            onPermissionDenied(deniedPermissions)
        }
    }
}

@Composable
fun MediaPlayerCard(viewModel: ServiceViewModel = hiltViewModel()) {
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle(MediaPlayerState.MEDIA_PLAYER_STATE_IDLE)
    if (mediaState == MediaPlayerState.MEDIA_PLAYER_STATE_IDLE) return

    val mediaTitle by viewModel.mediaTitle.collectAsStateWithLifecycle(null)
    val mediaArtist by viewModel.mediaArtist.collectAsStateWithLifecycle(null)
    val mediaArtworkData by viewModel.mediaArtworkData.collectAsStateWithLifecycle(null)
    val mediaArtworkUri by viewModel.mediaArtworkUri.collectAsStateWithLifecycle(null)
    val mediaPosition by viewModel.mediaPosition.collectAsStateWithLifecycle(0L)
    val mediaDuration by viewModel.mediaDuration.collectAsStateWithLifecycle(0L)

    val isPlaying = mediaState == MediaPlayerState.MEDIA_PLAYER_STATE_PLAYING

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art — shown when artwork bytes or URI is available
                val artworkModel = mediaArtworkData ?: mediaArtworkUri
                if (artworkModel != null) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                // Title + artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaTitle ?: if (isPlaying) "Playing" else "Paused",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (mediaArtist != null) {
                        Text(
                            text = mediaArtist!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(
                        painter = painterResource(R.drawable.skip_previous_24px),
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { viewModel.toggleMediaPlayback() }) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(
                        painter = painterResource(R.drawable.skip_next_24px),
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            // Progress bar + timestamps
            if (mediaDuration > 0) {
                LinearProgressIndicator(
                    progress = { (mediaPosition.toFloat() / mediaDuration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatMediaDuration(mediaPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatMediaDuration(mediaDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatMediaDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}