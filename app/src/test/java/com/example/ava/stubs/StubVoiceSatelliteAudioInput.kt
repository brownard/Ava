package com.example.ava.stubs

import com.example.ava.esphome.voicesatellite.AudioResult
import com.example.ava.esphome.voicesatellite.VoiceSatelliteAudioInput
import com.example.ava.wakewords.models.WakeWordWithId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

open class StubVoiceSatelliteAudioInput : VoiceSatelliteAudioInput {
    override val availableWakeWords = emptyList<WakeWordWithId>()
    override val availableStopWords = emptyList<WakeWordWithId>()
    protected val _activeWakeWords = MutableStateFlow(emptyList<String>())
    override val activeWakeWords: StateFlow<List<String>> = _activeWakeWords
    override fun setActiveWakeWords(value: List<String>) {
        _activeWakeWords.value = value
    }

    protected val _activeStopWords = MutableStateFlow(emptyList<String>())
    override val activeStopWords: StateFlow<List<String>> = _activeStopWords
    override fun setActiveStopWords(value: List<String>) {
        _activeStopWords.value = value
    }

    protected val _muted = MutableStateFlow(false)
    override val muted: StateFlow<Boolean> = _muted
    override fun setMuted(value: Boolean) {
        _muted.value = value
    }

    override var isStreaming: Boolean = false
    val audioResults = MutableSharedFlow<AudioResult>()
    override fun start(): Flow<AudioResult> = audioResults
}