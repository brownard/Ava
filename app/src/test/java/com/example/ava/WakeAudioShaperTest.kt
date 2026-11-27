package com.example.ava

import com.example.ava.esphome.voiceassistant.WakeAudioShaper
import com.google.protobuf.ByteString
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WakeAudioShaperTest {
    /** [ms] of audio whose samples alternate between +[level] and -[level] */
    private fun chunk(level: Int, ms: Int = 10): ByteString {
        val bytes = ByteArray(ms * WakeAudioShaper.BYTES_PER_MS)
        for (i in bytes.indices step 2) {
            val sample = if ((i / 2) % 2 == 0) level else -level
            bytes[i] = (sample and 0xff).toByte()
            bytes[i + 1] = (sample shr 8).toByte()
        }
        return ByteString.copyFrom(bytes)
    }

    private fun List<ByteString>.ms() = sumOf { it.size() } / WakeAudioShaper.BYTES_PER_MS
    private fun ByteString.isSilence() = toByteArray().all { it == 0.toByte() }

    /** 700 ms of quiet background followed by 300 ms of wake word, then a detection */
    private fun wokenShaper(lookbackMs: Int = 200): WakeAudioShaper {
        val shaper = WakeAudioShaper(lookbackMs = lookbackMs)
        repeat(70) { shaper.idle(chunk(100)) }
        repeat(30) { shaper.idle(chunk(5000)) }
        shaper.wake()
        return shaper
    }

    @Test
    fun should_pass_audio_through_when_streaming_without_a_wake_word() {
        val shaper = WakeAudioShaper(lookbackMs = 200)
        val audio = chunk(100)
        assertEquals(listOf(audio), shaper.stream(audio))
    }

    @Test
    fun should_send_lookback_after_silence_when_speech_goes_on() {
        val shaper = wokenShaper()
        // Still deciding: nothing sent yet
        assertEquals(emptyList(), shaper.stream(chunk(100)))

        val speech = chunk(5000)
        val sent = shaper.stream(speech)

        // 300 ms of silence, the last 200 ms before the detection, then the held audio
        assertTrue(sent.first().isSilence())
        assertEquals(300, sent.first().size() / WakeAudioShaper.BYTES_PER_MS)
        assertEquals(300 + 200 + 20, sent.ms())
        assertEquals(chunk(5000), sent[1].substring(0, chunk(5000).size()))
        assertEquals(speech, sent.last())

        // Then everything goes through
        val next = chunk(100)
        assertEquals(listOf(next), shaper.stream(next))
    }

    @Test
    fun should_send_silence_until_speech_after_a_pause() {
        val shaper = wokenShaper()
        val sent = mutableListOf<ByteString>()
        // One second of wake sound and room noise below the speech threshold
        repeat(100) { sent += shaper.stream(chunk(300)) }

        // All of it is sent as silence except the last 200 ms, still held
        assertTrue(sent.all { it.isSilence() })
        assertEquals(800, sent.ms())

        val speech = chunk(5000)
        val afterSpeech = shaper.stream(speech)
        // The last 200 ms are sent as they are, followed by the speech
        assertEquals(210, afterSpeech.ms())
        assertTrue(afterSpeech.none { it.isSilence() })
        assertEquals(speech, afterSpeech.last())

        val next = chunk(100)
        assertEquals(listOf(next), shaper.stream(next))
    }

    @Test
    fun should_not_send_lookback_when_disabled() {
        val shaper = wokenShaper(lookbackMs = 0)
        val speech = chunk(5000)
        assertEquals(listOf(speech), shaper.stream(speech))
    }

    @Test
    fun should_raise_speech_threshold_with_background_noise() {
        val shaper = WakeAudioShaper(lookbackMs = 200)
        repeat(100) { shaper.idle(chunk(1000)) }
        shaper.wake()

        // 2000 is below four times the background: not speech
        val sent = mutableListOf<ByteString>()
        repeat(40) { sent += shaper.stream(chunk(2000)) }
        assertTrue(sent.all { it.isSilence() })

        val speech = chunk(4000)
        assertEquals(speech, shaper.stream(speech).last())
    }

    @Test
    fun should_pass_audio_through_after_streaming_stops() {
        val shaper = wokenShaper()
        shaper.idle(chunk(100))
        val audio = chunk(100)
        assertEquals(listOf(audio), shaper.stream(audio))
    }
}
