package com.example.ava.ui.screens.home.components

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.voicesatellite.Listening
import com.example.ava.esphome.voicesatellite.Processing
import com.example.ava.esphome.voicesatellite.Responding
import com.example.ava.esphome.voicesatellite.VoiceTimer
import kotlin.collections.contains

private val wakingStates = setOf(Listening, Processing, Responding)

@Composable
fun WakeScreenOnInteraction(window: Window, timers: List<VoiceTimer>, satelliteState: EspHomeState?) {
    val isInteracting = remember(timers, satelliteState) {
        wakingStates.contains(satelliteState) || timers.any { it !is VoiceTimer.Paused }
    }
    if (isInteracting) {
        DisposableEffect(Unit) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}
