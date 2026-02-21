package com.example.ava

import com.example.ava.esphome.Connected
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.voicesatellite.Announcement
import com.example.ava.esphome.voicesatellite.Responding
import com.example.ava.stubs.StubAudioPlayer
import com.example.esphomeproto.api.VoiceAssistantAnnounceFinished
import com.google.protobuf.MessageLite
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnouncementTest {
    @Test
    fun should_change_to_responding_state_when_announcing() = runTest {
        val player = object : StubAudioPlayer() {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }
        }
        var state: EspHomeState = Connected
        var ended = false
        val announcement = Announcement(
            scope = this,
            player = player,
            sendMessage = {},
            stateChanged = { state = it },
            ended = { ended = true }
        )
        val oldState = announcement.state

        announcement.announce("media", "preannounce", true)

        assertEquals(Connected, oldState)
        assertEquals(Responding, state)
        assertEquals(Responding, announcement.state)
        assertEquals(listOf("preannounce", "media"), player.mediaUrls)
        assertEquals(false, ended)

        // Announcement finished
        player.onCompletion()
        advanceUntilIdle()

        assertEquals(true, ended)
    }

    @Test
    fun should_not_play_empty_preannounce_sound() = runTest {
        val player = object : StubAudioPlayer() {
            val mediaUrls = mutableListOf<String>()
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
            }
        }
        val announcement = Announcement(
            scope = this,
            player = player,
            sendMessage = {},
            stateChanged = {},
            ended = {}
        )
        announcement.announce("media", "", true)
        assertEquals(listOf("media"), player.mediaUrls)
    }

    @Test
    fun when_responding_should_send_announce_finished_when_stopped() = runTest {
        val player = object : StubAudioPlayer() {
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.onCompletion = onCompletion
            }
        }
        val sentMessages = mutableListOf<MessageLite>()
        var state: EspHomeState = Connected
        var ended = false
        val announcement = Announcement(
            scope = this,
            player = player,
            sendMessage = { sentMessages.add(it) },
            stateChanged = { state = it },
            ended = { ended = true }
        )

        announcement.announce("media", "preannounce", true)
        announcement.stop()

        assertEquals(1, sentMessages.size)
        assert(sentMessages[0] is VoiceAssistantAnnounceFinished)
        assertEquals(Connected, state)
        assertEquals(Connected, announcement.state)
    }

    @Test
    fun when_not_responding_should_not_send_announce_finished_when_stopped() = runTest {
        val sentMessages = mutableListOf<MessageLite>()
        var state: EspHomeState = Connected
        val announcement = Announcement(
            scope = this,
            player = StubAudioPlayer(),
            sendMessage = { sentMessages.add(it) },
            stateChanged = { state = it },
            ended = {}
        )

        // Should not send announce finished message if not announcing
        announcement.stop()
        assertEquals(0, sentMessages.size)
        assertEquals(Connected, state)
        assertEquals(Connected, announcement.state)
    }
}