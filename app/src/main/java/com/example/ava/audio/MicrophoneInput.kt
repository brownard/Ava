package com.example.ava.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import timber.log.Timber
import java.nio.ByteBuffer

class MicrophoneInput(
    val audioSource: Int = DEFAULT_AUDIO_SOURCE,
    val sampleRateInHz: Int = DEFAULT_SAMPLE_RATE_IN_HZ,
    val channelConfig: Int = DEFAULT_CHANNEL_CONFIG,
    val audioFormat: Int = DEFAULT_AUDIO_FORMAT,
    val enableNoiseSuppressor: Boolean = true,
    val enableAutomaticGainControl: Boolean = true,
    val enableAcousticEchoCanceler: Boolean = true
) : AutoCloseable {
    val bufferSize =
        AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
    private val buffer = ByteBuffer.allocateDirect(bufferSize)
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null

    val isRecording get() = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (audioRecord == null) {
            audioRecord = createAudioRecord()
        }
        if (!isRecording) {
            Timber.d("Starting microphone")
            audioRecord?.startRecording()
        } else {
            Timber.w("Microphone already started")
        }
    }

    fun read(): ByteBuffer {
        val audioRecord = this.audioRecord ?: error("Microphone not started")
        buffer.clear()
        val read = audioRecord.read(buffer, bufferSize)
        check(read >= 0) {
            "error reading audio, read: $read"
        }
        buffer.limit(read)
        return buffer
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun createAudioRecord(): AudioRecord {
        val audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            bufferSize * 2
        )
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            "Failed to initialize AudioRecord"
        }
        val sessionId = audioRecord.audioSessionId
        if (enableNoiseSuppressor && NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
            Timber.d("NoiseSuppressor enabled: ${noiseSuppressor != null}")
        }
        if (enableAutomaticGainControl && AutomaticGainControl.isAvailable()) {
            automaticGainControl = AutomaticGainControl.create(sessionId)?.also { it.enabled = true }
            Timber.d("AutomaticGainControl enabled: ${automaticGainControl != null}")
        }
        if (enableAcousticEchoCanceler && AcousticEchoCanceler.isAvailable()) {
            acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true }
            Timber.d("AcousticEchoCanceler enabled: ${acousticEchoCanceler != null}")
        }
        return audioRecord
    }

    override fun close() {
        noiseSuppressor?.release()
        noiseSuppressor = null
        automaticGainControl?.release()
        automaticGainControl = null
        acousticEchoCanceler?.release()
        acousticEchoCanceler = null
        audioRecord?.let {
            if (isRecording) {
                it.stop()
            }
            it.release()
            audioRecord = null
        }
        Timber.d("Microphone closed")
    }

    companion object {
        const val DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        const val DEFAULT_SAMPLE_RATE_IN_HZ = 16000
        const val DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}