package com.example.ava.stubs

import com.example.ava.esphome.voicesatellite.AudioResult
import com.example.ava.esphome.voicesatellite.VoiceInput
import com.example.ava.wakewords.models.WakeWordWithId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

open class StubVoiceInput : VoiceInput {
    override suspend fun getAvailableWakeWords() = emptyList<WakeWordWithId>()
    override suspend fun getAvailableStopWords() = emptyList<WakeWordWithId>()
    override val activeWakeWords = stubSettingState(emptyList<String>())
    override val activeStopWords = stubSettingState(emptyList<String>())
    override val muted = stubSettingState(false)
    override var isStreaming: Boolean = false
    val audioResults = MutableSharedFlow<AudioResult>()
    override fun start(): Flow<AudioResult> = audioResults
}