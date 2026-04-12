package com.example.ava.esphome.microphone

import java.nio.ByteBuffer

/**
 * Interface for reading audio from a microphone.
 */
interface Microphone : AutoCloseable {
    /**
     * Starts the microphone, allocating any resources.
     */
    fun start()

    /**
     * Reads audio from the microphone, blocking until audio is available.
     * The returned buffer is only valid until the next call to [read].
     * Must only be called between calls to [start] and [stop].
     */
    fun read(): ByteBuffer

    /**
     * Stops the microphone, releasing any resources.
     */
    fun stop()
}