package com.example.ava.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

interface Microphone : AutoCloseable {
    fun start()
    fun read(): ByteBuffer
    fun stop()
}

class MicrophoneInput(
    val audioSource: Int = DEFAULT_AUDIO_SOURCE,
    val sampleRateInHz: Int = DEFAULT_SAMPLE_RATE_IN_HZ,
    val channelConfig: Int = DEFAULT_CHANNEL_CONFIG,
    val audioFormat: Int = DEFAULT_AUDIO_FORMAT,
    val enableNoiseSuppressor: Boolean = true,
    val enableAcousticEchoCanceler: Boolean = true,
    val enableAutomaticGainControl: Boolean = true,
    //val context: Context
) : Microphone {
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRateInHz,
        channelConfig,
        audioFormat
    )
    private val buffer = ByteBuffer.allocateDirect(bufferSize)
    private var audioRecord: AudioRecord? = null
    private var preprocessor: Preprocessor? = null
    private var _started = AtomicBoolean(false)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() {
        if (!_started.compareAndSet(false, true)) {
            Timber.w("Microphone already started")
            return
        }
        Timber.d("Starting microphone")
        audioRecord = AudioRecord.Builder()
            .setAudioSource(audioSource)
            .setBufferSizeInBytes(bufferSize * 2)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRateInHz)
                    .setEncoding(audioFormat)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .build()

        check(audioRecord!!.state == AudioRecord.STATE_INITIALIZED) {
            "Failed to initialize AudioRecord"
        }

        // This doesn't actually seem to work on devices that I've tested (S9 and Lenovo M10 tablet)
        // Instead the only way I've got the preprocessing effects to change is by changing the mic's source.
        preprocessor = Preprocessor(
            audioRecord!!.audioSessionId,
            enableNoiseSuppressor,
            enableAcousticEchoCanceler,
            enableAutomaticGainControl
        )
        preprocessor!!.enable()

        // Setting the audio mode to MODE_IN_COMMUNICATION is apparently
        // required on some devices to trigger use of AEC, though it can
        // cause the device to indicate that a call is active.
        // IsSpeakerphoneOn is apparently required to ensure the mic listens
        // for far-audio, but on a Samsung Galaxy S9 it seems to also effect
        // audio playback quality which is noticeable when playing media.
        // Enabling this requires MODIFY_AUDIO_SETTINGS permission
        //val am = context.getSystemService(AudioManager::class.java)
        //am.mode = AudioManager.MODE_IN_COMMUNICATION
        //am.isSpeakerphoneOn = true

        audioRecord!!.startRecording()
        Timber.d("Microphone started")
    }

    override fun read(): ByteBuffer {
        check(audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            "Microphone not started"
        }
        buffer.clear()
        val read = audioRecord!!.read(buffer, bufferSize)
        check(read >= 0) {
            "error reading audio, read: $read"
        }
        buffer.limit(read)
        return buffer
    }

    override fun stop() {
        if (!_started.compareAndSet(true, false)) {
            Timber.w("Microphone already stopped")
            return
        }
        audioRecord?.release()
        preprocessor?.release()
        audioRecord = null
        preprocessor = null
        Timber.d("Microphone stopped")
    }

    override fun close() {
        stop()
    }

    companion object {
        const val DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        const val DEFAULT_SAMPLE_RATE_IN_HZ = 16000
        const val DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}