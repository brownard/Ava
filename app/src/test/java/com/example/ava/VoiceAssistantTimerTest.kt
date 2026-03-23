package com.example.ava

import com.example.ava.esphome.voiceassistant.AudioResult
import com.example.ava.esphome.voiceassistant.VoiceAssistant
import com.example.ava.esphome.voiceassistant.VoiceInput
import com.example.ava.esphome.voiceassistant.VoiceOutput
import com.example.ava.esphome.voiceassistant.VoiceTimer
import com.example.ava.stubs.StubVoiceInput
import com.example.ava.stubs.StubVoiceOutput
import com.example.esphomeproto.api.VoiceAssistantTimerEvent
import com.example.esphomeproto.api.voiceAssistantTimerEventResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VoiceAssistantTimerTest {
    private suspend fun TestScope.createVoiceAssistant(
        voiceInput: VoiceInput = StubVoiceInput(),
        voiceOutput: VoiceOutput = StubVoiceOutput()
    ) = VoiceAssistant(
        coroutineContext = this.coroutineContext,
        voiceInput = voiceInput,
        voiceOutput = voiceOutput,
    ).apply {
        start()
        onConnected()
        advanceUntilIdle()
    }

    @Test
    fun should_store_and_sort_timers() = runTest {
        val voiceAssistant = createVoiceAssistant()

        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "running1"
            totalSeconds = 61
            secondsLeft = 60
            isActive = true
        })

        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "paused1"
            totalSeconds = 62
            secondsLeft = 10
            isActive = false // Sorted last because paused
        })
        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "running2"
            totalSeconds = 63
            secondsLeft = 50 // Sorted first
            isActive = true
            name = "Named"
        })

        var timers = voiceAssistant.allTimers.first()
        assertEquals(3, timers.size)
        assertEquals(listOf("running2", "running1", "paused1"), timers.map { it.id })
        assertEquals(listOf("Named", "", ""), timers.map { it.name })
        assertEquals(listOf(63.seconds, 61.seconds, 62.seconds), timers.map { it.totalDuration })
        assertEquals(VoiceTimer.Paused("paused1", "", 62.seconds, 10.seconds), timers[2])

        val remaining2Millis = timers[0].remainingDuration(Clock.System.now()).inWholeMilliseconds
        assertTrue { remaining2Millis <= 50_000 }
        assertTrue { remaining2Millis > 49_900 }

        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_CANCELLED
            timerId = "running1"
        })
        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_UPDATED
            timerId = "paused1"
            totalSeconds = 62
            secondsLeft = 10
            isActive = true // Unpaused now
        })

        timers = voiceAssistant.allTimers.first()
        assertEquals(listOf("paused1", "running2"), timers.map { it.id })

        voiceAssistant.close()
    }

    @Test
    fun should_display_then_remove_finished_timer() = runTest {
        var audioPlayed = false
        val voiceOutput = object : StubVoiceOutput() {
            override suspend fun playTimerFinishedSound(onCompletion: (repeat: Boolean) -> Unit) {
                audioPlayed = true
                onCompletion(false)
            }
        }
        val voiceAssistant = createVoiceAssistant(voiceOutput = voiceOutput)
        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "timer2"
            totalSeconds = 61
            secondsLeft = 5
            isActive = true
            name = "Will ring"
        })
        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "timer1"
            totalSeconds = 60
            secondsLeft = 60
            isActive = true
        })
        var timers = voiceAssistant.allTimers.first()
        assertEquals(listOf("timer2", "timer1"), timers.map { it.id })
        assert(timers[0] is VoiceTimer.Running)

        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            timerId = "timer2"
            totalSeconds = 61
            secondsLeft = 0
            isActive = false
            name = "Will ring"
        })
        timers = voiceAssistant.allTimers.first()
        assertEquals(VoiceTimer.Ringing("timer2", "Will ring", 61.seconds), timers[0])
        assertEquals(Duration.ZERO, timers[0].remainingDuration(Clock.System.now()))

        // The timer is only removed after the playback completion handler
        // is called, which the satellite calls in a separate coroutine,
        // so it needs to waited on here
        advanceUntilIdle()

        timers = voiceAssistant.allTimers.first()
        assertEquals(listOf("timer1"), timers.map { it.id })
        assert(timers[0] is VoiceTimer.Running)
        assert(audioPlayed)

        voiceAssistant.close()
    }

    @Test
    fun should_remove_repeating_timer_on_wake_word() = runTest {
        val voiceAssistant = createVoiceAssistant()

        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            timerId = "ringing1"
            totalSeconds = 61
            secondsLeft = 0
            isActive = false
            name = "Will ring"
        })
        voiceAssistant.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "paused1"
            totalSeconds = 62
            secondsLeft = 10
            isActive = false
        })
        var timers = voiceAssistant.allTimers.first()
        assertEquals(2, timers.size)
        assert(timers[0] is VoiceTimer.Ringing)
        assert(timers[1] is VoiceTimer.Paused)

        (voiceAssistant.voiceInput as StubVoiceInput).audioResults.emit(
            AudioResult.WakeDetected("stop")
        )

        timers = voiceAssistant.allTimers.first()
        assertEquals(1, timers.size)
        assert(timers[0] is VoiceTimer.Paused)

        voiceAssistant.close()
    }
}
