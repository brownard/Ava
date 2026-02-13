package com.example.ava

import com.example.ava.esphome.voicesatellite.VoiceSatellite
import com.example.ava.esphome.voicesatellite.VoiceSatelliteAudioInput
import com.example.ava.esphome.voicesatellite.VoiceSatellitePlayer
import com.example.ava.esphome.voicesatellite.VoiceTimer
import com.example.ava.players.AudioPlayer
import com.example.ava.settings.VoiceSatelliteSettings
import com.example.ava.settings.VoiceSatelliteSettingsStore
import com.example.ava.stubs.StubAudioPlayer
import com.example.ava.stubs.StubSettingState
import com.example.esphomeproto.AsynchronousCodedChannel
import com.example.esphomeproto.api.VoiceAssistantTimerEvent
import com.example.esphomeproto.api.voiceAssistantTimerEventResponse
import com.google.protobuf.MessageLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketAddress
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VoiceSatelliteTimerTest {
    class StubAudioInput :
        VoiceSatelliteAudioInput(emptyList(), emptyList(), emptyList(), emptyList()) {
        val result = MutableStateFlow<AudioResult?>(null)
        override fun start(): Flow<AudioResult> = result.filterNotNull()
    }

    // Simplified Settings Store: Use a secondary constructor or default property values
    class StubSettingsStore(
        val settings: VoiceSatelliteSettings = VoiceSatelliteSettings(
            name = "Test", macAddress = "00:11:22:33:44:55", autoStart = false, serverPort = 16054
        )
    ) : VoiceSatelliteSettingsStore {
        override val name = StubSettingState(settings.name)
        override val serverPort = StubSettingState(settings.serverPort)
        override val autoStart = StubSettingState(settings.autoStart)
        override suspend fun ensureMacAddressIsSet() {}
        override fun getFlow() = MutableStateFlow(settings)
        override suspend fun get() = settings
        override suspend fun update(transform: suspend (VoiceSatelliteSettings) -> VoiceSatelliteSettings) {}
    }

    class EspHomeClient(val host: String, val port: Int) {
        private var channel: AsynchronousCodedChannel<AsynchronousSocketChannel>? = null

        suspend fun connect(timeoutMillis: Long = 1000, delayMillis: Long = 10) {
            withTimeout(timeoutMillis) {
                while (channel == null) {
                    runCatching {
                        val socket = AsynchronousSocketChannel.open()
                        socket.awaitConnect(InetSocketAddress(host, port))
                        channel = AsynchronousCodedChannel(socket)
                    }
                    if (channel == null) delay(delayMillis)
                }
            }
        }

        private suspend fun AsynchronousSocketChannel.awaitConnect(address: SocketAddress) =
            suspendCancellableCoroutine { cont ->
                connect(address, null, object : CompletionHandler<Void?, Any?> {
                    override fun completed(result: Void?, attachment: Any?) { cont.resume(Unit) }
                    override fun failed(exc: Throwable, attachment: Any?) { cont.resumeWithException(exc) }
                })
            }

        suspend fun sendMessage(message: MessageLite) {
            channel?.writeMessage(message)
        }

        fun close() {
            channel?.close()
        }
    }

    private fun start_satellite(
        port: Int,
        job: Job,
        player: AudioPlayer = StubAudioPlayer(),
        repeatTimerFinishedSound: Boolean = false
    ) =
        VoiceSatellite(
            coroutineContext = Dispatchers.Default + job,
            name = "Test Satellite",
            port = port,
            audioInput = StubAudioInput(),
            player = VoiceSatellitePlayer(
                ttsPlayer = player,
                mediaPlayer = player,
                enableWakeSound = StubSettingState(true),
                wakeSound = StubSettingState("wake.mp3"),
                timerFinishedSound = StubSettingState("timer.mp3"),
                repeatTimerFinishedSound = StubSettingState(repeatTimerFinishedSound)
            ),
            settingsStore = StubSettingsStore()
        ).apply { start() }


    suspend fun VoiceSatellite.getTimers(
        timeoutMillis: Long = 500,
        predicate: suspend (List<VoiceTimer>) -> Boolean
    ): List<VoiceTimer> {
        return withTimeout(timeoutMillis) { allTimers.first(predicate) }
    }


    private fun getFreePort() = ServerSocket(0).use { it.localPort }

    @Test
    fun should_store_and_sort_timers() = runBlocking {
        val port = getFreePort()
        val job = Job()
        val voiceSatellite = start_satellite(port, job)
        val client = EspHomeClient("localhost", port).apply { connect() }

        client.sendMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "running1"
            totalSeconds = 61
            secondsLeft = 60
            isActive = true
        })

        client.sendMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "paused1"
            totalSeconds = 62
            secondsLeft = 10
            isActive = false // Sorted last because paused
        })
        client.sendMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "running2"
            totalSeconds = 63
            secondsLeft = 50 // Sorted first
            isActive = true
            name = "Named"
        })

        var timers = voiceSatellite.getTimers { it.size == 3 }
        assertEquals(listOf("running2",  "running1", "paused1"), timers.map { it.id })
        assertEquals(listOf("Named",  "", ""), timers.map { it.name })
        assertEquals(listOf(63.seconds, 61.seconds, 62.seconds), timers.map { it.totalDuration })
        assertEquals(VoiceTimer.Paused("paused1", "", 62.seconds, 10.seconds), timers[2])

        val remaining2Millis = timers[0].remainingDuration(Clock.System.now()).inWholeMilliseconds
        assertTrue { remaining2Millis <= 50_000 }
        assertTrue { remaining2Millis > 49_900 }

        client.sendMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_CANCELLED
            timerId = "running1"
        })
        client.sendMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_UPDATED
            timerId = "paused1"
            totalSeconds = 62
            secondsLeft = 10
            isActive = true // Unpaused now
        })

        timers = voiceSatellite.getTimers { it[0].id == "paused1" }
        assertEquals(listOf("paused1", "running2"), timers.map { it.id })

        voiceSatellite.close()
        client.close()
        job.cancel()
    }

    @Test
    fun should_display_then_remove_finished_timer() = runBlocking {
        var audioPlayed: String? = null
        val audioPlayer = object : StubAudioPlayer() {
            override fun play(mediaUri: String, onCompletion: () -> Unit) {
                audioPlayed = mediaUri
                onCompletion()
            }
        }
        val port = getFreePort()
        val job = Job()
        val voiceSatellite = start_satellite(port, job, audioPlayer, false)
        val client = EspHomeClient("localhost", port).apply { connect() }

        client.sendMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "timer2"
            totalSeconds = 61
            secondsLeft = 5
            isActive = true
            name = "Will ring"
        })
        client.sendMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "timer1"
            totalSeconds = 60
            secondsLeft = 60
            isActive = true
        })

        var timers = voiceSatellite.getTimers { it.size == 2 }
        assertEquals(listOf("timer2", "timer1"), timers.map { it.id })
        assert(timers[0] is VoiceTimer.Running)

        client.sendMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            timerId = "timer2"
            totalSeconds = 61
            secondsLeft = 0
            isActive = false
            name = "Will ring"
        })
        timers = voiceSatellite.getTimers { it[0] is VoiceTimer.Ringing }
        assertEquals(VoiceTimer.Ringing("timer2", "Will ring", 61.seconds), timers[0])
        assertEquals(Duration.ZERO, timers[0].remainingDuration(Clock.System.now()))

        // TODO kotlinx-coroutines-test to shorten this test
        timers = voiceSatellite.getTimers(2000) { it[0] is VoiceTimer.Running }
        assertEquals(listOf("timer1"), timers.map { it.id })
        assertEquals("timer.mp3", audioPlayed)
    }

    @Test
    fun should_remove_repeating_timer_on_wake_word() = runBlocking {
        val port = getFreePort()
        val job = Job()
        val voiceSatellite = start_satellite(port, job, repeatTimerFinishedSound = true)
        val client = EspHomeClient("localhost", port).apply { connect() }

        client.sendMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED
            timerId = "ringing1"
            totalSeconds = 61
            secondsLeft = 0
            isActive = false
            name = "Will ring"
        })
        client.sendMessage(voiceAssistantTimerEventResponse {
            eventType = VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED
            timerId = "paused1"
            totalSeconds = 62
            secondsLeft = 10
            isActive = false
        })
        var timers = voiceSatellite.getTimers { it.size == 2 }
        assert(timers[0] is VoiceTimer.Ringing)
        assert(timers[1] is VoiceTimer.Paused)

        (voiceSatellite.audioInput as StubAudioInput).result.update {
            VoiceSatelliteAudioInput.AudioResult.WakeDetected("stop")
        }

        timers = voiceSatellite.getTimers { it[0] is VoiceTimer.Paused }
        assertEquals(1, timers.size)
    }
}
