package com.example.ava.wakewords.providers

import android.content.res.AssetManager
import android.util.Log
import com.example.ava.wakewords.models.WakeWord
import com.example.ava.wakewords.models.WakeWordWithId
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class AssetWakeWordProvider(
    val assets: AssetManager,
    val path: String = DEFAULT_WAKE_WORD_PATH,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : WakeWordProvider {
    override suspend fun get(): List<WakeWordWithId> = withContext(dispatcher) {
        val assetsList = assets.list(path)
        if (assetsList == null)
            return@withContext emptyList()

        val gson = Gson()
        val wakeWords = buildList {
            for (asset in assetsList.filter { it.endsWith(".json") }) {
                runCatching {
                    val json = assets.open("$path/$asset")
                        .bufferedReader()
                        .use { it.readText() }
                    val wakeWord: WakeWord = gson.fromJson(
                        json,
                        object : TypeToken<WakeWord>() {}.type
                    )
                    val id = asset.substring(0, asset.lastIndexOf(".json"))
                    add(WakeWordWithId(id, wakeWord) { loadModel(wakeWord.model) })
                }.onFailure {
                    Log.e(TAG, "Error loading wake word: $asset", it)
                }
            }
        }
        return@withContext wakeWords
    }

    private suspend fun loadModel(model: String): ByteBuffer = withContext(dispatcher) {
        val modelFileDescriptor = assets.openFd("$path/$model")
        modelFileDescriptor.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                val fileChannel = stream.channel
                val startOffset: Long = modelFileDescriptor.startOffset
                val declaredLength: Long = modelFileDescriptor.declaredLength
                return@use fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    startOffset,
                    declaredLength
                )
            }
        }
    }

    companion object {
        private const val TAG = "AssetWakeWordProvider"
        const val DEFAULT_WAKE_WORD_PATH = "wakeWords"
    }
}