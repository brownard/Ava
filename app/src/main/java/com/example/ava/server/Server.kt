package com.example.ava.server

import com.example.ava.utils.acceptAsync
import com.google.protobuf.MessageLite
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.atomic.AtomicReference

const val DEFAULT_SERVER_PORT = 6053

class ServerException(message: String?, cause: Throwable? = null) :
    Throwable(message, cause)

interface Server : AutoCloseable {
    /**
     * Returns whether a client is currently connected.
     */
    val isConnected: Flow<Boolean>

    /**
     * Starts listening for client connections and returns a flow
     * of messages received from the client.
     */
    fun start(port: Int = DEFAULT_SERVER_PORT): Flow<MessageLite>

    /**
     * Disconnects the current client, if any.
     */
    fun disconnectCurrentClient()

    /**
     * Sends a message to the connected client.
     */
    suspend fun sendMessage(message: MessageLite)
}

class ServerImpl(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : Server {
    private val serverRef = AtomicReference<AsynchronousServerSocketChannel?>(null)
    private val connection = MutableStateFlow<ClientConnection?>(null)
    override val isConnected = connection.map { it != null }

    override fun start(port: Int) = acceptClients(port)
        .catch { throw ServerException(it.message, it) }
        .flatMapConcat {
            connectClient(it)
        }
        .catch {
            if (it is ServerException) throw it
            Timber.e(it, "Client connection error")
        }
        .flowOn(dispatcher)

    override fun disconnectCurrentClient() {
        connection.value?.let { disconnectClient(it) }
    }

    private fun acceptClients(port: Int) = flow {
        var server: AsynchronousServerSocketChannel? = null
        try {
            server = AsynchronousServerSocketChannel.open()
            if (!serverRef.compareAndSet(null, server))
                error("Server already started")
            server.bind(InetSocketAddress(port))
            while (true) {
                val accepted = server.acceptAsync()
                emit(accepted)
            }
        } finally {
            server?.close()
            serverRef.compareAndSet(server, null)
        }
    }

    private fun connectClient(socket: AsynchronousSocketChannel): Flow<MessageLite> {
        val client = ClientConnection(socket)
        connection.getAndUpdate { client }?.close()
        return client.readMessages().onCompletion {
            disconnectClient(client)
        }
    }

    private fun disconnectClient(client: ClientConnection) {
        client.close()
        val updated = connection.compareAndSet(client, null)
        Timber.d("Disconnected client: $updated")
    }

    override suspend fun sendMessage(message: MessageLite): Unit = withContext(dispatcher) {
        connection.value?.sendMessage(message)
    }

    override fun close() {
        connection.getAndUpdate { null }?.close()
        serverRef.getAndSet(null)?.close()
    }
}