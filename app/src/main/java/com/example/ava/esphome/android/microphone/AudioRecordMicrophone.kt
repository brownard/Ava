package com.example.ava.esphome.android.microphone

import android.Manifest
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.example.ava.esphome.microphone.Microphone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import java.nio.ByteBuffer

const val DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
const val DEFAULT_AUDIO_MODE = AudioManager.MODE_NORMAL
const val DEFAULT_SAMPLE_RATE_IN_HZ = 16000
const val DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
const val DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

fun audioRecordMicrophoneFlow(
    audioManager: AudioManager,
    audioSource: Flow<Int> = flowOf(DEFAULT_AUDIO_SOURCE),
    audioMode: Flow<Int> = flowOf(DEFAULT_AUDIO_MODE),
    useSpeakerphone: Flow<Boolean> = flowOf(false)
): Flow<Microphone> = combine(
    audioSource,
    audioMode,
    useSpeakerphone
) { audioSource, audioMode, useSpeakerPhone ->
    AudioRecordMicrophone(
        audioManager = audioManager,
        audioSource = audioSource,
        audioMode = audioMode,
        useSpeakerphone = useSpeakerPhone
    )
}

class AudioRecordMicrophone(
    val audioManager: AudioManager? = null,
    val audioSource: Int = DEFAULT_AUDIO_SOURCE,
    val audioMode: Int = DEFAULT_AUDIO_MODE,
    val useSpeakerphone: Boolean = false,
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
        audioManager?.apply {
            mode = audioMode
            setSpeakerphoneCompat(useSpeakerphone)
        }
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
        audioManager?.apply {
            mode = AudioManager.MODE_NORMAL
            setSpeakerphoneCompat(false)
        }
    }

    override fun close() {
        stop()
    }

    @Suppress("DEPRECATION")
    private fun AudioManager.setSpeakerphoneCompat(enable: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            isSpeakerphoneOn = enable
        } else if (enable) {
            availableCommunicationDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { setCommunicationDevice(it) }
        } else {
            clearCommunicationDevice()
        }
    }
}