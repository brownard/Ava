package com.example.ava

import android.app.KeyguardManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.lifecycleScope
import com.example.ava.permissions.VOICE_SATELLITE_PERMISSIONS
import com.example.ava.ui.MainNavHost
import com.example.ava.ui.services.ServiceViewModel
import com.example.ava.ui.services.rememberLaunchWithMultiplePermissions
import com.example.ava.ui.theme.AvaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val serviceViewModel: ServiceViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Allow the activity to show over the lock screen and turn the screen on.
        // Combined with the service's ScreenWakeLock (ACQUIRE_CAUSES_WAKEUP), this
        // ensures the app is visible when a wake word fires with the screen off.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Dismiss the keyguard on devices with no lock screen security.
        // On password-protected devices this is a no-op (no auth UI is shown from here).
        (getSystemService(KEYGUARD_SERVICE) as KeyguardManager)
            .requestDismissKeyguard(this, null)
        lifecycleScope.launch {
            serviceViewModel.allowRotation.collect { allow ->
                requestedOrientation = if (allow) {
                    ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }
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