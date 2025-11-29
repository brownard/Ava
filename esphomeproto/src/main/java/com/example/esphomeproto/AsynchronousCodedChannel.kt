package com.example.esphomeproto

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.MessageLite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel

class AsynchronousCodedChannel<T : AsynchronousByteChannel>(channel: T) :
    AsynchronousBufferedByteChannel<T>(channel) {

    suspend fun writeMessage(message: MessageLite) {
        val messageType = MESSAGE_TYPES.getOrDefault(message::class.java, null)
        if (messageType == null) {
            error("No message type for ${message::class}")
        }

        val messageBytes = message.toByteArray()

        val length = 1 + // Preamble
                CodedOutputStream.computeUInt32SizeNoTag(messageType) +
                CodedOutputStream.computeUInt32SizeNoTag(messageBytes.size) +
                messageBytes.size

        val byteBuffer = ByteBuffer.allocate(length)
        CodedOutputStream.newInstance(byteBuffer).apply {
            writeUInt32NoTag(0)
            writeUInt32NoTag(messageBytes.size)
            writeUInt32NoTag(messageType)
            writeRawBytes(messageBytes)
        }
        writeFully(byteBuffer)
    }

    suspend fun readMessage(): MessageLite {
        var message = readMessageInternal()
        while (message == null) {
            // Unknown message type, ignore and read the next
            // TODO: Handle unknown message types
            message = readMessageInternal()
        }
        return message
    }

    private suspend fun readMessageInternal(): MessageLite? {
        // Plain text packets have an indicator bit of 0,
        // encrypted is 1. Only plain text is supported for now.
        val indicator = readByte().toInt()
        if (indicator != 0)
            error("Unsupported indicator: $indicator")

        val length = readVarUInt().toInt()
        val messageType = readVarUInt().toInt()
        val messageBytes = ByteArray(length)
        readFully(messageBytes, 0, length)
        val parser = MESSAGE_PARSERS.getOrDefault(messageType, null)
        if (parser == null) {
            // Unknown message type
            return null
        }
        return parser.parseFrom(messageBytes) as MessageLite
    }

    suspend fun readVarUInt(): UInt {
        var result = 0u
        var shift = 0
        while (shift < 32) {
            val resultUInt = readByte().toUInt()
            result = result or ((resultUInt and (0x7fu)) shl shift)
            if (resultUInt and 0x80u == 0u) {
                return result
            }
            shift += 7
        }

        // The ESPHome protocol should never send VarUInts more than 16 bits long,
        // but the reference protobuf implementation allows up to 64 bits and
        // simply discards the top 32 bits. Do the same here for maximum compatibility.
        while (shift < 64) {
            val resultInt = readByte().toInt()
            if (resultInt and 0x80 == 0) {
                return result
            }
            shift += 7
        }
        error("VarUInt value too large")
    }
}