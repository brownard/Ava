package com.example.ava.esphome

import com.example.ava.esphome.entities.Entity
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

abstract class EspHomeDevice(
    coroutineContext: CoroutineContext,
    protected val name: String,
    protected val port: Int = DEFAULT_SERVER_PORT,
    protected val server: Server = ServerImpl(),
    entities: Iterable<Entity> = emptyList()
) : AutoCloseable {
    protected val entities = entities.toList()
    protected val _state = MutableStateFlow<EspHomeState>(Disconnected)
    val state = _state.asStateFlow()
    protected val isSubscribedToEntityState = MutableStateFlow(false)

    protected val scope = CoroutineScope(
        coroutineContext + Job(coroutineContext.job) + CoroutineName("${this.javaClass.simpleName} Scope")
    )

    open fun start() {
        startServer()
        startConnectedChangedListener()
        listenForEntityStateChanges()
    }

    protected abstract suspend fun getDeviceInfo(): DeviceInfoResponse

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

    fun listenForEntityStateChanges() = isSubscribedToEntityState
        .flatMapLatest { subscribed ->
            if (!subscribed)
                emptyFlow()
            else
                entities
                    .map { it.subscribe() }
                    .merge()
                    .onEach { sendMessage(it) }
        }.launchIn(scope)

    private suspend fun handleMessageInternal(message: MessageLite) {
        Timber.d("Received message: ${message.javaClass.simpleName} $message")
        handleMessage(message)
    }

    protected open suspend fun handleMessage(message: MessageLite) {
        when (message) {
            is HelloRequest -> sendMessage(helloResponse {
                name = this@EspHomeDevice.name
                apiVersionMajor = 1
                apiVersionMinor = 10
            })

            is DisconnectRequest -> {
                sendMessage(disconnectResponse { })
                server.disconnectCurrentClient()
            }

            is DeviceInfoRequest -> sendMessage(getDeviceInfo())

            is PingRequest -> sendMessage(pingResponse { })

            is SubscribeHomeAssistantStatesRequest -> isSubscribedToEntityState.value = true

            is ListEntitiesRequest -> {
                entities.map { it.handleMessage(message) }.asFlow().flattenConcat()
                    .collect { response -> sendMessage(response) }
                sendMessage(listEntitiesDoneResponse { })
            }

            else -> {
                entities.map { it.handleMessage(message) }.asFlow().flattenConcat()
                    .collect { response -> sendMessage(response) }
            }
        }
    }

    protected suspend fun sendMessage(message: MessageLite) {
        Timber.d("Sending message: ${message.javaClass.simpleName} $message")
        server.sendMessage(message)
    }

    protected open suspend fun onConnected() {
        _state.value = Connected
    }

    protected open suspend fun onDisconnected() {
        isSubscribedToEntityState.value = false
        _state.value = Disconnected
    }

    override fun close() {
        scope.cancel()
        server.close()
    }
}