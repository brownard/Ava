package com.example.ava

import com.example.ava.esphome.Connected
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.voicesatellite.Announcement
import com.example.ava.esphome.voicesatellite.Responding
import com.example.ava.stubs.StubAudioPlayer
import com.example.esphomeproto.api.VoiceAssistantAnnounceFinished
import com.google.protobuf.MessageLite
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnouncementTest {
    fun TestScope.createAnnouncement(
        player: StubAudioPlayer = StubAudioPlayer(),
        sendMessage: suspend (MessageLite) -> Unit = {},
        stateChanged: (EspHomeState) -> Unit = {},
        ended: suspend (continueConversation: Boolean) -> Unit = {}
    ) = Announcement(
        scope = this,
        player = player,
        sendMessage = sendMessage,
        stateChanged = stateChanged,
        ended = ended
    )

    @Test
    fun should_change_to_responding_state_when_announcing() = runTest {
        val playedUrls = mutableListOf<String>()
        var playerCompletion: () -> Unit = {}
        var state: EspHomeState = Connected
        var ended = false
        val announcement = createAnnouncement(
            player = object : StubAudioPlayer() {
                override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                    playedUrls.addAll(mediaUris)
                    playerCompletion = onCompletion
                }
            },
            stateChanged = { state = it },
            ended = { ended = true }
        )
        val oldState = announcement.state

        announcement.announce("media", "preannounce", true)

        assertEquals(Connected, oldState)
        assertEquals(Responding, state)
        assertEquals(Responding, announcement.state)
        assertEquals(listOf("preannounce", "media"), playedUrls)
        assertEquals(false, ended)

        // Announcement finished
        playerCompletion()
        advanceUntilIdle()

        assertEquals(true, ended)
    }

    @Test
    fun should_not_play_empty_preannounce_sound() = runTest {
        val playedUrls = mutableListOf<String>()
        val announcement = createAnnouncement(
            player = object : StubAudioPlayer() {
                override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                    playedUrls.addAll(mediaUris)
                }
            }
        )
        announcement.announce("media", "", true)
        assertEquals(listOf("media"), playedUrls)
    }

    @Test
    fun when_responding_should_send_announce_finished_when_stopped() = runTest {
        val sentMessages = mutableListOf<MessageLite>()
        var state: EspHomeState = Connected
        val announcement = createAnnouncement(
            player = object : StubAudioPlayer() {
                override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                    // Never complete playback
                }
            },
            sendMessage = { sentMessages.add(it) },
            stateChanged = { state = it }
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
        val announcement = createAnnouncement(
            sendMessage = { sentMessages.add(it) },
            stateChanged = { state = it }
        )

        // Should not send announce finished message if not announcing
        announcement.stop()
        assertEquals(0, sentMessages.size)
        assertEquals(Connected, state)
        assertEquals(Connected, announcement.state)
    }
}