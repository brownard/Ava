package com.example.ava.esphome.voicesatellite

import com.example.ava.esphome.Connected
import com.example.ava.esphome.EspHomeState
import com.example.esphomeproto.api.voiceAssistantAnnounceFinished
import com.google.protobuf.MessageLite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Announcement(
    private val scope: CoroutineScope,
    private val voiceOutput: VoiceOutput,
    private val sendMessage: suspend (MessageLite) -> Unit,
    private val stateChanged: (state: EspHomeState) -> Unit,
    private val ended: suspend (continueConversation: Boolean) -> Unit
) {
    private var _state: EspHomeState = Connected
    val state get() = _state

    fun announce(mediaUrl: String, preannounceUrl: String, continueConversation: Boolean) {
        updateState(Responding)
        voiceOutput.playAnnouncement(preannounceUrl, mediaUrl) {
            scope.launch {
                stop()
                ended(continueConversation)
            }
        }
    }

    suspend fun stop() {
        if (_state == Responding) {
            voiceOutput.stopTTS()
            updateState(Connected)
            sendMessage(voiceAssistantAnnounceFinished { })
        }
    }

    private fun updateState(state: EspHomeState) {
        if (state != _state) {
            _state = state
            stateChanged(state)
        }
    }
}