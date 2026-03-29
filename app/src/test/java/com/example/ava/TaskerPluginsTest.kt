package com.example.ava

import android.content.ContextWrapper
import com.example.ava.esphome.voiceassistant.Listening
import com.example.ava.esphome.voiceassistant.VoiceAssistant
import com.example.ava.esphome.voiceassistant.VoiceInput
import com.example.ava.esphome.voiceassistant.VoiceOutput
import com.example.ava.stubs.StubVoiceInput
import com.example.ava.stubs.StubVoiceOutput
import com.example.ava.tasker.StopRingingRunner
import com.example.ava.tasker.WakeSatelliteRunner
import com.example.esphomeproto.api.VoiceAssistantRequest
import com.example.esphomeproto.api.VoiceAssistantTimerEvent
import com.example.esphomeproto.api.voiceAssistantTimerEventResponse
import com.google.protobuf.MessageLite
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class TaskerPluginsTest {
    private val dummyContext = ContextWrapper(null)

    private fun TestScope.createVoiceAssistant(
        voiceInput: VoiceInput = StubVoiceInput(),
        voiceOutput: VoiceOutput = StubVoiceOutput()
    ) = VoiceAssistant(
        coroutineContext = this.coroutineContext,
        voiceInput = voiceInput,
        voiceOutput = voiceOutput
    ).apply {
        start()
        advanceUntilIdle()
    }

    @Test
    fun should_handle_wake_satellite_action() = runTest {
        val voiceInput = StubVoiceInput()
        val voiceAssistant = createVoiceAssistant(voiceInput = voiceInput)
        val sentMessages = mutableListOf<MessageLite>()
        val messageJob = voiceAssistant.subscribe().onEach { sentMessages.add(it) }.launchIn(this)
        WakeSatelliteRunner.register { voiceAssistant.wakeAssistant() }
        advanceUntilIdle()

        val result = WakeSatelliteRunner().run(dummyContext, TaskerInput(Unit))
        assert(result is TaskerPluginResultSucess)
        advanceUntilIdle()

        assertEquals(Listening, voiceAssistant.state.value)
        assertEquals(true, voiceInput.isStreaming)
        assertEquals(1, sentMessages.size)
        assertEquals(true, (sentMessages[0] as VoiceAssistantRequest).start)

        messageJob.cancel()
        voiceAssistant.close()
    }

    @Test
    fun should_handle_stop_ringing_action() = runTest {
        val voiceOutput = object : StubVoiceOutput(timerFinishedSound = "ring") {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }

            var stopped = false
            override fun stopTTS() {
                stopped = true
            }
        }
        val voiceAssistant = createVoiceAssistant(voiceOutput = voiceOutput)
        StopRingingRunner.register { voiceAssistant.stopTimer() }

        // Make it ring by sending a timer finished event
        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            timerId = "id"
            totalSeconds = 60
            secondsLeft = 0
            isActive = true
        })
        advanceUntilIdle()

        assertEquals(false, voiceOutput.stopped)
        assertEquals(listOf("ring"), voiceOutput.mediaUrls)
        voiceOutput.mediaUrls.clear()

        // Trigger StopRingingAction via its runner
        val result = StopRingingRunner().run(dummyContext, TaskerInput(Unit))
        assert(result is TaskerPluginResultSucess)
        advanceUntilIdle()

        // Should no longer be ringing
        assertEquals(true, voiceOutput.stopped)

        voiceAssistant.close()
    }
}