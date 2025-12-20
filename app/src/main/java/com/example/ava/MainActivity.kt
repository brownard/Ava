package com.example.ava

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.example.ava.permissions.VOICE_SATELLITE_PERMISSIONS
import com.example.ava.ui.MainNavHost
import com.example.ava.ui.services.ServiceViewModel
import com.example.ava.ui.services.rememberLaunchWithMultiplePermissions
import com.example.ava.ui.theme.AvaTheme

class MainActivity : ComponentActivity() {
    private var created = false
    private val serviceViewModel: ServiceViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvaTheme {
                OnCreate()
                MainNavHost()
            }
        }
    }

    @Composable
    fun OnCreate() {
        val permissionsLauncher = rememberLaunchWithMultiplePermissions(
            onPermissionGranted = { serviceViewModel.autoStartServiceIfRequired() }
        )
        DisposableEffect(Unit) {
            permissionsLauncher.launch(VOICE_SATELLITE_PERMISSIONS)
            onDispose { }
        }
    }
}