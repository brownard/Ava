package com.example.ava.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.KSerializer

interface SettingsStore<T> {
    fun getFlow(): Flow<T>
    suspend fun get(): T
    suspend fun update(transform: suspend (T) -> T)
}

open class SettingsStoreImpl<T>(
    private val context: Context,
    private val default: T,
    fileName: String,
    serializer: KSerializer<T>
) :
    SettingsStore<T> {

    private val Context.dataStore: DataStore<T> by dataStore(
        fileName,
        SettingsSerializer(serializer, default),
        corruptionHandler = defaultCorruptionHandler(default)
    )

    override fun getFlow() = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading settings, returning defaults", exception)
                emit(default)
            } else throw exception
        }
        .onEach { Log.d(TAG, "Loaded settings: $it") }

    override suspend fun get(): T = getFlow().first()

    override suspend fun update(transform: suspend (T) -> T) {
        context.dataStore.updateData(transform)
    }

    companion object {
        private const val TAG = "SettingsStore"
    }
}