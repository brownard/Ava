package com.example.ava.esphome.android.microphone

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.example.ava.esphome.microphone.Microphone
import timber.log.Timber
import java.nio.ByteBuffer

const val DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
const val DEFAULT_SAMPLE_RATE_IN_HZ = 16000
const val DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
const val DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

class AudioRecordMicrophone(
    val audioSource: Int = DEFAULT_AUDIO_SOURCE,
    val sampleRateInHz: Int = DEFAULT_SAMPLE_RATE_IN_HZ,
    val channelConfig: Int = DEFAULT_CHANNEL_CONFIG,
    val audioFormat: Int = DEFAULT_AUDIO_FORMAT
) : Microphone {
    private val bufferSize =
        AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
    private val buffer = ByteBuffer.allocateDirect(bufferSize)
    private var audioRecord: AudioRecord? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() {
        audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            bufferSize * 2
        ).apply {
            check(state == AudioRecord.STATE_INITIALIZED) { "Failed to initialize AudioRecord" }
            Timber.d("Starting microphone")
            startRecording()
        }
    }

    override fun read(): ByteBuffer {
        audioRecord?.let {
            val read = it.read(buffer, bufferSize)
            check(read >= 0) { "error reading audio, read: $read" }
            // AudioRecord.read ignores the position and limit
            // of the buffer so manually update them.
            buffer.position(0)
            buffer.limit(read)
        } ?: error("Microphone not started")
        return buffer
    }

    override fun stop() {
        audioRecord?.let {
            it.release()
            audioRecord = null
            Timber.d("Microphone stopped")
        }
    }

    override fun close() {
        stop()
    }
}