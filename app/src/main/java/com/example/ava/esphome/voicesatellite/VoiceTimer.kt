package com.example.ava.esphome.voicesatellite

import com.example.esphomeproto.api.VoiceAssistantTimerEvent
import com.example.esphomeproto.api.VoiceAssistantTimerEventResponse
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed class VoiceTimer(
    open val id: String,
    open val name: String,
    open val totalDuration: Duration
) : Comparable<VoiceTimer> {

    data class Ringing(
        override val id: String,
        override val name: String,
        override val totalDuration: Duration
    ) : VoiceTimer(id, name, totalDuration)

    data class Running(
        override val id: String,
        override val name: String,
        override val totalDuration: Duration,
        val endsAt: Instant,
    ) : VoiceTimer(id, name, totalDuration)

    data class Paused(
        override val id: String,
        override val name: String,
        override val totalDuration: Duration,
        val remainingDuration: Duration
    ) : VoiceTimer(id, name, totalDuration)

    companion object {
        fun timerFromEvent(timer: VoiceAssistantTimerEventResponse, clock: Clock): VoiceTimer {
            val total = timer.totalSeconds.seconds
            val remaining = timer.secondsLeft.seconds
            val ringing = timer.eventType == VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            return when {
                ringing -> Ringing(timer.timerId, timer.name, total)
                !timer.isActive -> Paused(timer.timerId, timer.name, total, remaining)
                else -> Running(timer.timerId, timer.name, total, clock.now() + remaining)
            }
        }
    }

    fun remainingDuration(now: Instant): Duration = when (this) {
        is Ringing -> Duration.ZERO
        is Running -> (endsAt - now).coerceAtLeast(Duration.ZERO)
        is Paused -> remainingDuration
    }

    private val sortingRank: Int
        get() = when (this) {
            is Ringing -> 0
            is Running -> 1
            is Paused -> 2
        }

    override fun compareTo(other: VoiceTimer): Int {
        // Sort paused timers last for a stable order when running ones count down
        if (this.javaClass != other.javaClass) {
            return this.sortingRank.compareTo(other.sortingRank)
        }

        return when (this) {
            is Ringing -> 0
            is Running -> endsAt.compareTo((other as Running).endsAt)
            is Paused -> remainingDuration.compareTo((other as Paused).remainingDuration)
        }
    }
}