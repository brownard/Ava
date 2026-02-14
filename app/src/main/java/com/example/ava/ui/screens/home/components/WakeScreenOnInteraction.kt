package com.example.ava.ui.screens.home.components

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ava.esphome.voicesatellite.Listening
import com.example.ava.esphome.voicesatellite.Processing
import com.example.ava.esphome.voicesatellite.Responding
import com.example.ava.esphome.voicesatellite.VoiceTimer
import com.example.ava.ui.services.ServiceViewModel


@Composable
fun WakeScreenOnInteraction(viewModel: ServiceViewModel = hiltViewModel()) {
    val timers by viewModel.voiceTimers.collectAsStateWithLifecycle(emptyList())
    val currentState by viewModel.satelliteState.collectAsStateWithLifecycle(null)
    val wakingStates = setOf(Listening, Processing, Responding)

    if (wakingStates.contains(currentState) || timers.any({ it !is VoiceTimer.Paused })) {
        val window = LocalActivity.current?.window
        if (window != null) {
            DisposableEffect("WakeScreenOnInteraction") {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }
}