package com.example.ava.ui.services

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ava.R
import com.example.ava.esphome.Connected
import com.example.ava.esphome.Disconnected
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.ServerError
import com.example.ava.esphome.Stopped
import com.example.ava.permissions.VOICE_SATELLITE_PERMISSIONS
import com.example.ava.utils.translate

@Composable
fun StartStopVoiceSatellite(viewModel: ServiceViewModel = viewModel()) {
    val service by viewModel.satellite.collectAsStateWithLifecycle(null)
    val currentService = service
    if (currentService == null) {
        Text(
            text = "Service disconnected",
            color = MaterialTheme.colorScheme.error
        )
    } else {
        val serviceState by currentService.voiceSatelliteState.collectAsStateWithLifecycle(
            Stopped
        )

        val resources = LocalResources.current
        Text(
            text = remember(serviceState) { serviceState.translate(resources) },
            color = stateColor(serviceState),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        StartStopWithPermissionsButton(
            permissions = VOICE_SATELLITE_PERMISSIONS,
            isStarted = serviceState !is Stopped,
            onStart = { currentService.startVoiceSatellite() },
            onStop = { currentService.stopVoiceSatellite() },
            onPermissionDenied = { /*TODO*/ }
        )
    }
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
    val content =
        stringResource(if (isStarted) R.string.label_stop_service else R.string.label_start_service)
    ExtendedFloatingActionButton(
        onClick = {
            if (isStarted)
                onStop()
            else
                registerPermissionsResult.launch(permissions)
        }
    ) {
        Text(
            text = content,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun rememberLaunchWithMultiplePermissions(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: (deniedPermissions: Array<String>) -> Unit = { }
): ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>> {
    val registerPermissionsResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val deniedPermissions = granted.filter { !it.value }.keys.toTypedArray()
        if (deniedPermissions.isEmpty()) {
            onPermissionGranted()
        } else {
            onPermissionDenied(deniedPermissions)
        }
    }
    return registerPermissionsResult
}