package com.example.ava.wakewords.providers

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.ava.utils.copyTo
import com.example.ava.wakewords.models.WakeWord
import com.example.ava.wakewords.models.WakeWordWithId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

@OptIn(ExperimentalSerializationApi::class)
class DocumentTreeWakeWordProvider(
    val context: Context,
    val treeUri: Uri,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : WakeWordProvider {
    override suspend fun get(): List<WakeWordWithId> = withContext(dispatcher) {
        val documentsTree =
            DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val wakeWords = buildList {
            // Get all json files
            for (file in documentsTree.listFiles().filter { it.name?.endsWith(".json") ?: false }) {
                val name = file.name!!
                runCatching {
                    val wakeWord = context.contentResolver.openInputStream(file.uri)?.use {
                        Json.decodeFromStream<WakeWord>(it)
                    }
                    if (wakeWord != null) {
                        add(
                            WakeWordWithId(
                                file.uri.toString(),
                                wakeWord
                            ) { loadModel(wakeWord.model) })
                    }
                }.onFailure {
                    Log.e(TAG, "Error loading wake word: $name", it)
                }
            }
        }
        return@withContext wakeWords
    }

    private suspend fun loadModel(model: String): ByteBuffer = withContext(dispatcher) {
        val documentsTree = DocumentFile.fromTreeUri(context, treeUri)
        val file = documentsTree?.findFile(model) ?: error("Model $model not found")
        val buffer = context.contentResolver.getTFLiteModelBufferOrNull(file.uri)
            ?: error("Could not load model $model")
        return@withContext buffer
    }

    /**
     * Attempts to get a ByteBuffer for a model from the given uri.
     * TFLite requires that the ByteBuffer is either a Direct or Mapped ByteBuffer, in native order.
     * If the uri points to a local file a MappedByteBuffer is returned else the stream is copied
     * to a Direct ByteBuffer.
     * If the uri is invalid, returns null.
     */
    private fun ContentResolver.getTFLiteModelBufferOrNull(uri: Uri): ByteBuffer? =
        openFileDescriptor(uri, "r")?.use { descriptor ->
            // If statSize is >= 0 then the descriptor points to a local file
            // with the given size and can be mapped directly
            val size = descriptor.statSize
            if (size >= 0) {
                return FileInputStream(descriptor.fileDescriptor).use { stream ->
                    stream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        0,
                        size
                    ).apply {
                        order(ByteOrder.nativeOrder())
                    }
                }
            }

            // If not a local file, try and query the size and copy to a direct byte buffer
            return query(uri, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val sizeIndex: Int = it.getColumnIndex(OpenableColumns.SIZE)
                    val size = if (it.isNull(sizeIndex)) 0 else it.getLong(sizeIndex)
                    if (size >= 0) {
                        openInputStream(uri)?.use {
                            return ByteBuffer.allocateDirect(size.toInt()).apply {
                                order(ByteOrder.nativeOrder())
                                it.copyTo(this)
                                flip()
                            }
                        }
                    }
                }
                return null
            }
        }

    companion object {
        private const val TAG = "DocumentTreeWakeWordProvider"
    }
}