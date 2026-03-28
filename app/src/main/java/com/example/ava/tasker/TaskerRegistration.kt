package com.example.ava.tasker

import android.content.Context
import com.example.ava.esphome.EspHomeDevice
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Registers Tasker actions and passes device state to Tasker conditions until cancelled.
 */
suspend fun EspHomeDevice.observeTaskerState(context: Context) = combine(
    voiceAssistant.state,
    voiceAssistant.allTimers
) { state, timers ->
    AvaActivityRunner.updateState(state, timers)
    ActivityConfigAvaActivity::class.java.requestQuery(context)
}.onStart {
    registerTaskerActions()
}.onCompletion {
    unregisterTaskerActions()
}
    // Although implemented as a flow, conceptually this method is just a long running
    // background task as nothing is ever emitted, collect hides the implementation
    // detail and surfaces a cancellable suspend function instead
    .collect { }

private fun EspHomeDevice.registerTaskerActions() {
    WakeSatelliteRunner.register { voiceAssistant.wakeAssistant() }
    StopRingingRunner.register { voiceAssistant.stopTimer() }
}

private fun unregisterTaskerActions() {
    WakeSatelliteRunner.unregister()
    StopRingingRunner.unregister()
}