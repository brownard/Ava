package com.example.ava

import com.example.ava.esphome.voicesatellite.VoicePipeline
import com.example.ava.players.AudioPlayer
import com.example.ava.players.AudioPlayerState
import com.example.esphomeproto.api.VoiceAssistantAudio
import com.example.esphomeproto.api.VoiceAssistantEvent
import com.example.esphomeproto.api.VoiceAssistantRequest
import com.example.esphomeproto.api.voiceAssistantEventData
import com.example.esphomeproto.api.voiceAssistantEventResponse
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

open class StubAudioPlayer : AudioPlayer {
    override val state = MutableStateFlow(AudioPlayerState.IDLE)
    override var volume = 1f
    override fun init() {}
    override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {}
    override fun pause() {}
    override fun unpause() {}
    override fun stop() {}
    override fun close() {}
}

class VoicePipelineTest {
    @Test
    fun should_fire_changed_callbacks_on_start() {
        var listeningChangedCalled = false
        var stateChangedCalled = false
        val pipeline = VoicePipeline(
            player = StubAudioPlayer(),
            sendMessage = {},
            listeningChanged = { listeningChangedCalled = true },
            stateChanged = { stateChangedCalled = true },
            ended = {}
        )

        runBlocking {
            pipeline.start()
        }

        assert(listeningChangedCalled)
        assert(stateChangedCalled)
    }

    @Test
    fun should_send_start_request() {
        val sentMessages = mutableListOf<MessageLite>()
        val pipeline = VoicePipeline(
            player = StubAudioPlayer(),
            sendMessage = { sentMessages.add(it) },
            listeningChanged = {},
            stateChanged = {},
            ended = {}
        )
        val wakeWord = "Okay Nabu"
        runBlocking {
            pipeline.start(wakeWord)
        }

        assert(sentMessages.count() == 1)
        val sentMessage = sentMessages.first()
        assert(sentMessage is VoiceAssistantRequest)
        assert((sentMessage as VoiceAssistantRequest).wakeWordPhrase == wakeWord)
        assert(sentMessage.start)
    }

    @Test
    fun should_buffer_audio_received_before_running() {
        val sentMessages = mutableListOf<MessageLite>()
        val pipeline = VoicePipeline(
            player = StubAudioPlayer(),
            sendMessage = { sentMessages.add(it) },
            listeningChanged = {},
            stateChanged = {},
            ended = {}
        )

        val audioData = List(3, { ByteString.copyFrom(byteArrayOf(it.toByte())) })
        // Should buffer the audio, and not send it, until a VOICE_ASSISTANT_RUN_START event is received
        runBlocking {
            audioData.take(2).forEach {
                pipeline.processMicAudio(it)
            }
        }
        assert(sentMessages.isEmpty())

        pipeline.handleEvent(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })

        // Should now send the buffered audio with the new audio
        runBlocking {
            pipeline.processMicAudio(audioData[2])
        }

        assert(sentMessages.count() == 3)
        for (i in 0 until 3) {
            assert((sentMessages[i] as VoiceAssistantAudio).data == audioData[i])
        }
    }

    @Test
    fun should_fire_ended_with_continueConversation() {
        var continueConversation: Boolean? = null
        val pipeline = VoicePipeline(
            player = StubAudioPlayer(),
            sendMessage = {},
            listeningChanged = {},
            stateChanged = {},
            ended = { continueConversation = it }
        )

        runBlocking {
            pipeline.handleEvent(voiceAssistantEventResponse {
                eventType = VoiceAssistantEvent.VOICE_ASSISTANT_INTENT_END
                data += voiceAssistantEventData { name = "continue_conversation"; value = "1" }
            })
            pipeline.handleEvent(voiceAssistantEventResponse {
                eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
            })
        }

        assert(continueConversation == true)
    }

    @Test
    fun should_stream_tts() {
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

        runBlocking {
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
        }

        assert(playbackUrl == ttsStreamUrl)
    }

    @Test
    fun should_not_stream_tts() {
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

        runBlocking {
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
        }

        assert(playbackUrl == notTtsStreamUrl)
    }
}