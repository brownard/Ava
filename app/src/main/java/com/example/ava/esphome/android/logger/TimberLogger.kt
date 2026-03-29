package com.example.ava.esphome.android.logger

import android.util.Log
import com.example.ava.esphome.logger.Logger
import com.example.esphomeproto.api.LogLevel
import com.example.esphomeproto.api.SubscribeLogsResponse
import com.example.esphomeproto.api.subscribeLogsResponse
import com.google.protobuf.ByteString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import timber.log.Timber
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A [Logger] implementation that sends [SubscribeLogsResponse] messages for messages logged through [Timber].
 */
@OptIn(ExperimentalAtomicApi::class)
class TimberLogger : Logger {
    /**
     * A [Timber.Tree] that sends log messages to a callback.
     * Planted when logs are being subscribed to and uprooted when not.
     */
    class Tree(
        private val callback: (
            level: LogLevel,
            message: String
        ) -> Unit
    ) : Timber.DebugTree() {
        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?
        ) {
            val level = when (priority) {
                Log.VERBOSE -> LogLevel.LOG_LEVEL_VERBOSE
                Log.DEBUG -> LogLevel.LOG_LEVEL_DEBUG
                Log.INFO -> LogLevel.LOG_LEVEL_INFO
                Log.WARN -> LogLevel.LOG_LEVEL_WARN
                Log.ERROR, Log.ASSERT -> LogLevel.LOG_LEVEL_ERROR
                // Shouldn't ever happen
                else -> LogLevel.LOG_LEVEL_ERROR
            }
            callback(level, if (tag != null) "$tag: $message" else message)
        }
    }

    private val level = MutableStateFlow(LogLevel.LOG_LEVEL_NONE)
    override fun setLogLevel(level: LogLevel) {
        this.level.value = level
    }

    override fun subscribe(): Flow<SubscribeLogsResponse> = level.flatMapLatest { logLevel ->
        // LOG_LEVEL_NONE is set when no logs should be sent
        if (logLevel == LogLevel.LOG_LEVEL_NONE) {
            emptyFlow()
        } else callbackFlow {
            val tree = Tree { level, message ->
                if (logLevel.ordinal >= level.ordinal)
                    trySend(subscribeLogsResponse {
                        this.message = ByteString.copyFromUtf8(message)
                        this.level = level
                    })
            }
            Timber.plant(tree)
            awaitClose {
                Timber.uproot(tree)
            }
        }.buffer(Channel.UNLIMITED)
    }
}