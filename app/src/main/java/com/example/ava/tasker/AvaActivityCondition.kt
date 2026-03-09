package com.example.ava.tasker

import android.content.Context
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.voicesatellite.Listening
import com.example.ava.esphome.voicesatellite.Processing
import com.example.ava.esphome.voicesatellite.Responding
import com.example.ava.esphome.voicesatellite.VoiceTimer
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionState
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import timber.log.Timber

/**
 * Holds the tasker configuration for evaluating the state condition.
 *
 * Because of limitations in Tasker, it cannot be a data class, so toString and copy
 * are implemented manually.
 */
@TaskerInputRoot
class AvaActivityInput @JvmOverloads constructor(
    @field:TaskerInputField("conversing", labelResIdName = "tasker_filter_conversing")
    val conversing: Boolean = true,
    @field:TaskerInputField("timer_ringing", labelResIdName = "tasker_filter_timer_ringing")
    val timerRinging: Boolean = true,
    @field:TaskerInputField("timer_running", labelResIdName = "tasker_filter_timer_running")
    val timerRunning: Boolean = false,
    @field:TaskerInputField("timer_paused", labelResIdName = "tasker_filter_timer_paused")
    val timerPaused: Boolean = false
) {
    override fun toString(): String =
        "AvaActivityInput(conversing=$conversing, timerRinging=$timerRinging, timerRunning=$timerRunning, timerPaused=$timerPaused)"

    fun copy(
        conversing: Boolean = this.conversing,
        timerRinging: Boolean = this.timerRinging,
        timerRunning: Boolean = this.timerRunning,
        timerPaused: Boolean = this.timerPaused
    ): AvaActivityInput = AvaActivityInput(conversing, timerRinging, timerRunning, timerPaused)
}

val conversingStates = setOf(Listening, Processing, Responding)

/**
 * Holds the condition evaluation logic: Tasker calls getSatisfiedCondition when
 * we signal a change (updateState is called by VoiceSatelliteService).
 *
 * Because of limitations in Tasker, we cannot hold a reactive state, so we get
 * state pushed in through a companion object.
 */
class AvaActivityRunner :
    TaskerPluginRunnerConditionState<AvaActivityInput, Unit>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<AvaActivityInput>,
        update: Unit?
    ): TaskerPluginResultCondition<Unit> {
        val filter = input.regular
        Timber.d("Evaluating tasker condition: $filter - $currentState - $currentTimers")

        val timers = currentTimers ?: return TaskerPluginResultConditionUnsatisfied()
        val satisfied = when {
            filter.conversing && currentState in conversingStates -> true
            filter.timerRinging && timers.any { it is VoiceTimer.Ringing } -> true
            filter.timerRunning && timers.any { it is VoiceTimer.Running } -> true
            filter.timerPaused && timers.any { it is VoiceTimer.Paused } -> true
            else -> false
        }

        Timber.d("Evaluated to $satisfied")
        return if (satisfied) {
            TaskerPluginResultConditionSatisfied(context)
        } else {
            TaskerPluginResultConditionUnsatisfied()
        }
    }

    companion object {
        var currentState: EspHomeState? = null
            private set
        var currentTimers: List<VoiceTimer>? = null
            private set

        fun updateState(state: EspHomeState, timers: List<VoiceTimer>) {
            Timber.d("Tasker state updating: state=$state, timers=${timers.size}")
            currentState = state
            currentTimers = timers
        }
    }
}
