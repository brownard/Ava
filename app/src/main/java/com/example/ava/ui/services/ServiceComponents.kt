package com.example.ava.ui.services

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ava.R
import com.example.ava.esphome.Connected
import com.example.ava.esphome.Disconnected
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.ServerError
import com.example.ava.esphome.Stopped
import com.example.ava.permissions.VOICE_SATELLITE_PERMISSIONS
import com.example.ava.utils.translate

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