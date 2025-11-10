package com.example.ava.esphome

import android.util.Log
import com.example.ava.esphome.entities.Entity
import com.example.ava.server.Server
import com.example.esphomeproto.api.ConnectRequest
import com.example.esphomeproto.api.DeviceInfoRequest
import com.example.esphomeproto.api.DeviceInfoResponse
import com.example.esphomeproto.api.DisconnectRequest
import com.example.esphomeproto.api.HelloRequest
import com.example.esphomeproto.api.ListEntitiesRequest
import com.example.esphomeproto.api.MediaPlayerCommandRequest
import com.example.esphomeproto.api.PingRequest
import com.example.esphomeproto.api.SubscribeHomeAssistantStatesRequest
import com.example.esphomeproto.api.connectResponse
import com.example.esphomeproto.api.deviceInfoResponse
import com.example.esphomeproto.api.disconnectResponse
import com.example.esphomeproto.api.helloResponse
import com.example.esphomeproto.api.listEntitiesDoneResponse
import com.example.esphomeproto.api.pingResponse
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
    )

    open fun start() {
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

    override fun close() {
        scope.cancel()
        server.close()
    }

    companion object {
        val TAG = this::class.java.simpleName
    }
}