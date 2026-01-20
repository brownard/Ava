package com.example.ava.utils

import java.io.InputStream
import java.nio.ByteBuffer

const val DEFAULT_BUFFER_SIZE = 8192

fun ByteBuffer.fillFrom(src: ByteBuffer): Int {
    val remaining = remaining()
    if (remaining == 0)
        return 0

    val srcRemaining = src.remaining()
    if (srcRemaining <= remaining) {
        put(src)
        return srcRemaining
    } else {
        val currentLimit = src.limit()
        src.limit(src.position() + remaining)
        put(src)
        src.limit(currentLimit)
        return remaining
    }
}

/**
 * Copies as many bytes as possible from this stream to the given ByteBuffer, returning the number of bytes copied.
 */
fun InputStream.copyTo(out: ByteBuffer, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer).coerceAtMost(out.remaining())
    while (bytes >= 0 && out.hasRemaining()) {
        out.put(buffer, 0, bytes)
        bytesCopied += bytes
        bytes = read(buffer).coerceAtMost(out.remaining())
    }
    return bytesCopied
}