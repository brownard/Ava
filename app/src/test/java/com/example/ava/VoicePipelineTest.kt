package com.example.ava

import com.example.ava.esphome.Connected
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.voicesatellite.Listening
import com.example.ava.esphome.voicesatellite.Responding
import com.example.ava.esphome.voicesatellite.VoicePipeline
import com.example.ava.stubs.StubAudioPlayer
import com.example.esphomeproto.api.VoiceAssistantAudio
import com.example.esphomeproto.api.VoiceAssistantEvent
import com.example.esphomeproto.api.VoiceAssistantRequest
import com.example.esphomeproto.api.voiceAssistantEventData
import com.example.esphomeproto.api.voiceAssistantEventResponse
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VoicePipelineTest {
    @Test
    fun should_fire_changed_callbacks_on_start() = runTest {
        var listeningChangedCalled = false
        var stateChangedCalled = false
        val pipeline = VoicePipeline(
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
    fun when_continue_conversation_is_true_should_fire_ended_with_continueConversation() {
        var continueConversation: Boolean? = null
        val pipeline = VoicePipeline(
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
    fun when_tts_start_streaming_is_true_should_stream_tts() {
        val ttsStreamUrl = "tts_stream"
        val notTtsStreamUrl = "not_tts_stream"
        var playbackUrl: String? = null
        val pipeline = VoicePipeline(
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
    fun when_tts_start_streaming_not_received_should_not_stream_tts() {
        val ttsStreamUrl = "tts_stream"
        val notTtsStreamUrl = "not_tts_stream"
        var playbackUrl: String? = null
        val pipeline = VoicePipeline(
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
    fun when_tts_not_played_should_change_state_on_pipeline_end() = runTest {
        var listening = false
        var state: EspHomeState = Connected
        val pipeline = VoicePipeline(
            player = StubAudioPlayer(),
            sendMessage = {},
            listeningChanged = { listening = it },
            stateChanged = { state = it },
            ended = {}
        )

        pipeline.start()

        assert(listening)
        assert(state == Listening)
        assert(pipeline.state == Listening)

        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })

        assert(!listening)
        assert(state == Connected)
        assert(pipeline.state == Connected)
    }

    @Test
    fun when_tts_playing_should_change_state_when_tts_played() = runTest {
        var listening = false
        var state: EspHomeState = Connected
        val player = object : StubAudioPlayer() {
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUri: String, onCompletion: () -> Unit) {
                this.onCompletion = onCompletion
            }
        }
        val pipeline = VoicePipeline(
            player = player,
            sendMessage = {},
            listeningChanged = { listening = it },
            stateChanged = { state = it },
            ended = {}
        )
        pipeline.start()

        // Should change the pipeline state to Responding
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_START
        })

        assert(!listening)
        assert(state == Responding)
        assert(pipeline.state == Responding)

        // Start TTS playback
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_END
            data += voiceAssistantEventData { name = "url"; value = "tts_stream" }
        })

        // Pipeline state should not change on VOICE_ASSISTANT_RUN_END if tts playback hasn't ended yet
        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })

        assert(!listening)
        assert(state == Responding)
        assert(pipeline.state == Responding)

        player.onCompletion()

        // Now state should change
        assert(!listening)
        assert(state == Connected)
        assert(pipeline.state == Connected)
    }
}