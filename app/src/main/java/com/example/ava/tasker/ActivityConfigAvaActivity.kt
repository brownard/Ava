package com.example.ava.tasker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ava.R
import com.example.ava.ui.screens.settings.components.SwitchSetting
import com.example.ava.ui.theme.AvaTheme
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput

class AvaActivityHelper(config: TaskerPluginConfig<AvaActivityInput>) :
    TaskerPluginConfigHelper<AvaActivityInput, Unit, AvaActivityRunner>(config) {
    override val runnerClass = AvaActivityRunner::class.java
    override val inputClass = AvaActivityInput::class.java
    override val outputClass = Unit::class.java
}

@OptIn(ExperimentalMaterial3Api::class)
class ActivityConfigAvaActivity : ComponentActivity(),
    TaskerPluginConfig<AvaActivityInput> {
    override val context get() = applicationContext
    private val taskerHelper by lazy { AvaActivityHelper(this) }

    private var inputState = AvaActivityInput()

    override val inputForTasker: TaskerInput<AvaActivityInput>
        get() = TaskerInput(inputState)

    override fun assignFromInput(input: TaskerInput<AvaActivityInput>) {
        inputState = input.regular
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvaTheme {
                var state by remember { mutableStateOf(inputState) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            colors = topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            title = {
                                Text(stringResource(R.string.tasker_condition_ava_activity))
                            },
                            actions = {
                                TextButton(
                                    onClick = {
                                        inputState = state
                                        taskerHelper.finishForTasker()
                                    }
                                ) {
                                    Text(stringResource(R.string.label_save))
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Text(
                            text = stringResource(R.string.tasker_condition_description),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        SwitchSetting(
                            name = stringResource(R.string.tasker_filter_conversing),
                            description = stringResource(R.string.tasker_filter_conversing_description),
                            value = state.conversing,
                            onCheckedChange = { state = state.copy(conversing = it) }
                        )
                        SwitchSetting(
                            name = stringResource(R.string.tasker_filter_timer_ringing),
                            description = stringResource(R.string.tasker_filter_timer_ringing_description),
                            value = state.timerRinging,
                            onCheckedChange = { state = state.copy(timerRinging = it) }
                        )
                        SwitchSetting(
                            name = stringResource(R.string.tasker_filter_timer_running),
                            description = stringResource(R.string.tasker_filter_timer_running_description),
                            value = state.timerRunning,
                            onCheckedChange = { state = state.copy(timerRunning = it) }
                        )
                        SwitchSetting(
                            name = stringResource(R.string.tasker_filter_timer_paused),
                            description = stringResource(R.string.tasker_filter_timer_paused_description),
                            value = state.timerPaused,
                            onCheckedChange = { state = state.copy(timerPaused = it) }
                        )
                    }
                }
            }
        }
        taskerHelper.onCreate()
    }
}
