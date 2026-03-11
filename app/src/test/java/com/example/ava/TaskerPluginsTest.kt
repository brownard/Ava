package com.example.ava

import android.content.ContextWrapper
import androidx.compose.material3.TimeInput
import com.example.ava.esphome.voicesatellite.AudioResult
import com.example.ava.esphome.voicesatellite.Listening
import com.example.ava.esphome.voicesatellite.VoiceSatellite
import com.example.ava.esphome.voicesatellite.VoiceSatelliteAudioInput
import com.example.ava.esphome.voicesatellite.VoiceSatellitePlayer
import com.example.ava.server.Server
import com.example.ava.stubs.StubAudioPlayer
import com.example.ava.stubs.StubServer
import com.example.ava.stubs.StubVoiceSatelliteAudioInput
import com.example.ava.stubs.StubVoiceSatellitePlayer
import com.example.ava.stubs.StubVoiceSatelliteSettingsStore
import com.example.ava.stubs.stubSettingState
import com.example.ava.tasker.AvaActivityInput
import com.example.ava.tasker.AvaActivityRunner
import com.example.ava.tasker.StopRingingRunner
import com.example.ava.tasker.WakeSatelliteRunner
import com.example.esphomeproto.api.VoiceAssistantRequest
import com.example.esphomeproto.api.VoiceAssistantTimerEvent
import com.example.esphomeproto.api.voiceAssistantTimerEventResponse
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class TaskerPluginsTest {
    private val dummyContext = ContextWrapper(null)

    private fun TestScope.createSatellite(
        server: Server = StubServer(),
        audioInput: VoiceSatelliteAudioInput = StubVoiceSatelliteAudioInput(),
        player: VoiceSatellitePlayer = StubVoiceSatellitePlayer()
    ) = VoiceSatellite(
        coroutineContext = this.coroutineContext,
        "Test Satellite",
        server = server,
        audioInput = audioInput,
        player = player,
        settingsStore = StubVoiceSatelliteSettingsStore()
    ).apply {
        start()
        advanceUntilIdle()
    }

    @Test
    fun should_handle_wake_satellite_action() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val satellite = createSatellite(server = server, audioInput = audioInput)
        advanceUntilIdle()

        val result = WakeSatelliteRunner().run(dummyContext, TaskerInput(Unit))
        assert(result is TaskerPluginResultSucess)
        advanceUntilIdle()

        assertEquals(Listening, satellite.state.value)
        assertEquals(true, audioInput.isStreaming)
        assertEquals(1, server.sentMessages.size)
        assertEquals(true, (server.sentMessages[0] as VoiceAssistantRequest).start)

        satellite.close()
    }

    @Test
    fun should_handle_stop_ringing_action() = runTest {
        val server = StubServer()
        val ttsPlayer = object : StubAudioPlayer() {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }

            var stopped = false
            override fun stop() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            server = server,
            player = StubVoiceSatellitePlayer(
                ttsPlayer = ttsPlayer,
                repeatTimerFinishedSound = stubSettingState(true),
                timerFinishedSound = stubSettingState("ring")
            )
        )

        // Make it ring by sending a timer finished event
        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            timerId = "id"
            totalSeconds = 60
            secondsLeft = 0
            isActive = true
        })
        advanceUntilIdle()

        assertEquals(false, ttsPlayer.stopped)
        assertEquals(listOf("ring"), ttsPlayer.mediaUrls)
        ttsPlayer.mediaUrls.clear()

        // Trigger StopRingingAction via its runner
        val result = StopRingingRunner().run(dummyContext, TaskerInput(Unit))
        assert(result is TaskerPluginResultSucess)
        advanceUntilIdle()

        // Should no longer be ringing
        assertEquals(true, ttsPlayer.stopped)

        satellite.close()
    }
}