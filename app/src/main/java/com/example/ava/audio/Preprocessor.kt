package com.example.ava.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import timber.log.Timber

class Preprocessor(
    private val audioSessionId: Int,
    private val enableNoiseSuppressor: Boolean,
    private val enableAcousticEchoCanceler: Boolean,
    private val enableAutomaticGainControl: Boolean
) {
    private var noiseSuppressor: NoiseSuppressor? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null

    private var _enabled = false
    val enabled get() = _enabled

    fun enable() {
        if (_enabled)
            return
        _enabled = true

        if (enableNoiseSuppressor) {
            if (!NoiseSuppressor.isAvailable()) {
                Timber.w("NoiseSuppressor not available")
            } else {
                enableEffect("NoiseSuppressor") { NoiseSuppressor.create(audioSessionId) }
            }
        }

        if (enableAcousticEchoCanceler) {
            if (!AcousticEchoCanceler.isAvailable()) {
                Timber.w("AcousticEchoCanceler not available")
            } else {
                enableEffect("AcousticEchoCanceler") { AcousticEchoCanceler.create(audioSessionId) }
            }
        }

        if (enableAutomaticGainControl) {
            if (!AutomaticGainControl.isAvailable()) {
                Timber.w("AutomaticGainControl not available")
            } else {
                enableEffect("AutomaticGainControl") { AutomaticGainControl.create(audioSessionId) }
            }
        }
    }

    private fun enableEffect(tag: String, effect: () -> AudioEffect?) {
        runCatching {
            val effect = effect()
            if (effect == null) {
                Timber.w("Error creating $tag")
            } else {
                effect.enabled = true
                if (effect.enabled) {
                    Timber.d("Enabled $tag")
                } else {
                    Timber.w("Enabling $tag failed")
                }
            }
        }.onFailure {
            Timber.e(it, "Error enabling $tag")
        }
    }

    fun release() {
        noiseSuppressor?.release()
        acousticEchoCanceler?.release()
        automaticGainControl?.release()
        _enabled = false
    }
}