package com.example.ava.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream

fun <T> defaultCorruptionHandler(default: T) = ReplaceFileCorruptionHandler { exception ->
    Timber.e(exception, "Error reading settings, returning defaults")
    default
}

@OptIn(ExperimentalSerializationApi::class)
class SettingsSerializer<T>(val serializer: KSerializer<T>, override val defaultValue: T) :
    Serializer<T> {
    val json = Json { ignoreUnknownKeys = true }
    override suspend fun readFrom(input: InputStream): T =
        try {
            json.decodeFromStream(
                deserializer = serializer,
                stream = input
            )
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read Settings", serialization)
        }

    override suspend fun writeTo(t: T, output: OutputStream) {
        json.encodeToStream(
            serializer = serializer,
            value = t,
            stream = output
        )
    }
}
