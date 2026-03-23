package com.example.ava.esphome

import android.Manifest
import androidx.annotation.RequiresPermission
import com.example.ava.esphome.entities.Entity
import com.example.ava.esphome.voiceassistant.VoiceAssistant
import com.example.ava.server.DEFAULT_SERVER_PORT
import com.example.ava.server.Server
import com.example.ava.server.ServerException
import com.example.ava.server.ServerImpl
import com.example.esphomeproto.api.DeviceInfoRequest
import com.example.esphomeproto.api.DeviceInfoResponse
import com.example.esphomeproto.api.DisconnectRequest
import com.example.esphomeproto.api.HelloRequest
import com.example.esphomeproto.api.ListEntitiesRequest
import com.example.esphomeproto.api.PingRequest
import com.example.esphomeproto.api.SubscribeHomeAssistantStatesRequest
import com.example.esphomeproto.api.SubscribeVoiceAssistantRequest
import com.example.esphomeproto.api.VoiceAssistantAnnounceRequest
import com.example.esphomeproto.api.VoiceAssistantConfigurationRequest
import com.example.esphomeproto.api.VoiceAssistantEventResponse
import com.example.esphomeproto.api.VoiceAssistantSetConfiguration
import com.example.esphomeproto.api.VoiceAssistantTimerEventResponse
import com.example.esphomeproto.api.disconnectResponse
import com.example.esphomeproto.api.helloResponse
import com.example.esphomeproto.api.listEntitiesDoneResponse
import com.example.esphomeproto.api.pingResponse
import com.google.protobuf.MessageLite
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

interface EspHomeState
data object Connected : EspHomeState
data object Disconnected : EspHomeState
data object Stopped : EspHomeState
data class ServerError(val message: String) : EspHomeState

class EspHomeDevice(
    coroutineContext: CoroutineContext,
    private val port: Int = DEFAULT_SERVER_PORT,
    private val server: Server = ServerImpl(),
    private val deviceInfo: DeviceInfoResponse,
    val voiceAssistant: VoiceAssistant,
    entities: Iterable<Entity> = emptyList()
) : AutoCloseable {
    private val entities = entities.toList()
    private val _state = MutableStateFlow<EspHomeState>(Disconnected)
    val state = _state.asStateFlow()
    private val isSubscribedToVoiceAssistant = MutableStateFlow(false)
    private val isSubscribedToEntityState = MutableStateFlow(false)

    private val scope = CoroutineScope(
        coroutineContext + Job(coroutineContext.job) + CoroutineName("${this.javaClass.simpleName} Scope")
    )

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        startServer()
        voiceAssistant.start()
        startConnectedChangedListener()
        listenForEntityStateChanges()
        listenForVoiceAssistantResponses()
    }

    private fun startServer() {
        server.start(port)
            .onEach { handleMessageInternal(it) }
            .catch { e ->
                if (e !is ServerException) throw e
                _state.value = ServerError(e.message ?: "Unknown error")
            }
            .launchIn(scope)
    }

    private fun startConnectedChangedListener() = server.isConnected
        .onEach { isConnected ->
            if (isConnected)
                onConnected()
            else
                onDisconnected()
        }
        .launchIn(scope)

    private fun listenForEntityStateChanges() = isSubscribedToEntityState
        .flatMapLatest { subscribed ->
            if (!subscribed)
                emptyFlow()
            else
                entities.map { it.subscribe() }.merge()
        }
        .onEach { sendMessage(it) }
        .launchIn(scope)

    private fun listenForVoiceAssistantResponses() = isSubscribedToVoiceAssistant
        .flatMapLatest { subscribed ->
            if (!subscribed)
                emptyFlow()
            else
                voiceAssistant.subscribe()
        }
        .onEach { sendMessage(it) }
        .launchIn(scope)


    private suspend fun handleMessageInternal(message: MessageLite) {
        Timber.d("Received message: ${message.javaClass.simpleName} $message")
        handleMessage(message)
    }

    private suspend fun handleMessage(message: MessageLite) {
        when (message) {
            is HelloRequest -> sendMessage(helloResponse {
                name = deviceInfo.name
                apiVersionMajor = 1
                apiVersionMinor = 10
            })

            is DisconnectRequest -> {
                sendMessage(disconnectResponse { })
                server.disconnectCurrentClient()
            }

            is DeviceInfoRequest -> sendMessage(deviceInfo)

            is PingRequest -> sendMessage(pingResponse { })

            is SubscribeHomeAssistantStatesRequest -> isSubscribedToEntityState.value = true

            is ListEntitiesRequest -> {
                entities.map { it.handleMessage(message) }.asFlow().flattenConcat()
                    .collect { response -> sendMessage(response) }
                sendMessage(listEntitiesDoneResponse { })
            }

            is SubscribeVoiceAssistantRequest -> isSubscribedToVoiceAssistant.value =
                message.subscribe

            is VoiceAssistantConfigurationRequest,
            is VoiceAssistantSetConfiguration,
            is VoiceAssistantAnnounceRequest,
            is VoiceAssistantEventResponse,
            is VoiceAssistantTimerEventResponse -> voiceAssistant.handleMessage(message)

            else -> {
                entities.map { it.handleMessage(message) }.asFlow().flattenConcat()
                    .collect { response -> sendMessage(response) }
            }
        }
    }

    private suspend fun sendMessage(message: MessageLite) {
        Timber.d("Sending message: ${message.javaClass.simpleName} $message")
        server.sendMessage(message)
    }

    private suspend fun onConnected() {
        _state.value = Connected
        voiceAssistant.onConnected()
    }

    private suspend fun onDisconnected() {
        isSubscribedToEntityState.value = false
        isSubscribedToVoiceAssistant.value = false
        _state.value = Disconnected
        voiceAssistant.onDisconnected()
    }

    override fun close() {
        scope.cancel()
        voiceAssistant.close()
        server.close()
    }
}