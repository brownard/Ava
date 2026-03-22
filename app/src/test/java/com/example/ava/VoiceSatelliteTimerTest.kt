package com.example.ava

import com.example.ava.esphome.voicesatellite.AudioResult
import com.example.ava.esphome.voicesatellite.VoiceOutput
import com.example.ava.esphome.voicesatellite.VoiceSatellite
import com.example.ava.esphome.voicesatellite.VoiceTimer
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

class VoiceSatelliteTimerTest {
    private suspend fun TestScope.start_satellite(
        voiceOutput: VoiceOutput = StubVoiceOutput()
    ) = VoiceSatellite(
        coroutineContext = coroutineContext,
        voiceInput = StubVoiceInput(),
        voiceOutput = voiceOutput
    ).apply {
        start()
        onConnected()
        // Internally the voice satellite starts collecting server and
        // microphone messages in separate coroutines, wait for collection
        // to start to ensure all messages are collected.
        advanceUntilIdle()
    }

    @Test
    fun should_store_and_sort_timers() = runTest {
        val voiceSatellite = start_satellite()

        voiceSatellite.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "running1"
            totalSeconds = 61
            secondsLeft = 60
            isActive = true
        })

        voiceSatellite.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "paused1"
            totalSeconds = 62
            secondsLeft = 10
            isActive = false // Sorted last because paused
        })
        voiceSatellite.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "running2"
            totalSeconds = 63
            secondsLeft = 50 // Sorted first
            isActive = true
            name = "Named"
        })

        var timers = voiceSatellite.allTimers.first()
        assertEquals(3, timers.size)
        assertEquals(listOf("running2", "running1", "paused1"), timers.map { it.id })
        assertEquals(listOf("Named", "", ""), timers.map { it.name })
        assertEquals(listOf(63.seconds, 61.seconds, 62.seconds), timers.map { it.totalDuration })
        assertEquals(VoiceTimer.Paused("paused1", "", 62.seconds, 10.seconds), timers[2])

        val remaining2Millis = timers[0].remainingDuration(Clock.System.now()).inWholeMilliseconds
        assertTrue { remaining2Millis <= 50_000 }
        assertTrue { remaining2Millis > 49_900 }

        voiceSatellite.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_CANCELLED
            timerId = "running1"
        })
        voiceSatellite.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_UPDATED
            timerId = "paused1"
            totalSeconds = 62
            secondsLeft = 10
            isActive = true // Unpaused now
        })

        timers = voiceSatellite.allTimers.first()
        assertEquals(listOf("paused1", "running2"), timers.map { it.id })

        voiceSatellite.close()
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
        val voiceSatellite = start_satellite(voiceOutput)
        voiceSatellite.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "timer2"
            totalSeconds = 61
            secondsLeft = 5
            isActive = true
            name = "Will ring"
        })
        voiceSatellite.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "timer1"
            totalSeconds = 60
            secondsLeft = 60
            isActive = true
        })
        var timers = voiceSatellite.allTimers.first()
        assertEquals(listOf("timer2", "timer1"), timers.map { it.id })
        assert(timers[0] is VoiceTimer.Running)

        voiceSatellite.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            timerId = "timer2"
            totalSeconds = 61
            secondsLeft = 0
            isActive = false
            name = "Will ring"
        })
        timers = voiceSatellite.allTimers.first()
        assertEquals(VoiceTimer.Ringing("timer2", "Will ring", 61.seconds), timers[0])
        assertEquals(Duration.ZERO, timers[0].remainingDuration(Clock.System.now()))

        // The timer is only removed after the playback completion handler
        // is called, which the satellite calls in a separate coroutine,
        // so it needs to waited on here
        advanceUntilIdle()

        timers = voiceSatellite.allTimers.first()
        assertEquals(listOf("timer1"), timers.map { it.id })
        assert(timers[0] is VoiceTimer.Running)
        assert(audioPlayed)

        voiceSatellite.close()
    }

    @Test
    fun should_remove_repeating_timer_on_wake_word() = runTest {
        val voiceSatellite = start_satellite()

        voiceSatellite.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            timerId = "ringing1"
            totalSeconds = 61
            secondsLeft = 0
            isActive = false
            name = "Will ring"
        })
        voiceSatellite.handleMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "paused1"
            totalSeconds = 62
            secondsLeft = 10
            isActive = false
        })
        var timers = voiceSatellite.allTimers.first()
        assertEquals(2, timers.size)
        assert(timers[0] is VoiceTimer.Ringing)
        assert(timers[1] is VoiceTimer.Paused)

        (voiceSatellite.voiceInput as StubVoiceInput).audioResults.emit(
            AudioResult.WakeDetected("stop")
        )

        timers = voiceSatellite.allTimers.first()
        assertEquals(1, timers.size)
        assert(timers[0] is VoiceTimer.Paused)

        voiceSatellite.close()
    }
}
