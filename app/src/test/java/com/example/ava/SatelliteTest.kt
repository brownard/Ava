package com.example.ava

import com.example.ava.esphome.Connected
import com.example.ava.esphome.voicesatellite.AudioResult
import com.example.ava.esphome.voicesatellite.Listening
import com.example.ava.esphome.voicesatellite.Processing
import com.example.ava.esphome.voicesatellite.Responding
import com.example.ava.esphome.voicesatellite.VoiceInput
import com.example.ava.esphome.voicesatellite.VoiceOutput
import com.example.ava.esphome.voicesatellite.VoiceSatellite
import com.example.ava.stubs.StubVoiceInput
import com.example.ava.stubs.StubVoiceOutput
import com.example.esphomeproto.api.VoiceAssistantAnnounceFinished
import com.example.esphomeproto.api.VoiceAssistantEvent
import com.example.esphomeproto.api.VoiceAssistantRequest
import com.example.esphomeproto.api.voiceAssistantAnnounceRequest
import com.example.esphomeproto.api.voiceAssistantEventData
import com.example.esphomeproto.api.voiceAssistantEventResponse
import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class SatelliteTest {
    suspend fun TestScope.createSatellite(
        voiceInput: VoiceInput = StubVoiceInput(),
        voiceOutput: VoiceOutput = StubVoiceOutput()
    ) = VoiceSatellite(
        coroutineContext = this.coroutineContext,
        voiceInput = voiceInput,
        voiceOutput = voiceOutput,
    ).apply {
        start()
        onConnected()
        advanceUntilIdle()
    }

    @Test
    fun should_handle_wake_word_intercept_during_setup() = runTest {
        val voiceInput = StubVoiceInput()
        val satellite = createSatellite(voiceInput = voiceInput)
        val sentMessages = mutableListOf<MessageLite>()
        val messageJob = satellite.subscribe().onEach { sentMessages.add(it) }.launchIn(this)

        voiceInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        assertEquals(Listening, satellite.state.value)
        assertEquals(true, voiceInput.isStreaming)
        assertEquals(1, sentMessages.size)
        assertEquals("wake word", (sentMessages[0] as VoiceAssistantRequest).wakeWordPhrase)

        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })
        advanceUntilIdle()

        assertEquals(Connected, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)
        assertEquals(1, sentMessages.size)

        messageJob.cancel()
        satellite.close()
    }

    @Test
    fun should_ignore_duplicate_wake_detections() = runTest {
        val voiceInput = StubVoiceInput()
        val satellite = createSatellite(voiceInput = voiceInput)
        val sentMessages = mutableListOf<MessageLite>()
        val messageJob = satellite.subscribe().onEach { sentMessages.add(it) }.launchIn(this)

        voiceInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        voiceInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()
        voiceInput.audioResults.emit(AudioResult.WakeDetected("wake word"))

        assertEquals(Listening, satellite.state.value)
        assertEquals(true, voiceInput.isStreaming)
        assertEquals(1, sentMessages.size)
        assertEquals("wake word", (sentMessages[0] as VoiceAssistantRequest).wakeWordPhrase)

        messageJob.cancel()
        satellite.close()
    }

    @Test
    fun should_stop_existing_pipeline_and_restart_on_wake_word() = runTest {
        val voiceInput = StubVoiceInput()
        val voiceOutput = object : StubVoiceOutput() {
            var stopped = false
            override fun stopTTS() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = voiceOutput
        )
        val sentMessages = mutableListOf<MessageLite>()
        val messageJob = satellite.subscribe().onEach { sentMessages.add(it) }.launchIn(this)

        voiceInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })
        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_STT_END
        })
        advanceUntilIdle()

        assertEquals(Processing, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)

        sentMessages.clear()

        voiceInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        assertEquals(true, voiceOutput.stopped)

        // Should send a pipeline stop request, followed by a start request
        assertEquals(2, sentMessages.size)
        assertEquals(false, (sentMessages[0] as VoiceAssistantRequest).start)
        assertEquals(true, (sentMessages[1] as VoiceAssistantRequest).start)

        // Should correctly handle receiving confirmation of the
        // previous pipeline stop before the new pipeline is started
        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })
        advanceUntilIdle()
        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })
        advanceUntilIdle()

        assertEquals(Listening, satellite.state.value)
        assertEquals(true, voiceInput.isStreaming)

        messageJob.cancel()
        satellite.close()
    }

    @Test
    fun should_stop_existing_announcement_and_start_pipeline_on_wake_word() = runTest {
        val voiceInput = StubVoiceInput()
        val voiceOutput = object : StubVoiceOutput(
            wakeSound = "wake"
        ) {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }

            var stopped = false
            override fun stopTTS() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = voiceOutput
        )
        val sentMessages = mutableListOf<MessageLite>()
        val messageJob = satellite.subscribe().onEach { sentMessages.add(it) }.launchIn(this)

        satellite.handleMessage(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
        })
        advanceUntilIdle()

        assertEquals(Responding, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)
        assertEquals(listOf("preannounce", "media"), voiceOutput.mediaUrls)
        voiceOutput.mediaUrls.clear()

        voiceInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        // Should stop playback and send an announce finished response
        assertEquals(true, voiceOutput.stopped)
        assertEquals(1, sentMessages.size)
        assert(sentMessages[0] is VoiceAssistantAnnounceFinished)

        // Wake sound playback and completion
        assertEquals(listOf("wake"), voiceOutput.mediaUrls)
        voiceOutput.onCompletion()
        advanceUntilIdle()

        // Should send pipeline start request
        assertEquals(2, sentMessages.size)
        assertEquals(true, (sentMessages[1] as VoiceAssistantRequest).start)

        assertEquals(Listening, satellite.state.value)
        assertEquals(true, voiceInput.isStreaming)

        messageJob.cancel()
        satellite.close()
    }

    @Test
    fun should_stop_existing_announcement_and_restart_on_new_announcement() = runTest {
        val voiceInput = StubVoiceInput()
        val voiceOutput = object : StubVoiceOutput() {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }

            var stopped = false
            override fun stopTTS() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = voiceOutput
        )
        val sentMessages = mutableListOf<MessageLite>()
        val messageJob = satellite.subscribe().onEach { sentMessages.add(it) }.launchIn(this)

        satellite.handleMessage(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
        })
        advanceUntilIdle()

        assertEquals(Responding, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)
        assertEquals(listOf("preannounce", "media"), voiceOutput.mediaUrls)
        voiceOutput.mediaUrls.clear()

        satellite.handleMessage(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce2"
            mediaId = "media2"
        })
        advanceUntilIdle()

        // Should stop playback and send an announce finished response
        assertEquals(true, voiceOutput.stopped)
        assertEquals(1, sentMessages.size)
        assert(sentMessages[0] is VoiceAssistantAnnounceFinished)

        // New announcement played
        assertEquals(listOf("preannounce2", "media2"), voiceOutput.mediaUrls)
        assertEquals(Responding, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)

        voiceOutput.onCompletion()
        advanceUntilIdle()

        // Should send an announce finished response
        assertEquals(2, sentMessages.size)
        assert(sentMessages[1] is VoiceAssistantAnnounceFinished)

        // And revert to idle
        assertEquals(Connected, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)

        messageJob.cancel()
        satellite.close()
    }

    @Test
    fun should_stop_processing_pipeline_on_stop_word() = runTest {
        val voiceInput = StubVoiceInput()
        val voiceOutput = object : StubVoiceOutput() {
            var stopped = false
            override fun stopTTS() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = voiceOutput
        )
        val sentMessages = mutableListOf<MessageLite>()
        val messageJob = satellite.subscribe().onEach { sentMessages.add(it) }.launchIn(this)

        voiceInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })
        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_STT_END
        })
        advanceUntilIdle()

        assertEquals(Processing, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)

        sentMessages.clear()
        voiceInput.audioResults.emit(AudioResult.StopDetected())
        advanceUntilIdle()

        // Should stop playback and send a pipeline stop request
        assertEquals(true, voiceOutput.stopped)
        assertEquals(1, sentMessages.size)
        assertEquals(false, (sentMessages[0] as VoiceAssistantRequest).start)

        // And revert to idle
        assertEquals(Connected, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)

        messageJob.cancel()
        satellite.close()
    }

    @Test
    fun should_stop_tts_playback_on_stop_word() = runTest {
        val voiceInput = StubVoiceInput()
        val voiceOutput = object : StubVoiceOutput(
            wakeSound = "wake"
        ) {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }

            var stopped = false
            override fun stopTTS() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = voiceOutput
        )
        val sentMessages = mutableListOf<MessageLite>()
        val messageJob = satellite.subscribe().onEach { sentMessages.add(it) }.launchIn(this)

        voiceInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        assertEquals("wake", voiceOutput.mediaUrls[0])
        // Wake sound finished
        voiceOutput.onCompletion()
        advanceUntilIdle()

        voiceOutput.mediaUrls.clear()
        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })
        // Start TTS playback
        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_START
        })
        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_END
            data += voiceAssistantEventData { name = "url"; value = "tts" }
        })
        advanceUntilIdle()

        assertEquals("tts", voiceOutput.mediaUrls[0])
        assertEquals(Responding, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)

        sentMessages.clear()
        voiceInput.audioResults.emit(AudioResult.StopDetected())
        advanceUntilIdle()

        // Should stop playback and send an announce finished response
        assertEquals(true, voiceOutput.stopped)
        assertEquals(1, sentMessages.size)
        assert(sentMessages[0] is VoiceAssistantAnnounceFinished)

        // And revert to idle
        assertEquals(Connected, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)

        messageJob.cancel()
        satellite.close()
    }

    @Test
    fun should_stop_announcement_on_stop_word() = runTest {
        val voiceInput = StubVoiceInput()
        val voiceOutput = object : StubVoiceOutput() {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }

            var stopped = false
            override fun stopTTS() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = voiceOutput
        )
        val sentMessages = mutableListOf<MessageLite>()
        val messageJob = satellite.subscribe().onEach { sentMessages.add(it) }.launchIn(this)

        satellite.handleMessage(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
        })
        advanceUntilIdle()

        assertEquals(Responding, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)
        assertEquals(listOf("preannounce", "media"), voiceOutput.mediaUrls)

        voiceInput.audioResults.emit(AudioResult.StopDetected())
        advanceUntilIdle()

        // Should stop playback and send an announce finished response
        assertEquals(true, voiceOutput.stopped)
        assertEquals(1, sentMessages.size)
        assert(sentMessages[0] is VoiceAssistantAnnounceFinished)

        // And revert to idle
        assertEquals(Connected, satellite.state.value)
        assertEquals(false, voiceInput.isStreaming)

        messageJob.cancel()
        satellite.close()
    }

    @Test
    fun should_duck_media_volume_during_pipeline_run() = runTest {
        val voiceInput = StubVoiceInput()
        var isDucked = false
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = object : StubVoiceOutput() {
                override fun duck() {
                    isDucked = true
                }

                override fun unDuck() {
                    isDucked = false
                }
            }
        )

        voiceInput.audioResults.emit(AudioResult.WakeDetected(""))
        advanceUntilIdle()

        // Should duck immediately after the wake word
        assertEquals(true, isDucked)

        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })
        advanceUntilIdle()

        // Should un-duck and revert to idle when the pipeline ends
        assertEquals(false, isDucked)
        assertEquals(Connected, satellite.state.value)

        satellite.close()
    }

    @Test
    fun should_un_duck_media_volume_when_pipeline_stopped() = runTest {
        val voiceInput = StubVoiceInput()
        var isDucked = false
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = object : StubVoiceOutput() {
                override fun duck() {
                    isDucked = true
                }

                override fun unDuck() {
                    isDucked = false
                }
            }
        )

        voiceInput.audioResults.emit(AudioResult.WakeDetected(""))
        advanceUntilIdle()

        // Should duck immediately after the wake word
        assertEquals(true, isDucked)

        // Stop detections are ignored when the satellite is in
        // the listening state, so change state to processing
        satellite.handleMessage(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_STT_END
        })

        assertEquals(Processing, satellite.state.value)
        assertEquals(true, isDucked)

        // Stop the pipeline
        voiceInput.audioResults.emit(AudioResult.StopDetected())
        advanceUntilIdle()

        // Should un-duck and revert to idle
        assertEquals(false, isDucked)
        assertEquals(Connected, satellite.state.value)

        satellite.close()
    }

    @Test
    fun should_duck_media_volume_during_announcement() = runTest {
        val voiceInput = StubVoiceInput()
        val voiceOutput = object : StubVoiceOutput() {
            var isDucked = false
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.onCompletion = onCompletion
            }

            override fun duck() {
                isDucked = true
            }

            override fun unDuck() {
                isDucked = false
            }
        }
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = voiceOutput
        )
        satellite.handleMessage(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
        })
        advanceUntilIdle()

        // Should duck whilst the announcement is playing
        assertEquals(true, voiceOutput.isDucked)

        voiceOutput.onCompletion()
        advanceUntilIdle()

        // Should un-duck and revert to idle when the announcement finishes
        assertEquals(false, voiceOutput.isDucked)
        assertEquals(Connected, satellite.state.value)

        satellite.close()
    }

    @Test
    fun should_un_duck_media_volume_when_announcement_stopped() = runTest {
        val voiceInput = StubVoiceInput()
        val voiceOutput = object : StubVoiceOutput() {
            var isDucked = false
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.onCompletion = onCompletion
            }

            override fun duck() {
                isDucked = true
            }

            override fun unDuck() {
                isDucked = false
            }
        }
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = voiceOutput
        )
        satellite.handleMessage(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
        })
        advanceUntilIdle()

        // Should duck whilst the announcement is playing
        assertEquals(true, voiceOutput.isDucked)

        // Stop the announcement
        voiceInput.audioResults.emit(AudioResult.StopDetected())
        advanceUntilIdle()

        // Should un-duck and revert to idle
        assertEquals(false, voiceOutput.isDucked)
        assertEquals(Connected, satellite.state.value)

        satellite.close()
    }

    @Test
    fun should_not_un_duck_media_volume_when_starting_conversation() = runTest {
        val voiceInput = StubVoiceInput()
        val voiceOutput = object : StubVoiceOutput() {
            var isDucked = false
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.onCompletion = onCompletion
            }

            override fun duck() {
                isDucked = true
            }

            override fun unDuck() {
                isDucked = false
            }
        }
        val satellite = createSatellite(
            voiceInput = voiceInput,
            voiceOutput = voiceOutput
        )
        satellite.handleMessage(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
            startConversation = true
        })
        advanceUntilIdle()

        // Should duck whilst the announcement is playing
        assertEquals(true, voiceOutput.isDucked)

        // End the announcement and start conversation
        voiceOutput.onCompletion()
        advanceUntilIdle()

        // Should be ducked and in the listening state
        assertEquals(true, voiceOutput.isDucked)
        assertEquals(Listening, satellite.state.value)
        satellite.close()
    }
}