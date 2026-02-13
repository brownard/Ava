package com.example.ava.stubs

import com.example.ava.server.Server
import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf

open class StubServer : Server {
    override val isConnected = flowOf(true)
    val receivedMessages = MutableSharedFlow<MessageLite>()
    override fun start(port: Int) = receivedMessages
    override fun disconnectCurrentClient() {}
    override suspend fun sendMessage(message: MessageLite) {}
    override fun close() {}
}