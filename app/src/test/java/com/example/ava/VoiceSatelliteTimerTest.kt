package com.example.ava

import com.example.ava.esphome.voicesatellite.AudioResult
import com.example.ava.esphome.voicesatellite.VoiceSatellite
import com.example.ava.esphome.voicesatellite.VoiceTimer
import com.example.ava.players.AudioPlayer
import com.example.ava.server.Server
import com.example.ava.stubs.StubAudioPlayer
import com.example.ava.stubs.StubServer
import com.example.ava.stubs.StubSettingState
import com.example.ava.stubs.StubVoiceSatelliteAudioInput
import com.example.ava.stubs.StubVoiceSatellitePlayer
import com.example.ava.stubs.StubVoiceSatelliteSettingsStore
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
    private fun TestScope.start_satellite(
        server: Server,
        player: AudioPlayer = StubAudioPlayer(),
        repeatTimerFinishedSound: Boolean = false
    ) =
        VoiceSatellite(
            coroutineContext = coroutineContext,
            name = "Test Satellite",
            server = server,
            audioInput = StubVoiceSatelliteAudioInput(),
            player = StubVoiceSatellitePlayer(
                ttsPlayer = player,
                wakeSound = StubSettingState("wake.mp3"),
                timerFinishedSound = StubSettingState("timer.mp3"),
                repeatTimerFinishedSound = StubSettingState(repeatTimerFinishedSound)
            ),
            settingsStore = StubVoiceSatelliteSettingsStore()
        ).apply {
            start()
            // Internally the voice satellite starts collecting server and
            // microphone messages in separate coroutines, wait for collection
            // to start to ensure all messages are collected.
            advanceUntilIdle()
        }

    @Test
    fun should_store_and_sort_timers() = runTest {
        val server = StubServer()
        val voiceSatellite = start_satellite(server)

        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "running1"
            totalSeconds = 61
            secondsLeft = 60
            isActive = true
        })

        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "paused1"
            totalSeconds = 62
            secondsLeft = 10
            isActive = false // Sorted last because paused
        })
        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
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

        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_CANCELLED
            timerId = "running1"
        })
        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
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
        var audioPlayed: String? = null
        val audioPlayer = object : StubAudioPlayer() {
            override fun play(mediaUri: String, onCompletion: () -> Unit) {
                audioPlayed = mediaUri
                onCompletion()
            }
        }
        val server = StubServer()
        val voiceSatellite = start_satellite(server, audioPlayer, false)
        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "timer2"
            totalSeconds = 61
            secondsLeft = 5
            isActive = true
            name = "Will ring"
        })
        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "timer1"
            totalSeconds = 60
            secondsLeft = 60
            isActive = true
        })
        var timers = voiceSatellite.allTimers.first()
        assertEquals(listOf("timer2", "timer1"), timers.map { it.id })
        assert(timers[0] is VoiceTimer.Running)

        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
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
        assertEquals("timer.mp3", audioPlayed)

        voiceSatellite.close()
    }

    @Test
    fun should_remove_repeating_timer_on_wake_word() = runTest {
        val server = StubServer()
        val voiceSatellite = start_satellite(server, repeatTimerFinishedSound = true)

        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            timerId = "ringing1"
            totalSeconds = 61
            secondsLeft = 0
            isActive = false
            name = "Will ring"
        })
        server.receivedMessages.emit(voiceAssistantTimerEventResponse {
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

        (voiceSatellite.audioInput as StubVoiceSatelliteAudioInput).audioResults.emit(
            AudioResult.WakeDetected("stop")
        )

        timers = voiceSatellite.allTimers.first()
        assertEquals(1, timers.size)
        assert(timers[0] is VoiceTimer.Paused)

        voiceSatellite.close()
    }
}
