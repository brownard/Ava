package com.example.ava.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.example.ava.R
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutputOrInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutputOrInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess

class WakeSatelliteHelper(config: TaskerPluginConfig<Unit>) :
    TaskerPluginConfigHelperNoOutputOrInput<WakeSatelliteRunner>(config) {
    override val runnerClass get() = WakeSatelliteRunner::class.java
}

class ActivityConfigWakeSatellite : Activity(), TaskerPluginConfigNoInput {
    override val context: Context get() = this
    private val taskerHelper by lazy { WakeSatelliteHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskerHelper.finishForTasker()
    }
}

class WakeSatelliteRunner : TaskerPluginRunnerActionNoOutputOrInput() {
    override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
        val cb = callback
            ?: return TaskerPluginResultError(Exception(context.getString(R.string.tasker_action_error_not_running)))
        cb()
        return TaskerPluginResultSucess()
    }

    companion object {
        @Volatile
        private var callback: (() -> Unit)? = null

        fun register(cb: () -> Unit) {
            callback = cb
        }

        fun unregister() {
            callback = null
        }
    }
}
