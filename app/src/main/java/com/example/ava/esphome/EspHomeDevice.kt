package com.example.ava.esphome

import android.util.Log
import com.example.ava.esphome.entities.Entity
import com.example.ava.server.Server
import com.example.esphomeproto.ConnectRequest
import com.example.esphomeproto.DeviceInfoRequest
import com.example.esphomeproto.DeviceInfoResponse
import com.example.esphomeproto.DisconnectRequest
import com.example.esphomeproto.HelloRequest
import com.example.esphomeproto.ListEntitiesRequest
import com.example.esphomeproto.MediaPlayerCommandRequest
import com.example.esphomeproto.PingRequest
import com.example.esphomeproto.SubscribeHomeAssistantStatesRequest
import com.example.esphomeproto.connectResponse
import com.example.esphomeproto.deviceInfoResponse
import com.example.esphomeproto.disconnectResponse
import com.example.esphomeproto.helloResponse
import com.example.esphomeproto.listEntitiesDoneResponse
import com.example.esphomeproto.pingResponse
import com.google.protobuf.GeneratedMessage
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

open class EspHomeDevice(
    coroutineContext: CoroutineContext,
    protected val name: String,
    protected val port: Int = Server.DEFAULT_SERVER_PORT,
    private val deviceInfo: DeviceInfoResponse = deviceInfoResponse { },
    entities: Iterable<Entity> = emptyList()
): AutoCloseable {
    protected val server = Server()
    protected val entities = entities.toList()
    protected val isSubscribedToEntityState = AtomicBoolean(false)

    protected val scope = CoroutineScope(
        coroutineContext + Job(coroutineContext.job) + CoroutineName("${this.javaClass.simpleName} Scope")
    ).apply {
        coroutineContext.job.invokeOnCompletion(::onScopeCompleted)
    }

    open fun start() {
        scope.coroutineContext.job.invokeOnCompletion(::onScopeCompleted)
        startServer()
        listenForEntityStateChanges()
    }

    fun startServer(){
        server.start(port)
            .onEach { handleMessageInternal(it) }
            .launchIn(scope)
    }

    fun listenForEntityStateChanges() {
        for (entity in entities) {
            entity.state.onEach {
                if (isSubscribedToEntityState.get())
                    sendMessage(it)
            }.launchIn(scope)
        }
    }

    private suspend fun handleMessageInternal(message: GeneratedMessage){
        Log.d(TAG, "Received message: ${message.javaClass.simpleName} $message")
        handleMessage(message)
    }

    protected open suspend fun handleMessage(message: GeneratedMessage) {
        when (message) {
            is HelloRequest -> sendMessage(helloResponse {
                name = this@EspHomeDevice.name
                apiVersionMajor = 1
                apiVersionMinor = 10
            })

            is ConnectRequest -> {
                sendMessage(connectResponse { })
                onConnected()
            }

            is DisconnectRequest -> {
                sendMessage(disconnectResponse { })
                onDisconnected()
            }

            is DeviceInfoRequest -> sendMessage(deviceInfo)

            is PingRequest -> sendMessage(pingResponse { })

            is ListEntitiesRequest, is SubscribeHomeAssistantStatesRequest, is MediaPlayerCommandRequest -> {
                if (message is SubscribeHomeAssistantStatesRequest)
                    isSubscribedToEntityState.set(true)
                entities.forEach { entity ->
                    entity.handleMessage(message).forEach { response ->
                        sendMessage(response)
                    }
                }
                if (message is ListEntitiesRequest)
                    sendMessage(listEntitiesDoneResponse { })
            }
        }
    }

    protected suspend fun sendMessage(message: GeneratedMessage) {
        Log.d(TAG, "Sending message: ${message.javaClass.simpleName} $message")
        server.sendMessage(message)
    }

    protected open suspend fun onConnected() { }
    protected open suspend fun onDisconnected() { }

    open fun onScopeCompleted(cause: Throwable?) {
        Log.d(TAG, "$scope completed")
        server.close()
    }

    override fun close() {
        scope.cancel()
    }

    companion object {
        val TAG = this::class.java.simpleName
    }
}