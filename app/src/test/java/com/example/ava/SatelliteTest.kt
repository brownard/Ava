package com.example.ava

import com.example.ava.esphome.Connected
import com.example.ava.esphome.voicesatellite.AudioResult
import com.example.ava.esphome.voicesatellite.Listening
import com.example.ava.esphome.voicesatellite.Processing
import com.example.ava.esphome.voicesatellite.Responding
import com.example.ava.esphome.voicesatellite.VoiceSatellite
import com.example.ava.esphome.voicesatellite.VoiceSatelliteAudioInput
import com.example.ava.esphome.voicesatellite.VoiceSatellitePlayer
import com.example.ava.server.Server
import com.example.ava.stubs.StubAudioPlayer
import com.example.ava.stubs.StubServer
import com.example.ava.stubs.StubSettingState
import com.example.ava.stubs.StubVoiceSatelliteAudioInput
import com.example.ava.stubs.StubVoiceSatellitePlayer
import com.example.ava.stubs.StubVoiceSatelliteSettingsStore
import com.example.esphomeproto.api.VoiceAssistantAnnounceFinished
import com.example.esphomeproto.api.VoiceAssistantEvent
import com.example.esphomeproto.api.VoiceAssistantRequest
import com.example.esphomeproto.api.voiceAssistantAnnounceRequest
import com.example.esphomeproto.api.voiceAssistantEventData
import com.example.esphomeproto.api.voiceAssistantEventResponse
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class SatelliteTest {
    fun TestScope.createSatellite(
        server: Server = StubServer(),
        audioInput: VoiceSatelliteAudioInput = StubVoiceSatelliteAudioInput(),
        player: VoiceSatellitePlayer = StubVoiceSatellitePlayer()
    ) = VoiceSatellite(
        coroutineContext = this.coroutineContext,
        "Test Satellite",
        server = server,
        audioInput = audioInput,
        player = player,
        settingsStore = StubVoiceSatelliteSettingsStore()
    ).apply {
        start()
        advanceUntilIdle()
    }

    @Test
    fun should_handle_wake_word_intercept_during_setup() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val satellite = createSatellite(server = server, audioInput = audioInput)

        audioInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        assertEquals(Listening, satellite.state.value)
        assertEquals(true, audioInput.isStreaming)
        assertEquals(1, server.sentMessages.size)
        assertEquals("wake word", (server.sentMessages[0] as VoiceAssistantRequest).wakeWordPhrase)

        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })
        advanceUntilIdle()

        assertEquals(Connected, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)
        assertEquals(1, server.sentMessages.size)

        satellite.close()
    }

    @Test
    fun should_ignore_duplicate_wake_detections() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val satellite = createSatellite(server = server, audioInput = audioInput)

        audioInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        audioInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()
        audioInput.audioResults.emit(AudioResult.WakeDetected("wake word"))

        assertEquals(Listening, satellite.state.value)
        assertEquals(true, audioInput.isStreaming)
        assertEquals(1, server.sentMessages.size)
        assertEquals("wake word", (server.sentMessages[0] as VoiceAssistantRequest).wakeWordPhrase)

        satellite.close()
    }

    @Test
    fun should_stop_existing_pipeline_and_restart_on_wake_word() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val ttsPlayer = object : StubAudioPlayer() {
            var stopped = false
            override fun stop() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            server,
            audioInput,
            StubVoiceSatellitePlayer(ttsPlayer = ttsPlayer)
        )

        audioInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })
        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_STT_END
        })
        advanceUntilIdle()

        assertEquals(Processing, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)

        server.sentMessages.clear()

        audioInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        assertEquals(true, ttsPlayer.stopped)

        // Should send a pipeline stop request, followed by a start request
        assertEquals(2, server.sentMessages.size)
        assertEquals(false, (server.sentMessages[0] as VoiceAssistantRequest).start)
        assertEquals(true, (server.sentMessages[1] as VoiceAssistantRequest).start)

        // Should correctly handle receiving confirmation of the
        // previous pipeline stop before the new pipeline is started
        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END
        })
        advanceUntilIdle()
        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })
        advanceUntilIdle()

        assertEquals(Listening, satellite.state.value)
        assertEquals(true, audioInput.isStreaming)

        satellite.close()
    }

    @Test
    fun should_stop_existing_announcement_and_start_pipeline_on_wake_word() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val ttsPlayer = object : StubAudioPlayer() {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }

            var stopped = false
            override fun stop() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            server,
            audioInput,
            StubVoiceSatellitePlayer(ttsPlayer = ttsPlayer, wakeSound = StubSettingState("wake"))
        )

        server.receivedMessages.emit(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
        })
        advanceUntilIdle()

        assertEquals(Responding, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)
        assertEquals(listOf("preannounce", "media"), ttsPlayer.mediaUrls)
        ttsPlayer.mediaUrls.clear()

        audioInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        // Should stop playback and send an announce finished response
        assertEquals(true, ttsPlayer.stopped)
        assertEquals(1, server.sentMessages.size)
        assert(server.sentMessages[0] is VoiceAssistantAnnounceFinished)

        // Wake sound playback and completion
        assertEquals(listOf("wake"), ttsPlayer.mediaUrls)
        ttsPlayer.onCompletion()
        advanceUntilIdle()

        // Should send pipeline start request
        assertEquals(2, server.sentMessages.size)
        assertEquals(true, (server.sentMessages[1] as VoiceAssistantRequest).start)

        assertEquals(Listening, satellite.state.value)
        assertEquals(true, audioInput.isStreaming)

        satellite.close()
    }

    @Test
    fun should_stop_existing_announcement_and_restart_on_new_announcement() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val ttsPlayer = object : StubAudioPlayer() {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }

            var stopped = false
            override fun stop() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            server,
            audioInput,
            StubVoiceSatellitePlayer(ttsPlayer = ttsPlayer, wakeSound = StubSettingState("wake"))
        )

        server.receivedMessages.emit(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
        })
        advanceUntilIdle()

        assertEquals(Responding, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)
        assertEquals(listOf("preannounce", "media"), ttsPlayer.mediaUrls)
        ttsPlayer.mediaUrls.clear()

        server.receivedMessages.emit(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce2"
            mediaId = "media2"
        })
        advanceUntilIdle()

        // Should stop playback and send an announce finished response
        assertEquals(true, ttsPlayer.stopped)
        assertEquals(1, server.sentMessages.size)
        assert(server.sentMessages[0] is VoiceAssistantAnnounceFinished)

        // New announcement played
        assertEquals(listOf("preannounce2", "media2"), ttsPlayer.mediaUrls)
        assertEquals(Responding, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)

        ttsPlayer.onCompletion()
        advanceUntilIdle()

        // Should send an announce finished response
        assertEquals(2, server.sentMessages.size)
        assert(server.sentMessages[1] is VoiceAssistantAnnounceFinished)

        // And revert to idle
        assertEquals(Connected, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)

        satellite.close()
    }

    @Test
    fun should_stop_processing_pipeline_on_stop_word() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val ttsPlayer = object : StubAudioPlayer() {
            var stopped = false
            override fun stop() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            server,
            audioInput,
            StubVoiceSatellitePlayer(ttsPlayer = ttsPlayer, wakeSound = StubSettingState("wake"))
        )

        audioInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })
        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_STT_END
        })
        advanceUntilIdle()

        assertEquals(Processing, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)

        server.sentMessages.clear()
        audioInput.audioResults.emit(AudioResult.StopDetected())
        advanceUntilIdle()

        // Should stop playback and send a pipeline stop request
        assertEquals(true, ttsPlayer.stopped)
        assertEquals(1, server.sentMessages.size)
        assertEquals(false, (server.sentMessages[0] as VoiceAssistantRequest).start)

        // And revert to idle
        assertEquals(Connected, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)

        satellite.close()
    }

    @Test
    fun should_stop_tts_playback_on_stop_word() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val ttsPlayer = object : StubAudioPlayer() {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }

            var stopped = false
            override fun stop() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            server,
            audioInput,
            StubVoiceSatellitePlayer(ttsPlayer = ttsPlayer, wakeSound = StubSettingState("wake"))
        )

        audioInput.audioResults.emit(AudioResult.WakeDetected("wake word"))
        advanceUntilIdle()

        assertEquals("wake", ttsPlayer.mediaUrls[0])
        // Wake sound finished
        ttsPlayer.onCompletion()
        advanceUntilIdle()

        ttsPlayer.mediaUrls.clear()
        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START
        })
        // Start TTS playback
        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_START
        })
        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_TTS_END
            data += voiceAssistantEventData { name = "url"; value = "tts" }
        })
        advanceUntilIdle()

        assertEquals("tts", ttsPlayer.mediaUrls[0])
        assertEquals(Responding, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)

        server.sentMessages.clear()
        audioInput.audioResults.emit(AudioResult.StopDetected())
        advanceUntilIdle()

        // Should stop playback and send an announce finished response
        assertEquals(true, ttsPlayer.stopped)
        assertEquals(1, server.sentMessages.size)
        assert(server.sentMessages[0] is VoiceAssistantAnnounceFinished)

        // And revert to idle
        assertEquals(Connected, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)

        satellite.close()
    }

    @Test
    fun should_stop_announcement_on_stop_word() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val ttsPlayer = object : StubAudioPlayer() {
            val mediaUrls = mutableListOf<String>()
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.mediaUrls.addAll(mediaUris)
                this.onCompletion = onCompletion
            }

            var stopped = false
            override fun stop() {
                stopped = true
            }
        }
        val satellite = createSatellite(
            server,
            audioInput,
            StubVoiceSatellitePlayer(ttsPlayer = ttsPlayer, wakeSound = StubSettingState("wake"))
        )

        server.receivedMessages.emit(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
        })
        advanceUntilIdle()

        assertEquals(Responding, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)
        assertEquals(listOf("preannounce", "media"), ttsPlayer.mediaUrls)

        audioInput.audioResults.emit(AudioResult.StopDetected())
        advanceUntilIdle()

        // Should stop playback and send an announce finished response
        assertEquals(true, ttsPlayer.stopped)
        assertEquals(1, server.sentMessages.size)
        assert(server.sentMessages[0] is VoiceAssistantAnnounceFinished)

        // And revert to idle
        assertEquals(Connected, satellite.state.value)
        assertEquals(false, audioInput.isStreaming)

        satellite.close()
    }

    @Test
    fun should_duck_media_volume_during_pipeline_run() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        var isDucked = false
        val satellite = createSatellite(
            server,
            audioInput,
            object : StubVoiceSatellitePlayer(
                enableWakeSound = StubSettingState(false)
            ) {
                override fun duck() {
                    isDucked = true
                }

                override fun unDuck() {
                    isDucked = false
                }
            }
        )
        audioInput.audioResults.emit(AudioResult.WakeDetected(""))
        advanceUntilIdle()

        // Should duck immediately after the wake word
        assertEquals(true, isDucked)

        server.receivedMessages.emit(voiceAssistantEventResponse {
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
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        var isDucked = false
        val satellite = createSatellite(
            server,
            audioInput,
            object : StubVoiceSatellitePlayer(
                enableWakeSound = StubSettingState(false)
            ) {
                override fun duck() {
                    isDucked = true
                }

                override fun unDuck() {
                    isDucked = false
                }
            }
        )

        audioInput.audioResults.emit(AudioResult.WakeDetected(""))
        advanceUntilIdle()

        // Should duck immediately after the wake word
        assertEquals(true, isDucked)

        // Stop detections are ignored when the satellite is in
        // the listening state, so change state to processing
        server.receivedMessages.emit(voiceAssistantEventResponse {
            eventType = VoiceAssistantEvent.VOICE_ASSISTANT_STT_END
        })

        assertEquals(Processing, satellite.state.value)
        assertEquals(true, isDucked)

        // Stop the pipeline
        audioInput.audioResults.emit(AudioResult.StopDetected())
        advanceUntilIdle()

        // Should un-duck and revert to idle
        assertEquals(false, isDucked)
        assertEquals(Connected, satellite.state.value)

        satellite.close()
    }

    @Test
    fun should_duck_media_volume_during_announcement() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val ttsPlayer = object : StubAudioPlayer() {
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.onCompletion = onCompletion
            }
        }
        var isDucked = false
        val satellite = createSatellite(
            server,
            audioInput,
            object : StubVoiceSatellitePlayer(
                ttsPlayer = ttsPlayer,
                enableWakeSound = StubSettingState(false)
            ) {
                override fun duck() {
                    isDucked = true
                }

                override fun unDuck() {
                    isDucked = false
                }
            }
        )
        server.receivedMessages.emit(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
        })
        advanceUntilIdle()

        // Should duck whilst the announcement is playing
        assertEquals(true, isDucked)

        ttsPlayer.onCompletion()
        advanceUntilIdle()

        // Should un-duck and revert to idle when the announcement finishes
        assertEquals(false, isDucked)
        assertEquals(Connected, satellite.state.value)

        satellite.close()
    }

    @Test
    fun should_un_duck_media_volume_when_announcement_stopped() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val ttsPlayer = object : StubAudioPlayer() {
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.onCompletion = onCompletion
            }
        }
        var isDucked = false
        val satellite = createSatellite(
            server,
            audioInput,
            object : StubVoiceSatellitePlayer(
                ttsPlayer = ttsPlayer,
                enableWakeSound = StubSettingState(false)
            ) {
                override fun duck() {
                    isDucked = true
                }

                override fun unDuck() {
                    isDucked = false
                }
            }
        )
        server.receivedMessages.emit(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
        })
        advanceUntilIdle()

        // Should duck whilst the announcement is playing
        assertEquals(true, isDucked)

        // Stop the announcement
        audioInput.audioResults.emit(AudioResult.StopDetected())
        advanceUntilIdle()

        // Should un-duck and revert to idle
        assertEquals(false, isDucked)
        assertEquals(Connected, satellite.state.value)

        satellite.close()
    }

    @Test
    fun should_not_un_duck_media_volume_when_starting_conversation() = runTest {
        val server = StubServer()
        val audioInput = StubVoiceSatelliteAudioInput()
        val ttsPlayer = object : StubAudioPlayer() {
            lateinit var onCompletion: () -> Unit
            override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                this.onCompletion = onCompletion
            }
        }
        var isDucked = false
        val satellite = createSatellite(
            server,
            audioInput,
            object : StubVoiceSatellitePlayer(
                ttsPlayer = ttsPlayer,
                enableWakeSound = StubSettingState(false)
            ) {
                override fun duck() {
                    isDucked = true
                }

                override fun unDuck() {
                    isDucked = false
                }
            }
        )
        server.receivedMessages.emit(voiceAssistantAnnounceRequest {
            preannounceMediaId = "preannounce"
            mediaId = "media"
            startConversation = true
        })
        advanceUntilIdle()

        // Should duck whilst the announcement is playing
        assertEquals(true, isDucked)

        // End the announcement and start conversation
        ttsPlayer.onCompletion()
        advanceUntilIdle()

        // Should be ducked and in the listening state
        assertEquals(true, isDucked)
        assertEquals(Listening, satellite.state.value)
        satellite.close()
    }
}