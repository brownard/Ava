package com.example.ava.stubs

import com.example.ava.esphome.microphone.Microphone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private val EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0)

@OptIn(ExperimentalAtomicApi::class)
open class StubMicrophone() : Microphone {
    val readCount = MutableStateFlow(0)

    override fun start() {}

    override fun read(): ByteBuffer {
        readCount.update { it + 1 }
        return EMPTY_BYTE_BUFFER
    }

    override fun stop() {}

    override fun close() {}
}