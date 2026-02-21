package com.example.ava

import com.example.ava.esphome.Connected
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.voicesatellite.Listening
import com.example.ava.esphome.voicesatellite.Responding
import com.example.ava.esphome.voicesatellite.VoicePipeline
import com.example.ava.stubs.StubAudioPlayer
import com.example.esphomeproto.api.VoiceAssistantAnnounceFinished
import com.example.esphomeproto.api.VoiceAssistantAudio
import com.example.esphomeproto.api.VoiceAssistantEvent
import com.example.esphomeproto.api.VoiceAssistantRequest
import com.example.esphomeproto.api.voiceAssistantEventData
import com.example.esphomeproto.api.voiceAssistantEventResponse
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class VoicePipelineTest {
    @Test
    fun should_fire_changed_callbacks_on_start() = runTest {
        var listeningChangedCalled = false
        var stateChangedCalled = false
        val pipeline = VoicePipeline(
            scope = this,
            player = StubAudioPlayer(),
            sendMessage = {},
            listeningChanged = { listeningChangedCalled = true },
            stateChanged = { stateChangedCalled = true },
            ended = {}
        )

        pipeline.start()

        assert(listeningChangedCalled)
        assert(stateChangedCalled)
    }

    @Test
    fun should_send_start_request() = runTest {
        val sentMessages = mutableListOf<MessageLite>()
        val pipeline = VoicePipeline(
            scope = this,
            player = StubAudioPlayer(),
            sendMessage = { sentMessages.add(it) },
            listeningChanged = {},
            stateChanged = {},
            ended = {}
        )
        val wakeWord = "Okay Nabu"

        pipeline.start(wakeWord)

        assert(sentMessages.count() == 1)
        val sentMessage = sentMessages.first()
        assert(sentMessage is VoiceAssistantRequest)
        assert((sentMessage as VoiceAssistantRequest).wakeWordPhrase == wakeWord)
        assert(sentMessage.start)
    }

    @Test
    fun should_buffer_audio_received_before_running() = runTest {
        val sentMessages = mutableListOf<MessageLite>()
        val pipeline = VoicePipeline(
            scope = this,
            player = StubAudioPlayer(),
            sendMessage = { sentMessages.add(it) },
            listeningChanged = {},
            stateChanged = {},
            ended = {}
        )
        val audioData = List(3) { ByteString.copyFrom(byteArrayOf(it.toByte())) }

        // Should buffer the audio, and not send it, until a VOICE_ASSISTANT_RUN_START event is received
        audioData.take(2).forEach {
            pipeline.processMicAudio(it)
        }

        assert(sentMessages.isEmpty())

        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })

        // Should now send the buffered audio with the new audio
        pipeline.processMicAudio(audioData[2])

        assert(sentMessages.count() == 3)
        for (i in 0 until 3) {
            assert((sentMessages[i] as VoiceAssistantAudio).data == audioData[i])
        }
    }

    @Test
    fun should_handle_restart() = runTest {
        val sentMessages = mutableListOf<MessageLite>()
        var listening = true
        var state: EspHomeState = Listening
        val pipeline = VoicePipeline(
            scope = this,
            player = StubAudioPlayer(),
            sendMessage = { sentMessages.add(it) },
            listeningChanged = { listening = it },
            stateChanged = { state = it },
            ended = { }
        )

        val audioData = List(3) { ByteString.copyFrom(byteArrayOf(it.toByte())) }

        // Should buffer the audio
        audioData.take(2).forEach {
            pipeline.processMicAudio(it)
        }

        // Should drop all buffered audio
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })

        // Should restart cleanly with new audio
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })
        pipeline.processMicAudio(audioData[2])

        assertEquals(true, listening)
        assertEquals(Listening, state)
        assertEquals(1, sentMessages.size)
        assertEquals(audioData[2], (sentMessages[0] as VoiceAssistantAudio).data)
    }

    @Test
    fun when_continue_conversation_is_true_should_fire_ended_with_continueConversation() = runTest {
        var continueConversation: Boolean? = null
        val pipeline = VoicePipeline(
            scope = this,
            player = StubAudioPlayer(),
            sendMessage = {},
            listeningChanged = {},
            stateChanged = {},
            ended = { continueConversation = it }
        )

        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_INTENT_END
            data += voiceAssistantEventData { name = "continue_conversation"; value = "1" }
        })
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })

        assert(continueConversation == true)
    }

    @Test
    fun when_tts_start_streaming_is_true_should_stream_tts() = runTest {
        val ttsStreamUrl = "tts_stream"
        val notTtsStreamUrl = "not_tts_stream"
        var playbackUrl: String? = null
        val pipeline = VoicePipeline(
            scope = this,
            player = object : StubAudioPlayer() {
                override fun play(mediaUri: String, onCompletion: () -> Unit) {
                    playbackUrl = mediaUri
                }
            },
            sendMessage = {},
            listeningChanged = {},
            stateChanged = {},
            ended = {}
        )

        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
            data += voiceAssistantEventData { name = "url"; value = ttsStreamUrl }
        })
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_INTENT_PROGRESS
            data += voiceAssistantEventData { name = "tts_start_streaming"; value = "1" }
        })
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_END
            data += voiceAssistantEventData { name = "url"; value = notTtsStreamUrl }
        })

        assert(playbackUrl == ttsStreamUrl)
    }

    @Test
    fun when_tts_start_streaming_not_received_should_not_stream_tts() = runTest {
        val ttsStreamUrl = "tts_stream"
        val notTtsStreamUrl = "not_tts_stream"
        var playbackUrl: String? = null
        val pipeline = VoicePipeline(
            scope = this,
            player = object : StubAudioPlayer() {
                override fun play(mediaUri: String, onCompletion: () -> Unit) {
                    playbackUrl = mediaUri
                }
            },
            sendMessage = {},
            listeningChanged = {},
            stateChanged = {},
            ended = {}
        )

        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
            data += voiceAssistantEventData { name = "url"; value = ttsStreamUrl }
        })
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_INTENT_PROGRESS
        })
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_END
            data += voiceAssistantEventData { name = "url"; value = notTtsStreamUrl }
        })

        assert(playbackUrl == notTtsStreamUrl)
    }

    @Test
    fun when_tts_not_played_should_call_ended_on_pipeline_end() = runTest {
        var ended = false
        val pipeline = VoicePipeline(
            scope = this,
            player = StubAudioPlayer(),
            sendMessage = {},
            listeningChanged = {},
            stateChanged = {},
            ended = { ended = true }
        )

        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })

        assertEquals(true, ended)
    }

    @Test
    fun when_tts_played_should_call_ended_on_tts_end() = runTest {
        var ended = false
        val player = object : StubAudioPlayer() {
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUri: String, onCompletion: () -> Unit) {
                this.onCompletion = onCompletion
            }
        }
        val pipeline = VoicePipeline(
            scope = this,
            player = player,
            sendMessage = {},
            listeningChanged = {},
            stateChanged = {},
            ended = { ended = true }
        )

        // Should change the pipeline state to Responding
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_START
        })

        assertEquals(Responding, pipeline.state)

        // Start TTS playback
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_END
            data += voiceAssistantEventData { name = "url"; value = "tts_stream" }
        })

        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })

        // Pipeline should not end on VOICE_ASSISTANT_RUN_END if tts playback hasn't ended yet
        assertEquals(Responding, pipeline.state)
        assertEquals(false, ended)

        player.onCompletion()
        advanceUntilIdle()

        // Now pipeline should end
        assertEquals(true, ended)
    }

    @Test
    fun when_stopped_whilst_running_should_send_pipeline_stop_event() = runTest {
        val sentMessages = mutableListOf<MessageLite>()
        var listening = true
        var state: EspHomeState = Listening
        val pipeline = VoicePipeline(
            scope = this,
            player = StubAudioPlayer(),
            sendMessage = {
                sentMessages.add(it)
            },
            listeningChanged = { listening = it },
            stateChanged = { state = it },
            ended = {}
        )

        // Should change the pipeline state to Responding
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })

        pipeline.stop()

        assertEquals(false, listening)
        assertEquals(Connected, state)
        assertEquals(1, sentMessages.size)
        assert(sentMessages[0] is VoiceAssistantRequest && !(sentMessages[0] as VoiceAssistantRequest).start)

        // Should not send any messages if the pipeline is already stopped
        pipeline.stop()
        assertEquals(1, sentMessages.size)
    }

    @Test
    fun when_stopped_whilst_tts_playing_should_send_announce_finished_event() = runTest {
        val sentMessages = mutableListOf<MessageLite>()
        var listening = true
        var state: EspHomeState = Listening
        val pipeline = VoicePipeline(
            scope = this,
            player = StubAudioPlayer(),
            sendMessage = {
                sentMessages.add(it)
            },
            listeningChanged = { listening = it },
            stateChanged = { state = it },
            ended = {}
        )

        // Should change the pipeline state to Responding
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_START
        })

        assertEquals(false, listening)
        assertEquals(Responding, state)

        pipeline.stop()

        assertEquals(false, listening)
        assertEquals(Connected, state)
        assertEquals(1, sentMessages.size)
        assert(sentMessages[0] is VoiceAssistantAnnounceFinished)

        // Should not send any messages if the pipeline is already stopped
        pipeline.stop()
        assertEquals(1, sentMessages.size)
    }
}