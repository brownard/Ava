package com.example.ava

import com.example.ava.esphome.voicesatellite.VoiceTimer
import com.example.esphomeproto.api.VoiceAssistantTimerEvent
import com.example.esphomeproto.api.voiceAssistantTimerEventResponse
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.asClock

class VoiceTimerTest {
    val fixedClock = TestTimeSource().asClock(Clock.System.now())

    @Test
    fun should_parse_running_timer() {
        val timer = VoiceTimer.timerFromEvent(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "id"
            totalSeconds = 61
            secondsLeft = 40
            isActive = true
            name = "Name"
        }, fixedClock)

        assert(timer is VoiceTimer.Running)
        assertEquals("id", timer.id)
        assertEquals("Name", timer.name)
        assertEquals(61.seconds, timer.totalDuration)
        assertEquals(40.seconds, timer.remainingDuration(fixedClock.now()))
        assertEquals(12.seconds, timer.remainingDuration(fixedClock.now().plus(28.seconds)))
    }

    @Test
    fun should_parse_paused_timer() {
        val timer = VoiceTimer.timerFromEvent(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "id"
            totalSeconds = 61
            secondsLeft = 40
            isActive = false
            name = "Name"
        }, fixedClock)

        assert(timer is VoiceTimer.Paused)
        assertEquals("id", timer.id)
        assertEquals("Name", timer.name)
        assertEquals(61.seconds, timer.totalDuration)
        assertEquals(40.seconds, timer.remainingDuration(fixedClock.now()))
    }

    @Test
    fun should_parse_ringing_timer() {
        val timer = VoiceTimer.timerFromEvent(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            timerId = "id"
            totalSeconds = 61
            secondsLeft = 0
            isActive = true
            name = "Name"
        }, fixedClock)

        assert(timer is VoiceTimer.Ringing)
        assertEquals("id", timer.id)
        assertEquals("Name", timer.name)
        assertEquals(61.seconds, timer.totalDuration)
        assertEquals(Duration.ZERO, timer.remainingDuration(fixedClock.now()))
    }
}